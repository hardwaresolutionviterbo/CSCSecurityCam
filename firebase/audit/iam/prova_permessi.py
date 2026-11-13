#!/usr/bin/env python3
"""Permessi EFFETTIVI dell'account di audit sul progetto, su TUTTI i permessi verificabili.

Uso: printf '%s\\n' "$TOKEN" | python3 prova_permessi.py testable.json

- Il token temporaneo dell'account impersonato arriva su stdin e viene passato a curl su stdin
  (--config -): non compare mai negli argomenti dei processi e non viene stampato.
- testable.json è l'output JSON di `gcloud iam list-testable-permissions` sul progetto: l'elenco
  completo dei permessi che Google sa verificare a livello di progetto.
- I permessi vengono chiesti a testIamPermissions a blocchi di 100 (limite dell'API). Se Google
  rifiuta un blocco come non valido (400 INVALID_ARGUMENT), il blocco viene diviso fino a isolare
  i permessi non verificabili, che vengono elencati.
- Nessuna chiamata a Firestore.

Uscita: 0 esattamente i 3 permessi previsti; 1 permessi mancanti o in più; 2 errore (verifica
impossibile); 3 verifica incompleta (alcuni permessi non verificabili).
"""
import json
import os
import re
import subprocess
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from permessi import PROJECT, ROLE_PERMISSIONS, classify  # noqa: E402

URL = f"https://cloudresourcemanager.googleapis.com/v1/projects/{PROJECT}:testIamPermissions"
BATCH = 100
MIN_TESTABLE = 100  # un elenco più corto indica una risposta anomala: niente esito positivo
# Permessi critici che devono comparire tra quelli verificati (controllo di completezza).
CRITICAL = (
    "datastore.entities.create", "datastore.entities.update", "datastore.entities.delete",
    "datastore.databases.create", "datastore.databases.update", "datastore.databases.delete",
    "datastore.databases.export", "datastore.databases.import",
    "datastore.schemas.create", "datastore.schemas.update", "datastore.schemas.delete",
    "firebaserules.releases.create", "firebaserules.releases.update", "firebaserules.rulesets.create",
    "resourcemanager.projects.setIamPolicy", "iam.serviceAccountKeys.create",
    "iam.serviceAccounts.actAs", "iam.serviceAccounts.getAccessToken",
)
EMAIL = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")


def mask(text):
    text = EMAIL.sub(lambda m: m.group(0) if m.group(0).endswith(".gserviceaccount.com") else "<email>", text)
    return " ".join(str(text).split())[:240]


class Fatal(Exception):
    pass


class Invalid(Exception):
    pass


def ask(token, permissions, workdir):
    body = os.path.join(workdir, "body.json")
    with open(body, "w", encoding="utf-8") as f:
        json.dump({"permissions": permissions}, f)
    cmd = ["curl", "-sS", "--config", "-", "-X", "POST", "-H", "Content-Type: application/json",
           "--data-binary", f"@{body}", "-w", "\n%{http_code}", URL]
    res = subprocess.run(cmd, input=f'header = "Authorization: Bearer {token}"\n',
                         capture_output=True, text=True)
    if res.returncode != 0:
        raise Fatal(f"richiesta non riuscita: {mask(res.stderr)}")
    payload, _, code = res.stdout.rpartition("\n")
    try:
        data = json.loads(payload)
    except ValueError:
        raise Fatal(f"risposta non valida da Google (HTTP {code.strip()})")
    if "error" in data:
        err = data["error"]
        if err.get("status") == "INVALID_ARGUMENT" or code.strip() == "400":
            raise Invalid(mask(err.get("message", "")))
        raise Fatal(f"errore Google: {err.get('status')} {mask(err.get('message', ''))}")
    return set(data.get("permissions", []))


def check(token, permissions, workdir, granted, unverifiable):
    try:
        granted |= ask(token, permissions, workdir) & set(permissions)
    except Invalid:
        if len(permissions) == 1:
            unverifiable.append(permissions[0])
            return
        mid = len(permissions) // 2
        check(token, permissions[:mid], workdir, granted, unverifiable)
        check(token, permissions[mid:], workdir, granted, unverifiable)


def main():
    token = sys.stdin.readline().strip()
    if not token:
        print("  PROBLEMA token mancante")
        return 2
    try:
        with open(sys.argv[1], encoding="utf-8") as f:
            entries = json.load(f)
    except (OSError, ValueError):
        print("  PROBLEMA elenco dei permessi verificabili non leggibile")
        return 2
    names = sorted({e["name"] for e in entries if isinstance(e, dict) and e.get("name")})
    print(f"  INFO     permessi verificabili sul progetto secondo Google: {len(names)}")
    if len(names) < MIN_TESTABLE:
        print(f"  PROBLEMA elenco troppo corto (meno di {MIN_TESTABLE}): verifica impossibile")
        return 2
    absent = [p for p in (*ROLE_PERMISSIONS, *CRITICAL) if p not in names]
    for p in absent:
        print(f"  AVVISO   {p}: non presente tra i permessi verificabili sul progetto")

    granted, unverifiable = set(), []
    with tempfile.TemporaryDirectory() as workdir:
        try:
            for i in range(0, len(names), BATCH):
                check(token, names[i:i + BATCH], workdir, granted, unverifiable)
        except Fatal as exc:
            print(f"  PROBLEMA {exc}")
            print("\nEsito: verifica IMPOSSIBILE. Non eseguire l'audit.")
            return 2
    token = None  # noqa: F841

    checked = len(names) - len(unverifiable)
    print(f"  INFO     permessi verificati: {checked} su {len(names)}")
    missing = [p for p in ROLE_PERMISSIONS if p not in granted]
    extra = sorted(granted - set(ROLE_PERMISSIONS))
    for p in ROLE_PERMISSIONS:
        print(f"  {'OK      ' if p in granted else 'PROBLEMA'} lettura prevista: {p}")
    for p in extra:
        kind = classify(p)
        label = "PROBLEMA" if kind == "scrittura" else "AVVISO  "
        print(f"  {label} permesso NON previsto ({kind}): {p}")
    for p in unverifiable:
        print(f"  AVVISO   non verificabile (rifiutato da Google come non valido): {p}")

    if missing or extra:
        print(f"\nEsito: NON conforme ({len(missing)} mancanti, {len(extra)} in più). Non eseguire l'audit.")
        return 1
    if unverifiable or absent:
        print(f"\nEsito: verifica INCOMPLETA ({len(unverifiable) + len(absent)} permessi non verificati). "
              "Non è un esito positivo.")
        return 3
    print(f"\nEsito: l'account ha ESATTAMENTE i {len(ROLE_PERMISSIONS)} permessi previsti "
          f"su {len(names)} verificabili sul progetto.")
    print("Limite: sono SOLO i permessi sul progetto. Questa prova non vede i permessi su singole")
    print("risorse (bucket, secret, altri account di servizio) né su altri progetti. 01-verifica.sh")
    print("li cerca con Cloud Asset Inventory solo se l'API è già attiva e solo nella gerarchia del")
    print("progetto; i progetti fuori dalla gerarchia non li controlla nessuno dei due script.")
    print("Un permesso tolto da una regola di divieto può non comparire qui pur essendo concesso.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
