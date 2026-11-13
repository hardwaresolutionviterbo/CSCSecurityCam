// Nexus Social — logica dell'audit Firestore in sola lettura.
//
// Questo modulo NON accede a Firestore: riceve documenti già letti e li classifica
// rispetto a firebase/firestore.rules. I validatori riproducono le funzioni delle regole
// (stessi nomi, stessi vincoli); i test in audit.test.mjs confrontano il loro esito con
// quello delle regole vere nel Firestore Emulator.
//
// Privacy: nessuna funzione restituisce valori dei documenti. I report contengono solo
// codici di anomalia, nomi di campo previsti dallo schema e identificativi anonimizzati.

import { createHmac, randomBytes } from 'node:crypto';

// ─── Valori tipizzati ──────────────────────────────────────────────────────────
//
// Le regole distinguono int e double, timestamp, map, list… Il valore JS restituito
// dall'SDK non basta (2 e 2.0 sono entrambi number), quindi si usa la rappresentazione
// protobuf del documento. Ogni valore diventa { t: <tipo>, v: <valore> }.

export function typedFromProto(p) {
  if (p == null || typeof p !== 'object') throw new Error('valore protobuf non valido');
  const kind = p.valueType ?? Object.keys(p).find((k) => k.endsWith('Value'));
  switch (kind) {
    case 'nullValue': return { t: 'null', v: null };
    case 'booleanValue': return { t: 'bool', v: Boolean(p.booleanValue) };
    case 'integerValue': return { t: 'int', v: Number(p.integerValue) };
    case 'doubleValue': return { t: 'double', v: Number(p.doubleValue) };
    case 'timestampValue': return { t: 'timestamp', v: p.timestampValue };
    case 'stringValue': return { t: 'string', v: String(p.stringValue) };
    case 'bytesValue': return { t: 'bytes', v: null };
    case 'referenceValue': return { t: 'reference', v: null };
    case 'geoPointValue': return { t: 'geopoint', v: null };
    case 'arrayValue':
      return { t: 'list', v: (p.arrayValue?.values ?? []).map(typedFromProto) };
    case 'mapValue': return { t: 'map', v: typedFieldsFromProto(p.mapValue?.fields ?? {}) };
    default: throw new Error(`tipo protobuf non riconosciuto: ${String(kind)}`);
  }
}

export function typedFieldsFromProto(fields) {
  const out = {};
  for (const [k, p] of Object.entries(fields)) out[k] = typedFromProto(p);
  return out;
}

// ─── Primitive (come in firestore.rules) ──────────────────────────────────────

const isStr = (x) => x?.t === 'string';
const len = (s) => [...s].length; // caratteri, come string.size() nelle regole

export const isUid = (x) => isStr(x) && len(x.v) >= 1 && len(x.v) <= 128 && /^[A-Za-z0-9_-]+$/.test(x.v);
export const hasEmail = (s) => /@/s.test(s);
export const hasPhone = (s) => /[0-9][0-9 ./()+-]{5,}[0-9]/s.test(s);
export const isSafeText = (x, max) => isStr(x) && len(x.v) >= 1 && len(x.v) <= max && !hasEmail(x.v) && !hasPhone(x.v);
export const isUsername = (x) => isStr(x) && /^[A-Za-z0-9._]{3,24}$/.test(x.v);
export const isPublicName = (x) => isStr(x) && len(x.v) >= 1 && len(x.v) <= 81 && !hasPhone(x.v);
export const isHttpsUrl = (x) => isStr(x) && len(x.v) <= 2048 && /^https:\/\/[^ @]+$/.test(x.v);
export const isBase64 = (x, max) => isStr(x) && len(x.v) > 0 && len(x.v) <= max && /^[A-Za-z0-9+/]+={0,2}$/.test(x.v);
export const isTimestamp = (x) => x?.t === 'timestamp';
export const isInt = (x) => x?.t === 'int';
export const isBool = (x) => x?.t === 'bool';
export const isMap = (x) => x?.t === 'map';
export const isSchemaV2 = (d) => isInt(d.schemaVersion) && d.schemaVersion.v === 2;
// Visibile alle query filtrate where('schemaVersion', '==', 2): Firestore confronta i
// numeri per valore, quindi anche 2.0 decimale compare nei risultati (mentre la lettura
// singola lo rifiuta, perché le regole richiedono un intero).
export const isQueryVisible = (d) =>
  (d.schemaVersion?.t === 'int' || d.schemaVersion?.t === 'double') && d.schemaVersion.v === 2;
