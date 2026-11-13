#!/usr/bin/env bash
# Nexus Social · configurazione dell'account di audit Firestore.
#
# MODIFICA IAM: da eseguire solo dopo che 01-verifica.sh è uscito senza problemi.
# Richiede Owner, oppure Role Administrator + Service Account Admin + Project IAM Admin.
# Ogni passo è ripetibile: se una cosa esiste già non viene duplicata né modificata.
#
# Cosa fa, e nient'altro (dopo il riepilogo e la tua conferma esplicita):
#   1. crea il ruolo personalizzato firestoreAuditReader con ESATTAMENTE 3 permessi
#      (datastore.databases.list, datastore.entities.get, datastore.entities.list), se non esiste;
#      se esiste con una definizione diversa, eliminato o non in stage GA si ferma senza modificarlo;
#   2. crea firestore-audit, se non esiste;
#   3. gli assegna il ruolo personalizzato sul progetto, senza condizioni;
#   4. permette SOLO al tuo account di impersonarlo (Token Creator sull'account, non sul progetto).
# Non attiva API: se iamcredentials o cloudresourcemanager mancano si ferma prima di qualsiasi
# modifica e spiega. Cloud Asset (cloudasset) NON è obbligatoria: se è spenta la configurazione
# procede, ma la verifica finale risulta INCOMPLETA (permessi su singole risorse non controllati).
# Non crea chiavi, non tocca altri account o ruoli, non legge né modifica dati Firestore, non fa
# rollback automatici.
#
# Uso (Google Cloud Shell):  bash 02-configura.sh
# Uscita: 0 configurato e verificato · 1 fermato o non verificato · 3 API da autorizzare a parte
#         (nessuna modifica) oppure configurato ma verifica finale INCOMPLETA

set -Eeuo pipefail
# Nessuna domanda interattiva di gcloud: l'unica conferma è quella esplicita qui sotto.
export CLOUDSDK_CORE_DISABLE_PROMPTS=1
export PYTHONDONTWRITEBYTECODE=1

PROJECT="nexus-social-e3eee"
SA_ID="firestore-audit"
SA="${SA_ID}@${PROJECT}.iam.gserviceaccount.com"
ROLE_ID="firestoreAuditReader"
ROLE="projects/${PROJECT}/roles/${ROLE_ID}"
ROLE_PERMISSIONS="datastore.databases.list,datastore.entities.get,datastore.entities.list"
TOKEN_CREATOR="roles/iam.serviceAccountTokenCreator"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export APPROVATI_FILE="${APPROVATI_FILE:-$HERE/identita-approvate.txt}"

# File temporanei (solo testi di errore di gcloud): cancellati sempre all'uscita.
TMP="$(mktemp -d)"
chmod 700 "$TMP"
CONFERMATO=0
SOLO_VERIFICA_INCOMPLETA=0   # 1: tutte le modifiche riuscite, verifica finale INCOMPLETA
FATTI=()          # operazioni completate
IN_CORSO=""       # operazione in corso
DA_FARE=()        # operazioni previste e non ancora iniziate

