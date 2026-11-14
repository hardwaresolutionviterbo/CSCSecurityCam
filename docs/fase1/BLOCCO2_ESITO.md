# Fase 1 · Blocco 2 «Navigazione e grafica»: esito delle verifiche automatiche

Stato:
- controlli automatici superati (14 novembre 2026);
- **prova sul telefono del titolare da fare**;
- Blocco 3 non avviato; `main` invariato.

## 1. Riferimenti

| Dato | Valore |
|---|---|
| Ramo | `fase1/blocco2`, derivato da `fase1/blocco1` al commit `f039b11` |
| Passaggio 1.3 (test di non regressione) | Commit `053fa0a`, `5dbfce9`, `d960b2c`, `d6bcb1a`, `52bc276`; build 14001 verde **prima** di ogni cambio grafico |
| Passaggi 1.4 e 1.5 (barra A1, apertura su Chat, grafica) | Commit `4ced4af`; build 14101 verde |
| APK da provare | `nexus-social-0.1.0+14101-4ced4af-fase1-blocco2.apk` |

## 2. Test automatici

| Gruppo | Esito |
|---|---|
| Analisi statica | ✅ Nessun problema |
| Test Flutter | ✅ 43 superati, più F6 con Firebase attivo in una seconda esecuzione (1 saltato nella prima, come previsto) |
| F1-F11 (non regressione, sezione 6 del piano) | ✅ Verdi sull'app **prima** e **dopo** la nuova grafica |
| F8 (registrazione push con Firebase simulato) | ✅ 8 casi |
| Regole Firestore sull'emulatore `demo-nexus-social` | ✅ Workflow «Firestore Rules» avviato sul ramo |
| A1 (ordine, apertura su Chat, Scambia centrale arancione in chiaro e scuro) | ✅ |
| Test esistenti dei blocchi precedenti | ✅ Invariati |

## 3. Controlli sull'APK

| Controllo | Esito |
|---|---|
| Identificativi (versione, build, commit, ramo) | ✅ |
| Deep link verso schermate dell'app | ✅ Nessuno. Ammesse solo due activity della libreria **Firebase Authentication** (`GenericIdpActivity`, `RecaptchaActivity`), che servono al ritorno dalla verifica del login con SMS (Fase 2) e non aprono schermate di Nexus Social |
| Chiavi privilegiate (token con ruolo diverso da `anon`, chiavi `sb_secret_`) | ✅ Nessuna. La parola `service_role` compare solo in un commento della libreria Supabase («Never expose your service_role key in the browser») |

## 4. Note

- **F11** · Il selettore del raggio della Mappa è una funzione esistente non elencata nel
  piano: è stato aggiunto ai test di non regressione.
- **F4** · Le righe Privato e Azienda oggi sono solo informative, non selezionabili: il test
  verifica che restino così.
- **F8** · Modifica minima al codice: la registrazione push usa un componente sostituibile.
  - In produzione esegue le stesse operazioni di Firebase di prima.
  - Nei test usa una simulazione in memoria.
- **Passaggi 1.4 e 1.5** in un solo commit, perché toccano gli stessi file.

## 5. Prova sul telefono (da fare)

1. L'app si apre su **Chat**.
2. Barra: Chat, Mappa, **Scambia** (pulsante centrale arancione), Vendita, Profilo.
   Tutte le voci si aprono.
3. Campanella: «Nessuna notifica per ora».
4. Tema chiaro e scuro.
5. Profilo:
   - Supabase e Firebase «Collegato»;
   - Diagnostica push con la registrazione «registrata»;
   - riga Versione completa su una riga sotto l'etichetta.
6. Mappa: il raggio si cambia (1, 5, 10, 30 km).
7. Giudizio del titolare sulla nuova grafica.
