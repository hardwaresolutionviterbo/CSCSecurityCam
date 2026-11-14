# Fase 1 · Piano operativo definitivo

Stato: **piano approvato nei contenuti, lavori NON avviati.** L'avvio richiede un'autorizzazione
separata del titolare.

Riferimento: `Nexus_Social_Piano_di_Lavoro_COMPLETO_AGGIORNATO.pdf`, «FASE 1 – Fondamenta
tecniche» (SHA-256 `7da25d52be13b242d9e0db3b87c740212b2d80fb7c0d4dd75d8e7e541b8561eb`).

## 1. Obiettivo (dal PDF)

Avere un'app vuota che si installa sul telefono e si collega ai server.

> Cosa devi riuscire a provare sul telefono: l'app si installa sul telefono e mostra la barra di
> navigazione con le schermate vuote e i servizi collegati.

Requisiti del PDF per la Fase 1:

| Requisito | Situazione su `main` (`5e32045`) |
|---|---|
| Progetto Flutter unico per Android e iPhone su GitHub | Presente |
| Cartelle: account, chat, mappa, annunci, vendita, admin | Presenti con nomi in inglese (`profile`, `chat`, `map`, `listings`, `sale`, `admin`): corrispondenza da documentare |
| Collegare Firebase (login, chat, notifiche) e Supabase (annunci, distanza, gestionale) | Presente, attivo quando ci sono le chiavi |
| Codemagic per ottenere l'app a ogni modifica | `codemagic.yaml` presente ma non collegato; da allineare a F11 |
| Tema, modalità scura, barra in basso con campanella | Presente; ordine della barra da allineare ad A1 |
| Regole di sicurezza e separazione dati pubblici/privati | Firestore presente con test; Supabase da correggere (sez. 3) |

Gran parte del codice esiste già: questa fase **completa e allinea** il lavoro esistente al PDF e
alle decisioni della Fase 0. Non lo rifà da zero.

## 2. Decisioni che valgono per la Fase 1

| Decisione | Effetto |
|---|---|
| **A1** | Barra: **Chat, Mappa, Scambia** (pulsante centrale arancione), **Vendita, Profilo**; stile del mockup; niente Home né Community. **Modifica dell'ordine del PDF** (Mappa, Chat, …) approvata dal titolare |
| **Schermata iniziale** (14 novembre 2026) | All'apertura l'app mostra **Chat**, come WhatsApp (D4) |
| **Verifica dei servizi** (14 novembre 2026) | **Nessuna schermata tecnica «Stato servizi».** Firebase e Supabase si verificano solo con i log delle build e i test automatici |
| **F11** | Android su GitHub Actions; Codemagic solo per iPhone. **Modifica rispetto al PDF** (che indica Codemagic per le build) approvata dal titolare |
| **F8** | Identificativo `com.nexussocial.app` invariato in questa fase; cambio entro la Fase 9 |
| **F10** | Chiavi solo nei Secrets di GitHub e nelle variabili di Codemagic, mai nel repository |
| **P1, P2** | Privacy predefinita più protettiva; controlli lato server |
| **D1, M1** | La data di nascita è un dato privato: nello schema va nella parte privata |
| **L1** | Il progetto Firebase resta sul piano Spark: in Fase 1 nessuna Cloud Function |

## 3. Incoerenze da risolvere in questa fase

1. Il README dà la Fase 1 come «Completata» e cita un nome vecchio del PDF.
2. `PRIVACY_IMPOSTAZIONI.md` mostra le proposte originali senza indicare che valgono le decisioni
   di `DECISIONI_PRIVACY_APPROVATE.md`.
3. Le modifiche al PDF su A1 (ordine) e F11 (build) non sono ancora segnate come tali.
4. `codemagic.yaml` compila l'Android a ogni push su `main`, contro F11.
5. Supabase, tabella `profiles`:
   - il nome completo è leggibile da tutti gli utenti autenticati (il PDF vuole in pubblico solo
     il nome abbreviato e i dati pubblici separati dai privati);
   - la tabella è legata agli account di Supabase (`auth.users`), ma il login usa Firebase.

## 4. Passaggi

Regole per ogni passaggio:
- lavoro su un ramo dedicato, mai su `main`;
- un commit per passaggio;
- alla fine di ogni passaggio mi fermo per la verifica del titolare.

| N. | Cosa viene realizzato | Come si prova sul telefono Android |
|---|---|---|
| **1.1** | Documentazione: incoerenze 1, 2 e 3; corrispondenza tra le cartelle del codice e quelle del PDF | Leggere i file su GitHub |
| **1.2** | Build dell'app così com'è, dal ramo di lavoro, come punto di partenza | Scaricare l'APK da GitHub Actions, installarlo e aprirlo: 5 schede, campanella, modalità scura |
| **1.3** | Barra A1 (Chat, Mappa, Scambia centrale arancione, Vendita, Profilo) e apertura su **Chat** | Nuovo APK: l'app si apre su Chat; ordine corretto; pulsante centrale arancione; ogni scheda si apre |
| **1.4** | Tema con lo stile grafico del mockup, chiaro e scuro | Cambiare il tema del telefono: l'app si adegua; colori e barra come nel mockup |
| **1.5** | Verifica dei servizi con log e test: controllo dei passaggi già presenti nella build Android («Verifica connessione Supabase», «Verifica Firebase nell'APK»), che non stampano mai chiavi né dati personali | Su GitHub, nella build, i passaggi di verifica sono verdi |
| **1.6** | Schema Supabase `0002` preparato e testato: profili legati all'utente Firebase; dati privati (nome completo, data di nascita) separati da quelli pubblici (nome abbreviato, @username, avatar, bio, link social) | Spunta verde dei test su GitHub. **L'applicazione al database reale richiede un'autorizzazione separata** |
| **1.7** | Revisione delle regole Firestore sulla separazione pubblico/privato; gli 88 test esistenti più eventuali nuovi | Spunta verde su GitHub. **Nessuna pubblicazione delle regole senza autorizzazione** |
| **1.8** | `codemagic.yaml` solo per la build iPhone, senza build Android doppie | Dopo l'autorizzazione, il titolare collega il repository dall'app Codemagic e vede la build iPhone verde. Installazione su iPhone solo con l'account Apple (Fasi 9-10) |
| **1.9** | Checklist della Fase 1 (`docs/fase1/CHECKLIST.md`) con i criteri del PDF | APK finale: app installata, barra completa, servizi verificati dai log. Merge su `main` **solo con autorizzazione esplicita** |

## 5. Cosa NON si fa in questa fase

- Nessuna funzione delle Fasi 2-10: login, profili, mappa, chat, annunci.
- Nessuna schermata tecnica «Stato servizi».
- Nessun cambio di nome o di identificativo dell'app.
- Nessun piano a pagamento (Blaze o Codemagic) e nessun abbonamento.

Senza un'autorizzazione esplicita e separata del titolare, non si fa nulla di quanto segue:
- scritture su Firebase, Supabase, Firestore o IAM;
- deploy;
- collegamenti a servizi;
- merge su `main`.
