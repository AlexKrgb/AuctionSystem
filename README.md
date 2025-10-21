# TCP Auction – Online Auction System via TCP

[![Java](https://img.shields.io/badge/Java-21+-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/build-Maven-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/license-Academic-lightgrey.svg)](LICENSE)
[![Status](https://img.shields.io/badge/status-Active-success.svg)]()

---

**TCP Auction** è un’applicazione client-server scritta in **Java** che simula un sistema di **asta online sincrona**, ispirato al funzionamento di piattaforme come *eBay*, ma basata su comunicazione diretta tramite **socket TCP**.  
Il progetto è stato sviluppato come parte dell’assegnamento per il corso di *Reti di Calcolatori* (Autunno–Inverno 2025/2026).

---

## Descrizione del progetto
Il sistema permette a molteplici client di connettersi a un server centrale per partecipare a un’asta online in tempo reale.  
Oltre a effettuare offerte, gli utenti possono comunicare tra loro in una chat condivisa.  
Ogni client è identificato da un nickname e può entrare o uscire liberamente dall’asta, purché il server sia attivo.

---

## Funzionalità principali
- **Architettura client-server basata su TCP**
- **Connessioni multiple simultanee**: più client possono partecipare alla stessa asta
- **Chat condivisa**: tutti i messaggi vengono inoltrati a ogni client connesso
- **Sistema d’asta sincrono**:
  - Ogni oggetto ha un prezzo di partenza e un incremento minimo
  - I client possono effettuare offerte tramite comando `/bid <importo>`
  - Il server valida e diffonde in tempo reale l’offerta più alta
- **Gestione sessione**:
  - Comando `/quit` per disconnessione controllata
  - Log server che registra azioni e messaggi
- **Gestione robusta degli errori**: crash o disconnessioni di un client non compromettono il sistema
- **Asta a tempo**: ogni sessione termina dopo un intervallo predefinito (es. 2 minuti), e l’oggetto viene assegnato al miglior offerente

---

## Estensioni opzionali
- Gestione dell’uscita del miglior offerente prima della chiusura dell’asta
- Possibile versione alternativa basata su **UDP** per confronto delle prestazioni

---

## Struttura del progetto
```
AuctionSystem/
│
├── docs/
│   └── Relazione_Tecnica.pdf
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── it/
│   │   │       └── unibz/
│   │   │           └── auction/
│   │   │               ├── Client.java      # Gestione del client TCP
│   │   │               └── Server.java      # Gestione del server e delle aste
│   │   └── resources/
│   │
│   └── test/
│       └── java/
│
├── pom.xml
└── README.md
```

---

## Requisiti
- **Java 21+**
- **Apache Maven**
- IDE a scelta (IntelliJ, Eclipse, VS Code, ecc.)

---

## Compilazione ed esecuzione

### Compilazione con Maven
```bash
mvn clean package
```

### Avvio del server
```bash
java -cp target/AuctionSystem-1.0-SNAPSHOT.jar it.unibz.auction.Server
```

### Avvio di un client
```bash
java -cp target/AuctionSystem-1.0-SNAPSHOT.jar it.unibz.auction.Client <indirizzo_server>
```

All’avvio, l’utente sceglie un nickname e può:
- Scrivere messaggi nella chat pubblica
- Inviare offerte con `/bid <importo>`
- Uscire dall’asta con `/quit`

---

## Autori
- **Andrea Zicarelli**
- **Alexei Karavan**  
  Corso di *Reti di Calcolatori*, A.A. 2025/2026

---

## Licenza
Questo progetto è distribuito per scopi accademici e didattici.  
L’uso o la modifica del codice è consentita previa citazione degli autori originali.
