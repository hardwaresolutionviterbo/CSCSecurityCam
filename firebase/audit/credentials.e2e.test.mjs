// Test riproducibile delle credenziali impersonate, senza accesso a Google.
//
// Simula gli endpoint Google con un server locale HTTPS/HTTP2 (certificato di prova) e
// reindirizza i loro nomi a 127.0.0.1 tramite /etc/hosts (lo fa il workflow). Poi esegue
// lo script vero con un file di credenziali nello stesso formato prodotto da
//   gcloud auth application-default login --impersonate-service-account=...
// e verifica:
//   - che Firebase Admin SDK e il client Firestore usino il token dell'account impersonato;
//   - che le credenziali personali di origine non arrivino mai a Firestore o a IAM;
//   - che identità, permessi e database siano verificati PRIMA di qualsiasi lettura;
//   - che una lettura non riuscita produca INCOMPLETO, mai PASS.
//
// Richiede privilegi per la porta 443 ed E2E_FAKE_GOOGLE=1: viene eseguito solo dal job
// dedicato del workflow "Firestore Audit Tests".

import { after, before, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { execFile, execFileSync } from 'node:child_process';
import { mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import { createSecureServer } from 'node:http2';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const ENABLED = process.env.E2E_FAKE_GOOGLE === '1';
const HERE = fileURLToPath(new URL('.', import.meta.url));
const SCRIPT = join(HERE, 'audit-firestore.mjs');
const PROJECT = 'nexus-social-e3eee';
const SA = `firestore-audit@${PROJECT}.iam.gserviceaccount.com`;
const USER_TOKEN = 'tok-utente-personale';
const SA_TOKEN = `tok-impersonato:${SA}`;
const HOSTS = [
  'oauth2.googleapis.com', 'www.googleapis.com', 'accounts.google.com', 'sts.googleapis.com',
  'iamcredentials.googleapis.com', 'cloudresourcemanager.googleapis.com', 'firestore.googleapis.com',
];
const READ = ['datastore.entities.get', 'datastore.entities.list', 'datastore.databases.list'];

let dir;
let server;
let log;
let scenario;

const defaultScenario = () => ({
  impersonationStatus: 200,
  crmStatus: 200,
  permissions: [...READ, 'datastore.schemas.list'],
  databases: ['(default)'],
  grpcStatus: 7, // PERMISSION_DENIED su ogni chiamata Firestore
});

function makeCertificates(target) {
  const ssl = (...args) => execFileSync('openssl', args, { cwd: target, stdio: 'pipe' });
  ssl('req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-days', '1', '-subj', '/CN=Fake Google CA',
    '-keyout', 'ca.key', '-out', 'ca.pem', '-addext', 'basicConstraints=critical,CA:TRUE',
    '-addext', 'keyUsage=critical,keyCertSign,cRLSign');
  ssl('req', '-newkey', 'rsa:2048', '-nodes', '-subj', '/CN=fake-google', '-keyout', 'server.key', '-out', 'server.csr');
  writeFileSync(join(target, 'ext.cnf'),
    `subjectAltName=${HOSTS.map((h) => `DNS:${h}`).join(',')}\nextendedKeyUsage=serverAuth\n`);
  ssl('x509', '-req', '-in', 'server.csr', '-CA', 'ca.pem', '-CAkey', 'ca.key', '-CAcreateserial',
    '-days', '1', '-out', 'server.pem', '-extfile', 'ext.cnf');
}

function json(res, status, body) {
  res.writeHead(status, { 'content-type': 'application/json' });
  res.end(JSON.stringify(body));
}

function handle(req, res, body) {
  const host = String(req.headers[':authority'] ?? req.headers.host ?? '').split(':')[0];
  const path = req.url;
  const auth = String(req.headers.authorization ?? '');
  const grpc = String(req.headers['content-type'] ?? '').startsWith('application/grpc');
  log.push({ host, path, auth, grpc });

  if (grpc) {
    // Risposta gRPC "trailers-only": nessun dato Firestore viene mai restituito.
    req.stream.respond({
      ':status': 200, 'content-type': 'application/grpc',
      'grpc-status': String(scenario.grpcStatus), 'grpc-message': 'simulato',
    }, { endStream: true });
    return;
  }
  // Token delle credenziali personali di origine (authorized_user).
  if ((host === 'oauth2.googleapis.com' || host === 'www.googleapis.com' || host === 'accounts.google.com')
      && /token/.test(path)) {
    return json(res, 200, { access_token: USER_TOKEN, expires_in: 3600, token_type: 'Bearer' });
  }
  // Impersonazione: solo con il token di origine, solo per l'account previsto.
  const imp = path.match(/^\/v1\/projects\/-\/serviceAccounts\/([^:]+):generateAccessToken$/);
  if (host === 'iamcredentials.googleapis.com' && imp) {
    if (scenario.impersonationStatus !== 200) {
      return json(res, scenario.impersonationStatus, { error: { status: 'PERMISSION_DENIED', message: 'iam.serviceAccounts.getAccessToken denied' } });
    }
    if (auth !== `Bearer ${USER_TOKEN}`) return json(res, 401, { error: { status: 'UNAUTHENTICATED' } });
    const email = decodeURIComponent(imp[1]);
    return json(res, 200, { accessToken: `tok-impersonato:${email}`, expireTime: new Date(Date.now() + 3_600_000).toISOString() });
  }
  if (host === 'cloudresourcemanager.googleapis.com' && path === `/v1/projects/${PROJECT}:testIamPermissions`) {
    if (scenario.crmStatus !== 200) {
      return json(res, scenario.crmStatus, { error: { status: 'PERMISSION_DENIED', message: 'Cloud Resource Manager API has not been used in project 123 before or it is disabled.' } });
    }
    if (auth !== `Bearer ${SA_TOKEN}`) return json(res, 403, { error: { status: 'PERMISSION_DENIED' } });
    const requested = JSON.parse(body || '{}').permissions ?? [];
    return json(res, 200, { permissions: requested.filter((p) => scenario.permissions.includes(p)) });
  }
  if (host === 'firestore.googleapis.com' && path === `/v1/projects/${PROJECT}/databases`) {
    if (auth !== `Bearer ${SA_TOKEN}`) return json(res, 403, { error: { status: 'PERMISSION_DENIED' } });
    return json(res, 200, { databases: scenario.databases.map((id) => ({ name: `projects/${PROJECT}/databases/${id}` })) });
  }
  return json(res, 404, { error: { status: 'NOT_FOUND' } });
}

function credentialsFile(content) {
  const file = join(mkdtempSync(join(tmpdir(), 'adc-')), 'application_default_credentials.json');
  writeFileSync(file, JSON.stringify(content));
  return file;
}

// Stesso formato del file creato da gcloud con --impersonate-service-account.
const impersonatedAdc = (email = SA) => ({
  delegates: [],
  service_account_impersonation_url: `https://iamcredentials.googleapis.com/v1/projects/-/serviceAccounts/${email}:generateAccessToken`,
  source_credentials: { client_id: 'id-prova', client_secret: 'segreto-prova', refresh_token: 'refresh-prova', type: 'authorized_user' },
  type: 'impersonated_service_account',
});

function runCli(env) {
  return new Promise((resolve) => {
    const childEnv = {
      PATH: process.env.PATH,
      HOME: dir,
      NODE_EXTRA_CA_CERTS: join(dir, 'ca.pem'),
      GRPC_DEFAULT_SSL_ROOTS_FILE_PATH: join(dir, 'ca.pem'),
      ...env,
    };
    for (const [k, v] of Object.entries(childEnv)) if (v === undefined) delete childEnv[k];
    execFile(process.execPath, [SCRIPT, '--project', PROJECT], { env: childEnv, timeout: 120_000 }, (err, stdout, stderr) => {
      resolve({ code: err ? err.code : 0, stdout, stderr });
    });
  });
}

const firestoreGrpc = () => log.filter((r) => r.grpc);
const firestoreOrCrm = () => log.filter((r) => r.grpc || r.host === 'cloudresourcemanager.googleapis.com' || r.host === 'firestore.googleapis.com');
const indexOf = (pred) => log.findIndex(pred);

describe('credenziali impersonate con Firebase Admin SDK (Google simulato)', { skip: !ENABLED && 'richiede E2E_FAKE_GOOGLE=1' }, () => {
  before(async () => {
    dir = mkdtempSync(join(tmpdir(), 'fake-google-'));
    makeCertificates(dir);
    server = createSecureServer({
      key: readFileSync(join(dir, 'server.key')),
      cert: readFileSync(join(dir, 'server.pem')),
      allowHTTP1: true,
    });
    server.on('request', (req, res) => {
      let body = '';
      req.on('data', (c) => { body += c; });
      req.on('end', () => handle(req, res, body));
    });
    await new Promise((resolve) => server.listen(443, '127.0.0.1', resolve));
  });

  after(() => new Promise((resolve) => server?.close(resolve)));

  beforeEach(() => {
    log = [];
    scenario = defaultScenario();
  });

  test('impersonazione: tutte le richieste a IAM e Firestore usano il token dell\'account di sola lettura', async () => {
    const res = await runCli({ GOOGLE_APPLICATION_CREDENTIALS: credentialsFile(impersonatedAdc()) });

    // La lettura Firestore simulata viene negata: l'esito deve essere INCOMPLETO, mai PASS.
    assert.equal(res.code, 30, `${res.stdout}\n${res.stderr}`);
    assert.match(res.stdout, new RegExp(`Identità: ${SA.replace(/\./g, '\\.')} \\(impersonated_service_account\\)`));
    assert.match(res.stdout, /Database analizzato: \(default\)\n/);
    assert.match(res.stdout, /Esito: INCOMPLETO/);
    assert.doesNotMatch(res.stdout, /Esito: PASS/);

    // Sequenza: token di origine → impersonazione → permessi → database → Firestore.
    const iSource = indexOf((r) => /token/.test(r.path) && r.host !== 'iamcredentials.googleapis.com');
    const iImp = indexOf((r) => r.host === 'iamcredentials.googleapis.com');
    const iCrm = indexOf((r) => r.host === 'cloudresourcemanager.googleapis.com');
    const iDbs = indexOf((r) => r.host === 'firestore.googleapis.com' && !r.grpc);
    const iGrpc = indexOf((r) => r.grpc);
    assert.ok(iSource >= 0 && iImp > iSource, 'impersonazione dopo il token di origine');
    assert.ok(iCrm > iImp && iDbs > iCrm && iGrpc > iDbs, 'verifiche prima di qualsiasi lettura Firestore');

    // Il client Firestore (gRPC) usa davvero l'identità impersonata.
    assert.ok(firestoreGrpc().length > 0);
    for (const r of firestoreOrCrm()) assert.equal(r.auth, `Bearer ${SA_TOKEN}`, `${r.host}${r.path}`);
    // Le credenziali personali servono solo a ottenere il token impersonato (una volta per
    // Firebase Admin SDK e una per il client Firestore): mai verso IAM o Firestore.
    const personal = log.filter((r) => r.auth === `Bearer ${USER_TOKEN}`);
    assert.ok(personal.length >= 1);
    assert.ok(personal.every((r) => r.host === 'iamcredentials.googleapis.com'));
    // Solo metodi di lettura verso Firestore.
    for (const r of firestoreGrpc()) {
      assert.match(r.path, /^\/google\.firestore\.v1\.Firestore\/(ListCollectionIds|ListDocuments|BatchGetDocuments)$/, r.path);
    }
  });

  test('un solo permesso di scrittura: si ferma prima di qualsiasi lettura Firestore', async () => {
    for (const extra of ['datastore.entities.update', 'iam.serviceAccounts.getAccessToken', 'firebaserules.releases.update']) {
      log = [];
      scenario.permissions = [...READ, extra];
      const res = await runCli({ GOOGLE_APPLICATION_CREDENTIALS: credentialsFile(impersonatedAdc()) });
      assert.equal(res.code, 2);
      assert.match(res.stderr, /oltre la sola lettura/);
      assert.match(res.stderr, /Nessun dato è stato letto/);
      assert.equal(firestoreGrpc().length, 0);
      assert.equal(indexOf((r) => r.host === 'firestore.googleapis.com'), -1);
    }
  });

  test('API Cloud Resource Manager non attiva: si ferma senza leggere', async () => {
    scenario.crmStatus = 403;
    const res = await runCli({ GOOGLE_APPLICATION_CREDENTIALS: credentialsFile(impersonatedAdc()) });
    assert.equal(res.code, 2);
    assert.match(res.stderr, /HTTP 403 \(PERMISSION_DENIED: Cloud Resource Manager API/);
    assert.equal(firestoreGrpc().length, 0);
  });

  test('impersonazione negata (manca Service Account Token Creator): si ferma senza leggere', async () => {
    scenario.impersonationStatus = 403;
    const res = await runCli({ GOOGLE_APPLICATION_CREDENTIALS: credentialsFile(impersonatedAdc()) });
    assert.equal(res.code, 2);
    assert.match(res.stderr, /Nessun dato è stato letto/);
    assert.equal(firestoreOrCrm().length, 0);
  });

  test('database aggiuntivo: segnalato, esito mai PASS', async () => {
    scenario.databases = ['(default)', 'archivio'];
    const res = await runCli({ GOOGLE_APPLICATION_CREDENTIALS: credentialsFile(impersonatedAdc()) });
    assert.equal(res.code, 30);
    assert.match(res.stdout, /Altri database non analizzati: archivio/);
  });

  test('nessun fallback a credenziali personali o implicite: rifiuto senza alcuna richiesta di rete', async () => {
    const cases = [
      [{ GOOGLE_APPLICATION_CREDENTIALS: undefined }, /GOOGLE_APPLICATION_CREDENTIALS obbligatorio/],
      [{ GOOGLE_APPLICATION_CREDENTIALS: credentialsFile({ type: 'authorized_user', client_id: 'a', client_secret: 'b', refresh_token: 'c' }) }, /credenziali personali/],
      [{ GOOGLE_APPLICATION_CREDENTIALS: credentialsFile(impersonatedAdc(`admin@${PROJECT}.iam.gserviceaccount.com`)) }, /non previsto/],
    ];
    for (const [env, message] of cases) {
      log = [];
      const res = await runCli(env);
      assert.equal(res.code, 2);
      assert.match(res.stderr, message);
      assert.deepEqual(log, [], 'nessuna richiesta deve partire');
    }
  });
});
