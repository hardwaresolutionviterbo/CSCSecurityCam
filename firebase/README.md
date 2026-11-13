# Firebase · Nexus Social

Firebase serve per **login con SMS, chat, chiamate e notifiche**. Annunci, foto, ricerca per
distanza e gestionale restano su Supabase.

## Perché i file di configurazione non sono nel repository

`google-services.json` (Android) e `GoogleService-Info.plist` (iOS) contengono gli
identificativi del progetto Firebase. Non sono segreti nel senso di password, ma tengono il
repository legato a un solo progetto: vanno aggiunti localmente e su Codemagic, e sono già
esclusi da `.gitignore`.

## Collegamento (Fase 2, quando arriva il login SMS)

1. Crea il progetto su [console.firebase.google.com](https://console.firebase.google.com).
2. Aggiungi due app: Android con package `com.nexussocial.app`, iOS con bundle id
   `com.nexussocial.app`.
3. Attiva **Authentication → Phone** (per l'SMS), **Cloud Firestore** e **Cloud Messaging**.
4. Scarica `google-services.json` in `android/app/` e `GoogleService-Info.plist` in `ios/Runner/`.
5. Genera `lib/firebase_options.dart` con FlutterFire CLI:

   ```bash
   dart pub global activate flutterfire_cli
   flutterfire configure --project=<id-progetto>
   ```

6. Imposta `FIREBASE_ENABLED=true` in `env.json` (o nelle variabili di Codemagic) e passa
   `--dart-define-from-file=env.json` alla build.
7. Pubblica le regole Firestore **solo dopo** l'audit in sola lettura descritto in
   [Pubblicazione delle regole](#pubblicazione-delle-regole-deploy).

Senza questi passaggi l'app funziona lo stesso: mostra «Firebase non configurato» nella
sezione Profilo e non tenta nessuna connessione.

## Collegamento nella build GitHub Actions (Android)

Il secret `GOOGLE_SERVICES_JSON` contiene il file scaricato da Firebase. Il workflow lo
ricostruisce in `android/app/google-services.json` prima della build, senza stamparne il
contenuto, e passa `FIREBASE_ENABLED=true`; il file resta escluso dal repository.

Se il secret manca, la build continua senza Firebase e l'app mostra «Firebase non
configurato». Il job **verifica runtime Firebase** (solo avvio manuale del workflow) installa
l'APK su un emulatore, apre l'app e controlla nei log che Firebase Core, Auth e Firestore
siano inizializzati. Per iOS serviranno `GoogleService-Info.plist` e `firebase_options.dart`.

## Notifiche push (FCM)

- Il canale Android è `nexus_social_default` (importanza alta) ed è impostato come canale
  predefinito nel manifest, così funzionano anche le notifiche ricevute in background.
- Su Android 13+ l'app chiede il permesso `POST_NOTIFICATIONS` a runtime tramite
  `FirebaseMessaging.requestPermission()`.
- La registrazione usa la Firebase Installation ID
  (`firebase_messaging_installation_id_enabled=true`). Il FID non viene scritto nei log.
- In foreground FCM non mostra la notifica da solo: l'app la pubblica sul canale con
  `flutter_local_notifications`. In background la mostra Android usando il canale predefinito.
- La schermata **Profilo → Diagnostica push (debug)** mostra FID, permesso, stato della
  registrazione, associazione all'account e canale. È compilata solo in debug e serve per il
  test manuale.

### Associazione del dispositivo all'account

Quando un utente accede con Firebase Auth, `PushDeviceRegistry` salva il FID in
`profiles/{uid}/devices/{fid}` con piattaforma, versione dell'app e timestamp. Le regole
Firestore consentono lettura, creazione, aggiornamento ed eliminazione **solo al proprietario
del profilo**; nessun altro utente può leggere il FID. Prima di un logout o di un cambio
account il flusso di autenticazione deve chiamare `PushDeviceRegistry.unlinkCurrentDevice()`
(o `signOut()`): la cancellazione riesce solo finché l'utente proprietario è ancora
autenticato. Un cambio account diretto non può cancellare il documento del vecchio utente.

L'app non ha ancora la schermata di accesso (Fase 2): fino al primo login la diagnostica mostra
«in attesa di accesso» e non viene scritto nulla. Le regole aggiornate si pubblicano seguendo
[Pubblicazione delle regole](#pubblicazione-delle-regole-deploy).

### Test manuale con Firebase Console

1. Installa l'APK debug e apri **Profilo → Diagnostica push (debug)**.
2. Copia il **Firebase Installation ID** mostrato (non condividerlo pubblicamente).
3. In Firebase Console → **Messaging** → **Invia messaggio di prova**, incolla il FID come
   destinatario e invia una notifica con titolo e testo.
4. Con l'app in foreground deve comparire la notifica locale; con l'app in background deve
   comparire la notifica di sistema. Il contatore «Messaggi in foreground» conferma la ricezione.

Il job emulatore verifica registrazione FCM, permesso e canale, ma non può inviare dalla
Firebase Console: la ricezione di una notifica reale va confermata con il test manuale.

## Cosa fanno le regole Firestore

Firestore restituisce sempre il documento intero, quindi le regole controllano **sia la
scrittura sia la lettura**. Il dettaglio dei campi ammessi è in
[`tests/README.md`](tests/README.md#modello-dati-verificato).

- **Letture singole (get) rivalidate**: profili, chat, membership, messaggi e richieste sono
  leggibili solo se hanno esattamente i campi previsti, con i tipi giusti. Un documento legacy
  con campi extra (`email`, `address`…) o strutture annidate **non è leggibile**.
- **Profili: copia minima scritta solo dal server**. La fonte ufficiale dei profili è
  Supabase (`public.profiles`); l'autenticazione è Firebase Auth. `profiles/{uid}` in
  Firestore contiene solo `username`, `publicName`, `avatarUrl` e `updatedAt`, usati da chat,
  chiamate e notifiche. **Nessun client può creare, modificare o cancellare un profilo**, neanche
  il proprio: la copia la scriverà la sincronizzazione lato server Supabase → Firestore (Fase 2,
  non ancora implementata). Finché manca, i profili Firestore restano vuoti.
- **Profili: lettura rivalidata con i vincoli di Supabase**: `username` come
  `profiles.username` (3-24 caratteri tra lettere, cifre, punto e trattino basso, maiuscole
  comprese perché il campo è `citext`); `publicName` come il `public_name` generato, fino a 80
  caratteri, `@` compresa. Resta escluso un `publicName` che somiglia a un numero di telefono.
  Le regole di prodotto (es. vietare username di sole cifre o simili a un telefono) vanno in
  Supabase nella Fase 2.
- **Query (list)**: le regole valutano i filtri, non i documenti restituiti, quindi le query
  su membri, messaggi e richieste devono filtrare `schemaVersion == 2`, valore che un client può
  scrivere solo con un documento completamente valido. I profili e le chat non sono
  enumerabili. Prima del deploy serve un audit in sola lettura dei documenti scritti fuori dalle
  regole: vedi [`tests/README.md`](tests/README.md#sicurezza-delle-query-list).
- **Chat**: solo testo cifrato in base64 (`ciphertext` + `iv`), massimo 64 KB. Solo i membri
  leggono e scrivono; i destinatari aggiornano lo stato, il mittente «elimina per tutti».
- **Membership** in `chats/{chatId}/members/{uid}`: ruoli `owner`, `admin`, `member` e permessi
  `addMembers`/`removeMembers`. Chi viene rimosso perde subito l'accesso e non può reinvitarsi;
  un membro normale non può reinvitare; un admin con `addMembers` sì, e il reinvito crea una
  nuova membership con `joinedAt` e `addedBy` aggiornati. L'owner non si rimuove. Una
  membership malformata (per esempio scritta con Admin SDK) non concede accesso.
- **Massimo 256 membri**: la chat tiene `memberCount` e `lastMemberChange`, che cambiano di uno
  nello stesso batch di ogni aggiunta o rimozione di una membership. La chat nasce con l'owner e
  al più un secondo membro; gli altri si aggiungono un batch alla volta.
- **Richieste di messaggio**: le crea il mittente, le accetta o rifiuta solo il destinatario.
- `/profiles/{uid}/devices/{fid}` è leggibile e modificabile solo dal proprietario (invariato);
  funziona anche senza documento profilo.
- Tutto ciò che non è elencato è negato.

### Pubblicazione delle regole (deploy)

Le regole Firestore valgono **solo per le richieste dei client**. Admin SDK, Firebase Console,
import ed eventuali Cloud Functions le ignorano completamente: i documenti scritti in questi modi
non sono vincolati dalle regole e, se hanno `schemaVersion: 2`, compaiono nelle query.

Il deploy è quindi subordinato a questa sequenza, da eseguire ogni volta:

1. **Audit in sola lettura** dei dati in produzione su `profiles`, `chats` (con `members` e
   `messages`) e `messageRequests`: nessuna scrittura, nessuna correzione automatica, report
   senza valori sensibili.
2. **Esito richiesto**: nessun documento con `schemaVersion: 2` che non rispetta lo schema in
   [`tests/README.md`](tests/README.md#modello-dati-verificato). Se l'audit ne trova, il deploy
   si ferma: la bonifica è un intervento lato server separato, da approvare.
3. **Test verdi** del workflow **Firestore Rules** sul commit da pubblicare.
4. Solo allora: `firebase deploy --only firestore:rules`.

Lo script di audit non è ancora nel repository (escluso dalla Fase A.1): finché manca, le regole
non vanno pubblicate su un progetto che contiene dati.

### Test automatici

`firebase/tests/` contiene i test permanenti con il Firestore Emulator, eseguiti da GitHub
Actions (workflow **Firestore Rules**) a ogni modifica di `firebase/`. Istruzioni in
[`tests/README.md`](tests/README.md).

## Decisioni aperte (Fase 4)

- **Media delle chat** (foto, video, file): Firebase Storage non può verificare l'appartenenza
  a una chat senza custom claim o Cloud Functions. Valutare Supabase Storage con RLS sulle
  membership, oppure URL firmati emessi da una Cloud Function.
- **Chiamate**: WebRTC con signaling su Firestore; TURN (coturn) per NAT simmetrici.
- **Chiavi E2E**: generate sul dispositivo, mai su Firebase; backup delle chiavi a carico
  dell'utente (frase di recupero) — da progettare prima di scrivere la chat.
