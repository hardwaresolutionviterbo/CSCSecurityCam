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
| Qualsiasi altra collezione | segnalata come non prevista dalle regole (negata ai client) |

I tipi sono letti dalla rappresentazione tipizzata di Firestore, quindi l'audit distingue
intero e decimale come fanno le regole. I test confrontano, documento per documento, l'esito
dei validatori dell'audit con quello delle regole vere nel Firestore Emulator.

## Come classifica

| Stato | Significato | Gravità |
| --- | --- | --- |
| `compatibile` | conforme alle regole | — |
| `legacy` | formato precedente (senza `schemaVersion: 2`, chat con array `members`): non leggibile dopo il deploy, escluso dalle query | avviso |
| `non_conforme` con `schemaVersion` 2 (anche 2.0) in membri, messaggi, richieste | **compare nelle query senza essere rivalidato** | **bloccante** |
| `non_conforme` chat v2 | viola lo schema che le regole presuppongono | **bloccante** |
| `non_conforme` profilo o dispositivo | non leggibile / non aggiornabile dall'app | avviso |
| `fuori_regole` | collezione non prevista | avviso |
| contatore membri incoerente, owner mancante o multiplo, chat diretta con più di due membri | invarianti delle regole violate | **bloccante** |
| possibile dato privato (email o telefono in un valore di testo) | da verificare | avviso |

Esito: **PASS** (nessuna anomalia), **PASS CON AVVISI**, **BLOCKED** (almeno una anomalia
bloccante: il deploy si ferma).

## Privacy dell'output

- Solo conteggi, codici di anomalia e nomi di campo previsti dallo schema. Un nome di campo
  diverso da un identificatore semplice (per esempio una chiave che contiene una `@` o un
  numero) compare come `<campo_non_standard>`.
- Nessun valore dei documenti: niente email, telefoni, nomi, contenuti dei messaggi, token.
- Identificativi dei documenti anonimizzati con HMAC-SHA256 (`chats/{a1b2c3d4e5f6}/…`). Con un
  sale casuale non sono riconducibili ai documenti; con `AUDIT_ID_SALT` (64 caratteri esadecimali,
  da tenere segreto) chi ha accesso al database può ricalcolarli per trovare un documento.
- Il report JSON viene creato con permessi `0600` e non sovrascrive file esistenti.
- Gli errori riportano solo il tipo di errore, mai i dati.

## Garanzie di sola lettura

1. **IAM**: l'account di servizio ha solo il ruolo **Cloud Datastore Viewer**
   (`roles/datastore.viewer`). Anche un errore nello script non potrebbe scrivere.
2. **Codice**: l'unico accesso a Firestore passa da `readOnly()` in `audit-firestore.mjs`, che
   espone solo `listCollections`, `listDocuments` e `getAll`; batch, transazioni, `bulkWriter` e
   `recursiveDelete` lanciano un errore. Un test verifica che il codice dell'audit non contenga
   chiamate di scrittura.
3. **Opzioni**: `--fix`, `--apply`, `--delete`, `--migrate`, `--write`, `--rollback` e simili
   sono rifiutate prima di qualsiasi accesso.
4. **Progetto**: accetta solo `nexus-social-e3eee` e verifica che le credenziali appartengano a
   quel progetto; si rifiuta se è attivo l'emulatore.
5. **Test**: sull'emulatore il database è identico prima e dopo l'audit.

## Come eseguirlo sul progetto reale (senza incollare chiavi in chat)

### 1. Account di servizio di sola lettura (una volta)

Da [console.cloud.google.com](https://console.cloud.google.com), progetto
**nexus-social-e3eee** (anche da telefono):

1. **IAM e amministrazione → Account di servizio → Crea account di servizio**, nome
   `firestore-audit`.
2. Ruolo: **Cloud Datastore Viewer**. Nessun altro ruolo.
3. Scegli una delle due modalità:
   - **Consigliata, senza chiavi**: Workload Identity Federation per GitHub (pool e provider
     limitati al repository `hardwaresolutionviterbo/Nexus-Social`), poi su GitHub
     *Settings → Secrets and variables → Actions → Variables* crea
     `FIREBASE_AUDIT_WIF_PROVIDER` e `FIREBASE_AUDIT_SERVICE_ACCOUNT`.
   - **Più semplice**: *Chiavi → Aggiungi chiave → JSON*, poi su GitHub
     *Settings → Secrets and variables → Actions → Secrets* crea `FIREBASE_AUDIT_SA_KEY` incollando
     il file. Cancella il file scaricato dal telefono e revoca la chiave dopo l'audit.
4. Su GitHub *Settings → Environments* crea l'ambiente `firestore-audit` (facoltativo: approvatori
   obbligatori).

Con la chiave JSON lo script verifica il progetto leggendo `project_id` dalla chiave. Con
Workload Identity il progetto viene dalla configurazione del workflow; la protezione resta il
ruolo di sola lettura dell'account di servizio.

### 2. Avvio

Il workflow **Firestore Audit (produzione, sola lettura)** si avvia solo a mano e solo dal ramo
predefinito (`main`): GitHub accetta `workflow_dispatch` solo per workflow presenti su `main`.
Dopo il merge: *Actions → Firestore Audit (produzione, sola lettura) → Run workflow*, scrivi
`nexus-social-e3eee` nel campo di conferma. Il riepilogo compare nella pagina dell'esecuzione;
il report anonimo è tra gli artifact per 7 giorni.

### In alternativa: Google Cloud Shell

```bash
git clone https://github.com/hardwaresolutionviterbo/Nexus-Social.git
cd Nexus-Social/firebase/audit
npm ci --omit=dev
gcloud auth application-default login --impersonate-service-account \
  firestore-audit@nexus-social-e3eee.iam.gserviceaccount.com
GOOGLE_CLOUD_PROJECT=nexus-social-e3eee node audit-firestore.mjs \
  --project nexus-social-e3eee --out firestore-audit.report.json
```

L'impersonazione usa i permessi dell'account di sola lettura, non quelli personali.

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

- Gli indici composti non sono verificabili con Admin SDK: vanno controllati in console
  (*Firestore → Indici*). Quelli necessari dalla Fase 4 sono in
  [`../tests/README.md`](../tests/README.md#limiti-noti).
- Il controllo dei «possibili dati privati» cerca email e numeri di telefono nei testi: può dare
  falsi positivi (per esempio uno username fatto di cifre) e non riconosce altri dati personali.
- L'audit legge l'intero database: con molti documenti richiede tempo e consuma letture
  (`reads` nel report).
