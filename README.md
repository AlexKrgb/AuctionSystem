# TCP Auction – Online Auction System via TCP

[![Java](https://img.shields.io/badge/Java-21+-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/build-Maven-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/license-Academic-lightgrey.svg)](LICENSE)
[![Status](https://img.shields.io/badge/status-Active-success.svg)]()

---

**TCP Auction** è un’applicazione client-server scritta in **Java** che simula un sistema di **asta online sincrona**, ispirato al funzionamento di piattaforme come *eBay*, ma basata su comunicazione diretta tramite **socket TCP**.

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
- **Java 21 o superiore**
- *(Opzionale)* **Apache Maven**  
  → utile per compilare e creare i jar automaticamente
- Sistema operativo: macOS / Linux / Windows

---

## Compilazione ed esecuzione

### Opzione 1 – Con Maven

Assicurati di essere nella cartella principale del progetto (`AuctionSystem/`).

#### Compilazione
```bash
mvn clean package
```

#### Avvio del Server
```bash
java -cp target/AuctionSystem-1.0-SNAPSHOT.jar it.unibz.auction.Server 5000
```

> Se la porta 5000 è occupata, il server proverà automaticamente la successiva (5001, 5002, …).

#### Avvio del Client
In un altro terminale:
```bash
java -cp target/AuctionSystem-1.0-SNAPSHOT.jar it.unibz.auction.Client 127.0.0.1 5000
```

---

### Opzione 2 – Senza Maven (solo Java)

Dalla cartella principale (`AuctionSystem/`):

#### Compila i file sorgenti
```bash
javac src/main/java/it/unibz/auction/*.java
```

#### Avvia il server
```bash
java -cp src/main/java it.unibz.auction.Server 5000
```

#### Avvia uno o più client
```bash
java -cp src/main/java it.unibz.auction.Client 127.0.0.1 5000
```

---

## Comandi disponibili nel client

| Comando | Descrizione |
|----------|-------------|
| `JOIN <nick>` | Entra nell’asta con un nickname unico |
| `MSG <testo>` | Invia un messaggio nella chat pubblica |
| `BID <valore>` | Effettua un’offerta sull’oggetto corrente |
| `/info` | Mostra le informazioni attuali sull’asta e la porta attiva |
| `/help` | Mostra la lista dei comandi disponibili |
| `QUIT` | Disconnette il client in modo sicuro |

---

## Esempio di sessione

```
✅ Connesso al server 127.0.0.1:5000
──────────────────────────────
COMANDI DISPONIBILI:
──────────────────────────────
JOIN alexei
SYSTEM Ciao alexei
──────────────────────────────
👤 Utente: alexei
🏷️ Oggetto in asta: Laptop
💰 Prezzo attuale: 500.00
⭐ Miglior offerente: Nessuno
──────────────────────────────
(Barra aggiornata automaticamente ogni 10 secondi)
```

---

## Troubleshooting

### Errore: `java.net.BindException: Address already in use`
→ La porta è già occupata.  
Soluzioni:
- Usa una porta diversa (es. `5050`)
- Oppure termina il processo che la usa:
  ```bash
  lsof -i :5000
  kill -9 <PID>
  ```

### Errore: `Could not find or load main class`
→ Sei nella directory sbagliata.  
Esegui sempre i comandi **dalla cartella principale del progetto**:
```bash
java -cp src/main/java it.unibz.auction.Server 5000
```

### Errore: `mvn command not found`
→ Maven non è installato o non è nel PATH.  
Su macOS:
```bash
brew install maven
```
Verifica poi con:
```bash
mvn -v
```

---

## Autori

- **Andrea Zicarelli**
- **Alexei Karavan**  
  *Corso di Reti di Calcolatori – Università di Bolzano (A.A. 2025/2026)*

---

## Licenza

Questo progetto è distribuito esclusivamente per scopi **accademici e didattici**.  
L’utilizzo, la modifica o la distribuzione del codice sono consentiti previa citazione degli autori originali.