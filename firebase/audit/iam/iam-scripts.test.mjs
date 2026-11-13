// Test degli script IAM con gcloud e curl SIMULATI (test-stubs/): nessuna chiamata a Google.
//
// Esecuzione: node --test iam-scripts.test.mjs   (servono bash, python3, ps)

import { describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { spawn, spawnSync } from 'node:child_process';
import { copyFileSync, chmodSync, existsSync, mkdirSync, mkdtempSync, readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = fileURLToPath(new URL('.', import.meta.url));
const P = 'nexus-social-e3eee';
const SA = `firestore-audit@${P}.iam.gserviceaccount.com`;
const ROLE = `projects/${P}/roles/firestoreAuditReader`;
const ROLE_PERMS = ['datastore.databases.list', 'datastore.entities.get', 'datastore.entities.list'];
const ME_RAW = 'Mario.Rossi@gmail.com';
const ME = `user:${ME_RAW.toLowerCase()}`;
const ME_MASKED = 'ma•••@gmail.com';
const OTHER = 'user:altra.persona@gmail.com';
const GROUP = 'group:squadra@example.com';
const AGENT = 'serviceAccount:service-123@gcp-sa-firebase.iam.gserviceaccount.com';
const TOKEN = 'ya29.TOKEN-SEGRETO-DI-PROVA-1234567890';
const PERSONAL = [/mario\.rossi@gmail\.com/i, /altra\.persona@gmail\.com/i, /squadra@example\.com/i];

const CRITICAL = [
  'datastore.entities.create', 'datastore.entities.update', 'datastore.entities.delete',
  'datastore.databases.create', 'datastore.databases.update', 'datastore.databases.delete',
  'datastore.databases.export', 'datastore.databases.import',
  'datastore.schemas.create', 'datastore.schemas.delete',
  'firebaserules.releases.create', 'firebaserules.releases.update', 'firebaserules.rulesets.create',
  'resourcemanager.projects.setIamPolicy', 'iam.serviceAccountKeys.create',
  'iam.serviceAccounts.actAs', 'iam.serviceAccounts.getAccessToken',
];
const FILLER = Array.from({ length: 230 }, (_, i) => `servizio${Math.floor(i / 10)}.risorse${i % 10}.get`);
const TESTABLE = [...new Set([...ROLE_PERMS, ...CRITICAL, 'datastore.schemas.get', 'datastore.schemas.list', 'datastore.databases.listTagBindings', ...FILLER])]
  .map((name) => ({ name, stage: 'GA' })); // customRolesSupportLevel omesso = SUPPORTED

// Permessi (ridotti) dei ruoli predefiniti usati nei test: quelli che contano per i controlli.
const ROLE_DB = {
  'roles/owner': ['resourcemanager.projects.setIamPolicy', 'iam.serviceAccounts.actAs', 'iam.serviceAccountKeys.create', 'datastore.entities.update'],
  'roles/editor': ['iam.serviceAccounts.actAs', 'datastore.entities.update'],
  'roles/viewer': ['resourcemanager.projects.get', 'datastore.entities.get'],
  'roles/datastore.viewer': ['datastore.entities.get', 'datastore.entities.list', 'datastore.databases.get'],
  'roles/datastore.user': ['datastore.entities.get', 'datastore.entities.update'],
  'roles/iam.serviceAccountTokenCreator': ['iam.serviceAccounts.getAccessToken', 'iam.serviceAccounts.getOpenIdToken',
    'iam.serviceAccounts.implicitDelegation', 'iam.serviceAccounts.signBlob', 'iam.serviceAccounts.signJwt'],
  'roles/iam.serviceAccountKeyAdmin': ['iam.serviceAccountKeys.create', 'iam.serviceAccountKeys.delete', 'iam.serviceAccountKeys.list'],
  'roles/iam.roleAdmin': ['iam.roles.create', 'iam.roles.update', 'iam.roles.delete'],
  'roles/iam.serviceAccountOpenIdTokenCreator': ['iam.serviceAccounts.getOpenIdToken'],
  'roles/firebase.admin': ['firebase.projects.update'],
};

const SERVICES = ['iamcredentials.googleapis.com', 'cloudresourcemanager.googleapis.com', 'firestore.googleapis.com'].map((name) => ({ config: { name } }));
const PROJECT = { projectId: P, projectNumber: '123', lifecycleState: 'ACTIVE' };
const BASE_POLICY = { bindings: [
  { role: 'roles/owner', members: [ME] },
  { role: 'roles/iam.serviceAccountTokenCreator', members: [AGENT] },
] };
const CONFIGURED_POLICY = { bindings: [...BASE_POLICY.bindings, { role: ROLE, members: [`serviceAccount:${SA}`] }] };
const ancestors = (policy) => [{ id: P, type: 'project', policy }];
const customRole = (perms = ROLE_PERMS, extra = {}) => ({ name: ROLE, title: 'Firestore audit - sola lettura', includedPermissions: perms, stage: 'GA', ...extra });
const MY_TOKEN_CREATOR = { bindings: [{ role: 'roles/iam.serviceAccountTokenCreator', members: [ME] }] };

const clone = (o) => JSON.parse(JSON.stringify(o));
const before = () => clone({
  project: PROJECT, project_policy: BASE_POLICY, ancestors: ancestors(BASE_POLICY), sa_list: [],
  deny: [], services: SERVICES, testable: TESTABLE,
});
const configured = () => clone({
  project: PROJECT, project_policy: CONFIGURED_POLICY, ancestors: ancestors(CONFIGURED_POLICY),
  sa_list: [{ email: SA }], sa: { email: SA, disabled: false },
  sa_policy: MY_TOKEN_CREATOR,
  keys: [], deny: [], services: SERVICES, testable: TESTABLE, custom_role: customRole(),
});
const withPolicy = (state, policy) => ({ ...state, project_policy: policy, ancestors: ancestors(policy) });
const addBinding = (state, binding) => withPolicy(state, { bindings: [...state.project_policy.bindings, binding] });
const withoutServices = (state, ...names) => ({ ...state, services: SERVICES.filter((s) => !names.includes(s.config.name)) });

const DENIED = (perm) => `ERROR: (gcloud) PERMISSION_DENIED: Permission '${perm}' denied for ${ME_RAW} on resource (or it may not exist).`;
const safe = (s) => s.replace(/[^A-Za-z0-9]/g, '_');

// Ruoli assegnati in uno stato (progetto e gerarchia), per scrivere le loro descrizioni simulate.
function boundRoles(st) {
  const levels = st.ancestors ?? (st.project_policy ? [{ policy: st.project_policy }] : []);
  return [...new Set(levels.flatMap((l) => (l.policy?.bindings ?? []).map((b) => b.role)))];
}

function scenario(state, { after, fails = {}, prompts = [], token = TOKEN, granted, invalid, curlError, curlRaw, curlFail,
  roles = {}, approvate = ['agenti-google-del-progetto'], failOps = {}, flipAtRoleDescribe } = {}) {
  const dir = mkdtempSync(join(tmpdir(), 'iam-'));
  const bin = join(dir, 'bin');
  mkdirSync(bin);
  mkdirSync(join(dir, 'tmp'));
  for (const tool of ['gcloud', 'curl']) {
    copyFileSync(join(HERE, 'test-stubs', tool), join(bin, tool));
    chmodSync(join(bin, tool), 0o755);
  }
  const write = (name, files) => {
    mkdirSync(join(dir, name));
    for (const [k, v] of Object.entries(files)) {
      if (v !== undefined) writeFileSync(join(dir, name, `${k}.json`), JSON.stringify(v));
    }
    for (const r of boundRoles(files)) {
      if (r === ROLE) continue; // il ruolo di audit lo descrive custom_role
      const perms = roles[r] ?? ROLE_DB[r] ?? [];
      if (perms === null) continue; // ruolo che il test vuole illeggibile
      const file = join(dir, name, `ruolo_${safe(r)}.json`);
      if (!existsSync(file)) writeFileSync(file, JSON.stringify({ name: r, includedPermissions: perms, stage: 'GA' }));
    }
  };
  write('state', state);
  if (after) write('after', after);
  for (const [k, msg] of Object.entries(fails)) writeFileSync(join(dir, 'state', `${k}.fail`), msg);
  for (const k of prompts) writeFileSync(join(dir, 'state', `${k}.prompt`), '');
  for (const [op, msg] of Object.entries(failOps)) writeFileSync(join(dir, `fail.${op}`), msg);
  if (flipAtRoleDescribe) writeFileSync(join(dir, 'flip_at_role_describe'), String(flipAtRoleDescribe));
  writeFileSync(join(dir, 'state', 'token.txt'), token);
  if (granted) writeFileSync(join(dir, 'granted.json'), JSON.stringify(granted));
  if (invalid) writeFileSync(join(dir, 'invalid.json'), JSON.stringify(invalid));
  if (curlError) writeFileSync(join(dir, 'curl.error.json'), JSON.stringify(curlError));
  if (curlRaw) writeFileSync(join(dir, 'curl.raw'), curlRaw);
  if (curlFail) writeFileSync(join(dir, 'curl.fail'), curlFail);
  writeFileSync(join(dir, 'approvate.txt'), `# identità approvate (test)\n${approvate.join('\n')}\n`);
  writeFileSync(join(dir, 'calls.log'), '');
  return dir;
}

function run(dir, script, input = '', args = []) {
  const env = { PATH: `${join(dir, 'bin')}:${process.env.PATH}`, FAKE: dir, FAKE_ME: ME_RAW, HOME: dir,
    TMPDIR: join(dir, 'tmp'), APPROVATI_FILE: join(dir, 'approvate.txt') };
  const res = spawnSync('bash', [join(HERE, script), ...args], { env, input, encoding: 'utf8', timeout: 120_000 });
  assert.notEqual(res.error?.code, 'ETIMEDOUT', 'lo script si è bloccato (domanda interattiva?)');
  const calls = readFileSync(join(dir, 'calls.log'), 'utf8').split('\n').filter(Boolean);
  return { code: res.status, out: `${res.stdout}${res.stderr}`, calls, dir };
}
const finale = (dir) => run(dir, '01-verifica.sh', '', ['--finale']);

const MODIFYING = /^(services enable|iam roles (create|update|delete|undelete)|iam service-accounts (create|delete|add-iam|remove-iam|keys create|keys delete|enable|disable)|projects (add|remove|set)-iam|.*set-iam-policy)/;
const READ_ONLY = /^(config get-value account|projects describe|projects get-iam-policy|projects get-ancestors-iam-policy|iam service-accounts (list|describe|get-iam-policy|keys list)|iam policies list|services list|iam roles describe|iam list-testable-permissions)( |$)/;

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
    for (const m of c.matchAll(/\/\/cloudresourcemanager\.googleapis\.com\/projects\/(\S+)/g)) assert.equal(m[1], P, c);
  }
}
function assertNoPersonalData(out) {
  for (const re of PERSONAL) assert.doesNotMatch(out, re);
  assert.ok(!out.includes(TOKEN), 'token stampato');
}
function assertTmpClean(dir) {
  assert.deepEqual(readdirSync(join(dir, 'tmp')), [], 'file temporanei rimasti');
}
const pyEnv = { ...process.env, PYTHONDONTWRITEBYTECODE: '1' };

