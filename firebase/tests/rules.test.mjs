// Test permanenti delle regole Firestore (Fase A.1).
//
// Ogni caso negativo ha accanto un controllo positivo con dati validi: così un test di
// diniego non può passare solo perché una regola nega tutto o per un errore generico.
// Eseguiti con il Firestore Emulator: vedi firebase/tests/README.md.

import { after, before, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  Timestamp,
  collection,
  collectionGroup,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  limit,
  orderBy,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
  writeBatch,
} from 'firebase/firestore';

const RULES_PATH = fileURLToPath(new URL('../firestore.rules', import.meta.url));

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-nexus-social',
    firestore: { rules: readFileSync(RULES_PATH, 'utf8') },
  });
});

after(async () => {
  // Report di copertura delle regole (facoltativo): mostra quali espressioni sono state
  // valutate, con quale esito e se hanno prodotto errori di valutazione.
  const out = process.env.RULES_COVERAGE_FILE;
  const host = process.env.FIRESTORE_EMULATOR_HOST;
  if (out && host) {
    const base = `http://${host}/emulator/v1/projects/demo-nexus-social:ruleCoverage`;
    writeFileSync(out, await (await fetch(base)).text());
    writeFileSync(out.replace(/\.json$/, '.html'), await (await fetch(`${base}.html`)).text());
  }
  await env?.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
});

// ─── Helper ──────────────────────────────────────────────────────────────────

const db = (uid) => env.authenticatedContext(uid).firestore();
const anon = () => env.unauthenticatedContext().firestore();

// Riga della regola finale "tutto il resto è negato": ogni valutazione dell'emulatore
// termina con il suo esito, quindi delimita i passaggi nel messaggio di errore.
const CATCH_ALL_LINE = (() => {
  const lines = readFileSync(RULES_PATH, 'utf8').split('\n');
  const i = lines.findLastIndex((l) => l.includes('allow read, write: if false;'));
  assert.ok(i >= 0, 'regola finale di diniego non trovata');
  return i + 1;
})();

// Esito decisivo nel messaggio dell'emulatore. Il dettaglio elenca un esito per regola,
// per ogni passaggio di valutazione (es. "false for 'get' @ L110, false for 'get' @ L354");
// l'emulatore fa un passaggio preliminare che può contenere errori e chiude con quello
// sul documento reale. Si considera decisivo l'ultimo passaggio, che termina con la
// regola finale di diniego.
function decisivePass(message) {
  const body = String(message).replace(/^[\s\S]*?PERMISSION_DENIED:\s*/, '');
  const items = body.split(/,\s*(?=[^,]*for '[a-z]+' @ L\d+)/);
  const passes = [];
  let current = [];
  for (const item of items) {
    current.push(item.trim());
    if (new RegExp(`@ L${CATCH_ALL_LINE}\\b`).test(item)) {
      passes.push(current);
      current = [];
    }
  }
  if (current.length) passes.push(current);
  return passes.at(-1) ?? [];
}

const isCleanFalse = (item) => /^false for '/.test(item);

// Diniego autorizzativo pulito: permission-denied e, nel passaggio decisivo, solo esiti
// "false". Un test negativo non può quindi passare perché una regola va in errore
// (campo mancante, documento nullo) invece di valutare una condizione falsa.
//
// Per le query (list) le regole sono valutate sui filtri della query, non sui documenti:
// se la query omette un filtro richiesto, l'emulatore risponde "Property <campo> is
// undefined". Con { missingFilter: '<campo>' } il test verifica che il diniego sia dovuto
// proprio a quel filtro mancante e a nient'altro (anche più campi: ['from', 'schemaVersion']).
async function denied(promise, { missingFilter } = {}) {
  const err = await assertFails(promise);
  assert.equal(err.code, 'permission-denied', `atteso permission-denied, ricevuto ${err.code}`);
  const pass = decisivePass(err.message);
  const fields = [missingFilter].flat().filter((f) => f !== undefined);
  const filterError = (item) => fields.some(
    (f) => item.startsWith(`Property ${f} is undefined on object. for 'list'`));
  assert.ok(pass.length > 0 && pass.every((i) => isCleanFalse(i) || filterError(i)),
    `diniego non dovuto a una condizione falsa:\n${err.message}`);
  if (missingFilter !== undefined) {
    assert.ok(pass.some(filterError), `atteso diniego per filtro mancante su ${missingFilter}:\n${err.message}`);
  }
  return err;
}

async function seed(path, data) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), path), data);
  });
}

async function readAdmin(path) {
  let snap;
  await env.withSecurityRulesDisabled(async (ctx) => {
    snap = await getDoc(doc(ctx.firestore(), path));
  });
  return snap;
}

const past = () => Timestamp.fromMillis(Date.now() - 3_600_000);

const PERMS = {
  owner: { addMembers: true, removeMembers: true },
  adminFull: { addMembers: true, removeMembers: true },
  adminNone: { addMembers: false, removeMembers: false },
  member: { addMembers: false, removeMembers: false },
};

const validProfile = (extra = {}) => ({
  username: 'carlo.c',
  publicName: 'Carlo C.',
  avatarUrl: 'https://cdn.example.com/a/carlo.webp',
  updatedAt: past(),
  ...extra,
});

const validChat = (createdBy, extra = {}) => ({
  type: 'group',
  createdBy,
  createdAt: past(),
  updatedAt: past(),
  schemaVersion: 2,
  title: 'Calcetto giovedì',
  ...extra,
});

const membership = (uid, role, permissions, addedBy, extra = {}) => ({
  uid,
  role,
  permissions,
  joinedAt: past(),
  addedBy,
  schemaVersion: 2,
  ...extra,
});

const validMessage = (senderId, extra = {}) => ({
  senderId,
  ciphertext: 'U2FsdGVkX1+8m0dGZXN0Y2lmcmF0bw==',
  iv: 'aXZkaXByb3ZhMTIz',
  type: 'text',
  createdAt: past(),
  status: 'sent',
  deletedForAll: false,
  schemaVersion: 2,
  ...extra,
});

const validRequest = (from, to, extra = {}) => ({
  from,
  to,
  status: 'pending',
  createdAt: past(),
  schemaVersion: 2,
  ...extra,
});

