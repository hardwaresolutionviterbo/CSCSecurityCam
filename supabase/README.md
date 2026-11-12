# Supabase · Nexus Social

Qui vivono **annunci, ricerca per distanza, foto, segnalazioni e gestionale**.
Chat, chiamate e notifiche restano su Firebase (vedi `../firebase/`).

## Applicare lo schema

1. Crea il progetto su [supabase.com](https://supabase.com) (piano gratuito per iniziare).
2. Apri **SQL Editor**, incolla il contenuto di `migrations/0001_init.sql` e premi **Run**.
3. In **Project Settings → API** copia `Project URL` e la chiave pubblica del progetto
   (`anon public key`, oppure `publishable key` nei progetti nuovi): servono all'app tramite
   `--dart-define` (mai scritti nel repository).
4. Verifica in **Table Editor** che esistano `profiles`, `listings`, `listing_locations`,
   `reports`, `user_moderation`, `admin_users`, e in **Storage** il bucket `listing-photos`.

## Collegare l'app alla build GitHub

L'integrazione GitHub del pannello Supabase distribuisce solo le migrazioni: **non passa URL e
chiave all'app**. Per collegare davvero l'APK:

1. Su GitHub apri **Settings → Secrets and variables → Actions** e crea due *repository secrets*:
   `SUPABASE_URL` (Project URL) e `SUPABASE_ANON_KEY` (anon/publishable key). Non scriverli nel
   repository né in chat.
2. Fai ripartire **Android APK** (push su `main` oppure *Run workflow*): lo step «Verifica
   connessione Supabase» interroga `profiles` e stampa solo il codice HTTP.
3. Se lo step passa, nel Profilo dell'app i servizi risultano **Collegato**; senza i due segreti
   il workflow compila comunque, ma l'app mostra «Supabase non configurato».

## Cosa è già protetto

- **RLS attiva su tutte le tabelle**: senza policy esplicita non si legge e non si scrive nulla.
- Gli annunci si creano **solo** con la funzione `create_listing(...)`: il client non può
  scrivere direttamente la tabella e quindi non può inventarsi una posizione esatta visibile.
- La posizione esatta sta in `listing_locations`, leggibile solo dal proprietario e dagli admin.
  La mappa usa `approx_location` (griglia ~550 m) e `nearby_listings(...)` restituisce distanze
  a fasce di 250 m.
- `profiles.rating_avg`, `rating_count`, `account_type` e `created_at` sono protetti da trigger:
  li aggiorna solo il server/admin.
- Un utente può avere una sola segnalazione aperta per lo stesso oggetto.
- Le foto stanno in `listing-photos/<user-id>/...`; upload e cancellazione solo nella propria
  cartella.

## Rendere un utente amministratore

Dal SQL Editor (sostituisci l'UUID del profilo):

```sql
insert into public.admin_users (user_id, role)
values ('00000000-0000-0000-0000-000000000000', 'owner')
on conflict (user_id) do update set role = excluded.role;
```

## Prossime migrazioni

- `0002_business.sql` — aziende, partita IVA, badge verificato, abbonamento 9,99 €/mese.
- `0003_promotions.sql` — promozioni, coupon QR, limite di 5 al giorno.
- `0004_feedback.sql` — recensioni dopo annuncio concluso e media stelle sul profilo.
- `0005_events.sql` — eventi di gruppo («calcetto stasera, mancano 3 persone»).
