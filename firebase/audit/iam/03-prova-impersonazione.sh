#!/usr/bin/env bash
# Nexus Social · prova dell'impersonazione e dei permessi EFFETTIVI dell'account di audit.
#
# 1. Chiede a Google l'elenco COMPLETO dei permessi verificabili sul progetto (con il tuo account,
#    sola lettura).
# 2. Ottiene un token temporaneo di firestore-audit (senza chiavi, senza salvarlo su disco).
# 3. Chiede a Google, con testIamPermissions e a blocchi di 100, quali di quei permessi l'account
#    ha davvero. Deve averne ESATTAMENTE 3: datastore.databases.list, datastore.entities.get,
#    datastore.entities.list.
# Vede SOLO i permessi sul progetto: non quelli su singole risorse (bucket, secret, altri account
# di servizio) né su altri progetti. 01-verifica.sh cerca i primi con Cloud Asset Inventory, solo se
# l'API è già attiva e solo nella gerarchia del progetto.
# NON legge dati Firestore e non modifica nulla. Il token non viene mai stampato né passato come
# argomento di un processo.
#
# Uso (Google Cloud Shell):  bash 03-prova-impersonazione.sh
# Uscita: 0 esattamente i permessi previsti; 1 permessi mancanti o in più; 2 prova impossibile;
#         3 verifica incompleta.

set -uo pipefail
export CLOUDSDK_CORE_DISABLE_PROMPTS=1
export PYTHONDONTWRITEBYTECODE=1

PROJECT="nexus-social-e3eee"
SA="firestore-audit@${PROJECT}.iam.gserviceaccount.com"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Nasconde le email di persone (lascia quelle degli account di servizio) e accorcia il testo.
mask() {
  python3 -c '
import re, sys
text = sys.stdin.read()
text = re.sub(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}",
              lambda m: m.group(0) if m.group(0).endswith(".gserviceaccount.com") else "<email>", text)
print(" ".join(text.split())[:300])'
}

TMP="$(mktemp -d)"
chmod 700 "$TMP"
trap 'rm -rf "$TMP"' EXIT

echo "== 1. Elenco completo dei permessi verificabili sul progetto"
if ! gcloud iam list-testable-permissions "//cloudresourcemanager.googleapis.com/projects/${PROJECT}" \
     --format=json >"$TMP/testable.json" 2>"$TMP/err"; then
  echo "  PROBLEMA elenco non ottenuto: $(mask <"$TMP/err")"
  exit 2
fi
echo "  OK       elenco ottenuto"

echo "== 2. Token temporaneo di firestore-audit (impersonazione)"
if ! TOKEN="$(gcloud auth print-access-token --impersonate-service-account="$SA" 2>"$TMP/err")" || [[ -z "$TOKEN" ]]; then
  echo "  PROBLEMA impersonazione non riuscita: manca Token Creator, l'API iamcredentials è spenta"
  echo "           oppure i permessi non si sono ancora propagati (riprova tra qualche minuto)."
  echo "           Motivo: $(mask <"$TMP/err")"
  exit 2
fi
echo "  OK       token ottenuto (non mostrato, valido circa 1 ora, non salvato)"

echo "== 3. Permessi effettivi dell'account (testIamPermissions su tutti i permessi verificabili)"
# Il token passa a Python su stdin tramite printf (comando interno di bash): non compare mai
# tra gli argomenti dei processi.
printf '%s\n' "$TOKEN" | python3 "$HERE/prova_permessi.py" "$TMP/testable.json"
code=$?
unset TOKEN
exit "$code"