// ---------------------------------------------------------------------------------------------
describe('classificazione dei permessi (permessi.py)', () => {
  const classify = (perms) => {
    const res = spawnSync('python3', ['-c', `
import json, sys
sys.path.insert(0, ${JSON.stringify(HERE)})
from permessi import classify
print(json.dumps({p: classify(p) for p in json.loads(sys.argv[1])}))
`, JSON.stringify(perms)], { encoding: 'utf8', env: pyEnv });
    assert.equal(res.status, 0, res.stderr);
    return JSON.parse(res.stdout);
  };

  test('falsi positivi del vecchio controllo: permessi di lettura con azioni diverse da get/list', () => {
    const r = classify(['datastore.databases.listTagBindings', 'datastore.databases.listEffectiveTags',
      'datastore.databases.listEffectiveTagBindings', 'datastore.databases.getMetadata', 'resourcemanager.projects.getIamPolicy',
      'datastore.entities.get', 'datastore.entities.list']);
    for (const [p, kind] of Object.entries(r)) assert.equal(kind, 'lettura', p);
  });

  test('scritture e concessione di privilegi riconosciute, anche se iniziano con "get"', () => {
    const r = classify(['datastore.schemas.create', 'datastore.schemas.update', 'datastore.schemas.delete',
      'datastore.entities.allocateIds', 'datastore.databases.bulkDelete', 'datastore.backups.restoreDatabase',
      'datastore.databases.createTagBinding', 'iam.serviceAccounts.getAccessToken', 'iam.serviceAccounts.getOpenIdToken',
      'iam.serviceAccounts.signBlob', 'iam.serviceAccounts.actAs', 'resourcemanager.projects.setIamPolicy',
      'firebaserules.releases.update', 'storage.objects.setRetention']);
    for (const [p, kind] of Object.entries(r)) assert.equal(kind, 'scrittura', p);
  });

  test('azioni sconosciute: mai scrittura né lettura', () => {
    const r = classify(['servizio.risorse.frobnicate', 'compute.instances.use', 'datastore.databases.queryPlan', 'x.y.getSomethingElse']);
    for (const [p, kind] of Object.entries(r)) assert.equal(kind, 'sconosciuto', p);
  });

  test('customRolesSupportLevel omesso = SUPPORTED (valore predefinito 0 dell\'API IAM)', () => {
    const res = spawnSync('python3', ['-c', `
import sys; sys.path.insert(0, ${JSON.stringify(HERE)})
from permessi import support_level
print(support_level({"name": "a"}), support_level({"name": "a", "customRolesSupportLevel": "TESTING"}), support_level({"name": "a", "customRolesSupportLevel": "NOT_SUPPORTED"}))
`], { encoding: 'utf8', env: pyEnv });
    assert.equal(res.stdout.trim(), 'SUPPORTED TESTING NOT_SUPPORTED');
  });

  test('privilegi pericolosi: impersonazione, chiavi, ruoli e criteri IAM; ruoli fissi anche senza permessi letti', () => {
    const res = spawnSync('python3', ['-c', `
import json, sys; sys.path.insert(0, ${JSON.stringify(HERE)})
from permessi import dangerous
print(json.dumps([
  dangerous("x", ["iam.serviceAccountKeys.create", "datastore.entities.get"]),
  dangerous("x", ["iam.roles.update"]),
  dangerous("x", ["iam.serviceAccounts.getOpenIdToken"]),
  dangerous("roles/iam.serviceAccountKeyAdmin", []),
  dangerous("roles/iam.roleAdmin", []),
  dangerous("roles/iam.serviceAccountOpenIdTokenCreator", []),
  dangerous("x", ["datastore.entities.get", "servizio.risorse.frobnicate"]),
]))
`], { encoding: 'utf8', env: pyEnv });
    const r = JSON.parse(res.stdout);
    assert.deepEqual(r[0], ['iam.serviceAccountKeys.create']);
    assert.deepEqual(r[1], ['iam.roles.update']);
    assert.deepEqual(r[2], ['iam.serviceAccounts.getOpenIdToken']);
    for (const k of [3, 4, 5]) assert.equal(r[k].length, 1);
    assert.deepEqual(r[6], []);
  });
});

