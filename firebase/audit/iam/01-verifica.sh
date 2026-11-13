#!/usr/bin/env bash
# Nexus Social · verifica IAM dell'account di audit Firestore.
#
# SOLA LETTURA: legge configurazioni IAM, account di servizio, chiavi (solo l'elenco) e API.
# Non modifica nulla, non crea nulla e non legge dati Firestore.
# Non stampa email di persone. I file temporanei vengono cancellati alla fine.
#
# Uso (Google Cloud Shell):  bash 01-verifica.sh
# Uscita: 0 nessun problema, 1 problemi trovati, 2 verifica impossibile.

set -uo pipefail
# Nessuna domanda interattiva di gcloud (per esempio "attivare l'API?"): risposta sempre no.
export CLOUDSDK_CORE_DISABLE_PROMPTS=1
export PYTHONDONTWRITEBYTECODE=1

PROJECT="nexus-social-e3eee"
SA="firestore-audit@${PROJECT}.iam.gserviceaccount.com"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

command -v gcloud >/dev/null || { echo "gcloud non trovato: usa Google Cloud Shell."; exit 2; }
command -v python3 >/dev/null || { echo "python3 non trovato."; exit 2; }

ME="$(gcloud config get-value account 2>/dev/null)"
if [[ -z "$ME" || "$ME" == *.gserviceaccount.com ]]; then
  echo "Serve il tuo account personale attivo in gcloud (non un account di servizio)."
  exit 2
fi

TMP="$(mktemp -d)"
chmod 700 "$TMP"
trap 'rm -rf "$TMP"' EXIT

# Ogni lettura salva il JSON e un esito: ok, notfound (la risorsa non esiste: NOT_FOUND)
# oppure error (permessi insufficienti, API spenta, rete...). Un errore non viene MAI
# interpretato come "non esiste" o "elenco vuoto": l'analisi interrompe la verifica.
read_json() {
  local file="$1"; shift
  if gcloud "$@" --format=json >"$TMP/$file" 2>"$TMP/$file.err"; then
    echo ok >"$TMP/$file.status"
  else
    : >"$TMP/$file"
    if grep -q 'NOT_FOUND' "$TMP/$file.err"; then echo notfound; else echo error; fi >"$TMP/$file.status"
  fi
}

echo "Raccolta delle configurazioni (sola lettura)..."
read_json project.json        projects describe "$PROJECT"
read_json project_policy.json projects get-iam-policy "$PROJECT"
read_json ancestors.json      projects get-ancestors-iam-policy "$PROJECT"
read_json sa_list.json        iam service-accounts list --project="$PROJECT"
read_json sa.json             iam service-accounts describe "$SA" --project="$PROJECT"
if [[ "$(cat "$TMP/sa.json.status")" == ok ]]; then
  read_json sa_policy.json    iam service-accounts get-iam-policy "$SA" --project="$PROJECT"
  read_json keys.json         iam service-accounts keys list --iam-account="$SA" --project="$PROJECT" --managed-by=user
fi
read_json deny.json           iam policies list --kind=denypolicies \
                                --attachment-point="cloudresourcemanager.googleapis.com%2Fprojects%2F${PROJECT}"
read_json services.json       services list --enabled --project="$PROJECT"
read_json custom_role.json    iam roles describe firestoreAuditReader --project="$PROJECT"
read_json testable.json       iam list-testable-permissions \
                                "//cloudresourcemanager.googleapis.com/projects/${PROJECT}" --filter='name~^datastore\.'

PROJECT="$PROJECT" SA="$SA" ME="$ME" python3 "$HERE/analizza.py" "$TMP"
