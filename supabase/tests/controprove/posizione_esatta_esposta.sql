-- Difetto introdotto apposta: gli annunci approssimati mostrano il punto esatto. Deve far fallire D2.1.
create or replace function public.public_point(
  p_exact extensions.geography,
  p_mode public.location_mode
)
returns extensions.geography language sql immutable as $$ select p_exact $$;
