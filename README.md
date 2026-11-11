# CSCSecurityCam

App Android per usare uno smartphone come videocamera di sicurezza, con funzioni di monitor,
controllo PTZ, rilevamento movimento e accesso remoto.

## Requisiti di build

- JDK 21
- Android SDK con `platforms;android-36.1` e `build-tools;36.1.0` (compileSdk 36.1, targetSdk 36,
  minSdk 24)
- Gradle tramite wrapper incluso (`./gradlew`)

Configura il percorso dell'SDK in `local.properties` (file non versionato):

```
sdk.dir=/percorso/Android/Sdk
```

## Comandi

```bash
./gradlew assembleDebug   # APK in app/build/outputs/apk/debug/app-debug.apk
./gradlew test            # test unitari JVM + Robolectric
./gradlew assembleRelease # APK non firmato se le credenziali non sono impostate
```

## Firma

- **Debug**: usa il keystore di debug generato automaticamente da Android Gradle Plugin
  (`~/.android/debug.keystore`). Nessun keystore è versionato.
- **Release**: la firma viene applicata solo se sono presenti tutte le variabili d'ambiente
  `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD` (e opzionalmente `KEY_ALIAS`, default `upload`).
  Il keystore deve restare fuori dal repository; senza queste variabili la build release produce un
  APK non firmato.

Non inserire chiavi, keystore o credenziali nel repository: in CI usare i secret del repository.

## Versioni archiviate

Il vecchio prototipo 0.2 (streaming LAN MJPEG) è conservato nel branch `archivio/0.2` e non fa parte
del ramo principale.

## Controllo remoto fuori casa (WebRTC)

Il flusso LAN resta invariato. Per guardare la telecamera da un'altra rete servono un relay di
signaling raggiungibile da internet e, per NAT simmetrici e reti mobili, un TURN.

1. Avvia il relay: `cd signaling && npm install && npm start` (porta 8787). Mettilo dietro TLS
   (Caddy/Nginx) e usa `wss://...` nell'app: `ws://` è accettato solo per localhost.
2. Su entrambi i telefoni: Impostazioni → Accesso remoto → inserisci l'URL `wss://` e il codice
   stanza (quello mostrato sulla telecamera come "Codice Associazione P2P") → Salva.
3. Sulla telecamera attiva "Accesso remoto": pubblica appena lo streaming è attivo.
4. Sul centro di controllo: Impostazioni → Accesso remoto → "Guarda" apre il video remoto.
5. Per NAT simmetrici configura un TURN (es. coturn) con URL, utente e password.

I fotogrammi non passano dal relay: viaggiano cifrati DTLS/SRTP tra i due telefoni. Il data channel
WebRTC trasporta anche i comandi di torcia, registrazione e qualità.

## Griglia multi-telecamera

Il Centro di controllo ha un selettore 1 / 2 / 4 riquadri. Ogni riquadro ha il proprio flusso
MJPEG (massimo quattro), ridotto a 640 px di lato massimo per non tenere in RAM frame 1080p; i
bitmap sostituiti vengono riciclati poco dopo. Doppio tocco su un riquadro = schermo intero con
audio e comandi legati a quella telecamera; un riquadro offline mostra "Riconnetti" senza
interrompere gli altri.

In griglia il client aggiunge `?fps=12` a `/live.mjpg`: il server limita i frame inviati a quel
client (12 fps) per non saturare il Wi-Fi con quattro flussi. Il flusso singolo resta a piena
frequenza.

## Account, area personale e piano Premium

- **Registrazione e accesso reali**: nome, email e password vengono salvati in Room. La password
  non è mai in chiaro: `PasswordHasher` usa PBKDF2-HMAC-SHA256 con salt casuale per utente
  (120 000 iterazioni) e confronto a tempo costante. Login con password errata, email sconosciuta
  o email già registrata restituiscono un errore esplicito.
- **Sessione**: "ricordami" salva l'utente attivo; alla riapertura l'app ripristina l'account e le
  sue telecamere senza richiedere di nuovo la password. "Esci" cancella solo la sessione.
- **Google**: pulsante su schermo di login tramite Credential Manager. Serve il client ID OAuth web
  del progetto: `./gradlew assembleDebug -PGOOGLE_WEB_CLIENT_ID=...` (o variabile d'ambiente
  omonima). Senza client ID il pulsante resta visibile ma spiega che la build non è configurata.
- **Telecamere per account**: ogni telecamera salvata ha un `ownerId`; accedendo con un altro
  account non si vedono le telecamere altrui, e chi esce e rientra ritrova le proprie.
- **Area personale** (Impostazioni → riga account): foto profilo scelta dalla galleria e copiata
  nello spazio privato dell'app, nome modificabile, piano e giorni residui dell'abbonamento.
- **Premium**: il piano Free salva 1 telecamera; dalla seconda compare il paywall a
  **3,99 €/mese**. Piano e scadenza sono persistiti per utente (`plan`, `planExpiresAtMillis`) e
  l'area personale mostra ad esempio "Mancano 25 giorni". L'addebito vero richiede il prodotto in
  abbonamento su Google Play Console e l'integrazione Play Billing: il pulsante apre oggi la
  scheda Play Store dell'app.
