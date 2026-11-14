# Fase 1 · Blocco 1 «Fondamenta e APK»: esito delle verifiche

Stato:
- **tutte le verifiche superate** (14 novembre 2026);
- in attesa dell'approvazione finale del titolare e delle decisioni aperte (sezione 4);
- il Blocco 2 non è avviato.

## 1. Riferimenti

| Dato | Valore |
|---|---|
| Ramo | `fase1/blocco1`, derivato da `fase0/completamento` al commit `17d85de` |
| Commit | `547b428` (documentazione, passaggio 1.1); `5910fb3` (build identificabile, passaggio 1.2) |
| `main` | Invariato a `5e32045` |
| Build GitHub Actions | Esecuzione 135, tentativo 1: tutti i passaggi riusciti |
| APK | `nexus-social-0.1.0+13501-5910fb3-fase1-blocco1.apk` (debug), disponibile fino al 28/11/2026 |

## 2. Controlli automatici (GitHub Actions)

| Controllo | Esito |
|---|---|
| Analisi statica | ✅ Nessun problema |
| Test Flutter | ✅ 11 su 11: i 7 esistenti, invariati, più 4 nuovi per la regola R1 |
| Configurazione Firebase per `com.nexussocial.app` | ✅ Presente nell'APK |
| Supabase raggiungibile, schema presente | ✅ HTTP 200 |
| Identificativi nell'APK (versione 0.1.0, build 13501, commit, ramo) | ✅ Riletti dall'APK prodotto |

## 3. Prove sul telefono Android del titolare

| Prova | Esito |
|---|---|
| Installazione e avvio | ✅ |
| Apertura delle 5 sezioni (Mappa, Chat, Scambia, Vendita, Profilo) | ✅ |
| Campanella: «Nessuna notifica per ora» | ✅ |
| Tema chiaro e tema scuro | ✅ |
| Profilo: Supabase «Collegato» (lettura reale dal server) | ✅ |
| Profilo: Firebase «Collegato» | ✅ |
| Diagnostica push: permesso concesso, «Registrazione FCM: registrata», canale `nexus_social_default` | ✅ Registrazione reale sui server Firebase |
| Diagnostica push: «Associazione account: in attesa di accesso» | ✅ Atteso: il login arriva in Fase 2 |
| Profilo → Informazioni → Versione: `0.1.0 (13501) · 5910fb3 · fase1/blocco1` | ✅ |

**Limite noto:** login e Firestore sono solo inizializzati. Nessuna lettura o scrittura
autenticata avviene prima della Fase 2.

## 4. Punti aperti

| N. | Punto | Stato |
|---|---|---|
| 1 | Riesecuzione reale della build per dimostrare un numero distinto (atteso 13502); oggi la regola è verificata nel codice e con prove simulate | Decisione del titolare |
| 2 | Verifica Firebase su emulatore per questo ramo (facoltativa: le parti dei servizi non sono cambiate; l'ultima verifica riuscita è del 12/11/2026 sul codice di `main`) | Decisione del titolare |
| 3 | Repository pubblico: l'APK di prova è scaricabile da qualsiasi utente GitHub collegato | Decisione del titolare |
| 4 | La riga Versione va a capo dentro il nome del ramo («fase1/» · «blocco1»); tutte le informazioni restano leggibili | Miglioria estetica per il restyling |
