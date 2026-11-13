// Test dell'audit Firestore in sola lettura, sul Firestore Emulator.
//
//  1. Classificazione di un insieme di dati misto (compatibili, legacy, v2 alterati, ...).
//  2. Privacy: nessun valore dei documenti né identificativo reale nell'output.
//  3. Sola lettura: il database è identico prima e dopo l'audit.
//  4. Coerenza con firebase/firestore.rules: i validatori dell'audit danno lo stesso esito
//     delle regole vere sulle letture singole.
//  5. Configurazione: opzioni di scrittura, progetto e credenziali rifiutati prima di
//     qualsiasi accesso alla rete.

import { after, before, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import { mkdtempSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

import { initializeApp } from 'firebase-admin/app';
import { Timestamp, getFirestore } from 'firebase-admin/firestore';
import { initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { doc as clientDoc, getDoc } from 'firebase/firestore';

import { STATUS, classify, makeAnonymizer, typedFieldsFromProto } from './audit-lib.mjs';
import {
  EXIT,
  EXPECTED_SERVICE_ACCOUNT,
  FORBIDDEN_PERMISSIONS,
  REQUIRED_READ_PERMISSIONS,
  checkCredentialConfig,
  listDatabases,
  readOnly,
  runAudit,
  summaryText,
  verifyPermissions,
} from './audit-firestore.mjs';

const PROJECT = 'demo-nexus-audit';
const HERE = fileURLToPath(new URL('.', import.meta.url));
const SCRIPT = join(HERE, 'audit-firestore.mjs');
const RULES = readFileSync(join(HERE, '..', 'firestore.rules'), 'utf8');

// Valori sentinella: non devono mai comparire nell'output dell'audit.
const SENT = {
  email: 'mario.rossi.sentinel@example.com',
  phone: '+39 347 555 0199',
  name: 'Mario Sentinella Rossi',
  secret: 'TESTOSEGRETOSENTINELLA',
  address: 'Via Sentinella 7, Milano',
};
const ID = (s) => `idSENT${s}`;

let db;
let env;

before(async () => {
  db = getFirestore(initializeApp({ projectId: PROJECT }, 'test-admin'));
  env = await initializeTestEnvironment({ projectId: PROJECT, firestore: { rules: RULES } });
});

after(async () => {
  await env?.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
});

// ─── Helper ──────────────────────────────────────────────────────────────────

const ts = (offsetMs = 0) => Timestamp.fromMillis(Date.now() - 3_600_000 + offsetMs);
const put = (path, data) => db.doc(path).set(data); // solo nella preparazione dei test

const profile = (extra = {}) => ({ username: 'carlo.c', publicName: 'Carlo C.', avatarUrl: 'https://cdn.example.com/a.webp', updatedAt: ts(), ...extra });
const device = (extra = {}) => ({ platform: 'android', appVersion: '0.1.0', enabled: true, createdAt: ts(), updatedAt: ts(), lastSeenAt: ts(), ...extra });
const chat = (createdBy, extra = {}) => ({
  type: 'group', createdBy, createdAt: ts(), updatedAt: ts(), schemaVersion: 2,
  memberCount: 2, lastMemberChange: { op: 'add', uid: ID('bob') }, title: 'Calcetto', ...extra,
});
const OWNER = { addMembers: true, removeMembers: true };
const MEMBER = { addMembers: false, removeMembers: false };
const membership = (uid, role, perms, addedBy, extra = {}) => ({ uid, role, permissions: perms, joinedAt: ts(), addedBy, schemaVersion: 2, ...extra });
const message = (senderId, extra = {}) => ({
  senderId, ciphertext: 'U2FsdGVkX1+8m0dGZXN0', iv: 'aXZkaXByb3Zh', type: 'text', createdAt: ts(),
  status: 'sent', deletedForAll: false, schemaVersion: 2, ...extra,
});
const request = (from, to, extra = {}) => ({ from, to, status: 'pending', createdAt: ts(), schemaVersion: 2, ...extra });

async function seedValidGroup(chatId) {
  await put(`chats/${chatId}`, chat(ID('carlo')));
  await put(`chats/${chatId}/members/${ID('carlo')}`, membership(ID('carlo'), 'owner', OWNER, ID('carlo')));
  await put(`chats/${chatId}/members/${ID('bob')}`, membership(ID('bob'), 'member', MEMBER, ID('carlo')));
  await put(`chats/${chatId}/messages/${ID('m1')}`, message(ID('carlo')));
}

// Stato completo del database: percorso, updateTime e contenuto di ogni documento.
async function snapshotAll() {
  const out = {};
  const walk = async (collections) => {
    for (const c of collections) {
      for (const ref of await c.listDocuments()) {
        const s = await ref.get();
        if (s.exists) out[ref.path] = { updateTime: s.updateTime.toMillis(), data: JSON.stringify(s.data()) };
        await walk(await ref.listCollections());
      }
    }
  };
  await walk(await db.listCollections());
  return out;
}

function runCli(args, extraEnv = {}) {
  return new Promise((resolve) => {
    const childEnv = { ...process.env, ...extraEnv };
    for (const [k, v] of Object.entries(extraEnv)) if (v === undefined) delete childEnv[k];
    execFile(process.execPath, [SCRIPT, ...args], { env: childEnv }, (err, stdout, stderr) => {
      resolve({ code: err ? err.code : 0, stdout, stderr });
    });
  });
}

const audit = () => runAudit(readOnly(db), { anonymizer: makeAnonymizer() });
const findingCount = (report, key) => report.findings[key]?.count ?? 0;

// ─── 1. Classificazione ──────────────────────────────────────────────────────

describe('classificazione', () => {
  test('database vuoto: PASS', async () => {
    const r = await audit();
    assert.equal(r.examined, 0);
    assert.equal(r.verdict, 'PASS');
  });

  test('dati tutti conformi: PASS senza avvisi', async () => {
    await put(`profiles/${ID('carlo')}`, profile());
    await put(`profiles/${ID('carlo')}/devices/fid_0123456789abcdef`, device());
    await seedValidGroup(ID('g1'));
    await put(`messageRequests/${ID('r1')}`, request(ID('carlo'), ID('bob')));

    const r = await audit();
    assert.equal(r.examined, 7);
    assert.equal(r.compatible, 7);
    assert.equal(r.legacy, 0);
    assert.equal(r.blocking, 0);
    assert.equal(r.warnings, 0);
    assert.equal(r.verdict, 'PASS');
  });

  test('dati misti: legacy, v2 alterati, contatori e collezioni non previste', async () => {
    // Profili
    await put(`profiles/${ID('carlo')}`, profile());
    await put(`profiles/${ID('mario')}`, profile({ email: SENT.email }));
    await put(`profiles/${ID('anna')}`, { username: 'anna', publicName: 'Anna B.' });
    // Dispositivi
    await put(`profiles/${ID('carlo')}/devices/fid_0123456789abcdef`, device());
    await put(`profiles/${ID('carlo')}/devices/fid_aaaaaaaaaaaaaaaa`, device({ enabled: false }));
    // Chat legacy con array members e messaggi legacy
    await put(`chats/${ID('legacy')}`, { members: [ID('carlo'), ID('bob')], title: SENT.name });
    await put(`chats/${ID('legacy')}/messages/${ID('lm1')}`, { senderId: ID('carlo'), ciphertext: SENT.secret, iv: 'x', email: SENT.email });
    // Gruppo v2 valido con un messaggio e una membership v2 alterati
    await seedValidGroup(ID('g1'));
    await put(`chats/${ID('g1')}/messages/${ID('forged')}`, message(ID('carlo'), { address: SENT.address }));
    await put(`chats/${ID('g1')}/members/${ID('zed')}`, membership(ID('zed'), 'member', MEMBER, ID('carlo'), { phone: SENT.phone }));
    await put(`chats/${ID('g1')}/reactions/${ID('x1')}`, { emoji: 'ok' });
    // Gruppo v2 con contatore incoerente
    await seedValidGroup(ID('g2'));
    await db.doc(`chats/${ID('g2')}`).update({ memberCount: 3 });
    // Chat marcata v2 ma non conforme
    await put(`chats/${ID('g3')}`, chat(ID('carlo'), { memberCount: 1, lastMemberChange: { op: 'add', uid: ID('carlo') }, email: SENT.email }));
    await put(`chats/${ID('g3')}/members/${ID('carlo')}`, membership(ID('carlo'), 'owner', OWNER, ID('carlo')));
    // Membership sotto una chat che non esiste
    await put(`chats/${ID('ghost')}/members/${ID('bob')}`, membership(ID('bob'), 'member', MEMBER, ID('carlo')));
    // Richieste: valida, legacy, v2 alterata
    await put(`messageRequests/${ID('r1')}`, request(ID('carlo'), ID('bob')));
    await put(`messageRequests/${ID('r2')}`, { from: ID('carlo'), to: ID('bob'), status: 'pending', createdAt: ts(), address: SENT.address });
    await put(`messageRequests/${ID('r3')}`, request(ID('carlo'), ID('bob'), { phone: SENT.phone }));
    // Collezione radice non prevista
    await put(`segreti/${ID('s1')}`, { note: SENT.name });

    const r = await audit();

    assert.equal(r.examined, 25);
    assert.equal(r.missingParents, 1); // chats/ghost esiste solo come genitore
    assert.equal(r.byStatus[STATUS.LEGACY], 3); // chat legacy, messaggio legacy, richiesta legacy

    // Bloccanti: messaggio v2 alterato, membership v2 alterata, richiesta v2 alterata,
    // chat v2 non conforme, contatore incoerente.
    assert.equal(findingCount(r, 'bloccante:message_v2_non_conforme_esposto_nelle_query'), 1);
    assert.equal(findingCount(r, 'bloccante:member_v2_non_conforme_esposto_nelle_query'), 1);
    assert.equal(findingCount(r, 'bloccante:messageRequest_v2_non_conforme_esposto_nelle_query'), 1);
    assert.equal(findingCount(r, 'bloccante:chat_v2_non_conforme'), 1);
    assert.equal(findingCount(r, 'bloccante:contatore_membri_incoerente'), 1);
    assert.equal(r.blocking, 5);

    // Avvisi principali.
    assert.equal(findingCount(r, 'avviso:chat_legacy_array_members'), 1);
    assert.equal(findingCount(r, 'avviso:message_senza_schema_v2'), 1);
    assert.equal(findingCount(r, 'avviso:messageRequest_senza_schema_v2'), 1);
    assert.equal(findingCount(r, 'avviso:profilo_non_leggibile_dopo_deploy'), 2);
    assert.equal(findingCount(r, 'avviso:dispositivo_non_aggiornabile_dall_app'), 1);
    assert.equal(findingCount(r, 'avviso:collezione_non_prevista_dalle_regole'), 2);
    assert.equal(findingCount(r, 'avviso:membership_in_chat_inesistente'), 1);
    assert.equal(findingCount(r, 'avviso:membership_v2_in_chat_non_conforme'), 1);
    // email nel profilo, nel messaggio legacy e nella chat g3; telefono nella membership e
    // nella richiesta v2
    assert.equal(findingCount(r, 'avviso:possibile_dato_privato'), 5);

    // Dettaglio tecnico: solo nomi di campo dello schema o segnaposto.
    assert.equal(r.issueCodes['profile:campo_non_ammesso:email'].count, 1);
    assert.equal(r.issueCodes['profile:campo_mancante:updatedAt'].count, 1);
    assert.equal(r.issueCodes['chat:campo_non_ammesso:members'].count, 1);
    assert.equal(r.verdict, 'BLOCKED');
  });

  test('campi con nomi non standard: mai stampati', async () => {
    await put(`profiles/${ID('k')}`, profile({ [SENT.email]: true, '3475550199': 'x' }));
    const r = await audit();
    const keys = Object.keys(r.issueCodes);
    assert.ok(keys.includes('profile:campo_non_ammesso:<campo_non_standard>'));
    assert.ok(!JSON.stringify(r).includes('3475550199'));
    assert.ok(!JSON.stringify(r).includes(SENT.email));
  });
});

// ─── 2 e 3. Privacy e sola lettura, eseguendo lo script da riga di comando ──

describe('script da riga di comando sull\'emulatore', () => {
  test('nessun dato personale nell\'output e nessuna scrittura', async () => {
    await put(`profiles/${ID('mario')}`, profile({ email: SENT.email, username: 'mario.rossi' }));
    await put(`chats/${ID('legacy')}`, { members: [ID('mario')], title: SENT.name });
    await put(`chats/${ID('legacy')}/messages/${ID('lm1')}`, { senderId: ID('mario'), ciphertext: SENT.secret, phone: SENT.phone });
    await seedValidGroup(ID('g1'));
    await put(`chats/${ID('g1')}/messages/${ID('forged')}`, message(ID('carlo'), { address: SENT.address }));
    await put(`messageRequests/${ID('r3')}`, request(ID('carlo'), ID('bob'), { phone: SENT.phone }));

    const before = await snapshotAll();
    const dir = mkdtempSync(join(tmpdir(), 'audit-'));
    const out = join(dir, 'report.json');
    const res = await runCli(['--emulator', '--project', PROJECT, '--out', out]);
    const after = await snapshotAll();

    assert.equal(res.code, EXIT.BLOCKED, res.stderr);
    assert.deepEqual(after, before, 'il database deve restare identico');

    const report = readFileSync(out, 'utf8');
    const everything = `${res.stdout}\n${res.stderr}\n${report}`;
    for (const value of [...Object.values(SENT), 'idSENT', 'mario.rossi', 'Mario', 'example.com', 'Sentinella']) {
      assert.ok(!everything.includes(value), `valore privato trovato nell'output: ${value}`);
    }
    // Report con permessi 0600.
    assert.equal(statSync(out).mode & 0o777, 0o600);
    // Percorsi anonimizzati: collezione nota + hash.
    assert.match(report, /chats\/\{[0-9a-f]{12}\}\/messages\/\{[0-9a-f]{12}\}/);
  });

  test('il report non sovrascrive un file esistente', async () => {
    const dir = mkdtempSync(join(tmpdir(), 'audit-'));
    const out = join(dir, 'report.json');
    writeFileSync(out, 'esistente');
    const res = await runCli(['--emulator', '--project', PROJECT, '--out', out]);
    assert.equal(res.code, EXIT.ERROR);
    assert.equal(readFileSync(out, 'utf8'), 'esistente');
  });
});

// ─── 4. Coerenza con le regole ───────────────────────────────────────────────

describe('coerenza con firebase/firestore.rules (letture singole)', () => {
  // Per ogni variante: il documento è "compatibile" per l'audit se e solo se le regole
  // ne consentono la lettura singola a un utente autorizzato.
  const variants = {
    profile: {
      reader: 'reader',
      path: (id) => `profiles/${id}`,
      base: profile(),
      mutations: {
        valido: {}, senzaAvatar: { avatarUrl: undefined }, maiuscole: { username: 'Carlo.C' },
        cifre: { username: '3331234567' }, nome81: { publicName: `${'x'.repeat(78)} Z.` },
        nome82: { publicName: 'x'.repeat(82) }, chiocciola: { publicName: 'carlo@x.it R.' },
        telefono: { publicName: 'Carlo 333 123 4567' }, extra: { email: 'a@b.it' },
        annidato: { avatarUrl: { url: 'x' } }, username25: { username: 'a'.repeat(25) },
        httpUrl: { avatarUrl: 'http://x.it/a.webp' }, updatedTesto: { updatedAt: 'ieri' },
      },
    },
    messageRequest: {
      reader: ID('bob'),
      path: (id) => `messageRequests/${id}`,
      base: request(ID('carlo'), ID('bob')),
      mutations: {
        valido: {}, accettata: { status: 'accepted', updatedAt: ts() }, statoErrato: { status: 'letta' },
        versioneTesto: { schemaVersion: '2' }, versione1: { schemaVersion: 1 }, senzaVersione: { schemaVersion: undefined },
        extra: { address: 'x' }, stessoUtente: { from: ID('bob') }, mappa: { createdAt: { s: 1 } },
      },
    },
    chat: {
      reader: ID('bob'),
      path: (id) => `chats/${id}`,
      base: chat(ID('carlo')),
      mutations: {
        valido: {}, diretta: { type: 'direct', title: undefined }, contatore0: { memberCount: 0 },
        contatore257: { memberCount: 257 }, contatoreTesto: { memberCount: '2' }, arrayMembers: { members: ['a'] },
        titoloTelefono: { title: 'Chiama 333 123 4567' }, titoloEmail: { title: 'a@b.it' },
        cambioExtra: { lastMemberChange: { op: 'add', uid: ID('bob'), x: 1 } }, cambioOp: { lastMemberChange: { op: 'move', uid: ID('bob') } },
        senzaCambio: { lastMemberChange: undefined }, tipo: { type: 'channel' },
      },
    },
    member: {
      reader: ID('bob'),
      path: (id) => `chats/${ID('cc')}/members/${id}`,
      id: ID('target'),
      base: membership(ID('target'), 'member', MEMBER, ID('carlo')),
      mutations: {
        valido: {}, admin: { role: 'admin', permissions: { addMembers: true, removeMembers: false } },
        ownerSenzaPermessi: { role: 'owner' }, memberConPermessi: { permissions: OWNER },
        ruolo: { role: 'superadmin' }, uidDiverso: { uid: ID('altro') }, extra: { email: 'a@b.it' },
        permessiExtra: { permissions: { ...MEMBER, x: true } }, versione: { schemaVersion: 1 },
        addedByMappa: { addedBy: { uid: 'x' } },
      },
    },
    message: {
      reader: ID('bob'),
      path: (id) => `chats/${ID('cc')}/messages/${id}`,
      base: message(ID('carlo')),
      mutations: {
        valido: {}, risposta: { replyTo: ID('m1') }, rispostaNull: { replyTo: null }, letto: { status: 'read' },
        chiaro: { ciphertext: 'ciao a tutti' }, ivLungo: { iv: 'a'.repeat(65) }, tipo: { type: 'sticker' },
        extra: { email: 'a@b.it' }, rispostaMappa: { replyTo: { id: 'x' } }, versioneTesto: { schemaVersion: '2' },
      },
    },
  };

  const apply = (base, mutation) => {
    const d = { ...base };
    for (const [k, v] of Object.entries(mutation)) {
      if (v === undefined) delete d[k];
      else d[k] = v;
    }
    return d;
  };

  for (const [kind, spec] of Object.entries(variants)) {
    test(`${kind}: audit e regole concordano su ogni variante`, async () => {
      // Chat di contesto valida con il lettore come membro, per chat/member/message.
      await put(`chats/${ID('cc')}`, chat(ID('carlo')));
      await put(`chats/${ID('cc')}/members/${ID('carlo')}`, membership(ID('carlo'), 'owner', OWNER, ID('carlo')));
      await put(`chats/${ID('cc')}/members/${ID('bob')}`, membership(ID('bob'), 'member', MEMBER, ID('carlo')));

      const disagreements = [];
      for (const [name, mutation] of Object.entries(spec.mutations)) {
        const id = spec.id ?? ID(`v_${name}`);
        let path = spec.path(id);
        if (kind === 'chat') {
          path = `chats/${ID(`c_${name}`)}`;
          await put(`${path}/members/${ID('bob')}`, membership(ID('bob'), 'member', MEMBER, ID('carlo')));
        }
        const data = apply(spec.base, mutation);
        await put(path, data);

        const snap = await db.doc(path).get();
        const auditOk = classify(kind, id, typedFieldsFromProto(snap._fieldsProto)).status === STATUS.COMPATIBLE;
        let rulesOk;
        try {
          await getDoc(clientDoc(env.authenticatedContext(spec.reader).firestore(), path));
          rulesOk = true;
        } catch {
          rulesOk = false;
        }
        if (auditOk !== rulesOk) disagreements.push(`${name}: audit=${auditOk} regole=${rulesOk}`);
      }
      assert.deepEqual(disagreements, []);
    });
  }
});

// ─── Libreria: casi che l'emulatore non permette di scrivere ────────────────

describe('libreria: tipi numerici', () => {
  const T = { timestampValue: { seconds: 1, nanos: 0 } };
  const req = (schemaVersion, extra = {}) => typedFieldsFromProto({
    from: { stringValue: 'a' }, to: { stringValue: 'b' }, status: { stringValue: 'pending' },
    createdAt: T, schemaVersion, ...extra,
  });

  test('schemaVersion intero 2: compatibile', () => {
    assert.equal(classify('messageRequest', 'r', req({ integerValue: '2' })).status, STATUS.COMPATIBLE);
  });

  test('schemaVersion 2.0 decimale: non leggibile con get, ma visibile nelle query', () => {
    const ok = classify('messageRequest', 'r', req({ doubleValue: 2 }));
    assert.equal(ok.status, STATUS.NON_CONFORMING);
    assert.ok(ok.findings.some((f) => f.severity === 'bloccante'));
    const legacy = classify('messageRequest', 'r', req({ doubleValue: 1 }));
    assert.equal(legacy.status, STATUS.LEGACY);
  });

  test('memberCount decimale: chat non conforme', () => {
    const c = typedFieldsFromProto({
      type: { stringValue: 'group' }, createdBy: { stringValue: 'a' }, createdAt: T, updatedAt: T,
      schemaVersion: { integerValue: '2' }, memberCount: { doubleValue: 2 },
      lastMemberChange: { mapValue: { fields: { op: { stringValue: 'add' }, uid: { stringValue: 'a' } } } },
    });
    assert.equal(classify('chat', 'c', c).status, STATUS.NON_CONFORMING);
  });
});

// ─── 5. Configurazione ───────────────────────────────────────────────────────

describe('configurazione e sicurezza dell\'avvio', () => {
  const NO_EMU = { FIRESTORE_EMULATOR_HOST: undefined, GOOGLE_APPLICATION_CREDENTIALS: undefined,
    GOOGLE_CLOUD_PROJECT: undefined, GCLOUD_PROJECT: undefined, CLOUDSDK_CORE_PROJECT: undefined };

  for (const flag of ['--fix', '--apply', '--delete', '--migrate', '--write', '--rollback', '--repair', '--force']) {
    test(`opzione ${flag} rifiutata`, async () => {
      const res = await runCli([flag, '--project', 'nexus-social-e3eee'], NO_EMU);
      assert.equal(res.code, EXIT.CONFIG);
      assert.match(res.stderr, /solo in lettura/);
    });
  }

  test('progetto mancante, sconosciuto o opzioni ignote rifiutati', async () => {
    assert.equal((await runCli([], NO_EMU)).code, EXIT.CONFIG);
    assert.equal((await runCli(['--project', 'altro-progetto'], NO_EMU)).code, EXIT.CONFIG);
    assert.equal((await runCli(['--project', 'nexus-social-e3eee', '--verbose'], NO_EMU)).code, EXIT.CONFIG);
  });

  test('progetto reale senza file di credenziali esplicito: rifiutato prima di connettersi', async () => {
    const res = await runCli(['--project', 'nexus-social-e3eee'], NO_EMU);
    assert.equal(res.code, EXIT.CONFIG);
    assert.match(res.stderr, /GOOGLE_APPLICATION_CREDENTIALS obbligatorio/);
  });

  // D4: solo l'account di sola lettura impersonato, mai credenziali personali o chiavi.
  const credFile = (json) => {
    const file = join(mkdtempSync(join(tmpdir(), 'cred-')), 'adc.json');
    writeFileSync(file, typeof json === 'string' ? json : JSON.stringify(json));
    return file;
  };
  const impersonation = (email) => `https://iamcredentials.googleapis.com/v1/projects/-/serviceAccounts/${email}:generateAccessToken`;
  const cases = {
    'credenziali personali (authorized_user)': [{ type: 'authorized_user', client_id: 'x', refresh_token: 'y' }, /credenziali personali rifiutate/],
    'chiave JSON (service_account)': [{ type: 'service_account', project_id: 'nexus-social-e3eee' }, /chiave JSON/],
    'altro account impersonato': [{ type: 'impersonated_service_account', service_account_impersonation_url: impersonation('admin@nexus-social-e3eee.iam.gserviceaccount.com') }, /account di servizio non previsto/],
    'account di un altro progetto': [{ type: 'impersonated_service_account', service_account_impersonation_url: impersonation('firestore-audit@altro.iam.gserviceaccount.com') }, /account di servizio non previsto/],
    'workload identity senza impersonazione': [{ type: 'external_account', audience: 'x' }, /non impersonano/],
    'catena di delega': [{ type: 'impersonated_service_account', service_account_impersonation_url: impersonation(EXPECTED_SERVICE_ACCOUNT), delegates: ['altro@x.iam.gserviceaccount.com'] }, /delega/],
    'tipo sconosciuto': [{ type: 'gdch_service_account' }, /tipo di credenziale non ammesso/],
    'file non valido': ['{non json', /non leggibile/],
  };
  for (const [name, [json, message]] of Object.entries(cases)) {
    test(`credenziali rifiutate: ${name}`, async () => {
      const res = await runCli(['--project', 'nexus-social-e3eee'], { ...NO_EMU, GOOGLE_APPLICATION_CREDENTIALS: credFile(json) });
      assert.equal(res.code, EXIT.CONFIG);
      assert.match(res.stderr, message);
    });
  }

  test("credenziali ammesse: impersonazione dell'account di sola lettura (Cloud Shell e Workload Identity)", () => {
    const url = impersonation(EXPECTED_SERVICE_ACCOUNT);
    assert.deepEqual(checkCredentialConfig({ type: 'impersonated_service_account', service_account_impersonation_url: url }),
      { type: 'impersonated_service_account', serviceAccount: EXPECTED_SERVICE_ACCOUNT });
    assert.deepEqual(checkCredentialConfig({ type: 'external_account', service_account_impersonation_url: url }),
      { type: 'external_account', serviceAccount: EXPECTED_SERVICE_ACCOUNT });
  });

  test('progetto reale con emulatore attivo: rifiutato', async () => {
    const res = await runCli(['--project', 'nexus-social-e3eee'], { FIRESTORE_EMULATOR_HOST: '127.0.0.1:8080' });
    assert.equal(res.code, EXIT.CONFIG);
  });

  test('--emulator solo con progetto demo- ed emulatore attivo', async () => {
    assert.equal((await runCli(['--emulator', '--project', 'nexus-social-e3eee'])).code, EXIT.CONFIG);
    assert.equal((await runCli(['--emulator', '--project', PROJECT], NO_EMU)).code, EXIT.CONFIG);
  });
});

// ─── D4: permessi effettivi, D5: database ────────────────────────────────────

describe('verifiche preliminari (con risposte simulate, nessuna rete)', () => {
  const fakeFetch = (handler) => async (url, init) => handler(url, init);
  const ok = (body) => ({ ok: true, status: 200, json: async () => body });

  test('permessi di sola lettura: verifica superata e tutti i permessi di scrittura controllati', async () => {
    let asked;
    const fetchImpl = fakeFetch((url, init) => {
      assert.match(url, /cloudresourcemanager\.googleapis\.com\/v1\/projects\/nexus-social-e3eee:testIamPermissions$/);
      asked = JSON.parse(init.body).permissions;
      return ok({ permissions: [...REQUIRED_READ_PERMISSIONS, 'datastore.indexes.list'] });
    });
    await verifyPermissions({ project: 'nexus-social-e3eee', token: 't', fetchImpl });
    for (const p of [...REQUIRED_READ_PERMISSIONS, ...FORBIDDEN_PERMISSIONS]) assert.ok(asked.includes(p), p);
  });

  test('anche un solo permesso di scrittura interrompe l\'audit', async () => {
    for (const extra of ['datastore.entities.create', 'datastore.entities.delete', 'firebaserules.releases.update', 'resourcemanager.projects.setIamPolicy']) {
      const fetchImpl = fakeFetch(() => ok({ permissions: [...REQUIRED_READ_PERMISSIONS, extra] }));
      await assert.rejects(verifyPermissions({ project: 'nexus-social-e3eee', token: 't', fetchImpl }), /oltre la sola lettura/);
    }
  });

  test('permessi di lettura mancanti, errore HTTP o di rete: audit interrotto', async () => {
    await assert.rejects(verifyPermissions({ project: 'p', token: 't', fetchImpl: fakeFetch(() => ok({ permissions: ['datastore.entities.get'] })) }), /mancanti/);
    await assert.rejects(verifyPermissions({ project: 'p', token: 't', fetchImpl: fakeFetch(() => ok({})) }), /mancanti/);
    await assert.rejects(verifyPermissions({ project: 'p', token: 't', fetchImpl: fakeFetch(() => ({ ok: false, status: 403 })) }), /HTTP 403/);
    await assert.rejects(verifyPermissions({ project: 'p', token: 't', fetchImpl: fakeFetch(() => { throw new Error('rete'); }) }), /richiesta non riuscita/);
  });

  test('database: (default) obbligatorio, altri database segnalati', async () => {
    const dbs = (...ids) => fakeFetch(() => ok({ databases: ids.map((id) => ({ name: `projects/p/databases/${id}` })) }));
    assert.deepEqual(await listDatabases({ project: 'p', token: 't', fetchImpl: dbs('(default)') }),
      { analyzed: '(default)', others: [], verified: true });
    assert.deepEqual((await listDatabases({ project: 'p', token: 't', fetchImpl: dbs('(default)', 'archivio') })).others, ['archivio']);
    await assert.rejects(listDatabases({ project: 'p', token: 't', fetchImpl: dbs('archivio') }), /\(default\) non esiste/);
    await assert.rejects(listDatabases({ project: 'p', token: 't', fetchImpl: fakeFetch(() => ({ ok: false, status: 403 })) }), /HTTP 403/);
  });
});

describe('D5: database aggiuntivi', () => {
  test('dati conformi ma un database non analizzato: INCOMPLETO, mai PASS', async () => {
    await put(`profiles/${ID('carlo')}`, profile());
    const r = await runAudit(readOnly(db), {
      anonymizer: makeAnonymizer(),
      databases: { analyzed: '(default)', others: ['archivio'], verified: true },
    });
    assert.equal(r.verdict, 'INCOMPLETO');
    assert.equal(r.database, '(default)');
    assert.deepEqual(r.otherDatabases, ['archivio']);
    const text = summaryText({ mode: 'test', project: 'p', identity: null }, r);
    assert.match(text, /Database analizzato: \(default\)/);
    assert.match(text, /Altri database non analizzati: archivio/);
  });

  test('con anomalie bloccanti l\'esito resta BLOCKED', async () => {
    await put(`messageRequests/${ID('r3')}`, request(ID('carlo'), ID('bob'), { phone: SENT.phone }));
    const r = await runAudit(readOnly(db), { anonymizer: makeAnonymizer(), databases: { analyzed: '(default)', others: ['x'], verified: true } });
    assert.equal(r.verdict, 'BLOCKED');
  });
});

// ─── D1: members fuori da chats/{chatId} ─────────────────────────────────────

describe('D1: sottocollezioni members fuori dalle chat', () => {
  test('esposte alla query collection group: bloccanti, e la query delle regole le restituisce davvero', async () => {
    await put(`profiles/${ID('carlo')}`, profile());
    await put(`profiles/${ID('carlo')}/members/${ID('x')}`, { uid: 'eve', schemaVersion: 2, email: SENT.email });
    await put(`gruppi/${ID('g')}/members/${ID('y')}`, membership('eve', 'member', MEMBER, ID('carlo')));
    await seedValidGroup(ID('g1'));
    await put(`chats/${ID('g1')}/messages/${ID('m1')}/members/${ID('w')}`, { uid: 'eve', schemaVersion: 2 });
    // Non visibili alla query: schemaVersion 1, uid non testuale.
    await put(`profiles/${ID('carlo')}/members/${ID('z')}`, { uid: 'eve', schemaVersion: 1 });
    await put(`profiles/${ID('carlo')}/members/${ID('k')}`, { uid: 7, schemaVersion: 2 });

    const r = await audit();
    assert.equal(findingCount(r, 'bloccante:members_fuori_chat_esposto_collection_group'), 3);
    assert.equal(r.verdict, 'BLOCKED');

    // Conferma con le regole vere: la query collection group di eve restituisce i documenti
    // fuori dalle chat, che con una lettura singola sarebbero negati.
    const eve = env.authenticatedContext('eve').firestore();
    const { collectionGroup, query, where, getDocs } = await import('firebase/firestore');
    const snap = await getDocs(query(collectionGroup(eve, 'members'), where('uid', '==', 'eve'), where('schemaVersion', '==', 2)));
    const paths = snap.docs.map((d) => d.ref.path).sort();
    assert.deepEqual(paths, [
      `chats/${ID('g1')}/messages/${ID('m1')}/members/${ID('w')}`,
      `gruppi/${ID('g')}/members/${ID('y')}`,
      `profiles/${ID('carlo')}/members/${ID('x')}`,
    ].sort());
    await assert.rejects(getDoc(clientDoc(eve, `profiles/${ID('carlo')}/members/${ID('x')}`)));
  });

  test('le membership nelle chat restano classificate come membership, senza doppio conteggio', async () => {
    await seedValidGroup(ID('g1'));
    const r = await audit();
    assert.equal(r.byKind.member.esaminati, 2);
    assert.equal(r.byKind.memberOutsideChats, undefined);
    assert.equal(r.verdict, 'PASS');
  });
});

// ─── D2: nomi di collezioni non previste ─────────────────────────────────────

describe('D2: nomi delle collezioni non previste mai nel report', () => {
  test('collezioni e sottocollezioni con nomi personali sostituite da un segnaposto', async () => {
    await put(`mario_rossi/${ID('a')}`, { x: 1 });
    await put(`mario_rossi/${ID('a')}/carlo_bianchi/${ID('b')}`, { x: 1 });
    await put(`profiles/${ID('carlo')}`, profile());
    await put(`profiles/${ID('carlo')}/anna_verdi/${ID('c')}`, { x: 1 });
    const dir = mkdtempSync(join(tmpdir(), 'audit-'));
    const out = join(dir, 'report.json');
    const res = await runCli(['--emulator', '--project', PROJECT, '--out', out]);
    const everything = `${res.stdout}\n${res.stderr}\n${readFileSync(out, 'utf8')}`;
    for (const name of ['mario_rossi', 'carlo_bianchi', 'anna_verdi']) assert.ok(!everything.includes(name), name);
    assert.match(everything, /<collezione_non_prevista>/);
    assert.match(everything, /profiles\/\{[0-9a-f]{12}\}\/<collezione_non_prevista>\/\{[0-9a-f]{12}\}/);
  });
});

// ─── Robustezza: errori, timeout, risposte incomplete, ricorsione ────────────

describe('letture parziali: mai scambiate per assenza di documenti', () => {
  const wrap = (overrides) => ({
    listCollections: () => db.listCollections(),
    collection: (p) => db.collection(p),
    doc: (p) => db.doc(p),
    getAll: (...refs) => db.getAll(...refs),
    ...overrides,
  });

  beforeEach(async () => {
    await put(`profiles/${ID('carlo')}`, profile());
    await put(`profiles/${ID('anna')}`, profile());
    await seedValidGroup(ID('g1'));
  });

  test('timeout su una chiamata: INCOMPLETO', async () => {
    const r = await runAudit(readOnly(wrap({ listCollections: () => new Promise(() => {}) }), { timeoutMs: 50 }), { anonymizer: makeAnonymizer() });
    assert.equal(r.verdict, 'INCOMPLETO');
    assert.equal(r.examined, 0);
    assert.deepEqual(r.incompleteReasons[0], { fase: 'elenco collezioni radice', codice: 'timeout' });
  });

  test('errore di accesso a metà scansione: INCOMPLETO con i documenti letti fino a quel punto', async () => {
    const denied = Object.assign(new Error('PERMISSION_DENIED'), { code: 7 });
    const r = await runAudit(readOnly(wrap({
      doc: (p) => (p.startsWith('chats/') ? { listCollections: async () => { throw denied; } } : db.doc(p)),
    })), { anonymizer: makeAnonymizer() });
    assert.equal(r.verdict, 'INCOMPLETO');
    assert.equal(r.incompleteReasons[0].codice, '7');
    // La chat letta prima dell'errore resta nel conteggio; il resto non viene inventato.
    assert.ok(r.examined >= 1);
  });

  test('risposta di getAll con documenti mancanti o in ordine diverso: INCOMPLETO', async () => {
    for (const getAll of [
      async (...refs) => (await db.getAll(...refs)).slice(1),
      async (...refs) => (await db.getAll(...refs)).reverse(),
    ]) {
      const r = await runAudit(readOnly(wrap({ getAll })), { anonymizer: makeAnonymizer() });
      assert.equal(r.verdict, 'INCOMPLETO');
      assert.match(r.incompleteReasons[0].fase, /risposta incompleta/);
    }
  });

  test('anomalie già trovate non vengono nascoste da un errore successivo', async () => {
    await put(`chats/${ID('g1')}/messages/${ID('forged')}`, message(ID('carlo'), { email: SENT.email }));
    const r = await runAudit(readOnly(wrap({
      doc: (p) => (p.startsWith('profiles/') ? { listCollections: async () => { throw new Error('x'); } } : db.doc(p)),
    })), { anonymizer: makeAnonymizer() });
    // L'errore arriva su profiles; se chats viene prima l'anomalia resta, se no l'esito è
    // comunque INCOMPLETO e mai PASS.
    assert.ok(['BLOCKED', 'INCOMPLETO'].includes(r.verdict));
  });
});

describe('scansione ricorsiva', () => {
  test('documenti profondi sotto genitori mancanti: tutti trovati', async () => {
    await put(`x1/${ID('a')}/x2/${ID('b')}/x3/${ID('c')}/x4/${ID('d')}`, { x: 1 });
    await put(`chats/${ID('assente')}/messages/${ID('assente2')}/extra/${ID('e')}`, { x: 1 });
    await put(`profiles/${ID('assente3')}/devices/fid_0123456789abcdef/storico/${ID('f')}`, { x: 1 });
    const r = await audit();
    assert.equal(r.examined, 3);
    assert.equal(r.missingParents, 3 + 2 + 2);
    assert.equal(r.byStatus.fuori_regole, 3);
  });
});

// ─── Sola lettura: verifica statica del codice dell'audit ────────────────────

describe('codice dell\'audit senza operazioni di scrittura', () => {
  test('nessuna chiamata di scrittura in audit-firestore.mjs e audit-lib.mjs', () => {
    const forbidden = /\.(set|update|delete|create|add|commit)\(|writeBatch|runTransaction\(|bulkWriter\(|recursiveDelete\(|\.batch\(/;
    for (const file of ['audit-firestore.mjs', 'audit-lib.mjs']) {
      const lines = readFileSync(join(HERE, file), 'utf8').split('\n');
      const hits = lines
        .map((l, i) => ({ l, n: i + 1 }))
        // Eccezioni esplicite: dichiarazioni di insiemi e l'HMAC dell'anonimizzazione
        // (createHmac(...).update(...) non riguarda Firestore).
        .filter(({ l }) => !l.trim().startsWith('//') && !l.includes('new Set([') && !l.includes('createHmac('))
        .filter(({ l }) => forbidden.test(l));
      assert.deepEqual(hits, [], `${file}: ${hits.map((h) => h.n).join(', ')}`);
    }
  });

  test('l\'accesso in sola lettura blocca le operazioni di scrittura sull\'istanza', async () => {
    let touched = null;
    const fake = new Proxy({}, { get: (_, p) => { touched = p; return () => {}; } });
    const ro = readOnly(fake);
    assert.deepEqual(Object.keys(ro).sort(), ['documentIds', 'getDocuments', 'reads', 'rootCollections', 'subcollections']);
    assert.equal(touched, null);
  });
});
