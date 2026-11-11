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

## Cosa fanno le regole Firestore

- Le chat contengono **solo testo cifrato** (`ciphertext` + `iv`), scritto dal dispositivo.
  Le regole rifiutano campi in chiaro e payload oltre 64 KB.
- Solo i membri della chat leggono e scrivono i messaggi; le chat non si cancellano.
- I destinatari possono aggiornare solo stato di consegna/lettura.
- `/profiles/{uid}` è una copia minima e pubblica (nome abbreviato, @username, avatar).
- Tutto ciò che non è elencato è negato.

## Decisioni aperte (Fase 4)

- **Media delle chat** (foto, video, file): Firebase Storage non può verificare l'appartenenza
  a una chat senza custom claim o Cloud Functions. Valutare Supabase Storage con RLS sulle
  membership, oppure URL firmati emessi da una Cloud Function.
- **Chiamate**: WebRTC con signaling su Firestore; TURN (coturn) per NAT simmetrici.
- **Chiavi E2E**: generate sul dispositivo, mai su Firebase; backup delle chiavi a carico
  dell'utente (frase di recupero) — da progettare prima di scrivere la chat.
