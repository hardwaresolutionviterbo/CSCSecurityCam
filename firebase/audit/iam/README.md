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
| `01-verifica.sh` | Controlla progetto, gerarchia, ruoli, impersonazione, chiavi, condizioni, regole di divieto, API, supporto dei 3 permessi nei ruoli personalizzati, definizione esatta del ruolo | No | Viewer o Security Reviewer sul progetto (Owner basta) |
| `02-configura.sh` | Attiva 2 API se spente, crea il ruolo con esattamente 3 permessi, crea l'account, gli assegna il ruolo, ti permette di impersonarlo | **Sì (IAM)** | Owner, oppure Role Administrator + Service Account Admin + Project IAM Admin (+ Service Usage Admin per le API) |
| `03-prova-impersonazione.sh` | Controlla i permessi effettivi dell'account su **tutti** i permessi verificabili del progetto | No | Token Creator sull'account (lo assegna il passo 2) |

Nessuno dei tre legge dati Firestore, crea chiavi o stampa email di persone o token.

### Come vengono classificati i permessi

La classificazione è esplicita (vedi `permessi.py`):

- **scrittura**: azioni come `create`, `update`, `delete`, `setIamPolicy`, `getAccessToken`,
  `signBlob`, oppure che iniziano con `create`, `update`, `delete`, `set`...;
- **lettura**: elenco chiuso (`get`, `list`, `getMetadata`, `getIamPolicy`, `listTagBindings`,
  `listEffectiveTags`, `listEffectiveTagBindings`);
- **sconosciuto**: tutto il resto. Produce un AVVISO da valutare a mano e non viene mai trattato
  come scrittura.

Il ruolo deve comunque contenere esattamente i 3 permessi: qualsiasi permesso in più, anche di
lettura, rende la configurazione non conforme.

## Passo 0: scaricare gli script (Cloud Shell)

```bash
cd ~ && rm -rf nexus-audit
git clone -b audit/firestore-readonly https://github.com/hardwaresolutionviterbo/Nexus-Social.git nexus-audit
cd nexus-audit && git checkout -q --detach COMMIT && git log -1 --format=%h
cd firebase/audit/iam
```

`COMMIT` è il commit indicato da Claude. L'ultimo comando prima di `cd` deve stamparlo.

## Passo 1: verifica (sola lettura)

```bash
bash 01-verifica.sh; echo "CODICE: $?"
```

- `CODICE: 0` con `0 problemi`: si può passare al passo 2.
- `CODICE: 1`: ci sono righe PROBLEMA.
- `CODICE: 2`: una lettura è stata negata, quindi la verifica è interrotta e non è un esito positivo.

La sezione 9 mostra, per ciascuno dei 3 permessi, lo stage e il supporto nei ruoli personalizzati:

- **SUPPORTED** è l'unico valore accettato. Se il campo `customRolesSupportLevel` manca, vale
  SUPPORTED, perché nell'API IAM SUPPORTED è il valore predefinito e i valori predefiniti
  vengono omessi.
- `TESTING` o `NOT_SUPPORTED` danno PROBLEMA.

La sezione 9 elenca anche i permessi `datastore.schemas.*` che esistono davvero sul progetto. La
sezione 10 controlla la definizione esatta del ruolo, se esiste già.

## Passo 2: configurazione (modifica IAM)

Solo dopo un passo 1 senza PROBLEMA:

```bash
bash 02-configura.sh; echo "CODICE: $?"
```

Ripete la verifica e poi chiede di scrivere `nexus-social-e3eee`, esattamente. Dopo la conferma
esegue questi comandi, ognuno solo se serve:

```bash
gcloud services enable iamcredentials.googleapis.com --project=nexus-social-e3eee
gcloud services enable cloudresourcemanager.googleapis.com --project=nexus-social-e3eee
gcloud iam roles create firestoreAuditReader --project=nexus-social-e3eee \
  --title="Firestore audit - sola lettura" \
  --description="Solo lettura documenti Firestore per l'audit. Nessuna scrittura." \
  --permissions=datastore.databases.list,datastore.entities.get,datastore.entities.list \
  --stage=GA
gcloud iam service-accounts create firestore-audit --project=nexus-social-e3eee \
  --display-name="Firestore audit (sola lettura)"
gcloud projects add-iam-policy-binding nexus-social-e3eee \
  --member="serviceAccount:firestore-audit@nexus-social-e3eee.iam.gserviceaccount.com" \
  --role="projects/nexus-social-e3eee/roles/firestoreAuditReader" --condition=None --format=none
gcloud iam service-accounts add-iam-policy-binding \
  firestore-audit@nexus-social-e3eee.iam.gserviceaccount.com --project=nexus-social-e3eee \
  --member="user:TUA_EMAIL" --role="roles/iam.serviceAccountTokenCreator" \
  --condition=None --format=none
```

- Se il ruolo esiste già con una definizione diversa o è eliminato, lo script si ferma senza
  modificarlo.
- Se Google rifiutasse un permesso nel ruolo personalizzato, `roles create` fallisce e lo script
  si ferma.
- La tua email viene letta da gcloud: non devi scriverla.
- Alla fine lo script ripete la verifica. Se non è pulita, esce con codice 1 e stampa
  "CONFIGURAZIONE NON VERIFICATA".

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

Devono valere tutte e tre le condizioni:

- `01-verifica.sh` finisce con `CODICE: 0`;
- `03-prova-impersonazione.sh` finisce con `CODICE: 0`;
- nella sezione 5 della verifica non compaiono persone che non conosci.

Cosa la verifica **non** può vedere:

- ruoli ricevuti tramite gruppi Google: non aggiungere l'account a nessun gruppo;
- permessi su altri progetti;
- criteri di cartelle o organizzazione, se il tuo account non può leggerli (in quel caso compare
  un AVVISO).

## Test

`node --test iam-scripts.test.mjs` prova i tre script con `gcloud` e `curl` simulati
(`test-stubs/`), senza contattare Google. Gli stessi test girano in GitHub Actions.
