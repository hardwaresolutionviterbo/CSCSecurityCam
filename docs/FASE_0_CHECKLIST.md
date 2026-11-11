# Fase 0 · Preparazione

Nessun codice: solo account, chiavi, grafica, costi e regole. Questa è la lista operativa.

## Account da creare

| Servizio | A cosa serve | Costo iniziale |
| --- | --- | --- |
| GitHub | Codice del progetto | Gratuito |
| Codemagic | Compila l'app nel cloud, senza PC | Piano gratuito con minuti limitati |
| Firebase | Login SMS, chat, chiamate, notifiche | Piano Spark gratuito, poi a consumo |
| Supabase | Annunci, ricerca per distanza, foto, segnalazioni, admin | Piano gratuito, poi a consumo |
| MapLibre + OpenFreeMap | Mappa 3D con edifici e inclinazione | Gratuito, nessuna chiave |
| Google Play Console | Pubblicazione Android | Una tantum (~25 $) |
| Apple Developer | Pubblicazione iPhone | Annuale (~99 $) |

## Chiavi e segreti

Mai nel repository. Si impostano in `env.json` in locale (già ignorato da git) e nelle
**Environment variables** di Codemagic:

| Nome | Dove si trova | Quando serve |
| --- | --- | --- |
| `SUPABASE_URL` | Supabase → Project Settings → API | Fase 1 (pronta) |
| `SUPABASE_ANON_KEY` | Supabase → Project Settings → API (chiave `anon public`) | Fase 1 (pronta) |
| `FIREBASE_ENABLED` | `true` dopo aver collegato Firebase | Fase 2 |
| `google-services.json` | Firebase → app Android | Fase 2 |
| `GoogleService-Info.plist` | Firebase → app iOS | Fase 2 |
| `GOOGLE_WEB_CLIENT_ID` | Google Cloud Console (solo se serve anche l'accesso Google) | Fase 2 |
| Chiavi dei pagamenti | Google Play / App Store | Fase 6 |

## Grafica

- Nome: **Nexus Social** (da verificare su Play Store, App Store e dominio prima del lancio).
- Stile: moderno, scuro come modalità principale, accenti viola/menta, angoli arrotondati,
  avatar animati sulla mappa.
- Logo: monogramma **N** su gradiente viola→menta (già usato nell'app); l'icona definitiva per
  gli store si prepara in Fase 9/10.

## Costi da tenere d'occhio

- **SMS di verifica**: si pagano oltre la quota gratuita di Firebase. Prevedere un limite
  giornaliero per numero e un captcha prima dell'invio.
- **Firebase/Supabase**: foto e video sono la voce che cresce di più; compressione obbligatoria
  e limiti di peso.
- **Mappa**: MapLibre e le tile gratuite hanno limiti d'uso; se il traffico cresce serve un
  provider di tile a pagamento.
- **Abbonamenti**: Google e Apple trattengono tra il 15% e il 30%.
- **Verifica partita IVA**: VIES è gratuito; i servizi che restituiscono anche denominazione e
  indirizzo sono a pagamento.

## Decisioni consigliate

- **Mappa 3D**: MapLibre GL con tile vettoriali gratuite, inclinazione e edifici 3D. Non richiede
  chiavi né carta di credito, a differenza di Google Maps.
- **Verifica partita IVA**: controllo gratuito su VIES; se non restituisce i dati dell'azienda,
  l'admin li confronta a mano (Fase 7).
- **Limiti media chat** (da confermare in Fase 4): foto 10 MB compresse, video 50 MB / 60 s,
  documenti 25 MB.
- **Moderazione**: avviso → ban 24 ore → ban 7 giorni → ban permanente. Motivi: spam, truffa,
  molestie, contenuti illegali, profilo falso.
- **Città pilota**: da scegliere in Fase 10, meglio una sola città per avere annunci vicini fin
  dall'inizio.

## Documenti da preparare (servono agli store)

- Privacy Policy: posizione approssimata, rubricа con consenso, età minima, cancellazione
  account, dati conservati e per quanto.
- Termini d'uso: regole della community, contenuti vietati, sospensioni, responsabilità sugli
  scambi (l'app non gestisce pagamenti).
- Regole della community in linguaggio semplice, linkate dentro l'app.

Farsi revisionare i testi da un professionista prima della pubblicazione (Fase 9).
