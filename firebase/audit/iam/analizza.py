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
      analizza.py CARTELLA --ambito   ambito della ricerca Cloud Asset (vuoto: ricerca da non fare)
      analizza.py CARTELLA --account  account di servizio da leggere (criteri e, se approvati, chiavi)
      analizza.py CARTELLA --ruoli    ruoli assegnati da descrivere (per 01-verifica.sh)

Uscita: 0 nessun problema, 1 almeno un problema, 2 verifica impossibile (interrotta),
        3 INCOMPLETO (nessun problema trovato, ma qualcosa non è verificabile).
"""
import json
import os
import re
import sys

PROJECT = os.environ["PROJECT"]
SA = os.environ["SA"].lower()
ME = os.environ.get("ME", "").lower()
MODE = os.environ.get("MODO", "preliminare")
APPROVALS_FILE = os.environ.get("APPROVATI_FILE", "")
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from permessi import (  # noqa: E402
    OBSOLETE_GOOGLE_AGENTS_LINE, PUBLIC_MEMBERS, ROLE_NAME as ROLE, ROLE_PERMISSIONS, classify,
    dangerous, google_agent_kind, project_service_account, support_level,
)
TOKEN_CREATOR = "roles/iam.serviceAccountTokenCreator"
CLOUDASSET = "cloudasset.googleapis.com"
DIR = sys.argv[1]
FINAL = MODE == "finale"
REQUIRED_APIS = {
    "iamcredentials.googleapis.com": "serve a ottenere i token temporanei dell'account (impersonazione)",
    "cloudresourcemanager.googleapis.com": "serve a leggere i criteri IAM e a controllare i permessi effettivi",
    "firestore.googleapis.com": "è il servizio da controllare: senza, non c'è Firestore",
}
# Membri che valgono per chiunque, account di audit compreso.
PUBLIC = set(PUBLIC_MEMBERS) | {"principalSet://goog/public:all"}
# Membri il cui contenuto non è verificabile da qui (potrebbero comprendere l'account di audit).
OPAQUE_PREFIXES = ("group:", "domain:", "principalSet:", "principal:")

problems = 0
warnings = 0
incompletes = 0


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


def incomplete(msg):
    """Elemento non verificabile: l'esito non può essere positivo, ma l'analisi continua."""
    global incompletes
    incompletes += 1
    print(f"  INCOMPLETO {msg}")


def title(msg): print(f"\n== {msg}")


EMAIL = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")


def mask(text):
    """Nasconde le email di persone; lascia quelle degli account di servizio."""
    return EMAIL.sub(lambda m: m.group(0) if m.group(0).endswith(".gserviceaccount.com") else "<email>", text)


def mask_me(email):
    local, _, domain = email.partition("@")
    return f"{local[:2]}•••@{domain}" if domain else "<email>"


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


def finish_counts():
    return f"{problems} problemi, {incompletes} non verificabili, {warnings} avvisi"


def stop(msg):
    """Elemento indispensabile non verificabile: la verifica si interrompe."""
    bad(msg)
    print(f"\nVerifica INTERROTTA: {finish_counts()}. Nessun esito positivo è possibile.")
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


# --- Dati di base ----------------------------------------------------------------------------
project = load("project.json") if status("project.json") == "ok" else None
parent = (project or {}).get("parent") or {}
number = str((project or {}).get("projectNumber", ""))
ancestors = load("ancestors.json") if status("ancestors.json") == "ok" else None
sa_list = load("sa_list.json", []) if status("sa_list.json") == "ok" else []
project_sas = {a.get("email", "").lower() for a in sa_list if a.get("email")}
services = ({s.get("config", {}).get("name") for s in load("services.json", [])}
            if status("services.json") == "ok" else None)


def collect_levels():
    if ancestors:
        return [(a.get("type", "?"), a.get("id", "?"), (a.get("policy") or {}).get("bindings", [])) for a in ancestors]
    policy = load("project_policy.json") if status("project_policy.json") == "ok" else None
    return [("project", PROJECT, policy.get("bindings", []))] if policy is not None else []


def asset_scope():
    """Ambito più alto della gerarchia per Cloud Asset; None se non determinabile."""
    if not parent:
        return f"projects/{PROJECT}"
    for a in ancestors or []:
        if a.get("type") == "organization":
            return f"organizations/{a.get('id')}"
    return None


# --- Identità approvate (file) ---------------------------------------------------------------
def read_approval_lines():
    lines = []
    if APPROVALS_FILE and os.path.exists(APPROVALS_FILE):
        with open(APPROVALS_FILE, encoding="utf-8") as f:
            for line in f:
                line = line.split("#", 1)[0].strip()
                if line:
                    lines.append(line)
    return lines


def approval_kind(entry):
    """(tipo, motivo del rifiuto). Tipo: 'persona' | 'account-progetto' | 'agente' | ''."""
    low = entry.lower()
    if low.startswith("user:"):
        return ("persona", "") if "@" in low else ("", "voce non valida")
    if low.startswith("serviceaccount:"):
        email = low.split(":", 1)[1]
        if email == SA:
            return "", "l'account di audit non si approva: sull'account vale solo Token Creator per te"
        if project_service_account(email, PROJECT, number):
            if email in project_sas:
                return "account-progetto", ""
            return "", "account del progetto che non esiste (o non compare nell'elenco degli account)"
        if google_agent_kind(email, number):
            return "agente", ""
        return "", ("account di servizio di un altro progetto o di un dominio condiviso: non è "
                    "verificabile chi può usarlo")
    if entry in PUBLIC or low in {m.lower() for m in PUBLIC}:
        return "", "membro pubblico: vale per chiunque, account di audit compreso"
    if low.startswith(("group:", "domain:")):
        return "", "gruppo o dominio: i membri non sono verificabili da qui"
    if low.startswith(("principalset:", "principal:")):
        return "", "identità federate: i membri non sono verificabili da qui"
    if low.startswith("deleted:"):
        return "", "membro eliminato: potrebbe essere ripristinato"
    return "", "tipo di membro non riconosciuto"


approved = {}        # membro (minuscolo) -> tipo
rejected = []        # (voce, motivo)
obsolete_line = False
for entry in read_approval_lines():
    if entry == OBSOLETE_GOOGLE_AGENTS_LINE:
        obsolete_line = True
        continue
    kind, why = approval_kind(entry)
    if kind:
        approved[entry.lower()] = kind
    else:
        rejected.append((entry, why))
approved_sas = sorted(m.split(":", 1)[1] for m, k in approved.items() if k == "account-progetto")


# --- Modalità di servizio per 01-verifica.sh ------------------------------------------------
if len(sys.argv) > 2 and sys.argv[2] == "--ambito":
    scope = asset_scope()
    if scope and services is not None and CLOUDASSET in services:
        print(scope)
    sys.exit(0)

if len(sys.argv) > 2 and sys.argv[2] == "--account":
    for email in sorted(project_sas - {SA}):
        print(f"{safe(email)}\t{email}\t{'chiavi' if email in approved_sas else ''}")
    sys.exit(0)


def sa_policy_bindings(email):
    data = load(f"sapol_{safe(email)}.json", {}) if status(f"sapol_{safe(email)}.json") == "ok" else None
    return None if data is None else data.get("bindings", [])


if len(sys.argv) > 2 and sys.argv[2] == "--ruoli":
    # Solo l'elenco dei ruoli da leggere: nessun giudizio qui (lo fa l'analisi completa).
    seen = set()
    groups = [bs for _, _, bs in collect_levels()]
    groups += [sa_policy_bindings(e) or [] for e in sorted(project_sas - {SA})]
    for bindings in groups:
        for b in bindings:
            r = b.get("role", "")
            if r and r not in seen:
                seen.add(r)
                print("\t".join([safe(r)] + describe_args(r)))
    sys.exit(0)

print(f"Modalità: {MODE}" + (" (tutto deve essere presente ed esatto)" if FINAL else
                              " (account, ruolo e binding previsti possono ancora mancare)"))

# --- 1. Progetto e gerarchia -------------------------------------------------------------
title("1. Progetto e gerarchia")
require("project.json", "dati del progetto")
if not project or project.get("projectId") != PROJECT:
    stop("progetto non leggibile o diverso da quello previsto")
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


def label(level_type, level_id):
    return "progetto" if level_type == "project" else f"{level_type} {level_id}"


def who(member):
    """Descrive un membro IAM senza stampare l'email di una persona."""
    low = member.lower()
    if ME and low == f"user:{ME}":
        return "tu"
    if low == f"serviceaccount:{SA}":
        return "account di audit"
    if low.startswith("deleted:"):
        return "membro eliminato"
    if low.startswith("serviceaccount:"):
        email = low.split(":", 1)[1]
        if google_agent_kind(email, number):
            return f"agente Google del progetto ({email})"
        if project_service_account(email, PROJECT, number):
            return f"account di servizio del progetto ({email})"
        return f"account di servizio esterno ({email})"
    if member in PUBLIC:
        return f"{member} (PUBBLICO)"
    for prefix, lab in (("user:", "altro utente"), ("group:", "gruppo"), ("domain:", "dominio"),
                        ("principalSet:", "insieme di identità federate"), ("principal:", "identità federata")):
        if member.startswith(prefix):
            return lab
    return "altro tipo di membro"