const isIn = (x, list) => isStr(x) && list.includes(x.v);

// ─── Schemi: campi obbligatori, ammessi e controlli per campo ─────────────────
//
// Ogni controllo restituisce un elenco di problemi; un documento è conforme se l'elenco
// è vuoto. Le funzioni corrispondono a isPublicProfile, validPushDevice, isChat,
// isMembership, isMessage e isMessageRequest di firestore.rules.

function checkKeys(d, required, allowed, issues) {
  const keys = Object.keys(d);
  for (const k of required) if (!keys.includes(k)) issues.push(`campo_mancante:${k}`);
  for (const k of keys) if (!allowed.includes(k)) issues.push(`campo_non_ammesso:${safeFieldName(k)}`);
}

function check(cond, code, issues) {
  if (!cond) issues.push(code);
}

export function checkPublicProfile(d) {
  const issues = [];
  checkKeys(d, ['username', 'publicName', 'updatedAt'], ['username', 'publicName', 'avatarUrl', 'updatedAt'], issues);
  if ('username' in d) check(isUsername(d.username), 'valore_non_valido:username', issues);
  if ('publicName' in d) check(isPublicName(d.publicName), 'valore_non_valido:publicName', issues);
  if ('updatedAt' in d) check(isTimestamp(d.updatedAt), 'tipo_errato:updatedAt', issues);
  if ('avatarUrl' in d) check(isHttpsUrl(d.avatarUrl), 'valore_non_valido:avatarUrl', issues);
  return issues;
}

// Stesso controllo di validPushDevice (applicato dalle regole alle scritture del
// proprietario): un dispositivo non conforme blocca l'aggiornamento da parte dell'app.
export function checkPushDevice(deviceId, d) {
  const issues = [];
  check(len(deviceId) >= 10 && len(deviceId) <= 128 && /^[A-Za-z0-9_-]+$/.test(deviceId), 'id_non_valido', issues);
  const fields = ['platform', 'appVersion', 'enabled', 'createdAt', 'updatedAt', 'lastSeenAt'];
  checkKeys(d, fields, fields, issues);
  if ('platform' in d) check(isIn(d.platform, ['android', 'ios']), 'valore_non_valido:platform', issues);
  if ('appVersion' in d) check(isStr(d.appVersion) && len(d.appVersion.v) <= 20, 'valore_non_valido:appVersion', issues);
  if ('enabled' in d) check(isBool(d.enabled) && d.enabled.v === true, 'valore_non_valido:enabled', issues);
  for (const k of ['createdAt', 'updatedAt', 'lastSeenAt']) {
    if (k in d) check(isTimestamp(d[k]), `tipo_errato:${k}`, issues);
  }
  return issues;
}

function checkMemberChangeField(c, issues) {
  if (!isMap(c)) return issues.push('tipo_errato:lastMemberChange');
  const m = c.v;
  checkKeys(m, ['op', 'uid'], ['op', 'uid'], issues);
  if ('op' in m) check(isIn(m.op, ['add', 'remove']), 'valore_non_valido:lastMemberChange.op', issues);
  if ('uid' in m) check(isUid(m.uid), 'valore_non_valido:lastMemberChange.uid', issues);
}

export function checkChat(d) {
  const issues = [];
  checkKeys(
    d,
    ['type', 'createdBy', 'createdAt', 'updatedAt', 'schemaVersion', 'memberCount', 'lastMemberChange'],
    ['type', 'createdBy', 'createdAt', 'updatedAt', 'schemaVersion', 'memberCount', 'lastMemberChange',
      'title', 'avatarUrl', 'lastMessageAt'],
    issues,
  );
  if ('memberCount' in d) {
    check(isInt(d.memberCount) && d.memberCount.v >= 1 && d.memberCount.v <= 256, 'valore_non_valido:memberCount', issues);
  }
  if ('lastMemberChange' in d) checkMemberChangeField(d.lastMemberChange, issues);
  if ('type' in d) check(isIn(d.type, ['direct', 'group']), 'valore_non_valido:type', issues);
  if ('createdBy' in d) check(isUid(d.createdBy), 'valore_non_valido:createdBy', issues);
  for (const k of ['createdAt', 'updatedAt']) if (k in d) check(isTimestamp(d[k]), `tipo_errato:${k}`, issues);
  if ('schemaVersion' in d) check(isSchemaV2(d), 'valore_non_valido:schemaVersion', issues);
  if ('title' in d) check(isSafeText(d.title, 80), 'valore_non_valido:title', issues);
  if ('avatarUrl' in d) check(isHttpsUrl(d.avatarUrl), 'valore_non_valido:avatarUrl', issues);
  if ('lastMessageAt' in d) check(isTimestamp(d.lastMessageAt), 'tipo_errato:lastMessageAt', issues);
  return issues;
}

