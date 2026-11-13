# Architettura · Nexus Social

## Un solo codice, due backend

| Ambito | Servizio | Perché |
| --- | --- | --- |
| Login SMS, chat, chiamate, notifiche | **Firebase** | Authentication con SMS, Firestore in tempo reale, Cloud Messaging, WebRTC per le chiamate |
| Annunci, ricerca per distanza, foto, segnalazioni, gestionale | **Supabase** (PostgreSQL + PostGIS + Storage) | Query geografiche vere, RLS granulare, SQL per statistiche e moderazione |

L'app parla con i due servizi tramite SDK ufficiali (`firebase_core`, `firebase_auth`,
`cloud_firestore`, `firebase_messaging`, `supabase_flutter`). Ogni servizio si attiva solo se
la configurazione è presente: senza chiavi l'app resta utilizzabile e mostra lo stato nel
Profilo.

## Modello dati Supabase (Fase 1)

- `profiles` — un profilo per utente (`auth.users.id`), tipo Privato/Azienda, `@username`
  univoco, nome pubblico abbreviato generato dal database (`Carlo C.`), bio, social, media
  stelle.
- `listings` — annunci: tipo `social`, `swap_offer`, `swap_want`, `sale`; stato; foto;
  posizione **approssimata**; scadenza (massimo 30 giorni); disponibilità ora.
- `listing_locations` — posizione **esatta**, leggibile solo dal proprietario e dagli admin.
- `reports` — segnalazioni con coda per la moderazione.
- `user_moderation` / `admin_users` — sospensioni e ruoli interni.

Le prossime migrazioni aggiungono aziende e abbonamenti (`0002`), promozioni (`0003`),
feedback (`0004`) ed eventi (`0005`).

## Privacy della posizione

1. Il client invia la posizione una sola volta alla funzione `create_listing(...)`.
2. Il server conserva l'esatto in `listing_locations` e calcola `approx_location` con
   `ST_SnapToGrid` su una griglia di 0,005° (~550 m).
3. `nearby_listings(lat, lon, raggio)` filtra sulla posizione esatta con `ST_DWithin`, ma
   restituisce solo la coordinata approssimata e una distanza arrotondata a 250 m.
4. RLS impedisce al client di leggere `listing_locations` di altre persone e di scrivere
   direttamente annunci o coordinate.

Risultato: chi è a Milano non vede Torino, il raggio massimo è 30 km, ma nessuno può ricavare
l'indirizzo di una persona dalle coordinate o dalla distanza.

## Sicurezza

- RLS attiva su tutte le tabelle Supabase; senza policy esplicita l'accesso è negato.
- Annunci creati solo tramite funzione `security definer`; `listing_locations` senza policy di
  scrittura.
- Trigger che impediscono al client di modificare proprietario, tipo, coordinate, data di
  creazione, media stelle e tipo di account.
- Foto in `listing-photos/<user-id>/…`: ognuno scrive solo nella propria cartella.
- Firestore: allowlist di campi e tipi anche in lettura (i documenti legacy con dati privati
  non sono leggibili), query solo su documenti `schemaVersion == 2`, messaggi solo cifrati,
  membership con ruoli in `chats/{chatId}/members`, default negato. Test con l'emulatore in
  `firebase/tests/`.
- Le chiavi `anon` di Supabase sono pubbliche per definizione: la sicurezza sta nelle policy.

## Chat cifrata end-to-end (Fase 4)

- Testo e media vengono cifrati **sul dispositivo**; Firestore conserva solo `ciphertext` e
  `iv`. La copia dei profili su Firestore è minima (nome abbreviato, `@username`, avatar).
- Il protocollo deve essere collaudato, non inventato: in Fase 4 si valuta una libreria del
  protocollo Signal (X3DH + Double Ratchet) o MLS per i gruppi, con verifica delle chiavi di
  sicurezza e frase di recupero per il backup.
- Le chiamate usano WebRTC; il signaling passa da Firestore e serve un TURN (coturn) per i NAT
  simmetrici e le reti mobili.
- Punti aperti: storage dei media delle chat (Storage non conosce le membership) e notifiche
  con anteprima decifrata solo sul dispositivo.

## Mappa 3D (Fase 3)

MapLibre GL con tile vettoriali gratuite (OpenFreeMap): edifici 3D, inclinazione, avatar
animati come marker. Aggiornamento della posizione ogni poche centinaia di metri, non continuo,
per non consumare batteria. Se in futuro servisse un provider commerciale, l'integrazione resta
isolata nel modulo `features/map`.

## Build e distribuzione

- Codemagic compila APK a ogni push su `main` e la build iOS su richiesta; i minuti macOS sono
  limitati sul piano gratuito, quindi iOS resta manuale.
- Firma Android e iOS solo tramite variabili d'ambiente/segreti della CI; nessun keystore nel
  repository.
- In Fase 9 si aggiungono TestFlight e test interno su Google Play.

## Scelte rimandate

- Servizio per la verifica delle partite IVA con denominazione e indirizzo.
- Limiti definitivi di peso per i media delle chat.
- Città pilota e regole di moderazione dettagliate.
- Nome definitivo dopo la verifica su Play Store e App Store.
