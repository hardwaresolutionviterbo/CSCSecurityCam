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
| `01-verifica.sh` | Controlla progetto, gerarchia, ruoli (leggendo i permessi di ogni ruolo assegnato), impersonazione, chiavi, criteri degli altri account di servizio, permessi su singole risorse (Cloud Asset), condizioni, API, supporto dei 3 permessi, definizione e stato esatti del ruolo | No | Viewer o Security Reviewer sul progetto (Owner basta), la lettura dei criteri di cartelle e organizzazione se il progetto ne ha, e per Cloud Asset `cloudasset.assets.searchAllIamPolicies` sull'ambito di ricerca (per esempio Cloud Asset Viewer) |
| `01-verifica.sh --finale` | Come sopra, ma **tutto** deve essere presente ed esatto | No | Gli stessi |
| `02-configura.sh` | Mostra il riepilogo, chiede conferma, crea il ruolo con esattamente 3 permessi, crea l'account, gli assegna il ruolo, ti permette di impersonarlo, poi esegue la verifica finale | **Sì (IAM)** | Owner, oppure Role Administrator + Service Account Admin + Project IAM Admin |
| `03-prova-impersonazione.sh` | Controlla i permessi effettivi dell'account su **tutti** i permessi verificabili del progetto | No | Token Creator sull'account (lo assegna il passo 2) |

Nessuno degli script attiva API (nemmeno `cloudasset.googleapis.com`), legge dati Firestore, crea
chiavi o stampa email di persone o token.

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
progetto, nelle cartelle, nell'organizzazione e sugli account di servizio del progetto: ruoli
predefiniti, ruoli personalizzati del progetto e dell'organizzazione. Un ruolo che non si riesce a
leggere interrompe la verifica.

Un ruolo è pericoloso se contiene uno di questi permessi (elenco in `permessi.py`):

- impersonazione: `iam.serviceAccounts.getAccessToken`, `getOpenIdToken`, `signBlob`, `signJwt`,
  `implicitDelegation`, `actAs`;
- chiavi: `iam.serviceAccountKeys.create`, `iam.serviceAccountKeys.upload`;
- ciclo di vita dell'account: `iam.serviceAccounts.create`, `delete`, `undelete`, `disable`, `enable`;
- permessi e criteri: `iam.serviceAccounts.setIamPolicy`, `resourcemanager.projects/folders/organizations.setIamPolicy`,
  `iam.roles.update`, `iam.roles.delete`, `iam.roles.undelete`, `orgpolicy.policy.set`;
- intere famiglie amministrative, tranne le letture (`get`, `list`): `iam.denypolicies.*`,
  `orgpolicy.policies.*`, `iam.principalaccessboundarypolicies.*`;
- Deployment Manager (`deploymentmanager.deployments.create/update`), che agisce con l'agente
  Google APIs, di norma Editor.

Sono pericolosi comunque, anche senza leggerne i permessi, tra gli altri: `roles/owner`,
`roles/editor`, `roles/iam.serviceAccountTokenCreator`, `roles/iam.serviceAccountKeyAdmin`,
`roles/iam.roleAdmin`, `roles/iam.denyAdmin`, `roles/orgpolicy.policyAdmin`,
`roles/deploymentmanager.editor`, `roles/resourcemanager.projectIamAdmin`, `roles/firebase.admin`.

Per chi ha un ruolo pericoloso, a parte te, conta **se ha già i permessi dell'account di audit**:

- **privilegi equivalenti o superiori: AVVISO, non bloccante.** Vale se, con binding senza
  condizioni nel progetto o sopra, ha già tutti e 3 i permessi di lettura dell'audit. Esempi tipici:
  Owner, Editor, l'account `firebase-adminsdk-…` creato da Firebase. Impersonando l'account di
  audit non ottiene nulla in più.
  **Rischio residuo documentato:** potrebbe modificare l'account o il ruolo personalizzato. Lo
  rilevano `01-verifica.sh` (definizione esatta), `03-prova-impersonazione.sh` e il controllo dei
  permessi che l'audit fa prima di ogni lettura. Se un account così ha chiavi create a mano è un
  AVVISO: rischio di sicurezza del progetto, non della sola lettura dell'audit.
- **privilegi NON equivalenti: PROBLEMA**, salvo approvazione. Esempio: Token Creator senza permessi
  di lettura su Firestore. Impersonando l'account di audit otterrebbe una lettura che non ha.
- **membri pubblici** (`allUsers`, `allAuthenticatedUsers`): sempre PROBLEMA.
- **membro eliminato** (`deleted:`) senza privilegi equivalenti: PROBLEMA, perché se venisse
  ripristinato riotterrebbe i privilegi.

