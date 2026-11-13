#!/usr/bin/env python3
"""Analizza le configurazioni IAM raccolte da 01-verifica.sh (file JSON in una cartella).

Sola lettura: legge solo i file JSON passati, non chiama Google. Non stampa email di persone:
i membri sono descritti per tipo ("tu", "altro utente", "gruppo"...). Le email degli account
di servizio non sono dati personali e vengono mostrate.

Modalità (variabile MODO):
  preliminare  prima di 02-configura.sh: account, ruolo e binding previsti possono mancare
               (verranno creati), ma tutto il resto deve essere già pulito;
  finale       dopo 02-configura.sh: TUTTO deve essere presente ed esatto.
In entrambe le modalità qualsiasi elemento non verificabile impedisce l'esito positivo.

Uso:  analizza.py CARTELLA            analisi completa
      analizza.py CARTELLA --ruoli    elenca i ruoli assegnati da descrivere (per 01-verifica.sh)

Uscita: 0 nessun problema, 1 almeno un problema, 2 verifica impossibile.
"""
import json
import os
import re
import sys

PROJECT = os.environ["PROJECT"]
SA = os.environ["SA"]
ME = os.environ.get("ME", "").lower()
MODE = os.environ.get("MODO", "preliminare")
APPROVALS_FILE = os.environ.get("APPROVATI_FILE", "")
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from permessi import (  # noqa: E402
    APPROVE_GOOGLE_AGENTS, ROLE_NAME as ROLE, ROLE_PERMISSIONS, classify, dangerous, support_level,
)
TOKEN_CREATOR = "roles/iam.serviceAccountTokenCreator"
DIR = sys.argv[1]
FINAL = MODE == "finale"
REQUIRED_APIS = {
    "iamcredentials.googleapis.com": "serve a ottenere i token temporanei dell'account (impersonazione)",
    "cloudresourcemanager.googleapis.com": "serve a leggere i criteri IAM e a controllare i permessi effettivi",
    "firestore.googleapis.com": "è il servizio da controllare: senza, non c'è Firestore",
}

problems = 0
warnings = 0


def ok(msg): print(f"  OK       {msg}")
def info(msg): print(f"  INFO     {msg}")


def warn(msg):
    global warnings
    warnings += 1
    print(f"  AVVISO   {msg}")


def bad(msg):
    global problems
    problems += 1
    print(f"  PROBLEMA {msg}")


def title(msg): print(f"\n== {msg}")


EMAIL = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")


def mask(text):
    """Nasconde le email di persone; lascia quelle degli account di servizio."""
    return EMAIL.sub(lambda m: m.group(0) if m.group(0).endswith(".gserviceaccount.com") else "<email>", text)


def status(name):
    """ok | notfound | error | missing (lettura non eseguita)."""
    path = os.path.join(DIR, name + ".status")
    if not os.path.exists(path):
        return "missing"
    with open(path, encoding="utf-8") as f:
        return f.read().strip()


def reason(name):
    path = os.path.join(DIR, name + ".err")
    if not os.path.exists(path):
        return "lettura non eseguita"
    with open(path, encoding="utf-8", errors="replace") as f:
        lines = [l.strip() for l in f if l.strip()]
    return mask(lines[-1])[:240] if lines else "motivo non disponibile"


def stop(msg):
    """Elemento non verificabile: la verifica non può essere considerata superata."""
    bad(msg)
    print(f"\nVerifica INTERROTTA: {problems} problemi, {warnings} avvisi. Nessun esito positivo è possibile.")
    sys.exit(2)


def require(name, what, allow_notfound=False):
    st = status(name)
    if st == "ok" or (allow_notfound and st == "notfound"):
        return st
    stop(f"{what}: lettura non riuscita ({reason(name)}). "
         "Un errore di accesso non viene trattato come assenza di dati")


def load(name, default=None):
    path = os.path.join(DIR, name)
    if not os.path.exists(path) or os.path.getsize(path) == 0:
        return default
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def safe(name):
    return re.sub(r"[^A-Za-z0-9]", "_", name)