function checkPermissions(p, role, issues) {
  if (!isMap(p)) return issues.push('tipo_errato:permissions');
  const m = p.v;
  checkKeys(m, ['addMembers', 'removeMembers'], ['addMembers', 'removeMembers'], issues);
  const add = m.addMembers;
  const rem = m.removeMembers;
  check(isBool(add) && isBool(rem), 'tipo_errato:permissions', issues);
  if (isBool(add) && isBool(rem)) {
    if (role === 'owner') check(add.v && rem.v, 'permessi_incoerenti_col_ruolo', issues);
    if (role === 'member') check(!add.v && !rem.v, 'permessi_incoerenti_col_ruolo', issues);
  }
}

export function checkMembership(memberId, d) {
  const issues = [];
  const fields = ['uid', 'role', 'permissions', 'joinedAt', 'addedBy', 'schemaVersion'];
  checkKeys(d, fields, fields, issues);
  if ('uid' in d) {
    check(isStr(d.uid) && d.uid.v === memberId, 'uid_diverso_da_id_documento', issues);
    check(isUid(d.uid), 'valore_non_valido:uid', issues);
  }
  if ('role' in d) check(isIn(d.role, ['owner', 'admin', 'member']), 'valore_non_valido:role', issues);
  if ('permissions' in d) checkPermissions(d.permissions, isStr(d.role) ? d.role.v : null, issues);
  if ('joinedAt' in d) check(isTimestamp(d.joinedAt), 'tipo_errato:joinedAt', issues);
  if ('addedBy' in d) check(isUid(d.addedBy), 'valore_non_valido:addedBy', issues);
  if ('schemaVersion' in d) check(isSchemaV2(d), 'valore_non_valido:schemaVersion', issues);
  return issues;
}

export function checkMessage(d) {
  const issues = [];
  checkKeys(
    d,
    ['senderId', 'ciphertext', 'iv', 'type', 'createdAt', 'status', 'deletedForAll', 'schemaVersion'],
    ['senderId', 'ciphertext', 'iv', 'type', 'createdAt', 'status', 'deletedForAll', 'schemaVersion', 'replyTo'],
    issues,
  );
  if ('senderId' in d) check(isUid(d.senderId), 'valore_non_valido:senderId', issues);
  if ('ciphertext' in d) check(isBase64(d.ciphertext, 65535), 'valore_non_valido:ciphertext', issues);
  if ('iv' in d) check(isBase64(d.iv, 64), 'valore_non_valido:iv', issues);
  if ('type' in d) check(isIn(d.type, ['text', 'image', 'video', 'audio', 'file']), 'valore_non_valido:type', issues);
  if ('createdAt' in d) check(isTimestamp(d.createdAt), 'tipo_errato:createdAt', issues);
  if ('status' in d) check(isIn(d.status, ['sent', 'delivered', 'read']), 'valore_non_valido:status', issues);
  if ('deletedForAll' in d) check(isBool(d.deletedForAll), 'tipo_errato:deletedForAll', issues);
  if ('schemaVersion' in d) check(isSchemaV2(d), 'valore_non_valido:schemaVersion', issues);
  if ('replyTo' in d) check(d.replyTo.t === 'null' || isUid(d.replyTo), 'valore_non_valido:replyTo', issues);
  return issues;
}

export function checkMessageRequest(d) {
  const issues = [];
  checkKeys(
    d,
    ['from', 'to', 'status', 'createdAt', 'schemaVersion'],
    ['from', 'to', 'status', 'createdAt', 'updatedAt', 'schemaVersion'],
    issues,
  );
  if ('from' in d) check(isUid(d.from), 'valore_non_valido:from', issues);
  if ('to' in d) check(isUid(d.to), 'valore_non_valido:to', issues);
  if (isUid(d.from) && isUid(d.to)) check(d.from.v !== d.to.v, 'mittente_uguale_destinatario', issues);
  if ('status' in d) check(isIn(d.status, ['pending', 'accepted', 'declined']), 'valore_non_valido:status', issues);
  if ('createdAt' in d) check(isTimestamp(d.createdAt), 'tipo_errato:createdAt', issues);
  if ('updatedAt' in d) check(isTimestamp(d.updatedAt), 'tipo_errato:updatedAt', issues);
  if ('schemaVersion' in d) check(isSchemaV2(d), 'valore_non_valido:schemaVersion', issues);
  return issues;
}

