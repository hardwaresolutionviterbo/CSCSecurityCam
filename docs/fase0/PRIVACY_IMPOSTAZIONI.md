# Fase 0 · Progetto delle impostazioni Privacy e del «Controllo privacy»

Documento di progetto, senza codice. Riferimento:
`Nexus_Social_Piano_di_Lavoro_COMPLETO_AGGIORNATO.pdf`, versione del 13 novembre 2026.

Nel documento si distinguono due tipi di voce:

- **[PDF]**: requisito del piano. Non si modifica e non si elimina.
- **[PROPOSTA]**: scelta di dettaglio che il piano non fissa. Va approvata prima dello sviluppo
  (elenco completo nella sezione 8).

Le impostazioni si **progettano** in Fase 0 e si **sviluppano** nelle fasi indicate: la pagina
Privacy e il Controllo privacy in Fase 2, la posizione e il GPS in Fase 3, le preferenze delle
notifiche dei commenti in Fase 4 e 5.

---

## 1. Principi

1. **[PDF]** I dati pubblici e i dati privati sono separati fin dalle regole dei database
   (Fase 1).
2. **[PDF]** Il numero di telefono e i dati di autenticazione restano privati. Non compaiono mai
   in mappa, annunci, commenti, gruppi o notifiche. Il numero può essere mostrato solo ai contatti
   riconosciuti tramite rubrica e agli amici o contatti accettati, se le impostazioni Privacy lo
   consentono. Agli sconosciuti mai.
3. **[PDF]** La posizione esatta privata non si vede mai pubblicamente. Gli annunci personali
   compaiono in una zona approssimata.
4. **[PDF]** La condivisione GPS in tempo reale è spenta per impostazione predefinita, è
   temporanea, si può revocare in qualsiasi momento ed è visibile solo alle persone autorizzate.
5. **[PDF]** La posizione scelta a mano vale solo per il singolo annuncio. Non cambia la posizione
   privata abituale né la posizione GPS corrente.
6. **[PDF]** La sincronizzazione della rubrica è facoltativa e revocabile, con consenso
   esplicito.
   - Il riconoscimento dei contatti non espone la rubrica in chiaro: niente semplice SHA-256 dei
     numeri, ma PSI/OPRF o una soluzione equivalente.
   - Servono rate limit, protezione anti-abuso, revoca, cancellazione e scadenza dei dati.
7. **[PDF]** Il profilo sociale è consultabile dagli utenti autenticati quando aprono un avatar o
   un annuncio: nome abbreviato, @username, avatar, bio e link social, secondo le preferenze.
8. **[PDF]** Mi piace e Non mi piace non generano notifiche. I nomi di chi reagisce si vedono
   aprendo gli elenchi, che rispettano blocchi, ban, cancellazioni e autorizzazioni.
9. **[PROPOSTA P1]** **Privacy predefinita.** Ogni opzione che il piano non fissa parte dalla
   scelta più protettiva, coerente con il principio di privacy fin dalla progettazione e per
   impostazione predefinita (art. 25 GDPR). Lo verifica chi rivede i testi legali.
10. **[PROPOSTA P2]** **Controlli lato server.** Le scelte della pagina Privacy le applica il
    server (regole Firestore, policy RLS di Supabase, funzioni server). L'app mostra soltanto ciò
    che il server le consegna: un'opzione nascosta solo nell'interfaccia non conta come
    protezione.

## 2. Classificazione dei dati

Livelli di visibilità:

- **Pubblico (autenticati)**: lo vede qualsiasi utente registrato e autenticato. Non è visibile
  fuori dall'app.
- **Limitato**: lo vede solo chi è autorizzato da una scelta dell'utente.
- **Privato**: lo vedono solo l'utente stesso e, quando serve, l'amministratore autorizzato.
- **Solo server**: non arriva a nessun telefono, nemmeno a quello del proprietario, se non serve.

