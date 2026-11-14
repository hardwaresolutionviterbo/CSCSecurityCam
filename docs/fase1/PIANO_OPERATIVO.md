# Fase 1 · Piano operativo definitivo

Stato: **piano in revisione, lavori NON avviati.** L'avvio richiede un'autorizzazione separata del
titolare.

Riferimento: `Nexus_Social_Piano_di_Lavoro_COMPLETO_AGGIORNATO.pdf`, «FASE 1 – Fondamenta
tecniche» (SHA-256 `7da25d52be13b242d9e0db3b87c740212b2d80fb7c0d4dd75d8e7e541b8561eb`).

Documenti di supporto:
- `docs/fase1/TEST_SUPABASE_RLS.md`: casi di prova per Supabase.

## 1. Obiettivo (dal PDF)

Avere un'app vuota che si installa sul telefono e si collega ai server.

> Cosa devi riuscire a provare sul telefono: l'app si installa sul telefono e mostra la barra di
> navigazione con le schermate vuote e i servizi collegati.

| Requisito del PDF | Situazione su `main` (`5e32045`) |
|---|---|
| Progetto Flutter unico per Android e iPhone su GitHub | Presente |
| Cartelle: account, chat, mappa, annunci, vendita, admin | Presenti con nomi in inglese (`profile`, `chat`, `map`, `listings`, `sale`, `admin`): corrispondenza da documentare |
| Collegare Firebase (login, chat, notifiche) e Supabase (annunci, distanza, gestionale) | Presente, attivo quando ci sono le chiavi |
| Codemagic per ottenere l'app a ogni modifica | `codemagic.yaml` presente ma non collegato; da allineare a F11 |
| Tema, modalità scura, barra in basso con campanella | Presente; ordine della barra da allineare ad A1 |
| Regole di sicurezza e separazione dati pubblici/privati | Firestore presente con test; Supabase da correggere (sez. 4) |

Gran parte del codice esiste già: questa fase **completa e allinea** il lavoro esistente al PDF e
alle decisioni della Fase 0. Non lo rifà da zero.

## 2. Regole di lavoro (vincolanti)

Servono sempre un'**autorizzazione esplicita e separata** del titolare per:
- migrazioni o scritture sul database reale (Supabase, Firestore);
- pubblicazione o deploy di regole, funzioni o app;
- collegamento di servizi (Codemagic, Supabase, Firebase o altri);
- qualunque spesa, abbonamento o attivazione di piani a pagamento (Blaze, Codemagic);
- modifiche a IAM o a Firebase;
- merge su `main`.

Inoltre:
- si lavora su un ramo dedicato, mai su `main`;
- un commit per passaggio e una pausa per la verifica del titolare alla fine di ciascuno;
- log e test non stampano mai chiavi, token, numeri di telefono, email, nomi reali o altri dati
  personali.

## 3. Decisioni che valgono per la Fase 1

| Decisione | Effetto |
|---|---|
| **A1** | Barra: **Chat, Mappa, Scambia** (pulsante centrale arancione), **Vendita, Profilo**; stile del mockup; niente Home né Community. **Modifica dell'ordine del PDF** (Mappa, Chat, …) approvata dal titolare |
| **Schermata iniziale** (14/11/2026) | All'apertura l'app mostra **Chat**, come WhatsApp (D4) |
| **Verifica dei servizi** (14/11/2026) | **Nessuna nuova schermata «Stato servizi».** Firebase e Supabase si verificano con i log delle build e i test automatici |
| **R1 · Identificazione dell'APK** (14/11/2026) | Ogni APK riporta chiaramente **versione, commit Git e ramo** di provenienza, con un numero di build diverso anche per le riesecuzioni (sez. 5) |
| **R2 · Restyling senza perdite** (14/11/2026) | Il restyling non elimina, non altera e non compromette nessuna funzione esistente; ogni funzione ha una verifica automatica (sez. 6) |
| **R3 · Test di sicurezza Supabase** (14/11/2026) | Test automatici delle regole di accesso (RLS): separazione tra dati pubblici e privati, nessuna lettura dei dati privati altrui, rifiuto dei token Firebase non validi o di altri utenti (`TEST_SUPABASE_RLS.md`) |
| **F11** | Android su GitHub Actions; Codemagic solo per iPhone. **Modifica rispetto al PDF** (che indica Codemagic per le build) approvata dal titolare |
| **F8** | Identificativo `com.nexussocial.app` invariato in questa fase; cambio entro la Fase 9 |
| **F10** | Chiavi solo nei Secrets di GitHub e nelle variabili di Codemagic, mai nel repository |
| **P1, P2** | Privacy predefinita più protettiva; controlli lato server |
| **D1, M1** | La data di nascita è un dato privato |
| **L1** | Firebase resta sul piano Spark: in Fase 1 nessuna Cloud Function |

