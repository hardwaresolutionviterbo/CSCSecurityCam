# Fase 0 · Decisioni privacy approvate (P1-P15)

Decisioni prese il 13 novembre 2026 sulle proposte di
[`PRIVACY_IMPOSTAZIONI.md`](PRIVACY_IMPOSTAZIONI.md).

- I requisiti del PDF ufficiale restano invariati: queste decisioni riguardano solo i dettagli che
  il piano non fissa.
- Nessuna modifica a codice, database, Firebase, Supabase o configurazioni: le decisioni si
  applicano nelle fasi indicate.

Legenda dell'esito:

- **Approvata**: proposta accettata così com'era;
- **Approvata con precisazioni**: proposta accettata, con dettagli aggiunti;
- **Modificata**: è stata scelta un'opzione diversa dalla proposta.

## Riepilogo

| N. | Argomento | Decisione | Esito | Fase |
|---|---|---|---|---|
| P1 | Privacy predefinita | Ogni opzione non fissata dal PDF parte dalla scelta più protettiva, salvo le eccezioni decise qui (P3, P5, P8) | Approvata | Tutte |
| P2 | Controlli lato server | Tutte le scelte privacy applicate e testate lato server | Approvata | Tutte |
| P3 | Bio e link social | Interruttore «Mostra» per ciascuno, acceso per impostazione predefinita | Approvata | 2 |
| P4 | Dati aziendali | P.IVA, ragione sociale e documenti protetti fino alla decisione in Fase 6; sede ufficiale pubblica sulla mappa | Approvata con precisazioni | 6 |
| P5 | Numero, scelta di partenza | «Contatti riconosciuti dalla rubrica» | **Modificata** (proposta: «Nessuno») | 2 |
| P6 | Chi vede il numero | Solo chi ha il tuo numero salvato in rubrica ed è riconosciuto; accettare una chat non lo rende visibile | **Modificata** | 2, 4 |
| P7 | Scadenza dei dati della rubrica | 30 giorni senza nuova sincronizzazione | Approvata | 2 |
| P8 | «Permetti a chi ha il mio numero di trovarmi» | Acceso quando attivi la tua sincronizzazione della rubrica, poi modificabile; spento altrimenti | **Modificata** (proposta: spento) | 2 |
| P9 | Posizione dei nuovi annunci | Zona approssimata preselezionata; scelta per ogni annuncio tra approssimata, precisa scelta a mano e GPS in tempo reale | Approvata con precisazioni | 3 |
| P10 | Durata del GPS in tempo reale | Durata libera scelta dall'utente, massimo 8 ore | **Modificata** (proposta: 15 min, 1 h, 8 h) | 3 |
| P11 | Posizione condivisa a fine GPS | Cancellata subito dal server alla scadenza o alla revoca; nessuno storico | Approvata | 3 |
| P12 | Effetti del blocco | Come proposto, applicati lato server | Approvata | 2, 4, 5 |
| P13 | Consensi nella pagina Privacy | Elencati con data e pulsante «Revoca» | Approvata | 2, 3, 6 |
| P14 | Quando proporre il Controllo privacy | Alla fine della registrazione, saltabile; sempre disponibile dalla pagina Privacy | Approvata | 2 |
| P15 | Salvataggio nel Controllo privacy | Immediato, scelta per scelta | Approvata | 2 |

**Proposte ancora da approvare: nessuna.** Tutte e 15 hanno una decisione.

## Dettaglio delle decisioni

### P1 · Privacy predefinita: approvata

Ogni opzione che il PDF non fissa parte dalla scelta più protettiva. Le eccezioni sono quelle
decise esplicitamente in questo documento:

- P3: bio e social visibili;
- P5: numero visibile ai contatti riconosciuti;
- P8: trovabilità legata alla propria rubrica.

### P2 · Controlli lato server: approvata

Ogni scelta della pagina Privacy la applica il server (regole Firestore, policy RLS di Supabase,
funzioni server). L'app mostra solo ciò che il server le consegna. Ogni regola va provata anche
con tentativi non autorizzati, come chiede il PDF.

### P3 · Bio e link social: approvata

Un interruttore «Mostra nel profilo pubblico» per la bio e uno per i link social, accesi per
impostazione predefinita. Se non sono compilati non compare nulla. Restano sempre visibili agli
utenti autenticati, come da PDF: nome abbreviato, @username, avatar.

### P4 · Dati aziendali: approvata con precisazioni

- Tutte le attività commerciali (bar, ristoranti, negozi, aziende) inseriscono il proprio
  **indirizzo ufficiale**.
- Dopo la registrazione la **sede compare automaticamente sulla Mappa Sociale**, con un segnalino
  dedicato e il profilo dell'attività.
- La posizione pubblica è quella della **sede dichiarata**, mai quella GPS privata del titolare.
- **Partita IVA, ragione sociale e documenti** restano protetti, visibili solo all'azienda e
  all'amministratore, fino alla decisione sulla loro visibilità in Fase 6. Il documento non sarà
  pubblico.
- Il **badge** lo assegna solo l'amministratore.
- Resta valido anche l'altro requisito del PDF: il badge è solo per le aziende, richiede
  l'abbonamento attivo e sparisce se l'abbonamento scade.

### P5 · Chi vede il numero, scelta di partenza: modificata

