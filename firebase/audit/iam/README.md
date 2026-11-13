# Account di audit Firestore: preparazione guidata

Tre script per Google Cloud Shell. Solo il secondo modifica IAM, e solo dopo che hai scritto il
nome del progetto per confermare.

| Script | Cosa fa | Modifica qualcosa? | Privilegi che ti servono |
|---|---|---|---|
| `01-verifica.sh` | Controlla progetto, gerarchia, ruoli, impersonazione, chiavi, condizioni, regole di divieto, API e contenuto di `roles/datastore.viewer` | No | Viewer o Security Reviewer sul progetto (Owner basta) |
| `02-configura.sh` | Attiva 2 API se spente, crea l'account se manca, assegna `roles/datastore.viewer`, ti permette di impersonarlo | **Sì (IAM)** | Owner, oppure Service Account Admin + Project IAM Admin (+ Service Usage Admin per le API) |
| `03-prova-impersonazione.sh` | Ottiene un token temporaneo dell'account e chiede a Google i suoi permessi effettivi | No | Token Creator sull'account (lo assegna il passo 2) |

Nessuno dei tre legge dati Firestore, crea chiavi o stampa email di persone.

## Passo 0: scarica gli script (Cloud Shell)

Apri [console.cloud.google.com](https://console.cloud.google.com) e il terminale (icona `>_`):

```bash
gcloud config set account TUA_EMAIL      # solo se in Cloud Shell c'è più di un account
git clone --branch audit/firestore-readonly https://github.com/hardwaresolutionviterbo/Nexus-Social.git
cd Nexus-Social
git log -1 --format=%h                   # deve corrispondere al commit indicato da Claude
cd firebase/audit/iam
```

## Passo 1: verifica (sola lettura)

```bash
bash 01-verifica.sh
```

Ogni riga inizia con:

- `OK`: come previsto.
- `INFO`: informazione; per esempio "non esiste ancora: lo crea 02-configura.sh".
- `AVVISO`: da leggere, non blocca. Esempio tipico: altre persone con ruolo Owner o Editor
  sul progetto, che per natura possono agire come qualsiasi account di servizio.
- `PROBLEMA`: blocca il passo 2 finché non viene risolto. Esempi: ruoli in più sull'account,
  chiavi esistenti, altri utenti autorizzati a impersonarlo, ruoli concessi a `allUsers` o
  `allAuthenticatedUsers`.

Cosa controlla:

1. **Progetto e gerarchia**: se il progetto sta sotto una cartella o un'organizzazione, ne legge i
   criteri IAM per trovare ruoli ereditati. Un progetto creato con un account Gmail di solito non
   ne ha.
2. **Il tuo account**: quali ruoli hai e se puoi eseguire il passo 2.
3. **Account di servizio**: se `firestore-audit` esiste già, se è attivo, se ci sono duplicati.
4. **Ruoli dell'account** a ogni livello: l'unico ammesso è `roles/datastore.viewer` sul
   progetto, senza condizioni.
5. **Impersonazione**: chi ha ruoli direttamente sull'account (ammesso solo tu, con Token
   Creator) e chi, con ruoli di progetto (Owner, Editor, Token Creator di progetto...), può
   agire come qualsiasi account di servizio.
6. **Chiavi**: devono essere zero.
7. **Condizioni IAM e regole di divieto**: le condizioni sull'account vengono segnalate; le regole
   di divieto possono solo togliere permessi.
8. **API**: `iamcredentials`, `cloudresourcemanager`, `firestore`.
9. **`roles/datastore.viewer`**: legge da Google la definizione attuale del ruolo e verifica che
   contenga solo permessi `get`, `list`, `getMetadata`.

## Passo 2: configurazione (modifica IAM)

Solo dopo che il passo 1 è uscito senza `PROBLEMA`:

```bash
bash 02-configura.sh
```

Ripete la verifica, chiede di scrivere `nexus-social-e3eee` e poi esegue esattamente questi
comandi. Ognuno è ripetibile senza creare duplicati.

```bash
# solo se spente
gcloud services enable iamcredentials.googleapis.com --project=nexus-social-e3eee
gcloud services enable cloudresourcemanager.googleapis.com --project=nexus-social-e3eee
# solo se l'account non esiste
gcloud iam service-accounts create firestore-audit --project=nexus-social-e3eee \
  --display-name="Firestore audit (sola lettura)"
# ruolo di sola lettura, senza condizioni
gcloud projects add-iam-policy-binding nexus-social-e3eee \
  --member="serviceAccount:firestore-audit@nexus-social-e3eee.iam.gserviceaccount.com" \
  --role="roles/datastore.viewer" --condition=None --format=none
# impersonazione: solo il tuo account, solo su questo account di servizio (non sul progetto)
gcloud iam service-accounts add-iam-policy-binding \
  firestore-audit@nexus-social-e3eee.iam.gserviceaccount.com --project=nexus-social-e3eee \
  --member="user:TUA_EMAIL" --role="roles/iam.serviceAccountTokenCreator" \
  --condition=None --format=none
```

Lo script prende `TUA_EMAIL` dall'account attivo in gcloud: non devi scriverla tu. Alla fine
ripete la verifica.

## Passo 3: prova dei permessi effettivi (dopo 2-5 minuti)

```bash
bash 03-prova-impersonazione.sh
```

Esito atteso: i 3 permessi di lettura presenti e nessuno dei 29 permessi di scrittura o
amministrazione controllati. È lo stesso controllo che l'audit fa prima di leggere.

## Come sai che l'account ha solo i privilegi previsti

Devono valere tutte e tre le condizioni:

- `01-verifica.sh` finisce con `0 problemi`;
- `03-prova-impersonazione.sh` finisce con "impersonazione funzionante, permessi di sola lettura";
- nella sezione 5 della verifica non compaiono persone che non conosci.

Cosa la verifica **non** può vedere:

- ruoli ricevuti tramite gruppi Google: non aggiungere l'account a nessun gruppo;
- permessi su altri progetti;
- criteri di cartelle o organizzazione, se il tuo account non può leggerli (in quel caso compare
  un `AVVISO`).

## Se vuoi annullare tutto

```bash
gcloud iam service-accounts delete firestore-audit@nexus-social-e3eee.iam.gserviceaccount.com \
  --project=nexus-social-e3eee
gcloud projects remove-iam-policy-binding nexus-social-e3eee \
  --member="deleted:serviceAccount:firestore-audit@nexus-social-e3eee.iam.gserviceaccount.com?uid=UID" \
  --role="roles/datastore.viewer"   # UID: lo mostra get-iam-policy dopo l'eliminazione
```
