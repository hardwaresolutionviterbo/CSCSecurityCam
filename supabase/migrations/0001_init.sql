-- Nexus Social · Migrazione iniziale (Fase 1)
-- Annunci, ricerca per distanza, foto, segnalazioni e base del gestionale admin.
--
-- Come applicarla: Supabase → SQL Editor → incolla tutto il file → Run.
-- La migrazione presuppone un progetto Supabase vuoto (le policy storage possono
-- essere rieseguite, le tabelle no).
--
-- Privacy della posizione:
--  • la posizione esatta vive solo in public.listing_locations, leggibile dal proprietario
--    e dagli amministratori;
--  • public.listings.approx_location è la posizione aggiornata a una griglia di ~550 m ed è
--    l'unica coordinata restituita dalle ricerche;
--  • le distanze restituite sono arrotondate a fasce di 250 m per non triangolare la posizione.

create extension if not exists postgis with schema extensions;
create extension if not exists citext with schema extensions;

-- ---------------------------------------------------------------- tipi
create type public.account_type as enum ('private', 'business');
create type public.listing_kind as enum ('social', 'swap_offer', 'swap_want', 'sale');
create type public.listing_status as enum ('active', 'hidden', 'completed', 'removed');
create type public.report_target as enum ('listing', 'profile', 'chat', 'business');
create type public.report_status as enum ('open', 'reviewing', 'resolved', 'dismissed');
create type public.admin_role as enum ('owner', 'moderator', 'support');

-- ------------------------------------------------------- funzioni comuni
create or replace function public.touch_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

