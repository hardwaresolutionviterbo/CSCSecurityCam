# CSCSecurityCam

App Android nativa per trasformare uno smartphone in una videocamera di sicurezza e usare un secondo dispositivo come centro di controllo.

## Versione 0.2 – Streaming LAN sperimentale

Questa versione implementa il primo collegamento video reale tra due telefoni sulla stessa rete Wi-Fi:
- Il telefono videocamera acquisisce immagini con CameraX.
- Un server HTTP locale trasmette fotogrammi JPEG in formato MJPEG.
- Il telefono monitor scansiona un QR temporaneo e apre il flusso live.
- Il flusso è protetto da un token casuale temporaneo.
- Il monitor può anche ricevere il codice di collegamento incollato manualmente.

**Limiti noti di questa fase:** il telefono videocamera deve restare nell'app in primo piano; il collegamento funziona solo sulla stessa rete locale; l'audio non è ancora trasmesso; MJPEG è un trasporto iniziale di prova e non sostituisce WebRTC per una soluzione remota e a bassa latenza. Non usare questa versione su reti pubbliche o non fidate.

## Prova con due telefoni

1. Installa lo stesso APK su entrambi i telefoni.
2. Collega entrambi alla stessa rete Wi-Fi (non alla rete ospiti).
3. Sul primo telefono apri **Camera** e premi **Avvia videocamera**.
4. Con il secondo telefono apri **Monitor**, premi **Scansiona QR code** e inquadra il codice mostrato sul primo.
5. Attendi il caricamento del flusso; per interrompere, premi **Arresta videocamera** sul primo o **Disconnetti** sul secondo.

Se il router isola i dispositivi Wi-Fi tra loro, il collegamento LAN non sarà possibile: disattiva l'isolamento client/AP isolation nelle impostazioni del router.

## Stack
- Kotlin
- Jetpack Compose + Material 3
- CameraX
- NanoHTTPD (server LAN)
- ZXing (QR code)
- GitHub Actions per build APK

## Prossime fasi
1. Stabilizzare e testare il flusso su dispositivi reali, rotazione, rete e riconnessione.
2. Aggiungere audio e controlli sessione.
3. Sostituire il trasporto MJPEG con WebRTC e un server di signaling proprietario per collegamenti remoti.
4. Implementare pairing autenticato, cifratura, notifiche e servizio foreground.
5. Integrare progressivamente ONVIF/RTSP per telecamere IP compatibili.

## Sicurezza
Non inserire password, chiavi API, certificati privati o credenziali nel repository. Il token QR è temporaneo ma il flusso LAN in questa versione non è cifrato: usare solo una rete Wi-Fi fidata. Per build firmate usare GitHub Actions Secrets.
