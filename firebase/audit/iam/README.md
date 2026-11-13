# Account di audit Firestore: preparazione guidata

L'account `firestore-audit` riceve un solo ruolo personalizzato, `firestoreAuditReader`, con
**esattamente** questi 3 permessi:

| Permesso | Serve a (documentazione Firestore) |
|---|---|
| `datastore.entities.get` | `getAll` (batchGet) e `listDocuments` (documents.list) |
| `datastore.entities.list` | `listDocuments` e `listCollections` (listCollectionIds) |
| `datastore.databases.list` | elenco dei database, controllato prima della lettura |

`roles/datastore.viewer` non viene più usato: comprende più permessi del necessario, tra cui
`datastore.databases.get` (avvio di transazioni), statistiche e metadati, e Google può
aggiungerne altri. Un ruolo personalizzato, invece, Google non lo modifica da solo.

## Gli script

| Script | Cosa fa | Modifica qualcosa? | Privilegi che ti servono |
|---|---|---|---|
| `01-verifica.sh` | Controlla progetto, gerarchia, ruoli (leggendo i permessi di ogni ruolo assegnato), impersonazione, chiavi, condizioni, API, supporto dei 3 permessi, definizione esatta del ruolo | No | Viewer o Security Reviewer sul progetto (Owner basta), più la lettura dei criteri di cartelle e organizzazione se il progetto ne ha |
| `01-verifica.sh --finale` | Come sopra, ma **tutto** deve essere presente ed esatto | No | Gli stessi |
| `02-configura.sh` | Mostra il riepilogo, chiede conferma, crea il ruolo con esattamente 3 permessi, crea l'account, gli assegna il ruolo, ti permette di impersonarlo, poi esegue la verifica finale | **Sì (IAM)** | Owner, oppure Role Administrator + Service Account Admin + Project IAM Admin |
| `03-prova-impersonazione.sh` | Controlla i permessi effettivi dell'account su **tutti** i permessi verificabili del progetto | No | Token Creator sull'account (lo assegna il passo 2) |

Nessuno degli script attiva API, legge dati Firestore, crea chiavi o stampa email di persone o token.

### Come vengono classificati i permessi

La classificazione è esplicita (vedi `permessi.py`):

- **scrittura**: azioni come `create`, `update`, `delete`, `setIamPolicy`, `getAccessToken`,
  `signBlob`, oppure che iniziano con `create`, `update`, `delete`, `set`...;
- **lettura**: elenco chiuso (`get`, `list`, `getMetadata`, `getIamPolicy`, `listTagBindings`,
  `listEffectiveTags`, `listEffectiveTagBindings`);
- **sconosciuto**: tutto il resto. Produce un AVVISO da valutare a mano e non viene mai trattato
  come scrittura.

Il ruolo deve comunque contenere esattamente i 3 permessi: qualsiasi permesso in più, anche di
lettura o sconosciuto, rende la configurazione non conforme.

### Privilegi pericolosi (impersonazione, chiavi, modifica del ruolo)

La verifica legge con `gcloud iam roles describe` i permessi di **ogni** ruolo assegnato nel
progetto, nelle cartelle e nell'organizzazione: ruoli predefiniti, ruoli personalizzati del
progetto e ruoli personalizzati dell'organizzazione. Un ruolo che non si riesce a leggere
interrompe la verifica.

Un ruolo è pericoloso se contiene uno di questi permessi (elenco in `permessi.py`):
`iam.serviceAccounts.getAccessToken`, `getOpenIdToken`, `signBlob`, `signJwt`,
`implicitDelegation`, `actAs`, `setIamPolicy`, `iam.serviceAccountKeys.create`,
`iam.roles.update`, `resourcemanager.projects/folders/organizations.setIamPolicy`. Sono
pericolosi comunque, anche senza leggerne i permessi, tra gli altri: `roles/owner`, `roles/editor`,
`roles/iam.serviceAccountTokenCreator`, `roles/iam.serviceAccountOpenIdTokenCreator`,
`roles/iam.serviceAccountKeyAdmin`, `roles/iam.roleAdmin`, `roles/iam.organizationRoleAdmin`,
`roles/iam.serviceAccountUser`, `roles/iam.serviceAccountAdmin`, `roles/resourcemanager.projectIamAdmin`,
`roles/firebase.admin`.