def is_approved(member):
    return member.lower() in approved


def condition_text(binding):
    cond = binding.get("condition")
    if not cond:
        return ""
    return mask(f" [condizione: {cond.get('title') or 'senza titolo'} → {cond.get('expression', '')}]")


# --- Permessi di ogni ruolo assegnato (gerarchia e account di servizio del progetto) ----------
role_perms = {}


def read_roles(bindings):
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


for _, _, bindings in levels:
    read_roles(bindings)

# --- 2. Il tuo account -----------------------------------------------------------------------
title("2. Il tuo account (serve per la configurazione)")
if ME:
    info(f"account gcloud attivo: {mask_me(ME)} (è l'unico \"tu\" escluso dai controlli; se non è il tuo, "
         "interrompi e cambia account con gcloud config set account)")
my_roles = sorted({b["role"] for t, i, bs in levels for b in bs
                   if ME and f"user:{ME}" in [m.lower() for m in b.get("members", [])]})
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
if sa is None:
    if FINAL:
        bad("l'account firestore-audit NON esiste")
    else:
        info("non esiste ancora: lo crea 02-configura.sh")
    if SA in project_sas:
        bad("dati incoerenti: l'account compare nell'elenco ma la sua descrizione dice che non esiste")
else:
    if sa.get("email", SA).lower() != SA:
        stop("i dati letti non sono quelli dell'account firestore-audit")
    if sa.get("disabled"):
        bad("l'account esiste ma è DISATTIVATO (02-configura.sh non lo riattiva: decisione tua)")
    else:
        ok(f"esiste ed è attivo: {SA}")
    if SA not in project_sas:
        bad("dati incoerenti: l'account esiste ma non compare nell'elenco degli account del progetto")