| Dato | Livello | Fonte nel piano / nota |
|---|---|---|
| Nome e cognome completi | Privato | [PDF] in pubblico solo abbreviato |
| Nome abbreviato (es. «Carlo C.») | Pubblico (autenticati) | [PDF] |
| @username | Pubblico (autenticati) | [PDF] univoco |
| Avatar | Pubblico (autenticati) | [PDF] |
| Bio, link social | Pubblico (autenticati), secondo le preferenze | [PDF]; preferenze in [PROPOSTA P3] |
| Numero di telefono | Privato; Limitato solo se l'utente lo consente | [PDF] mai agli sconosciuti |
| Dati di autenticazione (codici SMS, token, 2FA) | Solo server / dispositivo | [PDF] |
| Posizione esatta dell'utente | Privato (solo dispositivo, oppure server senza esposizione) | [PDF] |
| Zona approssimata dell'annuncio | Pubblico (autenticati), entro il raggio | [PDF] |
| Posizione scelta a mano per un annuncio | Pubblico (autenticati), solo per quell'annuncio | [PDF] |
| Posizione GPS in tempo reale | Limitato: solo persone autorizzate, a tempo | [PDF] |
| Sede aziendale | Pubblico (autenticati), segnaposto permanente | [PDF] Fase 6 |
| Rubrica del telefono | Mai inviata in chiaro; dati derivati a scadenza | [PDF] |
| Contatti riconosciuti | Privato | [PDF] |
| Elenco dei bloccati | Privato | [PDF] |
| Reazioni (Mi piace / Non mi piace) | Nome pubblico visibile negli elenchi dell'annuncio | [PDF] |
| Commenti | Pubblico (autenticati): avatar, nome pubblico, testo, data e ora | [PDF] |
| Feedback a stelle e media | Pubblico (autenticati) sul profilo dei privati | [PDF] Fase 5 |
| Partita IVA, ragione sociale, documento facoltativo | Privato e admin; esposizione pubblica da decidere | [PROPOSTA P4] |
| Messaggi delle chat | Cifrati end-to-end: leggibili solo dai partecipanti | [PDF] Fase 4 |
| Messaggi allegati a una segnalazione | Solo quelli scelti da chi segnala, visibili all'admin autorizzato | [PDF] Fase 4 |
| Token delle notifiche push | Solo server e proprietario | già così nelle regole Firestore |

## 3. Pagina «Privacy»

**[PDF]** Pagina dedicata, ispirata alla chiarezza delle impostazioni di WhatsApp. Raccoglie:
visibilità del profilo, numero di telefono, contatti, posizione, condivisione GPS, blocchi e
autorizzazioni.

Struttura proposta: una voce per sezione, con sotto la scelta attuale (es. «Numero: Nessuno»).
Toccando la voce si apre il dettaglio.

### 3.1 Visibilità del profilo (Fase 2)

- **[PDF]** Nome abbreviato, @username e avatar sono sempre visibili agli utenti autenticati: sono
  il minimo per riconoscere chi pubblica o commenta.
- **[PROPOSTA P3]** Bio e link social hanno ciascuno un interruttore «Mostra nel profilo
  pubblico», acceso per impostazione predefinita.
  - Sono dati che l'utente scrive apposta per condividerli; se non li compila, non compare nulla.
  - È l'interpretazione proposta di «secondo le preferenze previste».
- **[PROPOSTA]** Un'anteprima «Come ti vedono gli altri» mostra il profilo pubblico esattamente
  come lo riceve un altro utente.

### 3.2 Numero di telefono (Fase 2)

Domanda: «Chi può vedere il mio numero?»

| Opzione | Effetto |
|---|---|
| **Nessuno** | Il numero non viene consegnato a nessun altro utente. **[PROPOSTA P5]: predefinita.** |
| Contatti riconosciuti dalla rubrica | Solo chi ha il tuo numero in rubrica, ed è riconosciuto con il matching sicuro, vede il numero. |
| Contatti riconosciuti e amici o contatti accettati | Come sopra, più chi hai accettato come contatto. |

- **[PDF]** Agli sconosciuti mai, in nessuna opzione.
- **Da definire, [PROPOSTA P6]:** il piano parla di «amici/contatti accettati» ma non definisce
  come si diventa contatto accettato. Proposta: è contatto accettato chi ha una conversazione
  accettata con te tramite «richiesta di messaggio» (Fase 4). Nessun nuovo sistema di amicizia.

### 3.3 Contatti / rubrica (Fase 2)

- **[PDF]** Interruttore «Trova i contatti iscritti», spento per impostazione predefinita.
  Quando lo accendi:
  1. una schermata spiega cosa succede: quali dati, come vengono protetti, per quanto tempo;
  2. chiede il consenso esplicito;
  3. solo dopo chiede il permesso di sistema per i contatti.
- **[PDF]** Pulsante «Revoca e cancella»: interrompe la sincronizzazione e cancella dal server
  tutti i dati derivati dalla rubrica.
- Mostra lo stato: attiva o non attiva, data dell'ultima sincronizzazione, data di scadenza dei
  dati.
- **[PROPOSTA P7]** I dati derivati dalla rubrica scadono dopo **30 giorni** senza una nuova
  sincronizzazione e vengono cancellati.
