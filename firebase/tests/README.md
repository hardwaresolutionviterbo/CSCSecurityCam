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
| `chats/{chatId}/members/{uid}` | `uid` = id documento, `role` owner/admin/member, `permissions` {`addMembers`, `removeMembers`} booleani, `joinedAt`, `addedBy`, `schemaVersion` 2 | `get` dai membri, rivalidato; `list` dai membri; collection group solo con `where('uid', '==', mio uid)` |
| `chats/{chatId}/messages/{id}` | `senderId`, `ciphertext` e `iv` in base64, `type`, `createdAt`, `status` sent/delivered/read, `deletedForAll`, `schemaVersion` 2 · *`replyTo`* | `get` dai membri, rivalidato; `list` solo con `where('schemaVersion', '==', 2)` |
| `messageRequests/{id}` | `from`, `to`, `status` pending/accepted/declined, `createdAt`, `schemaVersion` 2 · *`updatedAt`* | `get` da mittente/destinatario, rivalidato; `list` con `to` o `from` == mio uid e `schemaVersion` 2 |

Regole funzionali coperte dai test: rimozione con revoca immediata dell'accesso, niente
auto-reinvito, niente reinvito da un membro normale o da un admin senza `addMembers`, reinvito
da admin con `addMembers` (nuova membership con `joinedAt` e `addedBy` aggiornati), owner non
rimovibile e con ruolo immutabile, niente falsificazione di ruoli e permessi.

## Limiti noti

- **Query su documenti legacy**: le regole non possono rivalidare il contenuto dei documenti
  restituiti da una query. Il filtro `schemaVersion == 2` esclude i documenti scritti con le
  regole precedenti, ma un documento scritto da strumenti amministrativi (Admin SDK, console)
  con `schemaVersion: 2` e campi privati comparirebbe nelle query. Le letture singole restano
  protette. L'audit dei dati legacy è rimandato a una fase successiva.
- **Richieste di messaggio legacy**: le regole precedenti permettevano a mittente e destinatario
  di aggiornare liberamente la richiesta; un partecipante potrebbe quindi aver aggiunto
  `schemaVersion: 2` a una propria richiesta legacy.
- **Indice**: la query collection group su `members.uid` richiede in produzione l'indice a campo
  singolo per collection group (da abilitare prima della Fase 4); l'emulatore non lo richiede.
- **Chat dirette**: le regole impediscono aggiunte dopo la creazione, ma non limitano a due il
  numero di membri inseriti nel batch di creazione.
- **Emulatore**: il controllo dei dinieghi si basa sul formato del messaggio dell'emulatore
  (`false for '<operazione>' @ L<riga>`). Se una versione futura cambia formato, i test negativi
  falliscono invece di passare: il controllo è conservativo.