// Gruppo g1: owner, admin con tutti i permessi, admin senza permessi, due member.
async function seedGroup(chatId = 'g1') {
  await seed(`chats/${chatId}`, validChat('owner'));
  await seed(`chats/${chatId}/members/owner`, membership('owner', 'owner', PERMS.owner, 'owner'));
  await seed(`chats/${chatId}/members/admin`, membership('admin', 'admin', PERMS.adminFull, 'owner'));
  await seed(`chats/${chatId}/members/weakadmin`, membership('weakadmin', 'admin', PERMS.adminNone, 'owner'));
  await seed(`chats/${chatId}/members/bob`, membership('bob', 'member', PERMS.member, 'owner'));
  await seed(`chats/${chatId}/members/carol`, membership('carol', 'member', PERMS.member, 'owner'));
  await seed(`chats/${chatId}/messages/m1`, validMessage('owner'));
}

// Nuova membership scritta da un client (joinedAt = ora del server).
const invite = (uid, role, permissions, addedBy) => ({
  uid,
  role,
  permissions,
  joinedAt: serverTimestamp(),
  addedBy,
  schemaVersion: 2,
});

// ─── Profili ─────────────────────────────────────────────────────────────────

describe('profiles: lettura', () => {
  test('profilo valido leggibile da un utente autenticato (controllo positivo)', async () => {
    await seed('profiles/carlo', validProfile());
    await assertSucceeds(getDoc(doc(db('alice'), 'profiles/carlo')));
  });

  test('profilo senza avatarUrl leggibile (campo facoltativo)', async () => {
    const { avatarUrl, ...rest } = validProfile();
    await seed('profiles/carlo', rest);
    await assertSucceeds(getDoc(doc(db('alice'), 'profiles/carlo')));
  });

  test('utente non autenticato non legge i profili', async () => {
    await seed('profiles/carlo', validProfile());
    await denied(getDoc(doc(anon(), 'profiles/carlo')));
  });

  test('profilo con campi extra privati non leggibile', async () => {
    await seed('profiles/p1', validProfile({ email: 'carlo@example.com' }));
    await seed('profiles/p2', validProfile({ address: 'Via Roma 1' }));
    await seed('profiles/ok', validProfile());
    await denied(getDoc(doc(db('alice'), 'profiles/p1')));
    await denied(getDoc(doc(db('alice'), 'profiles/p2')));
    await assertSucceeds(getDoc(doc(db('alice'), 'profiles/ok')));
  });

  test('profilo legacy senza campi obbligatori non leggibile', async () => {
    await seed('profiles/p1', { username: 'carlo.c', publicName: 'Carlo C.' });
    await denied(getDoc(doc(db('alice'), 'profiles/p1')));
  });

  test('username con email o telefono non leggibile', async () => {
    await seed('profiles/u1', validProfile({ username: 'carlo@example.com' }));
    await seed('profiles/u2', validProfile({ username: '3331234567' }));
    await seed('profiles/u3', validProfile({ username: 'carlo3331234567' }));
    await seed('profiles/ok', validProfile({ username: 'carlo_2026' }));
    for (const id of ['u1', 'u2', 'u3']) {
      await denied(getDoc(doc(db('alice'), `profiles/${id}`)));
    }
    await assertSucceeds(getDoc(doc(db('alice'), 'profiles/ok')));
  });

  test('publicName con email o telefono non leggibile', async () => {
    await seed('profiles/n1', validProfile({ publicName: 'carlo@example.com' }));
    await seed('profiles/n2', validProfile({ publicName: 'Carlo +39 333 123 4567' }));
    await seed('profiles/n3', validProfile({ publicName: 'Carlo 333-1234567' }));
    await seed('profiles/ok', validProfile({ publicName: 'Carlo C. 2026' }));
    for (const id of ['n1', 'n2', 'n3']) {
      await denied(getDoc(doc(db('alice'), `profiles/${id}`)));
    }
    await assertSucceeds(getDoc(doc(db('alice'), 'profiles/ok')));
  });

  test('avatarUrl, username e updatedAt con mappe annidate non leggibili', async () => {
    await seed('profiles/a1', validProfile({ avatarUrl: { email: 'carlo@example.com' } }));
    await seed('profiles/a2', validProfile({ username: { phone: '+393331234567' } }));
    await seed('profiles/a3', validProfile({ updatedAt: { email: 'carlo@example.com' } }));
    await seed('profiles/a4', validProfile({ publicName: { first: 'Carlo', last: 'Crescenzi' } }));
    await seed('profiles/a5', validProfile({ avatarUrl: ['https://cdn.example.com/a.webp'] }));
    for (const id of ['a1', 'a2', 'a3', 'a4', 'a5']) {
      await denied(getDoc(doc(db('alice'), `profiles/${id}`)));
    }
  });

  test('avatarUrl non https o con @ non leggibile', async () => {
    await seed('profiles/h1', validProfile({ avatarUrl: 'http://cdn.example.com/a.webp' }));
    await seed('profiles/h2', validProfile({ avatarUrl: 'https://carlo@example.com/a.webp' }));
    await denied(getDoc(doc(db('alice'), 'profiles/h1')));
    await denied(getDoc(doc(db('alice'), 'profiles/h2')));
  });

  test('enumerazione dei profili negata', async () => {
    await seed('profiles/carlo', validProfile());
    await denied(getDocs(collection(db('alice'), 'profiles')));
    await denied(getDocs(query(collection(db('alice'), 'profiles'), where('username', '==', 'carlo.c'))));
  });
});

describe('profiles: scrittura', () => {
  const write = (extra = {}) => ({ ...validProfile(), updatedAt: serverTimestamp(), ...extra });

  test('il proprietario crea e aggiorna un profilo valido (controllo positivo)', async () => {
    await assertSucceeds(setDoc(doc(db('carlo'), 'profiles/carlo'), write()));
    await assertSucceeds(setDoc(doc(db('carlo'), 'profiles/carlo'), write({ publicName: 'Carlo Cr.' })));
  });

  test('scritture non valide negate', async () => {
    const me = db('carlo');
    await denied(setDoc(doc(me, 'profiles/carlo'), write({ email: 'carlo@example.com' })));
    await denied(setDoc(doc(me, 'profiles/carlo'), write({ username: 'Carlo C' })));
    await denied(setDoc(doc(me, 'profiles/carlo'), write({ publicName: 'chiama 3331234567' })));
    await denied(setDoc(doc(me, 'profiles/carlo'), write({ avatarUrl: { url: 'x' } })));
    await denied(setDoc(doc(me, 'profiles/carlo'), write({ updatedAt: past() })));
  });

  test('nessuno scrive il profilo di un altro né lo cancella', async () => {
    await seed('profiles/carlo', validProfile());
    await denied(setDoc(doc(db('alice'), 'profiles/carlo'), write()));
    await denied(deleteDoc(doc(db('carlo'), 'profiles/carlo')));
  });
});

