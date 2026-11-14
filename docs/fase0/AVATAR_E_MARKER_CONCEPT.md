# Fase 0 · Concept grafico: avatar animati e marker della Mappa Social

Riferimento grafico fornito dal titolare il 13 novembre 2026:
[`riferimenti/mockup_avatar_mappa.png`](riferimenti/mockup_avatar_mappa.png).

- Il mockup è un **riferimento di stile**, non una specifica funzionale. Le funzioni restano quelle
  del PDF e delle decisioni approvate (`DECISIONI_PRIVACY_APPROVATE.md`).
- Nessuna modifica all'app in Fase 0. Gli avatar si realizzano in Fase 2 (profilo) e Fase 3
  (mappa).

## 1. Stile approvato

- **3D, moderno e colorato**: personaggi in stile illustrazione 3D, con luci morbide, contorno
  luminoso e sfondo scuro, coerenti con la modalità scura del PDF.
- Palette del brand: arancione e blu del logo, con accenti viola, verde, azzurro e rosso per le
  categorie della mappa.
- Immagine della persona (decisione **A5**, modificata dal titolare): l'utente sceglie
  liberamente tra:
  - la **propria fotografia**, usata sia nel profilo sia come marker sulla Mappa Social;
  - un **avatar illustrato 3D di Nexus Social** al posto della foto.

  Nessun obbligo di usare i personaggi 3D.
- Gli avatar illustrati sono **personaggi originali di Nexus Social**: nessun personaggio, stile o
  marchio di terzi.
- Animazione leggera (respiro, piccolo rimbalzo, anello luminoso che pulsa), mai invadente.

## 2. Tipi di marker: distinguibili a colpo d'occhio

Regola di base: ogni tipo si distingue per **forma + icona + colore**, mai solo per il colore, così
resta leggibile anche per chi distingue male i colori.

| Tipo | Forma | Icona | Colore (mockup) | Riferimento nel PDF |
|---|---|---|---|---|
| **Persona** | **Rotondo**, con anello colorato e punta | Foto profilo dell'utente oppure avatar 3D (A5) | Anello colorato | Avatar animati sulla mappa (Fase 3) |
| **Bar / Caffè** | Goccia | Tazzina | Arancione | Attività: sede fissa (Fase 6) |
| **Ristorante** | Goccia | Posate | Blu | Attività: sede fissa (Fase 6) |
| **Attività commerciale / azienda** | Goccia | Negozio | Viola | Attività: sede fissa (Fase 6) |
| **Evento / incontro** | Goccia | Gruppo di persone | Verde | Eventi di gruppo, con conteggio dei partecipanti (Fase 3) |
| **Gaming / hobby** | Goccia | Controller | Azzurro | Categoria di annuncio (Fase 3) |
| **Annuncio (vendita, scambio, regalo)** | Goccia | Etichetta | Rosso | Annunci, Scambia e Vendita (Fasi 3 e 5) |

Come applicare questi tipi:

- **Persone ≠ attività.** Le persone hanno l'avatar rotondo; le attività hanno sempre la goccia
  con l'icona della categoria.
- **Il badge «Verificato»** compare solo sulle aziende approvate dall'admin e con abbonamento
  attivo, e sparisce alla scadenza (PDF, P4).
- **Le sedi delle attività** sono segnaposto permanenti all'indirizzo ufficiale (P4).

## 3. Scheda utente (dal mockup) e regole da rispettare

La scheda del mockup (avatar, nome, distanza, bio, pulsanti Chat e Profilo) è coerente con il PDF,
con queste correzioni obbligatorie:

| Nel mockup | Regola da applicare | Fonte |
|---|---|---|
| «Marco» | Nome pubblico **abbreviato**: «Marco R.» | PDF Fase 2 |
| Manca l'@username | Mostrare l'**@username** | PDF Fase 2 |
| «2,3 km da te» | Distanza **arrotondata** a fasce, calcolata dal server sulla zona approssimata; mai la posizione esatta | PDF Fase 3; schema Supabase (fasce di 250 m) |
| Pulsante chat | Per gli sconosciuti apre una **richiesta di messaggio**; il numero non compare mai | PDF Fase 4; P6; M5 |
| Bio e social | Visibili secondo gli interruttori di P3 | P3 |
| Età | **Mai mostrata** | M1 |

