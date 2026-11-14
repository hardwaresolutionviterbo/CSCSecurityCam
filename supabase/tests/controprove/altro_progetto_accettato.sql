-- Difetto introdotto apposta: accetta token di qualsiasi progetto. Deve far fallire C4.
create or replace function public.current_uid()
returns text language sql stable as $$ select auth.jwt() ->> 'sub' $$;
