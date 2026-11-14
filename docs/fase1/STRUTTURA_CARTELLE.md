# Corrispondenza tra le cartelle del codice e la struttura del piano

Il PDF (Fase 1) chiede di organizzare le cartelle in: **account, chat, mappa, annunci, vendita,
admin**. Nel codice le cartelle hanno nomi in inglese, come d'uso nei progetti Flutter.

## Sezioni dell'app (`lib/features/`)

| Cartella del PDF | Cartella nel codice | Contenuto oggi | Fase di sviluppo |
|---|---|---|---|
| account | `lib/features/profile/` | Schermata Profilo: scelta Privato / Azienda (anteprima), indicatori dei servizi, riga Informazioni con la versione; diagnostica push (solo nelle build di debug) | 2 |
| chat | `lib/features/chat/` | Anteprima della sezione Chat | 4 |
| mappa | `lib/features/map/` | Anteprima della Mappa Social | 3 |
| annunci | `lib/features/listings/` | Anteprima della sezione **Scambia**; gli annunci sulla mappa e gli scambi usano la stessa tabella `listings` di Supabase | 3, 5 |
| vendita | `lib/features/sale/` | Anteprima della sezione Vendita | 5 |
| admin | `lib/features/admin/` | Anteprima «Gestionale Admin». **Non raggiungibile** dall'app in questa fase | 7 |
| — | `lib/features/shell/` | Impalcatura comune: barra in alto con campanella, barra di navigazione in basso | 1 |

## Parti comuni (`lib/core/`)

| Cartella | Contenuto |
|---|---|
| `lib/core/config/` | Configurazione: chiavi passate dalla build, versione, numero di build, commit e ramo |
| `lib/core/services/` | Avvio di Firebase e Supabase, notifiche push, registrazione del dispositivo |
| `lib/core/theme/` | Colori, caratteri, tema chiaro e scuro |
| `lib/core/widgets/` | Componenti grafici condivisi (logo, campanella, anteprime) |

## Server e build

| Cartella o file | Contenuto |
|---|---|
| `supabase/migrations/` | Schema del database Supabase e regole di accesso (RLS) |
| `firebase/firestore.rules`, `firebase/tests/` | Regole di sicurezza di Firestore e relativi test sull'emulatore |
| `firebase/audit/` | Strumenti di audit in sola lettura (Fase A.2) |
| `.github/workflows/` | Build dell'APK Android e test automatici su GitHub Actions |
| `codemagic.yaml` | Build per iPhone su Codemagic (F11; non ancora collegato) |
| `docs/` | Documentazione delle fasi |

## Perché i nomi non vengono rinominati

- Cambiare nome alle cartelle non aggiunge nulla per l'utente.
- Romperebbe riferimenti e test, e rischierebbe di alterare funzioni esistenti (regola R2).
- La corrispondenza qui sopra è la regola ufficiale.