-- ---------------------------------------------------------------- profili
create table public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  account_type public.account_type not null default 'private',
  username extensions.citext not null unique
    check (username ~ '^[a-z0-9._]{3,24}$'),
  full_name text not null
    check (char_length(btrim(full_name)) between 2 and 80),
  -- In pubblico si vede solo "Carlo C." (nome + iniziale del cognome).
  public_name text generated always as (
    case
      when position(' ' in btrim(full_name)) = 0 then btrim(full_name)
      else split_part(btrim(full_name), ' ', 1) || ' ' ||
           upper(left(split_part(btrim(full_name), ' ', 2), 1)) || '.'
    end
  ) stored,
  avatar_url text,
  bio text check (bio is null or char_length(bio) <= 300),
  social_links jsonb not null default '{}'::jsonb,
  rating_avg numeric(3, 2) not null default 0 check (rating_avg between 0 and 5),
  rating_count integer not null default 0 check (rating_count >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create trigger profiles_touch_updated_at
  before update on public.profiles
  for each row execute function public.touch_updated_at();

-- ------------------------------------------------- administrazione (interna)
create table public.admin_users (
  user_id uuid primary key references auth.users (id) on delete cascade,
  role public.admin_role not null default 'moderator',
  created_at timestamptz not null default now()
);

create or replace function public.is_admin()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (select 1 from public.admin_users where user_id = auth.uid());
$$;

create table public.user_moderation (
  user_id uuid primary key references auth.users (id) on delete cascade,
  banned_until timestamptz,
  ban_reason text,
  updated_by uuid references auth.users (id),
  updated_at timestamptz not null default now()
);

create or replace function public.protect_profile_write()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if public.is_admin() then
    return new;
  end if;
  if new.rating_avg is distinct from old.rating_avg
     or new.rating_count is distinct from old.rating_count
     or new.created_at is distinct from old.created_at
     or new.account_type is distinct from old.account_type then
    raise exception 'Campi gestiti dal server' using errcode = '42501';
  end if;
  return new;
end;
$$;

create trigger profiles_protect_write
  before update on public.profiles
  for each row execute function public.protect_profile_write();

-- ---------------------------------------------------------------- annunci
create table public.listings (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references public.profiles (id) on delete cascade,
  kind public.listing_kind not null,
  status public.listing_status not null default 'active',
  title text not null check (char_length(btrim(title)) between 3 and 120),
  description text not null default '' check (char_length(description) <= 2000),
  category text,
  price_cents integer check (price_cents is null or price_cents >= 0),
  currency text not null default 'EUR' check (char_length(currency) = 3),
  photos text[] not null default '{}'
    check (coalesce(array_length(photos, 1), 0) <= 6),
  -- Posizione approssimata: unica coordinata visibile agli altri utenti.
  approx_location extensions.geography (point, 4326) not null,
  available_now boolean not null default false,
  expires_at timestamptz not null default now() + interval '7 days',
  completed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint sale_needs_price check (kind <> 'sale' or price_cents is not null),
  constraint expires_in_range check (expires_at <= created_at + interval '30 days')
);

create index listings_approx_location_idx
  on public.listings using gist (approx_location);
create index listings_owner_idx on public.listings (owner_id);
create index listings_active_idx
  on public.listings (kind, created_at desc)
  where status = 'active';

create table public.listing_locations (
  listing_id uuid primary key references public.listings (id) on delete cascade,
  exact_location extensions.geography (point, 4326) not null
);

create index listing_locations_exact_idx
  on public.listing_locations using gist (exact_location);

create trigger listings_touch_updated_at
  before update on public.listings
  for each row execute function public.touch_updated_at();

create or replace function public.protect_listing_write()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if public.is_admin() then
    return new;
  end if;
  if new.owner_id is distinct from old.owner_id
     or new.kind is distinct from old.kind
     or st_astext(new.approx_location) is distinct from st_astext(old.approx_location)
     or new.created_at is distinct from old.created_at then
    raise exception 'Campi non modificabili' using errcode = '42501';
  end if;
  return new;
end;
$$;

create trigger listings_protect_write
  before update on public.listings
  for each row execute function public.protect_listing_write();

-- Unico modo per creare un annuncio: la posizione arriva qui e viene approssimata dal server.
create or replace function public.create_listing(
  p_kind public.listing_kind,
  p_title text,
  p_description text,
  p_lat double precision,
  p_lon double precision,
  p_category text default null,
  p_price_cents integer default null,
  p_photos text[] default '{}',
  p_available_now boolean default false,
  p_expires_at timestamptz default now() + interval '7 days'
)
returns uuid
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  v_uid uuid := auth.uid();
  v_exact extensions.geography;
  v_approx extensions.geography;
  v_id uuid;
begin
  if v_uid is null then
    raise exception 'Autenticazione richiesta' using errcode = '42501';
  end if;

  if p_lat is null or p_lon is null
     or p_lat not between -90 and 90
     or p_lon not between -180 and 180 then
    raise exception 'Posizione non valida' using errcode = '22023';
  end if;

  if p_expires_at > now() + interval '30 days' then
    raise exception 'Scadenza troppo lontana (massimo 30 giorni)' using errcode = '22023';
  end if;

  if exists (
    select 1 from public.user_moderation m
    where m.user_id = v_uid and m.banned_until is not null and m.banned_until > now()
  ) then
    raise exception 'Account sospeso' using errcode = '42501';
  end if;

  v_exact := st_setsrid(st_makepoint(p_lon, p_lat), 4326)::extensions.geography;
  v_approx := st_snapToGrid(st_setsrid(st_makepoint(p_lon, p_lat), 4326), 0.005, 0.005)
    ::extensions.geography;

  insert into public.listings (
    owner_id, kind, title, description, category, price_cents, photos,
    approx_location, available_now, expires_at
  )
  values (
    v_uid, p_kind, btrim(p_title), coalesce(p_description, ''), p_category,
    p_price_cents, coalesce(p_photos, '{}'), v_approx, p_available_now, p_expires_at
  )
  returning id into v_id;

  insert into public.listing_locations (listing_id, exact_location)
  values (v_id, v_exact);

  return v_id;
end;
$$;

revoke all on function public.create_listing(
  public.listing_kind, text, text, double precision, double precision, text,
  integer, text[], boolean, timestamptz
) from public, anon;
grant execute on function public.create_listing(
  public.listing_kind, text, text, double precision, double precision, text,
  integer, text[], boolean, timestamptz
) to authenticated, service_role;

-- Ricerca per distanza: filtra sulla posizione esatta ma restituisce solo quella
-- approssimata e una fascia di distanza arrotondata.
create or replace function public.nearby_listings(
  p_lat double precision,
  p_lon double precision,
  p_radius_m integer default 10000,
  p_kind public.listing_kind default null,
  p_limit integer default 100
)
returns table (
  id uuid,
  owner_id uuid,
  kind public.listing_kind,
  title text,
  description text,
  category text,
  price_cents integer,
  currency text,
  photos text[],
  approx_lat double precision,
  approx_lon double precision,
  distance_band_m integer,
  available_now boolean,
  expires_at timestamptz,
  created_at timestamptz
)
language sql
stable
security definer
set search_path = public, extensions
as $$
  with origin as (
    select st_setsrid(st_makepoint(p_lon, p_lat), 4326)::extensions.geography as g
  )
  select
    l.id,
    l.owner_id,
    l.kind,
    l.title,
    l.description,
    l.category,
    l.price_cents,
    l.currency,
    l.photos,
    st_y(l.approx_location::geometry) as approx_lat,
    st_x(l.approx_location::geometry) as approx_lon,
    (floor(st_distance(ll.exact_location, o.g) / 250.0) * 250)::integer as distance_band_m,
    l.available_now,
    l.expires_at,
    l.created_at
  from public.listings l
  join public.listing_locations ll on ll.listing_id = l.id
  cross join origin o
  where l.status = 'active'
    and l.expires_at > now()
    and st_dwithin(ll.exact_location, o.g, least(greatest(p_radius_m, 1000), 30000))
    and (p_kind is null or l.kind = p_kind)
  order by ll.exact_location <-> o.g
  limit least(greatest(p_limit, 1), 200);
$$;

revoke all on function public.nearby_listings(
  double precision, double precision, integer, public.listing_kind, integer
) from public, anon;
grant execute on function public.nearby_listings(
  double precision, double precision, integer, public.listing_kind, integer
) to authenticated, service_role;

-- ---------------------------------------------------------------- segnalazioni
create table public.reports (
  id uuid primary key default gen_random_uuid(),
  reporter_id uuid not null references public.profiles (id) on delete cascade,
  target public.report_target not null,
  target_id uuid not null,
  reason text not null check (char_length(btrim(reason)) between 3 and 80),
  details text check (details is null or char_length(details) <= 1000),
  status public.report_status not null default 'open',
  handled_by uuid references auth.users (id),
  handled_at timestamptz,
  created_at timestamptz not null default now()
);

-- Una sola segnalazione aperta per utente e oggetto.
create unique index reports_one_open_per_target
  on public.reports (reporter_id, target, target_id)
  where status = 'open';
create index reports_queue_idx on public.reports (status, created_at);

-- ------------------------------------------------ sicurezza a livello di riga
alter table public.profiles enable row level security;
alter table public.admin_users enable row level security;
alter table public.user_moderation enable row level security;
alter table public.listings enable row level security;
alter table public.listing_locations enable row level security;
alter table public.reports enable row level security;

-- Profili: visibili a chi è autenticato, modificabili solo da chi li possiede.
create policy "profili visibili agli utenti"
  on public.profiles for select to authenticated using (true);
create policy "crea il proprio profilo"
  on public.profiles for insert to authenticated with check (id = auth.uid());
create policy "aggiorna il proprio profilo"
  on public.profiles for update to authenticated
  using (id = auth.uid()) with check (id = auth.uid());

-- Annunci: gli annunci attivi e non scaduti sono visibili; il proprietario vede i suoi.
create policy "annunci attivi visibili"
  on public.listings for select to authenticated
  using ((status = 'active' and expires_at > now()) or owner_id = auth.uid());
create policy "aggiorna i propri annunci"
  on public.listings for update to authenticated
  using (owner_id = auth.uid()) with check (owner_id = auth.uid());
create policy "elimina i propri annunci"
  on public.listings for delete to authenticated using (owner_id = auth.uid());
-- Nessuna policy di INSERT: si crea solo con public.create_listing().

-- Posizione esatta: solo proprietario dell'annuncio e amministratori.
create policy "posizione esatta del proprietario"
  on public.listing_locations for select to authenticated
  using (
    exists (
      select 1 from public.listings l
      where l.id = listing_id and l.owner_id = auth.uid()
    )
    or public.is_admin()
  );
-- Nessuna policy di scrittura: si scrive solo con public.create_listing().

-- Segnalazioni: chi segnala vede le sue, gli admin vedono la coda.
create policy "crea le proprie segnalazioni"
  on public.reports for insert to authenticated
  with check (reporter_id = auth.uid());
create policy "legge le proprie segnalazioni o tutta la coda da admin"
  on public.reports for select to authenticated
  using (reporter_id = auth.uid() or public.is_admin());
create policy "gli admin gestiscono le segnalazioni"
  on public.reports for update to authenticated
  using (public.is_admin()) with check (public.is_admin());

-- Moderazione: l'utente sa se è sospeso, gli admin gestiscono tutto.
create policy "leggi la propria moderazione"
  on public.user_moderation for select to authenticated
  using (user_id = auth.uid() or public.is_admin());
create policy "gli admin gestiscono la moderazione"
  on public.user_moderation for all to authenticated
  using (public.is_admin()) with check (public.is_admin());

-- Elenco admin: ognuno vede solo la propria riga; si gestisce da SQL Editor.
create policy "leggi la propria riga admin"
  on public.admin_users for select to authenticated using (user_id = auth.uid());

-- ------------------------------------------------ foto degli annunci
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'listing-photos',
  'listing-photos',
  true,
  5242880,
  array['image/jpeg', 'image/png', 'image/webp']
)
on conflict (id) do nothing;

create policy "foto annunci: lettura pubblica"
  on storage.objects for select
  using (bucket_id = 'listing-photos');

create policy "foto annunci: upload nella propria cartella"
  on storage.objects for insert to authenticated
  with check (
    bucket_id = 'listing-photos'
    and (storage.foldername(name))[1] = auth.uid()::text
  );

create policy "foto annunci: modifica le proprie"
  on storage.objects for update to authenticated
  using (
    bucket_id = 'listing-photos'
    and (storage.foldername(name))[1] = auth.uid()::text
  );

create policy "foto annunci: elimina le proprie"
  on storage.objects for delete to authenticated
  using (
    bucket_id = 'listing-photos'
    and (storage.foldername(name))[1] = auth.uid()::text
  );