describe('profiles/{uid}/devices (invariato)', () => {
  const device = () => ({
    platform: 'android',
    appVersion: '0.1.0',
    enabled: true,
    createdAt: Timestamp.now(),
    updatedAt: Timestamp.now(),
    lastSeenAt: Timestamp.now(),
  });

  test('il proprietario registra e legge il dispositivo; gli altri no', async () => {
    const path = 'profiles/carlo/devices/fid_0123456789';
    await assertSucceeds(setDoc(doc(db('carlo'), path), device()));
    await assertSucceeds(getDoc(doc(db('carlo'), path)));
    await denied(getDoc(doc(db('alice'), path)));
    await denied(setDoc(doc(db('alice'), 'profiles/carlo/devices/fid_9876543210'), device()));
  });
});

// ─── Chat: creazione e lettura ───────────────────────────────────────────────

describe('chats: creazione', () => {
  const newChat = (createdBy, extra = {}) => ({
    ...validChat(createdBy),
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
    ...extra,
  });

  test('gruppo creato in un batch con owner e membri iniziali (controllo positivo)', async () => {
    const me = db('alice');
    const batch = writeBatch(me);
    batch.set(doc(me, 'chats/c1'), newChat('alice'));
    batch.set(doc(me, 'chats/c1/members/alice'), invite('alice', 'owner', PERMS.owner, 'alice'));
    batch.set(doc(me, 'chats/c1/members/bob'), invite('bob', 'member', PERMS.member, 'alice'));
    await assertSucceeds(batch.commit());
    await assertSucceeds(getDoc(doc(db('bob'), 'chats/c1')));
  });

  test('chat senza membership owner del creatore negata', async () => {
    await denied(setDoc(doc(db('alice'), 'chats/c1'), newChat('alice')));
  });

  test('chat con createdBy falsificato o campi extra negata', async () => {
    for (const data of [newChat('bob'), newChat('alice', { email: 'a@example.com' })]) {
      const me = db('alice');
      const batch = writeBatch(me);
      batch.set(doc(me, 'chats/c1'), data);
      batch.set(doc(me, 'chats/c1/members/alice'), invite('alice', 'owner', PERMS.owner, 'alice'));
      await denied(batch.commit());
    }
  });

  test('alla creazione nessuno diventa admin o owner oltre al creatore', async () => {
    const me = db('alice');
    const batch = writeBatch(me);
    batch.set(doc(me, 'chats/c1'), newChat('alice'));
    batch.set(doc(me, 'chats/c1/members/alice'), invite('alice', 'owner', PERMS.owner, 'alice'));
    batch.set(doc(me, 'chats/c1/members/bob'), invite('bob', 'owner', PERMS.owner, 'alice'));
    await denied(batch.commit());
  });

  test('membership owner su una chat creata da altri negata', async () => {
    await seedGroup();
    await denied(setDoc(doc(db('mallory'), 'chats/g1/members/mallory'),
      invite('mallory', 'owner', PERMS.owner, 'mallory')));
  });
});

describe('chats: lettura e privacy', () => {
  test('un membro legge la chat; un non membro no', async () => {
    await seedGroup();
    await assertSucceeds(getDoc(doc(db('bob'), 'chats/g1')));
    await denied(getDoc(doc(db('mallory'), 'chats/g1')));
  });

  test('chat con dati privati o campi extra non leggibile neanche dai membri', async () => {
    await seedGroup('ok');
    const cases = {
      e1: validChat('owner', { email: 'owner@example.com' }),
      e2: validChat('owner', { address: { street: 'Via Roma 1' } }),
      e3: validChat('owner', { title: 'Chiamami 333 123 4567' }),
      e4: validChat('owner', { title: { text: 'Calcetto' } }),
      e5: validChat('owner', { members: ['owner', 'bob'] }),
      e6: { ...validChat('owner'), schemaVersion: '2' },
    };
    for (const [id, data] of Object.entries(cases)) {
      await seed(`chats/${id}`, data);
      await seed(`chats/${id}/members/bob`, membership('bob', 'member', PERMS.member, 'owner'));
      await denied(getDoc(doc(db('bob'), `chats/${id}`)));
    }
    await assertSucceeds(getDoc(doc(db('bob'), 'chats/ok')));
  });

  test('le chat non sono enumerabili', async () => {
    await seedGroup();
    await denied(getDocs(collection(db('bob'), 'chats')));
  });

  test('aggiornamento del titolo solo da owner/admin, ultimo messaggio da tutti', async () => {
    await seedGroup();
    const ts = serverTimestamp();
    await assertSucceeds(updateDoc(doc(db('admin'), 'chats/g1'), { title: 'Calcetto venerdì', updatedAt: ts }));
    await denied(updateDoc(doc(db('bob'), 'chats/g1'), { title: 'Altro', updatedAt: ts }));
    await assertSucceeds(updateDoc(doc(db('bob'), 'chats/g1'), { lastMessageAt: ts, updatedAt: ts }));
    await denied(updateDoc(doc(db('bob'), 'chats/g1'), { createdBy: 'bob', updatedAt: ts }));
  });
});

