# Fase 1 · Blocco 1 «Fondamenta e APK»: esito delle verifiche

Stato:
- **tutte le verifiche superate** (14 novembre 2026);
- decisioni del titolare prese (sezione 4); in attesa della sua approvazione finale;
- il Blocco 2 non è avviato.

## 1. Riferimenti

| Dato | Valore |
|---|---|
| Ramo | `fase1/blocco1`, derivato da `fase0/completamento` al commit `17d85de` |
| Commit | `547b428` (documentazione, passaggio 1.1); `5910fb3` (build identificabile, passaggio 1.2) |
| `main` | Invariato a `5e32045` |
| Build GitHub Actions | Esecuzione 135: tentativo 1 (build 13501) e riesecuzione, tentativo 2 (build 13502), entrambi riusciti |
| APK | `nexus-social-0.1.0+13501-5910fb3-fase1-blocco1.apk`, provato sul telefono; `nexus-social-0.1.0+13502-5910fb3-fase1-blocco1.apk`, prodotto dalla riesecuzione (stesso codice). Entrambi debug |

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
| 1 | Riesecuzione reale della build per dimostrare un numero distinto | ✅ **Fatta**: la riesecuzione ha prodotto la build **13502** (versione 0.1.0, stesso commit e ramo), riletta dall'APK; 11 test su 11, Supabase HTTP 200 |
| 2 | Verifica Firebase su emulatore per questo ramo | ✅ **Non necessaria** (titolare, 14/11/2026). Le parti dei servizi non sono cambiate; la registrazione FCM è confermata anche sul telefono reale |
| 3 | Repository pubblico: l'APK di prova è scaricabile da qualsiasi utente GitHub collegato | ✅ **Resta così per ora** (titolare, 14/11/2026). Da riconsiderare prima della beta (Fase 9) |
| 4 | La riga Versione va a capo dentro il nome del ramo («fase1/» · «blocco1»); tutte le informazioni restano leggibili | Miglioria estetica per il restyling |
