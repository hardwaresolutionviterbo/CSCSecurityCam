# Fase 1 · Test automatici di Supabase (regola R3)

Documento di supporto a `PIANO_OPERATIVO.md`, passaggio 1.7. **Nessun test viene eseguito sul
database reale.**

## 1. Dove girano i test

- In GitHub Actions, su un database Supabase **temporaneo** creato all'inizio della prova e
  cancellato alla fine (Supabase CLI in locale nella macchina di GitHub).
- Gli utenti e i dati di prova sono inventati: nessun dato reale, nessuna chiave reale.
- I token Firebase di prova sono firmati con una **chiave di prova** generata al momento.
  - Il database temporaneo si fida solo di quella chiave.
  - Il progetto Firebase reale non viene mai contattato.
- I log mostrano solo il nome del caso e l'esito (superato / fallito), mai token o contenuti.

**Da verificare all'inizio del passaggio 1.7:** il modo esatto in cui il Supabase locale accetta
token esterni (integrazione «Firebase Auth» di Supabase oppure configurazione JWT equivalente).

- Se l'ambiente locale non permette di simularla fedelmente, prima di scrivere i test si propone
  al titolare un'alternativa documentata.
- In nessun caso si riduce il numero di casi coperti.

## 2. Struttura dei dati da provare

| Parte | Contenuto | Chi può leggere |
|---|---|---|
| **Pubblica** | Nome abbreviato, @username, avatar, bio e link social (secondo P3), tipo di account | Utenti autenticati, salvo blocco (P12, quando arriverà in Fase 2) |
| **Privata** | Nome e cognome completi, data di nascita (D1, M1), altri dati personali previsti | **Solo il proprietario**. Mai altri utenti, mai chi non è autenticato |

Il numero di telefono non viene salvato in Supabase in questa fase: resta a Firebase
Authentication.

## 3. Casi di prova obbligatori

**A · Separazione tra dati pubblici e privati**

| N. | Caso | Esito atteso |
|---|---|---|
| A1 | L'utente A legge i propri dati privati | Consentito |
| A2 | L'utente A legge il profilo pubblico di B | Consentito, solo campi pubblici |
| A3 | Il profilo pubblico non contiene nome completo né data di nascita, in nessuna forma | Verificato sulle colonne restituite |
| A4 | Il nome pubblico è sempre abbreviato (es. «Carlo C.») | Verificato |

**B · Nessuna lettura dei dati privati altrui**

| N. | Caso | Esito atteso |
|---|---|---|
| B1 | A legge i dati privati di B direttamente | Zero righe o errore |
| B2 | A cerca i dati privati di B con filtri, ordinamenti o ricerche (nome, data di nascita) | Zero righe |
| B3 | A tenta di modificare o cancellare i dati di B | Rifiutato; dati di B invariati |
| B4 | A tenta di creare un profilo con l'identità di B | Rifiutato |
| B5 | A tenta di cambiare il proprio identificativo in quello di B | Rifiutato |
| B6 | Funzioni del database (es. `nearby_listings`) non restituiscono dati privati | Verificato sulle colonne restituite |
| B7 | Utente non autenticato (chiave pubblica senza token) | Nessun dato privato né pubblico dei profili |

**C · Token Firebase non validi o di altri utenti**

| N. | Caso | Esito atteso |
|---|---|---|
| C1 | Nessun token | Trattato come non autenticato (come B7) |
| C2 | Token scaduto | Rifiutato |
| C3 | Token con firma alterata o firmato da una chiave sconosciuta | Rifiutato |
| C4 | Token emesso per un **altro progetto Firebase** (destinatario o emittente diversi) | Rifiutato |
| C5 | Token valido dell'utente B usato per leggere i dati privati di A | Vede solo i dati di B, mai quelli di A |
| C6 | Token valido di A con il contenuto modificato per fingersi B | Rifiutato (firma non valida) |
| C7 | Token malformato o troncato | Rifiutato, senza dettagli sensibili nel messaggio di errore |
| C8 | Token con data di emissione nel futuro | Rifiutato |

**D · Regole già presenti nello schema (non devono peggiorare)**

| N. | Caso | Esito atteso |
|---|---|---|
| D1 | Un utente non diventa admin da solo | Rifiutato |
| D2 | Posizione degli annunci solo approssimata per gli altri utenti | Verificato |
| D3 | Le segnalazioni di un utente non sono leggibili da altri utenti | Verificato |

## 4. Criterio di superamento

- Tutti i casi A, B, C e D superati in GitHub Actions.
- Se anche un solo caso fallisce, il passaggio 1.7 non è concluso e la migrazione non viene
  proposta per il database reale.
- L'applicazione al database reale resta comunque soggetta all'autorizzazione esplicita del
  titolare.
