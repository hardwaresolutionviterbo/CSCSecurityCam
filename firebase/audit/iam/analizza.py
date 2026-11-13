#!/usr/bin/env python3
"""Analizza le configurazioni IAM raccolte da 01-verifica.sh (file JSON in una cartella).

Sola lettura: legge solo i file JSON passati, non chiama Google. Non stampa email di persone:
i membri sono descritti per tipo ("tu", "altro utente", "gruppo"...). Le email degli account
di servizio non sono dati personali e vengono mostrate.

Uscita: 0 nessun problema, 1 almeno un problema, 2 dati mancanti.
"""
import json
import os
import re
import sys

PROJECT = os.environ["PROJECT"]
SA = os.environ["SA"]
ME = os.environ["ME"].lower()
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from permessi import ROLE_NAME as ROLE, ROLE_PERMISSIONS, classify, support_level  # noqa: E402
TOKEN_CREATOR = "roles/iam.serviceAccountTokenCreator"
DIR = sys.argv[1]

# Ruoli di progetto che permettono, direttamente o assegnandosi permessi, di agire come
# QUALSIASI account di servizio del progetto, quindi anche come firestore-audit.
IMPERSONATION_ROLES = {
    "roles/owner": "proprietario",
    "roles/editor": "editor (può usare gli account di servizio)",
    "roles/iam.serviceAccountTokenCreator": "creazione token di tutti gli account",
    "roles/iam.serviceAccountUser": "uso di tutti gli account",
    "roles/iam.serviceAccountAdmin": "amministrazione degli account",
    "roles/iam.workloadIdentityUser": "workload identity su tutti gli account",
    "roles/iam.securityAdmin": "amministrazione della sicurezza IAM",
    "roles/resourcemanager.projectIamAdmin": "amministrazione IAM del progetto",
    "roles/firebase.admin": "Firebase Admin",
}
REQUIRED_APIS = {
    "iamcredentials.googleapis.com": "impersonazione (token temporanei)",
    "cloudresourcemanager.googleapis.com": "controllo dei permessi dell'audit",
    "firestore.googleapis.com": "Firestore",
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
        return "motivo non disponibile"
    with open(path, encoding="utf-8", errors="replace") as f:
        lines = [l.strip() for l in f if l.strip()]
    return mask(lines[-1])[:240] if lines else "motivo non disponibile"


def stop(msg):
    """Errore di accesso: la verifica non può essere considerata superata."""
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


def who(member):
    """Descrive un membro IAM senza stampare l'email di una persona."""
    if member == f"user:{ME}":
        return "tu"
    if member == f"serviceAccount:{SA}":
        return "account di audit"
    if member.startswith("deleted:"):
        return "membro eliminato"
    if member.startswith("serviceAccount:"):
        email = member.split(":", 1)[1]
        if "@gcp-sa-" in email or email.endswith("@cloudservices.gserviceaccount.com") \
                or email.endswith("@firebase-sa-management.iam.gserviceaccount.com"):
            return f"agente Google ({email})"
        return f"account di servizio ({email})"
    if member in ("allUsers", "allAuthenticatedUsers"):
        return f"{member} (PUBBLICO)"
    for prefix, label in (("user:", "altro utente"), ("group:", "gruppo"), ("domain:", "dominio"),
                          ("principal", "identità federata")):
        if member.startswith(prefix):
            return label
    return "altro tipo di membro"


def condition_text(binding):
    cond = binding.get("condition")
    if not cond:
        return ""
    return f" [condizione: {cond.get('title') or 'senza titolo'} → {cond.get('expression', '')}]"


# --- 1. Progetto e gerarchia -------------------------------------------------------------
title("1. Progetto e gerarchia")
require("project.json", "dati del progetto")
project = load("project.json")
if not project or project.get("projectId") != PROJECT:
    bad("progetto non leggibile o diverso da quello previsto")
    sys.exit(2)
ok(f"progetto {PROJECT} (stato: {project.get('lifecycleState', '?')})")
parent = project.get("parent") or {}
ancestors = load("ancestors.json")
if not parent:
    ok("nessuna cartella né organizzazione: non ci sono privilegi ereditati")
elif ancestors is None:
    warn(f"il progetto è sotto {parent.get('type')} {parent.get('id')}, ma non ho potuto leggerne i "
         "criteri IAM: chiedi a chi amministra l'organizzazione di controllare i ruoli ereditati")
else:
    levels = ", ".join(f"{a.get('type')} {a.get('id')}" for a in ancestors if a.get("type") != "project")
    ok(f"gerarchia letta: {levels}")

# Tutti i binding: progetto + cartelle/organizzazione (se leggibili).
levels = []
if ancestors:
    for a in ancestors:
        levels.append((a.get("type", "?"), a.get("id", "?"), (a.get("policy") or {}).get("bindings", [])))
else:
    require("project_policy.json", "criteri IAM del progetto (servono almeno Viewer o Security Reviewer)")
    policy = load("project_policy.json")
    levels.append(("project", PROJECT, policy.get("bindings", [])))


def label(level_type, level_id):
    return "progetto" if level_type == "project" else f"{level_type} {level_id}"


# --- 2. I tuoi privilegi --------------------------------------------------------------------
title("2. Il tuo account (serve per la configurazione)")
my_roles = sorted({b["role"] for t, i, bs in levels for b in bs if f"user:{ME}" in b.get("members", [])})
if my_roles:
    info("i tuoi ruoli: " + ", ".join(my_roles))
else:
    info("nessun ruolo diretto (potresti averne tramite un gruppo)")
can_configure = "roles/owner" in my_roles or {
    "roles/iam.serviceAccountAdmin", "roles/resourcemanager.projectIamAdmin"} <= set(my_roles)
if can_configure:
    ok("puoi eseguire 02-configura.sh")
else:
    warn("per 02-configura.sh servono Owner, oppure Service Account Admin + Project IAM Admin "
         "(+ Service Usage Admin se mancano API)")

# --- 3. Account di servizio ----------------------------------------------------------------
title("3. Account di servizio firestore-audit")
require("sa_list.json", "elenco degli account di servizio")
sa_state = require("sa.json", "dettagli dell'account firestore-audit", allow_notfound=True)
sa = load("sa.json") if sa_state == "ok" else None
sa_list = load("sa_list.json", [])
if sa is None:
    info("non esiste ancora: lo crea 02-configura.sh")
else:
    ok(f"esiste: {SA}")
    if sa.get("disabled"):
        warn("è disattivato: l'audit non potrà usarlo")
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
                    warn(f"{label(t, i)}: {ROLE} con condizione{cond}: la configurazione prevista è senza condizioni")
                else:
                    bad(f"{label(t, i)}: ruolo NON previsto {b['role']}{cond}")
            elif m in ("allUsers", "allAuthenticatedUsers"):
                bad(f"{label(t, i)}: {b['role']} concesso a {m}: vale per chiunque, account di audit compreso")
            elif m.startswith("principalSet:"):
                warn(f"{label(t, i)}: {b['role']} concesso a un insieme di identità federate: "
                     "verifica che non includa l'account di audit")
if sa is not None and not found_role:
    info(f"{ROLE} non ancora assegnato: lo assegna 02-configura.sh")
info("i ruoli ricevuti tramite gruppi Google non sono visibili qui: non aggiungere l'account a gruppi")

# --- 5. Chi può impersonare l'account ---------------------------------------------------------
title("5. Chi può impersonare l'account di audit")
if sa is not None:
    require("sa_policy.json", "permessi sull'account di servizio")
    sa_policy = load("sa_policy.json", {})
    mine = False
    for b in sa_policy.get("bindings", []):
        cond = condition_text(b)
        for m in b.get("members", []):
            if b["role"] == TOKEN_CREATOR and m == f"user:{ME}" and not cond:
                mine = True
                ok("tu: Service Account Token Creator, senza condizioni")
            elif b["role"] == TOKEN_CREATOR and m == f"user:{ME}":
                warn(f"tu: Token Creator con condizione{cond}")
            else:
                bad(f"sull'account: {who(m)} ha {b['role']}{cond}")
    if not mine:
        info("la tua autorizzazione all'impersonazione non c'è ancora: la aggiunge 02-configura.sh")

print("  Ruoli di progetto o superiori che permettono di agire come QUALSIASI account di servizio:")
others = 0
for t, i, bindings in levels:
    for b in bindings:
        if b["role"] not in IMPERSONATION_ROLES:
            continue
        for m in b.get("members", []):
            desc = who(m)
            line = f"{label(t, i)}: {desc} — {IMPERSONATION_ROLES[b['role']]}{condition_text(b)}"
            if desc == "tu" or desc.startswith("agente Google"):
                info(line)
            else:
                others += 1
                warn(line + " → può impersonare anche firestore-audit")
if not others:
    ok("nessun altro, a parte te e gli agenti gestiti da Google")

# --- 6. Chiavi -------------------------------------------------------------------------------
title("6. Chiavi dell'account")
keys = []
if sa is not None:
    require("keys.json", "elenco delle chiavi dell'account")
    keys = load("keys.json", [])
if sa is None:
    info("account non ancora creato")
elif keys:
    bad(f"{len(keys)} chiavi create a mano: vanno eliminate (l'audit non usa chiavi)")
else:
    ok("nessuna chiave creata a mano")

# --- 7. Condizioni e regole di divieto -------------------------------------------------------
title("7. Condizioni IAM e regole di divieto")
conditional = [(t, i, b) for t, i, bs in levels for b in bs if b.get("condition")]
if conditional:
    info(f"{len(conditional)} binding con condizioni nel progetto o sopra (quelli sull'account sono già nel punto 4)")
else:
    ok("nessun binding con condizioni")
deny = load("deny.json")
if deny is None:
    info("regole di divieto non leggibili (servono permessi di revisione della sicurezza); "
         "possono solo togliere permessi, mai aggiungerne")
elif deny:
    info(f"{len(deny)} regole di divieto sul progetto: tolgono permessi, non ne aggiungono; "
         "se l'audit dà errore di permessi, controllale")
else:
    ok("nessuna regola di divieto sul progetto")

# --- 8. API ----------------------------------------------------------------------------------
title("8. API necessarie")
require("services.json", "elenco delle API attive")
services = {s.get("config", {}).get("name") for s in load("services.json", [])}
for api, why in REQUIRED_APIS.items():
    if api in services:
        ok(f"{api} attiva ({why})")
    elif api == "firestore.googleapis.com":
        bad(f"{api} non attiva: il progetto non ha Firestore attivo?")
    else:
        warn(f"{api} non attiva ({why}): la attiva 02-configura.sh")

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
    info("il ruolo non esiste ancora: lo crea 02-configura.sh con esattamente "
         + ", ".join(ROLE_PERMISSIONS))
else:
    role = load("custom_role.json", {})
    perms = set(role.get("includedPermissions", []))
    if role.get("deleted"):
        bad("il ruolo esiste ma è ELIMINATO: va ripristinato o scelto un altro nome (decisione tua)")
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

print(f"\nRiepilogo: {problems} problemi, {warnings} avvisi.")
sys.exit(1 if problems else 0)
