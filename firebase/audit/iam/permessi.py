"""Definizioni comuni agli script IAM: ruolo personalizzato e classificazione dei permessi.

La classificazione è ESPLICITA. Un permesso è:
  - "scrittura"   se l'azione finale (dopo l'ultimo punto) è un'azione che modifica qualcosa o
                  concede privilegi (create, update, delete, setIamPolicy, getAccessToken...);
  - "lettura"     se l'azione è in un elenco chiuso di azioni di sola lettura;
  - "sconosciuto" in tutti gli altri casi. Un permesso sconosciuto NON viene mai trattato come
                  scrittura né come lettura: va valutato a mano (AVVISO).
"""

PROJECT = "nexus-social-e3eee"
ROLE_ID = "firestoreAuditReader"
ROLE_NAME = f"projects/{PROJECT}/roles/{ROLE_ID}"
ROLE_TITLE = "Firestore audit - sola lettura"
# Esattamente i permessi usati dall'audit (documentazione Firestore, permessi per metodo):
#   batchGet (getAll) → entities.get; list (listDocuments) → entities.get + entities.list;
#   listCollectionIds (listCollections) → entities.list; databases.list → databases.list.
ROLE_PERMISSIONS = ("datastore.databases.list", "datastore.entities.get", "datastore.entities.list")

# Azioni di sola lettura (elenco chiuso).
READ_ACTIONS = frozenset({
    "get", "list", "getMetadata", "getIamPolicy",
    "listTagBindings", "listEffectiveTags", "listEffectiveTagBindings",
})

# Azioni che modificano o concedono privilegi: corrispondenza esatta...
WRITE_ACTIONS = frozenset({
    "create", "update", "delete", "undelete", "patch", "write", "commit", "run", "upload",
    "import", "export", "restore", "restoreDatabase", "clone", "bulkDelete", "allocateIds",
    "setIamPolicy", "actAs", "getAccessToken", "getOpenIdToken", "signJwt", "signBlob",
    "implicitDelegation", "enable", "disable", "move", "purge", "deploy", "invoke",
})
# ...oppure per prefisso (createTagBinding, deleteTagBinding, setMetadata, updateTag...).
WRITE_PREFIXES = ("create", "update", "delete", "set", "remove", "add", "import", "export",
                  "restore", "undelete", "upload", "bulk")


def action(permission: str) -> str:
    return permission.rsplit(".", 1)[-1]


def classify(permission: str) -> str:
    """'scrittura' | 'lettura' | 'sconosciuto'. Le azioni di scrittura hanno la precedenza:
    getAccessToken inizia con 'get' ma concede privilegi."""
    act = action(permission)
    if act in WRITE_ACTIONS or act.startswith(WRITE_PREFIXES):
        return "scrittura"
    if act in READ_ACTIONS:
        return "lettura"
    return "sconosciuto"


def support_level(entry: dict) -> str:
    """Livello di supporto nei ruoli personalizzati di una voce di list-testable-permissions.

    Nell'API IAM, SUPPORTED è il valore 0 dell'enum ("Default state. Permission is fully
    supported for custom role use."): nel JSON i valori predefiniti vengono omessi, quindi un
    campo assente significa SUPPORTED. Qualsiasi altro valore viene restituito così com'è.
    """
    return entry.get("customRolesSupportLevel") or "SUPPORTED"


# --- Privilegi che permettono di agire come l'account di audit o di allargarne i permessi ------
# Chi ha anche uno solo di questi permessi, a livello di progetto o superiore, può:
#   - ottenere token o firme dell'account (impersonazione);
#   - farlo usare a una risorsa che gira con la sua identità (actAs);
#   - creargli o caricargli una chiave;
#   - eliminarlo e ricrearlo o ripristinarlo, disattivarlo o riattivarlo;
#   - cambiare chi può impersonarlo, i criteri IAM, le regole di divieto, le policy
#     dell'organizzazione o il ruolo personalizzato;
#   - usare Deployment Manager, che agisce con l'agente Google APIs (di norma Editor).
DANGEROUS_PERMISSIONS = frozenset({
    "iam.serviceAccounts.getAccessToken",
    "iam.serviceAccounts.getOpenIdToken",
    "iam.serviceAccounts.signBlob",
    "iam.serviceAccounts.signJwt",
    "iam.serviceAccounts.implicitDelegation",
    "iam.serviceAccounts.actAs",
    "iam.serviceAccounts.setIamPolicy",
    "iam.serviceAccounts.create",
    "iam.serviceAccounts.delete",
    "iam.serviceAccounts.undelete",
    "iam.serviceAccounts.disable",
    "iam.serviceAccounts.enable",
    "iam.serviceAccountKeys.create",
    "iam.serviceAccountKeys.upload",
    "iam.roles.update",
    "iam.roles.delete",
    "iam.roles.undelete",
    "resourcemanager.projects.setIamPolicy",
    "resourcemanager.folders.setIamPolicy",
    "resourcemanager.organizations.setIamPolicy",
    "orgpolicy.policy.set",
    "deploymentmanager.deployments.create",
    "deploymentmanager.deployments.update",
})
# Famiglie di permessi amministrativi: pericoloso ogni permesso della famiglia che non sia di sola
# lettura (iam.denypolicies.create/update/delete sì, iam.denypolicies.get/list no).
DANGEROUS_FAMILIES = ("iam.denypolicies.", "orgpolicy.policies.", "iam.principalaccessboundarypolicies.")

