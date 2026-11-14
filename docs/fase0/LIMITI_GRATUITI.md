# Fase 0 · Limiti gratuiti di Firebase, SMS e Supabase

Verifica del 13 novembre 2026 su fonti pubbliche (pagine ufficiali e guide aggiornate, link in
fondo).

- **Prezzi e limiti cambiano**: vanno riconfermati nelle console di Firebase e Supabase prima di
  attivare un servizio a pagamento.
- Nessuna modifica è stata fatta ai progetti Firebase e Supabase.

## 1. Sintesi

| Servizio | Gratuito? | Cosa conta per Nexus Social |
|---|---|---|
| **Login con SMS (Firebase)** | **No sul piano gratuito Spark.** Serve il piano Blaze, con carta collegata; si paga ogni SMS | **Indispensabile in Fase 2.** Decisione e budget da approvare prima |
| Utenti Firebase Auth | Gratis fino a 50.000 utenti attivi al mese | Ampio margine per la beta |
| Firestore (chat, membership, notifiche) | 1 GiB di dati, 50.000 letture, 20.000 scritture e 20.000 cancellazioni al giorno, 10 GiB di traffico al mese | Sufficiente per sviluppo e beta chiusa |
| Cloud Functions (logica lato server) | Solo con Blaze; con Blaze le prime 2 milioni di chiamate al mese sono gratuite | Servono per P2 (controlli lato server), rubrica PSI/OPRF e notifiche dei commenti |
| Cloud Storage for Firebase | Dal 3 febbraio 2026 **solo con Blaze** | Oggi le foto degli annunci sono su Supabase; da decidere in Fase 4 dove vanno i media delle chat |
| Notifiche push (FCM) | Gratuito (da riconfermare in console) | Già configurato in CI |
| **Supabase gratuito** | 2 progetti; per ciascuno 500 MB di database, 1 GB di file, 5 GB di traffico, 50.000 utenti attivi al mese | Sufficiente per sviluppo e beta, con due limiti importanti (sezione 4) |
| Mappa (OpenFreeMap) | Gratuito, senza chiavi né limiti dichiarati, senza garanzie | Già deciso (F9) |

## 2. SMS di verifica (Firebase Phone Auth)

- **Serve il piano Blaze.** Dal settembre 2024 un progetto deve essere collegato a un account di
  fatturazione per inviare gli SMS dell'autenticazione telefonica. Sul piano Spark il login con
  SMS non è disponibile.
- **Costo per l'Italia:** la tabella ufficiale di Identity Platform indica **0,05 $ per SMS
  inviato**, e **i primi 10 SMS al giorno non vengono addebitati**. Da riconfermare in console
  prima dell'attivazione.
- **Limite:** senza l'aggiornamento a Identity Platform c'è un tetto di 3.000 SMS al giorno.
- **La verifica in due passaggi via SMS** (2FA, richiesta dal PDF in Fase 2) è disponibile solo
  con Identity Platform, alle stesse tariffe per SMS.

**Stima indicativa** (ogni registrazione o nuovo accesso consuma almeno un SMS, di più in caso di
reinvii):

| Scenario | SMS | Costo indicativo |
|---|---|---|
| Sviluppo e test (meno di 10 SMS al giorno) | fino a 10 al giorno | 0 $ |
| Beta chiusa (10-20 persone, PDF Fase 9) | qualche decina in tutto | pochi dollari |
| 1.000 nuove registrazioni | circa 1.000-1.500 | circa 50-75 $ |

**Rischi e protezioni da prevedere in Fase 2** (requisito già in checklist: limite giornaliero e
protezione anti-abuso):

- **Attacchi «SMS pumping»**: richieste automatiche di codici verso numeri a pagamento, che
  generano costi. Servono:
  - limite di invii per numero e per dispositivo;
  - verifica anti-bot;
  - restrizione dei Paesi a cui si inviano SMS (per esempio solo Italia nella prima versione,
    **da decidere**).
- **Il piano Blaze non ha un tetto di spesa automatico.** Serve un **avviso di budget** nella
  console Google Cloud, per esempio con soglie al 50%, 80% e 100% di una cifra mensile da
  decidere.

## 3. Firebase: altri limiti

- **Firestore:** 1 GiB, 50.000 letture al giorno, 20.000 scritture al giorno, 20.000 cancellazioni
  al giorno, 10 GiB di traffico al mese.
  - La quota gratuita vale per **un solo database per progetto**.
  - Ogni query costa almeno una lettura, anche senza risultati.
  - Le quote si azzerano ogni giorno alla mezzanotte del Pacifico, cioè alle 9:00 italiane.
- **Cloud Functions (con Blaze):** gratuite fino a 2 milioni di chiamate al mese, 400.000 GB-secondi
  e 5 GB di traffico in uscita. C'è anche un piccolo costo di archiviazione per ogni funzione
  pubblicata.
- **Cloud Storage for Firebase:** dal 3 febbraio 2026 serve Blaze anche per l'uso gratuito.
- **Credito iniziale:** Google offre un credito di 300 $ ai nuovi account di fatturazione idonei.
  Va verificato se il tuo account ne ha diritto.

## 4. Supabase (piano gratuito)

- 2 progetti attivi.
- Per progetto:
  - 500 MB di database;
  - 1 GB di file;
  - 5 GB di traffico più 5 GB in cache;
  - 50.000 utenti attivi al mese;
  - richieste API illimitate.
- Limiti riportati da guide aggiornate, da riconfermare: 500.000 chiamate alle Edge Functions al
  mese, 200 connessioni realtime, file di massimo 50 MB.

