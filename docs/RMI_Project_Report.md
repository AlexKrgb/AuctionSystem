## Sistema d’Asta Online via Java RMI – Relazione di Progetto

### 1. Introduzione e requisiti
L’obiettivo del progetto è la migrazione del sistema d’asta online originariamente basato su socket TCP a un’architettura Java RMI che supporti:
- comunicazione bidirezionale tra server e client tramite callback;
- robustezza rispetto a guasti di processo e di rete;
- equivalenza funzionale rispetto alla versione precedente (chat condivisa, gestione offerte, turni a tempo).

Il lavoro si articola in quattro deliverable principali:
1. progettazione delle interazioni RMI e descrizione del nuovo design;
2. implementazione Java di server e client RMI;
3. campagna di test con segnalazione di bug o comportamenti inattesi;
4. discussione dettagliata delle differenze rispetto alla soluzione TCP, evidenziandone vantaggi e svantaggi.

La presente relazione (circa 2,5 pagine) sintetizza quanto realizzato nei punti sopra indicati.

---

### 2. Progetto dell’architettura RMI
#### 2.1 Componenti principali
- **AuctionService**: interfaccia remota pubblicata nel registro RMI, espone i metodi `registerClient`, `unregisterClient`, `getCurrentState`, `submitBid` e `sendChatMessage`. Tutti i metodi rilasciano eccezioni applicative (`AuctionException`) in caso di input non valido o violazioni di business logic.
- **ClientCallback**: interfaccia di callback implementata dai client per ricevere messaggi push (`onSystemMessage`, `onAuctionUpdate`, `onBidOutcome`). Questa scelta permette al server di notificare eventi senza richiedere polling continuo.
- **RMIAuctionServer**: implementazione concreta del servizio. Mantiene coda di oggetti (`Deque<AuctionItem>`), stato dell’asta (`AuctionState`), elenco di client registrati e un `ScheduledExecutorService` per la gestione dei round. Utilizza un `ReentrantLock` per la sincronizzazione degli accessi e garantisce che ogni broadcast sia protetto da gestione di `RemoteException`: se un client non risponde, viene deregistrato in modo sicuro.
- **RMIClient**: applicazione console che si collega al registro, invoca i metodi remoti e istanzia il callback (`ClientListener`). Rileva automaticamente `RemoteException` e tenta la riconnessione, mantenendo il nickname e la barra di stato locale.

#### 2.2 Flussi d’interazione
1. **Registrazione**: il client effettua lookup del servizio, invia il nickname e la callback. Il server valida il nickname (pattern alfanumerico 3–16) e registra il client. In caso di successo, invia lo stato corrente tramite `onAuctionUpdate` e notifica gli altri client con `onSystemMessage`.
2. **Offerte**: il client richiama `submitBid`. Il server controlla incremento minimo e consistenza del nickname; se valida, aggiorna `currentPrice`/`topBidder` e inoltra `onBidOutcome` al proponente, oltre a un broadcast dell’aggiornamento.
3. **Chat**: i messaggi vengono sanificati per rimuovere newline ed evitano iniezioni nella console remota. Ogni messaggio viene diffuso tramite `onSystemMessage`.
4. **Gestione round**: ogni articolo ha una durata (es. 120 s). Al termine, il server annuncia vincitore, aggiorna lo stato (non attivo) e passa all’oggetto successivo. Quando la coda termina, tutti i client ricevono la notifica di fine asta.

#### 2.3 Robustezza
- **Guasti client**: ogni `RemoteException` durante le callback comporta rimozione del client dalla mappa e broadcast di disconnessione. L’asta prosegue senza blocchi.
- **Guasti server**: il client intercetta `RemoteException` durante l’invocazione del servizio, tenta il lookup fino a 5 volte con backoff e si registra nuovamente. In caso di fallimento definitivo, mostra un messaggio d’errore all’utente.
- **Fallimenti di binding**: il server implementa `createRegistryWithFallback`, provando porte consecutive (default 5099, +4 fallback). In fase di reconnect il client prova a usare lo stesso nome di binding, sollevando un’eccezione chiara se il servizio non è disponibile.

---

### 3. Implementazione
#### 3.1 Server
- File principale: `it.unibz.auction.rmi.RMIAuctionServer`.
- Gli oggetti d’asta sono rappresentati dal record serializzabile `AuctionItem`, mentre lo stato condiviso è `AuctionState`.
- La schedulazione dei round avviene tramite `ScheduledExecutorService`; ogni volta che un round termina si pianifica automaticamente il successivo.
- Il metodo `safeInvoke` centralizza l’invocazione delle callback e la rimozione dei client non raggiungibili.