Sulla mappa, l'avatar di una persona compare **nella zona approssimata** dei suoi annunci, mai
nella posizione esatta. Il GPS in tempo reale si vede solo con l'autorizzazione dell'utente
(P9-P11) e, per i minorenni, solo per i contatti riconosciuti (M4).

## 4. Punti del mockup da decidere (in conflitto o assenti nel PDF)

Questi elementi **non vengono adottati** finché non li approvi. Il PDF ha la precedenza.

| N. | Elemento del mockup | Cosa dice il PDF | Proposta |
|---|---|---|---|
| A1 | Barra in basso: Home, Chat, Mappa, Annunci, Community | Barra: **Mappa, Chat, Scambia, Vendita, Profilo** (Fase 1, già realizzata) | **Decisa:** le cinque voci del PDF nell'ordine Chat, Mappa, Scambia (centrale, arancione), Vendita, Profilo, con lo stile grafico del mockup; niente Home né Community |
| A2 | Stati **Online / Offline** sulla mappa | Non previsti. Il PDF prevede lo stato **«Disponibile ora»** degli annunci | Non mostrare online/offline: rivela quando una persona usa l'app. Usare «Disponibile ora» |
| A3 | Stati «In attività (es. Gaming)» e «In gruppo (es. Evento)» | Il PDF prevede eventi di gruppo con conteggio dei partecipanti | Mostrarli solo come proprietà di un annuncio o evento pubblicato, non come stato continuo della persona |
| A4 | Schede in alto: Mappa Social, Annunci, Attività | Il PDF prevede «Ultimi annunci» con due schede: **Annunci** (persone) e **Promozioni** (attività) | Usare le due schede del PDF dentro la Mappa |
| A5 | Avatar «personalizzabili» (personaggi illustrati) | Il PDF prevede foto profilo e «avatar animati» | **Decisa (modificata):** scelta libera tra foto propria (profilo e mappa) e avatar 3D di Nexus Social. Vedi sezione 7 |
| A6 | Pulsante «3D» sulla mappa | Mappa 3D (F9: inclinazione ed edifici 3D) | Compatibile: pulsante per passare da vista piatta a inclinata |

## 5. Ottimizzazione per smartphone e prestazioni

Vincoli di progetto per le Fasi 2 e 3, da verificare nel prototipo previsto da F9.

1. **Pochi avatar animati insieme.**
   - Si animano solo gli avatar visibili e vicini al centro dello schermo, con un numero massimo
     prefissato (da tarare nel prototipo).
   - Gli altri restano immagini ferme.
2. **Raggruppamento (cluster).** Quando si allontana lo zoom, i marker vicini diventano un cerchio
   con il numero, così la mappa resta leggibile e veloce.
3. **Immagini leggere.**
   - Avatar e icone come immagini piccole e compresse (per esempio WebP), in due o tre dimensioni
     per gli schermi.
   - Niente video e niente modelli 3D veri sulla mappa: il «3D» degli avatar è nell'illustrazione.
4. **Cache.** Avatar e icone vengono scaricati una volta e riusati, anche senza rete.
5. **Edifici 3D solo da vicino.** Gli edifici in rilievo compaiono solo a zoom elevato, come negli
   esempi MapLibre.
6. **Batteria.**
   - Animazioni ferme quando l'app è in secondo piano o il telefono è in risparmio energetico.
   - Rispetto dell'impostazione di sistema «riduci animazioni».
   - Posizione aggiornata ogni poche centinaia di metri (PDF).
7. **Obiettivi da misurare nel prototipo della Fase 3, sul telefono di prova:**
   - scorrimento fluido della mappa;
   - consumo di batteria accettabile in 10 minuti di uso;
   - tempo di apertura della mappa.

   I valori precisi si fissano dopo la prima misura, non a priori.

