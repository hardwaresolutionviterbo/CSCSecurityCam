# Audit Firestore in sola lettura · Nexus Social

Verifica che i dati già presenti in Firestore siano compatibili con
[`../firestore.rules`](../firestore.rules) **prima** del deploy delle regole. È il passo 1
della sequenza in [`../README.md`](../README.md#pubblicazione-delle-regole-deploy).

L'audit **non scrive, non corregge e non migra nulla**.

## Cosa controlla

| Percorso | Controllo (come nelle regole) |
| --- | --- |
| `profiles/{uid}` | `isPublicProfile`: campi ammessi, `username` come Supabase, `publicName` fino a 81 caratteri |
| `profiles/{uid}/devices/{fid}` | `validPushDevice`: un dispositivo non conforme blocca l'aggiornamento dall'app |
| `chats/{chatId}` | `isChat`: schema v2, `memberCount` 1-256, `lastMemberChange`; formato legacy con array `members` |
| `chats/{chatId}/members/{uid}` | `isMembership`: ruolo, permessi, `uid` uguale all'id del documento |
| `chats/{chatId}/messages/{id}` | `isMessage`: payload cifrato in base64, campi e tipi ammessi |
| `messageRequests/{id}` | `isMessageRequest` |
| Ogni chat v2 | membri validi = `memberCount`, un solo owner, chat diretta con al più due membri |
| Ogni sottocollezione `members` **fuori** da `chats/{chatId}` | esposta alla query collection group `/{path=**}/members` se ha `uid` testuale e `schemaVersion` 2 |
| Qualsiasi altra collezione, a qualsiasi profondità | segnalata come non prevista dalle regole (negata ai client) |

I tipi sono letti dalla rappresentazione tipizzata di Firestore, quindi l'audit distingue
intero e decimale come fanno le regole. I test confrontano, documento per documento, l'esito
dei validatori dell'audit con quello delle regole vere nel Firestore Emulator, comprese le query
collection group.

## Come classifica

| Stato | Significato | Gravità |
| --- | --- | --- |
| `compatibile` | conforme alle regole | — |
| `legacy` | formato precedente (senza `schemaVersion: 2`, chat con array `members`): non leggibile dopo il deploy, escluso dalle query | avviso |
| `non_conforme` con `schemaVersion` 2 (anche 2.0) in membri, messaggi, richieste | **compare nelle query senza essere rivalidato** | **bloccante** |
| `members` fuori dalle chat visibile alla query collection group | **restituito alla query, negato alla lettura singola** | **bloccante** |
| `non_conforme` chat v2 | viola lo schema che le regole presuppongono | **bloccante** |
| `non_conforme` profilo o dispositivo | non leggibile / non aggiornabile dall'app | avviso |
| `fuori_regole` | collezione non prevista | avviso |
| contatore membri incoerente, owner mancante o multiplo, chat diretta con più di due membri | invarianti delle regole violate | **bloccante** |
| possibile dato privato (email o telefono in un valore di testo) | da verificare | avviso |

Esiti e codici di uscita:

| Esito | Codice | Significato |
| --- | --- | --- |
| **PASS** | 0 | nessuna anomalia |
| **PASS CON AVVISI** | 10 | solo avvisi |
| **BLOCKED** | 20 | almeno un'anomalia bloccante: il deploy si ferma |
| **INCOMPLETO** | 30 | lettura parziale (errore, timeout, risposta incompleta) o database aggiuntivi non analizzati: **mai PASS** |
| — | 2 | verifica preliminare non superata (progetto, identità, permessi, database): **nessun dato letto** |
| — | 1 | errore imprevisto |

## Privacy dell'output

- Solo conteggi, codici di anomalia e nomi previsti dallo schema. Un nome di campo diverso da un
  identificatore semplice compare come `<campo_non_standard>`; il nome di una collezione non
  prevista compare sempre come `<collezione_non_prevista>`.
- Nessun valore dei documenti: niente email, telefoni, nomi, contenuti dei messaggi, token.
- Identificativi dei documenti anonimizzati con HMAC-SHA256 (`chats/{a1b2c3d4e5f6}/…`). Con un
  sale casuale non sono riconducibili ai documenti; con `AUDIT_ID_SALT` (64 caratteri esadecimali,
  da tenere segreto) chi ha accesso al database può ricalcolarli.
- L'unica identità stampata è l'account di servizio di audit (non è un dato personale).
- Il report JSON viene creato con permessi `0600` e non sovrascrive file esistenti.
- Gli errori riportano solo la fase e il codice, mai i dati.

## Garanzie di sola lettura

1. **IAM**: l'account di servizio `firestore-audit` ha solo il ruolo personalizzato
   `firestoreAuditReader`, con **esattamente** `datastore.databases.list`,
   `datastore.entities.get` e `datastore.entities.list`, e viene **impersonato**, senza chiavi.
2. **Identità verificata**: lo script accetta solo `GOOGLE_APPLICATION_CREDENTIALS` di tipo
   `impersonated_service_account` (Cloud Shell) o `external_account` con impersonazione (Workload
   Identity), e solo per `firestore-audit@nexus-social-e3eee.iam.gserviceaccount.com`. Rifiuta
   credenziali personali, chiavi JSON, altri account, deleghe e credenziali implicite
   dell'ambiente.
3. **Permessi verificati dal vivo**: prima di leggere chiama `testIamPermissions`. Se l'account ha
   anche un solo permesso di scrittura su Firestore, indici, regole o IAM, oppure mancano i
   permessi di lettura, si ferma senza leggere dati.
4. **Database verificati**: elenca i database del progetto, analizza `(default)` e segnala gli
   altri (esito INCOMPLETO).
5. **Codice**: l'unico accesso a Firestore passa da `readOnly()` in `audit-firestore.mjs`, che
   espone solo `listCollections`, `listDocuments` e `getAll`; batch, transazioni, `bulkWriter` e
   `recursiveDelete` lanciano un errore. Un test verifica che il codice dell'audit non contenga
   chiamate di scrittura.
6. **Opzioni**: `--fix`, `--apply`, `--delete`, `--migrate`, `--write`, `--rollback` e simili
   sono rifiutate prima di qualsiasi accesso.
7. **Progetto**: accetta solo `nexus-social-e3eee` e si rifiuta se è attivo l'emulatore.
8. **Test**: sull'emulatore il database è identico prima e dopo l'audit.

## Come eseguirlo sul progetto reale: Google Cloud Shell (consigliato)

Nessuna chiave: Google rilascia token temporanei per l'account di sola lettura.

### 1. e 2. Configurazione e verifiche: script guidati

Ruolo, account, autorizzazioni e verifiche si preparano con gli script di
[`iam/`](iam/README.md):

1. `bash 01-verifica.sh`: sola lettura;
2. `bash 02-configura.sh`: modifica IAM, solo dopo una verifica senza problemi e la tua conferma;
3. `bash 03-prova-impersonazione.sh`: permessi effettivi su **tutti** i permessi verificabili
   del progetto; deve risultare che l'account ha esattamente i 3 permessi previsti.

Prima di ogni audit ripeti `01-verifica.sh` e `03-prova-impersonazione.sh`.

### Limiti del controllo dei permessi

Anche il controllo più completo vede i permessi **sul progetto**, compresi quelli ereditati da
cartella e organizzazione. Non vede:

- permessi concessi su singole risorse (per esempio il ruolo Token Creator su un *altro* account di
  servizio);
- permessi su altri progetti;
- ruoli ricevuti tramite gruppi Google.

Per questo la sola lettura si basa su più controlli insieme:

- account dedicato con il solo ruolo personalizzato (controllato da `01-verifica.sh`);
- nessuna chiave;
- permessi effettivi controllati con `03-prova-impersonazione.sh`;
- controllo dei permessi dal vivo dentro l'audit;
- codice che espone solo metodi di lettura.

### 3. Audit

```bash
SA=firestore-audit@nexus-social-e3eee.iam.gserviceaccount.com
gcloud auth application-default login --impersonate-service-account=$SA
git clone --branch audit/firestore-readonly --depth 1 https://github.com/hardwaresolutionviterbo/Nexus-Social.git
cd Nexus-Social/firebase/audit
node -v                          # serve v22 o superiore; altrimenti: nvm install 24 && nvm use 24
npm ci --omit=dev
mkdir -p reports
GOOGLE_APPLICATION_CREDENTIALS=$HOME/.config/gcloud/application_default_credentials.json \
  node audit-firestore.mjs --project nexus-social-e3eee \
  --out reports/firestore-audit-$(date +%Y%m%d-%H%M).json
echo "codice di uscita: $?"
gcloud auth application-default revoke
```

`GOOGLE_APPLICATION_CREDENTIALS` va indicato esplicitamente: senza, in Cloud Shell le librerie
Google userebbero le tue credenziali personali, che lo script comunque rifiuta.

### 4. Come riconosci che è tutto corretto

Le prime righe del riepilogo devono essere:

```text
Audit Firestore in sola lettura — produzione (sola lettura)
Progetto: nexus-social-e3eee
Identità: firestore-audit@nexus-social-e3eee.iam.gserviceaccount.com (impersonated_service_account)
Database analizzato: (default)
Altri database non analizzati: nessuno
```

Se compare «Verifica preliminare non superata» (codice 2), l'audit si è fermato **prima** di
leggere dati: il messaggio dice se il problema è il progetto, l'identità, i permessi o il
database.

### 5. Dove trovi il report

- Il riepilogo (solo conteggi e codici) nel terminale.
- Il report JSON anonimo in `firebase/audit/reports/`; per scaricarlo: menu ⋮ di Cloud Shell →
  **Scarica**. Non condividerlo fuori dal progetto.

## In alternativa: GitHub Actions con Workload Identity Federation

Il workflow **Firestore Audit (produzione, sola lettura)** usa solo Workload Identity Federation,
senza chiavi. Richiede di configurare in Google Cloud un pool e un provider di identità per GitHub,
con condizione sugli attributi limitata al repository `hardwaresolutionviterbo/Nexus-Social`, e il
permesso `roles/iam.workloadIdentityUser` sull'account `firestore-audit` per quel repository; su
GitHub le variabili `FIREBASE_AUDIT_WIF_PROVIDER` e `FIREBASE_AUDIT_SERVICE_ACCOUNT`, e l'ambiente
`firestore-audit` con approvatori obbligatori (altrimenti GitHub lo crea senza protezioni al primo
avvio).

GitHub permette l'avvio manuale solo per workflow presenti su `main`, quindi questa strada richiede
il merge del ramo. Il riepilogo compare nella pagina dell'esecuzione, il report anonimo è tra gli
artifact per 7 giorni.

## Test

```bash
cd firebase/audit
npm ci
npm run check
npm test   # Firestore Emulator (Node ≥ 24.12, Java 21)
```

Il workflow **Firestore Audit Tests** li esegue a ogni modifica di `firebase/audit/`,
`firebase/firestore.rules` o `firebase.json`, senza segreti né accesso a progetti reali.

## Limiti

- **Nessuna fotografia istantanea**: le letture avvengono in momenti diversi. Scritture durante
  l'audit possono produrre incoerenze apparenti (per esempio sui contatori dei membri): eseguirlo
  senza traffico sull'app.
- **Indici**: non verificati dall'audit; vanno controllati in console (*Firestore → Indici*).
  Quelli necessari dalla Fase 4 sono in [`../tests/README.md`](../tests/README.md#limiti-noti).
- **Un solo database**: viene analizzato `(default)`, l'unico usato dall'app; gli altri vengono
  solo segnalati e rendono l'esito INCOMPLETO.
- **Lunghezza dei testi**: contata in caratteri Unicode; con emoji vicino ai limiti il conteggio
  potrebbe differire da quello delle regole.
- **Rappresentazione tipizzata**: usa un campo interno di firebase-admin (versione fissata
  14.5.1). Se non fosse disponibile, l'audit si ferma con esito INCOMPLETO invece di classificare
  male.
- **Dati privati**: il controllo cerca solo email e numeri di telefono nei testi; può dare falsi
  positivi (per esempio uno username fatto di cifre) e non riconosce altri dati personali.
- **Classificazione prudente**: un messaggio v2 non conforme in una chat senza membri validi è
  bloccante anche se nessuno può interrogarlo.
- **Primo avvio con credenziali reali**: il percorso impersonazione → firebase-admin non è
  verificabile sull'emulatore; se le credenziali non venissero accettate, l'audit si ferma con
  codice 2 senza leggere dati.
- **Costi**: ogni documento letto conta come lettura Firestore (`reads` nel report).