describe('members: lettura e privacy', () => {
  test('membership valida leggibile dai membri, non da estranei', async () => {
    await seedGroup();
    await assertSucceeds(getDoc(doc(db('bob'), 'chats/g1/members/carol')));
    const roster = query(collection(db('bob'), 'chats/g1/members'), where('schemaVersion', '==', 2));
    assert.equal((await assertSucceeds(getDocs(roster))).size, 5);
    await denied(getDocs(collection(db('bob'), 'chats/g1/members')), { missingFilter: 'schemaVersion' });
    await denied(getDoc(doc(db('mallory'), 'chats/g1/members/carol')));
    await denied(getDocs(query(collection(db('mallory'), 'chats/g1/members'), where('schemaVersion', '==', 2))),
      { missingFilter: 'uid' });
  });

  test('membership con campi privati o annidati non leggibile con get', async () => {
    await seedGroup();
    await seed('chats/g1/members/x1', membership('x1', 'member', PERMS.member, 'owner', { email: 'x@example.com' }));
    await seed('chats/g1/members/x2', membership('x2', 'member', { ...PERMS.member, phone: '333' }, 'owner'));
    await seed('chats/g1/members/x3', membership('x3', 'member', PERMS.member, { email: 'x@example.com' }));
    for (const id of ['x1', 'x2', 'x3']) {
      await denied(getDoc(doc(db('bob'), `chats/g1/members/${id}`)));
    }
    await assertSucceeds(getDoc(doc(db('bob'), 'chats/g1/members/carol')));
  });

  test('collection group: solo le proprie membership', async () => {
    await seedGroup('g1');
    await seedGroup('g2');
    const mine = query(collectionGroup(db('bob'), 'members'), where('uid', '==', 'bob'), where('schemaVersion', '==', 2));
    const snap = await assertSucceeds(getDocs(mine));
    assert.equal(snap.size, 2);
    await denied(getDocs(query(collectionGroup(db('bob'), 'members'),
      where('uid', '==', 'carol'), where('schemaVersion', '==', 2))));
    await denied(getDocs(query(collectionGroup(db('bob'), 'members'), where('uid', '==', 'bob'))),
      { missingFilter: 'schemaVersion' });
    await denied(getDocs(collectionGroup(db('bob'), 'members')), { missingFilter: ['uid', 'schemaVersion'] });
  });
});

// ─── Rimozione e reinvito ────────────────────────────────────────────────────

describe('members: rimozione e reinvito', () => {
  test('rimozione: accesso a chat, messaggi e membri revocato subito', async () => {
    await seedGroup();
    const bob = db('bob');
    await assertSucceeds(getDoc(doc(bob, 'chats/g1')));
    await assertSucceeds(getDoc(doc(bob, 'chats/g1/messages/m1')));

    await assertSucceeds(deleteDoc(doc(db('admin'), 'chats/g1/members/bob')));

    await denied(getDoc(doc(bob, 'chats/g1')));
    await denied(getDoc(doc(bob, 'chats/g1/messages/m1')));
    await denied(getDocs(query(collection(bob, 'chats/g1/messages'), where('schemaVersion', '==', 2))));
    await denied(getDoc(doc(bob, 'chats/g1/members/carol')));
    await denied(setDoc(doc(bob, 'chats/g1/messages/m2'),
      { ...validMessage('bob'), createdAt: serverTimestamp() }));
  });

  test('l\'utente rimosso non può reinvitarsi da solo', async () => {
    await seedGroup();
    await assertSucceeds(deleteDoc(doc(db('admin'), 'chats/g1/members/bob')));
    await denied(setDoc(doc(db('bob'), 'chats/g1/members/bob'), invite('bob', 'member', PERMS.member, 'bob')));
    await denied(setDoc(doc(db('bob'), 'chats/g1/members/bob'), invite('bob', 'member', PERMS.member, 'owner')));
  });

  test('un membro normale non può reinvitare un utente rimosso', async () => {
    await seedGroup();
    await assertSucceeds(deleteDoc(doc(db('admin'), 'chats/g1/members/bob')));
    await denied(setDoc(doc(db('carol'), 'chats/g1/members/bob'), invite('bob', 'member', PERMS.member, 'carol')));
  });

  test('un admin senza addMembers non può reinvitare', async () => {
    await seedGroup();
    await assertSucceeds(deleteDoc(doc(db('admin'), 'chats/g1/members/bob')));
    await denied(setDoc(doc(db('weakadmin'), 'chats/g1/members/bob'),
      invite('bob', 'member', PERMS.member, 'weakadmin')));
  });

  test('admin con addMembers reinvita: nuova membership e accesso ripristinato', async () => {
    await seedGroup();
    const before = (await readAdmin('chats/g1/members/bob')).data();
    await assertSucceeds(deleteDoc(doc(db('admin'), 'chats/g1/members/bob')));
    await denied(getDoc(doc(db('bob'), 'chats/g1')));

    await assertSucceeds(setDoc(doc(db('admin'), 'chats/g1/members/bob'),
      invite('bob', 'member', PERMS.member, 'admin')));

    const fresh = (await readAdmin('chats/g1/members/bob')).data();
    assert.equal(fresh.addedBy, 'admin');
    assert.notEqual(before.addedBy, fresh.addedBy);
    assert.ok(fresh.joinedAt.toMillis() > before.joinedAt.toMillis(), 'joinedAt deve essere aggiornato');

    const bob = db('bob');
    await assertSucceeds(getDoc(doc(bob, 'chats/g1')));
    await assertSucceeds(getDoc(doc(bob, 'chats/g1/messages/m1')));
    await assertSucceeds(getDocs(query(collection(bob, 'chats/g1/messages'), where('schemaVersion', '==', 2))));
  });

  test('reinvito con addedBy o joinedAt falsificati negato', async () => {
    await seedGroup();
    await assertSucceeds(deleteDoc(doc(db('admin'), 'chats/g1/members/bob')));
    await denied(setDoc(doc(db('admin'), 'chats/g1/members/bob'), invite('bob', 'member', PERMS.member, 'owner')));
    await denied(setDoc(doc(db('admin'), 'chats/g1/members/bob'),
      { ...invite('bob', 'member', PERMS.member, 'admin'), joinedAt: past() }));
  });

  test('uscita volontaria consentita a un membro', async () => {
    await seedGroup();
    await assertSucceeds(deleteDoc(doc(db('carol'), 'chats/g1/members/carol')));
    await denied(getDoc(doc(db('carol'), 'chats/g1')));
  });

  test('un membro normale non rimuove altri; un admin non rimuove un altro admin', async () => {
    await seedGroup();
    await denied(deleteDoc(doc(db('carol'), 'chats/g1/members/bob')));
    await denied(deleteDoc(doc(db('weakadmin'), 'chats/g1/members/bob')));
    await denied(deleteDoc(doc(db('admin'), 'chats/g1/members/weakadmin')));
    await assertSucceeds(deleteDoc(doc(db('owner'), 'chats/g1/members/weakadmin')));
  });

  test('nessuna aggiunta in una chat diretta dopo la creazione', async () => {
    await seed('chats/d1', { type: 'direct', createdBy: 'owner', createdAt: past(), updatedAt: past(), schemaVersion: 2 });
    await seed('chats/d1/members/owner', membership('owner', 'owner', PERMS.owner, 'owner'));
    await assertSucceeds(getDoc(doc(db('owner'), 'chats/d1')));
    await denied(setDoc(doc(db('owner'), 'chats/d1/members/eve'), invite('eve', 'member', PERMS.member, 'owner')));
  });
});

