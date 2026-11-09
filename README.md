# CSCSecurityCam

App Android nativa per trasformare uno smartphone in una videocamera di sicurezza e usare un secondo dispositivo come centro di controllo.

## Stato
**Fase 0.1 – Fondamenta del progetto.** Questa prima versione prepara struttura Android, interfaccia Compose, permessi e pipeline CI per generare un APK debug. Le funzioni di streaming remoto, pairing, registrazione persistente e compatibilità ONVIF/RTSP saranno implementate e testate progressivamente: non sono ancora dichiarate operative.

## Stack
- Kotlin
- Jetpack Compose + Material 3
- CameraX per acquisizione locale
- Android Gradle Plugin
- GitHub Actions per build APK

## Build locale
Aprire il progetto con Android Studio (JDK 17) e avviare la configurazione Gradle. In alternativa, dopo aver aggiunto il Gradle Wrapper, usare `./gradlew assembleDebug`.

## APK automatico
Ogni push su `main` avvia la workflow **Android Debug APK**. L'APK viene pubblicato come artifact scaricabile nella pagina Actions della repository.

## Roadmap
1. Camera locale affidabile con preview e selezione fotocamera.
2. Modalità monitor con pairing sicuro tra dispositivi.
3. Streaming LAN e poi remoto con WebRTC.
4. Registrazione, rilevamento movimento e notifiche.
5. Integrazione progressiva di ONVIF/RTSP per telecamere IP compatibili.
6. Test su dispositivi reali, inclusi modelli meno recenti.

## Sicurezza
Non inserire password, chiavi API, certificati privati o credenziali nel repository. Per le build firmate usare GitHub Actions Secrets.