Restano sempre **bloccanti** i privilegi assegnati all'account di audit stesso: qualsiasi ruolo
diverso da `firestoreAuditReader`, una definizione del ruolo diversa dai 3 permessi, permessi su
altri account di servizio o su singole risorse.

### Gruppi, domini e identità federate

Non si può controllare da qui se comprendono l'account di audit. Per questo:

- non sono approvabili (una loro riga nel file viene rifiutata: PROBLEMA);
- con un ruolo pericoloso sono un PROBLEMA;
- con qualsiasi altro ruolo, nel progetto, sopra o su una singola risorsa, rendono l'esito
  **INCOMPLETO** (`CODICE: 3`).

`allUsers`, `allAuthenticatedUsers` e `principalSet://goog/public:all` comprendono l'account di
audit: con un ruolo qualsiasi sono sempre un PROBLEMA.

### Identità approvate

Copia `identita-approvate.esempio.txt` come `identita-approvate.txt` e lascia solo le righe che
hai controllato. Il file non va su GitHub (è in `.gitignore`); senza il file nessuna identità è
approvata. Si possono approvare solo:

- **persone** (`user:`): con privilegi pericolosi non sono mai un esito positivo, perché lo script
  non può verificare chi usa il loro account: l'approvazione trasforma il PROBLEMA in INCOMPLETO
  (`CODICE: 3`). Solo il tuo account attivo è escluso dai controlli;
- **account di servizio di questo progetto** che esistono: lo script controlla anche chi può
  impersonarli (deve essere approvato anche lui) e se hanno chiavi create a mano (se sì, l'esito è
  INCOMPLETO: chi possiede una chiave non è verificabile);
- **agenti Google di questo progetto**, per indirizzo esatto: `service-NUMERO@gcp-sa-SERVIZIO.iam.gserviceaccount.com`,
  `NUMERO@cloudservices.gserviceaccount.com` e pochi domini storici, con `NUMERO` uguale al numero
  del progetto.

Nessuna approvazione generica: la vecchia riga `agenti-google-del-progetto` non approva più nulla
(AVVISO). Non sono approvabili account di altri progetti, account del dominio condiviso
`firebase-sa-management` né `NUMERO@cloudbuild.gserviceaccount.com` (chi crea build agisce come lui).
Le approvazioni non valgono mai sull'account di audit: lì è ammesso solo Token Creator per te.

### Permessi su singole risorse (Cloud Asset Inventory)

`01-verifica.sh` controlla anche i permessi concessi **direttamente su singole risorse**:

1. legge il criterio IAM di ogni altro account di servizio del progetto: se l'account di audit
   può agire su uno di loro (escalation) è un PROBLEMA;
2. se `cloudasset.googleapis.com` è **già** attiva, esegue in sola lettura
   `gcloud asset search-all-iam-policies` su tutta la gerarchia: l'organizzazione, oppure il solo
   progetto se non ha organizzazione. Ogni permesso dell'account di audit su una risorsa (bucket,
   secret, account di altri progetti, altri progetti dell'organizzazione) è un PROBLEMA.

Cloud Asset è **facoltativa** in questa fase: gli script non la attivano mai.

L'esito della verifica finale è **INCOMPLETO** (`CODICE: 3`), mai positivo, se:

- l'API Cloud Asset non è attiva. L'attivazione è una tua decisione separata, con
  `gcloud services enable cloudasset.googleapis.com --project=nexus-social-e3eee`;
