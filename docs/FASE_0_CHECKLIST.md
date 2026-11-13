# Fase 0 · Preparazione

Nessun codice: solo account, chiavi, grafica, costi e regole.

Riferimento: `Nexus_Social_Piano_di_Lavoro_COMPLETO_AGGIORNATO.pdf`, versione del 13 novembre
2026. Stato aggiornato al 13 novembre 2026.

**Prova richiesta dal piano:** account creati e funzionanti, chiavi salvate, grafica di base
scelta e impostazioni privacy progettate.

## Stato dei requisiti del piano

Legenda:

- ✅ completato e verificabile;
- 🟡 parziale;
- ⏳ da fare;
- 🔷 serve una tua decisione o conferma.

| Requisito del piano | Stato | Evidenza / cosa manca |
|---|---|---|
| Nome definitivo «Nexus Social» e controllo sugli store | 🔷 | Verifica fatta: [`fase0/VERIFICA_NOME_STORE.md`](fase0/VERIFICA_NOME_STORE.md). L'identificativo Android `com.nexussocial.app` è **già usato su Google Play** da un'altra app; il nome ha omonimi. Decisione sul nome e sul nuovo identificativo: tua. |
| Account GitHub | ✅ | Repository attivo |
| Account Firebase | ✅ | La build Android in CI contiene la configurazione Firebase |
| Account Supabase | ✅ | La CI raggiunge Supabase e trova lo schema (HTTP 200) |
| Account Codemagic | 🔷 | `codemagic.yaml` presente, ma nessuna build Codemagic risulta eseguita. Le build attuali usano GitHub Actions. Conferma se l'account esiste. |
| Servizio mappa 3D (account e chiave gratuita) | 🔷 | Analisi: [`fase0/DECISIONE_MAPPA.md`](fase0/DECISIONE_MAPPA.md). Proposta: MapLibre + OpenFreeMap, che non richiedono account né chiave. Il rilievo 3D del terreno non è disponibile ufficialmente: va deciso cosa si intende per «3D». |
| Chiavi e password in un posto sicuro | 🔷 | Non verificabile dal repository: serve la tua conferma (gestore di password). Nessuna chiave nel repository. |
| Logo, icona, colori, stile (moderno, avatar animati, modalità scura) | 🟡 | Monogramma «N» su gradiente viola-menta, icona dell'app, tema chiaro e scuro presenti. Lo stile degli **avatar animati** non è ancora definito; l'icona definitiva per gli store è prevista in Fase 9/10. |
| Piano costi | 🟡 | Voci di costo sotto. Mancano le soglie gratuite attuali di Firebase, Supabase e SMS, da verificare prima della Fase 2 (SMS). |
| Bozze di Termini d'uso, Privacy Policy, Regole della community | ✅ come **bozze non validate** | [`fase0/BOZZA_TERMINI_USO.md`](fase0/BOZZA_TERMINI_USO.md), [`fase0/BOZZA_PRIVACY_POLICY.md`](fase0/BOZZA_PRIVACY_POLICY.md), [`fase0/BOZZA_REGOLE_COMMUNITY.md`](fase0/BOZZA_REGOLE_COMMUNITY.md). Revisione professionale prevista in Fase 9. |
| Impostazioni Privacy, Controllo privacy, separazione dati pubblici e privati | ✅ progetto, 🔷 15 scelte da approvare | [`fase0/PRIVACY_IMPOSTAZIONI.md`](fase0/PRIVACY_IMPOSTAZIONI.md) |

## Decisioni aperte

Il piano elenca sei «decisioni ancora da prendere». Questa tabella ne riporta lo stato.

| Decisione | Stato | Quando serve |
|---|---|---|
| Servizio mappa 3D e limiti gratuiti | Proposta pronta (MapLibre + OpenFreeMap, con condizioni) | Prima della Fase 3 |
| Verifica partita IVA (VIES o servizio completo) | Aperta. Proposta: VIES più confronto manuale dell'admin | Fasi 6 e 7 |
| Limiti di peso dei media in chat | Aperta. Proposta: foto 10 MB compresse, video 50 MB / 60 s, documenti 25 MB | Fase 4 |
| Città pilota | Aperta | Fase 10 |
| Regole di moderazione (motivi, durata dei ban, testi) | Aperta. Proposta: avviso → 24 ore → 7 giorni → permanente; motivi: spam, truffa, molestie, contenuti illegali, profilo falso | Prima della Fase 5 (commenti) e della Fase 7 |
| Nome definitivo dopo il controllo sugli store | **Urgente**: l'identificativo Android va cambiato prima di qualsiasi pubblicazione | Prima possibile |

