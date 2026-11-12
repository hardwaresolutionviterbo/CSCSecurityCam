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
7. Pubblica le regole Firestore: `firebase deploy --only firestore:rules`
   (oppure incolla `firestore.rules` in Console → Firestore → Regole).

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
del profilo**; nessun altro utente può leggere il FID. Durante la sessione, al cambio account o
alla disconnessione il collegamento precedente viene eliminato prima di associarne uno nuovo.

L'app non ha ancora la schermata di accesso (Fase 2): fino al primo login la diagnostica mostra
«in attesa di accesso» e non viene scritto nulla. Dopo aver aggiunto le regole aggiornate,
pubblica con:

```bash
firebase deploy --only firestore:rules
```

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

- Le chat contengono **solo testo cifrato** (`ciphertext` + `iv`), scritto dal dispositivo.
  Le regole rifiutano campi in chiaro e payload oltre 64 KB.
- Solo i membri delle chat leggono e scrivono i messaggi; le chat non si cancellano.
- I destinatari possono aggiornare solo stato di consegna/lettura.
- `/profiles/{uid}` è una copia minima e pubblica (nome abbreviato, @username, avatar).
- `/profiles/{uid}/devices/{fid}` è leggibile e modificabile solo dal proprietario del profilo.
- Tutto ciò che non è elencato è negato.

## Decisioni aperte (Fase 4)

- **Media delle chat** (foto, video, file): Firebase Storage non può verificare l'appartenenza
  a una chat senza custom claim o Cloud Functions. Valutare Supabase Storage con RLS sulle
  membership, oppure URL firmati emessi da una Cloud Function.
- **Chiamate**: WebRTC con signaling su Firestore; TURN (coturn) per NAT simmetrici.
- **Chiavi E2E**: generate sul dispositivo, mai su Firebase; backup delle chiavi a carico
  dell'utente (frase di recupero) — da progettare prima di scrivere la chat.