## 4. Incoerenze da risolvere in questa fase

1. Il README dà la Fase 1 come «Completata» e cita un nome vecchio del PDF.
2. `PRIVACY_IMPOSTAZIONI.md` mostra le proposte originali senza indicare che valgono le decisioni
   di `DECISIONI_PRIVACY_APPROVATE.md`.
3. Le modifiche al PDF su A1 (ordine) e F11 (build) non sono ancora segnate come tali.
4. `codemagic.yaml` compila l'Android a ogni push su `main`, contro F11.
5. Supabase, tabella `profiles`:
   - il nome completo è leggibile da tutti gli utenti autenticati (il PDF vuole in pubblico solo
     il nome abbreviato e i dati pubblici separati dai privati);
   - la tabella è legata agli account di Supabase (`auth.users`), ma il login usa Firebase.
6. La build Android parte da sola solo sui push su `main`. Per i rami di lavoro va avviata a mano
   (`workflow_dispatch`, già presente) oppure va aggiunto il ramo di lavoro agli eventi che la
   avviano.

## 5. R1 · Identificazione di ogni APK

Ogni APK prodotto riporta **versione, numero di build, commit e ramo** in tre punti:

| Dove | Esempio |
|---|---|
| Nome dell'artifact e del file APK scaricato da GitHub | `nexus-social-0.1.0+1202-a1b2c3d-fase1-lavori.apk` |
| Riepilogo della build su GitHub Actions | Tabella con versione, numero di build, esecuzione e tentativo, commit completo, ramo, data |
| Dentro l'app: riga «Versione» già esistente in **Profilo → Informazioni** | `0.1.0 (1202) · a1b2c3d · fase1/lavori` |

**Numero di build sempre diverso, anche rieseguendo la stessa build:**
- GitHub assegna a ogni esecuzione un numero progressivo (`run_number`) e a ogni riesecuzione della
  stessa esecuzione un numero di tentativo (`run_attempt`: 1, 2, 3…).
- Il numero di build è `run_number × 100 + run_attempt`. Esempio: esecuzione 12, primo tentativo →
  `1201`; stessa esecuzione rieseguita → `1202`.
- Il numero cresce sempre, quindi Android accetta l'aggiornamento sopra un APK precedente dello
  stesso ramo.
- Se i tentativi superassero 99, la build si ferma con un messaggio chiaro invece di produrre un
  numero già usato.
- Il nome dell'artifact contiene il numero di build, quindi due tentativi non si sovrascrivono.

**Regole:**
- versione, commit e ramo sono inseriti dalla build in automatico (`--dart-define` e
  `--build-number`), mai scritti a mano;
- nel nome del file la `/` del ramo diventa `-`; nell'app il ramo resta scritto per intero;
- non viene aggiunta nessuna nuova schermata: si completa la riga «Versione» che esiste già.

**Verifica automatica (passaggio 1.2):**
- un controllo nella build rilegge dall'APK prodotto numero di build e nome della versione e li
  confronta con quelli attesi;
- un test dell'interfaccia controlla che la riga «Versione» mostri versione, numero di build,
  commit e ramo;
- se un dato manca, la build fallisce.

## 6. R2 · Restyling senza perdite

**Ogni funzione esistente su `main` ha una verifica automatica e un criterio di superamento.**

| N. | Funzione | Verifica automatica | Criterio di superamento |
|---|---|---|---|
| F1 | Cinque sezioni con schermate di anteprima | Test di interfaccia: tocca ogni voce della barra e controlla la schermata aperta | Le 5 voci esistono con i nomi del PDF; ciascuna apre la propria anteprima; nessun errore |
| F2 | Campanella con il foglio delle notifiche | Test di interfaccia esistente («la campanella apre il foglio delle notifiche vuote») | La campanella è visibile su ogni scheda e apre il foglio delle notifiche |
| F3 | Tema chiaro e scuro che segue il telefono, icone di sistema adeguate | Test esistente sulle icone di sistema, più un test che avvia l'app in modalità chiara e scura | In ciascuna modalità l'app usa il tema corrispondente e le icone di sistema restano leggibili |
| F4 | Scelta Privato / Azienda (anteprima) | Test di interfaccia sul Profilo | Entrambe le voci sono presenti e selezionabili come oggi |
| F5 | Indicatori Supabase e Firebase nel Profilo | Test esistente («il profilo mostra i servizi non configurati») più un test con lo stato «configurato» simulato | Ogni stato possibile mostra la propria scritta; nessuna chiave o indirizzo visibile |
| F6 | Diagnostica push (solo debug con Firebase attivo) | Test di interfaccia in due condizioni | Con debug e Firebase la voce c'è e apre la schermata «Diagnostica push»; senza Firebase la voce non compare |
| F7 | Riga Informazioni: versione, fase, privacy | Test di interfaccia sul Profilo (unito alla verifica di R1) | Compaiono «Versione» (con build, commit e ramo), «Fase» e «Privacy: Posizione approssimata» |
| F8 | **Registrazione del dispositivo per le notifiche push** | Test della logica di `PushDeviceRegistry` con Firebase simulato, più i test esistenti delle regole Firestore sui dispositivi (emulatore) | Vedi «Dettaglio F8» |
| F9 | **Avvio dell'app senza chiavi** | Test che avvia `BackendBootstrap` e l'app senza nessuna chiave (`--dart-define` assenti), più il test esistente «senza dart-define i servizi restano spenti» | Nessun errore o crash; Supabase e Firebase risultano «Non configurato»; la barra e tutte e 5 le schede funzionano; la registrazione push non parte |
| F10 | **Anteprima del gestionale admin** | Test di interfaccia che apre `AdminScreen`, più i controlli sui percorsi alternativi | Vedi «Dettaglio F10» |

