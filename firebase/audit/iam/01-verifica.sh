#!/usr/bin/env bash
# Nexus Social · verifica IAM dell'account di audit Firestore.
#
# SOLA LETTURA: legge configurazioni IAM, account di servizio, chiavi (solo l'elenco), API e la
# descrizione di ogni ruolo assegnato nel progetto e sopra. Non modifica nulla, non crea nulla e
# non legge dati Firestore. Non stampa email di persone. I file temporanei vengono cancellati
# alla fine, anche se lo script viene interrotto.
#
# Uso (Google Cloud Shell):
#   bash 01-verifica.sh            verifica preliminare (prima di 02-configura.sh)
#   bash 01-verifica.sh --finale   verifica finale: tutto deve essere presente ed esatto
# Uscita: 0 nessun problema, 1 problemi trovati, 2 verifica impossibile.
#
# Identità approvate: file identita-approvate.txt accanto allo script (o APPROVATI_FILE),
# vedi identita-approvate.esempio.txt. Non va messo su GitHub (contiene email).

set -uo pipefail
# Nessuna domanda interattiva di gcloud (per esempio "attivare l'API?"): risposta sempre no.
export CLOUDSDK_CORE_DISABLE_PROMPTS=1
export PYTHONDONTWRITEBYTECODE=1

PROJECT="nexus-social-e3eee"
SA="firestore-audit@${PROJECT}.iam.gserviceaccount.com"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODO="preliminare"
case "${1:-}" in
  "") ;;
  --finale) MODO="finale" ;;
  *) echo "Uso: bash 01-verifica.sh [--finale]"; exit 2 ;;
esac
APPROVATI_FILE="${APPROVATI_FILE:-$HERE/identita-approvate.txt}"

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
trap 'exit 130' INT TERM HUP

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

# Permessi di OGNI ruolo assegnato nel progetto e sopra (predefinito o personalizzato). Un ruolo
# che non si riesce a leggere blocca l'esito positivo (lo controlla analizza.py).
export PROJECT SA ME MODO APPROVATI_FILE
while IFS=$'\t' read -r name a1 a2; do
  [[ -n "$name" ]] || continue
  if [[ -n "${a2:-}" ]]; then
    read_json "ruolo_${name}.json" iam roles describe "$a1" "$a2"
  else
    read_json "ruolo_${name}.json" iam roles describe "$a1"
  fi
done < <(python3 "$HERE/analizza.py" "$TMP" --ruoli)

python3 "$HERE/analizza.py" "$TMP"
