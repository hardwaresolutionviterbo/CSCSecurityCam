# Nexus Social

App **Flutter** per Android e iPhone: mappa sociale 3D con annunci e richieste vicino a te,
chat e chiamate cifrate end-to-end, sezioni **Scambia** e **Vendita**, profili **Privato** e
**Azienda**, promozioni locali e gestionale di amministrazione dentro l'app.

Sviluppo a fasi: alla fine di ogni fase c'è qualcosa da installare e provare sul telefono.

## Stato del lavoro

| Fase | Contenuto | Stato |
| --- | --- | --- |
| 0 | Account, chiavi, grafica, costi, regole | **Completata** (14/11/2026): [`docs/FASE_0_CHECKLIST.md`](docs/FASE_0_CHECKLIST.md) |
| 1 | Fondamenta tecniche: app installabile, servizi collegati, tema, navigazione | **Fase 1 completata** (14/11/2026): [`docs/fase1/CHECKLIST.md`](docs/fase1/CHECKLIST.md). Punti aperti nella sezione 4 della checklist |
| 2 | Account e profili (SMS, profilo, 2FA, blocchi) | Da iniziare |
| 3 | Mappa Sociale 3D, annunci, raggio fino a 30 km | Da iniziare |
| 4 | Chat cifrata, gruppi, chiamate, notifiche | Da iniziare |
| 5 | Scambia, Vendita, feedback | Da iniziare |
| 6 | Aziende, promozioni, abbonamento 9,99 €/mese | Da iniziare |
| 7 | Gestionale Admin dentro l'app | Da iniziare |
| 8 | Traduzione, gamification, ricerca avanzata | Da iniziare |
| 9 | Test, sicurezza, GDPR, beta chiusa | Da iniziare |
| 10 | Pubblicazione e lancio | Da iniziare |

Il dettaglio delle fasi è nel piano di lavoro ufficiale
(`Nexus_Social_Piano_di_Lavoro_COMPLETO_AGGIORNATO.pdf`, versione del 13 novembre 2026).

Documenti da leggere insieme al piano:
- modifiche approvate al piano: [`docs/fase1/MODIFICHE_AL_PIANO.md`](docs/fase1/MODIFICHE_AL_PIANO.md);
- corrispondenza tra le cartelle del codice e quelle del piano:
  [`docs/fase1/STRUTTURA_CARTELLE.md`](docs/fase1/STRUTTURA_CARTELLE.md).

## Cosa contiene la Fase 1 (completata)

**Fase 1 completata** il 14 novembre 2026: esito e prove in
[`docs/fase1/CHECKLIST.md`](docs/fase1/CHECKLIST.md), piano in
[`docs/fase1/PIANO_OPERATIVO.md`](docs/fase1/PIANO_OPERATIVO.md).

Restano fuori, come decisioni del titolare elencate nei punti aperti della checklist:
- l'applicazione dello schema Supabase `0002` al database reale;
- il collegamento di Codemagic;
- la questione del tipo di account Azienda (regola M6).

- Progetto Flutter unico per Android e iPhone (`android/`, `ios/`).
- Barra di navigazione in basso con **Mappa, Chat, Scambia, Vendita, Profilo** e campanella
  delle notifiche in alto.
  - Dal Blocco 2 (ramo `fase1/blocco2`) l'ordine è quello approvato, **Chat, Mappa, Scambia,
    Vendita, Profilo**, con Scambia come pulsante centrale arancione e apertura su Chat (A1,
    vedi `docs/fase1/MODIFICHE_AL_PIANO.md`).
- Tema grafico Material 3 con **modalità scura** (e chiara), colori e tipografia del brand.
- Schermate svuotate con l'anteprima di cosa arriverà in ogni fase.
- Collegamento pronto a **Supabase** (annunci, ricerca per distanza, foto, segnalazioni) e
  **Firebase** (login SMS, chat, chiamate, notifiche), attivo solo quando le chiavi ci sono.
- Schema Supabase con **RLS** e posizione approssimata; regole **Firestore** per chat cifrate.
  - Nello schema Supabase attuale il nome completo è leggibile da tutti gli utenti autenticati.
  - Va corretto prima di usare dati reali (passaggio 1.7 del piano).
- APK Android da **GitHub Actions** (decisione F11). `codemagic.yaml` è presente ma non
  collegato: servirà solo per la build iPhone.

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
codemagic.yaml               build nel cloud
docs/                        checklist Fase 0 e architettura
```

## Installare l'app sul telefono (senza PC)

**GitHub Actions (Android).** `.github/workflows/android.yml` si esegue a ogni push su `main` o
su un ramo di lavoro `fase1/...`, oppure a mano. Esegue analisi, test e build.

1. Apri **Actions → Android APK** e scegli l'esecuzione.
2. In **Artifacts** scarica il file.
3. Aprilo sul telefono, consentendo l'installazione da origini sconosciute.

Ogni APK è identificabile (regola R1):
- **nome del file:** `nexus-social-<versione>+<build>-<commit>-<ramo>.apk`;
- **riepilogo dell'esecuzione:** la stessa tabella;
- **nell'app:** Profilo → Informazioni → Versione.

Il numero di build è `numero esecuzione × 100 + tentativo`, quindi anche una riesecuzione produce
un APK distinto.

**Codemagic (solo iPhone, decisione F11).**
- Il collegamento del repository richiede l'autorizzazione del titolare.
- Firebase su iOS: il file `GoogleService-Info.plist` non sta nel repository (regola F10). La
  build lo crea dal segreto `GOOGLE_SERVICE_INFO_PLIST` del gruppo `nexus_social`: istruzioni
  in [`docs/fase1/FIREBASE_IOS.md`](docs/fase1/FIREBASE_IOS.md).
- La build iOS senza firma si avvia manualmente.
- Per installare su iPhone serve un account Apple (Fasi 9-10).

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

## Verifiche della base esistente

Verifiche fatte quando la base è stata creata, prima del piano della Fase 1. Le verifiche
aggiornate di ogni build sono nel riepilogo dell'esecuzione su GitHub Actions.

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

Comportamento dello schema attuale (`0001`). Con la decisione **P9** la zona approssimata resta
predefinita, ma per ogni annuncio l'autore potrà scegliere una posizione precisa (punto d'incontro,
locale, indirizzo). Lo schema `0002` aggiunge questa scelta (piano della Fase 1, passaggio 1.7).

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