// ─── Indicatori di dati privati ───────────────────────────────────────────────
//
// Cerca email e numeri di telefono nei valori di testo, anche annidati. Restituisce solo
// il percorso del campo (normalizzato) e la categoria, mai il valore. I campi cifrati
// (ciphertext, iv) sono esclusi: il base64 contiene cifre e darebbe falsi positivi.

const SKIP_PRIVATE_SCAN = new Set(['ciphertext', 'iv']);

export function privateIndicators(fields) {
  const found = [];
  const walk = (x, path) => {
    if (x.t === 'string') {
      if (hasEmail(x.v)) found.push(`${path}:email`);
      if (hasPhone(x.v)) found.push(`${path}:telefono`);
    } else if (x.t === 'map') {
      for (const [k, v] of Object.entries(x.v)) walk(v, `${path}.${safeFieldName(k)}`);
    } else if (x.t === 'list') {
      x.v.forEach((v) => walk(v, `${path}[]`));
    }
  };
  for (const [k, v] of Object.entries(fields)) {
    if (!SKIP_PRIVATE_SCAN.has(k)) walk(v, safeFieldName(k));
  }
  return [...new Set(found)];
}

// Un nome di campo compare nel report solo se ha la forma di un identificatore e non
// contiene una @ o lunghe sequenze di cifre: le chiavi possono essere dati (es. una
// mappa indicizzata per email o numero) e non vanno mai stampate.
export function safeFieldName(k) {
  return /^[A-Za-z_][A-Za-z0-9_]{0,40}$/.test(k) && !/[0-9]{4,}/.test(k) ? k : '<campo_non_standard>';
}

// ─── Anonimizzazione ──────────────────────────────────────────────────────────

// Nomi di collezione che possono comparire nel report: solo quelli dello schema.
// Qualsiasi altro nome (potrebbe essere un dato, es. una collezione per utente) diventa
// un segnaposto.
export const KNOWN_COLLECTIONS = new Set(['profiles', 'devices', 'chats', 'members', 'messages', 'messageRequests']);
export const UNKNOWN_COLLECTION = '<collezione_non_prevista>';
export const collectionLabel = (name) => (KNOWN_COLLECTIONS.has(name) ? name : UNKNOWN_COLLECTION);

export function makeAnonymizer(saltHex) {
  const salt = saltHex ? Buffer.from(saltHex, 'hex') : randomBytes(32);
  const cache = { __proto__: null };
  const id = (raw) => {
    cache[raw] ??= createHmac('sha256', salt).update(raw).digest('hex').slice(0, 12);
    return cache[raw];
  };
  // Percorso tecnico con ID anonimi: chats/{a1b2…}/messages/{c3d4…}
  const path = (segments) => segments.map((s, i) => (i % 2 === 0 ? collectionLabel(s) : `{${id(s)}}`)).join('/');
  return { id, path, saltProvided: Boolean(saltHex) };
}

// ─── Classificazione ──────────────────────────────────────────────────────────

export const SEVERITY = { BLOCKING: 'bloccante', WARNING: 'avviso' };

// Stati del documento.
export const STATUS = {
  COMPATIBLE: 'compatibile',
  LEGACY: 'legacy',
  NON_CONFORMING: 'non_conforme',
  OUTSIDE_RULES: 'fuori_regole',
};