def describe_args(role):
    """Argomenti di 'gcloud iam roles describe' per un nome di ruolo completo."""
    m = re.fullmatch(r"projects/([^/]+)/roles/([^/]+)", role)
    if m:
        return [m.group(2), f"--project={m.group(1)}"]
    m = re.fullmatch(r"organizations/([^/]+)/roles/([^/]+)", role)
    if m:
        return [m.group(2), f"--organization={m.group(1)}"]
    return [role]


# --- Livelli IAM: progetto + cartelle/organizzazione ----------------------------------------
project = load("project.json") if status("project.json") == "ok" else None
parent = (project or {}).get("parent") or {}
ancestors = load("ancestors.json") if status("ancestors.json") == "ok" else None


def collect_levels():
    if ancestors:
        return [(a.get("type", "?"), a.get("id", "?"), (a.get("policy") or {}).get("bindings", [])) for a in ancestors]
    policy = load("project_policy.json") if status("project_policy.json") == "ok" else None
    return [("project", PROJECT, policy.get("bindings", []))] if policy is not None else []


if len(sys.argv) > 2 and sys.argv[2] == "--ruoli":
    # Solo l'elenco dei ruoli da leggere: nessun giudizio qui (lo fa l'analisi completa).
    seen = set()
    for _, _, bindings in collect_levels():
        for b in bindings:
            r = b.get("role", "")
            if r and r not in seen:
                seen.add(r)
                print("\t".join([safe(r)] + describe_args(r)))
    sys.exit(0)

print(f"Modalità: {MODE}" + (" (tutto deve essere presente ed esatto)" if FINAL else
                              " (account, ruolo e binding previsti possono ancora mancare)"))

# --- Identità approvate ------------------------------------------------------------------------
approved = set()
if APPROVALS_FILE and os.path.exists(APPROVALS_FILE):
    with open(APPROVALS_FILE, encoding="utf-8") as f:
        for line in f:
            line = line.split("#", 1)[0].strip()
            if line:
                approved.add(line if line == APPROVE_GOOGLE_AGENTS else line.lower())
approve_agents = APPROVE_GOOGLE_AGENTS in approved
n_identities = len(approved - {APPROVE_GOOGLE_AGENTS})

# --- 1. Progetto e gerarchia -------------------------------------------------------------
title("1. Progetto e gerarchia")
require("project.json", "dati del progetto")
if not project or project.get("projectId") != PROJECT:
    stop("progetto non leggibile o diverso da quello previsto")
number = str(project.get("projectNumber", ""))
ok(f"progetto {PROJECT} (stato: {project.get('lifecycleState', '?')})")
if project.get("lifecycleState", "ACTIVE") != "ACTIVE":
    bad(f"il progetto non è attivo ({project.get('lifecycleState')})")
if not parent:
    if ancestors is None:
        require("project_policy.json", "criteri IAM del progetto (servono almeno Viewer o Security Reviewer)")
    ok("nessuna cartella né organizzazione: non ci sono privilegi ereditati")
elif ancestors is None:
    stop(f"il progetto è sotto {parent.get('type')} {parent.get('id')}, ma i criteri IAM della gerarchia non "
         f"sono leggibili ({reason('ancestors.json')}): i privilegi ereditati non sono verificabili")
else:
    if not any(a.get("type") == "project" for a in ancestors):
        stop("i criteri della gerarchia non contengono il progetto: dati incompleti")
    ok("gerarchia letta: " + ", ".join(f"{a.get('type')} {a.get('id')}" for a in ancestors if a.get("type") != "project"))
levels = collect_levels()
if not levels:
    stop("criteri IAM del progetto non leggibili")
info("identità approvate: " + (f"{n_identities} dal file" if n_identities else "nessuna")
     + (", più gli agenti Google del progetto" if approve_agents else ""))


def label(level_type, level_id):
    return "progetto" if level_type == "project" else f"{level_type} {level_id}"


def google_agent(member):
    """Agente di servizio gestito da Google per questo progetto (non impersonabile da persone)."""
    if not member.startswith("serviceAccount:"):
        return False
    email = member.split(":", 1)[1]
    if email.endswith("@firebase-sa-management.iam.gserviceaccount.com"):
        return True
    return bool(number) and (email.startswith(f"service-{number}@") and email.endswith(".gserviceaccount.com")
                             or email == f"{number}@cloudservices.gserviceaccount.com")