- **[PROPOSTA P8]** Interruttore «Permetti a chi ha il mio numero di trovarmi», spento per
  impostazione predefinita.
  - Chi lo spegne non viene riconosciuto dalle rubriche altrui.
  - Serve a dare un controllo anche a chi non ha attivato la propria sincronizzazione.
  - Da approvare: va verificato che rientri nel requisito dei «contatti autorizzati».
  - Conseguenza da valutare: se è spento per impostazione predefinita, all'inizio quasi nessuno
    sarà trovabile. L'alternativa è accenderlo insieme alla propria sincronizzazione della
    rubrica, con spiegazione chiara nel consenso.
- **[PDF]** Requisiti tecnici, da progettare in Fase 2:
  - PSI/OPRF o una soluzione equivalente;
  - niente SHA-256 semplice;
  - rate limit e protezione anti-abuso (per esempio contro chi carica rubriche enormi o casuali
    per scoprire chi è iscritto).

### 3.4 Posizione (Fase 3)

- **[PDF]** Informazione fissa: «La tua posizione esatta non è mai visibile agli altri. I tuoi
  annunci compaiono in una zona approssimata.»
- **[PDF]** Modalità per i nuovi annunci privati:
  - zona approssimata dalla posizione corrente;
  - GPS in tempo reale, facoltativo;
  - posizione scelta a mano (punto d'incontro, bar, ristorante, indirizzo), valida solo per
    l'annuncio.
- **[PROPOSTA P9]** La modalità predefinita per i nuovi annunci è la **zona approssimata**.
- **[PDF]** La modalità attiva si mostra prima della pubblicazione e nella gestione
  dell'annuncio. Nella pagina Privacy c'è il richiamo a questa regola.
- **[PDF]** La mappa si centra sulla posizione in tempo reale e si aggiorna ogni poche centinaia
  di metri. La posizione serve a centrare la mappa e a calcolare il raggio: non viene pubblicata.

### 3.5 Condivisione GPS in tempo reale (Fase 3)

- **[PDF]** Spenta per impostazione predefinita, temporanea, revocabile, visibile solo alle
  persone autorizzate.
- Mostra l'elenco delle condivisioni attive: con chi, legata a quale annuncio, quando scade.
- Ha il pulsante «Interrompi tutte le condivisioni».
- **[PROPOSTA P10]** Durate selezionabili: 15 minuti, 1 ora, 8 ore. Nessuna condivisione senza
  scadenza.
- **[PROPOSTA P11]** Alla scadenza o alla revoca, l'ultima posizione condivisa viene cancellata
  dal server.

### 3.6 Blocchi (Fase 2)

- **[PDF]** Elenco degli utenti bloccati con «Sblocca».
- **[PROPOSTA P12]** Effetti di un blocco, applicati dal server:
  - l'utente bloccato non può scriverti né chiamarti, e non vede il tuo numero;
  - non può commentare i tuoi annunci;
  - ognuno dei due non vede le reazioni e i commenti dell'altro;
  - non viene avvisato del blocco.

  Il piano chiede che blocchi ed elenchi delle reazioni siano coerenti, e che i blocchi valgano
  anche per i commenti (Fasi 5 e 9).

### 3.7 Autorizzazioni (Fasi 2-4)

- **[PDF]** Riepilogo dei permessi di sistema: posizione, contatti, notifiche, fotocamera e foto,
  microfono per i vocali e le chiamate.
- Per ciascuno mostra lo stato e un collegamento alle impostazioni del telefono.
- **[PROPOSTA P13]** Accanto ai permessi, i consensi dati nell'app (rubrica, GPS in tempo reale,
  notifiche geolocalizzate delle promozioni in Fase 6) con la data e il pulsante per revocarli.

### 3.8 Collegamenti

- Privacy Policy, Termini d'uso, Regole della community.
- **[PROPOSTA]** «Scarica i miei dati» e «Elimina account»: richieste previste dai diritti GDPR.
  Si sviluppano in Fase 2 o al più tardi in Fase 9; il percorso è da approvare.

## 4. Percorso guidato «Controllo privacy»

**[PDF]** Spiega le impostazioni e permette di rivedere e modificare le scelte.

- **Quando si apre:**
  - dalla pagina Privacy, in qualsiasi momento;
  - **[PROPOSTA P14]** proposto anche alla fine della registrazione (Fase 2), con la possibilità
    di saltarlo.
- **Come funziona:**
  - una schermata per argomento, con spiegazione semplice, scelta attuale e pulsante per
    cambiarla;
  - si può uscire in qualsiasi momento;
  - **[PROPOSTA P15]** ogni scelta si salva subito, non alla fine.

