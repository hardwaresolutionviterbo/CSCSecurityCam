#!/usr/bin/env node
// Nexus Social — audit Firestore in SOLA LETTURA (Fase A.2).
//
// Verifica che i dati esistenti siano compatibili con firebase/firestore.rules prima del
// deploy delle regole. Usa Firebase Admin SDK esclusivamente per leggere:
//   listCollections, listDocuments, getAll. Nessun'altra operazione è raggiungibile.
//
// Uso:
//   node audit-firestore.mjs --project nexus-social-e3eee [--out report.json]
//   node audit-firestore.mjs --emulator --project demo-nexus-social   (solo test)
//
// Credenziali (progetto reale): SOLO l'account di servizio di sola lettura
// firestore-audit@nexus-social-e3eee.iam.gserviceaccount.com, impersonato senza chiavi
// (Cloud Shell: gcloud auth application-default login --impersonate-service-account,
// oppure Workload Identity Federation da GitHub Actions). GOOGLE_APPLICATION_CREDENTIALS
// deve indicare quel file. Prima di leggere, lo script verifica:
//   1. tipo di credenziale e account impersonato;
//   2. permessi effettivi (testIamPermissions): lettura sì, nessun permesso di scrittura;
//   3. elenco dei database Firestore del progetto.
// Se una verifica non riesce, si ferma senza leggere dati.
//
// Privacy: l'output contiene solo conteggi, codici di anomalia, nomi dello schema e
// identificativi anonimizzati (HMAC con sale casuale o AUDIT_ID_SALT).
//
// Codici di uscita: 0 PASS · 10 PASS CON AVVISI · 20 BLOCKED · 30 INCOMPLETO ·
//                   2 configurazione o verifiche preliminari · 1 errore.