# Ruoli predefiniti considerati pericolosi comunque, anche se la loro descrizione letta da Google
# non contenesse i permessi qui sopra (difesa in profondità).
DANGEROUS_ROLES = frozenset({
    "roles/owner",
    "roles/editor",
    "roles/iam.serviceAccountTokenCreator",
    "roles/iam.serviceAccountOpenIdTokenCreator",
    "roles/iam.serviceAccountUser",
    "roles/iam.serviceAccountAdmin",
    "roles/iam.serviceAccountKeyAdmin",
    "roles/iam.workloadIdentityUser",
    "roles/iam.securityAdmin",
    "roles/iam.roleAdmin",
    "roles/iam.organizationRoleAdmin",
    "roles/iam.denyAdmin",
    "roles/orgpolicy.policyAdmin",
    "roles/deploymentmanager.editor",
    "roles/resourcemanager.projectIamAdmin",
    "roles/resourcemanager.folderAdmin",
    "roles/resourcemanager.folderIamAdmin",
    "roles/resourcemanager.organizationAdmin",
    "roles/firebase.admin",
})

PUBLIC_MEMBERS = ("allUsers", "allAuthenticatedUsers")


def dangerous(role: str, permissions) -> list:
    """Permessi pericolosi di un ruolo (ordinati); il nome del ruolo se è nell'elenco fisso."""
    found = sorted(p for p in set(permissions)
                   if p in DANGEROUS_PERMISSIONS
                   or (p.startswith(DANGEROUS_FAMILIES) and classify(p) != "lettura"))
    if not found and role in DANGEROUS_ROLES:
        found = [f"(ruolo {role})"]
    return found


# --- Agenti di servizio gestiti da Google -----------------------------------------------------
# Un agente è riconosciuto SOLO se il suo indirizzo contiene il numero di QUESTO progetto e il
# dominio è di Google (account non creabili dagli utenti):
#   service-NUMERO@gcp-sa-SERVIZIO.iam.gserviceaccount.com, alcuni domini storici elencati qui
#   sotto, NUMERO@cloudservices.gserviceaccount.com (agente Google APIs).
# Non sono agenti: firebase-sa-management (dominio condiviso tra progetti, non legato a questo),
# NUMERO@cloudbuild.gserviceaccount.com (chi crea build agisce come lui), qualsiasi account di
# altri progetti. Un agente non è MAI approvato in automatico: va approvato per indirizzo esatto.
LEGACY_AGENT_DOMAINS = frozenset({
    "compute-system.iam.gserviceaccount.com",
    "container-engine-robot.iam.gserviceaccount.com",
    "containerregistry.iam.gserviceaccount.com",
    "gs-project-accounts.iam.gserviceaccount.com",
    "firebase-rules.iam.gserviceaccount.com",
    "serverless-robot-prod.iam.gserviceaccount.com",
    "cloud-ml.google.com.iam.gserviceaccount.com",
    "dataflow-service-producer-prod.iam.gserviceaccount.com",
})


def google_agent_kind(email: str, number: str) -> str:
    """'agente' se è un agente Google di questo progetto, altrimenti ''."""
    email = email.lower()
    if not number or "@" not in email:
        return ""
    local, domain = email.split("@", 1)
    if email == f"{number}@cloudservices.gserviceaccount.com":
        return "agente"
    if local != f"service-{number}":
        return ""
    if domain in LEGACY_AGENT_DOMAINS:
        return "agente"
    if domain.startswith("gcp-sa-") and domain.endswith(".iam.gserviceaccount.com"):
        svc = domain[len("gcp-sa-"):-len(".iam.gserviceaccount.com")]
        if svc and all(c.isalnum() or c == "-" for c in svc):
            return "agente"
    return ""


def project_service_account(email: str, project: str, number: str) -> bool:
    """Account di servizio creato in QUESTO progetto (gestito dall'utente)."""
    email = email.lower()
    return (email.endswith(f"@{project}.iam.gserviceaccount.com")
            or email == f"{project}@appspot.gserviceaccount.com"
            or (bool(number) and email == f"{number}-compute@developer.gserviceaccount.com"))


# Riga del vecchio formato del file delle approvazioni: non approva più nulla.
OBSOLETE_GOOGLE_AGENTS_LINE = "agenti-google-del-progetto"
