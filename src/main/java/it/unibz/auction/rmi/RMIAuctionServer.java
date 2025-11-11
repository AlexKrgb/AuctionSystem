package it.unibz.auction.rmi;

import it.unibz.auction.rmi.dto.AuctionItem;
import it.unibz.auction.rmi.dto.AuctionState;
import it.unibz.auction.rmi.dto.BidOutcome;
import it.unibz.auction.rmi.exceptions.AuctionException;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/**
 * Implementazione del server RMI dell'asta.
 */
public class RMIAuctionServer extends UnicastRemoteObject implements AuctionService {

    private static final Pattern NICKNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    private final Map<String, ClientSession> clients = new ConcurrentHashMap<>();
    private final Deque<AuctionItem> itemsQueue = new ArrayDeque<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Lock stateLock = new ReentrantLock();

    private AuctionItem currentItem;
    private double currentPrice;
    private String topBidder;
    private Instant roundEnd;
    private boolean activeRound;
    private ScheduledFuture<?> roundFuture;

    public RMIAuctionServer(Iterable<AuctionItem> seedItems) throws RemoteException {
        super();
        seedItems.forEach(itemsQueue::offer);
    }

    public static void main(String[] args) throws Exception {
        int basePort = args.length > 0 ? Integer.parseInt(args[0]) : 5099;
        String bindingName = args.length > 1 ? args[1] : "AuctionService";
        int maxAttempts = args.length > 2 ? Integer.parseInt(args[2]) : 5;

        Registry registry = createRegistryWithFallback(basePort, maxAttempts);
        RMIAuctionServer server = new RMIAuctionServer(defaultItems());
        registry.rebind(bindingName, server);
        System.out.printf("✅ Registry RMI attivo sulla porta %d. Servizio bindato come '%s'%n",
                registryPort(registry), bindingName);

        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(server::shutdown));
    }

    private static Registry createRegistryWithFallback(int basePort, int maxAttempts) throws RemoteException {
        RemoteException lastEx = null;
        for (int i = 0; i < maxAttempts; i++) {
            int port = basePort + i;
            try {
                Registry registry = LocateRegistry.createRegistry(port);
                registry.list(); // forza inizializzazione
                return new WrappedRegistry(registry, port);
            } catch (RemoteException ex) {
                lastEx = ex;
                System.err.printf("⚠️ Porta RMI %d occupata (%s).%n", port, ex.getMessage());
            }
        }
        throw lastEx != null ? lastEx : new RemoteException("Impossibile creare un registro RMI");
    }

    private static Iterable<AuctionItem> defaultItems() {
        return List.of(
                new AuctionItem("Laptop", "Ultrabook 14\" con SSD 1TB", 500.0, 10.0, 120),
                new AuctionItem("Cuffie", "Over-ear noise cancelling", 70.0, 5.0, 90),
                new AuctionItem("Smartwatch", "Resistente all'acqua, GPS integrato", 120.0, 8.0, 90)
        );
    }

    @Override
    public void registerClient(String nickname, ClientCallback callback) throws RemoteException, AuctionException {
        Objects.requireNonNull(callback, "callback nulla");
        String sanitizedNick = sanitizeNickname(nickname);

        if (clients.containsKey(sanitizedNick)) {
            throw new AuctionException("Nickname già in uso");
        }

        clients.put(sanitizedNick, new ClientSession(sanitizedNick, callback));
        System.out.printf("👤 Client registrato: %s (totale=%d)%n", sanitizedNick, clients.size());

        broadcastSystem(String.format("%s si è unito all'asta", sanitizedNick));

        AuctionState snapshot = getCurrentState();
        safeInvoke(callback, cb -> cb.onAuctionUpdate(snapshot));
    }

    @Override
    public void unregisterClient(String nickname) {
        Optional.ofNullable(nickname)
                .map(clients::remove)
                .ifPresent(session -> broadcastSystem(String.format("%s ha lasciato l'asta", session.nickname())));
    }

    @Override
    public AuctionState getCurrentState() {
        stateLock.lock();
        try {
            return snapshot();
        } finally {
            stateLock.unlock();
        }
    }

    @Override
    public void submitBid(String nickname, double amount) throws AuctionException {
        ClientSession session = clients.get(nickname);
        if (session == null) {
            throw new AuctionException("Client non registrato");
        }

        stateLock.lock();
        BidOutcome outcome;
        try {
            if (!activeRound || currentItem == null) {
                throw new AuctionException("Nessuna asta attiva in questo momento");
            }
            if (Double.isNaN(amount) || Double.isInfinite(amount) || amount <= 0) {
                throw new AuctionException("Importo non valido");
            }
            double minimumRequired = currentPrice + currentItem.minIncrement();
            if (amount < minimumRequired) {
                outcome = new BidOutcome(false, amount, minimumRequired,
                        "Offerta troppo bassa", snapshot());
            } else {
                currentPrice = amount;
                topBidder = nickname;
                outcome = new BidOutcome(true, amount, minimumRequired,
                        "Offerta accettata", snapshot());
            }
        } finally {
            stateLock.unlock();
        }

        safeInvoke(session.callback(), cb -> cb.onBidOutcome(outcome));
        if (outcome.accepted()) {
            broadcastAuctionUpdate(outcome.stateSnapshot());
            broadcastSystem(String.format("Nuova offerta da %s: %.2f €",
                    nickname, amount));
        }
    }

    @Override
    public void sendChatMessage(String nickname, String message) throws AuctionException {
        ClientSession session = clients.get(nickname);
        if (session == null) {
            throw new AuctionException("Client non registrato");
        }
        String sanitized = sanitizeMessage(message);
        if (sanitized.isBlank()) {
            throw new AuctionException("Messaggio vuoto");
        }
        broadcastSystem(String.format("[%s] %s", nickname, sanitized));
    }

    public void start() {
        stateLock.lock();
        try {
            scheduleNextRound();
        } finally {
            stateLock.unlock();
        }
    }

    public void shutdown() {
        System.out.println("🛑 Arresto server RMI...");
        scheduler.shutdownNow();
        try {
            UnicastRemoteObject.unexportObject(this, true);
        } catch (RemoteException ignored) {
        }
    }

    private void scheduleNextRound() {
        cancelExistingTimer();

        currentItem = itemsQueue.poll();
        if (currentItem == null) {
            activeRound = false;
            roundEnd = null;
            broadcastSystem("Asta terminata. Nessun altro oggetto disponibile.");
            broadcastAuctionUpdate(snapshot());
            return;
        }

        currentPrice = currentItem.startPrice();
        topBidder = null;
        activeRound = true;
        roundEnd = Instant.now().plusSeconds(currentItem.durationSeconds());

        broadcastSystem(String.format("Nuovo articolo: %s — %s (prezzo di partenza %.2f €, incremento minimo %.2f €)",
                currentItem.name(), currentItem.description(), currentItem.startPrice(), currentItem.minIncrement()));
        broadcastAuctionUpdate(snapshot());

        roundFuture = scheduler.schedule(this::completeCurrentRound,
                currentItem.durationSeconds(), TimeUnit.SECONDS);
    }

    private void completeCurrentRound() {
        stateLock.lock();
        try {
            if (!activeRound) {
                return;
            }
            activeRound = false;
            String winner = topBidder != null ? topBidder : "Nessuno";
            double finalPrice = currentPrice;
            AuctionItem expiredItem = currentItem;

            broadcastSystem(String.format("Round terminato: %s aggiudicato a %s per %.2f €",
                    expiredItem.name(), winner, finalPrice));

            broadcastAuctionUpdate(snapshot());

            scheduleNextRound();
        } finally {
            stateLock.unlock();
        }
    }

    private void cancelExistingTimer() {
        if (roundFuture != null) {
            roundFuture.cancel(false);
            roundFuture = null;
        }
    }

    private AuctionState snapshot() {
        if (currentItem == null) {
            return new AuctionState(null, null, 0, 0, null, null, false);
        }
        return new AuctionState(
                currentItem.name(),
                currentItem.description(),
                currentPrice,
                currentItem.minIncrement(),
                topBidder,
                activeRound ? roundEnd : null,
                activeRound
        );
    }

    private void broadcastAuctionUpdate(AuctionState state) {
        broadcast(clientCallback -> clientCallback.onAuctionUpdate(state));
    }

    private void broadcastSystem(String message) {
        broadcast(clientCallback -> clientCallback.onSystemMessage(message));
    }

    private void broadcast(CallbackInvoker invoker) {
        clients.values().forEach(session -> safeInvoke(session.callback(), invoker));
    }

    private void safeInvoke(ClientCallback callback, CallbackInvoker invoker) {
        try {
            invoker.invoke(callback);
        } catch (RemoteException ex) {
            clients.entrySet().stream()
                    .filter(entry -> entry.getValue().callback().equals(callback))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .ifPresent(nick -> {
                        clients.remove(nick);
                        System.err.printf("⚠️ Callback fallita. Client '%s' rimosso: %s%n", nick, ex.getMessage());
                        broadcastSystem(String.format("%s si è disconnesso (connessione persa)", nick));
                    });
        }
    }

    private static String sanitizeNickname(String raw) throws AuctionException {
        if (raw == null || raw.isBlank()) {
            throw new AuctionException("Nickname vuoto");
        }
        String trimmed = raw.trim();
        if (!NICKNAME_PATTERN.matcher(trimmed).matches()) {
            throw new AuctionException("Nickname non valido. Usa 3-16 caratteri alfanumerici o underscore.");
        }
        return trimmed;
    }

    private static String sanitizeMessage(String message) {
        return message == null ? "" : message.replaceAll("[\\r\\n]+", " ").trim();
    }

    private static int registryPort(Registry registry) {
        if (registry instanceof WrappedRegistry wr) {
            return wr.port();
        }
        return 1099;
    }

    private record ClientSession(String nickname, ClientCallback callback) {}

    @FunctionalInterface
    private interface CallbackInvoker {
        void invoke(ClientCallback callback) throws RemoteException;
    }

    /**
     * Wrapper semplice per ricordare la porta del registry creato.
     */
    private record WrappedRegistry(Registry delegate, int port) implements Registry {
        @Override
        public void bind(String name, java.rmi.Remote obj) throws RemoteException, java.rmi.AlreadyBoundException {
            delegate.bind(name, obj);
        }

        @Override
        public void unbind(String name) throws RemoteException, java.rmi.NotBoundException {
            delegate.unbind(name);
        }

        @Override
        public void rebind(String name, java.rmi.Remote obj) throws RemoteException {
            delegate.rebind(name, obj);
        }

        @Override
        public java.rmi.Remote lookup(String name) throws RemoteException, java.rmi.NotBoundException {
            return delegate.lookup(name);
        }

        @Override
        public String[] list() throws RemoteException {
            return delegate.list();
        }
    }
}