def who(member):
    """Descrive un membro IAM senza stampare l'email di una persona."""
    if ME and member == f"user:{ME}":
        return "tu"
    if member == f"serviceAccount:{SA}":
        return "account di audit"
    if member.startswith("deleted:"):
        return "membro eliminato"
    if member.startswith("serviceAccount:"):
        email = member.split(":", 1)[1]
        return f"agente Google ({email})" if google_agent(member) else f"account di servizio ({email})"
    if member in ("allUsers", "allAuthenticatedUsers"):
        return f"{member} (PUBBLICO)"
    for prefix, lab in (("user:", "altro utente"), ("group:", "gruppo"), ("domain:", "dominio"),
                        ("principalSet:", "insieme di identità federate"), ("principal:", "identità federata")):
        if member.startswith(prefix):
            return lab
    return "altro tipo di membro"


def is_approved(member):
    if member in ("allUsers", "allAuthenticatedUsers"):
        return False  # mai approvabili
    if member.lower() in approved:
        return True
    return approve_agents and google_agent(member)


def condition_text(binding):
    cond = binding.get("condition")
    if not cond:
        return ""
    return mask(f" [condizione: {cond.get('title') or 'senza titolo'} → {cond.get('expression', '')}]")


# --- Permessi di ogni ruolo assegnato (predefinito o personalizzato) --------------------------
role_perms = {}
for _, _, bindings in levels:
    for b in bindings:
        r = b.get("role", "")
        if r in role_perms:
            continue
        name = f"ruolo_{safe(r)}.json"
        require(name, f"permessi del ruolo assegnato {r}")
        data = load(name, {})
        if data.get("name") and data["name"] != r:
            stop(f"la descrizione del ruolo {r} non corrisponde ({data.get('name')})")
        role_perms[r] = set(data.get("includedPermissions", []))

# --- 2. I tuoi privilegi --------------------------------------------------------------------
title("2. Il tuo account (serve per la configurazione)")
my_roles = sorted({b["role"] for t, i, bs in levels for b in bs if ME and f"user:{ME}" in b.get("members", [])})
info("i tuoi ruoli: " + ", ".join(my_roles) if my_roles else "nessun ruolo diretto (potresti averne tramite un gruppo)")
needed = {"roles/iam.roleAdmin", "roles/iam.serviceAccountAdmin", "roles/resourcemanager.projectIamAdmin"}
if "roles/owner" in my_roles or needed <= set(my_roles):
    ok("puoi eseguire 02-configura.sh")
elif not FINAL:
    warn("per 02-configura.sh servono Owner, oppure Role Administrator + Service Account Admin + Project IAM Admin")

# --- 3. Account di servizio ----------------------------------------------------------------
title("3. Account di servizio firestore-audit")
require("sa_list.json", "elenco degli account di servizio")
sa_state = require("sa.json", "dettagli dell'account firestore-audit", allow_notfound=True)
sa = load("sa.json") if sa_state == "ok" else None
sa_list = load("sa_list.json", [])
if sa is None:
    if FINAL:
        bad("l'account firestore-audit NON esiste")
    else:
        info("non esiste ancora: lo crea 02-configura.sh")
else:
    if sa.get("email", SA).lower() != SA:
        stop("i dati letti non sono quelli dell'account firestore-audit")
    if sa.get("disabled"):
        bad("l'account esiste ma è DISATTIVATO (02-configura.sh non lo riattiva: decisione tua)")
    else:
        ok(f"esiste ed è attivo: {SA}")
similar = [a["email"] for a in sa_list if "audit" in a.get("email", "") and a["email"] != SA]
if similar:
    warn("altri account con 'audit' nel nome (non verranno usati né toccati): " + ", ".join(similar))
else:
    ok("nessun account duplicato o simile")

