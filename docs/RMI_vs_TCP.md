## Confronto tra versione TCP e versione RMI

### Architettura e flussi
- **TCP**: comunicazione full-duplex gestita manualmente con socket bloccanti. Il server mantiene un thread per client (`ClientHandler`) e implementa autonomamente il protocollo testuale (`JOIN`, `MSG`, `BID`, ...).
- **RMI**: il trasporto è demandato al runtime RMI; il server espone metodi remoti (`AuctionService`) e riceve eventi push tramite callback (`ClientCallback`). Non è necessario definire un protocollo testuale né gestire stream manualmente.

### Gestione dello stato e sincronizzazione
- **TCP**: il server serializza l’accesso allo stato usando `synchronized`. Gli aggiornamenti verso i client avvengono via broadcast di stringhe; eventuali errori di rete vanno gestiti manualmente.
- **RMI**: lo stato viene incapsulato in DTO serializzabili (`AuctionState`, `BidOutcome`). La sincronizzazione usa un `ReentrantLock`, mentre la propagazione verso i client è realizzata tramite invocazioni di metodi remoti. Se una callback fallisce, il server rimuove automaticamente il client dalla mappa dei partecipanti.

### Robustezza ai guasti
- **TCP**: la perdita della connessione richiede la chiusura manuale del socket e la rimozione del client. Il client non dispone di un meccanismo integrato di riconnessione.
- **RMI**: il client intercetta `RemoteException` e tenta la riconnessione con backoff, rieseguendo `registerClient`. Il server, grazie alle callback, può individuare i client non raggiungibili e notificarne la disconnessione agli altri.

### Miglioramenti introdotti con RMI
- **Bidirezionalità nativa**: le callback eliminano la necessità di protocolli proprietari per gli aggiornamenti push.
- **Modello a oggetti**: i DTO serializzabili evitano parsing manuale di stringhe e riducono gli errori di formattazione.
- **Port fallback automatizzato**: il server tenta più porte sia per il registry RMI sia per l’esposizione del servizio, semplificando la messa in servizio.
- **Testabilità**: l’introduzione di `RMIAuctionServerIT` consente di validare automaticamente l’intero flusso (registro → callback → esito offerta), cosa più complessa con socket raw.

### Svantaggi / trade-off
- **Maggiore complessità di deployment**: è necessario un registry RMI e politiche di sicurezza se si distribuisce su host diversi.
- **Serializzazione**: i DTO devono essere compatibili tra client e server; modifiche alle classi richiedono attenzione alla compatibilità (ad es. `serialVersionUID`).
- **Dipendenza dal runtime RMI**: in ambienti con firewall restrittivi RMI può richiedere configurazioni aggiuntive (porte apertamente note, policy file).

### Conclusione
La migrazione a RMI riduce il codice di infrastruttura legato alla gestione delle socket e migliora la resilienza lato client/server, a fronte di requisiti di deployment leggermente più rigidi. La struttura modulare (interfacce, DTO, callback) rende inoltre più semplice estendere il sistema con nuove funzionalità o integrare un’interfaccia grafica.


