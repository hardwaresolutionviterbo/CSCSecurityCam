#!/usr/bin/env bash
# Nexus Social · prova dell'impersonazione e dei permessi EFFETTIVI dell'account di audit.
#
# Ottiene un token temporaneo di firestore-audit (senza chiavi, senza salvarlo su disco) e chiede
# a Google, con testIamPermissions, quali permessi ha davvero sul progetto. È lo stesso controllo
# che l'audit fa prima di leggere. NON legge dati Firestore e non modifica nulla.
# Il token non viene mai stampato.
#
# Uso (Google Cloud Shell):  bash 03-prova-impersonazione.sh
# Uscita: 0 solo lettura confermata, 1 permessi non previsti o mancanti, 2 prova impossibile.

set -uo pipefail

PROJECT="nexus-social-e3eee"
SA="firestore-audit@${PROJECT}.iam.gserviceaccount.com"

# Stesso elenco di audit-firestore.mjs (3 di lettura + 29 vietati).
READ=(datastore.entities.get datastore.entities.list datastore.databases.list)
FORBIDDEN=(
  datastore.entities.create datastore.entities.update datastore.entities.delete
  datastore.databases.create datastore.databases.update datastore.databases.delete
  datastore.databases.export datastore.databases.import datastore.databases.bulkDelete
  datastore.databases.clone datastore.backups.restoreDatabase datastore.backups.delete
  datastore.backupSchedules.create datastore.backupSchedules.delete
  datastore.schemas.create datastore.schemas.delete
  firebaserules.releases.create firebaserules.releases.update firebaserules.releases.delete
  firebaserules.rulesets.create
  resourcemanager.projects.setIamPolicy iam.serviceAccounts.setIamPolicy iam.serviceAccountKeys.create
  iam.serviceAccounts.actAs iam.serviceAccounts.getAccessToken iam.serviceAccounts.getOpenIdToken
  iam.serviceAccounts.signJwt iam.serviceAccounts.signBlob iam.serviceAccounts.implicitDelegation
)

echo "== 1. Token temporaneo di firestore-audit (impersonazione)"
if ! TOKEN="$(gcloud auth print-access-token --impersonate-service-account="$SA" 2>/dev/null)" || [[ -z "$TOKEN" ]]; then
  echo "  PROBLEMA impersonazione non riuscita: manca Token Creator, l'API iamcredentials è spenta"
  echo "           oppure i permessi non si sono ancora propagati (riprova tra qualche minuto)."
  exit 2
fi
echo "  OK       token ottenuto (non mostrato, valido circa 1 ora, non salvato)"

echo "== 2. Permessi effettivi sul progetto (testIamPermissions)"
body="$(python3 -c 'import json,sys; print(json.dumps({"permissions": sys.argv[1:]}))' "${READ[@]}" "${FORBIDDEN[@]}")"
response="$(curl -sS -X POST \
  -H "Authorization: Bearer ${TOKEN}" -H "Content-Type: application/json" \
  "https://cloudresourcemanager.googleapis.com/v1/projects/${PROJECT}:testIamPermissions" \
  -d "$body")"
unset TOKEN

python3 - "$response" "${#READ[@]}" "${READ[@]}" "${FORBIDDEN[@]}" <<'EOF'
import json, sys
response, n_read = sys.argv[1], int(sys.argv[2])
read = sys.argv[3:3 + n_read]
forbidden = sys.argv[3 + n_read:]
try:
    data = json.loads(response)
except ValueError:
    print("  PROBLEMA risposta non valida da Google"); sys.exit(2)
if "error" in data:
    err = data["error"]
    print(f"  PROBLEMA errore Google: {err.get('status')} {str(err.get('message', ''))[:200]}")
    sys.exit(2)
granted = set(data.get("permissions", []))
missing = [p for p in read if p not in granted]
extra = [p for p in forbidden if p in granted]
for p in read:
    print(f"  {'OK      ' if p in granted else 'PROBLEMA'} lettura: {p}")
if extra:
    for p in extra:
        print(f"  PROBLEMA permesso NON previsto: {p}")
else:
    print(f"  OK       nessuno dei {len(forbidden)} permessi di scrittura o amministrazione controllati")
if missing or extra:
    print("\nEsito: NON conforme. Non eseguire l'audit."); sys.exit(1)
print("\nEsito: impersonazione funzionante, permessi di sola lettura sui punti controllati.")
print("Limite: testIamPermissions vede i permessi sul progetto, non quelli concessi su singole")
print("risorse o su altri progetti: per quelli vale la verifica di 01-verifica.sh.")
EOF
