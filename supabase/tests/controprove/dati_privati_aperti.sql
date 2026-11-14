-- Difetto introdotto apposta: dati privati leggibili da tutti. Deve far fallire B1.
create policy "difetto di prova" on public.profile_private
  for select to authenticated using (true);
