// Test degli script IAM con gcloud e curl SIMULATI (test-stubs/): nessuna chiamata a Google.
//
// Esecuzione: node --test iam-scripts.test.mjs   (servono bash, python3, ps)

import { describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { copyFileSync, chmodSync, existsSync, mkdirSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
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
const OTHER = 'user:altra.persona@gmail.com';
const TOKEN = 'ya29.TOKEN-SEGRETO-DI-PROVA-1234567890';
const PERSONAL = [/mario\.rossi@gmail\.com/i, /altra\.persona@gmail\.com/i];

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

const SERVICES = ['iamcredentials.googleapis.com', 'cloudresourcemanager.googleapis.com', 'firestore.googleapis.com'].map((name) => ({ config: { name } }));
const PROJECT = { projectId: P, lifecycleState: 'ACTIVE' };
const BASE_POLICY = { bindings: [
  { role: 'roles/owner', members: [ME] },
  { role: 'roles/iam.serviceAccountTokenCreator', members: ['serviceAccount:service-123@gcp-sa-firebase.iam.gserviceaccount.com'] },
] };
const CONFIGURED_POLICY = { bindings: [...BASE_POLICY.bindings, { role: ROLE, members: [`serviceAccount:${SA}`] }] };
const ancestors = (policy) => [{ id: P, type: 'project', policy }];
const customRole = (perms = ROLE_PERMS, extra = {}) => ({ name: ROLE, title: 'Firestore audit - sola lettura', includedPermissions: perms, stage: 'GA', ...extra });

const clone = (o) => JSON.parse(JSON.stringify(o));
const before = () => clone({
  project: PROJECT, project_policy: BASE_POLICY, ancestors: ancestors(BASE_POLICY), sa_list: [],
  deny: [], services: SERVICES, testable: TESTABLE,
});
const configured = () => clone({
  project: PROJECT, project_policy: CONFIGURED_POLICY, ancestors: ancestors(CONFIGURED_POLICY),
  sa_list: [{ email: SA }], sa: { email: SA, disabled: false },
  sa_policy: { bindings: [{ role: 'roles/iam.serviceAccountTokenCreator', members: [ME] }] },
  keys: [], deny: [], services: SERVICES, testable: TESTABLE, custom_role: customRole(),
});
const withPolicy = (state, policy) => ({ ...state, project_policy: policy, ancestors: ancestors(policy) });

const DENIED = (perm) => `ERROR: (gcloud) PERMISSION_DENIED: Permission '${perm}' denied for ${ME_RAW} on resource (or it may not exist).`;

function scenario(state, { after, fails = {}, prompts = [], token = TOKEN, granted, invalid, curlError, curlRaw, curlFail } = {}) {
  const dir = mkdtempSync(join(tmpdir(), 'iam-'));
  const bin = join(dir, 'bin');
  mkdirSync(bin);
  for (const tool of ['gcloud', 'curl']) {
    copyFileSync(join(HERE, 'test-stubs', tool), join(bin, tool));
    chmodSync(join(bin, tool), 0o755);
  }
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
  if (granted) writeFileSync(join(dir, 'granted.json'), JSON.stringify(granted));
  if (invalid) writeFileSync(join(dir, 'invalid.json'), JSON.stringify(invalid));
  if (curlError) writeFileSync(join(dir, 'curl.error.json'), JSON.stringify(curlError));
  if (curlRaw) writeFileSync(join(dir, 'curl.raw'), curlRaw);
  if (curlFail) writeFileSync(join(dir, 'curl.fail'), curlFail);
  writeFileSync(join(dir, 'calls.log'), '');
  return dir;
}

function run(dir, script, input = '') {
  const env = { PATH: `${join(dir, 'bin')}:${process.env.PATH}`, FAKE: dir, FAKE_ME: ME_RAW, HOME: dir };
  const res = spawnSync('bash', [join(HERE, script)], { env, input, encoding: 'utf8', timeout: 120_000 });
  assert.notEqual(res.error?.code, 'ETIMEDOUT', 'lo script si è bloccato (domanda interattiva?)');
  const calls = readFileSync(join(dir, 'calls.log'), 'utf8').split('\n').filter(Boolean);
  return { code: res.status, out: `${res.stdout}${res.stderr}`, calls, dir };
}

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
});