**Dettaglio F8 · Registrazione del dispositivo push**

| Caso | Criterio di superamento |
|---|---|
| Firebase non configurato | Nessuna chiamata a Firebase; stato finale «idle»; nessun errore |
| Utente non ancora entrato | Stato «in attesa dell'utente»; nessuna scrittura |
| Utente entrato e identificativo del dispositivo disponibile | Un solo documento in `profiles/{uid}/devices/{id}`; stato «collegato» |
| L'identificativo del dispositivo cambia per lo stesso utente | Il vecchio collegamento viene rimosso e il nuovo creato; mai due dispositivi attivi per la stessa installazione |
| Errore di rete | Stato «non riuscito»; il pulsante «Riprova associazione» riprende la registrazione |
| Regole Firestore (emulatore, test esistenti) | Solo il proprietario legge, crea, modifica e cancella i propri dispositivi; la data di creazione non si può alterare |

Per F8 serve poter sostituire Firebase con una versione simulata nei test.

- Se oggi il codice non lo permette, si fa una modifica minima, **senza cambiare il comportamento**.
- La modifica è dichiarata nel commit e coperta dai test qui sopra.

**Isolamento di F8 (vincolante): i test non si collegano mai a Firebase reale e non vi scrivono
mai.**

| Misura | Criterio di superamento |
|---|---|
| Test della logica (Flutter) solo con oggetti simulati in memoria: utente, identificativo del dispositivo, database | Nessuna inizializzazione di Firebase reale nei test; nessuna connessione di rete |
| Test delle regole solo sull'**emulatore locale** di Firestore, con il progetto fittizio `demo-nexus-social` (già usato oggi) | Il prefisso `demo-` impedisce all'emulatore di raggiungere servizi reali; nessun altro identificativo di progetto ammesso |
| I job di test non ricevono i Secrets di Firebase (`GOOGLE_SERVICES_JSON` o altri) né `google-services.json` | Un controllo nel job verifica che le chiavi siano assenti; se ne trova una, il test si ferma con errore |
| Blocco di rete nei test Flutter: qualunque tentativo di connessione esterna fa fallire il test | Zero connessioni uscenti durante i test |
| Le chiavi reali servono solo al job che compila l'APK, separato dai test | Verificato nella configurazione della build |

Lo script `firebase/verify_android_runtime.sh` usa invece Firebase reale.

- Non fa parte dei test automatici e non viene eseguito da me.
- Il titolare può usarlo sul telefono, solo per sua scelta.

**Dettaglio F10 · Anteprima admin e accessi privilegiati**

La verifica copre la schermata e **qualunque percorso alternativo** per eseguire azioni
amministrative o ottenere accessi privilegiati.

*Nell'app:*

| Caso | Criterio di superamento |
|---|---|
| Anteprima | `AdminScreen` mostra solo «Gestionale Admin» in anteprima, senza pulsanti o azioni attive |
| Barra e Profilo | Nessuna voce porta all'anteprima admin |
| Altri punti dell'app | Un controllo automatico sul codice verifica che `AdminScreen` non sia aperta da nessun altro punto (rotte, menu, gesti nascosti, notifiche) |
| Link esterni all'app (deep link) | Un controllo sull'APK prodotto verifica che il manifesto Android abbia solo l'avvio normale (`MAIN`/`LAUNCHER`) e nessun link che apra schermate interne |
| Notifiche push | L'apertura di una notifica non può portare all'anteprima admin |
| Chiavi privilegiate | Un controllo sull'APK verifica che non contenga la chiave `service_role` di Supabase né altre credenziali amministrative |

*Sui server (database temporaneo ed emulatore, mai servizi reali):*