similar = sorted(e for e in project_sas if "audit" in e and e != SA)
if similar:
    warn("altri account con 'audit' nel nome (non verranno usati né toccati): " + ", ".join(similar))
else:
    ok("nessun account duplicato o simile")

# --- 4. Identità approvate ---------------------------------------------------------------------
title("4. Identità approvate (file)")
if not approved and not rejected and not obsolete_line:
    info("nessuna identità approvata")
for m, kind in sorted(approved.items()):
    info(f"approvata: {who(m)} [{kind}]")
for entry, why in rejected:
    bad(f"voce NON approvabile, ignorata: {who(entry)} → {why}")
if obsolete_line:
    warn(f"la riga '{OBSOLETE_GOOGLE_AGENTS_LINE}' non approva più nulla: approva ogni agente Google "
         "con il suo indirizzo esatto (serviceAccount:...), dopo averne controllato i ruoli")

# --- 5. Ruoli dell'account di audit (progetto + gerarchia) ------------------------------------
title("5. Ruoli dell'account di audit")
found_role = False
for t, i, bindings in levels:
    for b in bindings:
        for m in b.get("members", []):
            low = m.lower()
            if low.startswith("deleted:") and SA in low:
                warn(f"{label(t, i)}: binding {b['role']} di un vecchio account eliminato con lo stesso "
                     "nome (non dà permessi, ma conviene rimuoverlo)")
            elif low == f"serviceaccount:{SA}":
                cond = condition_text(b)
                if b["role"] == ROLE and t == "project" and not cond:
                    found_role = True
                    ok(f"progetto: {ROLE}, senza condizioni")
                elif b["role"] == ROLE and cond:
                    bad(f"{label(t, i)}: {ROLE} con condizione{cond}: deve essere senza condizioni")
                else:
                    bad(f"{label(t, i)}: ruolo NON previsto {b['role']}{cond}")
            elif m in PUBLIC:
                bad(f"{label(t, i)}: {b['role']} concesso a {m}: vale per chiunque, account di audit compreso")
            elif m.startswith(OPAQUE_PREFIXES):
                incomplete(f"{label(t, i)}: {b['role']} concesso a {who(m)}{condition_text(b)}: non posso "
                           "verificare se comprende l'account di audit (gruppi e identità federate non sono approvabili)")