// ---------------------------------------------------------------------------------------------
describe('01-verifica.sh', () => {
  test('prima della configurazione → nessun problema, solo lettura, ruolo e account da creare', () => {
    const r = run(scenario(before()), '01-verifica.sh');
    assert.equal(r.code, 0, r.out);
    assert.match(r.out, /il ruolo non esiste ancora: lo crea 02-configura\.sh/);
    assert.match(r.out, /INFO +non esiste ancora: lo crea 02-configura\.sh/);
    for (const p of ROLE_PERMS) assert.match(r.out, new RegExp(`OK +${p.replace(/\./g, '\\.')}: stage GA, ruoli personalizzati SUPPORTED \\(campo omesso`));
    assert.match(r.out, /datastore\.schemas\.\* esistenti: datastore\.schemas\.create, datastore\.schemas\.delete, datastore\.schemas\.get, datastore\.schemas\.list/);
    assertReadOnly(r.calls);
    assertProjectAlwaysExpected(r.calls);
    assertNoPersonalData(r.out);
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
    const policy = { bindings: [...CONFIGURED_POLICY.bindings, { role: 'roles/datastore.viewer', members: [`serviceAccount:${SA}`] }] };
    const r = run(scenario(withPolicy(configured(), policy)), '01-verifica.sh');
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

  test('ruolo con permesso sconosciuto in più → AVVISO sul permesso (non "scrittura"), definizione non esatta', () => {
    const s = configured();
    s.custom_role = customRole([...ROLE_PERMS, 'servizio.risorse.frobnicate']);
    const r = run(scenario(s), '01-verifica.sh');
    assert.equal(r.code, 1, r.out);
    assert.match(r.out, /AVVISO +in più, non classificato \(da valutare a mano\): servizio\.risorse\.frobnicate/);
    assert.doesNotMatch(r.out, /di scrittura: servizio/);
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

  test('altri problemi di configurazione → uscita 1, nessuna email', () => {
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
describe('02-configura.sh', () => {
  const modifications = (calls) => calls.filter((c) => MODIFYING.test(c));

  test('conferma errata o assente → nessuna modifica', () => {
    for (const answer of ['', 'no', 'nexus-social', 'NEXUS-SOCIAL-E3EEE', 'nexus-social-e3eee ', ' nexus-social-e3eee']) {
      const r = run(scenario(before(), { after: configured() }), '02-configura.sh', `${answer}\n`);
      assert.notEqual(r.code, 0, `risposta ${JSON.stringify(answer)}`);
      assert.deepEqual(modifications(r.calls), [], `risposta ${JSON.stringify(answer)}`);
    }
    const closed = run(scenario(before(), { after: configured() }), '02-configura.sh', '');
    assert.notEqual(closed.code, 0);
    assert.deepEqual(modifications(closed.calls), []);
  });

  test('conferma corretta → ruolo personalizzato con ESATTAMENTE 3 permessi, nessuna chiave, verifica finale superata', () => {
    const state = before();
    state.services = SERVICES.filter((s) => s.config.name !== 'cloudresourcemanager.googleapis.com');
    const r = run(scenario(state, { after: configured() }), '02-configura.sh', 'nexus-social-e3eee\n');
    assert.equal(r.code, 0, r.out);
    assert.match(r.out, /Configurazione verificata\./);
    const mods = modifications(r.calls);
    assert.deepEqual(mods.map((c) => c.split(' --')[0]), [
      'services enable cloudresourcemanager.googleapis.com',
      'iam roles create firestoreAuditReader',
      'iam service-accounts create firestore-audit',
      `projects add-iam-policy-binding ${P}`,
      `iam service-accounts add-iam-policy-binding ${SA}`,
    ]);
    const create = mods.find((c) => c.startsWith('iam roles create'));
    assert.match(create, new RegExp(`--project=${P} `));
    assert.match(create, /--permissions=datastore\.databases\.list,datastore\.entities\.get,datastore\.entities\.list --stage=GA/);
    assert.ok(mods.some((c) => c.includes(`--member=serviceAccount:${SA} --role=${ROLE} --condition=None --format=none`)));
    assert.ok(!mods.some((c) => c.includes('roles/datastore.viewer')));
    assert.ok(mods.some((c) => c.includes(`--member=${ME} --role=roles/iam.serviceAccountTokenCreator --condition=None --format=none`)));
    assert.ok(!r.calls.some((c) => /keys create|keys upload/.test(c)));
    assert.ok(!r.calls.some((c) => c.startsWith('PROMPTS_ENABLED')));
    assertProjectAlwaysExpected(r.calls);
    assertNoPersonalData(r.out);
  });

  test('ruolo già esistente con la definizione esatta → non viene ricreato né modificato', () => {
    const state = before();
    state.custom_role = customRole();
    const r = run(scenario(state, { after: configured() }), '02-configura.sh', 'nexus-social-e3eee\n');
    assert.equal(r.code, 0, r.out);
    assert.match(r.out, /esiste già con la definizione esatta/);
    assert.ok(!r.calls.some((c) => /^iam roles (create|update)/.test(c)));
  });

  test('ruolo esistente con definizione diversa → la verifica preliminare blocca, nessuna modifica', () => {
    const state = before();
    state.custom_role = customRole([...ROLE_PERMS, 'datastore.entities.update']);
    const r = run(scenario(state, { after: configured() }), '02-configura.sh', 'nexus-social-e3eee\n');
    assert.equal(r.code, 1, r.out);
    assert.match(r.out, /Mi fermo: la verifica ha trovato problemi/);
    assert.deepEqual(modifications(r.calls), []);
  });

  test('ruolo che diventa diverso dopo la verifica preliminare → controllo interno, nessuna creazione o modifica del ruolo', () => {
    const state = before();
    state.services = SERVICES.filter((s) => s.config.name !== 'iamcredentials.googleapis.com');
    const after = configured();
    after.custom_role = customRole([...ROLE_PERMS, 'datastore.entities.update']);
    const dir = scenario(state, { after });
    writeFileSync(join(dir, 'flip_on_enable'), '');
    const r = run(dir, '02-configura.sh', 'nexus-social-e3eee\n');
    assert.equal(r.code, 1, r.out);
    assert.match(r.out, /il ruolo esiste ma è eliminato o non contiene esattamente i 3 permessi/);
    assert.ok(!r.calls.some((c) => /^iam roles (create|update)|^iam service-accounts create|add-iam-policy-binding/.test(c)));
  });

  test('describe del ruolo con errore diverso da NOT_FOUND → nessuna creazione', () => {
    const state = before();
    state.services = SERVICES.filter((s) => s.config.name !== 'iamcredentials.googleapis.com');
    const dir = scenario(state, { after: before() });
    writeFileSync(join(dir, 'after', 'custom_role.fail'), DENIED('iam.roles.get'));
    writeFileSync(join(dir, 'flip_on_enable'), '');
    const r = run(dir, '02-configura.sh', 'nexus-social-e3eee\n');
    assert.equal(r.code, 1, r.out);
    assert.match(r.out, /impossibile verificare se il ruolo esiste/);
    assert.ok(!r.calls.some((c) => c.startsWith('iam roles create')));
  });

  test('describe dell\'account con errore diverso da NOT_FOUND → nessuna creazione dell\'account', () => {
    const state = before();
    state.services = SERVICES.filter((s) => s.config.name !== 'iamcredentials.googleapis.com');
    const after = before();
    after.custom_role = customRole();
    const dir = scenario(state, { after });
    writeFileSync(join(dir, 'after', 'sa.fail'), DENIED('iam.serviceAccounts.get'));
    writeFileSync(join(dir, 'flip_on_enable'), '');
    const r = run(dir, '02-configura.sh', 'nexus-social-e3eee\n');
    assert.equal(r.code, 1, r.out);
    assert.match(r.out, /impossibile verificare se l'account esiste/);
    assert.ok(!r.calls.some((c) => c.startsWith('iam service-accounts create')));
  });

  test('verifica finale non superata o con errore di accesso → uscita 1, nessun messaggio di successo', () => {
    const wrong = configured();
    wrong.custom_role = customRole([...ROLE_PERMS, 'datastore.schemas.create']);
    const r1 = run(scenario(before(), { after: wrong }), '02-configura.sh', 'nexus-social-e3eee\n');
    assert.equal(r1.code, 1, r1.out);
    assert.match(r1.out, /CONFIGURAZIONE NON VERIFICATA/);
    assert.doesNotMatch(r1.out, /Configurazione verificata/);

    const dir = scenario(before(), { after: configured() });
    writeFileSync(join(dir, 'after', 'keys.fail'), DENIED('iam.serviceAccountKeys.list'));
    const r2 = run(dir, '02-configura.sh', 'nexus-social-e3eee\n');
    assert.equal(r2.code, 1, r2.out);
    assert.match(r2.out, /Verifica INTERROTTA[\s\S]*CONFIGURAZIONE NON VERIFICATA/);
    assertNoPersonalData(r2.out);
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
