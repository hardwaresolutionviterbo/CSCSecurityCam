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
// Credenziali: Application Default Credentials (GOOGLE_APPLICATION_CREDENTIALS o
// Workload Identity). Usare un account di servizio con il solo ruolo
// "Cloud Datastore Viewer" (roles/datastore.viewer): così anche l'IAM impedisce scritture.
//
// Privacy: l'output contiene solo conteggi, codici di anomalia, nomi di campo dello
// schema e identificativi anonimizzati (HMAC con sale casuale o AUDIT_ID_SALT).
//
// Codici di uscita: 0 PASS · 10 PASS CON AVVISI · 20 BLOCKED · 2 configurazione · 1 errore.

import { readFileSync, writeFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

import {
  SEVERITY,
  STATUS,
  checkChatIntegrity,
  classify,
  makeAnonymizer,
  typedFieldsFromProto,
  verdict,
} from './audit-lib.mjs';

export const EXPECTED_PROJECT = 'nexus-social-e3eee';
export const EXIT = { PASS: 0, WARNINGS: 10, BLOCKED: 20, CONFIG: 2, ERROR: 1 };

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

// Controlla destinazione e credenziali prima di qualsiasi lettura.
export function checkTarget(opts, env = process.env) {
  const emulatorHost = env.FIRESTORE_EMULATOR_HOST;
  if (opts.emulator) {
    if (!emulatorHost) throw new ConfigError('--emulator richiede FIRESTORE_EMULATOR_HOST');
    if (!opts.project.startsWith('demo-')) throw new ConfigError("con --emulator il progetto deve iniziare con 'demo-'");
    return { mode: 'emulatore', project: opts.project };
  }
  if (emulatorHost) {
    throw new ConfigError('FIRESTORE_EMULATOR_HOST è impostato: rimuoverlo per il progetto reale, oppure usare --emulator');
  }
  if (opts.project !== EXPECTED_PROJECT) {
    throw new ConfigError(`progetto non previsto: atteso ${EXPECTED_PROJECT}`);
  }
  const detected = credentialProject(env);
  if (!detected) {
    throw new ConfigError('impossibile verificare il progetto delle credenziali (nessun project_id né GOOGLE_CLOUD_PROJECT)');
  }
  if (detected !== opts.project) {
    throw new ConfigError('le credenziali appartengono a un progetto diverso da quello richiesto');
  }
  return { mode: 'produzione (sola lettura)', project: opts.project };
}

function credentialProject(env) {
  const file = env.GOOGLE_APPLICATION_CREDENTIALS;
  if (file) {
    try {
      const json = JSON.parse(readFileSync(file, 'utf8'));
      if (json.project_id) return json.project_id;
    } catch {
      throw new ConfigError('GOOGLE_APPLICATION_CREDENTIALS non leggibile');
    }
  }
  return env.GOOGLE_CLOUD_PROJECT || env.GCLOUD_PROJECT || env.CLOUDSDK_CORE_PROJECT || null;
}

// ─── Accesso in sola lettura ──────────────────────────────────────────────────
//
// L'unico oggetto Firestore usato dall'audit. Espone solo letture; qualsiasi metodo di
// scrittura sull'istanza (batch, transazioni, bulkWriter, recursiveDelete) lancia un
// errore. I riferimenti a documenti e collezioni non escono da questo modulo.

const FORBIDDEN_DB = new Set(['batch', 'bulkWriter', 'runTransaction', 'recursiveDelete', 'settings']);

export function readOnly(db) {
  const guarded = new Proxy(db, {
    get(target, prop, receiver) {
      if (FORBIDDEN_DB.has(prop)) throw new Error(`operazione non consentita nell'audit: ${String(prop)}`);
      return Reflect.get(target, prop, receiver);
    },
  });
  let reads = 0;
  return {
    get reads() { return reads; },
    rootCollections: async () => { reads += 1; return (await guarded.listCollections()).map((c) => c.id); },
    // Riferimenti di tutti i documenti di una collezione, anche quelli "mancanti" che
    // hanno solo sottocollezioni.
    documentIds: async (collectionPath) => {
      reads += 1;
      return (await guarded.collection(collectionPath).listDocuments()).map((r) => r.id);
    },
    subcollections: async (docPath) => {
      reads += 1;
      return (await guarded.doc(docPath).listCollections()).map((c) => c.id);
    },
    // Lettura di documenti a blocchi; restituisce solo id, esistenza e campi tipizzati.
    getDocuments: async (collectionPath, ids) => {
      const out = [];
      for (let i = 0; i < ids.length; i += 300) {
        const refs = ids.slice(i, i + 300).map((id) => guarded.collection(collectionPath).doc(id));
        if (!refs.length) continue;
        reads += refs.length;
        const snaps = await guarded.getAll(...refs);
        for (const s of snaps) {
          if (!s.exists) { out.push({ id: s.id, exists: false }); continue; }
          if (!s._fieldsProto) throw new Error('rappresentazione tipizzata del documento non disponibile');
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

export async function runAudit(ro, { anonymizer = makeAnonymizer(process.env.AUDIT_ID_SALT) } = {}) {
  const report = {
    examined: 0,
    missingParents: 0,
    byStatus: Object.fromEntries(Object.values(STATUS).map((s) => [s, 0])),
    byKind: {},
    findings: {},
    issueCodes: {},
    privateIndicators: {},
    collections: [],
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

  // Tutti i documenti di una collezione non prevista, ricorsivamente.
  const walkUnknown = async (collSegments) => {
    const collPath = collSegments.join('/');
    const ids = await ro.documentIds(collPath);
    for (const d of await ro.getDocuments(collPath, ids)) {
      if (d.exists) record('unknown', [...collSegments, d.id], d);
      else report.missingParents += 1;
      for (const sub of await ro.subcollections(`${collPath}/${d.id}`)) {
        await walkUnknown([...collSegments, d.id, sub]);
      }
    }
  };

  const roots = await ro.rootCollections();
  report.collections = roots.map((c) => (KNOWN[c] ? c : '<collezione_non_prevista>'));

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

  const count = (sev) => Object.entries(report.findings)
    .filter(([k]) => k.startsWith(`${sev}:`))
    .reduce((n, [, v]) => n + v.count, 0);
  report.blocking = count(SEVERITY.BLOCKING);
  report.warnings = count(SEVERITY.WARNING);
  report.legacy = report.byStatus[STATUS.LEGACY];
  report.compatible = report.byStatus[STATUS.COMPATIBLE];
  report.verdict = verdict(report.blocking, report.warnings);
  report.reads = ro.reads;
  report.anonymization = anonymizer.saltProvided ? 'HMAC-SHA256 con AUDIT_ID_SALT' : 'HMAC-SHA256 con sale casuale (non riproducibile)';
  return report;
}

export function exitCodeFor(verdictText) {
  return { PASS: EXIT.PASS, 'PASS CON AVVISI': EXIT.WARNINGS, BLOCKED: EXIT.BLOCKED }[verdictText];
}

export function summaryText(target, report) {
  const lines = [
    `Audit Firestore in sola lettura — ${target.mode}`,
    `Progetto: ${target.project}`,
    `Collezioni radice: ${report.collections.join(', ') || '(nessuna)'}`,
    `Documenti esaminati: ${report.examined}  (riferimenti senza documento: ${report.missingParents})`,
    `Compatibili: ${report.compatible} · Legacy: ${report.legacy} · Non conformi: ${report.byStatus[STATUS.NON_CONFORMING]} · Fuori regole: ${report.byStatus[STATUS.OUTSIDE_RULES]}`,
    `Anomalie bloccanti: ${report.blocking} · Avvisi: ${report.warnings}`,
  ];
  for (const [k, v] of Object.entries(report.findings).sort()) lines.push(`  ${k}: ${v.count}`);
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
  const app = target.mode === 'emulatore'
    ? initializeApp({ projectId: opts.project }, 'nexus-audit')
    : initializeApp({ projectId: opts.project, credential: applicationDefault() }, 'nexus-audit');

  const report = await runAudit(readOnly(getFirestore(app)));
  const full = { project: target.project, mode: target.mode, generatedAt: new Date().toISOString(), ...report };

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
