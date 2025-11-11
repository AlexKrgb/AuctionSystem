package it.unibz.auction.rmi;

import it.unibz.auction.rmi.dto.AuctionState;
import it.unibz.auction.rmi.dto.BidOutcome;
import it.unibz.auction.rmi.exceptions.AuctionException;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Client console per il sistema d'asta RMI.
 */
public class RMIClient {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final String host;
    private final int port;
    private final String bindingName;
    private final AtomicReference<AuctionService> serviceRef = new AtomicReference<>();
    private final AtomicReference<AuctionState> stateRef = new AtomicReference<>();
    private final AtomicReference<String> nicknameRef = new AtomicReference<>();
    private final AtomicReference<ClientListener> listenerRef = new AtomicReference<>();

    private RMIClient(String host, int port, String bindingName) {
        this.host = host;
        this.port = port;
        this.bindingName = bindingName;
    }

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 5099;
        String binding = args.length > 2 ? args[2] : "AuctionService";

        RMIClient client = new RMIClient(host, port, binding);
        client.run();
    }

    private void run() throws Exception {
        Scanner scanner = new Scanner(System.in);
        System.out.println("=== Client Asta RMI ===");
        System.out.printf("Connessione a %s:%d (servizio: %s)%n", host, port, bindingName);

        String nickname = promptNickname(scanner);
        nicknameRef.set(nickname);

        ClientListener listener = new ClientListener();
        listenerRef.set(listener);
        connectAndRegister(nickname, listener, true);

        printHelp();

        while (true) {
            System.out.print("> ");
            String line = scanner.nextLine();
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            if ("/help".equalsIgnoreCase(trimmed)) {
                printHelp();
                continue;
            }
            if ("/info".equalsIgnoreCase(trimmed)) {
                printCurrentState();
                continue;
            }
            if ("/quit".equalsIgnoreCase(trimmed)) {
                gracefulShutdown();
                break;
            }
            if (trimmed.toUpperCase().startsWith("BID ")) {
                handleBid(trimmed.substring(4).trim());
                continue;
            }
            if (trimmed.toUpperCase().startsWith("MSG ")) {
                handleMessage(trimmed.substring(4).trim());
                continue;
            }
            System.out.println("Comando sconosciuto. Digita /help per la lista.");
        }
    }

    private void handleBid(String amountString) {
        try {
            double amount = Double.parseDouble(amountString.replace(',', '.'));
            invokeVoidWithReconnect(service -> service.submitBid(nicknameRef.get(), amount));
        } catch (NumberFormatException ex) {
            System.out.println("Importo non valido. Usa un numero.");
        } catch (AuctionException ex) {
            System.out.printf("Offerta rifiutata: %s%n", ex.getMessage());
        } catch (RemoteException ex) {
            System.out.printf("Errore di rete: %s%n", ex.getMessage());
        }
    }

    private void handleMessage(String message) {
        try {
            invokeVoidWithReconnect(service -> service.sendChatMessage(nicknameRef.get(), message));
        } catch (AuctionException ex) {
            System.out.printf("Messaggio non inviato: %s%n", ex.getMessage());
        } catch (RemoteException ex) {
            System.out.printf("Errore di rete: %s%n", ex.getMessage());
        }
    }

    private void printCurrentState() {
        try {
            AuctionState state = invokeWithReconnect(AuctionService::getCurrentState);
            if (state == null || !state.active()) {
                System.out.println("Nessuna asta attiva al momento.");
            } else {
                renderState(state);
            }
        } catch (AuctionException ex) {
            System.out.printf("Impossibile ottenere lo stato: %s%n", ex.getMessage());
        } catch (RemoteException ex) {
            System.out.printf("Errore di rete: %s%n", ex.getMessage());
        }
    }

    private void renderState(AuctionState state) {
        System.out.println("──────────────────────────────");
        System.out.printf("Oggetto: %s%n", Optional.ofNullable(state.itemName()).orElse("N/D"));
        System.out.printf("Prezzo attuale: %.2f €%n", state.currentPrice());
        System.out.printf("Incremento minimo: %.2f €%n", state.minIncrement());
        System.out.printf("Miglior offerente: %s%n", Optional.ofNullable(state.topBidder()).orElse("Nessuno"));
        if (state.roundEndTime() != null) {
            System.out.printf("Termine round alle: %s%n", TIME_FORMATTER.format(state.roundEndTime()));
        }
        System.out.println("──────────────────────────────");
    }

    private String promptNickname(Scanner scanner) {
        String nickname;
        do {
            System.out.print("Inserisci il tuo nickname (3-16 caratteri alfanumerici o '_'): ");
            nickname = scanner.nextLine();
        } while (nickname == null || nickname.isBlank());
        return nickname.trim();
    }

    private void printHelp() {
        System.out.println("──────────────────────────────");
        System.out.println("COMANDI DISPONIBILI:");
        System.out.println("──────────────────────────────");
        System.out.println("BID <valore>      → Effettua un'offerta");
        System.out.println("MSG <testo>       → Invia un messaggio in chat");
        System.out.println("/info             → Mostra lo stato attuale dell'asta");
        System.out.println("/help             → Mostra questa schermata");
        System.out.println("/quit             → Esci dal sistema");
        System.out.println("──────────────────────────────");
    }

    private void connectAndRegister(String nickname, ClientListener listener, boolean initial)
            throws RemoteException, AuctionException {
        AuctionService service;
        try {
            service = lookupServiceWithRetry();
        } catch (NotBoundException ex) {
            throw new RemoteException("Servizio RMI non trovato: " + bindingName, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RemoteException("Interrotto durante la riconnessione", ex);
        }
        try {
            service.unregisterClient(nickname);
        } catch (RemoteException ignored) {
        }
        service.registerClient(nickname, listener);
        serviceRef.set(service);
        if (initial) {
            System.out.println("✅ Registrazione completata. In attesa di notifiche...");
        } else {
            System.out.println("♻️ Riconnesso al server e registrato nuovamente.");
        }
        AuctionState state = service.getCurrentState();
        stateRef.set(state);
        if (state != null) {
            renderState(state);
        }
    }

    private AuctionService lookupServiceWithRetry() throws RemoteException, NotBoundException, InterruptedException {
        RemoteException lastRemote = null;
        NotBoundException lastBinding = null;
        int attempts = 0;
        while (attempts < 5) {
            try {
                Registry registry = LocateRegistry.getRegistry(host, port);
                return (AuctionService) registry.lookup(bindingName);
            } catch (RemoteException ex) {
                lastRemote = ex;
                attempts++;
                Thread.sleep(Duration.ofSeconds(1).toMillis());
            } catch (NotBoundException ex) {
                lastBinding = ex;
                attempts++;
                Thread.sleep(Duration.ofSeconds(1).toMillis());
            }
        }
        if (lastBinding != null) {
            throw lastBinding;
        }
        throw lastRemote != null ? lastRemote : new RemoteException("Impossibile connettersi al servizio RMI");
    }

    private void invokeVoidWithReconnect(ServiceVoidCall call) throws RemoteException, AuctionException {
        invokeWithReconnect(service -> {
            call.call(service);
            return null;
        });
    }

    private <T> T invokeWithReconnect(ServiceCall<T> call) throws RemoteException, AuctionException {
        AuctionService service = serviceRef.get();
        Objects.requireNonNull(service, "Servizio non inizializzato");
        try {
            return call.call(service);
        } catch (RemoteException ex) {
            System.err.println("⚠️ Connessione persa. Tentativo di riconnessione...");
            try {
                ClientListener listener = listenerRef.get();
                if (listener == null) {
                    listener = new ClientListener();
                    listenerRef.set(listener);
                }
                connectAndRegister(nicknameRef.get(), listener, false);
            } catch (RemoteException | AuctionException reconnectEx) {
                throw new RemoteException("Riconnessione fallita", reconnectEx);
            }
            AuctionService newService = serviceRef.get();
            return call.call(newService);
        }
    }

    private void gracefulShutdown() {
        AuctionService service = serviceRef.get();
        if (service != null) {
            try {
                service.unregisterClient(nicknameRef.get());
            } catch (RemoteException ignored) {
            }
        }
        System.out.println("Arrivederci!");
    }

    private class ClientListener extends UnicastRemoteObject implements ClientCallback {
        private ClientListener() throws RemoteException {
            super();
        }

        @Override
        public void onSystemMessage(String message) {
            System.out.printf("[Sistema] %s%n", message);
        }

        @Override
        public void onAuctionUpdate(AuctionState state) {
            stateRef.set(state);
            renderState(state);
        }

        @Override
        public void onBidOutcome(BidOutcome outcome) {
            String status = outcome.accepted() ? "accettata" : "rifiutata";
            String reason = outcome.message() != null ? outcome.message() : "";
            System.out.printf("➡️ Offerta %s (%.2f €). %s%n", status, outcome.amount(), reason);
            if (outcome.stateSnapshot() != null) {
                renderState(outcome.stateSnapshot());
            }
        }
    }

    @FunctionalInterface
    private interface ServiceCall<T> {
        T call(AuctionService service) throws RemoteException, AuctionException;
    }

    @FunctionalInterface
    private interface ServiceVoidCall {
        void call(AuctionService service) throws RemoteException, AuctionException;
    }
}


