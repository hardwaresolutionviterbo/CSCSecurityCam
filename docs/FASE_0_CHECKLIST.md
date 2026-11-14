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
| Nome definitivo «Nexus Social» e controllo sugli store | ✅ deciso (F8) | Verifica: [`fase0/VERIFICA_NOME_STORE.md`](fase0/VERIFICA_NOME_STORE.md). Nome invariato. L'identificativo `com.nexussocial.app`, già usato su Google Play da un'altra app, va cambiato **prima del primo caricamento su Play, entro la Fase 9**. |
| Account GitHub | ✅ | Repository attivo |
| Account Firebase | ✅ | La build Android in CI contiene la configurazione Firebase |
| Account Supabase | ✅ | La CI raggiunge Supabase e trova lo schema (HTTP 200) |
| Account Codemagic | 🟡 deciso (F11), in corso | Android resta su GitHub Actions; Codemagic per le build iPhone. **Il titolare sta creando l'account gratuito: serve la sua conferma.** |
| Servizio mappa 3D (account e chiave gratuita) | ✅ deciso (F9) | [`fase0/DECISIONE_MAPPA.md`](fase0/DECISIONE_MAPPA.md): MapLibre + OpenFreeMap, senza account né chiave. «3D» = inclinazione ed edifici 3D. Prototipo di conferma all'inizio della Fase 3. |
| Chiavi e password in un posto sicuro | ✅ confermato (F10) | Confermato dal titolare. Nessuna chiave nel repository. |
| Logo, icona, colori, stile (moderno, avatar animati, modalità scura) | ✅ concept; A1 e A5 decise, A2-A4 e A6 entro la Fase 3 | Logo, icona e tema chiaro/scuro presenti. Concept di avatar e marker dal mockup del titolare: [`fase0/AVATAR_E_MARKER_CONCEPT.md`](fase0/AVATAR_E_MARKER_CONCEPT.md). Decisioni A1-A6 aperte. Icona definitiva per gli store in Fase 9/10. |
| Piano costi | ✅ verificato; L1-L3 decise, L4-L5 più avanti | [`fase0/LIMITI_GRATUITI.md`](fase0/LIMITI_GRATUITI.md). **Il login con SMS richiede il piano Blaze di Firebase** (circa 0,05 $ per SMS in Italia, primi 10 al giorno gratuiti). Decisioni L1-L5 aperte. |
| Bozze di Termini d'uso, Privacy Policy, Regole della community | ✅ come **bozze non validate** | [`fase0/BOZZA_TERMINI_USO.md`](fase0/BOZZA_TERMINI_USO.md), [`fase0/BOZZA_PRIVACY_POLICY.md`](fase0/BOZZA_PRIVACY_POLICY.md), [`fase0/BOZZA_REGOLE_COMMUNITY.md`](fase0/BOZZA_REGOLE_COMMUNITY.md). Revisione professionale prevista in Fase 9. |
| Impostazioni Privacy, Controllo privacy, separazione dati pubblici e privati | ✅ progettate e approvate | [`fase0/PRIVACY_IMPOSTAZIONI.md`](fase0/PRIVACY_IMPOSTAZIONI.md); decisioni P1-P15, D1-D4, M1-M7 in [`fase0/DECISIONI_PRIVACY_APPROVATE.md`](fase0/DECISIONI_PRIVACY_APPROVATE.md) |

## Decisioni aperte

Il piano elenca sei «decisioni ancora da prendere». Questa tabella ne riporta lo stato.

| Decisione | Stato | Quando serve |
|---|---|---|
| Servizio mappa 3D e limiti gratuiti | **Decisa (F9)**: MapLibre + OpenFreeMap, prototipo in Fase 3 | Prima della Fase 3 |
| Verifica partita IVA (VIES o servizio completo) | Aperta. Proposta: VIES più confronto manuale dell'admin | Fasi 6 e 7 |
| Limiti di peso dei media in chat | Aperta. Proposta: foto 10 MB compresse, video 50 MB / 60 s, documenti 25 MB | Fase 4 |
| Città pilota | Aperta | Fase 10 |
| Regole di moderazione (motivi, durata dei ban, testi) | Aperta. Proposta: avviso → 24 ore → 7 giorni → permanente; motivi: spam, truffa, molestie, contenuti illegali, profilo falso | Prima della Fase 5 (commenti) e della Fase 7 |
| Nome definitivo dopo il controllo sugli store | **Decisa (F8)**: «Nexus Social»; identificativo da cambiare prima del primo caricamento su Play | Entro la Fase 9 |

Decisioni prese in Fase 0, tutte in
[`fase0/DECISIONI_PRIVACY_APPROVATE.md`](fase0/DECISIONI_PRIVACY_APPROVATE.md):

- **P1-P15**: impostazioni privacy;
- **D1**: età minima 14 anni, con numero e data di nascita, senza documenti né servizi a
  pagamento;
- **D2**: identità di Nexus Social, social network con marketplace, non app di incontri romantici
  né per adulti;
- **D3**: sicurezza dei minorenni, con avviso sulla Mappa Social;
- **D4**: esperienza semplice, simile a WhatsApp;
- **M1-M7**: dettagli per i minorenni;
- **F8-F11**: nome, mappa, chiavi, build.

Restano aperte le decisioni del piano che servono più avanti: verifica partita IVA, limiti dei
media in chat, città pilota, regole di moderazione.

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

Fatte:

1. ✅ Scelte P1-P15 approvate.
2. ✅ Nome deciso; cambio dell'identificativo pianificato entro la Fase 9 (F8).
3. ✅ Mappa approvata (F9).
4. ✅ Età minima: 14 anni (D1).
5. ✅ Chiavi e password confermate (F10).

Ancora da fare:

6. 🟡 **Account Codemagic**: il titolare lo sta creando (F11). Serve la sua conferma.
7. ✅ Concept di avatar e marker; **A1 e A5 decise**. A2, A3, A4 e A6 da decidere prima della Fase 3.
8. ✅ Limiti gratuiti verificati; **L1, L2 e L3 decise**. L4 e L5 da decidere più avanti.
9. ⏳ Dati del titolare nelle bozze legali; revisione del professionista in Fase 9, che copre anche
   gli obblighi sui minorenni (Digital Services Act, art. 28) e la foto dei minorenni sulla mappa
   (A5).

**Per dichiarare conclusa la Fase 0 manca solo la conferma dell'account Codemagic (punto 6).** Gli
altri punti aperti hanno una scadenza in fasi successive, come previsto dal piano.