**Due limiti importanti per Nexus Social:**

1. **Pausa dopo 1 settimana di inattività.**
   - Durante lo sviluppo, se per 7 giorni nessuno usa il progetto, Supabase lo mette in pausa e
     l'app riceve errori finché non lo riattivi dal pannello.
   - I dati non si perdono.
   - La CI di `main` interroga Supabase solo quando c'è una modifica su `main`, quindi non basta a
     tenerlo attivo.
2. **Backup automatici non inclusi nel piano gratuito.**
   - Prima di dati reali (beta chiusa) serve un backup manuale periodico, oppure il piano Pro.
   - Il piano Pro, secondo le guide, costa 25 $ al mese per organizzazione più consumi.

**Spazio:** 1 GB di file corrisponde a qualche centinaio o poche migliaia di foto compresse. La
compressione obbligatoria delle foto (già in checklist) è decisiva.

## 5. Decisioni (14 novembre 2026)

**Verifica richiesta dal titolare:** si possono usare gli SMS gratuiti senza metodo di pagamento?
**No.**

| Configurazione | SMS veri | Senza metodo di pagamento |
|---|---|---|
| Firebase Authentication su Spark | No (errore «billing not enabled» da settembre 2024) | Sì, ma senza SMS |
| Firebase Authentication su Blaze | Sì; primi 10 al giorno non addebitati | No |
| Firebase Authentication con Identity Platform su Spark | No: aggiunge 2FA e altre funzioni, con un limite di 3.000 utenti attivi al giorno, ma l'invio di SMS richiede comunque la fatturazione | Sì, ma senza SMS |

I **numeri di telefono di prova** configurati nella console (codice fisso, nessun SMS inviato)
funzionano senza metodo di pagamento e con lo stesso codice Flutter e Firebase.

| N. | Decisione |
|---|---|
| **L1** | La Fase 2 si sviluppa e si prova sul telefono con **numeri di prova**, sul piano Spark e senza carta. Il piano **Blaze** si attiva solo quando serve provare il numero reale del titolare (prima di chiudere la Fase 2) o iniziare la beta |
| **L2** | Quando si attiva Blaze: **budget di 10 € al mese** con avvisi al 50%, 80% e 100%. L'avviso non blocca la spesa |
| **L3** | SMS consentiti **solo verso numeri italiani (+39)** nella prima versione |
| L4 | Ancora aperta (Supabase: pausa e backup) |
| L5 | Ancora aperta (media delle chat, Fase 4) |

**Effetto da considerare nella progettazione della Fase 2:** anche le Cloud Functions di Firebase
richiedono Blaze.

- Finché il progetto resta su Spark (L1), la logica lato server della Fase 2 (P2, rubrica
  PSI/OPRF, scrittura dei profili riservata al server) non può usare le Cloud Functions.
- Va progettata con strumenti disponibili gratuitamente (per esempio le funzioni di Supabase),
  oppure si attiva Blaze prima.
- Da decidere nel documento di progetto della Fase 2. Nessuna scelta fatta ora.

## 5-bis. Proposte originali (per memoria)

| N. | Decisione | Proposta |
|---|---|---|
| L1 | Attivare il piano **Blaze** di Firebase (carta collegata) quando inizia la Fase 2, per il login con SMS | Necessario per il PDF (login SMS e 2FA). Senza, la Fase 2 non si può provare sul telefono |
| L2 | **Budget mensile** e avvisi di spesa su Google Cloud | Una cifra prudente per sviluppo e beta (per esempio 10-20 €/mese) con avvisi al 50/80/100% |
| L3 | **Paesi** abilitati per gli SMS | Solo Italia nella prima versione |
| L4 | **Supabase:** gestire la pausa per inattività e i backup | Restare sul piano gratuito in sviluppo, riattivando quando serve; backup manuale prima della beta; valutare Pro prima della Fase 10 |
| L5 | Dove salvare i **media delle chat** (Fase 4) | Da decidere in Fase 4, insieme ai limiti di peso già in sospeso nel piano |

## Fonti

- [Firebase Auth pricing 2026 (SuperTokens)](https://supertokens.com/blog/firebase-pricing)
- [Firebase Authentication pricing 2026 (Logto)](https://blog.logto.io/firebase-authentication-pricing)
- [Identity Platform pricing (Google Cloud)](https://cloud.google.com/identity-platform/pricing)
- [Firestore pricing (Google Cloud)](https://cloud.google.com/firestore/pricing)
- [Cloud Firestore billing (Firebase)](https://firebase.google.cn/docs/firestore/pricing?hl=en)
- [Cloud Storage for Firebase: cambi del 2026 (FAQ ufficiale)](https://firebase.google.com/docs/storage/faqs-storage-changes-announced-sept-2024?authuser=4)
- [Cloud Functions FAQ (Firebase)](https://firebase.google.com/docs/functions/faq-and-troubleshooting?hl=zh-TW)
- [Spark vs Blaze 2026 (DEV)](https://dev.to/androve2k/spark-vs-blaze-the-firebase-pricing-guide-i-wish-id-read-sooner-onb)
- [Supabase pricing (ufficiale)](https://supabase.com/pricing)
- [Supabase free tier 2026 (DEV)](https://dev.to/nayankyada/supabase-pricing-2026-free-tier-limits-compute-costs-when-to-upgrade-52af)
- [Supabase pricing 2026 (CloudZero)](https://www.cloudzero.com/blog/supabase-pricing/)
- [Evitare costi imprevisti su Blaze (RapidDev)](https://rapidevelopers.com/firebase-tutorial/how-to-avoid-overbilling-in-firebase)