if not found_role:
    if FINAL:
        bad(f"{ROLE} NON è assegnato all'account sul progetto (senza condizioni)")
    elif sa is not None:
        info(f"{ROLE} non ancora assegnato: lo assegna 02-configura.sh")

# --- 6. Chi può impersonare l'account ---------------------------------------------------------
title("6. Chi può impersonare l'account di audit")
mine = False
if sa is not None:
    require("sa_policy.json", "permessi sull'account di servizio")
    sa_policy = load("sa_policy.json", {})
    for b in sa_policy.get("bindings", []):
        cond = condition_text(b)
        for m in b.get("members", []):
            if b["role"] == TOKEN_CREATOR and ME and m.lower() == f"user:{ME}" and not cond:
                mine = True
                ok("sull'account: tu hai Service Account Token Creator, senza condizioni")
            else:
                bad(f"sull'account: {who(m)} ha {b['role']}{cond} (ammesso solo Token Creator per te)")
if not mine:
    if FINAL:
        bad("Token Creator sull'account per il tuo account NON è presente")
    else:
        info("la tua autorizzazione all'impersonazione non c'è ancora: la aggiunge 02-configura.sh")

print("  Ruoli di progetto o superiori che permettono di impersonare l'account, crearne o caricarne")
print("  chiavi, eliminarlo o ricrearlo, cambiarne i permessi o modificare il ruolo personalizzato:")


def already_has_audit_access(member):
    """True se il membro ha GIÀ, con binding senza condizioni nel progetto o sopra, tutti i permessi
    dell'account di audit: impersonarlo non gli darebbe nulla in più (privilegi equivalenti o
    superiori). Si contano solo i binding senza condizioni: una condizione può restringerli."""
    perms = set()
    for _, _, bs in levels:
        for b in bs:
            if not b.get("condition") and member.lower() in [x.lower() for x in b.get("members", [])]:
                perms |= role_perms.get(b["role"], set())
    return set(ROLE_PERMISSIONS) <= perms


risky = 0
equivalent = 0
powerful_approved = set()
for t, i, bindings in levels:
    for b in bindings:
        found = dangerous(b["role"], role_perms.get(b["role"], set()))
        if not found:
            continue
        what = ", ".join(found[:4]) + (f" e altri {len(found) - 4}" if len(found) > 4 else "")
        for m in b.get("members", []):
            desc = who(m)
            line = f"{label(t, i)}: {desc} — {b['role']} ({what}){condition_text(b)}"
            if desc == "tu":
                info(line)
            elif m in PUBLIC:
                risky += 1
                bad(line + " → membro pubblico: vale per chiunque")
            elif already_has_audit_access(m):
                # Rischio documentato, non bloccante: chi ha già tutti i permessi dell'account di audit
                # (e di norma molti di più) può impersonarlo, ma così non ottiene nulla di nuovo. Il
                # rischio residuo è che modifichi l'account o il ruolo: lo rilevano 01-verifica.sh
                # (definizione esatta), 03-prova-impersonazione.sh e il controllo dei permessi
                # dell'audit prima di ogni lettura.
                equivalent += 1
                if is_approved(m):
                    powerful_approved.add(m.lower())
                warn(line + " → può impersonare firestore-audit, ma ha GIÀ privilegi equivalenti o superiori "
                     "(tutti i permessi di lettura dell'audit): non ottiene nulla in più. Rischio residuo: "
                     "potrebbe modificare account o ruolo; lo rilevano le verifiche prima di ogni audit"
                     + (" (membro eliminato: li riottiene se ripristinato)" if m.lower().startswith("deleted:") else ""))
            elif m.lower().startswith("deleted:"):
                risky += 1
                bad(line + " → membro eliminato con privilegi pericolosi: se viene ripristinato li riottiene")
            elif is_approved(m):
                powerful_approved.add(m.lower())
                if approved[m.lower()] == "persona":
                    incomplete(line + " → approvata da te, ma è una persona: non posso verificare chi usa il "
                               "suo account, quindi l'esito non può essere positivo")
                else:
                    info(line + " → approvata da te")
            else:
                risky += 1
                bad(line + " → identità non approvata: può agire come firestore-audit o cambiarne i permessi")