import { readFileSync, writeFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

import {
  SEVERITY,
  STATUS,
  UNKNOWN_COLLECTION,
  checkChatIntegrity,
  classify,
  makeAnonymizer,
  typedFieldsFromProto,
  verdict,
} from './audit-lib.mjs';

export const EXPECTED_PROJECT = 'nexus-social-e3eee';
export const EXPECTED_SERVICE_ACCOUNT = `firestore-audit@${EXPECTED_PROJECT}.iam.gserviceaccount.com`;
export const DEFAULT_DATABASE = '(default)';
export const EXIT = { PASS: 0, WARNINGS: 10, BLOCKED: 20, INCOMPLETE: 30, CONFIG: 2, ERROR: 1 };
export const CALL_TIMEOUT_MS = 120_000;

const WRITE_FLAGS = /^--?(fix|apply|write|migrate|migration|delete|remove|repair|rollback|update|set|commit|force|yes)\b/i;

export class ConfigError extends Error {}

// ─── Argomenti ────────────────────────────────────────────────────────────────

export function parseArgs(argv) {
  const opts = { project: null, out: null, emulator: false };
  for (let i = 0; i < argv.length; i += 1) {
    const a = argv[i];
    if (WRITE_FLAGS.test(a)) {
      throw new ConfigError(`opzione rifiutata: ${a.split('=')[0]}. L'audit è solo in lettura e non corregge dati.`);
    }
    if (a === '--project') opts.project = argv[++i] ?? null;
    else if (a.startsWith('--project=')) opts.project = a.slice('--project='.length);
    else if (a === '--out') opts.out = argv[++i] ?? null;
    else if (a.startsWith('--out=')) opts.out = a.slice('--out='.length);
    else if (a === '--emulator') opts.emulator = true;
    else throw new ConfigError(`opzione sconosciuta: ${a.split('=')[0]}`);
  }
  if (!opts.project) throw new ConfigError('manca --project <id del progetto Firebase>');
  if (opts.out === '') throw new ConfigError('--out richiede un percorso');
  return opts;
}

// ─── Identità (D4) ────────────────────────────────────────────────────────────
//
// Sono ammesse solo credenziali che impersonano l'account di servizio di sola lettura:
//   impersonated_service_account (Cloud Shell, gcloud ... --impersonate-service-account)
//   external_account con impersonazione (Workload Identity Federation)
// Rifiutate: credenziali personali (authorized_user), chiavi JSON (service_account),
// qualsiasi altro tipo o account.

export function checkCredentialConfig(json, expectedServiceAccount = EXPECTED_SERVICE_ACCOUNT) {
  const type = json?.type;
  if (type === 'authorized_user') {
    throw new ConfigError("credenziali personali rifiutate: usare l'impersonazione dell'account di servizio di sola lettura");
  }
  if (type === 'service_account') {
    throw new ConfigError('chiave JSON di account di servizio rifiutata: usare credenziali senza chiavi permanenti');
  }
  if (type !== 'impersonated_service_account' && type !== 'external_account') {
    throw new ConfigError('tipo di credenziale non ammesso');
  }
  const url = json.service_account_impersonation_url;
  const m = typeof url === 'string'
    ? url.match(/^https:\/\/iamcredentials\.googleapis\.com\/v1\/projects\/-\/serviceAccounts\/([^/:]+):generateAccessToken$/)
    : null;
  if (!m) throw new ConfigError('le credenziali non impersonano un account di servizio');
  const serviceAccount = decodeURIComponent(m[1]);
  if (serviceAccount !== expectedServiceAccount) {
    throw new ConfigError(`account di servizio non previsto: è richiesto ${expectedServiceAccount}`);
  }
  if (Array.isArray(json.delegates) && json.delegates.length) {
    throw new ConfigError('catene di delega non ammesse');
  }
  return { type, serviceAccount };
}

// Controlla destinazione e credenziali prima di qualsiasi lettura.
export function checkTarget(opts, env = process.env) {
  const emulatorHost = env.FIRESTORE_EMULATOR_HOST;
  if (opts.emulator) {
    if (!emulatorHost) throw new ConfigError('--emulator richiede FIRESTORE_EMULATOR_HOST');
    if (!opts.project.startsWith('demo-')) throw new ConfigError("con --emulator il progetto deve iniziare con 'demo-'");
    return { mode: 'emulatore', project: opts.project, identity: null };
  }
  if (emulatorHost) {
    throw new ConfigError('FIRESTORE_EMULATOR_HOST è impostato: rimuoverlo per il progetto reale, oppure usare --emulator');
  }
  if (opts.project !== EXPECTED_PROJECT) {
    throw new ConfigError(`progetto non previsto: atteso ${EXPECTED_PROJECT}`);
  }
  // Niente credenziali implicite: senza un file esplicito le librerie Google userebbero
  // le credenziali dell'ambiente (in Cloud Shell quelle personali, con più privilegi).
  const file = env.GOOGLE_APPLICATION_CREDENTIALS;
  if (!file) {
    throw new ConfigError('GOOGLE_APPLICATION_CREDENTIALS obbligatorio: indicare il file delle credenziali impersonate');
  }
  let json;
  try {
    json = JSON.parse(readFileSync(file, 'utf8'));
  } catch {
    throw new ConfigError('GOOGLE_APPLICATION_CREDENTIALS non leggibile');
  }
  const identity = checkCredentialConfig(json);
  return { mode: 'produzione (sola lettura)', project: opts.project, identity };
}

// ─── Verifiche preliminari dal vivo (D4, D5) ─────────────────────────────────

export const REQUIRED_READ_PERMISSIONS = ['datastore.entities.get', 'datastore.entities.list', 'datastore.databases.list'];

// Permessi che l'account di audit NON deve avere sul progetto. Se ne ha anche uno solo,
// l'audit si ferma. Solo nomi documentati da Google (un nome inesistente farebbe fallire
// l'intera verifica; i caratteri jolly non sono ammessi da testIamPermissions).
export const FORBIDDEN_PERMISSIONS = [
  // Scrittura dei documenti Firestore
  'datastore.entities.create',
  'datastore.entities.update',
  'datastore.entities.delete',
  // Gestione dei database: creazione, modifica, cancellazione, import/export, clonazione,
  // eliminazione in blocco, ripristino da backup
  'datastore.databases.create',
  'datastore.databases.update',
  'datastore.databases.delete',
  'datastore.databases.export',
  'datastore.databases.import',
  'datastore.databases.bulkDelete',
  'datastore.databases.clone',
  'datastore.backups.restoreDatabase',
  'datastore.backups.delete',
  'datastore.backupSchedules.create',
  'datastore.backupSchedules.delete',
  // Indici Firestore
  'datastore.schemas.create',
  'datastore.schemas.update',
  'datastore.schemas.delete',
  // Regole di sicurezza Firebase (deploy)
  'firebaserules.releases.create',
  'firebaserules.releases.update',
  'firebaserules.releases.delete',
  'firebaserules.rulesets.create',
  // Amministrazione IAM e aggiramento del ruolo di sola lettura: modifica dei permessi,
  // chiavi, impersonazione di altri account di servizio
  'resourcemanager.projects.setIamPolicy',
  'iam.serviceAccounts.setIamPolicy',
  'iam.serviceAccountKeys.create',
  'iam.serviceAccounts.actAs',
  'iam.serviceAccounts.getAccessToken',
  'iam.serviceAccounts.getOpenIdToken',
  'iam.serviceAccounts.signJwt',
  'iam.serviceAccounts.signBlob',
  'iam.serviceAccounts.implicitDelegation',
];

// Dettaglio di un errore Google, senza dati personali: stato (es. PERMISSION_DENIED,
// INVALID_ARGUMENT) e messaggio breve con eventuali indirizzi email mascherati, tranne
// quelli degli account di servizio.
async function googleErrorDetail(res) {
  try {
    const body = await res.json();
    const status = String(body?.error?.status ?? '');
    const message = String(body?.error?.message ?? '')
      .replace(/[\w.+-]+@(?![\w-]+\.iam\.gserviceaccount\.com)[\w.-]+/g, '<email>')
      .slice(0, 240);
    return [status, message].filter(Boolean).join(': ');
  } catch {
    return '';
  }
}

async function getJson(fetchImpl, url, init, what) {
  let res;
  try {
    res = await fetchImpl(url, { ...init, signal: AbortSignal.timeout(30_000) });
  } catch {
    throw new ConfigError(`${what}: richiesta non riuscita`);
  }
  if (!res.ok) {
    const detail = await googleErrorDetail(res);
    throw new ConfigError(`${what}: risposta HTTP ${res.status}${detail ? ` (${detail})` : ''}`);
  }
  try {
    return await res.json();
  } catch {
    throw new ConfigError(`${what}: risposta non valida`);
  }
}

export async function verifyPermissions({ project, token, fetchImpl = fetch }) {
  const permissions = [...REQUIRED_READ_PERMISSIONS, ...FORBIDDEN_PERMISSIONS];
  const body = await getJson(
    fetchImpl,
    `https://cloudresourcemanager.googleapis.com/v1/projects/${encodeURIComponent(project)}:testIamPermissions`,
    { method: 'POST', headers: { authorization: `Bearer ${token}`, 'content-type': 'application/json' }, body: JSON.stringify({ permissions }) },
    'impossibile verificare i permessi',
  );
  const granted = new Set(body.permissions ?? []);
  const missing = REQUIRED_READ_PERMISSIONS.filter((p) => !granted.has(p));
  const excess = FORBIDDEN_PERMISSIONS.filter((p) => granted.has(p));
  if (excess.length) {
    throw new ConfigError(`l'account ha permessi oltre la sola lettura (${excess.join(', ')}): audit interrotto`);
  }
  if (missing.length) {
    throw new ConfigError(`permessi di lettura mancanti (${missing.join(', ')})`);
  }
  return { read: REQUIRED_READ_PERMISSIONS, forbiddenChecked: FORBIDDEN_PERMISSIONS.length };
}

export async function listDatabases({ project, token, fetchImpl = fetch }) {
  const body = await getJson(
    fetchImpl,
    `https://firestore.googleapis.com/v1/projects/${encodeURIComponent(project)}/databases`,
    { method: 'GET', headers: { authorization: `Bearer ${token}` } },
    'impossibile elencare i database Firestore',
  );
  const ids = (body.databases ?? []).map((d) => String(d.name ?? '').split('/').pop());
  if (!ids.includes(DEFAULT_DATABASE)) {
    throw new ConfigError('il database (default) non esiste nel progetto');
  }
  return { analyzed: DEFAULT_DATABASE, others: ids.filter((id) => id !== DEFAULT_DATABASE), verified: true };
}

// ─── Accesso in sola lettura ──────────────────────────────────────────────────
//
// L'unico oggetto Firestore usato dall'audit. Espone solo letture; qualsiasi metodo di
// scrittura sull'istanza (batch, transazioni, bulkWriter, recursiveDelete) lancia un
// errore. Ogni chiamata ha un timeout. I riferimenti non escono da questo modulo.

const FORBIDDEN_DB = new Set(['batch', 'bulkWriter', 'runTransaction', 'recursiveDelete', 'settings']);

export class ReadError extends Error {
  constructor(phase, cause) {
    super(`lettura non riuscita: ${phase}`);
    this.phase = phase;
    this.code = cause?.code ?? cause?.name ?? 'sconosciuto';
  }
}

export function readOnly(db, { timeoutMs = CALL_TIMEOUT_MS } = {}) {
  const guarded = new Proxy(db, {
    get(target, prop, receiver) {
      if (FORBIDDEN_DB.has(prop)) throw new Error(`operazione non consentita nell'audit: ${String(prop)}`);
      return Reflect.get(target, prop, receiver);
    },
  });
  let reads = 0;
  const call = async (phase, fn) => {
    let timer;
    try {
      return await Promise.race([
        fn(),
        new Promise((_, reject) => { timer = setTimeout(() => reject(Object.assign(new Error('timeout'), { code: 'timeout' })), timeoutMs); }),
      ]);
    } catch (e) {
      throw new ReadError(phase, e);
    } finally {
      clearTimeout(timer);
    }
  };
  return {
    get reads() { return reads; },
    rootCollections: () => call('elenco collezioni radice', async () => {
      reads += 1;
      return (await guarded.listCollections()).map((c) => c.id);
    }),
    // Riferimenti di tutti i documenti di una collezione, anche quelli "mancanti" che
    // hanno solo sottocollezioni. L'SDK pagina internamente fino all'ultima pagina.
    documentIds: (collectionPath) => call('elenco documenti', async () => {
      reads += 1;
      return (await guarded.collection(collectionPath).listDocuments()).map((r) => r.id);
    }),
    subcollections: (docPath) => call('elenco sottocollezioni', async () => {
      reads += 1;
      return (await guarded.doc(docPath).listCollections()).map((c) => c.id);
    }),
    // Lettura a blocchi. Ogni blocco deve restituire esattamente i documenti richiesti,
    // nello stesso ordine: altrimenti la lettura è considerata incompleta.
    getDocuments: async (collectionPath, ids) => {
      const out = [];
      for (let i = 0; i < ids.length; i += 300) {
        const chunk = ids.slice(i, i + 300);
        const snaps = await call('lettura documenti', async () => {
          reads += chunk.length;
          return guarded.getAll(...chunk.map((id) => guarded.collection(collectionPath).doc(id)));
        });
        if (!Array.isArray(snaps) || snaps.length !== chunk.length || snaps.some((s, k) => s.id !== chunk[k])) {
          throw new ReadError('lettura documenti (risposta incompleta)', { code: 'risposta_incompleta' });
        }
        for (const s of snaps) {
          if (!s.exists) { out.push({ id: s.id, exists: false }); continue; }
          if (!s._fieldsProto) throw new ReadError('rappresentazione tipizzata', { code: 'non_disponibile' });
          out.push({ id: s.id, exists: true, fields: typedFieldsFromProto(s._fieldsProto) });
        }
      }
      return out;
    },
  };
}

// ─── Audit ────────────────────────────────────────────────────────────────────

const KNOWN = {
  profiles: { kind: 'profile', sub: { devices: { kind: 'device' } } },
  chats: { kind: 'chat', sub: { members: { kind: 'member' }, messages: { kind: 'message' } } },
  messageRequests: { kind: 'messageRequest', sub: {} },
};

export async function runAudit(ro, {
  anonymizer = makeAnonymizer(process.env.AUDIT_ID_SALT),
  databases = { analyzed: DEFAULT_DATABASE, others: [], verified: false },
} = {}) {
  const report = {
    database: databases.analyzed,
    databasesVerified: databases.verified,
    otherDatabases: databases.others,
    examined: 0,
    missingParents: 0,
    byStatus: Object.fromEntries(Object.values(STATUS).map((s) => [s, 0])),
    byKind: {},
    findings: {},
    issueCodes: {},
    privateIndicators: {},
    collections: [],
    incomplete: false,
    incompleteReasons: [],
  };
  const MAX_EXAMPLES = 5;

  const note = (bucket, key, path) => {
    const e = (bucket[key] ??= { count: 0, examples: [] });
    e.count += 1;
    if (path && e.examples.length < MAX_EXAMPLES) e.examples.push(path);
  };
  const addFinding = (f, path) => note(report.findings, `${f.severity}:${f.code}`, path);

  const record = (kind, segments, doc) => {
    const path = anonymizer.path(segments);
    const r = classify(kind, doc.id, doc.fields);
    report.examined += 1;
    report.byStatus[r.status] += 1;
    const k = (report.byKind[kind] ??= { esaminati: 0, ...Object.fromEntries(Object.values(STATUS).map((s) => [s, 0])) });
    k.esaminati += 1;
    k[r.status] += 1;
    for (const code of r.issues) note(report.issueCodes, `${kind}:${code}`, path);
    for (const f of r.findings) addFinding(f, path);
    for (const p of r.privateIndicators ?? []) note(report.privateIndicators, `${kind}:${p}`, path);
    return r;
  };

  // Tutti i documenti di una collezione fuori dallo schema, ricorsivamente. Le
  // collezioni "members" ovunque si trovino sono esposte alla query collection group (D1).
  const walkUnknown = async (collSegments) => {
    const collPath = collSegments.join('/');
    const kind = collSegments.at(-1) === 'members' ? 'memberOutsideChats' : 'unknown';
    const ids = await ro.documentIds(collPath);
    for (const d of await ro.getDocuments(collPath, ids)) {
      if (d.exists) record(kind, [...collSegments, d.id], d);
      else report.missingParents += 1;
      for (const sub of await ro.subcollections(`${collPath}/${d.id}`)) {
        await walkUnknown([...collSegments, d.id, sub]);
      }
    }
  };

  const scan = async () => {
    const roots = await ro.rootCollections();
    report.collections = roots.map((c) => (KNOWN[c] ? c : UNKNOWN_COLLECTION));

    for (const root of roots) {
      const spec = KNOWN[root];
      if (!spec) { await walkUnknown([root]); continue; }

      const ids = await ro.documentIds(root);
      for (const d of await ro.getDocuments(root, ids)) {
        let parent = null;
        if (d.exists) parent = record(spec.kind, [root, d.id], d);
        else report.missingParents += 1;

        const memberDocs = [];
        for (const sub of await ro.subcollections(`${root}/${d.id}`)) {
          const subSpec = spec.sub[sub];
          const segs = [root, d.id, sub];
          if (!subSpec) { await walkUnknown(segs); continue; }
          const subPath = segs.join('/');
          for (const s of await ro.getDocuments(subPath, await ro.documentIds(subPath))) {
            if (!s.exists) { report.missingParents += 1; }
            else {
              const r = record(subSpec.kind, [...segs, s.id], s);
              if (subSpec.kind === 'member') {
                memberDocs.push({ id: s.id, fields: s.fields, conforming: r.status === STATUS.COMPATIBLE });
              }
            }
            for (const deeper of await ro.subcollections(`${subPath}/${s.id}`)) {
              await walkUnknown([...segs, s.id, deeper]);
            }
          }
        }

        if (spec.kind === 'chat') {
          const chat = { exists: d.exists, fields: d.fields, conforming: parent?.status === STATUS.COMPATIBLE };
          for (const f of checkChatIntegrity(chat, memberDocs)) addFinding(f, anonymizer.path([root, d.id]));
        }
      }
    }
  };

  // Un errore o un timeout non diventa mai "nessun documento": l'audit è INCOMPLETO.
  try {
    await scan();
  } catch (e) {
    if (!(e instanceof ReadError)) throw e;
    report.incomplete = true;
    report.incompleteReasons.push({ fase: e.phase, codice: String(e.code) });
  }
  if (!databases.verified) {
    report.incompleteReasons.push({ fase: 'elenco database', codice: 'non_verificato' });
  }
  if (databases.others.length) {
    // Database non analizzati: l'esito non può essere PASS (D5).
    report.incomplete = true;
    report.incompleteReasons.push({ fase: 'database aggiuntivi non analizzati', codice: String(databases.others.length) });
  }

  const count = (sev) => Object.entries(report.findings)
    .filter(([k]) => k.startsWith(`${sev}:`))
    .reduce((n, [, v]) => n + v.count, 0);
  report.blocking = count(SEVERITY.BLOCKING);
  report.warnings = count(SEVERITY.WARNING);
  report.legacy = report.byStatus[STATUS.LEGACY];
  report.compatible = report.byStatus[STATUS.COMPATIBLE];
  report.verdict = verdict(report.blocking, report.warnings, report.incomplete);
  report.reads = ro.reads;
  report.anonymization = anonymizer.saltProvided ? 'HMAC-SHA256 con AUDIT_ID_SALT' : 'HMAC-SHA256 con sale casuale (non riproducibile)';
  return report;
}

export function exitCodeFor(verdictText) {
  return { PASS: EXIT.PASS, 'PASS CON AVVISI': EXIT.WARNINGS, BLOCKED: EXIT.BLOCKED, INCOMPLETO: EXIT.INCOMPLETE }[verdictText];
}

export function summaryText(target, report) {
  const lines = [
    `Audit Firestore in sola lettura — ${target.mode}`,
    `Progetto: ${target.project}`,
    `Identità: ${target.identity ? `${target.identity.serviceAccount} (${target.identity.type})` : 'emulatore'}`,
    `Database analizzato: ${report.database}${report.databasesVerified ? '' : ' (elenco database non verificato)'}`,
    `Altri database non analizzati: ${report.otherDatabases.length ? report.otherDatabases.join(', ') : 'nessuno'}`,
    `Collezioni radice: ${report.collections.join(', ') || '(nessuna)'}`,
    `Documenti esaminati: ${report.examined}  (riferimenti senza documento: ${report.missingParents})`,
    `Compatibili: ${report.compatible} · Legacy: ${report.legacy} · Non conformi: ${report.byStatus[STATUS.NON_CONFORMING]} · Fuori regole: ${report.byStatus[STATUS.OUTSIDE_RULES]}`,
    `Anomalie bloccanti: ${report.blocking} · Avvisi: ${report.warnings}`,
  ];
  for (const [k, v] of Object.entries(report.findings).sort()) lines.push(`  ${k}: ${v.count}`);
  for (const r of report.incompleteReasons) lines.push(`  incompleto: ${r.fase} (${r.codice})`);
  lines.push(`Esito: ${report.verdict}`);
  return lines.join('\n');
}

// ─── Avvio da riga di comando ─────────────────────────────────────────────────

async function main() {
  let opts;
  let target;
  try {
    opts = parseArgs(process.argv.slice(2));
    target = checkTarget(opts);
  } catch (e) {
    if (e instanceof ConfigError) {
      console.error(`Configurazione non valida: ${e.message}`);
      process.exit(EXIT.CONFIG);
    }
    throw e;
  }

  const { initializeApp, applicationDefault } = await import('firebase-admin/app');
  const { getFirestore } = await import('firebase-admin/firestore');

  let databases = { analyzed: DEFAULT_DATABASE, others: [], verified: false };
  let app;
  if (target.mode === 'emulatore') {
    app = initializeApp({ projectId: opts.project }, 'nexus-audit');
  } else {
    const credential = applicationDefault();
    try {
      const { access_token: token } = await credential.getAccessToken();
      if (!token) throw new ConfigError('token non ottenuto');
      await verifyPermissions({ project: opts.project, token });
      databases = await listDatabases({ project: opts.project, token });
    } catch (e) {
      const msg = e instanceof ConfigError ? e.message : 'impossibile ottenere le credenziali impersonate';
      console.error(`Verifica preliminare non superata: ${msg}. Nessun dato è stato letto.`);
      process.exit(EXIT.CONFIG);
    }
    app = initializeApp({ projectId: opts.project, credential }, 'nexus-audit');
  }

  const report = await runAudit(readOnly(getFirestore(app)), { databases });
  const full = {
    project: target.project,
    mode: target.mode,
    identity: target.identity,
    generatedAt: new Date().toISOString(),
    ...report,
  };

  console.log(summaryText(target, report));
  if (opts.out) {
    // Creazione esclusiva e permessi 0600: non sovrascrive report esistenti.
    writeFileSync(opts.out, `${JSON.stringify(full, null, 2)}\n`, { mode: 0o600, flag: 'wx' });
    console.log(`Report: ${opts.out}`);
  }
  process.exit(exitCodeFor(report.verdict));
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? '').href) {
  main().catch((e) => {
    // Nessun dettaglio dei dati negli errori: solo il tipo e il codice.
    console.error(`Errore durante l'audit: ${e?.code ?? e?.name ?? 'sconosciuto'}`);
    process.exit(EXIT.ERROR);
  });
}

