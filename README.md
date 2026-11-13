# Nexus Social

App **Flutter** per Android e iPhone: mappa sociale 3D con annunci e richieste vicino a te,
chat e chiamate cifrate end-to-end, sezioni **Scambia** e **Vendita**, profili **Privato** e
**Azienda**, promozioni locali e gestionale di amministrazione dentro l'app.

Sviluppo a fasi: alla fine di ogni fase c'è qualcosa da installare e provare sul telefono.

## Stato del lavoro

| Fase | Contenuto | Stato |
| --- | --- | --- |
| 0 | Account, chiavi, grafica, costi, regole | Documentata in [`docs/FASE_0_CHECKLIST.md`](docs/FASE_0_CHECKLIST.md) |
| 1 | Fondamenta tecniche: app installabile, servizi collegati, tema, navigazione | **Completata** in questo repository |
| 2 | Account e profili (SMS, profilo, 2FA, blocchi) | Da iniziare |
| 3 | Mappa Sociale 3D, annunci, raggio fino a 30 km | Da iniziare |
| 4 | Chat cifrata, gruppi, chiamate, notifiche | Da iniziare |
| 5 | Scambia, Vendita, feedback | Da iniziare |
| 6 | Aziende, promozioni, abbonamento 9,99 €/mese | Da iniziare |
| 7 | Gestionale Admin dentro l'app | Da iniziare |
| 8 | Traduzione, gamification, ricerca avanzata | Da iniziare |
| 9 | Test, sicurezza, GDPR, beta chiusa | Da iniziare |
| 10 | Pubblicazione e lancio | Da iniziare |

Il dettaglio delle fasi è nel piano di lavoro originale (`Nexus_Social_Piano_di_Lavoro.pdf`).

## Cosa c'è nella Fase 1

- Progetto Flutter unico per Android e iPhone (`android/`, `ios/`).
- Barra di navigazione in basso con **Mappa, Chat, Scambia, Vendita, Profilo** e campanella
  delle notifiche in alto.
- Tema grafico Material 3 con **modalità scura** (e chiara), colori e tipografia del brand.
- Schermate svuotate con l'anteprima di cosa arriverà in ogni fase.
- Collegamento pronto a **Supabase** (annunci, ricerca per distanza, foto, segnalazioni) e
  **Firebase** (login SMS, chat, chiamate, notifiche), attivo solo quando le chiavi ci sono.
- Schema Supabase con **RLS** e posizione approssimata; regole **Firestore** per chat cifrate.
- Build cloud **Codemagic**: APK di prova a ogni push su `main`, build iOS senza firma.

## Struttura del repository

```
lib/
  main.dart                  avvio e inizializzazione dei servizi
  app.dart                   MaterialApp, tema, localizzazione
  core/
    config/                  configurazione (dart-define) e costanti app
    services/                bootstrap Firebase/Supabase
    theme/                   colori, tipografia, tema chiaro/scuro
    widgets/                 componenti condivisi (logo, campanella, anteprime)
  features/
    shell/                   barra di navigazione e impalcatura dell'app
    map/                     Mappa Sociale (Fase 3)
    chat/                    chat e chiamate (Fase 4)
    listings/                Scambia (Fase 5)
    sale/                    Vendita (Fase 5)
    profile/                 account e profilo (Fase 2)
    admin/                   gestionale (Fase 7)
supabase/migrations/         schema e regole di sicurezza Supabase
firebase/firestore.rules     regole di sicurezza Firestore
firebase/tests/              test delle regole con Firestore Emulator (CI: Firestore Rules)
firebase/audit/              audit in sola lettura dei dati Firestore prima del deploy delle regole
codemagic.yaml               build nel cloud
docs/                        checklist Fase 0 e architettura
```

## Installare l'app sul telefono (senza PC)

**GitHub Actions (già configurato).** Ogni push su `main` esegue `.github/workflows/android.yml`:
analisi, test e build. L'APK si scarica da **Actions → Android APK → ultima esecuzione →
Artifacts → `nexus-social-apk-debug`**, poi si apre sul telefono consentendo l'installazione da
origini sconosciute.

**Codemagic (alternativa).** Collega il repository a [Codemagic](https://codemagic.io) e seleziona
`codemagic.yaml`; la build iOS senza firma si avvia manualmente. Per installare su iPhone serve
un account Apple (Fase 9/10).

## Sviluppo locale

Requisiti: Flutter 3.50 o successivo, JDK 17, Xcode per iOS.

```bash
flutter pub get
flutter analyze
flutter test
flutter run --dart-define-from-file=env.json
flutter build apk --debug   # APK di prova installabile
```

`env.json` non è versionato: copia `env.example.json` e compila i valori. Senza chiavi l'app
si avvia normalmente e segnala nel Profilo quali servizi non sono configurati.

## Verifiche eseguite (Fase 1)

- `flutter analyze`: nessun problema.
- `flutter test`: 7 test superati (navigazione, campanella, profilo, configurazione, tema).
- Schema Supabase: 58 istruzioni con sintassi valida secondo il parser PostgreSQL; non è
  ancora stato eseguito su un progetto Supabase reale.
- `flutter build apk --debug`: APK generato e firmato con la chiave di debug, package
  `com.nexussocial.app`, minSdk 24.
- Workflow GitHub Actions: configurato per analisi, test e APK a ogni push su `main`.
- Build Codemagic e build iOS: configurate ma non eseguite qui (servono gli account).

## Collegare i servizi

- **Supabase** (annunci, distanza, foto, segnalazioni, admin): crea il progetto, esegui
  `supabase/migrations/0001_init.sql` nel SQL Editor, poi aggiungi `SUPABASE_URL` e
  `SUPABASE_ANON_KEY` nei **Settings → Secrets and variables → Actions** di GitHub: il workflow
  verifica la connessione e li passa alla build. L'integrazione GitHub del pannello Supabase
  serve solo alle migrazioni. Dettagli in [`supabase/README.md`](supabase/README.md).
- **Firebase** (SMS, chat, chiamate, notifiche): crea il progetto, aggiungi le app Android/iOS,
  scarica i file di configurazione e genera `lib/firebase_options.dart`, poi imposta
  `FIREBASE_ENABLED=true`. Dettagli in [`firebase/README.md`](firebase/README.md).
- Su Codemagic le stesse variabili vanno nelle **Environment variables** del progetto, mai nel
  repository.

## Privacy della posizione

La posizione esatta non è mai visibile agli altri utenti: resta in una tabella leggibile solo
dal proprietario e dagli amministratori, mentre la mappa usa una posizione approssimata a una
griglia di circa 550 metri. La ricerca per distanza filtra sulla posizione reale ma restituisce
distanze arrotondate a fasce di 250 metri, così non si può triangolare una persona. Il raggio è
regolabile fino a 30 km e parte da 10 km.

## Scelte tecniche

Le motivazioni sono in [`docs/ARCHITETTURA.md`](docs/ARCHITETTURA.md): un solo codice Flutter,
Firebase per identità/chat/chiamate, Supabase per dati geografici e gestionale, MapLibre per la
mappa 3D (nessuna chiave a pagamento).

## Progetto precedente

Il vecchio prototipo **CSCSecurityCam** (app Android in Kotlin per la videosorveglianza) non fa
più parte del ramo di lavoro: resta nella cronologia git (`main`, commit `f24339b`) e può essere
recuperato da lì.