# --- 4. Ruoli dell'account di audit (progetto + gerarchia) ------------------------------------
title("4. Ruoli dell'account di audit")
found_role = False
for t, i, bindings in levels:
    for b in bindings:
        for m in b.get("members", []):
            if m.startswith("deleted:") and SA in m:
                warn(f"{label(t, i)}: binding {b['role']} di un vecchio account eliminato con lo stesso "
                     "nome (non dà permessi, ma conviene rimuoverlo)")
            elif m == f"serviceAccount:{SA}":
                cond = condition_text(b)
                if b["role"] == ROLE and t == "project" and not cond:
                    found_role = True
                    ok(f"progetto: {ROLE}, senza condizioni")
                elif b["role"] == ROLE and cond:
                    bad(f"{label(t, i)}: {ROLE} con condizione{cond}: deve essere senza condizioni")
                else:
                    bad(f"{label(t, i)}: ruolo NON previsto {b['role']}{cond}")
            elif m in ("allUsers", "allAuthenticatedUsers"):
                bad(f"{label(t, i)}: {b['role']} concesso a {m}: vale per chiunque, account di audit compreso")
            elif m.startswith(("group:", "domain:", "principalSet:", "principal:")) and not is_approved(m):
                bad(f"{label(t, i)}: {b['role']} concesso a {who(m)}{condition_text(b)}: non posso verificare "
                    "se comprende l'account di audit (approvalo solo dopo averlo controllato)")
if not found_role:
    if FINAL:
        bad(f"{ROLE} NON è assegnato all'account sul progetto (senza condizioni)")
    elif sa is not None:
        info(f"{ROLE} non ancora assegnato: lo assegna 02-configura.sh")

# --- 5. Chi può impersonare l'account ---------------------------------------------------------
title("5. Chi può impersonare l'account di audit")
mine = False
if sa is not None:
    require("sa_policy.json", "permessi sull'account di servizio")
    sa_policy = load("sa_policy.json", {})
    for b in sa_policy.get("bindings", []):
        cond = condition_text(b)
        for m in b.get("members", []):
            if b["role"] == TOKEN_CREATOR and ME and m == f"user:{ME}" and not cond:
                mine = True
                ok("sull'account: tu hai Service Account Token Creator, senza condizioni")
            else:
                bad(f"sull'account: {who(m)} ha {b['role']}{cond} (ammesso solo Token Creator per te)")
if not mine:
    if FINAL:
        bad("Token Creator sull'account per il tuo account NON è presente")
    else:
        info("la tua autorizzazione all'impersonazione non c'è ancora: la aggiunge 02-configura.sh")

print("  Ruoli di progetto o superiori che permettono di impersonare l'account, crearne chiavi,")
print("  cambiarne i permessi o modificare il ruolo personalizzato:")
risky = 0
for t, i, bindings in levels:
    for b in bindings:
        found = dangerous(b["role"], role_perms.get(b["role"], set()))
        if not found:
            continue
        what = ", ".join(found[:4]) + (f" e altri {len(found) - 4}" if len(found) > 4 else "")
        for m in b.get("members", []):
            desc = who(m)
            line = f"{label(t, i)}: {desc} — {b['role']} ({what}){condition_text(b)}"
            if desc == "tu" or m.startswith("deleted:") or m == f"serviceAccount:{SA}":
                info(line)
            elif is_approved(m):
                info(line + " → approvata da te")
            else:
                risky += 1
                bad(line + " → identità non approvata: può agire come firestore-audit o cambiarne i permessi")
if not risky:
    ok("nessuna identità non approvata, a parte te")

# --- 6. Chiavi -------------------------------------------------------------------------------
title("6. Chiavi dell'account")
if sa is None:
    info("account non ancora creato")
else:
    require("keys.json", "elenco delle chiavi dell'account")
    keys = load("keys.json", [])
    if keys:
        bad(f"{len(keys)} chiavi create a mano: vanno eliminate (l'audit non usa chiavi)")
    else:
        ok("nessuna chiave creata a mano")

# --- 7. Condizioni e regole di divieto -------------------------------------------------------
title("7. Condizioni IAM e regole di divieto")
conditional = [(t, i, b) for t, i, bs in levels for b in bs if b.get("condition")]
if conditional:
    info(f"{len(conditional)} binding con condizioni nel progetto o sopra (quelli rilevanti sono già nei punti 4 e 5)")
