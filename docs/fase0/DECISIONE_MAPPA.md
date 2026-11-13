# Fase 0 · Servizio per la mappa 3D: analisi di MapLibre

Il piano elenca il servizio per la mappa 3D tra le **decisioni ancora da prendere**. La checklist
della Fase 0 indicava MapLibre come soluzione consigliata. Questo documento verifica se MapLibre
soddisfa i requisiti della mappa prima di adottarlo.

**Stato: candidato, non ancora adottato.** Serve la tua decisione sul significato di «3D»
(sezione 4) e un prototipo all'inizio della Fase 3.

## 1. Requisiti del piano per la mappa

1. Mappa 3D dell'Italia, centrata sulla posizione in tempo reale, aggiornata ogni poche centinaia
   di metri (Fase 3).
2. Zone approssimate per gli annunci personali; posizione scelta a mano per singolo annuncio; GPS
   in tempo reale solo per gli autorizzati.
3. Avatar animati sulla mappa; toccandoli si apre la scheda.
4. Raggio fino a 30 km.
5. Segnaposto permanente delle sedi aziendali e mappa delle promozioni attive (Fase 6).
6. Android e iPhone con un solo codice Flutter.
7. Costi: gratuito entro certi limiti, poi a pagamento. Il piano chiede di conoscere i limiti
   gratuiti.

## 2. Componenti valutati

- **MapLibre**: motore di mappa open source. In Flutter c'è il pacchetto ufficiale `maplibre_gl`
  per Android, iOS e Web; esiste anche il pacchetto più recente `maplibre`.
- **OpenFreeMap**: servizio gratuito di tile vettoriali basate su OpenStreetMap, quindi copre
  anche l'Italia.

## 3. Compatibilità con i requisiti

| Requisito | Esito | Dettaglio |
|---|---|---|
| Inclinazione e rotazione della mappa | ✅ | Supportate dai pacchetti Flutter MapLibre ([`maplibre`](https://pub.dev/documentation/maplibre/latest)) |
| Edifici 3D | ✅ | Livello *fill extrusion* supportato su Android, iOS e Web da `maplibre_gl` ([documentazione](https://pub.dev/documentation/maplibre_gl/0.26.2/)); le tile OpenFreeMap includono le altezze degli edifici ([esempio MapLibre](https://maplibre.org/maplibre-gl-js/docs/examples/display-buildings-in-3d/)) |
| **Rilievo del terreno 3D** (montagne in rilievo) | ❌ **Non disponibile ufficialmente** su Android e iPhone | MapLibre Native non lo supporta ancora; il pacchetto Flutter lo segnala come bloccato da MapLibre ([issue](https://github.com/josxha/flutter-maplibre/issues/657)). Esistono solo versioni non ufficiali di terzi. |
| Posizione dell'utente | ✅ | Supportata da `maplibre_gl`; i permessi li chiede l'app ([documentazione](https://pub.dev/documentation/maplibre_gl/0.26.2/)) |
| Segnaposto e simboli toccabili | ✅ | Livelli *symbol* e *circle* |
| **Avatar animati** | ⚠ Da provare | I simboli della mappa sono immagini statiche. L'animazione richiede una tecnica dedicata (widget Flutter sovrapposti alla mappa o immagini che cambiano). Va dimostrata con un prototipo, anche sul consumo di batteria. |
| Zone approssimate e raggio | ✅ | Le calcola il server (Supabase, `nearby_listings`); la mappa le disegna come cerchi o aree |
| Android e iPhone | ✅ | Pacchetto Flutter con implementazioni native |
| Costo | ✅ con riserva | OpenFreeMap è gratuito, senza limiti di visualizzazioni né richieste, senza registrazione, chiavi o cookie ([openfreemap.org](https://openfreemap.org)) |

## 4. Punti che richiedono una tua decisione

1. **Cosa significa «3D».**
   - Con MapLibre oggi si ottengono, su Android e iPhone, mappa inclinata ed edifici in 3D.
   - Il rilievo delle montagne in 3D non è disponibile ufficialmente.
   - **Proposta:** «3D» significa inclinazione, rotazione ed edifici 3D. Il rilievo del terreno si
     aggiunge quando MapLibre lo rilascerà in versione ufficiale, senza usare versioni non
     ufficiali.
2. **Affidabilità di OpenFreeMap.**
   - Il servizio è fornito «così com'è», senza garanzie; eventuali controversie vanno in arbitrato
     a Budapest ([termini](https://openfreemap.org/tos/)).
   - Non c'è un contratto di servizio. Per un'app pubblicata serve un piano di riserva.
   - **Proposta di riserva:**
     - OpenFreeMap è open source e si può ospitare in proprio;
     - oppure si passa a un fornitore a pagamento con chiave, cambiando solo l'indirizzo dello
       stile della mappa.
3. **Privacy delle mappe.**
   - Il telefono scarica le parti di mappa dal server delle tile, che riceve l'indirizzo IP e
     l'area guardata.
   - Va scritto nell'informativa privacy (già segnato nella bozza).
4. **Attribuzione.** I dati OpenStreetMap richiedono la dicitura «© OpenStreetMap contributors»
   visibile sulla mappa.

## 5. Alternative (non analizzate in dettaglio)

Fornitori con account e chiave, come Mapbox, MapTiler o Google Maps, offrono più funzioni, rilievo
compreso, con piani gratuiti limitati e poi a pagamento. Condizioni e limiti attuali **non sono
stati verificati** in questa analisi: vanno controllati solo se la proposta della sezione 4 non
viene approvata.

## 6. Proposta di decisione

> Adottare **MapLibre (`maplibre_gl`) con tile OpenFreeMap** come servizio mappa, alle condizioni
> della sezione 4. Validazione con un prototipo all'inizio della Fase 3, sul telefono:
> - mappa dell'Italia inclinata con edifici 3D;
> - posizione dell'utente;
> - un avatar animato toccabile;
> - consumo di batteria accettabile.
>
> Se il prototipo fallisce, si riapre la scelta tra le alternative della sezione 5.

Nessuna modifica al codice in Fase 0: la mappa si sviluppa in Fase 3.