describe('members: owner, ruoli e permessi', () => {
  test('l\'owner non può essere rimosso da nessuno, neanche da sé stesso', async () => {
    await seedGroup();
    for (const actor of ['admin', 'bob', 'owner']) {
      await denied(deleteDoc(doc(db(actor), 'chats/g1/members/owner')));
    }
    assert.ok((await readAdmin('chats/g1/members/owner')).exists());
  });

  test('il ruolo dell\'owner non si modifica', async () => {
    await seedGroup();
    await denied(updateDoc(doc(db('owner'), 'chats/g1/members/owner'), { role: 'member', permissions: PERMS.member }));
    await denied(updateDoc(doc(db('admin'), 'chats/g1/members/owner'), { role: 'member', permissions: PERMS.member }));
  });

  test('falsificazione dei ruoli in creazione negata', async () => {
    await seedGroup();
    await denied(setDoc(doc(db('bob'), 'chats/g1/members/eve'), invite('eve', 'owner', PERMS.owner, 'bob')));
    await denied(setDoc(doc(db('bob'), 'chats/g1/members/eve'), invite('eve', 'admin', PERMS.adminFull, 'bob')));
    await denied(setDoc(doc(db('admin'), 'chats/g1/members/eve'), invite('eve', 'owner', PERMS.owner, 'admin')));
    await denied(setDoc(doc(db('admin'), 'chats/g1/members/eve'), invite('eve', 'admin', PERMS.adminNone, 'admin')));
    // Controllo positivo: lo stesso admin può aggiungere un member.
    await assertSucceeds(setDoc(doc(db('admin'), 'chats/g1/members/eve'), invite('eve', 'member', PERMS.member, 'admin')));
    // L'owner può nominare direttamente un admin.
    await assertSucceeds(setDoc(doc(db('owner'), 'chats/g1/members/dan'), invite('dan', 'admin', PERMS.adminNone, 'owner')));
  });

  test('falsificazione dei permessi negata', async () => {
    await seedGroup();
    await denied(setDoc(doc(db('admin'), 'chats/g1/members/eve'),
      invite('eve', 'member', { addMembers: true, removeMembers: false }, 'admin')));
    await denied(setDoc(doc(db('admin'), 'chats/g1/members/eve'),
      invite('eve', 'member', { ...PERMS.member, editAll: true }, 'admin')));
    await denied(setDoc(doc(db('admin'), 'chats/g1/members/eve'),
      invite('eve', 'member', { addMembers: 'true', removeMembers: false }, 'admin')));
  });

  test('promozione e retrocessione solo dall\'owner', async () => {
    await seedGroup();
    const promote = { role: 'admin', permissions: PERMS.adminFull };
    await denied(updateDoc(doc(db('bob'), 'chats/g1/members/bob'), promote));
    await denied(updateDoc(doc(db('admin'), 'chats/g1/members/bob'), promote));
    await denied(updateDoc(doc(db('owner'), 'chats/g1/members/bob'), { role: 'owner', permissions: PERMS.owner }));
    await denied(updateDoc(doc(db('owner'), 'chats/g1/members/bob'), { ...promote, addedBy: 'bob' }));
    await assertSucceeds(updateDoc(doc(db('owner'), 'chats/g1/members/bob'), promote));
    await assertSucceeds(updateDoc(doc(db('owner'), 'chats/g1/members/bob'), { role: 'member', permissions: PERMS.member }));
  });
});

// ─── Messaggi ────────────────────────────────────────────────────────────────

describe('messages', () => {
  const send = (senderId, extra = {}) => ({ ...validMessage(senderId), createdAt: serverTimestamp(), ...extra });

  test('un membro invia un messaggio cifrato valido (controllo positivo)', async () => {
    await seedGroup();
    await assertSucceeds(setDoc(doc(db('bob'), 'chats/g1/messages/m2'), send('bob')));
    await assertSucceeds(setDoc(doc(db('bob'), 'chats/g1/messages/m3'), send('bob', { replyTo: 'm1' })));
  });

  test('invii non validi negati', async () => {
    await seedGroup();
    const bob = db('bob');
    await denied(setDoc(doc(bob, 'chats/g1/messages/x1'), send('carol')));
    await denied(setDoc(doc(bob, 'chats/g1/messages/x2'), send('bob', { ciphertext: 'ciao, scrivimi a bob@example.com' })));
    await denied(setDoc(doc(bob, 'chats/g1/messages/x3'), send('bob', { email: 'bob@example.com' })));
    await denied(setDoc(doc(bob, 'chats/g1/messages/x4'), send('bob', { replyTo: { email: 'bob@example.com' } })));
    await denied(setDoc(doc(bob, 'chats/g1/messages/x5'), send('bob', { createdAt: past() })));
    await denied(setDoc(doc(bob, 'chats/g1/messages/x6'), send('bob', { status: 'read' })));
    await denied(setDoc(doc(db('mallory'), 'chats/g1/messages/x7'), send('mallory')));
  });

  test('messaggio legacy con dati privati non leggibile; query filtrata sì', async () => {
    await seedGroup();
    await seed('chats/g1/messages/legacy1', { senderId: 'owner', ciphertext: 'abc', iv: 'x', email: 'owner@example.com' });
    await seed('chats/g1/messages/legacy2', validMessage('owner', { replyTo: { phone: '3331234567' } }));
    const bob = db('bob');
    await denied(getDoc(doc(bob, 'chats/g1/messages/legacy1')));
    await denied(getDoc(doc(bob, 'chats/g1/messages/legacy2')));
    await assertSucceeds(getDoc(doc(bob, 'chats/g1/messages/m1')));

    const snap = await assertSucceeds(getDocs(query(collection(bob, 'chats/g1/messages'), where('schemaVersion', '==', 2))));
    const ids = snap.docs.map((d) => d.id);
    assert.ok(ids.includes('m1'));
    assert.ok(!ids.includes('legacy1'), 'il messaggio legacy senza schemaVersion non deve comparire');
    // Senza il filtro su schemaVersion la query è negata.
    await denied(getDocs(collection(bob, 'chats/g1/messages')), { missingFilter: 'schemaVersion' });
  });

  test('stato aggiornato dai destinatari; elimina per tutti solo dal mittente', async () => {
    await seedGroup();
    await assertSucceeds(updateDoc(doc(db('bob'), 'chats/g1/messages/m1'), { status: 'read' }));
    await denied(updateDoc(doc(db('bob'), 'chats/g1/messages/m1'), { deletedForAll: true }));
    await denied(updateDoc(doc(db('bob'), 'chats/g1/messages/m1'), { ciphertext: 'QUJD' }));
    await denied(updateDoc(doc(db('bob'), 'chats/g1/messages/m1'), { status: 'letto' }));
    await assertSucceeds(updateDoc(doc(db('owner'), 'chats/g1/messages/m1'), { deletedForAll: true }));
    await denied(deleteDoc(doc(db('bob'), 'chats/g1/messages/m1')));
    await assertSucceeds(deleteDoc(doc(db('owner'), 'chats/g1/messages/m1')));
  });
});