else:
    ok("nessun binding con condizioni")
deny = load("deny.json") if status("deny.json") == "ok" else None
if deny is None:
    info("LIMITE: regole di divieto non leggibili; possono solo togliere permessi, mai aggiungerne")
elif deny:
    info(f"{len(deny)} regole di divieto sul progetto: tolgono permessi, non ne aggiungono")
else:
    ok("nessuna regola di divieto sul progetto")

# --- 8. API ----------------------------------------------------------------------------------
title("8. API necessarie")
require("services.json", "elenco delle API attive")
services = {s.get("config", {}).get("name") for s in load("services.json", [])}
for api, why in REQUIRED_APIS.items():
    if api in services:
        ok(f"{api} attiva")
    else:
        bad(f"{api} NON attiva: {why}. Non viene attivata da questi script: serve una tua autorizzazione separata")

# --- 9. Supporto dei permessi nei ruoli personalizzati ---------------------------------------
title("9. Permessi del ruolo personalizzato: esistenza e supporto")
require("testable.json", "permessi datastore verificabili sul progetto (list-testable-permissions)")
testable = {e["name"]: e for e in load("testable.json", []) if "name" in e}
for perm in ROLE_PERMISSIONS:
    entry = testable.get(perm)
    if entry is None:
        bad(f"{perm}: non risulta tra i permessi verificabili sul progetto")
        continue
    level = support_level(entry)
    stage = entry.get("stage", "?")
    raw = "campo omesso = valore predefinito" if "customRolesSupportLevel" not in entry else "campo presente"
    if level == "SUPPORTED":
        ok(f"{perm}: stage {stage}, ruoli personalizzati {level} ({raw})")
    else:
        bad(f"{perm}: stage {stage}, ruoli personalizzati {level}: non utilizzabile in un ruolo personalizzato")
schemas = sorted(n for n in testable if n.startswith("datastore.schemas."))
info("permessi datastore.schemas.* esistenti: " + (", ".join(schemas) if schemas else "nessuno"))

# --- 10. Definizione esatta del ruolo personalizzato ------------------------------------------
title(f"10. Definizione di {ROLE}")
role_state = require("custom_role.json", "definizione del ruolo personalizzato", allow_notfound=True)
if role_state == "notfound":
    if FINAL:
        bad("il ruolo personalizzato NON esiste")
    else:
        info("il ruolo non esiste ancora: lo crea 02-configura.sh con esattamente " + ", ".join(ROLE_PERMISSIONS))
else:
    role = load("custom_role.json", {})
    perms = set(role.get("includedPermissions", []))
    if role.get("deleted"):
        bad("il ruolo esiste ma è ELIMINATO: va ripristinato o scelto un altro nome (decisione tua)")
    if role.get("stage") == "DISABLED":
        bad("il ruolo esiste ma è DISATTIVATO (stage DISABLED): non concede permessi")
    missing = sorted(set(ROLE_PERMISSIONS) - perms)
    extra = sorted(perms - set(ROLE_PERMISSIONS))
    if not missing and not extra:
        ok(f"contiene esattamente i {len(ROLE_PERMISSIONS)} permessi previsti")
    else:
        bad(f"il ruolo non corrisponde alla definizione esatta ({len(missing)} mancanti, {len(extra)} in più)")
        for p in missing:
            bad(f"manca: {p}")
        for p in extra:
            kind = classify(p)
            if kind == "scrittura":
                bad(f"in più, di scrittura: {p}")
            elif kind == "sconosciuto":
                warn(f"in più, non classificato (da valutare a mano): {p}")
            else:
                info(f"in più, di lettura: {p}")

# --- 11. Limiti --------------------------------------------------------------------------------
title("11. Cosa questa verifica non può vedere")
info("permessi su risorse diverse dal progetto (per esempio singoli bucket) e appartenenza a gruppi: "
     "i permessi effettivi li controlla 03-prova-impersonazione.sh")

print(f"\nRiepilogo: {problems} problemi, {warnings} avvisi.")
if FINAL and not problems:
    print("Configurazione FINALE verificata: account, ruolo, binding, impersonazione, chiavi e API come previsto.")
sys.exit(1 if problems else 0)
