## Architettura RMI – Sistema d'Asta Online

### Panoramica
La nuova versione del sistema d'asta sfrutta Java RMI per esporre servizi remoti e consentire la propagazione asincrona degli eventi ai client. L'architettura è composta da tre macro-componenti:
- `AuctionService` – interfaccia remota pubblicata nel registro RMI.
- `RMIAuctionServer` – implementazione del servizio remoto, responsabile della logica d'asta e dell'orchestrazione delle chiamate ai client.
- `RMIClient` – applicazione lato utente che interagisce con il servizio remoto e implementa un canale di callback (`ClientCallback`) per ricevere notifiche push dal server.

### Componenti e responsabilità
- **AuctionService**  
  Espone i metodi remoti per:
  - registrare/deregistrare un client (`registerClient`, `unregisterClient`);
  - richiedere lo stato corrente (`getCurrentState`);
  - inoltrare un'offerta (`submitBid`);
  - inviare messaggi di chat (`sendChatMessage`).

- **ClientCallback**  
  Interfaccia remota implementata dal client per ricevere eventi dal server:
  - `onSystemMessage` per annunci testuali;
  - `onAuctionUpdate` per l'aggiornamento dello stato d'asta;
  - `onBidResult` per l'esito di un'offerta (successo/fallimento).

- **RMIAuctionServer**  
  - Mantiene la coda degli oggetti in asta e pianifica la durata delle singole sessioni (`ScheduledExecutorService`).
  - Sincronizza l'accesso allo stato corrente (oggetto, prezzo, best bidder).
  - Gestisce il broadcast verso i client registrati; quando una callback solleva `RemoteException`, il client viene rimosso automaticamente.
  - Supporta il failover della porta del registro: tenta la porta scelta e, se occupata, prova vari offset.

- **RMIClient**  
  - Si registra tramite `AuctionService` inviando anche la propria callback (`ClientEventListener`).
  - Reagisce alle notifiche push aggiornando la barra di stato locale e loggando i messaggi.
  - Gestisce la riconnessione all'occorrenza di `RemoteException` ripetendo il lookup e la registrazione, preservando il nickname.

### Flussi RMI principali
1. **Registrazione**
   1. Il client effettua il lookup del servizio nel registro RMI (`LocateRegistry.getRegistry(host, port)`).
   2. Invoca `registerClient(nickname, callback)`; il server valida l'univocità del nickname e aggiorna la mappa `Map<String, ClientSession>`.
   3. In caso di successo, il server invia:
      - a tutti i client una `onSystemMessage` di join;
      - al nuovo client una `onAuctionUpdate` con lo stato corrente.

2. **Invio Offerta**
   1. Il client invoca `submitBid(nickname, amount)`.
   2. Il server verifica incremento minimo e consistenza del nickname.
   3. Se accettata, aggiorna `currentPrice`/`topBidder`, programma il broadcast di `onAuctionUpdate` e chiama `onBidResult(true, …)` su chi ha offerto.
   4. Se rifiutata, chiama `onBidResult(false, …)` con il minimo accettabile.

3. **Aggiornamento Periodico / Cambio Oggetto**
   - Un job schedulato termina la sessione dopo `roundDuration`.
   - Il server annuncia il vincitore via `onSystemMessage`, passa al prossimo oggetto e invia `onAuctionUpdate` con il nuovo articolo.

4. **Chat**
   - `sendChatMessage` propaga messaggi formattati attraverso `onSystemMessage`.

### Robustezza a guasti
- **Guasto del client**  
  Ogni chiamata di broadcast è protetta: in caso di `RemoteException`, il server innesca un'operazione atomica di deregistrazione del client e logga l'evento. Le sessioni restanti continuano senza blocchi.

- **Guasto del server**  
  Il client intercetta `RemoteException` quando prova a invocare il servizio. In tale scenario:
  - effettua un tentativo di lookup dopo un backoff esponenziale;
  - se la riconnessione riesce, ristabilisce la registrazione.
  Inoltre il server tenta più porte per pubblicare il registro, mitigando conflitti di binding all'avvio.

- **Persistenza stato locale**  
  Entrambi i lati utilizzano oggetti `Record` serializzabili (`AuctionItem`, `AuctionState`) per trasferire informazioni; il client mantiene uno snapshot locale che consente di continuare a mostrare l'ultimo stato anche durante eventuali riconnessioni.

### Diagramma di sequenza (testuale)
```
Client -> Registry : lookup("AuctionService")
Client -> AuctionService : registerClient(nick, callback)
AuctionService -> Client : onSystemMessage("nick è entrato")
AuctionService -> Client : onAuctionUpdate(state)
Client -> AuctionService : submitBid(nick, amount)
AuctionService -> Client : onBidResult(true, state, "")
AuctionService -> All Clients : onAuctionUpdate(state)
```

### Considerazioni di sicurezza
- L'implementazione limita i nickname a caratteri alfanumerici/underscore e lunghezza massima.
- I messaggi vengono sanificati per prevenire iniezioni di newline nelle console dei client.
- Il registry può essere avviato su una porta dedicata e protetto con un `java.security.Policy` qualora si distribuisca su host condivisi.

### Parametri configurabili
- Durata round (`auction.round.durationSeconds`).
- Timeout di riconnessione del client.
- Strategia di fallback sulle porte (`registry.basePort`, `registry.maxAttempts`).

Questa progettazione consente di riutilizzare la logica core del sistema d'asta, isolando la parte di comunicazione e sfruttando le primitive RMI per semplificare lo scambio di messaggi bidirezionale.