// ─── Richieste di messaggio ──────────────────────────────────────────────────

describe('messageRequests', () => {
  const create = (from, to, extra = {}) => ({ ...validRequest(from, to), createdAt: serverTimestamp(), ...extra });

  test('richiesta valida creata dal mittente (controllo positivo)', async () => {
    await assertSucceeds(setDoc(doc(db('alice'), 'messageRequests/r1'), create('alice', 'bob')));
  });

  test('richieste non valide negate', async () => {
    const alice = db('alice');
    await denied(setDoc(doc(alice, 'messageRequests/x1'), create('bob', 'carol')));
    await denied(setDoc(doc(alice, 'messageRequests/x2'), create('alice', 'alice')));
    await denied(setDoc(doc(alice, 'messageRequests/x3'), create('alice', 'bob', { email: 'alice@example.com' })));
    await denied(setDoc(doc(alice, 'messageRequests/x4'), create('alice', 'bob', { status: 'accepted' })));
    await denied(setDoc(doc(alice, 'messageRequests/x5'), create('alice', 'bob', { to: { uid: 'bob' } })));
  });

  test('lettura solo per mittente e destinatario; legacy con dati privati negata', async () => {
    await seed('messageRequests/r1', validRequest('alice', 'bob'));
    await seed('messageRequests/legacy', { from: 'alice', to: 'bob', status: 'pending', createdAt: past(), address: 'Via Roma 1' });
    await assertSucceeds(getDoc(doc(db('bob'), 'messageRequests/r1')));
    await assertSucceeds(getDoc(doc(db('alice'), 'messageRequests/r1')));
    await denied(getDoc(doc(db('carol'), 'messageRequests/r1')));
    await denied(getDoc(doc(db('bob'), 'messageRequests/legacy')));

    const inbox = query(collection(db('bob'), 'messageRequests'),
      where('to', '==', 'bob'), where('schemaVersion', '==', 2));
    const snap = await assertSucceeds(getDocs(inbox));
    assert.deepEqual(snap.docs.map((d) => d.id), ['r1']);
    await denied(getDocs(query(collection(db('bob'), 'messageRequests'), where('to', '==', 'bob'))),
      { missingFilter: ['from', 'schemaVersion'] });
    const foreign = await denied(getDocs(query(collection(db('carol'), 'messageRequests'),
      where('to', '==', 'bob'), where('schemaVersion', '==', 2))), { missingFilter: 'from' });
    // La regola "posta in arrivo" nega con una condizione falsa (to != carol).
    assert.ok(decisivePass(foreign.message).some(isCleanFalse));
    // Posta inviata: alice vede la propria richiesta.
    const sent = await assertSucceeds(getDocs(query(collection(db('alice'), 'messageRequests'),
      where('from', '==', 'alice'), where('schemaVersion', '==', 2))));
    assert.deepEqual(sent.docs.map((d) => d.id), ['r1']);
  });

  test('solo il destinatario accetta o rifiuta', async () => {
    await seed('messageRequests/r1', validRequest('alice', 'bob'));
    const answer = { status: 'accepted', updatedAt: serverTimestamp() };
    await denied(updateDoc(doc(db('alice'), 'messageRequests/r1'), answer));
    await denied(updateDoc(doc(db('bob'), 'messageRequests/r1'), { ...answer, from: 'bob' }));
    await assertSucceeds(updateDoc(doc(db('bob'), 'messageRequests/r1'), answer));
    await denied(updateDoc(doc(db('bob'), 'messageRequests/r1'), { status: 'declined', updatedAt: serverTimestamp() }));
    await denied(deleteDoc(doc(db('bob'), 'messageRequests/r1')));
  });
});

// ─── Query: documenti fuori schema ───────────────────────────────────────────
//
// Le regole delle query sono valutate sui filtri, non sui documenti: non possono
// rivalidare allowlist e tipi. La protezione delle query è quindi:
//  1) filtro schemaVersion == 2 obbligatorio, che esclude i documenti legacy dei client;
//  2) nessuna via client per creare o alterare un documento v2 non conforme (verificato qui);
//  3) per i documenti scritti fuori dalle regole (Admin SDK, console, import) serve una
//     verifica lato server: i test "LIMITE" documentano ciò che le regole non possono fare.

const PRIVATE_EXTRAS = {
  email: { email: 'privato@example.com' },
  telefono: { phone: '+39 333 123 4567' },
  indirizzo: { address: 'Via Roma 1, Milano' },
  annidato: { meta: { contact: { email: 'privato@example.com', phone: '3331234567' } } },
};

const mapsInAllowedFields = {
  member: [
    { permissions: { ...PERMS.member, contact: { email: 'privato@example.com' } } },
    { addedBy: { uid: 'owner', email: 'owner@example.com' } },
    { role: { name: 'member', phone: '3331234567' } },
  ],
  message: [
    { replyTo: { id: 'm1', email: 'privato@example.com' } },
    { status: { value: 'sent', phone: '3331234567' } },
    { iv: { value: 'aXY=', address: 'Via Roma 1' } },
  ],
  request: [
    { to: { uid: 'bob', email: 'bob@example.com' } },
    { status: { value: 'pending', address: 'Via Roma 1' } },
  ],
};