- la ricerca non riesce (per esempio permessi di ricerca insufficienti sull'organizzazione);
- il progetto ha una cartella ma l'organizzazione non compare nella gerarchia letta;
- il criterio di un altro account di servizio del progetto non è leggibile.

Nella verifica preliminare l'API spenta è solo un'INFO, anche se l'account esiste già.

La ricerca **non** copre: progetti fuori dalla gerarchia (per esempio altri tuoi progetti senza
organizzazione), risorse o tipi non indicizzati da Cloud Asset e modifiche degli ultimi minuti
non ancora indicizzate.

### Regole di divieto

Le regole di divieto non vengono mai usate come garanzia di sola lettura: possono essere tolte in
qualsiasi momento. Un ruolo in più sull'account resta un PROBLEMA anche se una regola di divieto ne
blocca le scritture. Se non sono leggibili, la verifica lo dice (LIMITE) e l'esito non cambia.

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
- `CODICE: 2`: un elemento indispensabile non è leggibile (lettura negata, gerarchia illeggibile,
  ruolo assegnato illeggibile...), quindi la verifica è interrotta.
- `CODICE: 3`: INCOMPLETO. Nessun problema trovato, ma qualcosa non è verificabile (righe
  INCOMPLETO: Cloud Asset non attiva, gruppi con un ruolo, criteri non leggibili...). Non è un
  esito positivo.

La sezione 2 mostra l'account gcloud attivo, mascherato (per esempio `ma•••@gmail.com`): è
l'unico "tu" escluso dai controlli, quindi deve essere il tuo.

In modalità preliminare account, ruolo e binding previsti possono ancora mancare (compaiono come
INFO). Tutto il resto deve essere già pulito: API attive, nessun ruolo non previsto sull'account,
nessuna identità non approvata con privilegi pericolosi, nessuna chiave, account e ruolo (se
esistono) attivi e con la definizione esatta.

La sezione 12 mostra, per ciascuno dei 3 permessi, lo stage e il supporto nei ruoli personalizzati:

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
   attive. **Non le attiva**: se ne manca una si ferma con `CODICE: 3`, prima di qualsiasi
   modifica, spiega a cosa serve e indica il comando da eseguire a parte, solo dopo una tua
   autorizzazione separata. `cloudasset.googleapis.com` è facoltativa: se è spenta la
   configurazione procede, ma la verifica finale risulta INCOMPLETA (`CODICE: 3`, messaggio
   "CONFIGURAZIONE APPLICATA, VERIFICA FINALE INCOMPLETA").
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

- Se il ruolo esiste già con una definizione diversa, eliminato o non in stage GA, lo script si
  ferma senza modificarlo.
- La tua email viene letta da gcloud: non devi scriverla, e non viene mai stampata per intero.
- Se un passo fallisce, lo script si ferma subito e stampa le operazioni **completate**, quella
  **non riuscita** e quelle **non eseguite**. Non fa rollback: rieseguendolo, i passi già fatti
  non vengono duplicati.
- I file temporanei (solo testi di errore) vengono cancellati anche dopo un errore o Ctrl+C.
- Se la verifica preliminare o finale non è pulita (PROBLEMA, INCOMPLETO o interrotta), esce con
  codice 1 e, dopo le modifiche, stampa "CONFIGURAZIONE NON VERIFICATA".

### Verifica finale (`01-verifica.sh --finale`)

Esce con codice 0 **solo se** valgono tutte queste condizioni:

- `firestore-audit` esiste, è attivo e compare nell'elenco degli account del progetto;
- `firestoreAuditReader` esiste, non è eliminato, è in stage GA e contiene esattamente i 3 permessi;
- l'account ha esattamente quel ruolo sul progetto, senza condizioni, e nessun altro ruolo nel
  progetto, nelle cartelle o nell'organizzazione;
- sull'account c'è solo Token Creator per te, senza condizioni;
- nessun'altra identità, nel progetto o sopra, ha privilegi pericolosi **senza** avere già
  privilegi equivalenti o superiori, salvo quelle approvate (che a loro volta superano i controlli
  descritti sopra). Quelle con privilegi equivalenti o superiori sono AVVISO;
- nessuna voce non approvabile nel file delle approvazioni;
- nessun gruppo, dominio o identità federata con ruoli nel progetto, sopra o su singole risorse;
- nessun permesso dell'account di audit su altri account di servizio o su singole risorse
  (ricerca Cloud Asset riuscita su tutta la gerarchia);
- nessuna chiave creata a mano;
- `iamcredentials`, `cloudresourcemanager`, `firestore` e `cloudasset` attive.

Qualsiasi condizione mancante dà PROBLEMA (`CODICE: 1`); un elemento indispensabile non leggibile
interrompe la verifica (`CODICE: 2`); un elemento non verificabile dà INCOMPLETO (`CODICE: 3`).

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

Cosa **nessuno** dei due script può vedere:

- chi appartiene ai gruppi e alle identità federate (per questo ogni loro ruolo dà INCOMPLETO);
- permessi su progetti fuori dalla gerarchia del progetto, e risorse non indicizzate da Cloud Asset;
- chi può usare gli agenti Google approvati (li gestisce Google);
- `03-prova-impersonazione.sh` vede solo i permessi sul progetto, non quelli su singole risorse.

## Test

`node --test iam-scripts.test.mjs` prova i tre script con `gcloud` e `curl` simulati
(`test-stubs/`), senza contattare Google: binding mancanti e aggiuntivi, account e ruolo
disattivati o in stage diverso da GA, chiavi, identità aggiuntive, gruppi, domini e identità
federate (anche nel file delle approvazioni), membri pubblici ed eliminati, agenti Google falsi o
di altri progetti, privilegi ereditati, ruoli personalizzati con permessi di escalation, permessi
su singole risorse e su altri account, Cloud Asset disattivata o negata, regole di divieto, API
disattivate, conferma errata, interruzione a metà configurazione e con Ctrl+C. Gli stessi test
girano in GitHub Actions.