Chiunque abbia un ruolo pericoloso, a parte te, è un **PROBLEMA** (non un avviso), salvo che tu lo
abbia approvato esplicitamente. Sono PROBLEMA anche gruppi, domini e identità federate con un
ruolo qualsiasi: non si può controllare se comprendono l'account di audit.

### Identità approvate

Copia `identita-approvate.esempio.txt` come `identita-approvate.txt` e lascia solo le righe che
hai controllato (una per membro IAM, per esempio `serviceAccount:...@appspot.gserviceaccount.com`).
La riga `agenti-google-del-progetto` approva gli agenti di servizio gestiti da Google per questo
progetto. Il file non va su GitHub (è in `.gitignore`); senza il file nessuna identità è approvata.
Le approvazioni non valgono mai sull'account di audit: lì è ammesso solo Token Creator per te.

## Passo 0: scaricare gli script (Cloud Shell)

```bash
cd ~ && rm -rf nexus-audit
git clone -b audit/firestore-readonly https://github.com/hardwaresolutionviterbo/Nexus-Social.git nexus-audit
cd nexus-audit && git checkout -q --detach COMMIT && git log -1 --format=%h
cd firebase/audit/iam
```

`COMMIT` è il commit indicato da Claude. L'ultimo comando prima di `cd` deve stamparlo.

## Passo 1: verifica preliminare (sola lettura)

```bash
bash 01-verifica.sh; echo "CODICE: $?"
```

- `CODICE: 0` con `0 problemi`: si può passare al passo 2.
- `CODICE: 1`: ci sono righe PROBLEMA.
- `CODICE: 2`: un elemento non è verificabile (lettura negata, gerarchia illeggibile, ruolo
  assegnato illeggibile...), quindi la verifica è interrotta e non è un esito positivo.

In modalità preliminare account, ruolo e binding previsti possono ancora mancare (compaiono come
INFO). Tutto il resto deve essere già pulito: API attive, nessun ruolo non previsto sull'account,
nessuna identità non approvata con privilegi pericolosi, nessuna chiave, account e ruolo (se
esistono) attivi e con la definizione esatta.

La sezione 9 mostra, per ciascuno dei 3 permessi, lo stage e il supporto nei ruoli personalizzati:

- **SUPPORTED** è l'unico valore accettato. Se il campo `customRolesSupportLevel` manca, vale
  SUPPORTED, perché nell'API IAM SUPPORTED è il valore predefinito e i valori predefiniti
  vengono omessi.
- `TESTING` o `NOT_SUPPORTED` danno PROBLEMA.

## Passo 2: configurazione (modifica IAM)

Solo dopo un passo 1 senza PROBLEMA:

```bash
bash 02-configura.sh; echo "CODICE: $?"
```

1. Controlla che `iamcredentials.googleapis.com` e `cloudresourcemanager.googleapis.com` siano
   attive. **Non le attiva**: se ne manca una si ferma con `CODICE: 3`, spiega a cosa serve e
   indica il comando da eseguire a parte, solo dopo una tua autorizzazione separata.
2. Ripete la verifica preliminare.
3. Mostra il riepilogo: progetto, il tuo account (mascherato, per esempio `ma•••@gmail.com`),
   l'account di servizio e il ruolo (da creare o già esistenti), i 3 permessi, i 2 binding che
   verranno aggiunti, le API da attivare (nessuna).
4. Chiede di scrivere `nexus-social-e3eee`, esattamente. Qualsiasi altra risposta: nessuna modifica.
5. Esegue questi comandi, ognuno solo se serve:

```bash
gcloud iam roles create firestoreAuditReader --project=nexus-social-e3eee \
  --title="Firestore audit - sola lettura" \
  --description="Solo lettura documenti Firestore per l'audit. Nessuna scrittura." \
  --permissions=datastore.databases.list,datastore.entities.get,datastore.entities.list \
  --stage=GA --format=none
gcloud iam service-accounts create firestore-audit --project=nexus-social-e3eee \
  --display-name="Firestore audit (sola lettura)" \
  --description="Solo il ruolo firestoreAuditReader. Nessuna chiave. Impersonato solo dal proprietario." \
  --format=none
gcloud projects add-iam-policy-binding nexus-social-e3eee \
  --member="serviceAccount:firestore-audit@nexus-social-e3eee.iam.gserviceaccount.com" \
  --role="projects/nexus-social-e3eee/roles/firestoreAuditReader" --condition=None --format=none
gcloud iam service-accounts add-iam-policy-binding \
  firestore-audit@nexus-social-e3eee.iam.gserviceaccount.com --project=nexus-social-e3eee \
  --member="user:TUA_EMAIL" --role="roles/iam.serviceAccountTokenCreator" \
  --condition=None --format=none
```

