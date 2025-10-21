# TCP Auction – Online Auction System via TCP

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
TCP-Auction/
│
├── src/
│   ├── Server.java      # Gestione connessioni, offerte e messaggi
│   ├── Client.java      # Interfaccia testuale e invio comandi
│   └── utils/           # (eventuali classi di supporto)
│
├── docs/
│   └── Relazione_Tecnica.pdf
│
└── README.md            # Documentazione del progetto
```

---

## Requisiti
- **Java 17+**
- IDE a scelta (IntelliJ, Eclipse, VS Code, ecc.)

---

## Esecuzione

### Avvio del server
```bash
java Server
```

### Avvio di un client
```bash
java Client <indirizzo_server>
```

All’avvio, l’utente sceglie un nickname e può:
- Scrivere messaggi nella chat pubblica
- Inviare offerte con `/bid <importo>`
- Uscire dall’asta con `/quit`

---

## Autori
- Andrea Zicarelli
- Alexei Karavan
Corso di *Reti di Calcolatori*, A.A. 2025/2026

---

## Licenza
Questo progetto è distribuito per scopi accademici e didattici.  
L’uso o la modifica del codice è consentita previa citazione dell’autore originale.
