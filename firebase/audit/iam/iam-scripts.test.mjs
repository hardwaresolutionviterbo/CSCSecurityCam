// Test degli script IAM con gcloud e curl SIMULATI: nessuna chiamata a Google.
//
// Il finto gcloud registra ogni comando, risponde con i file JSON dello scenario e, se lo
// scenario lo prevede, fallisce con il messaggio indicato. Dopo il primo comando che modifica
// qualcosa passa allo stato "after", per simulare la verifica finale di 02-configura.sh.
//
// Esecuzione: node --test iam-scripts.test.mjs   (servono bash, python3, ps)

import { describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { chmodSync, existsSync, mkdirSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = fileURLToPath(new URL('.', import.meta.url));
const P = 'nexus-social-e3eee';
const SA = `firestore-audit@${P}.iam.gserviceaccount.com`;
const ME_RAW = 'Mario.Rossi@gmail.com';
const ME = `user:${ME_RAW.toLowerCase()}`;
const OTHER = 'user:altra.persona@gmail.com';
const TOKEN = 'ya29.TOKEN-SEGRETO-DI-PROVA-1234567890';
const PERSONAL = [/mario\.rossi@gmail\.com/i, /altra\.persona@gmail\.com/i];

const READ = ['datastore.entities.get', 'datastore.entities.list', 'datastore.databases.list'];
const ROLE = { includedPermissions: ['datastore.entities.get', 'datastore.entities.list', 'datastore.databases.getMetadata', 'datastore.databases.list', 'datastore.indexes.list', 'resourcemanager.projects.get'] };
const SERVICES = ['iamcredentials.googleapis.com', 'cloudresourcemanager.googleapis.com', 'firestore.googleapis.com'].map((name) => ({ config: { name } }));
const PROJECT = { projectId: P, lifecycleState: 'ACTIVE' };
const BASE_POLICY = { bindings: [
  { role: 'roles/owner', members: [ME] },
  { role: 'roles/iam.serviceAccountTokenCreator', members: ['serviceAccount:service-123@gcp-sa-firebase.iam.gserviceaccount.com'] },
] };
const CONFIGURED_POLICY = { bindings: [...BASE_POLICY.bindings, { role: 'roles/datastore.viewer', members: [`serviceAccount:${SA}`] }] };
const ancestors = (policy) => [{ id: P, type: 'project', policy }];

// Stato prima della configurazione: l'account non esiste.
const before = () => ({
  project: PROJECT, project_policy: BASE_POLICY, ancestors: ancestors(BASE_POLICY), sa_list: [],
  deny: [], services: SERVICES, role: ROLE,
});
// Stato configurato correttamente.
const configured = () => ({
  project: PROJECT, project_policy: CONFIGURED_POLICY, ancestors: ancestors(CONFIGURED_POLICY),
  sa_list: [{ email: SA }], sa: { email: SA, disabled: false },
  sa_policy: { bindings: [{ role: 'roles/iam.serviceAccountTokenCreator', members: [ME] }] },
  keys: [], deny: [], services: SERVICES, role: ROLE,
});

const NOT_FOUND = 'ERROR: (gcloud.iam.service-accounts.describe) NOT_FOUND: Unknown service account';
const DENIED = (perm) => `ERROR: (gcloud) PERMISSION_DENIED: Permission '${perm}' denied for ${ME_RAW} on resource (or it may not exist).`;

const GCLOUD = `#!/usr/bin/env bash
echo "$*" >> "$FAKE/calls.log"
[ "\${CLOUDSDK_CORE_DISABLE_PROMPTS:-}" = 1 ] || echo "PROMPTS_ENABLED $*" >> "$FAKE/calls.log"
D="$FAKE/state"; [ -f "$FAKE/modified" ] && [ -d "$FAKE/after" ] && D="$FAKE/after"
case "$1 $2 $3" in
  "config get-value account") echo "${ME_RAW}"; exit 0;;
  "projects describe "*) k=project;;
  "projects get-iam-policy "*) k=project_policy;;
  "projects get-ancestors-iam-policy "*) k=ancestors;;
  "iam service-accounts list") k=sa_list;;
  "iam service-accounts describe") k=sa;;
  "iam service-accounts get-iam-policy") k=sa_policy;;
  "iam service-accounts keys") k=keys;;
  "iam policies list") k=deny;;
  "services list --enabled") k=services;;
  "iam roles describe") k=role;;
  "auth print-access-token "*) k=token;;
  "services enable "*)
    [ -f "$FAKE/flip_on_enable" ] && touch "$FAKE/modified"; exit 0;;
  "iam service-accounts create"|"projects add-iam-policy-binding "*|"iam service-accounts add-iam-policy-binding")
    touch "$FAKE/modified"; exit 0;;
  *) echo "ERROR: comando non previsto dal simulatore: $*" >&2; exit 3;;
esac
if [ -f "$D/$k.fail" ]; then
  # Simula la domanda di gcloud "attivare l'API?": compare solo se i prompt sono abilitati.
  if [ -f "$D/$k.prompt" ] && [ "\${CLOUDSDK_CORE_DISABLE_PROMPTS:-}" != 1 ]; then
    printf 'API not enabled. Would you like to enable and retry? (y/N)? ' >&2
    read -r ans && [ "$ans" = y ] && echo "services enable (da prompt)" >> "$FAKE/calls.log"
  fi
  cat "$D/$k.fail" >&2; exit 1
fi
[ "$k" = token ] && { cat "$D/token.txt"; exit 0; }
[ -f "$D/$k.json" ] || { echo "${NOT_FOUND}" >&2; exit 1; }
case "$*" in
  *"--format=value(config.name)"*) python3 -c 'import json,sys; [print(s["config"]["name"]) for s in json.load(open(sys.argv[1]))]' "$D/$k.json";;
  *) cat "$D/$k.json";;
esac
`;

// Finto curl: salva argomenti, stdin e TUTTI i processi visibili in quel momento.
const CURL = `#!/usr/bin/env bash
printf '%s\\n' "$@" > "$FAKE/curl.args"
cat > "$FAKE/curl.stdin"
ps -eo args > "$FAKE/ps.txt" 2>/dev/null || cat /proc/[0-9]*/cmdline 2>/dev/null | tr '\\0' ' ' > "$FAKE/ps.txt"
[ -f "$FAKE/curl.fail" ] && { cat "$FAKE/curl.fail" >&2; exit 6; }
cat "$FAKE/curl.response"
`;

function scenario(state, { after, fails = {}, prompts = [], token = TOKEN, curlResponse, curlFail } = {}) {
  const dir = mkdtempSync(join(tmpdir(), 'iam-'));
  const bin = join(dir, 'bin');
  mkdirSync(bin);
  writeFileSync(join(bin, 'gcloud'), GCLOUD);
  writeFileSync(join(bin, 'curl'), CURL);
  chmodSync(join(bin, 'gcloud'), 0o755);
  chmodSync(join(bin, 'curl'), 0o755);
  const write = (name, files) => {
    mkdirSync(join(dir, name));
    for (const [k, v] of Object.entries(files)) {
      if (v !== undefined) writeFileSync(join(dir, name, `${k}.json`), JSON.stringify(v));
    }
  };
  write('state', state);
  if (after) write('after', after);
  for (const [k, msg] of Object.entries(fails)) writeFileSync(join(dir, 'state', `${k}.fail`), msg);
  for (const k of prompts) writeFileSync(join(dir, 'state', `${k}.prompt`), '');
  writeFileSync(join(dir, 'state', 'token.txt'), token);
  if (curlResponse) writeFileSync(join(dir, 'curl.response'), JSON.stringify(curlResponse));
  if (curlFail) writeFileSync(join(dir, 'curl.fail'), curlFail);
  writeFileSync(join(dir, 'calls.log'), '');
  return dir;
}

function run(dir, script, input = '') {
  const env = { PATH: `${join(dir, 'bin')}:${process.env.PATH}`, FAKE: dir, HOME: dir };
  const res = spawnSync('bash', [join(HERE, script)], { env, input, encoding: 'utf8', timeout: 60_000 });
  assert.notEqual(res.error?.code, 'ETIMEDOUT', 'lo script si è bloccato (domanda interattiva?)');
  const calls = readFileSync(join(dir, 'calls.log'), 'utf8').split('\n').filter(Boolean);
  return { code: res.status, out: `${res.stdout}${res.stderr}`, calls, dir };
}

const MODIFYING = /^(services enable|iam service-accounts (create|delete|add-iam|remove-iam|keys create|keys delete|enable|disable)|projects (add|remove|set)-iam|.*set-iam-policy|.*\bcreate\b|.*\bdelete\b)/;
const READ_ONLY = /^(config get-value account|projects describe|projects get-iam-policy|projects get-ancestors-iam-policy|iam service-accounts (list|describe|get-iam-policy|keys list)|iam policies list|services list|iam roles describe)( |$)/;

function assertReadOnly(calls) {
  for (const c of calls) {
    assert.doesNotMatch(c, /^PROMPTS_ENABLED/, `gcloud con prompt abilitati: ${c}`);
    assert.match(c, READ_ONLY, `comando non di sola lettura: ${c}`);
  }
}
function assertProjectAlwaysExpected(calls) {
  for (const c of calls) {
    for (const m of c.matchAll(/--project=(\S+)/g)) assert.equal(m[1], P, c);
    for (const m of c.matchAll(/^projects \S+ (\S+)/g)) assert.equal(m[1], P, c);
  }
}
function assertNoPersonalData(out) {
  for (const re of PERSONAL) assert.doesNotMatch(out, re);
  assert.ok(!out.includes(TOKEN), 'token stampato');
}

describe('01-verifica.sh', () => {
  test('prima della configurazione: account inesistente (NOT_FOUND) → nessun problema, solo lettura', () => {
    const r = run(scenario(before()), '01-verifica.sh');
    assert.equal(r.code, 0, r.out);
    assert.match(r.out, /non esiste ancora: lo crea 02-configura\.sh/);
    assertReadOnly(r.calls);
    assertProjectAlwaysExpected(r.calls);
    assertNoPersonalData(r.out);
  });

  test('configurazione corretta → 0 problemi, solo lettura', () => {
    const r = run(scenario(configured()), '01-verifica.sh');
    assert.equal(r.code, 0, r.out);
    assert.match(r.out, /Riepilogo: 0 problemi, 0 avvisi/);
    assertReadOnly(r.calls);
    assertNoPersonalData(r.out);
  });

  test('permessi insufficienti su "describe" → PROBLEMA e verifica interrotta, mai "non esiste"', () => {
    const r = run(scenario(before(), { fails: { sa: DENIED('iam.serviceAccounts.get') } }), '01-verifica.sh');
    assert.equal(r.code, 2, r.out);
    assert.match(r.out, /PROBLEMA dettagli dell'account firestore-audit: lettura non riuscita \(.*PERMISSION_DENIED/);
    assert.match(r.out, /Verifica INTERROTTA/);
    assert.doesNotMatch(r.out, /non esiste ancora|Riepilogo: 0 problemi/);
    assertReadOnly(r.calls);
    assertNoPersonalData(r.out);
    assert.match(r.out, /<email>/, 'email personale mascherata nel motivo');
  });

  test('errore su "keys list" → PROBLEMA e verifica interrotta, mai "nessuna chiave"', () => {
    const r = run(scenario(configured(), { fails: { keys: DENIED('iam.serviceAccountKeys.list') } }), '01-verifica.sh');
    assert.equal(r.code, 2, r.out);
    assert.match(r.out, /PROBLEMA elenco delle chiavi dell'account: lettura non riuscita/);
    assert.doesNotMatch(r.out, /nessuna chiave creata a mano/);
    assertReadOnly(r.calls);
    assertNoPersonalData(r.out);
  });

  test('errori sulle altre letture obbligatorie → verifica interrotta', () => {
    for (const k of ['project', 'sa_list', 'sa_policy', 'services', 'role']) {
      const r = run(scenario(configured(), { fails: { [k]: 'ERROR: (gcloud) PERMISSION_DENIED: denied' } }), '01-verifica.sh');
      assert.equal(r.code, 2, `${k}: ${r.out}`);
      assert.match(r.out, /Verifica INTERROTTA/, k);
    }
  });

  test('API non attiva: nessuna domanda interattiva, nessuna attivazione, verifica interrotta', () => {
    const r = run(scenario(configured(), {
      fails: { services: "ERROR: (gcloud.services.list) PERMISSION_DENIED: Service Usage API has not been used in project or it is disabled." },
      prompts: ['services'],
    }), '01-verifica.sh', 'y\ny\ny\n'); // anche rispondendo "y" a eventuali domande
    assert.equal(r.code, 2, r.out);
    assert.ok(!r.calls.some((c) => /^services enable|da prompt/.test(c)), 'nessuna attivazione di API');
    assert.ok(!r.calls.some((c) => c.startsWith('PROMPTS_ENABLED')), 'prompt disattivati');
    assert.doesNotMatch(r.out, /Would you like to enable/);
  });

  test('controllo del simulatore: con i prompt abilitati la domanda comparirebbe e "y" attiverebbe l\'API', () => {
    const dir = scenario(configured(), { fails: { services: 'ERROR: API disabled' }, prompts: ['services'] });
    const env = { PATH: process.env.PATH, FAKE: dir };
    const res = spawnSync(join(dir, 'bin', 'gcloud'), ['services', 'list', '--enabled'], { env, input: 'y\n', encoding: 'utf8' });
    assert.match(res.stderr, /Would you like to enable/);
    assert.match(readFileSync(join(dir, 'calls.log'), 'utf8'), /services enable \(da prompt\)/);
  });

  test('problemi di configurazione → uscita 1', () => {
    const bad = configured();
    bad.sa_policy.bindings[0].members.push(OTHER);
    bad.keys = [{ name: 'k1' }];
    const r = run(scenario(bad), '01-verifica.sh');
    assert.equal(r.code, 1, r.out);
    assert.match(r.out, /PROBLEMA sull'account: altro utente ha roles\/iam\.serviceAccountTokenCreator/);
    assert.match(r.out, /PROBLEMA 1 chiavi create a mano/);
    assertNoPersonalData(r.out);
  });
});

describe('02-configura.sh', () => {
  const modifications = (calls) => calls.filter((c) => MODIFYING.test(c));

  test('conferma errata del progetto → nessuna modifica, uscita diversa da zero', () => {
    for (const answer of ['', 'no', 'nexus-social', 'NEXUS-SOCIAL-E3EEE', 'nexus-social-e3eee ']) {
      const r = run(scenario(before(), { after: configured() }), '02-configura.sh', `${answer}\n`);
      assert.notEqual(r.code, 0, `risposta ${JSON.stringify(answer)}`);
      assert.deepEqual(modifications(r.calls), [], `risposta ${JSON.stringify(answer)}`);
      assert.match(r.out, /Conferma non valida\. Nessuna modifica eseguita/);
    }
  });

  test('senza input (stdin chiuso) → nessuna modifica', () => {
    const r = run(scenario(before(), { after: configured() }), '02-configura.sh', '');
    assert.notEqual(r.code, 0);
    assert.deepEqual(modifications(r.calls), []);
  });

  test('verifica preliminare con problemi → nessuna domanda, nessuna modifica', () => {
    const r = run(scenario(before(), { fails: { sa: DENIED('iam.serviceAccounts.get') } }), '02-configura.sh', 'nexus-social-e3eee\n');
    assert.equal(r.code, 1);
    assert.match(r.out, /Mi fermo: la verifica ha trovato problemi/);
    assert.deepEqual(modifications(r.calls), []);
  });

  test('conferma corretta e verifica finale superata → solo i 4 tipi di modifica previsti, nessuna chiave', () => {
    const state = before();
    state.services = SERVICES.filter((s) => s.config.name !== 'cloudresourcemanager.googleapis.com');
    const r = run(scenario(state, { after: configured() }), '02-configura.sh', 'nexus-social-e3eee\n');
    assert.equal(r.code, 0, r.out);
    assert.match(r.out, /Configurazione verificata\./);
    const mods = modifications(r.calls);
    assert.deepEqual(mods.map((c) => c.split(' --')[0]), [
      'services enable cloudresourcemanager.googleapis.com',
      'iam service-accounts create firestore-audit',
      `projects add-iam-policy-binding ${P}`,
      `iam service-accounts add-iam-policy-binding ${SA}`,
    ]);
    assert.ok(mods.some((c) => c.includes(`--member=serviceAccount:${SA} --role=roles/datastore.viewer --condition=None --format=none`)));
    assert.ok(mods.some((c) => c.includes(`--member=${ME} --role=roles/iam.serviceAccountTokenCreator --condition=None --format=none`)));
    assert.ok(!r.calls.some((c) => /keys create|keys upload/.test(c)), 'nessuna chiave');
    assert.ok(!r.calls.some((c) => c.startsWith('PROMPTS_ENABLED')));
    assertProjectAlwaysExpected(r.calls.filter((c) => !c.startsWith('PROMPTS')));
    assertNoPersonalData(r.out);
  });

  test('verifica finale non superata → uscita diversa da zero, nessun messaggio di successo', () => {
    const wrong = configured();
    wrong.project_policy = { bindings: [...CONFIGURED_POLICY.bindings, { role: 'roles/editor', members: [`serviceAccount:${SA}`] }] };
    wrong.ancestors = ancestors(wrong.project_policy);
    const r = run(scenario(before(), { after: wrong }), '02-configura.sh', 'nexus-social-e3eee\n');
    assert.equal(r.code, 1, r.out);
    assert.match(r.out, /CONFIGURAZIONE NON VERIFICATA/);
    assert.doesNotMatch(r.out, /Configurazione verificata|Fatto/);
  });

  test('verifica finale con errore di accesso ("keys list") → uscita diversa da zero', () => {
    const dir = scenario(before(), { after: configured() });
    writeFileSync(join(dir, 'after', 'keys.fail'), DENIED('iam.serviceAccountKeys.list'));
    const r = run(dir, '02-configura.sh', 'nexus-social-e3eee\n');
    assert.equal(r.code, 1, r.out);
    assert.ok(r.calls.some((c) => c.startsWith('projects add-iam-policy-binding')), 'le modifiche sono avvenute');
    assert.match(r.out, /Verifica INTERROTTA[\s\S]*CONFIGURAZIONE NON VERIFICATA/);
    assert.doesNotMatch(r.out, /Configurazione verificata/);
    assertNoPersonalData(r.out);
  });

  test('describe dell\'account con errore diverso da NOT_FOUND → nessuna creazione', () => {
    // La verifica preliminare passa (account inesistente), poi describe fallisce per permessi.
    const dir = scenario(before(), { after: configured() });
    writeFileSync(join(dir, 'state', 'sa.fail'), NOT_FOUND);
    // Dopo la prima modifica (attivazione API) describe restituisce PERMISSION_DENIED.
    const st = before();
    st.services = SERVICES.filter((s) => s.config.name !== 'iamcredentials.googleapis.com');
    const dir2 = scenario(st, { after: { ...configured() } });
    writeFileSync(join(dir2, 'after', 'sa.fail'), DENIED('iam.serviceAccounts.get'));
    writeFileSync(join(dir2, 'flip_on_enable'), '');
    const r = run(dir2, '02-configura.sh', 'nexus-social-e3eee\n');
    assert.equal(r.code, 1, r.out);
    assert.match(r.out, /impossibile verificare se l'account esiste/);
    assert.ok(!r.calls.some((c) => c.startsWith('iam service-accounts create')));
    assert.ok(existsSync(dir));
  });
});

describe('03-prova-impersonazione.sh', () => {
  const granted = (perms) => ({ permissions: perms });

  test('solo lettura → uscita 0; il token non compare negli argomenti dei processi né nell\'output', () => {
    const dir = scenario(configured(), { curlResponse: granted(READ) });
    const r = run(dir, '03-prova-impersonazione.sh');
    assert.equal(r.code, 0, r.out);
    assert.match(r.out, /impersonazione funzionante, permessi di sola lettura/);
    const args = readFileSync(join(dir, 'curl.args'), 'utf8');
    const ps = readFileSync(join(dir, 'ps.txt'), 'utf8');
    assert.match(ps, /03-prova-impersonazione\.sh/, 'elenco processi acquisito mentre lo script è in esecuzione');
    assert.match(ps, /curl/, 'curl presente nell\'elenco processi');
    assert.ok(!args.includes(TOKEN), 'token negli argomenti di curl');
    assert.ok(!ps.includes(TOKEN), 'token visibile nell\'elenco dei processi');
    // Il token arriva comunque a curl, da stdin.
    assert.match(readFileSync(join(dir, 'curl.stdin'), 'utf8'), new RegExp(`header = "Authorization: Bearer ${TOKEN.replace(/\./g, '\\.')}"`));
    assert.match(args, new RegExp(`https://cloudresourcemanager\\.googleapis\\.com/v1/projects/${P}:testIamPermissions`));
    assert.doesNotMatch(args, /firestore\.googleapis\.com/);
    assert.deepEqual(r.calls, [`auth print-access-token --impersonate-service-account=${SA}`]);
    assertNoPersonalData(r.out);
    // Tutti i 32 permessi vengono chiesti.
    const body = JSON.parse(args.split('\n')[args.split('\n').indexOf('-d') + 1]);
    assert.equal(body.permissions.length, 32);
  });

  test('un permesso vietato → uscita 1', () => {
    for (const extra of ['datastore.entities.update', 'iam.serviceAccountKeys.create', 'firebaserules.releases.update']) {
      const r = run(scenario(configured(), { curlResponse: granted([...READ, extra]) }), '03-prova-impersonazione.sh');
      assert.equal(r.code, 1, r.out);
      assert.match(r.out, new RegExp(`PROBLEMA permesso NON previsto: ${extra.replace(/\./g, '\\.')}`));
    }
  });

  test('permesso di lettura mancante → uscita 1', () => {
    const r = run(scenario(configured(), { curlResponse: granted(READ.slice(1)) }), '03-prova-impersonazione.sh');
    assert.equal(r.code, 1);
  });

  test('impersonazione negata → uscita 2, email personale mascherata, account di servizio visibile', () => {
    const r = run(scenario(configured(), {
      fails: { token: `ERROR: (gcloud.auth.print-access-token) Failed to impersonate [${SA}]. Make sure the account that's trying to impersonate it has access to the service account itself and the "roles/iam.serviceAccountTokenCreator" role. Caller: ${ME_RAW}` },
    }), '03-prova-impersonazione.sh');
    assert.equal(r.code, 2, r.out);
    assert.match(r.out, /Motivo: .*<email>/);
    assert.match(r.out, new RegExp(SA.replace(/\./g, '\\.')));
    assertNoPersonalData(r.out);
  });

  test('errore di Google con email personale → uscita 2, email mascherata', () => {
    const r = run(scenario(configured(), {
      curlResponse: { error: { status: 'PERMISSION_DENIED', message: `Cloud Resource Manager API disabled; contact ${OTHER.slice(5)} (owner ${ME_RAW})` } },
    }), '03-prova-impersonazione.sh');
    assert.equal(r.code, 2, r.out);
    assert.match(r.out, /PERMISSION_DENIED .*<email>/);
    assertNoPersonalData(r.out);
  });

  test('errore di rete o risposta non valida → uscita 2', () => {
    const r1 = run(scenario(configured(), { curlFail: `curl: (6) Could not resolve host; user ${ME_RAW}` }), '03-prova-impersonazione.sh');
    assert.equal(r1.code, 2, r1.out);
    assertNoPersonalData(r1.out);
    const dir = scenario(configured());
    writeFileSync(join(dir, 'curl.response'), '<html>non json</html>');
    const r2 = run(dir, '03-prova-impersonazione.sh');
    assert.equal(r2.code, 2, r2.out);
  });

  test('token vuoto → uscita 2', () => {
    const r = run(scenario(configured(), { token: '' }), '03-prova-impersonazione.sh');
    assert.equal(r.code, 2);
  });
});