- Scelta di partenza: **«Contatti riconosciuti dalla rubrica»**. La proposta era «Nessuno».
- È un'eccezione decisa a P1.
- L'utente può sempre passare a «Nessuno».
- Per effetto di P6, l'opzione «Contatti riconosciuti e amici o contatti accettati» non rende il
  numero visibile a nessuno in più: va rivista nell'interfaccia della Fase 2.

### P6 · Chi vede il numero: modificata

La regola funziona come WhatsApp:

- il numero è visibile **solo** se l'altra persona ha il tuo numero salvato nella propria rubrica,
  è riconosciuta con il sistema sicuro (PSI/OPRF o equivalente) e tu hai consentito la visibilità
  ai contatti riconosciuti;
- **accettare una richiesta di chat non rende visibile il numero**;
- chi non ha il tuo numero salvato può comunque chattare tramite @username, senza vedere il
  numero;
- la visibilità è **revocabile** e **verificata lato server**.

È compatibile con il PDF, che dice che il numero «può» essere mostrato ai contatti riconosciuti e
agli amici o contatti accettati, e mai agli sconosciuti. Qui si sceglie la forma più restrittiva:
i contatti accettati senza il numero in rubrica non lo vedono.

### P7 · Scadenza dei dati della rubrica: approvata

- I dati derivati dalla rubrica sul server si cancellano dopo **30 giorni** senza una nuova
  sincronizzazione.
- Revocare la sincronizzazione li cancella subito.
- Con P6, chi viene cancellato smette di vedere il tuo numero finché la rubrica non si
  risincronizza.

### P8 · «Permetti a chi ha il mio numero di trovarmi»: modificata

- L'interruttore si **accende quando attivi la tua sincronizzazione della rubrica**, con
  spiegazione nel consenso. Poi resta modificabile.
- Se non usi la rubrica resta **spento**.
- La proposta originale era «spento per impostazione predefinita».
- Conseguenza: chi sceglie la rubrica viene trovato dai propri contatti, come in WhatsApp. Chi non
  la usa non viene riconosciuto e il suo numero non è visibile a nessuno.

### P9 · Posizione dei nuovi annunci: approvata con precisazioni

- Per ogni singolo annuncio l'utente sceglie tra:
  - **posizione approssimata**, preselezionata per proteggere la privacy;
  - **posizione precisa scelta a mano**, per esempio un negozio o un punto d'incontro. Vale solo
    per quell'annuncio e non cambia la posizione privata abituale né il GPS corrente;
  - **GPS in tempo reale temporaneo**, che richiede un **consenso esplicito**, ha una **durata
    configurabile** (P10) e si può **interrompere subito**.
- Prima della pubblicazione viene **sempre mostrata** la modalità selezionata, anche nella gestione
  dell'annuncio, come da PDF.

### P10 · Durata del GPS in tempo reale: modificata

- **Durata libera scelta dall'utente, massimo 8 ore.** La proposta prevedeva tre durate fisse.
- Nessuna condivisione senza scadenza.
- Si può sempre interrompere prima.
- La durata minima e il modo di scegliere la durata (per esempio un selettore) si definiscono in
  Fase 3.

### P11 · Posizione condivisa a fine GPS: approvata

Alla scadenza o alla revoca, l'ultima posizione condivisa viene cancellata subito dal server. Non
resta nessuno storico dei percorsi.

### P12 · Effetti del blocco: approvata

Applicati lato server. Chi è bloccato:

- non può scriverti né chiamarti;
- non vede il tuo numero;
- non può commentare i tuoi annunci;
- non viene avvisato del blocco.

Inoltre nessuno dei due vede le reazioni e i commenti dell'altro. Il profilo pubblico resta
visibile sugli annunci pubblici, senza modo di contattarti.

### P13 · Consensi nella pagina Privacy: approvata

Accanto ai permessi del telefono (posizione, contatti, notifiche, foto, microfono) compaiono i
consensi dati nell'app, con data e pulsante «Revoca»:

- rubrica;
- GPS in tempo reale;
- notifiche geolocalizzate delle promozioni (Fase 6).

La revoca ferma subito la funzione collegata.

### P14 · Quando proporre il Controllo privacy: approvata

Proposto alla fine della registrazione (Fase 2), saltabile. Sempre disponibile dalla pagina
Privacy.

### P15 · Salvataggio nel Controllo privacy: approvata

Ogni scelta si salva subito. Non c'è un pulsante «Salva» finale. Il riepilogo mostra lo stato
reale e permette di correggere ogni voce.

## Prove collegate

Sul telefono, nelle fasi indicate e nei test della Fase 9:

1. **P5 e P6:**
   - un secondo telefono che **ha** il tuo numero in rubrica ed è riconosciuto vede il numero;
   - uno che **non** ce l'ha non lo vede, nemmeno dopo aver accettato una chat;
   - con la visibilità revocata, nessuno lo vede.
2. **P7 e P8:** la revoca della rubrica cancella i dati e rende il numero di nuovo invisibile. Chi
   non usa la rubrica non viene riconosciuto.
3. **P9-P11:** la modalità approssimata è preselezionata e mostrata prima della pubblicazione; il
   GPS live si ferma alla scadenza (massimo 8 ore) o alla revoca, e l'ultima posizione sparisce
   dal server.
4. **P4:** la sede di un'attività di prova compare sulla mappa all'indirizzo dichiarato, non alla
   posizione del titolare; P.IVA e documento non sono visibili ad altri utenti.
5. **P12:** l'utente bloccato non può scrivere né commentare, e i due non vedono le reciproche
   reazioni e commenti.
