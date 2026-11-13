# Test delle regole Firestore · Nexus Social

Test permanenti di `firebase/firestore.rules` con il **Firestore Emulator** (progetto locale
`demo-nexus-social`: nessun progetto reale, nessun deploy, nessun dato di produzione).

## Esecuzione

Su GitHub Actions il workflow **Firestore Rules** li esegue a ogni push o pull request che
modifica `firebase/`, `firebase.json` o il workflow stesso.

In locale servono **Node ≥ 24.12** e **Java 21**:

```bash
cd firebase/tests
npm ci          # versioni esatte dal package-lock.json
npm run check   # controllo sintattico
npm test        # avvia l'emulatore ed esegue la suite
```

Con `RULES_COVERAGE_FILE=/percorso/copertura.json npm test` viene salvato anche il report di
copertura delle regole (JSON e HTML), caricato come artifact dalla CI.

## Come sono scritti i test

- **Ogni caso negativo ha un controllo positivo vicino** con dati validi: un diniego non può
  passare solo perché una regola nega tutto.
- **I dinieghi devono essere puliti.** Il dettaglio dell'emulatore elenca l'esito di ogni regola
  in ogni passaggio di valutazione. Il test accetta un diniego solo se nel passaggio decisivo
  (l'ultimo, chiuso dalla regola finale di diniego) tutti gli esiti sono `false`. Un diniego
  causato da un errore («evaluation error», «Null value error», campo mancante) fa fallire il
  test. Il controllo stesso è verificato da due test dedicati.
- **Query senza filtro obbligatorio**: per le query l'emulatore valuta i filtri, non i
  documenti, e un filtro mancante produce «Property X is undefined». In quei casi il test
  dichiara il campo atteso (`{ missingFilter: 'uid' }`) e verifica che il diniego sia dovuto
  solo a quello.

## Modello dati verificato

| Percorso | Campi (obbligatori · *facoltativi*) | Lettura |
| --- | --- | --- |
| `profiles/{uid}` | `username` (a-z 0-9 . _, 3-30, max 6 cifre consecutive), `publicName` (≤ 60, senza email/telefono), `updatedAt` timestamp · *`avatarUrl`* (https, senza spazi né @) | `get` da utenti autenticati, rivalidato; nessuna `list` |
| `profiles/{uid}/devices/{fid}` | invariato | solo il proprietario |
| `chats/{chatId}` | `type` direct/group, `createdBy`, `createdAt`, `updatedAt`, `schemaVersion` 2 · *`title`* (≤ 80, senza email/telefono), *`avatarUrl`*, *`lastMessageAt`* | `get` dai membri, rivalidato; nessuna `list` |
| `chats/{chatId}/members/{uid}` | `uid` = id documento, `role` owner/admin/member, `permissions` {`addMembers`, `removeMembers`} booleani, `joinedAt`, `addedBy`, `schemaVersion` 2 | `get` dai membri, rivalidato; `list` dai membri con `where('schemaVersion', '==', 2)`; collection group con `where('uid', '==', mio uid)` e `schemaVersion` 2 |
| `chats/{chatId}/messages/{id}` | `senderId`, `ciphertext` e `iv` in base64, `type`, `createdAt`, `status` sent/delivered/read, `deletedForAll`, `schemaVersion` 2 · *`replyTo`* | `get` dai membri, rivalidato; `list` solo con `where('schemaVersion', '==', 2)` |
| `messageRequests/{id}` | `from`, `to`, `status` pending/accepted/declined, `createdAt`, `schemaVersion` 2 · *`updatedAt`* | `get` da mittente/destinatario, rivalidato; `list` con `to` o `from` == mio uid e `schemaVersion` 2 |

Regole funzionali coperte dai test: rimozione con revoca immediata dell'accesso, niente
auto-reinvito, niente reinvito da un membro normale o da un admin senza `addMembers`, reinvito
da admin con `addMembers` (nuova membership con `joinedAt` e `addedBy` aggiornati), owner non
rimovibile e con ruolo immutabile, niente falsificazione di ruoli e permessi.

## Sicurezza delle query (list)

Firestore valuta le regole di una query **sui filtri della query, non sui documenti
restituiti**, e il client riceve sempre il documento intero (non esiste proiezione dei campi
nell'SDK client). Una condizione come «ha solo questi campi» non si può dimostrare da un filtro:
inserita in una regola `list`, renderebbe la query sempre negata. Per questo le letture singole
(`get`) rivalidano tutto, mentre le query si basano su tre garanzie, tutte coperte dai test:

1. **Filtro obbligatorio `schemaVersion == 2`** su membri (chat e collection group), messaggi e
   richieste. Esclude i documenti scritti dai client con le regole precedenti: le membership non
   erano scrivibili, i messaggi avevano un'allowlist senza `schemaVersion`, quindi nessun
   documento legacy client può avere `schemaVersion: 2`.
2. **Nessuna via client per creare o alterare un documento v2 non conforme**: ogni create e update
   rivalida l'intero documento risultante (dati privati, mappe annidate nei campi ammessi, campi
   extra aggiunti con update o merge: tutti negati).
3. **Query legittime verificate**: elenco membri, proprie membership in tutte le chat, messaggi
   ordinati per data con `limit`, posta in arrivo, inviata e filtrata per stato.

I test `LIMITE` dimostrano ciò che le regole **non** possono impedire: un documento v2 con dati
privati scritto **fuori dalle regole** (Admin SDK, console, import, migrazioni) compare nelle
query, anche se la lettura singola resta negata.

### Soluzione architetturale per i documenti scritti fuori dalle regole

- **Precondizione al deploy delle regole**: audit in sola lettura di `chats/*/members`,
  `chats/*/messages` e `messageRequests` in produzione, con esito «nessun documento v2 non
  conforme». Le funzioni di chat non sono ancora nell'app, quindi ci si attende zero documenti.
  Se l'audit ne trova, vanno bonificati con un intervento lato server approvato, prima del deploy.
- **Richieste di messaggio legacy**: con le regole precedenti mittente e destinatario potevano
  modificare liberamente la richiesta, anche aggiungendo `schemaVersion: 2`. È l'unica via
  client legacy per un documento v2 non conforme, ed è coperta dallo stesso audit.
- **Scrittori privilegiati** (Cloud Functions, script, migrazioni future): non scrivono in queste
  collezioni, oppure lo fanno solo tramite un validatore condiviso con lo stesso schema delle
  regole, con test dedicati.
- **Se servisse una garanzia più forte** dopo il lancio: query servite da un backend (Cloud
  Function con Admin SDK e proiezione dei campi), con `list` client disattivato. Costa realtime,
  latenza e infrastruttura; per ora non è necessario.

## Limiti noti

- **Documenti scritti fuori dalle regole**: vedi sopra.
- **Indice**: la query collection group su `members` (`uid` + `schemaVersion`) richiede in
  produzione un indice composito per collection group (da creare prima della Fase 4);
  l'emulatore non lo richiede.
- **Chat dirette**: le regole impediscono aggiunte dopo la creazione, ma non limitano a due il
  numero di membri inseriti nel batch di creazione.
- **Emulatore**: il controllo dei dinieghi si basa sul formato del messaggio dell'emulatore
  (`false for '<operazione>' @ L<riga>`). Se una versione futura cambia formato, i test negativi
  falliscono invece di passare: il controllo è conservativo.