describe('query members: scritture client non conformi negate', () => {
  test('un admin non può aggiungere una membership con dati privati o mappe annidate', async () => {
    await seedGroup();
    for (const extra of Object.values(PRIVATE_EXTRAS)) {
      await denied(setDoc(doc(db('admin'), 'chats/g1/members/eve'),
        { ...invite('eve', 'member', PERMS.member, 'admin'), ...extra }));
    }
    for (const extra of mapsInAllowedFields.member) {
      await denied(setDoc(doc(db('admin'), 'chats/g1/members/eve'),
        { ...invite('eve', 'member', PERMS.member, 'admin'), ...extra }));
    }
    // Controllo positivo: la stessa aggiunta senza campi extra riesce.
    await assertSucceeds(setDoc(doc(db('admin'), 'chats/g1/members/eve'), invite('eve', 'member', PERMS.member, 'admin')));
  });

  test('nessuno può alterare una membership esistente aggiungendo dati privati', async () => {
    await seedGroup();
    for (const actor of ['owner', 'admin', 'bob']) {
      for (const extra of Object.values(PRIVATE_EXTRAS)) {
        await denied(updateDoc(doc(db(actor), 'chats/g1/members/bob'), extra));
      }
    }
    await denied(setDoc(doc(db('owner'), 'chats/g1/members/bob'),
      { ...membership('bob', 'admin', PERMS.adminFull, 'owner'), email: 'bob@example.com' }, { merge: true }));
  });

  test('alla creazione della chat nessuna membership con dati privati', async () => {
    const me = db('alice');
    const batch = writeBatch(me);
    batch.set(doc(me, 'chats/c1'), { ...validChat('alice'), createdAt: serverTimestamp(), updatedAt: serverTimestamp() });
    batch.set(doc(me, 'chats/c1/members/alice'), invite('alice', 'owner', PERMS.owner, 'alice'));
    batch.set(doc(me, 'chats/c1/members/bob'), { ...invite('bob', 'member', PERMS.member, 'alice'), phone: '3331234567' });
    await denied(batch.commit());
  });
});

describe('query members: documenti legacy e fuori schema', () => {
  test('membership senza schemaVersion 2 escluse da query di chat e collection group', async () => {
    await seedGroup();
    const legacy = {
      lx1: { uid: 'lx1', role: 'member', email: 'lx1@example.com' },
      lx2: { ...membership('lx2', 'member', PERMS.member, 'owner'), schemaVersion: 1, phone: '3331234567' },
      lx3: { ...membership('lx3', 'member', PERMS.member, 'owner'), schemaVersion: '2', address: 'Via Roma 1' },
    };
    for (const [id, data] of Object.entries(legacy)) {
      await seed(`chats/g1/members/${id}`, data);
    }
    // Membership legacy di bob in un'altra chat, con dati privati e senza schemaVersion 2.
    await seed('chats/old/members/bob', { uid: 'bob', role: 'member', joinedAt: past(), email: 'bob@example.com' });

    const roster = await assertSucceeds(getDocs(query(collection(db('bob'), 'chats/g1/members'), where('schemaVersion', '==', 2))));
    const ids = roster.docs.map((d) => d.id).sort();
    assert.deepEqual(ids, ['admin', 'bob', 'carol', 'owner', 'weakadmin']);
    for (const d of roster.docs) assert.deepEqual(Object.keys(d.data()).sort(), ['addedBy', 'joinedAt', 'permissions', 'role', 'schemaVersion', 'uid']);

    const mine = await assertSucceeds(getDocs(query(collectionGroup(db('bob'), 'members'),
      where('uid', '==', 'bob'), where('schemaVersion', '==', 2))));
    assert.deepEqual(mine.docs.map((d) => d.ref.path), ['chats/g1/members/bob']);
  });

  test('LIMITE: membership v2 con dati privati scritta fuori dalle regole compare nelle query', async () => {
    await seedGroup();
    // Scrittura con regole disattivate, equivalente a Admin SDK, console o import.
    await seed('chats/g1/members/zed', { ...membership('zed', 'member', PERMS.member, 'owner'), ...PRIVATE_EXTRAS.annidato });
    await seed('chats/other/members/bob', { ...membership('bob', 'member', PERMS.member, 'x'), ...PRIVATE_EXTRAS.email });

    // La lettura singola resta protetta.
    await denied(getDoc(doc(db('bob'), 'chats/g1/members/zed')));
    // Le query non possono rivalidare il documento: il campo extra viene restituito.
    const roster = await getDocs(query(collection(db('bob'), 'chats/g1/members'), where('schemaVersion', '==', 2)));
    assert.ok(roster.docs.some((d) => d.id === 'zed' && 'meta' in d.data()));
    const mine = await getDocs(query(collectionGroup(db('bob'), 'members'),
      where('uid', '==', 'bob'), where('schemaVersion', '==', 2)));
    assert.ok(mine.docs.some((d) => d.ref.path === 'chats/other/members/bob' && 'email' in d.data()));
  });
});

describe('query messages: documenti non conformi', () => {
  const send = (senderId, extra = {}) => ({ ...validMessage(senderId), createdAt: serverTimestamp(), ...extra });

  test('nessun client crea o altera un messaggio v2 con dati privati', async () => {
    await seedGroup();
    for (const extra of [...Object.values(PRIVATE_EXTRAS), ...mapsInAllowedFields.message]) {
      await denied(setDoc(doc(db('bob'), 'chats/g1/messages/x'), send('bob', extra)));
    }
    // Destinatario: stato + campo privato; mittente: elimina per tutti + campo privato.
    for (const extra of Object.values(PRIVATE_EXTRAS)) {
      await denied(updateDoc(doc(db('bob'), 'chats/g1/messages/m1'), { status: 'read', ...extra }));
      await denied(updateDoc(doc(db('owner'), 'chats/g1/messages/m1'), { deletedForAll: true, ...extra }));
    }
    await denied(updateDoc(doc(db('bob'), 'chats/g1/messages/m1'), { status: { value: 'read', email: 'x@example.com' } }));
    // Controllo positivo.
    await assertSucceeds(updateDoc(doc(db('bob'), 'chats/g1/messages/m1'), { status: 'read' }));
  });

  test('messaggi legacy senza schemaVersion 2 esclusi dalla query; query ordinata funziona', async () => {
    await seedGroup();
    await seed('chats/g1/messages/m0', validMessage('owner', { createdAt: Timestamp.fromMillis(Date.now() - 7_200_000) }));
    await seed('chats/g1/messages/legacy1', { senderId: 'owner', ciphertext: 'abc', iv: 'x', type: 'text', email: 'o@example.com' });
    await seed('chats/g1/messages/legacy2', { ...validMessage('owner'), schemaVersion: 1, ...PRIVATE_EXTRAS.annidato });
    await seed('chats/g1/messages/legacy3', { ...validMessage('owner'), schemaVersion: '2', ...PRIVATE_EXTRAS.telefono });

    const q = query(collection(db('bob'), 'chats/g1/messages'), where('schemaVersion', '==', 2), orderBy('createdAt'));
    const snap = await assertSucceeds(getDocs(q));
    assert.deepEqual(snap.docs.map((d) => d.id), ['m0', 'm1']);
    const recent = query(collection(db('bob'), 'chats/g1/messages'), where('schemaVersion', '==', 2),
      orderBy('createdAt', 'desc'), limit(1));
    assert.deepEqual((await assertSucceeds(getDocs(recent))).docs.map((d) => d.id), ['m1']);
  });

  test('LIMITE: messaggio v2 con dati privati scritto fuori dalle regole compare nella query', async () => {
    await seedGroup();
    await seed('chats/g1/messages/forged', { ...validMessage('owner'), ...PRIVATE_EXTRAS.indirizzo });
    await denied(getDoc(doc(db('bob'), 'chats/g1/messages/forged')));
    const snap = await getDocs(query(collection(db('bob'), 'chats/g1/messages'), where('schemaVersion', '==', 2)));
    assert.ok(snap.docs.some((d) => d.id === 'forged' && 'address' in d.data()));
  });
});