resoconto() {
  echo
  echo "== Esecuzione interrotta: stato delle modifiche"
  if ((${#FATTI[@]})); then
    echo "  Completate:"
    printf '    - %s\n' "${FATTI[@]}"
  else
    echo "  Completate: nessuna"
  fi
  [[ -n "$IN_CORSO" ]] && echo "  Non riuscita: $IN_CORSO"
  if ((${#DA_FARE[@]})); then
    echo "  Non eseguite:"
    printf '    - %s\n' "${DA_FARE[@]}"
  fi
  echo "  Nessun rollback automatico: le operazioni completate restano. Lo script si può ripetere:"
  echo "  i passi già fatti non vengono duplicati."
}

fine() {
  local rc=$?
  rm -rf "$TMP"
  if ((rc != 0 && CONFERMATO && ! SOLO_VERIFICA_INCOMPLETA)); then resoconto; fi
  exit "$rc"
}
trap fine EXIT
trap 'exit 130' INT TERM HUP

# Nasconde le email di persone (lascia quelle degli account di servizio).
maschera() {
  python3 -c '
import re, sys
e = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")
for line in sys.stdin:
    sys.stdout.write(e.sub(lambda m: m.group(0) if m.group(0).endswith(".gserviceaccount.com") else "<email>", line))
'
}

# gcloud con l'errore mascherato: in caso di errore stampa il motivo senza email e restituisce
# il codice di gcloud (set -e interrompe lo script).
gc() {
  local rc=0
  gcloud "$@" 2>"$TMP/gcloud.err" || rc=$?
  if ((rc)); then maschera <"$TMP/gcloud.err" >&2; fi
  return "$rc"
}

inizio() { IN_CORSO="$1"; DA_FARE=("${DA_FARE[@]:1}"); }
fatto()  { FATTI+=("$1"); IN_CORSO=""; }

ME="$(gcloud config get-value account 2>/dev/null)"
ME="${ME,,}"  # IAM usa le email in minuscolo
if [[ -z "$ME" || "$ME" == *.gserviceaccount.com || "$ME" != *@* ]]; then
  echo "Serve il tuo account personale attivo in gcloud."; exit 1
fi
ME_LOCALE="${ME%%@*}"
ME_VISIBILE="${ME_LOCALE:0:2}•••@${ME#*@}"   # mai l'email intera sullo schermo

echo "== API necessarie (sola lettura)"
enabled="$(gc services list --enabled --project="$PROJECT" --format="value(config.name)")"
mancanti=()
for api in iamcredentials.googleapis.com cloudresourcemanager.googleapis.com; do
  if grep -qx "$api" <<<"$enabled"; then echo "  attiva: $api"; else mancanti+=("$api"); fi
done
if grep -qx "cloudasset.googleapis.com" <<<"$enabled"; then
  ASSET="attiva"
  echo "  attiva: cloudasset.googleapis.com (facoltativa)"
else
  ASSET="spenta"
  echo "  spenta: cloudasset.googleapis.com (facoltativa, non viene attivata: la verifica finale sarà INCOMPLETA)"
fi
if ((${#mancanti[@]})); then
  echo
  echo "Mi fermo: queste API sono disattivate e lo script NON le attiva da solo."
  for api in "${mancanti[@]}"; do
    case "$api" in
      iamcredentials.googleapis.com)
        echo "  - $api: serve per ottenere i token temporanei con cui l'audit agisce come"
        echo "    firestore-audit (impersonazione, senza chiavi).";;
      cloudresourcemanager.googleapis.com)
        echo "  - $api: serve per leggere i criteri IAM del progetto e controllare i permessi"
        echo "    effettivi dell'account (01-verifica.sh e 03-prova-impersonazione.sh).";;
    esac
  done
  echo "Se e quando le autorizzi, il comando da eseguire a parte è:"
  for api in "${mancanti[@]}"; do echo "  gcloud services enable $api --project=$PROJECT"; done
  echo "Nessuna modifica eseguita."
  exit 3
fi

echo
echo "== Verifica preliminare (sola lettura)"
if ! bash "$HERE/01-verifica.sh"; then
  echo
  echo "Mi fermo: la verifica ha trovato problemi, è INCOMPLETA o non è riuscita. Nessuna modifica eseguita."
  exit 1
fi

# Stato attuale di ruolo e account, per il riepilogo (sola lettura).
stato_risorsa() {   # stampa: esiste | manca ; si ferma su qualsiasi altro errore
  if gcloud "$@" --format=none 2>"$TMP/stato.err"; then echo esiste
  elif grep -q 'NOT_FOUND' "$TMP/stato.err"; then echo manca
  else maschera <"$TMP/stato.err" >&2; return 1
  fi
}
stato_ruolo="$(stato_risorsa iam roles describe "$ROLE_ID" --project="$PROJECT")"
stato_sa="$(stato_risorsa iam service-accounts describe "$SA" --project="$PROJECT")"

DA_FARE=("ruolo personalizzato ${ROLE_ID}: $([[ $stato_ruolo == manca ]] && echo creazione || echo controllo)"
         "account di servizio ${SA}: $([[ $stato_sa == manca ]] && echo creazione || echo controllo)"
         "binding sul progetto: serviceAccount:${SA} → ${ROLE}"
         "binding sull'account: user:${ME_VISIBILE} → ${TOKEN_CREATOR}")

echo
echo "== Riepilogo delle modifiche (niente è stato ancora modificato)"
echo "  Progetto:              ${PROJECT}"
echo "  Account che esegue:    ${ME_VISIBILE} (il tuo account attivo in gcloud)"
if [[ $stato_sa == manca ]]; then
  echo "  Account di servizio:   ${SA} → verrà CREATO (nessuna chiave)"
else
  echo "  Account di servizio:   ${SA} → esiste già, non verrà modificato"
fi
if [[ $stato_ruolo == manca ]]; then
  echo "  Ruolo personalizzato:  ${ROLE} → verrà CREATO (stage GA)"
else
  echo "  Ruolo personalizzato:  ${ROLE} → esiste già, verrà solo controllato (mai modificato)"
fi
echo "    permessi, esattamente: ${ROLE_PERMISSIONS//,/, }"
echo "  Binding IAM da aggiungere (se ci sono già, nessun doppione):"
echo "    1. progetto ${PROJECT}: serviceAccount:${SA} → ${ROLE}, senza condizioni"
echo "    2. account ${SA}: user:${ME_VISIBILE} → ${TOKEN_CREATOR}, senza condizioni"
echo "  API da attivare:       nessuna (cloudasset ${ASSET}: facoltativa, mai attivata da questo script)"
echo "  Non verranno creati chiavi né assegnati altri ruoli; nessun dato Firestore viene letto."
echo
printf 'Per confermare scrivi esattamente il nome del progetto (%s): ' "$PROJECT"
IFS= read -r answer || answer=""
echo
if [[ "$answer" != "$PROJECT" ]]; then
  echo "Conferma non valida. Nessuna modifica eseguita."; exit 1
fi
CONFERMATO=1

echo
echo "== 1. Ruolo personalizzato ${ROLE_ID}"
inizio "ruolo personalizzato ${ROLE_ID}"
if role_json="$(gcloud iam roles describe "$ROLE_ID" --project="$PROJECT" --format=json 2>"$TMP/ruolo.err")"; then
  # Esiste: deve essere attivo e contenere esattamente i 3 permessi. Non viene mai modificato qui.
  if ! python3 -c '
import json, sys
role = json.loads(sys.stdin.read())
expected = set(sys.argv[1].split(","))
ok = not role.get("deleted") and role.get("stage") == "GA" and set(role.get("includedPermissions", [])) == expected
sys.exit(0 if ok else 1)
' "$ROLE_PERMISSIONS" <<<"$role_json"; then
    echo "  ERRORE: il ruolo esiste ma è eliminato, non è in stage GA o non contiene esattamente i 3 permessi. Mi fermo."
    exit 1
  fi
  echo "  esiste già con la definizione esatta"
  fatto "ruolo ${ROLE_ID}: già presente con la definizione esatta (non modificato)"
elif ! grep -q 'NOT_FOUND' "$TMP/ruolo.err"; then
  maschera <"$TMP/ruolo.err" >&2
  echo "  ERRORE: impossibile verificare se il ruolo esiste (permessi insufficienti?). Mi fermo."
  exit 1
else
  gc iam roles create "$ROLE_ID" --project="$PROJECT" \
    --title="Firestore audit - sola lettura" \
    --description="Solo lettura documenti Firestore per l'audit. Nessuna scrittura." \
    --permissions="$ROLE_PERMISSIONS" --stage=GA --format=none
  echo "  creato con esattamente: ${ROLE_PERMISSIONS//,/, }"
  fatto "ruolo ${ROLE_ID}: CREATO con ${ROLE_PERMISSIONS//,/, }"
fi

echo "== 2. Account di servizio"
inizio "account di servizio ${SA}"
# Crea l'account solo se Google risponde NOT_FOUND; qualsiasi altro errore interrompe.
if gcloud iam service-accounts describe "$SA" --project="$PROJECT" --format=none 2>"$TMP/sa.err"; then
  echo "  esiste già: $SA"
  fatto "account ${SA}: già presente (non modificato)"
elif ! grep -q 'NOT_FOUND' "$TMP/sa.err"; then
  maschera <"$TMP/sa.err" >&2
  echo "  ERRORE: impossibile verificare se l'account esiste (permessi insufficienti?). Mi fermo."
  exit 1
else
  gc iam service-accounts create "$SA_ID" --project="$PROJECT" \
    --display-name="Firestore audit (sola lettura)" \
    --description="Solo il ruolo firestoreAuditReader. Nessuna chiave. Impersonato solo dal proprietario." \
    --format=none
  echo "  creato: $SA"
  fatto "account ${SA}: CREATO (nessuna chiave)"
  # Un account appena creato può impiegare qualche secondo a essere visibile a IAM.
  for _ in $(seq 1 12); do
    gcloud iam service-accounts describe "$SA" --project="$PROJECT" --format=none >/dev/null 2>&1 && break
    sleep 5
  done
fi

echo "== 3. Ruolo di sola lettura sul progetto"
inizio "binding sul progetto: serviceAccount:${SA} → ${ROLE}"
# --format=none: non stampa l'intero criterio IAM (conterrebbe email).
gc projects add-iam-policy-binding "$PROJECT" \
  --member="serviceAccount:$SA" --role="$ROLE" --condition=None --format=none
echo "  $ROLE assegnato (se c'era già, nessun duplicato)"
fatto "binding sul progetto: serviceAccount:${SA} → ${ROLE}"

echo "== 4. Impersonazione solo per il tuo account, solo su questo account di servizio"
inizio "binding sull'account: user:${ME_VISIBILE} → ${TOKEN_CREATOR}"
gc iam service-accounts add-iam-policy-binding "$SA" --project="$PROJECT" \
  --member="user:$ME" --role="$TOKEN_CREATOR" --condition=None --format=none
echo "  Token Creator assegnato a te sull'account firestore-audit"
fatto "binding sull'account: user:${ME_VISIBILE} → ${TOKEN_CREATOR}"

echo
echo "== Verifica finale (sola lettura, modalità rigorosa)"
IN_CORSO="verifica finale (01-verifica.sh --finale)"
finale=0
bash "$HERE/01-verifica.sh" --finale || finale=$?
if ((finale == 3)); then
  IN_CORSO=""
  SOLO_VERIFICA_INCOMPLETA=1
  echo
  echo "CONFIGURAZIONE APPLICATA, VERIFICA FINALE INCOMPLETA: nessun problema trovato, ma alcuni"
  echo "elementi non sono verificabili (per esempio Cloud Asset spenta: permessi su singole risorse"
  echo "non controllati). Non è un esito positivo: leggi le righe INCOMPLETO qui sopra."
  exit 3
elif ((finale != 0)); then
  echo
  echo "CONFIGURAZIONE NON VERIFICATA: la verifica finale ha trovato problemi o non è riuscita."
  echo "Non eseguire l'audit. Se hai appena creato l'account, riprova 01-verifica.sh --finale tra qualche minuto."
  exit 1
fi
IN_CORSO=""
echo
echo "Configurazione verificata. Aspetta 2-5 minuti (propagazione IAM), poi esegui: bash 03-prova-impersonazione.sh"