6. Esegue `01-verifica.sh --finale`.

- Se il ruolo esiste già con una definizione diversa, eliminato o disattivato, lo script si ferma
  senza modificarlo.
- La tua email viene letta da gcloud: non devi scriverla, e non viene mai stampata per intero.
- Se un passo fallisce, lo script si ferma subito e stampa le operazioni **completate**, quella
  **non riuscita** e quelle **non eseguite**. Non fa rollback: rieseguendolo, i passi già fatti
  non vengono duplicati.
- I file temporanei (solo testi di errore) vengono cancellati anche dopo un errore o Ctrl+C.
- Se la verifica finale non è pulita, esce con codice 1 e stampa "CONFIGURAZIONE NON VERIFICATA".

### Verifica finale (`01-verifica.sh --finale`)

Esce con codice 0 **solo se** valgono tutte queste condizioni:

- `firestore-audit` esiste ed è attivo;
- `firestoreAuditReader` esiste, non è eliminato né disattivato e contiene esattamente i 3 permessi;
- l'account ha esattamente quel ruolo sul progetto, senza condizioni, e nessun altro ruolo nel
  progetto, nelle cartelle o nell'organizzazione;
- sull'account c'è solo Token Creator per te, senza condizioni;
- nessun'altra identità, nel progetto o sopra, può impersonare l'account, crearne chiavi,
  cambiarne i permessi o modificare il ruolo, salvo quelle approvate;
- nessun gruppo, dominio o identità federata non approvati con ruoli nel progetto o sopra;
- nessuna chiave creata a mano;
- `iamcredentials`, `cloudresourcemanager` e `firestore` attive.

Qualsiasi condizione mancante dà PROBLEMA (`CODICE: 1`); qualsiasi elemento non verificabile
interrompe la verifica (`CODICE: 2`).

## Passo 3: permessi effettivi (dopo 2-5 minuti)

```bash
bash 03-prova-impersonazione.sh; echo "CODICE: $?"
```

1. Chiede a Google l'elenco completo dei permessi verificabili sul progetto.
2. Ottiene un token temporaneo dell'account.
3. Chiede, a blocchi di 100, quali di quei permessi l'account ha.

Esiti:

- `0`: esattamente i 3 permessi previsti;
- `1`: permessi mancanti o in più. Quelli di scrittura sono PROBLEMA, gli altri AVVISO, ma
  l'esito è comunque non conforme;
- `2`: verifica impossibile;
- `3`: verifica incompleta, perché alcuni permessi sono stati rifiutati da Google come non
  validi. Vengono elencati.

## Come sai che l'account ha solo i privilegi previsti

Devono valere tutte e due le condizioni:

- `01-verifica.sh --finale` finisce con `CODICE: 0`;
- `03-prova-impersonazione.sh` finisce con `CODICE: 0`.

Cosa `01-verifica.sh` **non** può vedere (per questo serve anche il passo 3, che controlla i
permessi effettivi sul progetto):

- appartenenza ai gruppi Google: per sicurezza ogni gruppo con un ruolo blocca l'esito, salvo
  approvazione;
- permessi concessi su singole risorse diverse dal progetto (per esempio un bucket);
- permessi su altri progetti.

## Test

`node --test iam-scripts.test.mjs` prova i tre script con `gcloud` e `curl` simulati
(`test-stubs/`), senza contattare Google: binding mancanti e aggiuntivi, account e ruolo
disattivati, chiavi, identità aggiuntive, gruppi, privilegi ereditati, ruoli personalizzati,
permessi sconosciuti, API disattivate, conferma errata, interruzione a metà configurazione e con
Ctrl+C. Gli stessi test girano in GitHub Actions.
