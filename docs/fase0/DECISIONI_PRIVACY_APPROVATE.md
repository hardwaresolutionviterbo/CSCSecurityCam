# Fase 0 · Decisioni privacy approvate (P1-P15) e decisioni aggiuntive (D1-D4)

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

---

# Decisioni aggiuntive della Fase 0 (13 novembre 2026)

Decise dal titolare del progetto. Non modificano P1-P15 né i requisiti del PDF.

## D1 · Età minima

- Età minima dichiarata per registrarsi: **14 anni**.
- Registrazione semplice con **numero di telefono e data di nascita**.
- **Nessun caricamento obbligatorio di documenti d'identità.**
- **Nessun servizio esterno a pagamento** per la verifica dell'età nella prima versione.
- Nessuna procedura di verifica complessa che allontani Nexus Social dall'esperienza semplice di
  WhatsApp.
- Protezioni adeguate per i minorenni (D3) e verifica degli obblighi normativi prima della
  pubblicazione (vedi «Obblighi da verificare»).
- La data di nascita è un dato **privato**: serve solo al controllo dell'età e alle protezioni per
  i minorenni. È classificata così in `PRIVACY_IMPOSTAZIONI.md`.

## D2 · Identità e finalità di Nexus Social

Nexus Social **non è un'app di incontri romantici** e **non è una piattaforma per adulti o per
contenuti a luci rosse**.

È un **social network con marketplace integrato**, pensato per:

- socializzare e conoscere persone;
- organizzare incontri informali, per esempio andare a prendere un caffè;
- chiedere o offrire aiuto;
- trovare persone con interessi comuni, per esempio giocare insieme alla PlayStation;
- creare chat individuali e gruppi;
- usare la Mappa Social per scoprire persone, attività e iniziative;
- pubblicare annunci di vendita, acquisto, scambio e regalo;
- permettere ad aziende, negozi, bar e ristoranti di promuovere i propri locali e comparire sulla
  mappa con la propria sede ufficiale (vedi P4).

Nota di coerenza con il PDF:

- «regalo» rientra negli annunci di Scambia e Vendita;
- gli incontri informali e la ricerca di persone con interessi comuni rientrano negli annunci
  della Mappa Sociale e negli eventi di gruppo, già previsti in Fase 3.

Nessuna funzione nuova rispetto al piano.

## D3 · Sicurezza dei minorenni

- Nella **Mappa Social**, soprattutto al **primo accesso**, compare questo avviso di sicurezza,
  con il testo esatto:

  > «Se sei minorenne, presta attenzione alle persone che non conosci. Non condividere informazioni
  > personali e non accettare incontri con sconosciuti senza le dovute precauzioni. Se qualcuno ti
  > molesta, ti minaccia o si comporta in modo inappropriato, bloccalo e segnalalo immediatamente.»

- Strumenti **semplici e accessibili** per **bloccare** e **segnalare** gli utenti. Il PDF li
  prevede già nelle Fasi 2, 4, 5 e 7; gli effetti del blocco sono quelli di P12.
- **Protezioni adeguate della posizione personale.** Valgono già per tutti P9, P10 e P11, oltre al
  requisito del PDF che la posizione esatta non sia mai pubblica. Eventuali protezioni aggiuntive
  per i minorenni sono nella sezione «Dettagli».

## D4 · Principio fondamentale

Nexus Social **non diventa una piattaforma riservata agli adulti** né un sistema di messaggistica
con procedure e limitazioni eccessive. L'esperienza resta **semplice e immediata, simile a
WhatsApp**, e mantiene le funzioni social, la Mappa Social e il marketplace.

Ogni protezione per i minorenni va valutata anche rispetto a questo principio.

## Obblighi da verificare prima della pubblicazione (Fase 9, con il professionista)

Sono indicazioni per la revisione legale, non decisioni.

1. **GDPR e Codice privacy:** in Italia il consenso digitale autonomo è valido dai 14 anni
   (art. 2-quinquies). Le informative devono essere comprensibili anche per i minori.
2. **Digital Services Act, art. 28:** le piattaforme online accessibili ai minori devono adottare
   misure adeguate e proporzionate per un elevato livello di privacy, sicurezza e protezione dei
   minori. Lo stesso articolo vieta la pubblicità basata sulla profilazione verso i minori.
3. **Linee guida della Commissione europea del 14 luglio 2025 sull'art. 28.**
   - Raccomandano la **stima dell'età** anche quando i termini prevedono un'età minima inferiore
     ai 18 anni.
   - Sono il riferimento con cui viene valutata la conformità.
   - **Possibile tensione con D1** (solo data di nascita dichiarata): da valutare con il
     professionista, cercando la soluzione più semplice compatibile con D4.
4. **Compravendite e abbonamenti dei minorenni:** capacità di concludere contratti e regole degli
   store per gli acquisti dei minori.
5. **Promozioni e notifiche geolocalizzate ai minorenni:** compatibilità con il divieto di
   pubblicità profilata (punto 2).
