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
