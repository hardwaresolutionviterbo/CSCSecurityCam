#!/usr/bin/env bash
# Nexus Social · configurazione dell'account di audit Firestore.
#
# MODIFICA IAM: da eseguire solo dopo che 01-verifica.sh è uscito senza problemi.
# Richiede Owner, oppure Role Administrator + Service Account Admin + Project IAM Admin
# (+ Service Usage Admin se mancano API). Ogni passo è ripetibile: se una cosa esiste già non
# viene duplicata.
#
# Cosa fa, e nient'altro:
#   1. attiva le API iamcredentials e cloudresourcemanager, se spente;
#   2. crea il ruolo personalizzato firestoreAuditReader con ESATTAMENTE 3 permessi
#      (datastore.databases.list, datastore.entities.get, datastore.entities.list), se non esiste;
#      se esiste con una definizione diversa si ferma senza modificarlo;
#   3. crea firestore-audit, se non esiste;
#   4. gli assegna il ruolo personalizzato sul progetto, senza condizioni;
#   5. permette SOLO al tuo account di impersonarlo (Token Creator sull'account, non sul progetto).
# Non crea chiavi, non tocca altri account o ruoli, non legge dati Firestore.
#
# Uso (Google Cloud Shell):  bash 02-configura.sh

set -euo pipefail
# Nessuna domanda interattiva di gcloud: l'unica conferma è quella esplicita qui sotto.
export CLOUDSDK_CORE_DISABLE_PROMPTS=1

PROJECT="nexus-social-e3eee"
SA_ID="firestore-audit"
SA="${SA_ID}@${PROJECT}.iam.gserviceaccount.com"
ROLE_ID="firestoreAuditReader"
ROLE="projects/${PROJECT}/roles/${ROLE_ID}"
ROLE_PERMISSIONS="datastore.databases.list,datastore.entities.get,datastore.entities.list"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

ME="$(gcloud config get-value account 2>/dev/null)"
ME="${ME,,}"  # IAM usa le email in minuscolo
if [[ -z "$ME" || "$ME" == *.gserviceaccount.com ]]; then
  echo "Serve il tuo account personale attivo in gcloud."; exit 2
fi

echo "== Verifica preliminare (sola lettura)"
if ! bash "$HERE/01-verifica.sh"; then
  echo
  echo "Mi fermo: la verifica ha trovato problemi o non è riuscita. Nessuna modifica eseguita."
  exit 1
fi

echo
echo "Sto per modificare IAM del progetto ${PROJECT} con i 5 passi descritti in cima al file."
IFS= read -r -p "Per confermare scrivi il nome del progetto: " answer || answer=""
if [[ "$answer" != "$PROJECT" ]]; then
  echo "Conferma non valida. Nessuna modifica eseguita."; exit 1
fi

echo
echo "== 1. API"
# Se l'elenco non si legge, set -e interrompe lo script (nessuna attivazione "alla cieca").
enabled="$(gcloud services list --enabled --project="$PROJECT" --format="value(config.name)")"
for api in iamcredentials.googleapis.com cloudresourcemanager.googleapis.com; do
  if grep -qx "$api" <<<"$enabled"; then
    echo "  già attiva: $api"
  else
    gcloud services enable "$api" --project="$PROJECT"
    echo "  attivata: $api"
  fi
done

echo "== 2. Ruolo personalizzato ${ROLE_ID}"
role_err="$(mktemp)"
if role_json="$(gcloud iam roles describe "$ROLE_ID" --project="$PROJECT" --format=json 2>"$role_err")"; then
  rm -f "$role_err"
  # Esiste: deve essere attivo e contenere esattamente i 3 permessi. Non viene mai modificato qui.
  if ! python3 -c '
import json, sys
role = json.loads(sys.stdin.read())
expected = set(sys.argv[1].split(","))
sys.exit(0 if not role.get("deleted") and set(role.get("includedPermissions", [])) == expected else 1)
' "$ROLE_PERMISSIONS" <<<"$role_json"; then
    echo "  ERRORE: il ruolo esiste ma è eliminato o non contiene esattamente i 3 permessi. Mi fermo."
    exit 1
  fi
  echo "  esiste già con la definizione esatta"
elif ! grep -q 'NOT_FOUND' "$role_err"; then
  rm -f "$role_err"
  echo "  ERRORE: impossibile verificare se il ruolo esiste (permessi insufficienti?). Mi fermo."
  exit 1
else
  rm -f "$role_err"
  gcloud iam roles create "$ROLE_ID" --project="$PROJECT" \
    --title="Firestore audit - sola lettura" \
    --description="Solo lettura documenti Firestore per l'audit. Nessuna scrittura." \
    --permissions="$ROLE_PERMISSIONS" --stage=GA --format=none
  echo "  creato con esattamente: ${ROLE_PERMISSIONS//,/, }"
fi

echo "== 3. Account di servizio"
# Crea l'account solo se Google risponde NOT_FOUND; qualsiasi altro errore interrompe.
describe_err="$(mktemp)"
if gcloud iam service-accounts describe "$SA" --project="$PROJECT" --format="value(email)" >/dev/null 2>"$describe_err"; then
  rm -f "$describe_err"
  echo "  esiste già: $SA"
elif ! grep -q 'NOT_FOUND' "$describe_err"; then
  rm -f "$describe_err"
  echo "  ERRORE: impossibile verificare se l'account esiste (permessi insufficienti?). Mi fermo."
  exit 1
else
  rm -f "$describe_err"
  gcloud iam service-accounts create "$SA_ID" --project="$PROJECT" \
    --display-name="Firestore audit (sola lettura)" \
    --description="Solo il ruolo firestoreAuditReader. Nessuna chiave. Impersonato solo dal proprietario." \
    --format=none
  echo "  creato: $SA"
  # Un account appena creato può impiegare qualche secondo a essere visibile a IAM.
  for _ in $(seq 1 12); do
    gcloud iam service-accounts describe "$SA" --project="$PROJECT" --format="value(email)" >/dev/null 2>&1 && break
    sleep 5
  done
fi

echo "== 4. Ruolo di sola lettura sul progetto"
# --format=none: non stampa l'intero criterio IAM (conterrebbe email).
gcloud projects add-iam-policy-binding "$PROJECT" \
  --member="serviceAccount:$SA" --role="$ROLE" --condition=None --format=none
echo "  $ROLE assegnato (se c'era già, nessun duplicato)"

echo "== 5. Impersonazione solo per il tuo account, solo su questo account di servizio"
gcloud iam service-accounts add-iam-policy-binding "$SA" --project="$PROJECT" \
  --member="user:$ME" --role="roles/iam.serviceAccountTokenCreator" --condition=None --format=none
echo "  Token Creator assegnato a te sull'account firestore-audit"

echo
echo "== Verifica finale (sola lettura)"
if ! bash "$HERE/01-verifica.sh"; then
  echo
  echo "CONFIGURAZIONE NON VERIFICATA: la verifica finale ha trovato problemi o non è riuscita."
  echo "Non eseguire l'audit. Se hai appena creato l'account, riprova 01-verifica.sh tra qualche minuto."
  exit 1
fi
echo
echo "Configurazione verificata. Aspetta 2-5 minuti (propagazione IAM), poi esegui: bash 03-prova-impersonazione.sh"