Decisioni nuove emerse in Fase 0:

- età minima, 14 o 18 anni (proposta: 18);
- le 15 scelte P1-P15 delle impostazioni Privacy;
- significato di «3D» per la mappa.

## Account

| Servizio | A cosa serve | Costo iniziale |
| --- | --- | --- |
| GitHub | Codice del progetto | Gratuito |
| Codemagic | Compila l'app nel cloud, senza PC | Piano gratuito con minuti limitati |
| Firebase | Login SMS, chat, chiamate, notifiche | Piano Spark gratuito, poi a consumo |
| Supabase | Annunci, ricerca per distanza, foto, segnalazioni, admin | Piano gratuito, poi a consumo |
| MapLibre + OpenFreeMap (proposta) | Mappa con inclinazione ed edifici 3D | Gratuito, nessuna chiave; nessuna garanzia di servizio |
| Google Play Console | Pubblicazione Android | Una tantum |
| Apple Developer | Pubblicazione iPhone | Annuale |

## Chiavi e segreti

Mai nel repository. In locale stanno in `env.json`, che è escluso da git. In CI stanno nei
**Secrets** di GitHub Actions e nelle **Environment variables** di Codemagic.

| Nome | Dove si trova | Quando serve |
| --- | --- | --- |
| `SUPABASE_URL` | Supabase → Project Settings → API | Già in uso |
| `SUPABASE_ANON_KEY` | Supabase → Project Settings → API (chiave `anon public`) | Già in uso |
| `FIREBASE_ENABLED` | `true` dopo aver collegato Firebase | Già in uso nella CI |
| `google-services.json` | Firebase → app Android (segreto `GOOGLE_SERVICES_JSON`) | Già in uso; da rigenerare se cambia l'identificativo Android |
| `GoogleService-Info.plist` | Firebase → app iOS | Fase 2 (iPhone) |
| Chiavi dei pagamenti | Google Play / App Store | Fase 6 |

## Costi da tenere d'occhio

- **SMS di verifica:** si pagano oltre la quota gratuita. Servono un limite giornaliero per numero
  e una protezione anti-abuso prima dell'invio. Le soglie attuali sono da verificare prima della
  Fase 2.
- **Firebase e Supabase:** foto e video sono la voce che cresce di più. Compressione obbligatoria e
  limiti di peso.
- **Mappa:** OpenFreeMap non ha limiti dichiarati ma neppure garanzie. Se serve un fornitore a
  pagamento, i costi vanno verificati in quel momento.
- **Abbonamenti in-app:** Google e Apple trattengono una commissione, in genere tra il 15% e il 30%.
  Il piano chiede di verificare le condizioni aggiornate.
- **Account sviluppatore:** Google Play una tantum, Apple annuale.
- **Verifica partita IVA:** VIES è gratuito; i servizi che restituiscono anche denominazione e
  indirizzo sono a pagamento.

## Attività residue per chiudere la Fase 0

1. 🔷 Approvare o modificare le scelte P1-P15 di
   [`fase0/PRIVACY_IMPOSTAZIONI.md`](fase0/PRIVACY_IMPOSTAZIONI.md).
2. 🔷 Decidere il nome e il nuovo identificativo dell'app. La modifica tecnica dell'identificativo è
   un intervento di Fase 1, da autorizzare a parte.
3. 🔷 Approvare la proposta per la mappa e il significato di «3D».
4. 🔷 Decidere l'età minima (14 o 18 anni).
5. 🔷 Confermare che le chiavi e le password sono salvate in un gestore sicuro.
6. 🔷 Confermare l'account Codemagic, oppure decidere di usare solo GitHub Actions per le build
   Android.
7. ⏳ Definire lo stile degli avatar animati (bozza grafica).
8. ⏳ Verificare le soglie gratuite attuali di Firebase (SMS), Supabase e servizio mappa.
9. ⏳ Compilare i dati del titolare nelle bozze legali. La revisione del professionista è prevista
   in Fase 9.