## 6. Decisioni da prendere

| N. | Domanda | Quando serve |
|---|---|---|
| A1 | ✅ **Decisa**: Chat, Mappa, Scambia (centrale, arancione), Vendita, Profilo; stile del mockup | — |
| A2 | Confermare che lo stato online/offline non viene mostrato | Prima della Fase 3 |
| A3 | Confermare gli stati «in attività» e «in gruppo» solo come proprietà di annunci ed eventi | Prima della Fase 3 |
| A4 | Confermare le due schede del PDF (Annunci, Promozioni) | Prima della Fase 3 |
| A5 | ✅ **Decisa (modificata)**: foto propria o avatar 3D, a scelta dell'utente | — |
| A6 | Confermare il pulsante 3D | Prima della Fase 3 |

**Nota sui costi (A5):** un set di avatar illustrati va disegnato o acquistato con licenza d'uso
commerciale. Un editor di avatar personalizzabili è molto più costoso da sviluppare. Gli avatar
del mockup sono un riferimento di stile; per usarli nell'app servono immagini originali con i
diritti chiari.

## 7. Decisioni A1 e A5 (14 novembre 2026)

### A1 · Barra di navigazione: approvata con modifica dell'ordine

- Ordine definitivo:
  1. **Chat**
  2. **Mappa**
  3. **Scambia**, pulsante centrale evidenziato in arancione
  4. **Vendita**
  5. **Profilo**
- Restano tutte e cinque le voci del PDF, con le funzioni invariate.
- Si applica solo lo stile grafico del mockup. Niente Home né Community.
- **Da fare nel codice, solo su autorizzazione:** oggi l'app ha l'ordine Mappa, Chat, Scambia,
  Vendita, Profilo (`lib/features/shell/app_shell.dart`) e apre sulla Mappa. Va deciso anche quale
  scheda si apre all'avvio.

### A5 · Immagine della persona: modificata dal titolare

- L'utente sceglie liberamente:
  - **una propria fotografia**, usata sia come foto profilo sia come **marker sulla Mappa
    Social**;
  - **un avatar illustrato 3D di Nexus Social** al posto della fotografia.
- Nessun obbligo di usare i personaggi 3D; le foto reali non sono limitate alla pagina del profilo.
- I marker di **aziende, locali, eventi e annunci** restano distinti da quelli delle persone
  (sezione 2).

**Controllo di coerenza (fatto prima di registrare la decisione):**

| Regola | Esito |
|---|---|
| PDF: nome abbreviato, @username e avatar sempre visibili agli utenti autenticati | Compatibile: la foto è l'avatar |
| PDF: foto profilo animate e avatar animati | Compatibile: l'animazione è applicata al marker (anello, piccolo movimento) |
| Posizione (PDF, P9-P11, M4) | Compatibile: il marker con la foto compare solo nella zona approssimata. Nessuna posizione precisa esposta in automatico; GPS in tempo reale solo agli autorizzati |
| P3 (interruttori di bio e social) | Non toccata: riguarda solo bio e link social |
| M1 (età mai mostrata) | Non toccata. Una foto può comunque far intuire l'età: segnalato al professionista (sotto) |
| Distinzione dei marker (sezione 2) | Mantenuta: persone rotonde, tutto il resto a goccia con icona |

**Conseguenze pratiche, senza cambiare la decisione:**

- **Prestazioni:** sulla mappa si usa una miniatura piccola e compressa della foto, creata al
  caricamento, mai la foto originale.
- **Regole della community:** vale già il divieto di usare foto di altre persone senza permesso.
  Le foto profilo rientrano nei contenuti moderabili e segnalabili (PDF, Fasi 7 e 9).
- **Minorenni:** la foto reale come marker sulla mappa va valutata dal professionista nella
  revisione degli obblighi dell'art. 28 del Digital Services Act (vedi
  `DECISIONI_PRIVACY_APPROVATE.md`, «Obblighi da verificare»).