| Passo | Contenuto |
|---|---|
| 1. Il tuo profilo pubblico | Anteprima di come ti vedono gli altri; bio e social visibili sì o no |
| 2. Il tuo numero | Chi può vederlo (3.2); ricorda che agli sconosciuti non si vede mai |
| 3. I tuoi contatti | Stato della rubrica, cosa comporta, «Revoca e cancella», «Permetti di trovarmi» |
| 4. La tua posizione | Spiegazione della zona approssimata e delle tre modalità degli annunci |
| 5. GPS in tempo reale | Condivisioni attive e come interromperle |
| 6. Persone bloccate | Elenco e sblocco |
| 7. Autorizzazioni | Permessi di sistema e consensi, con revoca |
| 8. Riepilogo | Le scelte attuali in una schermata, con «Modifica» accanto a ognuna |

## 5. Notifiche (solo per coerenza: non sono nella pagina Privacy)

- **[PDF]** Le preferenze delle notifiche dei commenti (in-app e push) stanno tra le preferenze
  delle notifiche (Fasi 4 e 5).
- **[PDF]** Mi piace e Non mi piace non generano mai notifiche. Non esiste un'impostazione per
  attivarle.
- **[PDF]** Nessuna notifica mostra il numero di telefono. L'anteprima dei messaggi viene
  decifrata solo sul telefono.

## 6. Dove si applicano le regole

| Requisito | Dove viene applicato | Stato attuale |
|---|---|---|
| Numero mai pubblico | Server: il numero non sta nei profili pubblici | Profili Firestore senza telefono (regole testate, non ancora pubblicate); Supabase `profiles` senza telefono |
| Posizione esatta privata | Supabase: `listing_locations` leggibile solo dal proprietario e dagli admin; `nearby_listings` restituisce la zona approssimata | Schema presente; nessun test delle RLS |
| Nome abbreviato | Supabase: `public_name` generato dal database | Schema presente |
| Preferenze 3.1-3.7 | Da progettare in Fase 2 (tabelle o colonne nuove dopo verifica dello schema, come chiede il piano) | Non presenti |
| Rubrica PSI/OPRF | Funzione server dedicata, Fase 2 | Non presente |

## 7. Prove previste

Per le Fasi 2, 3 e 9, coerenti con «Cosa devi riuscire a provare sul telefono»:

1. Con il numero impostato su «Nessuno», un secondo telefono non vede il numero né nel profilo,
   né negli annunci, né nei commenti, né nelle chat o nelle notifiche.
2. Rubrica:
   - attivata: un contatto iscritto viene riconosciuto;
   - revocata: i dati derivati spariscono dal server;
   - alla scadenza vengono cancellati da soli.
3. Un annuncio con zona approssimata non permette di risalire all'indirizzo esatto, né dalla
   mappa né dalla distanza mostrata.
4. Il GPS in tempo reale lo vede solo la persona autorizzata e si ferma alla scadenza o alla
   revoca.
5. Un utente bloccato non può scrivere né commentare, e i due non vedono le reciproche reazioni.
6. Il Controllo privacy mostra le scelte reali e le modifiche hanno effetto subito.
7. Tentativi non autorizzati contro database e API vengono rifiutati, come chiede il piano.

## 8. Decisioni da approvare

| N. | Proposta |
|---|---|
| P1 | Privacy predefinita: ogni opzione non fissata dal piano parte dalla scelta più protettiva |
| P2 | Tutte le scelte applicate lato server |
| P3 | Bio e link social con interruttore «Mostra», acceso per impostazione predefinita |
| P4 | Partita IVA, ragione sociale e documento: non pubblici finché non si decide in Fase 6 cosa mostrare sul profilo dell'attività |
| P5 | Numero di telefono: predefinito «Nessuno» |
| P6 | «Contatto accettato» = conversazione accettata tramite richiesta di messaggio (Fase 4) |
| P7 | Scadenza dei dati della rubrica: 30 giorni |
| P8 | Interruttore «Permetti a chi ha il mio numero di trovarmi», spento per impostazione predefinita |
| P9 | Modalità predefinita dei nuovi annunci: zona approssimata |
| P10 | Durate del GPS in tempo reale: 15 minuti, 1 ora, 8 ore |
| P11 | Cancellazione dell'ultima posizione condivisa alla scadenza o alla revoca |
| P12 | Effetti del blocco come descritti in 3.6 |
| P13 | Consensi nell'app elencati con data e revoca |
| P14 | Controllo privacy proposto alla fine della registrazione, saltabile |
| P15 | Salvataggio immediato di ogni scelta |