| Caso | Criterio di superamento |
|---|---|
| Supabase: un utente normale tenta di diventare admin, di moderare, di gestire segnalazioni o di modificare dati altrui sfruttando le eccezioni previste per gli admin | Tutto rifiutato: casi E1-E8 di `TEST_SUPABASE_RLS.md` |
| Token con ruoli o permessi falsificati (`admin`, `service_role`) | Rifiutati o trattati come utente normale |
| Firestore: un utente si nomina admin o owner di una chat altrui, o si aggiunge da solo a un gruppo | Rifiutato: test esistenti delle regole, più eventuali nuovi |

Resta anche la verifica già presente nel repository `firebase/verify_android_runtime.sh`, che il
titolare può usare sul telefono reale.

Gli indicatori dei servizi nel Profilo **esistono già** e restano come sono, in base alla regola R2.
La decisione di non aggiungere la schermata «Stato servizi» riguarda solo schermate nuove.

**Regole sui test:**
1. I test F1-F10 si aggiungono e devono essere verdi **sull'app attuale, prima** del restyling
   (passaggio 1.3).
2. Nessun test esistente viene cancellato o indebolito:
   - i 7 test Flutter;
   - i test delle regole Firestore;
   - i test dell'audit.

   Se un test va adattato a una decisione approvata (per esempio l'ordine A1 della barra), il
   commit lo dichiara e il controllo resta almeno altrettanto severo.
3. Il restyling cambia solo aspetto, colori, ordine della barra e scheda iniziale.
4. Dopo ogni passaggio grafico tutti i test devono essere verdi, poi c'è la prova manuale sul
   telefono, voce per voce.

## 7. Passaggi

| N. | Cosa viene realizzato | Come si prova sul telefono Android |
|---|---|---|
| **1.1** | Documentazione: incoerenze 1, 2 e 3; corrispondenza tra le cartelle del codice e quelle del PDF | Leggere i file su GitHub |
| **1.2** | Identificazione dell'APK (R1) e avvio della build anche dal ramo di lavoro (incoerenza 6). Poi build dell'app attuale come punto di partenza | Scaricare l'APK da GitHub: il nome riporta versione, commit e ramo. Installato, in Profilo → Informazioni compaiono gli stessi dati. 5 schede, campanella e tema scuro funzionano |
| **1.3** | Test automatici F1-F10 di non regressione (R2, sez. 6) sull'app attuale, prima di ogni cambio grafico | Spunta verde dei test su GitHub, con l'elenco F1-F10 superati |
| **1.4** | Barra A1 (Chat, Mappa, Scambia centrale arancione, Vendita, Profilo) e apertura su **Chat** | Nuovo APK: l'app si apre su Chat; ordine corretto; pulsante arancione; ogni scheda si apre; tutte le voci della sez. 6 funzionano |
| **1.5** | Tema con lo stile grafico del mockup, chiaro e scuro | Cambiare il tema del telefono: l'app si adegua. Ricontrollare la sez. 6 voce per voce |
| **1.6** | Verifica dei servizi con log e test: controllo dei passaggi già presenti nella build Android («Verifica connessione Supabase», «Verifica Firebase nell'APK») | Su GitHub, nella build, i passaggi di verifica sono verdi; nessuna chiave o dato personale nei log |
| **1.7** | Schema Supabase `0002` **preparato** e provato su un database temporaneo creato dentro GitHub Actions (mai su quello reale), con tutti i test di `TEST_SUPABASE_RLS.md` | Spunta verde dei test su GitHub, con l'elenco dei casi superati. **L'applicazione al database reale richiede un'autorizzazione separata** |
| **1.8** | Revisione delle regole Firestore sulla separazione pubblico/privato; gli 88 test esistenti più eventuali nuovi, sull'emulatore | Spunta verde su GitHub. **Nessuna pubblicazione delle regole senza autorizzazione** |
| **1.9** | `codemagic.yaml` solo per la build iPhone, senza build Android doppie (incoerenza 4) | File pronto nel repository. Collegamento a Codemagic **solo dopo l'autorizzazione**, fatto dal titolare dall'app; poi la build iPhone verde. Installazione su iPhone solo con l'account Apple (Fasi 9-10) |
| **1.10** | Checklist della Fase 1 (`docs/fase1/CHECKLIST.md`) con i criteri del PDF e le regole R1-R3 | APK finale: app installata, barra completa, servizi verificati dai log, versione, commit e ramo visibili. Merge su `main` **solo con autorizzazione esplicita** |

## 8. Cosa NON si fa in questa fase

- Nessuna funzione delle Fasi 2-10: login, profili, mappa, chat, annunci.
- Nessuna nuova schermata «Stato servizi».
- Nessun cambio di nome o di identificativo dell'app.
- Nessuna azione dell'elenco della sezione 2 senza autorizzazione esplicita.
