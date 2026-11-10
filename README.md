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