if not risky:
    ok("nessuna identità con privilegi pericolosi che potrebbe ottenere, tramite l'account di audit, "
       "permessi che non ha già" + (f" ({equivalent} con privilegi equivalenti o superiori: AVVISO)" if equivalent else ""))

# --- 7. Chiavi -------------------------------------------------------------------------------
title("7. Chiavi dell'account")
if sa is None:
    info("account non ancora creato")
else:
    require("keys.json", "elenco delle chiavi dell'account")
    keys = load("keys.json", [])
    if keys:
        bad(f"{len(keys)} chiavi create a mano: vanno eliminate (l'audit non usa chiavi)")
    else:
        ok("nessuna chiave creata a mano")

# --- 8. Altri account di servizio del progetto ------------------------------------------------
title("8. Altri account di servizio del progetto (escalation e catene di impersonazione)")


def check_resource_member(where, role, member, cond, approved_target=False):
    """Un membro di un criterio IAM su una singola risorsa. Restituisce True se rilevante."""
    low = member.lower()
    found = dangerous(role, role_perms.get(role, set()))
    if low == f"serviceaccount:{SA}":
        extra = " → può agire con i privilegi di un altro account di servizio (ESCALATION)" if (
            found and "account di servizio" in where) else ""
        bad(f"{where}: l'account di audit ha {role}{cond}: permesso NON previsto{extra}")
        return True
    if low.startswith("deleted:"):
        if SA in low:
            warn(f"{where}: binding {role} di un vecchio account di audit eliminato (conviene rimuoverlo)")
            return True
        if approved_target and found:
            bad(f"{where}: membro eliminato con {role}: se viene ripristinato può agire come un account approvato")
            return True
        return False
    if member in PUBLIC:
        bad(f"{where}: {role} concesso a {member}{cond}: vale per chiunque, account di audit compreso")
        return True
    if member.startswith(OPAQUE_PREFIXES):
        if approved_target and found:
            bad(f"{where}: {who(member)} ha {role}{cond}: i suoi membri possono agire come un account approvato")
        else:
            incomplete(f"{where}: {role} concesso a {who(member)}{cond}: non posso verificare se comprende "
                       "l'account di audit")
        return True
    if approved_target and found and who(member) != "tu" and not is_approved(member):
        bad(f"{where}: {who(member)} ha {role}{cond} → può agire come un account approvato che ha privilegi "
            "sull'account di audit")
        return True
    return False


others = sorted(project_sas - {SA})
if not others:
    ok("nessun altro account di servizio nel progetto")
for email in others:
    bindings = sa_policy_bindings(email)
    if bindings is None:
        incomplete(f"permessi sull'account {email} non leggibili ({reason(f'sapol_{safe(email)}.json')}): "
                   "non posso escludere che l'account di audit possa agire come lui")
        continue
    read_roles(bindings)
    hits = 0
    for b in bindings:
        for m in b.get("members", []):
            hits += check_resource_member(f"account di servizio {email}", b["role"], m, condition_text(b),
                                          approved_target=email in approved_sas)
    if not hits:
        ok(f"{email}: nessun permesso per l'account di audit, nessun gruppo o membro pubblico")