#### 3.2 Client
- File principale: `it.unibz.auction.rmi.RMIClient`.
- Mostra un’interfaccia testuale con comandi `BID`, `MSG`, `/info`, `/help`, `/quit`.
- Mantiene snapshot locale (`AtomicReference<AuctionState>`) per aggiornare la barra di stato e riproporla anche dopo la riconnessione.
- La classe interna `ClientListener` estende `UnicastRemoteObject` e implementa `ClientCallback`, garantendo la ricezione asincrona degli eventi.

#### 3.3 DTO ed eccezioni
- `AuctionItem`, `AuctionState`, `BidOutcome` (serializzabili con `serialVersionUID`).
- `AuctionException` fornisce messaggi user-friendly in caso di errori lato server, evitando confondere l’utente con stack trace remoti.

#### 3.4 Compatibilità con consegna
- All’interno della cartella `submission/` sono presenti `Server.java` e `Client.java` con metodi `main` che delegano rispettivamente a `RMIAuctionServer` e `RMIClient`, rispettando la richiesta di denominazione specifica dei file.
- Il pacchetto è buildabile con Maven (`mvn clean package`). L’artefatto risultante è `target/auction-system-1.0-SNAPSHOT.jar`.

---

### 4. Test e bug individuati
#### 4.1 Test automatizzati
- `RMIAuctionServerIT` (JUnit 5) esegue un test end-to-end: crea un registro RMI su porta libera, avvia il server con due oggetti, registra un client fittizio con callback `TestCallback` e verifica ricezione stato iniziale e accettazione di un’offerta valida con conseguente aggiornamento del miglior offerente.
- Il goal `mvn verify` lancia automaticamente questi test, garantendo copertura minima delle funzionalità core.

#### 4.2 Test manuali
- Riconnessione del client dopo arresto/riavvio del server: il client effettua lookup, re-invia la callback e riceve lo snapshot aggiornato.
- Collisione di nickname: il secondo client che tenta di registrarsi con lo stesso nome riceve immediatamente `AuctionException` con messaggio “Nickname già in uso”.
- Chat con tre client simultanei: i messaggi vengono visualizzati da tutti in ordine, includendo autore entro un formato chiaro `[nickname] messaggio`.
- Fine della coda di oggetti: quando l’ultimo round termina, il server diffonde l’annuncio e i nuovi client ricevano subito `AuctionState` con `active=false`.

#### 4.3 Bug emersi e risolti
1. **Riconnessione** – inizialmente il client, dopo un’interruzione, riceveva “Nickname già in uso” perché la precedente sessione era ancora registrata. Fix: il client tenta `unregisterClient` prima del nuovo `registerClient`, rendendo l’operazione idempotente.
2. **Durata round nei test** – i test automatici risultavano lenti con oggetti da 120 s. Fix: gli item usati nel test hanno durata 10 s, accelerando la pipeline CI.

---

### 5. Confronto RMI vs TCP
| Aspetto | Soluzione TCP | Soluzione RMI |
|---------|---------------|---------------|
| Protocollo | Testuale proprietario, parsing manuale | Interfacce a oggetti, serializzazione automatica |
| Aggiornamenti push | Implementati via broadcast di stringhe | Nativi tramite callback (`ClientCallback`) |
| Gestione errori | Controllo manuale delle `IOException` | Eccezioni remote gestite centralmente (`RemoteException`, `AuctionException`) |
| Robustezza | Nessun auto-reconnect; disconnessione manuale dei client | Riconnessione automatica; rimozione client in caso di callback fallita |
| Deployment | Nessun registry; semplice ma meno modulare | Richiede avvio di Registry RMI, ma semplifica il discovery e l’estensione |
| Testabilità | Difficile simulare round completi senza tool esterni | JUnit esegue round completi via stub RemoteObject |

In sintesi, RMI introduce un overhead di configurazione (registry, policy di sicurezza eventuale) ma riduce drasticamente il codice dedicato alla comunicazione, migliora la robustezza (reconnect e gestione automatica delle disconnessioni) e rende il sistema modulare. Inoltre, la rappresentazione a oggetti dello stato facilita l’estensione futura (es. interfaccia grafica o persistenza su database).

---

### 6. Conclusioni e lavoro futuro
La migrazione a RMI soddisfa tutti i requisiti del compito:
- progettazione documentata con diagrammi testuali e descrizione dei flussi;
- implementazione completa di server e client robusti;
- campagna di test automatica/manuale con bug documentati e corretti;
- discussione delle differenze rispetto alla versione TCP.

Per sviluppi futuri si suggerisce:
- introdurre autenticazione (es. token, firmatura nickname) per impedire impersonificazione;
- integrare un’interfaccia grafica Swing/JavaFX che sfrutti gli stessi DTO;
- automatizzare test di carico (simulazione di più client) per valutare latenza e scalabilità del broadcast RMI;
- esplorare la persistenza su database per tenere traccia delle aste concluse e usi successivi.

Questa relazione può essere allegata alla consegna insieme ai file `Client.java` e `Server.java` RMI-ready, comprimendo il tutto in `RMIchat.zip` come richiesto.


