# Fase 0 · Verifica del nome «Nexus Social» sugli store

Verifica del 13 novembre 2026, fatta con ricerche web pubbliche. Non sono stati usati account
Google Play Console o App Store Connect, che non esistono ancora (Fase 10).

## Risultato in breve

| Controllo | Esito |
|---|---|
| Identificativo Android `com.nexussocial.app` | **GIÀ USATO su Google Play** da un'altra app. **Va cambiato prima del primo caricamento.** |
| Titolo «Nexus Social» su Google Play | Google Play non obbliga titoli unici, ma esistono già app «Nexus» di uno sviluppatore chiamato «Nexus Social». Alto rischio di confusione. |
| Nome «Nexus Social» su App Store | Non trovato identico, ma esistono nomi molto simili. La conferma definitiva si ha solo prenotando il nome in App Store Connect. |
| Marchio (EUIPO / UIBM) | **Non verificato.** Da fare prima di scegliere il nome definitivo. |

## Dettagli

### Google Play

- Le pagine di Google Play mostrano già:
  - un'app «Nexus» con identificativo `com.nexussocial.app`, sviluppatore «Nexus Social», rete
    professionale per creatori di contenuti per adulti
    ([scheda](https://play.google.com/store/apps/details?id=com.nexussocial.app&hl=en_US));
  - un'altra app «Nexus», `com.nexus.social`, dello stesso tipo
    ([scheda](https://play.google.com/store/apps/details?id=com.nexus.social)).
- `com.nexussocial.app` è **lo stesso identificativo** usato oggi da Nexus Social:
  - Android, `android/app/build.gradle.kts`: `applicationId`;
  - iOS: bundle identifier.
- Su Google Play l'identificativo è unico e permanente. Se un altro sviluppatore lo usa già, la
  console chiede di sceglierne un altro
  ([documentazione Android](https://developer.android.com/developer-verification/guides/google-play-console)).
- **Conseguenze:**
  - prima di pubblicare va scelto un nuovo `applicationId`;
  - il cambio tocca anche Firebase: l'app Android è registrata con `com.nexussocial.app` e la CI
    controlla proprio quell'identificativo;
  - il cambio è una modifica tecnica della **Fase 1**: **non è stato fatto ora** e richiede la tua
    autorizzazione;
  - finché si installano solo APK di prova sul tuo telefono non ci sono conflitti, ma conviene
    decidere presto, perché ogni dato collegato all'identificativo va poi migrato.
- I titoli delle app su Google Play non sono unici, quindi «Nexus Social» come titolo sarebbe
  tecnicamente possibile. Però uno sviluppatore si chiama già «Nexus Social» e ha app «Nexus» per
  un pubblico adulto: c'è un rischio concreto di confusione e di contestazioni sul marchio.

### App Store

- Su App Store il nome dell'app deve essere unico in tutto lo store. Se è già preso, App Store
  Connect non lo accetta; chi ha diritti di marchio può presentare un reclamo
  ([guida App Store Connect](https://developer-rno.apple.com/help/app-store-connect/create-an-app-record/add-a-new-app)).
- Trovati nomi simili:
  - «Nexus Socials», Social Networking
    ([scheda](https://apps.apple.com/us/app/nexus-socials/id6514310456));
  - lo sviluppatore «Nexus Social Limited Liability Company», con l'app «Nexus - Find Your
    Communities»
    ([pagina](https://apps.apple.com/sg/developer/nexus-social-limited-liability-company/id6777190731));
  - «The Nexus Social Networking»
    ([pagina](https://apps.apple.com/au/developer/abt-associates-pty-ltd/id1470368086)).
- Un nome identico «Nexus Social» non è emerso, ma può essere prenotato da app non ancora
  pubblicate. **La verifica definitiva richiede l'account Apple Developer**, a pagamento, previsto
  in Fase 10.

## Opzioni (decisione tua; il piano la elenca tra le «decisioni ancora da prendere»)

1. **Tenere «Nexus Social» come nome nell'app**, con un titolo distintivo sugli store e un nuovo
   identificativo tecnico. Rischio: confusione con le app esistenti.
2. **Scegliere un nome diverso** prima di investire in grafica, store e marchio. Ripetendo questa
   verifica su un elenco di 3-5 candidati.
3. In entrambi i casi: verifica del marchio (EUIPO per l'UE, UIBM per l'Italia) prima della
   decisione finale.

## Decisione (13 novembre 2026, F8)

- Il nome **«Nexus Social» resta invariato**.
- L'identificativo `com.nexussocial.app` resta durante lo sviluppo e i test con APK.
- Va **cambiato prima del primo caricamento su Google Play, entro la Fase 9** (beta chiusa).
- Il cambio è un intervento tecnico da autorizzare: Firebase, CI e iOS.

## Azioni residue

- [x] Decidere il nome: «Nexus Social» invariato (F8).
- [ ] Scegliere il nuovo identificativo Android/iOS e autorizzare la modifica tecnica, entro la Fase 9.
  Comporta: nuova app Android su Firebase, nuovo `google-services.json`, aggiornamento della CI.
- [ ] Ricerca di marchio sul nome scelto.
- [ ] Prenotare il nome su App Store Connect quando verrà creato l'account Apple (Fase 10).