for email in approved_sas:
    if f"serviceaccount:{email}" not in powerful_approved:
        continue
    st = status(f"keys_{safe(email)}.json")
    if st != "ok":
        incomplete(f"chiavi dell'account approvato {email} non leggibili ({reason(f'keys_{safe(email)}.json')})")
    elif load(f"keys_{safe(email)}.json", []):
        if already_has_audit_access(f"serviceAccount:{email}"):
            warn(f"l'account {email} ha chiavi create a mano: chi le possiede agisce come lui, che ha già "
                 "privilegi equivalenti o superiori all'account di audit (rischio di sicurezza generale "
                 "del progetto, non della sola lettura dell'audit)")
        else:
            incomplete(f"l'account approvato {email} ha privilegi pericolosi e chiavi create a mano: chi possiede "
                       "una chiave può agire come lui, e questo non è verificabile")
    else:
        ok(f"{email} (approvato, con privilegi pericolosi): nessuna chiave creata a mano")

# --- 9. Permessi su singole risorse (Cloud Asset Inventory) ------------------------------------
title("9. Permessi su singole risorse (Cloud Asset Inventory)")
require("services.json", "elenco delle API attive")
scope = asset_scope()
asset_needed = FINAL  # nella verifica preliminare la sua assenza è solo informativa
asset_ok = False
if CLOUDASSET not in services:
    msg = (f"{CLOUDASSET} NON attiva: non posso cercare i permessi concessi su singole risorse (bucket, "
           "secret, altri account, altri progetti). Non viene attivata da questi script")
    if asset_needed:
        incomplete(msg)
    else:
        info(msg + " (l'account non esiste ancora; servirà per la verifica finale)")
elif scope is None:
    incomplete("organizzazione non individuata nella gerarchia: non posso definire un ambito di ricerca "
               "che copra tutta la gerarchia")
elif status("asset.json") != "ok":
    incomplete(f"ricerca Cloud Asset su {scope} non riuscita ({reason('asset.json')}): servono "
               "cloudasset.assets.searchAllIamPolicies sull'ambito e serviceusage.services.use sul progetto")
else:
    asset_ok = True
    results = load("asset.json", []) or []
    skip = {f"//cloudresourcemanager.googleapis.com/projects/{PROJECT}"}
    if number:
        skip.add(f"//cloudresourcemanager.googleapis.com/projects/{number}")
    for a in ancestors or []:
        if a.get("type") == "folder":
            skip.add(f"//cloudresourcemanager.googleapis.com/folders/{a.get('id')}")
        elif a.get("type") == "organization":
            skip.add(f"//cloudresourcemanager.googleapis.com/organizations/{a.get('id')}")
    own_project = {f"projects/{PROJECT}", f"projects/{number}"} if number else {f"projects/{PROJECT}"}
    checked = hits = 0
    for res in results:
        name = res.get("resource", "")
        atype = res.get("assetType", "")
        if name in skip:
            continue  # progetto, cartelle e organizzazione: già controllati nelle sezioni 5 e 6
        if atype == "iam.googleapis.com/ServiceAccount" and res.get("project") in own_project:
            continue  # account di servizio del progetto: letti direttamente (sezioni 6 e 8)
        checked += 1
        where = f"{'account di servizio' if atype == 'iam.googleapis.com/ServiceAccount' else atype or 'risorsa'} {name}"
        for b in (res.get("policy") or {}).get("bindings", []):
            for m in b.get("members", []):
                hits += check_resource_member(where, b.get("role", "?"), m, condition_text(b))
    ok(f"ricerca su {scope}: {len(results)} criteri IAM trovati, {checked} su singole risorse controllati")
    if scope.startswith("organizations/"):
        info(f"copertura: tutta l'organizzazione {scope.split('/')[1]} (progetti, cartelle e risorse "
             "indicizzate da Cloud Asset)")
    else:
        info("copertura: il progetto, che non ha cartelle né organizzazione (la gerarchia è il solo progetto)")
    if not hits:
        ok("nessun permesso diretto all'account di audit su singole risorse; nessun gruppo o membro pubblico")