// ---------------------------------------------------------------------------------------------
describe('01-verifica.sh (preliminare)', () => {
  test('prima della configurazione → nessun problema, solo lettura, ruolo e account da creare', () => {
    const r = run(scenario(before()), '01-verifica.sh');
    assert.equal(r.code, 0, r.out);
    assert.match(r.out, /Modalità: preliminare/);
    assert.match(r.out, /il ruolo non esiste ancora: lo crea 02-configura\.sh/);
    assert.match(r.out, /INFO +non esiste ancora: lo crea 02-configura\.sh/);
    for (const p of ROLE_PERMS) assert.match(r.out, new RegExp(`OK +${p.replace(/\./g, '\\.')}: stage GA, ruoli personalizzati SUPPORTED \\(campo omesso`));
    assert.match(r.out, /datastore\.schemas\.\* esistenti: datastore\.schemas\.create, datastore\.schemas\.delete, datastore\.schemas\.get, datastore\.schemas\.list/);
    assert.ok(r.calls.includes('iam roles describe roles/owner --format=json'), 'ogni ruolo assegnato viene letto');
    assertReadOnly(r.calls);
    assertProjectAlwaysExpected(r.calls);
    assertNoPersonalData(r.out);
    assertTmpClean(r.dir);
  });

  test('configurazione corretta con ruolo personalizzato esatto → 0 problemi, 0 avvisi', () => {
    const r = run(scenario(configured()), '01-verifica.sh');
    assert.equal(r.code, 0, r.out);
    assert.match(r.out, /contiene esattamente i 3 permessi previsti/);
    assert.ok(r.out.includes(`progetto: ${ROLE}, senza condizioni`), r.out);
    assert.match(r.out, /Riepilogo: 0 problemi, 0 avvisi/);
    assertReadOnly(r.calls);
  });

  test('roles/datastore.viewer sull\'account → PROBLEMA (ruolo non previsto)', () => {
    const r = run(scenario(addBinding(configured(), { role: 'roles/datastore.viewer', members: [`serviceAccount:${SA}`] })), '01-verifica.sh');
    assert.equal(r.code, 1, r.out);
    assert.match(r.out, /PROBLEMA progetto: ruolo NON previsto roles\/datastore\.viewer/);
  });

  test('ruolo con permesso di scrittura in più → PROBLEMA', () => {
    const s = configured();
    s.custom_role = customRole([...ROLE_PERMS, 'datastore.schemas.create']);
    const r = run(scenario(s), '01-verifica.sh');
    assert.equal(r.code, 1, r.out);
    assert.match(r.out, /PROBLEMA il ruolo non corrisponde alla definizione esatta \(0 mancanti, 1 in più\)/);
    assert.match(r.out, /PROBLEMA in più, di scrittura: datastore\.schemas\.create/);
  });

  test('ruolo con permesso sconosciuto in più → AVVISO sul permesso (non "scrittura"), esito NON positivo', () => {
    const s = configured();
    s.custom_role = customRole([...ROLE_PERMS, 'servizio.risorse.frobnicate']);
    for (const r of [run(scenario(s), '01-verifica.sh'), finale(scenario(s))]) {
      assert.equal(r.code, 1, r.out);
      assert.match(r.out, /AVVISO +in più, non classificato \(da valutare a mano\): servizio\.risorse\.frobnicate/);
      assert.doesNotMatch(r.out, /di scrittura: servizio/);
      assert.doesNotMatch(r.out, /FINALE verificata/);
    }
  });

  test('ruolo con permesso di lettura in più (falso positivo del vecchio controllo) → INFO sul permesso, definizione non esatta', () => {
    const s = configured();
    s.custom_role = customRole([...ROLE_PERMS, 'datastore.databases.listTagBindings']);
    const r = run(scenario(s), '01-verifica.sh');
    assert.equal(r.code, 1, r.out);
    assert.match(r.out, /INFO +in più, di lettura: datastore\.databases\.listTagBindings/);
    assert.doesNotMatch(r.out, /di scrittura: datastore\.databases\.listTagBindings/);
  });

  test('ruolo con permesso mancante → PROBLEMA', () => {
    const s = configured();
    s.custom_role = customRole(ROLE_PERMS.slice(1));
    const r = run(scenario(s), '01-verifica.sh');
    assert.equal(r.code, 1, r.out);
    assert.match(r.out, /PROBLEMA manca: datastore\.databases\.list/);
  });

  test('ruolo eliminato → PROBLEMA', () => {
    const s = configured();
    s.custom_role = customRole(ROLE_PERMS, { deleted: true });
    const r = run(scenario(s), '01-verifica.sh');
    assert.equal(r.code, 1, r.out);
    assert.match(r.out, /PROBLEMA il ruolo esiste ma è ELIMINATO/);
  });

  test('ruolo disattivato (stage DISABLED) → PROBLEMA in entrambe le modalità', () => {
    const s = configured();
    s.custom_role = customRole(ROLE_PERMS, { stage: 'DISABLED' });
    for (const r of [run(scenario(s), '01-verifica.sh'), finale(scenario(s))]) {
      assert.equal(r.code, 1, r.out);
      assert.match(r.out, /PROBLEMA il ruolo esiste ma è DISATTIVATO/);
    }
  });

  test('account disattivato → PROBLEMA in entrambe le modalità', () => {
    const s = configured();
    s.sa.disabled = true;
    for (const r of [run(scenario(s), '01-verifica.sh'), finale(scenario(s))]) {
      assert.equal(r.code, 1, r.out);
      assert.match(r.out, /PROBLEMA l'account esiste ma è DISATTIVATO/);
    }
  });

  test('permesso NOT_SUPPORTED o TESTING nei ruoli personalizzati → PROBLEMA', () => {
    for (const level of ['NOT_SUPPORTED', 'TESTING']) {
      const s = before();
      s.testable = TESTABLE.map((e) => (e.name === 'datastore.entities.list' ? { ...e, customRolesSupportLevel: level } : e));
      const r = run(scenario(s), '01-verifica.sh');
      assert.equal(r.code, 1, `${level}: ${r.out}`);
      assert.match(r.out, new RegExp(`PROBLEMA datastore\\.entities\\.list: stage GA, ruoli personalizzati ${level}`));
    }
  });

  test('permesso del ruolo assente dall\'elenco verificabile → PROBLEMA', () => {
    const s = before();
    s.testable = TESTABLE.filter((e) => e.name !== 'datastore.databases.list');
    const r = run(scenario(s), '01-verifica.sh');
    assert.equal(r.code, 1, r.out);
    assert.match(r.out, /PROBLEMA datastore\.databases\.list: non risulta tra i permessi verificabili/);
  });

  test('permessi insufficienti su "describe" dell\'account → verifica interrotta, mai "non esiste"', () => {
    const r = run(scenario(before(), { fails: { sa: DENIED('iam.serviceAccounts.get') } }), '01-verifica.sh');
    assert.equal(r.code, 2, r.out);
    assert.match(r.out, /PROBLEMA dettagli dell'account firestore-audit: lettura non riuscita \(.*PERMISSION_DENIED/);
    assert.doesNotMatch(r.out, /INFO +non esiste ancora: lo crea|Riepilogo: 0 problemi/);
    assert.match(r.out, /<email>/);
    assertNoPersonalData(r.out);
    assertTmpClean(r.dir);
  });

  test('errore su "keys list" → verifica interrotta, mai "nessuna chiave"', () => {
    const r = run(scenario(configured(), { fails: { keys: DENIED('iam.serviceAccountKeys.list') } }), '01-verifica.sh');
    assert.equal(r.code, 2, r.out);
    assert.match(r.out, /PROBLEMA elenco delle chiavi dell'account: lettura non riuscita/);
    assert.doesNotMatch(r.out, /nessuna chiave creata a mano/);
  });

  test('errori sulle altre letture obbligatorie (ruolo e permessi verificabili compresi) → verifica interrotta', () => {
    for (const k of ['project', 'sa_list', 'sa_policy', 'services', 'custom_role', 'testable']) {
      const r = run(scenario(configured(), { fails: { [k]: 'ERROR: (gcloud) PERMISSION_DENIED: denied' } }), '01-verifica.sh');
      assert.equal(r.code, 2, `${k}: ${r.out}`);
      assert.match(r.out, /Verifica INTERROTTA/, k);
    }
  });

  test('un ruolo assegnato che non si riesce a leggere → verifica interrotta', () => {
    const s = addBinding(configured(), { role: 'roles/firebase.viewer', members: [OTHER] });
    const r = run(scenario(s, { fails: { ruolo_roles_firebase_viewer: DENIED('iam.roles.get') } }), '01-verifica.sh');
    assert.equal(r.code, 2, r.out);
    assert.match(r.out, /permessi del ruolo assegnato roles\/firebase\.viewer: lettura non riuscita/);
    assertNoPersonalData(r.out);
  });

  test('API non attiva: nessuna domanda interattiva, nessuna attivazione, verifica interrotta', () => {
    const r = run(scenario(configured(), {
      fails: { services: 'ERROR: (gcloud.services.list) PERMISSION_DENIED: Service Usage API has not been used in project or it is disabled.' },
      prompts: ['services'],
    }), '01-verifica.sh', 'y\ny\ny\n');
    assert.equal(r.code, 2, r.out);
    assert.ok(!r.calls.some((c) => /^services enable|da prompt/.test(c)), 'nessuna attivazione di API');
    assert.ok(!r.calls.some((c) => c.startsWith('PROMPTS_ENABLED')));
    assert.doesNotMatch(r.out, /Would you like to enable/);
  });

  test('controllo del simulatore: con i prompt abilitati la domanda comparirebbe e "y" attiverebbe l\'API', () => {
    const dir = scenario(configured(), { fails: { services: 'ERROR: API disabled' }, prompts: ['services'] });
    const res = spawnSync(join(dir, 'bin', 'gcloud'), ['services', 'list', '--enabled'], { env: { PATH: process.env.PATH, FAKE: dir }, input: 'y\n', encoding: 'utf8' });
    assert.match(res.stderr, /Would you like to enable/);
    assert.match(readFileSync(join(dir, 'calls.log'), 'utf8'), /services enable \(da prompt\)/);
  });

  test('API disattivate → PROBLEMA con spiegazione, nessuna attivazione (anche in preliminare)', () => {
    const s = withoutServices(configured(), 'iamcredentials.googleapis.com', 'cloudresourcemanager.googleapis.com');
    for (const r of [run(scenario(s), '01-verifica.sh'), finale(scenario(s))]) {
      assert.equal(r.code, 1, r.out);
      assert.match(r.out, /PROBLEMA iamcredentials\.googleapis\.com NON attiva: serve a ottenere i token/);
      assert.match(r.out, /PROBLEMA cloudresourcemanager\.googleapis\.com NON attiva/);
      assert.match(r.out, /serve una tua autorizzazione separata/);
      assertReadOnly(r.calls);
    }
  });

  test('identità aggiuntive sull\'account e chiavi → uscita 1, nessuna email', () => {
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

// ---------------------------------------------------------------------------------------------
describe('01-verifica.sh --finale', () => {
  test('configurazione completa ed esatta → uscita 0, "Configurazione FINALE verificata"', () => {
    const r = finale(scenario(configured()));
    assert.equal(r.code, 0, r.out);
    assert.match(r.out, /Modalità: finale/);
    assert.match(r.out, /Riepilogo: 0 problemi, 0 avvisi/);
    assert.match(r.out, /Configurazione FINALE verificata/);
    assertReadOnly(r.calls);
    assertNoPersonalData(r.out);
    assertTmpClean(r.dir);
  });

  test('niente ancora configurato → PROBLEMA per account, ruolo, binding e Token Creator', () => {
    const r = finale(scenario(before()));
    assert.equal(r.code, 1, r.out);
    assert.match(r.out, /PROBLEMA l'account firestore-audit NON esiste/);
    assert.match(r.out, /PROBLEMA il ruolo personalizzato NON esiste/);
    assert.match(r.out, /PROBLEMA projects\/nexus-social-e3eee\/roles\/firestoreAuditReader NON è assegnato/);
    assert.match(r.out, /PROBLEMA Token Creator sull'account per il tuo account NON è presente/);
  });

  test('binding mancante: ruolo non assegnato all\'account → uscita 1', () => {
    const r = finale(scenario(withPolicy(configured(), BASE_POLICY)));
    assert.equal(r.code, 1, r.out);
    assert.match(r.out, /NON è assegnato all'account sul progetto/);
  });

  test('binding mancante: Token Creator per te assente → uscita 1', () => {
    const s = configured();
    s.sa_policy = { bindings: [] };
    const r = finale(scenario(s));
    assert.equal(r.code, 1, r.out);
    assert.match(r.out, /Token Creator sull'account per il tuo account NON è presente/);
  });

  test('binding aggiuntivi o condizionati → uscita 1', () => {
    const cases = [
      addBinding(configured(), { role: 'roles/datastore.user', members: [`serviceAccount:${SA}`] }),
      withPolicy(configured(), { bindings: [...BASE_POLICY.bindings, { role: ROLE, members: [`serviceAccount:${SA}`], condition: { title: 'scade', expression: 'request.time < timestamp("2030-01-01T00:00:00Z")' } }] }),
      (() => { const s = configured(); s.sa_policy = { bindings: [...MY_TOKEN_CREATOR.bindings, { role: 'roles/iam.serviceAccountUser', members: [ME] }] }; return s; })(),
      (() => { const s = configured(); s.sa_policy = { bindings: [{ role: 'roles/iam.serviceAccountTokenCreator', members: [ME], condition: { title: 'x', expression: 'true' } }] }; return s; })(),
    ];
    for (const s of cases) {
      const r = finale(scenario(s));
      assert.equal(r.code, 1, r.out);
      assert.doesNotMatch(r.out, /FINALE verificata/);
    }
  });

  test('chiavi presenti → uscita 1', () => {
    const s = configured();
    s.keys = [{ name: 'k1' }, { name: 'k2' }];
    const r = finale(scenario(s));
    assert.equal(r.code, 1, r.out);
    assert.match(r.out, /PROBLEMA 2 chiavi create a mano/);
  });

  test('identità aggiuntive con ruoli che permettono di impersonare, creare chiavi o modificare il ruolo → uscita 1', () => {
    for (const role of ['roles/iam.serviceAccountKeyAdmin', 'roles/iam.roleAdmin', 'roles/iam.serviceAccountOpenIdTokenCreator',
      'roles/iam.serviceAccountTokenCreator', 'roles/editor', 'roles/owner']) {
      const r = finale(scenario(addBinding(configured(), { role, members: [OTHER] })));
      assert.equal(r.code, 1, `${role}: ${r.out}`);
      assert.ok(r.out.includes(`PROBLEMA progetto: altro utente — ${role}`), `${role}: ${r.out}`);
      assert.match(r.out, /identità non approvata/);
      assertNoPersonalData(r.out);
    }
  });

  test('gruppo con un ruolo qualsiasi → non verificabile, uscita 1 (anche in preliminare)', () => {
    const s = addBinding(configured(), { role: 'roles/viewer', members: [GROUP] });
    for (const r of [run(scenario(s), '01-verifica.sh'), finale(scenario(s))]) {
      assert.equal(r.code, 1, r.out);
      assert.match(r.out, /PROBLEMA progetto: roles\/viewer concesso a gruppo: non posso verificare se comprende l'account di audit/);
      assertNoPersonalData(r.out);
    }
  });

  test('identità approvata esplicitamente → accettata, senza stampare l\'email', () => {
    const s = addBinding(addBinding(configured(), { role: 'roles/iam.roleAdmin', members: [OTHER] }), { role: 'roles/viewer', members: [GROUP] });
    const r = finale(scenario(s, { approvate: ['agenti-google-del-progetto', OTHER, GROUP] }));
    assert.equal(r.code, 0, r.out);
    assert.match(r.out, /altro utente — roles\/iam\.roleAdmin .*approvata da te/);
    assert.match(r.out, /identità approvate: 2 dal file, più gli agenti Google del progetto/);
    assertNoPersonalData(r.out);
  });

  test('agente Google con Token Creator sul progetto: bloccato se non approvato, accettato se approvato', () => {
    const r1 = finale(scenario(configured(), { approvate: [] }));
    assert.equal(r1.code, 1, r1.out);
    assert.ok(r1.out.includes('PROBLEMA progetto: agente Google (service-123@gcp-sa-firebase.iam.gserviceaccount.com)'), r1.out);
    const r2 = finale(scenario(configured()));
    assert.equal(r2.code, 0, r2.out);
    // un account di servizio qualsiasi (non agente Google) non viene approvato dalla riga speciale
    const r3 = finale(scenario(addBinding(configured(), { role: 'roles/editor', members: [`serviceAccount:${P}@appspot.gserviceaccount.com`] })));
    assert.equal(r3.code, 1, r3.out);
    assert.match(r3.out, /account di servizio \(nexus-social-e3eee@appspot\.gserviceaccount\.com\) — roles\/editor/);
  });

  test('altri ruoli personalizzati: pericolosi → uscita 1; solo lettura → accettati', () => {
    const custom = `projects/${P}/roles/gestoreChiavi`;
    const r1 = finale(scenario(addBinding(configured(), { role: custom, members: [OTHER] }), { roles: { [custom]: ['iam.serviceAccountKeys.create', 'iam.serviceAccountKeys.list'] } }));
    assert.equal(r1.code, 1, r1.out);
    assert.ok(r1.out.includes(`altro utente — ${custom} (iam.serviceAccountKeys.create)`), r1.out);
    assert.ok(r1.calls.includes(`iam roles describe gestoreChiavi --project=${P} --format=json`));
    const reader = `projects/${P}/roles/soloLettura`;
    const r2 = finale(scenario(addBinding(configured(), { role: reader, members: [OTHER] }), { roles: { [reader]: ['datastore.entities.get', 'servizio.risorse.frobnicate'] } }));
    assert.equal(r2.code, 0, r2.out);
  });

  test('privilegi ereditati da organizzazione e cartelle → uscita 1', () => {
    const withOrg = (orgBindings) => {
      const s = configured();
      s.project.parent = { type: 'folder', id: '77' };
      s.ancestors = [
        { id: P, type: 'project', policy: CONFIGURED_POLICY },
        { id: '77', type: 'folder', policy: { bindings: [] } },
        { id: '55', type: 'organization', policy: { bindings: orgBindings } },
      ];
      return s;
    };
    const r1 = finale(scenario(withOrg([{ role: 'roles/datastore.user', members: [`serviceAccount:${SA}`] }])));
    assert.equal(r1.code, 1, r1.out);
    assert.match(r1.out, /PROBLEMA organization 55: ruolo NON previsto roles\/datastore\.user/);
    const r2 = finale(scenario(withOrg([{ role: 'roles/iam.organizationRoleAdmin', members: [OTHER] }])));
    assert.equal(r2.code, 1, r2.out);
    assert.match(r2.out, /PROBLEMA organization 55: altro utente — roles\/iam\.organizationRoleAdmin/);
    const orgRole = 'organizations/55/roles/impersonatore';
    const r3 = finale(scenario(withOrg([{ role: orgRole, members: [GROUP] }]), { roles: { [orgRole]: ['iam.serviceAccounts.getAccessToken'] } }));
    assert.equal(r3.code, 1, r3.out);
    assert.match(r3.out, /PROBLEMA organization 55: gruppo — organizations\/55\/roles\/impersonatore \(iam\.serviceAccounts\.getAccessToken\)/);
    assert.ok(r3.calls.includes('iam roles describe impersonatore --organization=55 --format=json'), r3.calls.join('\n'));
    const r4 = finale(scenario(withOrg([{ role: orgRole, members: [GROUP] }]), { roles: { [orgRole]: ['iam.serviceAccounts.getAccessToken'] }, approvate: ['agenti-google-del-progetto', GROUP] }));
    assert.equal(r4.code, 0, r4.out);
    assertNoPersonalData(r4.out);
  });

  test('gerarchia presente ma non leggibile → verifica interrotta (uscita 2)', () => {
    const s = configured();
    s.project.parent = { type: 'organization', id: '55' };
    const r = finale(scenario(s, { fails: { ancestors: DENIED('resourcemanager.organizations.getIamPolicy') } }));
    assert.equal(r.code, 2, r.out);
    assert.match(r.out, /i privilegi ereditati non sono verificabili/);
    assertNoPersonalData(r.out);
  });

  test('progetto senza gerarchia: get-ancestors non disponibile → si usa il criterio del progetto', () => {
    const r = finale(scenario(configured(), { fails: { ancestors: 'ERROR: (gcloud) INVALID_ARGUMENT: x' } }));
    assert.equal(r.code, 0, r.out);
  });
});
// ---------------------------------------------------------------------------------------------
describe('02-configura.sh', () => {
  const modifications = (calls) => calls.filter((c) => MODIFYING.test(c));
  const CONFIRM = 'nexus-social-e3eee\n';

  test('conferma errata o assente → nessuna modifica, riepilogo mostrato prima, file temporanei cancellati', () => {
    for (const answer of ['', 'no', 'nexus-social', 'NEXUS-SOCIAL-E3EEE', 'nexus-social-e3eee ', ' nexus-social-e3eee', 'si']) {
      const r = run(scenario(before(), { after: configured() }), '02-configura.sh', `${answer}\n`);
      assert.notEqual(r.code, 0, `risposta ${JSON.stringify(answer)}`);
      assert.deepEqual(modifications(r.calls), [], `risposta ${JSON.stringify(answer)}`);
      assert.match(r.out, /Riepilogo delle modifiche \(niente è stato ancora modificato\)[\s\S]*Conferma non valida\. Nessuna modifica eseguita\./);
      assert.doesNotMatch(r.out, /Esecuzione interrotta/);
      assertTmpClean(r.dir);
    }
    const closed = run(scenario(before(), { after: configured() }), '02-configura.sh', '');
    assert.notEqual(closed.code, 0);
    assert.deepEqual(modifications(closed.calls), []);
  });

  test('riepilogo completo prima della conferma: progetto, account (mascherato), account di servizio, ruolo, binding, API', () => {
    const r = run(scenario(before(), { after: configured() }), '02-configura.sh', 'no\n');
    const riepilogo = r.out.slice(r.out.indexOf('== Riepilogo delle modifiche'));
    assert.match(riepilogo, /Progetto: +nexus-social-e3eee/);
    assert.ok(riepilogo.includes(`Account che esegue:    ${ME_MASKED}`), riepilogo);
    assert.ok(riepilogo.includes(`Account di servizio:   ${SA} → verrà CREATO (nessuna chiave)`), riepilogo);
    assert.ok(riepilogo.includes(`Ruolo personalizzato:  ${ROLE} → verrà CREATO`), riepilogo);
    assert.match(riepilogo, /permessi, esattamente: datastore\.databases\.list, datastore\.entities\.get, datastore\.entities\.list/);
    assert.ok(riepilogo.includes(`1. progetto ${P}: serviceAccount:${SA} → ${ROLE}, senza condizioni`), riepilogo);
    assert.ok(riepilogo.includes(`2. account ${SA}: user:${ME_MASKED} → roles/iam.serviceAccountTokenCreator, senza condizioni`), riepilogo);
    assert.match(riepilogo, /API da attivare: +nessuna/);
    assert.match(riepilogo, /scrivi esattamente il nome del progetto \(nexus-social-e3eee\)/);
    assertNoPersonalData(r.out);
  });

  test('API disattivate → si ferma prima della conferma, spiega, nessuna attivazione né modifica (uscita 3)', () => {
    for (const off of [['cloudresourcemanager.googleapis.com'], ['iamcredentials.googleapis.com'], ['iamcredentials.googleapis.com', 'cloudresourcemanager.googleapis.com']]) {
      const r = run(scenario(withoutServices(before(), ...off), { after: configured() }), '02-configura.sh', CONFIRM);
      assert.equal(r.code, 3, r.out);
      assert.match(r.out, /Mi fermo: queste API sono disattivate e lo script NON le attiva da solo/);
      for (const api of off) assert.ok(r.out.includes(`gcloud services enable ${api} --project=${P}`), r.out);
      assert.deepEqual(modifications(r.calls), []);
      assert.ok(!r.calls.some((c) => c.startsWith('services enable')));
      assert.doesNotMatch(r.out, /Riepilogo delle modifiche|Per confermare/);
      assertTmpClean(r.dir);
    }
  });

  test('conferma corretta → esattamente 4 modifiche nell\'ordine previsto, nessuna chiave, verifica finale rigorosa superata', () => {
    const r = run(scenario(before(), { after: configured() }), '02-configura.sh', CONFIRM);
    assert.equal(r.code, 0, r.out);
    assert.match(r.out, /Configurazione FINALE verificata[\s\S]*Configurazione verificata\./);
    const mods = modifications(r.calls);
    assert.deepEqual(mods.map((c) => c.split(' --')[0]), [
      'iam roles create firestoreAuditReader',
      'iam service-accounts create firestore-audit',
      `projects add-iam-policy-binding ${P}`,
      `iam service-accounts add-iam-policy-binding ${SA}`,
    ]);
    const create = mods.find((c) => c.startsWith('iam roles create'));
    assert.match(create, new RegExp(`--project=${P} `));
    assert.match(create, /--permissions=datastore\.databases\.list,datastore\.entities\.get,datastore\.entities\.list --stage=GA/);
    assert.ok(mods.some((c) => c.includes(`--member=serviceAccount:${SA} --role=${ROLE} --condition=None --format=none`)));
    assert.ok(mods.some((c) => c.includes(`--member=${ME} --role=roles/iam.serviceAccountTokenCreator --condition=None --format=none`)));
    assert.ok(!mods.some((c) => /roles\/(owner|editor|viewer|firebase\.admin|datastore\.)/.test(c)));
    assert.ok(!r.calls.some((c) => /keys create|keys upload|services enable/.test(c)));
    assert.ok(!r.calls.some((c) => c.startsWith('PROMPTS_ENABLED')));
    assert.ok(r.calls.some((c) => c === 'config get-value account'));
    assertProjectAlwaysExpected(r.calls);
    assertNoPersonalData(r.out);
    assertTmpClean(r.dir);
  });

  test('ruolo già esistente con la definizione esatta → non viene ricreato né modificato', () => {
    const state = before();
    state.custom_role = customRole();
    const r = run(scenario(state, { after: configured() }), '02-configura.sh', CONFIRM);
    assert.equal(r.code, 0, r.out);
    assert.match(r.out, /esiste già, verrà solo controllato \(mai modificato\)/);
    assert.match(r.out, /esiste già con la definizione esatta/);
    assert.ok(!r.calls.some((c) => /^iam roles (create|update)/.test(c)));
  });

  test('ruolo esistente con definizione diversa → la verifica preliminare blocca, nessuna modifica', () => {
    const state = before();
    state.custom_role = customRole([...ROLE_PERMS, 'datastore.entities.update']);
    const r = run(scenario(state, { after: configured() }), '02-configura.sh', CONFIRM);
    assert.equal(r.code, 1, r.out);
    assert.match(r.out, /Mi fermo: la verifica ha trovato problemi/);
    assert.deepEqual(modifications(r.calls), []);
  });

  test('ruolo che diventa diverso o disattivato dopo la conferma → si ferma, resoconto senza operazioni completate', () => {
    for (const extra of [{ includedPermissions: [...ROLE_PERMS, 'datastore.entities.update'] }, { stage: 'DISABLED' }]) {
      const state = before();
      state.custom_role = customRole();
      const after = configured();
      after.custom_role = { ...customRole(), ...extra };
      const r = run(scenario(state, { after, flipAtRoleDescribe: 3 }), '02-configura.sh', CONFIRM);
      assert.equal(r.code, 1, r.out);
      assert.match(r.out, /il ruolo esiste ma è eliminato, disattivato o non contiene esattamente i 3 permessi/);
      assert.match(r.out, /Esecuzione interrotta: stato delle modifiche[\s\S]*Completate: nessuna[\s\S]*Non riuscita: ruolo personalizzato firestoreAuditReader/);
      assert.deepEqual(modifications(r.calls), []);
      assertTmpClean(r.dir);
    }
  });

  test('describe del ruolo con errore diverso da NOT_FOUND dopo la conferma → nessuna creazione, errore senza email', () => {
    const dir = scenario(before(), { after: before(), flipAtRoleDescribe: 3 });
    writeFileSync(join(dir, 'after', 'custom_role.fail'), DENIED('iam.roles.get'));
    const r = run(dir, '02-configura.sh', CONFIRM);
    assert.equal(r.code, 1, r.out);
    assert.match(r.out, /impossibile verificare se il ruolo esiste/);
    assert.match(r.out, /PERMISSION_DENIED.*<email>/);
    assert.ok(!r.calls.some((c) => c.startsWith('iam roles create')));
    assertNoPersonalData(r.out);
  });

  test('describe dell\'account con errore diverso da NOT_FOUND dopo la conferma → nessuna creazione dell\'account', () => {
    const state = before();
    state.custom_role = customRole();
    const after = before();
    after.custom_role = customRole();
    const dir = scenario(state, { after, flipAtRoleDescribe: 3 });
    writeFileSync(join(dir, 'after', 'sa.fail'), DENIED('iam.serviceAccounts.get'));
    const r = run(dir, '02-configura.sh', CONFIRM);
    assert.equal(r.code, 1, r.out);
    assert.match(r.out, /impossibile verificare se l'account esiste/);
    assert.match(r.out, /Completate:\n +- ruolo firestoreAuditReader: già presente con la definizione esatta \(non modificato\)/);
    assert.ok(!r.calls.some((c) => c.startsWith('iam service-accounts create')));
    assertNoPersonalData(r.out);
  });

  test('interruzione a metà configurazione → elenca le operazioni completate e quelle no, nessun rollback, temporanei cancellati', () => {
    const r = run(scenario(before(), { after: configured(), failOps: { 'project-binding': DENIED('resourcemanager.projects.setIamPolicy') } }), '02-configura.sh', CONFIRM);
    assert.equal(r.code, 1, r.out);
    const res = r.out.slice(r.out.indexOf('== Esecuzione interrotta'));
    assert.match(res, /Completate:\n +- ruolo firestoreAuditReader: CREATO con datastore\.databases\.list, datastore\.entities\.get, datastore\.entities\.list\n +- account firestore-audit@nexus-social-e3eee\.iam\.gserviceaccount\.com: CREATO \(nessuna chiave\)/);
    assert.ok(res.includes(`Non riuscita: binding sul progetto: serviceAccount:${SA} → ${ROLE}`), res);
    assert.ok(res.includes(`Non eseguite:\n    - binding sull'account: user:${ME_MASKED} → roles/iam.serviceAccountTokenCreator`), res);
    assert.match(res, /Nessun rollback automatico/);
    assert.match(r.out, /PERMISSION_DENIED.*<email>/);
    assert.ok(!r.calls.some((c) => c.startsWith('iam service-accounts add-iam-policy-binding')), 'si ferma al primo errore');
    assert.ok(!r.calls.some((c) => /remove-iam|delete/.test(c)), 'nessun rollback');
    assertNoPersonalData(r.out);
    assertTmpClean(r.dir);
  });

  test('interruzione sull\'ultimo passo → 3 operazioni completate elencate', () => {
    const r = run(scenario(before(), { after: configured(), failOps: { 'sa-binding': 'ERROR: (gcloud) FAILED_PRECONDITION: x' } }), '02-configura.sh', CONFIRM);
    assert.equal(r.code, 1, r.out);
    assert.match(r.out, /- binding sul progetto: serviceAccount:firestore-audit@[^\n]+\n +Non riuscita: binding sull'account/);
    assert.doesNotMatch(r.out, /Non eseguite:/);
  });

  test('verifica finale non superata o con errore di accesso → uscita 1, nessun messaggio di successo, resoconto', () => {
    const wrong = configured();
    wrong.custom_role = customRole([...ROLE_PERMS, 'datastore.schemas.create']);
    const r1 = run(scenario(before(), { after: wrong }), '02-configura.sh', CONFIRM);
    assert.equal(r1.code, 1, r1.out);
    assert.match(r1.out, /CONFIGURAZIONE NON VERIFICATA/);
    assert.doesNotMatch(r1.out, /Configurazione verificata\./);
    assert.match(r1.out, /Non riuscita: verifica finale/);

    const dir = scenario(before(), { after: configured() });
    writeFileSync(join(dir, 'after', 'keys.fail'), DENIED('iam.serviceAccountKeys.list'));
    const r2 = run(dir, '02-configura.sh', CONFIRM);
    assert.equal(r2.code, 1, r2.out);
    assert.match(r2.out, /Verifica INTERROTTA[\s\S]*CONFIGURAZIONE NON VERIFICATA/);
    assertNoPersonalData(r2.out);
    assertTmpClean(dir);
  });

  test('interruzione con Ctrl+C durante la conferma → nessuna modifica, file temporanei cancellati', async () => {
    const dir = scenario(before(), { after: configured() });
    const env = { PATH: `${join(dir, 'bin')}:${process.env.PATH}`, FAKE: dir, FAKE_ME: ME_RAW, HOME: dir,
      TMPDIR: join(dir, 'tmp'), APPROVATI_FILE: join(dir, 'approvate.txt') };
    const child = spawn('bash', [join(HERE, '02-configura.sh')], { env, stdio: ['pipe', 'pipe', 'pipe'] });
    let out = '';
    const code = await new Promise((resolve, reject) => {
      const timer = setTimeout(() => { child.kill('SIGKILL'); reject(new Error(`nessuna domanda di conferma: ${out}`)); }, 60_000);
      const onData = (d) => {
        out += d;
        if (out.includes('Per confermare')) {
          assert.ok(readdirSync(join(dir, 'tmp')).length > 0, 'durante l\'esecuzione esiste la cartella temporanea');
          child.kill('SIGINT');
        }
      };
      child.stdout.on('data', onData);
      child.stderr.on('data', onData);
      child.on('exit', (c, sig) => { clearTimeout(timer); resolve(c ?? sig); });
    });
    assert.equal(code, 130, out);
    const calls = readFileSync(join(dir, 'calls.log'), 'utf8').split('\n').filter(Boolean);
    assert.deepEqual(modifications(calls), []);
    assert.doesNotMatch(out, /Esecuzione interrotta/);
    assertTmpClean(dir);
  });

  test('verifica finale con binding mancante dopo la configurazione → non superata', () => {
    const after = configured();
    after.sa_policy = { bindings: [] };
    const r = run(scenario(before(), { after }), '02-configura.sh', CONFIRM);
    assert.equal(r.code, 1, r.out);
    assert.match(r.out, /Token Creator sull'account per il tuo account NON è presente[\s\S]*CONFIGURAZIONE NON VERIFICATA/);
  });
});

// ---------------------------------------------------------------------------------------------
describe('03-prova-impersonazione.sh', () => {
  const ALL = TESTABLE.map((e) => e.name);

  test('esattamente i 3 permessi → uscita 0; tutti i permessi verificabili controllati; token mai visibile', () => {
    const dir = scenario(configured(), { granted: ROLE_PERMS });
    const r = run(dir, '03-prova-impersonazione.sh');
    assert.equal(r.code, 0, r.out);
    assert.match(r.out, new RegExp(`ESATTAMENTE i 3 permessi previsti su ${ALL.length} verificabili`));
    const batches = readFileSync(join(dir, 'curl.requests'), 'utf8').trim().split('\n').map((l) => JSON.parse(l));
    assert.ok(batches.length >= 3 && batches.every((b) => b.length <= 100));
    assert.deepEqual([...new Set(batches.flat())].sort(), [...ALL].sort());
    const args = readFileSync(join(dir, 'curl.args'), 'utf8');
    const ps = readFileSync(join(dir, 'ps.txt'), 'utf8');
    assert.match(ps, /prova_permessi\.py/, 'elenco processi acquisito durante l\'esecuzione');
    assert.match(ps, /curl/);
    assert.ok(!args.includes(TOKEN), 'token negli argomenti di curl');
    assert.ok(!ps.includes(TOKEN), 'token visibile nell\'elenco dei processi');
    assert.match(readFileSync(join(dir, 'curl.stdin'), 'utf8'), /header = "Authorization: Bearer ya29\.TOKEN-SEGRETO/);
    assert.match(args, new RegExp(`https://cloudresourcemanager\\.googleapis\\.com/v1/projects/${P}:testIamPermissions`));
    assert.doesNotMatch(args, /firestore\.googleapis\.com/);
    assert.deepEqual(r.calls, [
      `iam list-testable-permissions //cloudresourcemanager.googleapis.com/projects/${P} --format=json`,
      `auth print-access-token --impersonate-service-account=${SA}`,
    ]);
    assertNoPersonalData(r.out);
  });

  test('permesso di scrittura in più → PROBLEMA, uscita 1', () => {
    for (const extra of ['datastore.entities.update', 'iam.serviceAccountKeys.create', 'datastore.schemas.create']) {
      const r = run(scenario(configured(), { granted: [...ROLE_PERMS, extra] }), '03-prova-impersonazione.sh');
      assert.equal(r.code, 1, r.out);
      assert.match(r.out, new RegExp(`PROBLEMA permesso NON previsto \\(scrittura\\): ${extra.replace(/\./g, '\\.')}`));
    }
  });

  test('permesso in più non di scrittura → AVVISO sul permesso ma esito NON conforme (uscita 1)', () => {
    const r1 = run(scenario(configured(), { granted: [...ROLE_PERMS, 'datastore.databases.listTagBindings'] }), '03-prova-impersonazione.sh');
    assert.equal(r1.code, 1, r1.out);
    assert.match(r1.out, /AVVISO +permesso NON previsto \(lettura\): datastore\.databases\.listTagBindings/);
    const s = configured();
    s.testable = [...TESTABLE, { name: 'servizio.risorse.frobnicate', stage: 'GA' }];
    const r2 = run(scenario(s, { granted: [...ROLE_PERMS, 'servizio.risorse.frobnicate'] }), '03-prova-impersonazione.sh');
    assert.equal(r2.code, 1, r2.out);
    assert.match(r2.out, /AVVISO +permesso NON previsto \(sconosciuto\): servizio\.risorse\.frobnicate/);
    assert.doesNotMatch(r2.out, /Esito: l'account ha ESATTAMENTE/);
  });

  test('permesso previsto mancante → uscita 1', () => {
    const r = run(scenario(configured(), { granted: ROLE_PERMS.slice(1) }), '03-prova-impersonazione.sh');
    assert.equal(r.code, 1, r.out);
    assert.match(r.out, /PROBLEMA lettura prevista: datastore\.databases\.list/);
  });

  test('permesso rifiutato da Google come non valido → isolato, elencato, esito INCOMPLETO (uscita 3)', () => {
    const r = run(scenario(configured(), { granted: ROLE_PERMS, invalid: ['servizio7.risorse3.get'] }), '03-prova-impersonazione.sh');
    assert.equal(r.code, 3, r.out);
    assert.match(r.out, /AVVISO +non verificabile \(rifiutato da Google come non valido\): servizio7\.risorse3\.get/);
    assert.match(r.out, /verifica INCOMPLETA/);
    assert.doesNotMatch(r.out, /ESATTAMENTE/);
  });

  test('elenco dei permessi verificabili troppo corto o senza permessi critici → nessun esito positivo', () => {
    const s1 = configured();
    s1.testable = TESTABLE.slice(0, 50);
    const r1 = run(scenario(s1, { granted: ROLE_PERMS }), '03-prova-impersonazione.sh');
    assert.equal(r1.code, 2, r1.out);
    const s2 = configured();
    s2.testable = TESTABLE.filter((e) => e.name !== 'datastore.entities.update');
    const r2 = run(scenario(s2, { granted: ROLE_PERMS }), '03-prova-impersonazione.sh');
    assert.equal(r2.code, 3, r2.out);
    assert.match(r2.out, /datastore\.entities\.update: non presente tra i permessi verificabili/);
  });

  test('errore nel recupero dell\'elenco → uscita 2, nessun token richiesto', () => {
    const r = run(scenario(configured(), { granted: ROLE_PERMS, fails: { testable: DENIED('iam.permissions.query') } }), '03-prova-impersonazione.sh');
    assert.equal(r.code, 2, r.out);
    assert.ok(!r.calls.some((c) => c.startsWith('auth print-access-token')));
    assertNoPersonalData(r.out);
  });

  test('impersonazione negata → uscita 2, email personale mascherata, account di servizio visibile', () => {
    const r = run(scenario(configured(), {
      granted: ROLE_PERMS,
      fails: { token: `ERROR: (gcloud.auth.print-access-token) Failed to impersonate [${SA}]. Caller: ${ME_RAW}` },
    }), '03-prova-impersonazione.sh');
    assert.equal(r.code, 2, r.out);
    assert.match(r.out, /Motivo: .*<email>/);
    assert.ok(r.out.includes(SA));
    assertNoPersonalData(r.out);
  });

  test('errore di Google con email personale → uscita 2, email mascherata', () => {
    const r = run(scenario(configured(), {
      granted: ROLE_PERMS,
      curlError: { error: { status: 'PERMISSION_DENIED', message: `API disabled; contact ${OTHER.slice(5)} (owner ${ME_RAW})` } },
    }), '03-prova-impersonazione.sh');
    assert.equal(r.code, 2, r.out);
    assert.match(r.out, /PERMISSION_DENIED .*<email>/);
    assert.match(r.out, /verifica IMPOSSIBILE/);
    assertNoPersonalData(r.out);
  });

  test('errore di rete o risposta non valida → uscita 2; token vuoto → uscita 2', () => {
    const r1 = run(scenario(configured(), { granted: ROLE_PERMS, curlFail: `curl: (6) Could not resolve host; user ${ME_RAW}` }), '03-prova-impersonazione.sh');
    assert.equal(r1.code, 2, r1.out);
    assertNoPersonalData(r1.out);
    const r2 = run(scenario(configured(), { granted: ROLE_PERMS, curlRaw: '<html>non json</html>' }), '03-prova-impersonazione.sh');
    assert.equal(r2.code, 2, r2.out);
    const r3 = run(scenario(configured(), { granted: ROLE_PERMS, token: '' }), '03-prova-impersonazione.sh');
    assert.equal(r3.code, 2, r3.out);
  });
});

test('nessun file compilato Python lasciato dagli script', () => {
  assert.ok(!existsSync(join(HERE, '__pycache__')), '__pycache__ creato nella cartella degli script');
});