6. **Foto reale dei minorenni come marker sulla mappa** (decisione A5): misure adeguate ai sensi
   dell'art. 28 del Digital Services Act.

## Dettagli di D1-D3 (M1-M7)

| N. | Argomento | Decisione | Fase |
|---|---|---|---|
| M1 | Data di nascita | Privata: età e data mai visibili agli altri; nessun segno «minorenne» nel profilo pubblico | 2 |
| M2 | Chi dichiara meno di 14 anni | Registrazione bloccata con messaggio chiaro; data e numero non salvati | 2 |
| M3 | Avviso di sicurezza della Mappa | A tutti al primo accesso, chiuso con «Ho capito»; sempre consultabile da un'icona «Sicurezza» sulla mappa. Per i minorenni, ripetuto prima di pubblicare un annuncio con posizione precisa o GPS in tempo reale | 3 |
| M4 | Posizione dei minorenni | Il GPS in tempo reale si condivide solo con contatti riconosciuti dalla rubrica, mai con sconosciuti; la posizione precisa scelta a mano resta disponibile | 3 |
| M5 | Richieste di messaggio da sconosciuti ai minorenni | Stesso meccanismo del PDF (accetta o rifiuta), con un richiamo breve all'avviso di sicurezza e i pulsanti Blocca e Segnala ben visibili | 4 |
| M6 | Marketplace e Azienda | I minorenni usano Scambia e Vendita come tutti, con l'avviso antitruffa; profilo Azienda e abbonamento solo per maggiorenni | 5, 6 |
| M7 | Notifiche geolocalizzate delle promozioni | Ai minorenni il consenso non viene proposto: niente notifiche, promozioni visibili su mappa e scheda | 6 |

Note:

- In M3 la domanda diceva «ogni volta che pubblicano il loro primo annuncio», una formulazione
  ambigua. Qui è registrato il richiamo **prima di ogni annuncio** con posizione precisa o GPS in
  tempo reale. Se preferisci solo la prima volta, si corregge prima della Fase 3.
- M4, M6 e M7 vanno confermate con il professionista in Fase 9: obblighi del Digital Services Act
  e capacità dei minori.

# Altre decisioni della Fase 0 (F8-F11)

| N. | Argomento | Decisione |
|---|---|---|
| F8 | Nome e identificativo | Nome «Nexus Social» invariato. Identificativo `com.nexussocial.app` mantenuto durante lo sviluppo; va **cambiato prima del primo caricamento su Google Play, entro la Fase 9** (beta chiusa), perché è già usato da un'altra app. Il cambio sarà un intervento tecnico da autorizzare (Firebase, CI, iOS) |
| F9 | Servizio mappa | **MapLibre + OpenFreeMap approvati.** «3D» = inclinazione, rotazione ed edifici 3D; il rilievo del terreno si aggiunge quando MapLibre lo rilascerà ufficialmente. Conferma con un prototipo all'inizio della Fase 3 (mappa, posizione, avatar animato, batteria); fornitore di riserva se fallisce |
| F10 | Chiavi e password | Confermato dal titolare: conservate in un posto sicuro |
| F11 | Build | Android su GitHub Actions (già attivo); iPhone su Codemagic: account gratuito da creare o confermare. Per installare su iPhone serve comunque l'account Apple (Fasi 9-10) |

# Decisioni su grafica e costi (14 novembre 2026)

| N. | Argomento | Decisione | Dettaglio |
|---|---|---|---|
| A1 | Barra di navigazione | Cinque voci del PDF nell'ordine **Chat, Mappa, Scambia (centrale, arancione), Vendita, Profilo**, con lo stile del mockup; niente Home né Community | `AVATAR_E_MARKER_CONCEPT.md`, sez. 7 |
| A5 | Immagine della persona | **Modificata dal titolare:** scelta libera tra foto propria (profilo **e** marker sulla mappa) e avatar 3D di Nexus Social; marker di attività, eventi e annunci distinti | `AVATAR_E_MARKER_CONCEPT.md`, sez. 7 |
| L1 | Login SMS | Sviluppo con numeri di prova su Spark senza carta; Blaze solo per la prova del numero reale e la beta | `LIMITI_GRATUITI.md`, sez. 5 |
| L2 | Budget | 10 € al mese con avvisi al 50%, 80% e 100% | `LIMITI_GRATUITI.md`, sez. 5 |
| L3 | Paesi SMS | Solo Italia (+39) | `LIMITI_GRATUITI.md`, sez. 5 |
| F11 | Account Codemagic | Il titolare lo crea ora; resta aperto fino alla sua conferma | — |

Controlli di coerenza:

- A5 è compatibile con il PDF e con P1-P15, D1-D4 e M1-M7: il marker con la foto rispetta le
  regole sulla posizione (P9-P11, M4).
- Da aggiungere agli «Obblighi da verificare» con il professionista: la foto reale dei minorenni
  usata come marker sulla mappa (art. 28 del Digital Services Act).
