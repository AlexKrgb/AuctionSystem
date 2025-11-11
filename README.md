# Auction System – TCP & Java RMI

[![Java](https://img.shields.io/badge/Java-21+-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/build-Maven-blue.svg)](https://maven.apache.org/)
[![Tests](https://img.shields.io/badge/tests-JUnit5-brightgreen.svg)]()

---

Questa repository contiene due implementazioni di un sistema d’asta online sincrono:

- **Versione originale TCP**, basata su socket bloccanti (`it.unibz.auction.Server` e `Client`).
- **Nuova versione Java RMI**, che sfrutta callback remoti per notificare in tempo reale i client (`it.unibz.auction.rmi.*`).

Entrambe le varianti condividono i requisiti funzionali del primo assignment (chat, offerte concorrenti, gestione turni di asta), ma la versione RMI introduce un modello di interazione più semplice e robusto rispetto a guasti di rete e di processo.

Per una descrizione dettagliata dell’architettura RMI e del confronto con la soluzione TCP, consultare:

- `docs/RMI_Design.md` – progettazione e flussi di interazione.
- `docs/RMI_Test_Report.md` – strategia di test e bug riscontrati.
- `docs/RMI_vs_TCP.md` – analisi delle differenze rispetto alla versione socket.

---

## Struttura del progetto

```
AuctionSystem/
│
├── docs/
│   ├── Relazione_Tecnica.pdf
│   ├── RMI_Design.md
│   ├── RMI_Test_Report.md
│   └── RMI_vs_TCP.md
│
├── src/
│   ├── main/java/it/unibz/auction/
│   │   ├── Client.java                 # client TCP legacy
│   │   └── Server.java                 # server TCP legacy
│   └── main/java/it/unibz/auction/rmi/
│       ├── AuctionService.java         # interfaccia remota
│       ├── ClientCallback.java         # callback lato client
│       ├── dto/
│       │   ├── AuctionItem.java
│       │   ├── AuctionState.java
│       │   └── BidOutcome.java
│       ├── exceptions/
│       │   └── AuctionException.java
│       ├── RMIClient.java              # client console basato su RMI
│       └── RMIAuctionServer.java       # server RMI con gestione round
│
├── src/test/java/it/unibz/auction/rmi/
│   └── RMIAuctionServerIT.java         # test end-to-end RMI
├── pom.xml
└── README.md
```

---

## Requisiti

- Java 21+
- Maven 3.9+ (per build e test)
- Porta TCP disponibile per il registry RMI (default: `5099`)

---

## Build e test

```bash
mvn clean verify
```

Il goal `verify` compila progetto, esegue i test JUnit5 (incluso il test end-to-end del server RMI) e produce il jar eseguibile in `target/auction-system-1.0-SNAPSHOT.jar`.

---

## Esecuzione – Versione Java RMI

### 1. Avvio del server RMI

```bash
java -cp target/auction-system-1.0-SNAPSHOT.jar it.unibz.auction.rmi.RMIAuctionServer [portaRegistry] [nomeServizio] [tentativiPorta]
```

- `portaRegistry` (default `5099`): porta del registro RMI.
- `nomeServizio` (default `AuctionService`): nome con cui viene pubblicato il servizio.
- `tentativiPorta` (default `5`): quanti fallback tentare in caso di conflitto di porta.

Il server carica una coda di oggetti d’asta predefiniti, pianifica automaticamente i round e invia notifiche push ai client registrati.

### 2. Avvio del client RMI

```bash
java -cp target/auction-system-1.0-SNAPSHOT.jar it.unibz.auction.rmi.RMIClient [host] [portaRegistry] [nomeServizio]
```

All’avvio viene richiesto un nickname (validato), dopodiché il client:

- riceve gli aggiornamenti sullo stato tramite callback (`onAuctionUpdate`);
- visualizza i messaggi broadcast del server (`onSystemMessage`);
- riceve l’esito delle offerte (`onBidOutcome`);
- gestisce automaticamente la riconnessione con re-registrazione in caso di `RemoteException`.

#### Comandi lato client

| Comando         | Descrizione                                              |
|-----------------|----------------------------------------------------------|
| `BID <valore>`  | Effettua un’offerta (>= prezzo corrente + incremento)    |
| `MSG <testo>`   | Invia un messaggio nella chat condivisa                  |
| `/info`         | Richiede lo snapshot attuale dell’asta                   |
| `/help`         | Mostra i comandi disponibili                             |
| `/quit`         | Deregistra il client e termina l’applicazione            |

---

## Esecuzione – Versione TCP legacy

Resta disponibile per riferimento storico. Compilando il progetto è possibile avviare il server e i client originali:

```bash
java -cp target/auction-system-1.0-SNAPSHOT.jar it.unibz.auction.Server 5000
java -cp target/auction-system-1.0-SNAPSHOT.jar it.unibz.auction.Client 127.0.0.1 5000
```

La nuova implementazione RMI non dipende dalla precedente, ma riutilizza la stessa logica di business (lista oggetti, regole di incremento, durata round).

---

## Documentazione di progetto

- `Relazione_Tecnica.pdf` – documentazione originale dell’implementazione TCP.
- `RMI_Design.md` – architettura, flussi RMI, strategie di robustezza.
- `RMI_Test_Report.md` – casi di test automatici/manuali e bug individuati.
- `RMI_vs_TCP.md` – discussione sui cambiamenti, vantaggi e svantaggi dell’adozione di RMI.

---

## Autori

- **Andrea Zicarelli**
- **Alexei Karavan**  
  *Corso di Reti di Calcolatori – Università di Bolzano (A.A. 2025/2026)*

--- 

## Licenza

Il progetto è destinato a scopi accademici e didattici.  
La modifica o distribuzione del codice è permessa citando gli autori originali.