info("NON coperti: progetti fuori da questa gerarchia, risorse o tipi non indicizzati da Cloud Asset, "
     "modifiche degli ultimi minuti non ancora indicizzate")

# --- 10. Condizioni e regole di divieto ------------------------------------------------------
title("10. Condizioni IAM e regole di divieto")
conditional = [(t, i, b) for t, i, bs in levels for b in bs if b.get("condition")]
if conditional:
    info(f"{len(conditional)} binding con condizioni nel progetto o sopra (quelli rilevanti sono già nei punti 5 e 6)")
else:
    ok("nessun binding con condizioni")
deny = load("deny.json") if status("deny.json") == "ok" else None
if deny is None:
    info("LIMITE: regole di divieto non leggibili. Non cambiano l'esito: questa verifica controlla i "
         "permessi CONCESSI e non considera mai un divieto come garanzia di sola lettura")
elif deny:
    info(f"{len(deny)} regole di divieto sul progetto: NON vengono usate come garanzia (possono essere "
         "tolte); i permessi concessi sono controllati comunque")
else:
    ok("nessuna regola di divieto sul progetto")

# --- 11. API ----------------------------------------------------------------------------------
title("11. API necessarie")
for api, why in REQUIRED_APIS.items():
    if api in services:
        ok(f"{api} attiva")
    else:
        bad(f"{api} NON attiva: {why}. Non viene attivata da questi script: serve una tua autorizzazione separata")
if CLOUDASSET in services:
    ok(f"{CLOUDASSET} attiva (usata solo in lettura per la sezione 9)")

# --- 12. Supporto dei permessi nei ruoli personalizzati ---------------------------------------
title("12. Permessi del ruolo personalizzato: esistenza e supporto")
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

# --- 13. Definizione esatta del ruolo personalizzato ------------------------------------------
title(f"13. Definizione di {ROLE}")
role_state = require("custom_role.json", "definizione del ruolo personalizzato", allow_notfound=True)
if role_state == "notfound":
    if FINAL:
        bad("il ruolo personalizzato NON esiste")
    else:
        info("il ruolo non esiste ancora: lo crea 02-configura.sh con esattamente " + ", ".join(ROLE_PERMISSIONS))
else:
    role = load("custom_role.json", {})
    perms = set(role.get("includedPermissions", []))
    if role.get("name") and role["name"] != ROLE:
        stop(f"la descrizione letta non è quella di {ROLE}")
    if role.get("deleted"):
        bad("il ruolo esiste ma è ELIMINATO: va ripristinato o scelto un altro nome (decisione tua)")
    if "stage" not in role:
        bad("stage del ruolo omesso, cioè ALPHA (valore predefinito dell'API IAM): previsto GA")
    elif role["stage"] == "DISABLED":
        bad("il ruolo esiste ma è DISATTIVATO (stage DISABLED): non concede permessi")
    elif role["stage"] != "GA":
        bad(f"stage del ruolo {role['stage']}: previsto GA (02-configura.sh lo crea GA)")
    elif not role.get("deleted"):
        ok("ruolo attivo: stage GA, non eliminato")
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

# --- 14. Limiti --------------------------------------------------------------------------------
title("14. Cosa questa verifica non può vedere")
info("appartenenza ai gruppi e identità federate: ogni loro ruolo rende l'esito INCOMPLETO")
info("progetti fuori dalla gerarchia, risorse non indicizzate da Cloud Asset" +
     ("" if asset_ok else " e, in questa esecuzione, tutte le singole risorse (ricerca non eseguita)"))
info("chi può usare gli agenti Google approvati (li gestisce Google)")
info("i permessi EFFETTIVI sul progetto li controlla 03-prova-impersonazione.sh")

print(f"\nRiepilogo: {finish_counts()}.")
if problems:
    sys.exit(1)
if incompletes:
    print("Esito: INCOMPLETO. Alcuni elementi non sono verificabili: nessun esito positivo è possibile.")
    sys.exit(3)
if FINAL:
    print("Configurazione FINALE verificata: account, ruolo, binding, impersonazione, chiavi, singole "
          "risorse e API come previsto.")
sys.exit(0)