// Classifica un documento in base al suo percorso. kind è uno di:
// profile, device, chat, member, message, messageRequest, unknown.
export function classify(kind, docId, fields) {
  const result = { kind, status: STATUS.COMPATIBLE, issues: [], findings: [] };
  const add = (severity, code) => result.findings.push({ severity, code });

  switch (kind) {
    case 'profile': {
      result.issues = checkPublicProfile(fields);
      if (result.issues.length) {
        // I profili non hanno schemaVersion: un profilo non conforme non è leggibile
        // dopo il deploy (get rivalidato) e non è mai esposto da query (list negata).
        result.status = STATUS.NON_CONFORMING;
        add(SEVERITY.WARNING, 'profilo_non_leggibile_dopo_deploy');
      }
      break;
    }
    case 'device': {
      result.issues = checkPushDevice(docId, fields);
      if (result.issues.length) {
        result.status = STATUS.NON_CONFORMING;
        add(SEVERITY.WARNING, 'dispositivo_non_aggiornabile_dall_app');
      }
      break;
    }
    case 'chat': {
      result.issues = checkChat(fields);
      if (!result.issues.length) break;
      if ('members' in fields || !isSchemaV2(fields)) {
        result.status = STATUS.LEGACY;
        add(SEVERITY.WARNING, 'members' in fields ? 'chat_legacy_array_members' : 'chat_senza_schema_v2');
      } else {
        result.status = STATUS.NON_CONFORMING;
        add(SEVERITY.BLOCKING, 'chat_v2_non_conforme');
      }
      break;
    }
    case 'member':
    case 'message':
    case 'messageRequest': {
      const checker = { member: () => checkMembership(docId, fields), message: () => checkMessage(fields),
        messageRequest: () => checkMessageRequest(fields) }[kind];
      result.issues = checker();
      if (!result.issues.length) break;
      if (!isQueryVisible(fields)) {
        // Escluso dalle query (filtro schemaVersion == 2) e non leggibile con get.
        result.status = STATUS.LEGACY;
        add(SEVERITY.WARNING, `${kind}_senza_schema_v2`);
      } else {
        // schemaVersion 2 (anche 2.0) ma non conforme: compare nelle query senza essere
        // rivalidato.
        result.status = STATUS.NON_CONFORMING;
        add(SEVERITY.BLOCKING, `${kind}_v2_non_conforme_esposto_nelle_query`);
      }
      break;
    }
    case 'memberOutsideChats': {
      // Sottocollezione "members" fuori da chats/{chatId}: le regole non consentono la
      // lettura singola, ma la regola collection group /{path=**}/members/{memberId}
      // restituisce il documento a chi ha uid == resource.data.uid se schemaVersion == 2.
      result.status = STATUS.OUTSIDE_RULES;
      if (isQueryVisible(fields) && isStr(fields.uid)) {
        add(SEVERITY.BLOCKING, 'members_fuori_chat_esposto_collection_group');
      } else {
        add(SEVERITY.WARNING, 'collezione_non_prevista_dalle_regole');
      }
      break;
    }
    default: {
      result.status = STATUS.OUTSIDE_RULES;
      add(SEVERITY.WARNING, 'collezione_non_prevista_dalle_regole');
    }
  }

  const privates = privateIndicators(fields);
  if (privates.length) {
    result.privateIndicators = privates;
    // Un indicatore in un documento esposto alle query è già bloccante tramite lo stato;
    // negli altri casi resta un avviso da verificare (es. username fatto di cifre).
    add(SEVERITY.WARNING, 'possibile_dato_privato');
  }
  return result;
}

// Controlli sulla chat nel suo insieme, dopo aver letto le membership.
// members: elenco di { id, fields, conforming }.
export function checkChatIntegrity(chat, members) {
  const findings = [];
  const valid = members.filter((m) => m.conforming);
  if (chat.exists && chat.conforming) {
    if (chat.fields.memberCount.v !== valid.length) findings.push({ severity: SEVERITY.BLOCKING, code: 'contatore_membri_incoerente' });
    const owners = valid.filter((m) => m.fields.role.v === 'owner');
    if (owners.length !== 1) findings.push({ severity: SEVERITY.BLOCKING, code: 'owner_mancante_o_multiplo' });
    else if (owners[0].id !== chat.fields.createdBy.v) findings.push({ severity: SEVERITY.WARNING, code: 'owner_diverso_dal_creatore' });
    if (chat.fields.type.v === 'direct' && valid.length > 2) findings.push({ severity: SEVERITY.BLOCKING, code: 'chat_diretta_con_piu_di_due_membri' });
    if (valid.length > 256) findings.push({ severity: SEVERITY.BLOCKING, code: 'oltre_256_membri' });
  }
  if (!chat.exists && members.length) findings.push({ severity: SEVERITY.WARNING, code: 'membership_in_chat_inesistente' });
  if (chat.exists && !chat.conforming && valid.length) findings.push({ severity: SEVERITY.WARNING, code: 'membership_v2_in_chat_non_conforme' });
  return findings;
}

// Esito complessivo. BLOCKED prevale sempre; un audit incompleto (letture parziali,
// errori, database non analizzati) non può mai risultare PASS.
export function verdict(blocking, warnings, incomplete = false) {
  if (blocking > 0) return 'BLOCKED';
  if (incomplete) return 'INCOMPLETO';
  if (warnings > 0) return 'PASS CON AVVISI';
  return 'PASS';
}
