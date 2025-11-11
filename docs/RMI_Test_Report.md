## Test Report – Implementazione RMI

### Strategia
- **Test automatici (JUnit 5)**: esecuzione tramite `mvn verify`. Coprono il flusso di registrazione, ricezione aggiornamenti, invio di un’offerta e validazione del risultato (`src/test/java/it/unibz/auction/rmi/RMIAuctionServerIT.java`).
- **Test manuali**: sessioni interattive con 3 client contemporanei su macOS e Windows, simulando offerte concorrenti, messaggi chat e riconnessioni forzate (spegnimento improvviso del client, stop del server).

### Risultati dei test automatici
- `RMIAuctionServerIT.testClientRegistrationAndBidFlow`  
  ✅ Verifica che:
  - la registrazione invochi il callback con lo stato iniziale;
  - un’offerta valida venga accettata e aggiorni lo stato lato server;
  - il risultato dell’offerta sia inoltrato tramite `onBidOutcome`.

### Test manuali principali

| ID | Scenario | Passi sintetici | Esito |
|----|----------|-----------------|-------|
| M1 | Riconnessione client | Avvio server; avvio client; stop forzato server; riavvio server; client in auto-reconnect | ✅ |
| M2 | Collisione nickname | Due client usano lo stesso nickname | ✅ – secondo client riceve errore «Nickname già in uso» |
| M3 | Chat broadcast | Tre client inviano `MSG` in rapida successione | ✅ – tutti i client ricevono i messaggi ordinati |
| M4 | Fine oggetti in coda | Consumati tutti gli articoli dell’asta | ✅ – notifiche di termine inviate, nuovi client informati |

### Bug riscontrati e fixati
1. **Riconnessione con nickname duplicato**  
   - Sintomo: dopo un `RemoteException`, il client non riusciva a registrarsi di nuovo (`AuctionException: Nickname già in uso`).  
   - Fix: durante la riconnessione il client richiede esplicitamente l’`unregister` prima della nuova `registerClient`, rendendo l’operazione idempotente.

2. **Timeout sessione in test**  
   - Sintomo: con gli item standard (120 s) i test di integrazione risultavano troppo lenti.  
   - Fix: introdotti item di test con durata 10 s per velocizzare l’esecuzione automatica.

### Issue notevoli ancora aperte
- Se il server viene terminato senza invocare `shutdown` i client riceveranno una `RemoteException` e proveranno a riconnettersi indefinitamente. È il comportamento atteso, ma si suggerisce di usare `Ctrl+C` (che attiva l’hook di shutdown) per una chiusura pulita.
- La lista degli oggetti è caricata in memoria. Non sono previsti test di persistenza su file/database.

### Prossimi miglioramenti suggeriti
- Aggiungere test di carico che simulino decine di client tramite thread per valutare le prestazioni del broadcast RMI.
- Introdurre mock dei callback per isolare la logica del server senza dipendere dall’RMI runtime nelle unit test.


