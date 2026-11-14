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
| **R1 · Identificazione dell'APK** (14/11/2026) | Ogni APK riporta chiaramente **versione, commit Git e ramo** di provenienza (sez. 5) |
| **R2 · Restyling senza perdite** (14/11/2026) | Il restyling non elimina, non altera e non compromette nessuna funzione esistente (sez. 6) |
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

Ogni APK prodotto riporta **versione, commit e ramo** in tre punti:

| Dove | Esempio |
|---|---|
| Nome dell'artifact e del file APK scaricato da GitHub | `nexus-social-0.1.0+12-a1b2c3d-fase1-lavori.apk` |
| Riepilogo della build su GitHub Actions | Tabella con versione, commit completo, ramo, data |
| Dentro l'app: riga «Versione» già esistente in **Profilo → Informazioni** | `0.1.0 (12) · a1b2c3d · fase1/lavori` |

Dettagli:
- il numero di build (`+12`) cresce a ogni esecuzione, così un APK nuovo si distingue sempre da
  uno vecchio;
- commit e ramo sono inseriti dalla build in automatico (`--dart-define`), mai scritti a mano;
- non viene aggiunta nessuna nuova schermata: si completa la riga «Versione» che esiste già.

## 6. R2 · Restyling senza perdite

**Funzioni esistenti su `main` che devono restare intatte:**

| Funzione | Dove |
|---|---|
| Cinque sezioni con schermate di anteprima | Barra in basso |
| Campanella con il foglio delle notifiche | Barra in alto |
| Tema chiaro e scuro che segue il telefono, icone di sistema adeguate | Tutta l'app |
| Scelta Privato / Azienda (anteprima) | Profilo |
| Indicatori Supabase e Firebase già esistenti («Non configurato», ecc.) | Profilo |
| Diagnostica push (solo nelle build di debug con Firebase attivo) | Profilo |
| Riga Informazioni: versione, fase, privacy | Profilo |
| Registrazione del dispositivo per le notifiche push | Avvio, servizi |
| Avvio dell'app anche senza chiavi (servizi spenti senza errori) | Avvio |
| Gestionale admin (anteprima) | `features/admin` |

Gli indicatori dei servizi nel Profilo **esistono già**: restano come sono, in base alla regola R2.
La decisione di non aggiungere la schermata «Stato servizi» riguarda solo schermate nuove.

**Come si garantisce:**
1. Prima del restyling:
   - si registra l'elenco delle funzioni qui sopra;
   - si confermano verdi i 7 test esistenti (4 di interfaccia, 3 di configurazione).
2. Si aggiungono test che bloccano le regressioni:
   - ogni scheda si apre;
   - la campanella funziona;
   - gli indicatori, la diagnostica push e la riga «Versione» sono presenti;
   - tema chiaro e scuro.
3. Restyling: cambiano solo aspetto, colori, ordine della barra e scheda iniziale.
4. Tutti i test devono restare verdi. Nessun test esistente viene cancellato o indebolito: se uno
   va adattato (per esempio all'ordine nuovo della barra), lo si dichiara nel commit.
5. Prova manuale sul telefono con la tabella qui sopra, voce per voce.

## 7. Passaggi

| N. | Cosa viene realizzato | Come si prova sul telefono Android |
|---|---|---|
| **1.1** | Documentazione: incoerenze 1, 2 e 3; corrispondenza tra le cartelle del codice e quelle del PDF | Leggere i file su GitHub |
| **1.2** | Identificazione dell'APK (R1) e avvio della build anche dal ramo di lavoro (incoerenza 6). Poi build dell'app attuale come punto di partenza | Scaricare l'APK da GitHub: il nome riporta versione, commit e ramo. Installato, in Profilo → Informazioni compaiono gli stessi dati. 5 schede, campanella e tema scuro funzionano |
| **1.3** | Test di non regressione (R2, sez. 6, punti 1-2) sull'app attuale, prima di ogni cambio grafico | Spunta verde dei test su GitHub |
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