describe('query messageRequests: documenti non conformi', () => {
  test('nessun client crea o altera una richiesta v2 con dati privati', async () => {
    await seed('messageRequests/r1', validRequest('alice', 'bob'));
    for (const extra of [...Object.values(PRIVATE_EXTRAS), ...mapsInAllowedFields.request]) {
      await denied(setDoc(doc(db('alice'), 'messageRequests/x'),
        { ...validRequest('alice', 'bob'), createdAt: serverTimestamp(), ...extra }));
    }
    for (const extra of Object.values(PRIVATE_EXTRAS)) {
      await denied(updateDoc(doc(db('bob'), 'messageRequests/r1'), { status: 'accepted', updatedAt: serverTimestamp(), ...extra }));
      await denied(updateDoc(doc(db('alice'), 'messageRequests/r1'), extra));
    }
    await assertSucceeds(updateDoc(doc(db('bob'), 'messageRequests/r1'), { status: 'accepted', updatedAt: serverTimestamp() }));
  });

  test('richieste legacy senza schemaVersion 2 escluse da posta in arrivo e inviata', async () => {
    await seed('messageRequests/r1', validRequest('alice', 'bob'));
    await seed('messageRequests/r2', validRequest('carol', 'bob', { status: 'declined', updatedAt: past() }));
    await seed('messageRequests/l1', { from: 'alice', to: 'bob', status: 'pending', createdAt: past(), ...PRIVATE_EXTRAS.email });
    await seed('messageRequests/l2', { ...validRequest('alice', 'bob'), schemaVersion: 1, ...PRIVATE_EXTRAS.annidato });
    await seed('messageRequests/l3', { ...validRequest('alice', 'bob'), schemaVersion: '2', ...PRIVATE_EXTRAS.indirizzo });

    const inbox = await assertSucceeds(getDocs(query(collection(db('bob'), 'messageRequests'),
      where('to', '==', 'bob'), where('schemaVersion', '==', 2))));
    assert.deepEqual(inbox.docs.map((d) => d.id).sort(), ['r1', 'r2']);
    const pending = await assertSucceeds(getDocs(query(collection(db('bob'), 'messageRequests'),
      where('to', '==', 'bob'), where('schemaVersion', '==', 2), where('status', '==', 'pending'))));
    assert.deepEqual(pending.docs.map((d) => d.id), ['r1']);
    const sent = await assertSucceeds(getDocs(query(collection(db('alice'), 'messageRequests'),
      where('from', '==', 'alice'), where('schemaVersion', '==', 2))));
    assert.deepEqual(sent.docs.map((d) => d.id), ['r1']);
  });

  test('LIMITE: richiesta v2 con dati privati scritta fuori dalle regole compare nella query', async () => {
    await seed('messageRequests/forged', { ...validRequest('alice', 'bob'), ...PRIVATE_EXTRAS.telefono });
    await denied(getDoc(doc(db('bob'), 'messageRequests/forged')));
    const inbox = await getDocs(query(collection(db('bob'), 'messageRequests'),
      where('to', '==', 'bob'), where('schemaVersion', '==', 2)));
    assert.ok(inbox.docs.some((d) => d.id === 'forged' && 'phone' in d.data()));
  });
});

// ─── Default ─────────────────────────────────────────────────────────────────

describe('collezioni non previste', () => {
  test('lettura e scrittura negate', async () => {
    await seed('secrets/x', { a: 1 });
    await denied(getDoc(doc(db('alice'), 'secrets/x')));
    await denied(setDoc(doc(db('alice'), 'secrets/y'), { a: 1 }));
  });
});

// ─── Autoverifica del controllo sui dinieghi ─────────────────────────────────

describe('controllo dei dinieghi', () => {
  const L = CATCH_ALL_LINE;
  const clean = (pass) => pass.length > 0 && pass.every(isCleanFalse);

  test('passaggio decisivo con sola condizione falsa: diniego pulito', () => {
    const msg = `7 PERMISSION_DENIED: \nevaluation error at L10:5 for 'get' @ L10, false for 'get' @ L${L}, `
      + `false for 'get' @ L10, false for 'get' @ L${L}`;
    assert.deepEqual(decisivePass(msg), ["false for 'get' @ L10", `false for 'get' @ L${L}`]);
    assert.ok(clean(decisivePass(msg)));
    assert.ok(clean(decisivePass(`7 PERMISSION_DENIED: \nfalse for 'delete' @ L5, false for 'delete' @ L${L}`)));
  });

  test('errore nel passaggio decisivo: non accettato come diniego', () => {
    assert.ok(!clean(decisivePass(`PERMISSION_DENIED: \nNull value error. for 'create' @ L20, false for 'create' @ L${L}`)));
    assert.ok(!clean(decisivePass(`PERMISSION_DENIED: \nevaluation error at L20:3 for 'create' @ L20, false for 'create' @ L${L}`)));
    assert.ok(!clean(decisivePass(`PERMISSION_DENIED: \nProperty uid is undefined on object. for 'list' @ L30, false for 'list' @ L${L}`)));
  });
});
