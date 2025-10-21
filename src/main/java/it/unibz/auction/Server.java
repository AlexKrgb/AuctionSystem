package it.unibz.auction;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public class Server {
    private final int port;
    private final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();
    private final Queue<AuctionItem> items = new ArrayDeque<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private final Object auctionLock = new Object();
    private AuctionItem currentItem;
    private double currentPrice;
    private String topBidder;

    public Server(int port) { this.port = port; }

    public static void main(String[] args) throws Exception {
        int port = (args.length > 0) ? Integer.parseInt(args[0]) : 5000;
        Server s = new Server(port);
        s.loadItems(); // TODO: carica lista
        s.start();
    }

    private void loadItems() {
        items.add(new AuctionItem("Laptop", "Ultrabook 14\"", 500.00, 10.00));
        items.add(new AuctionItem("Cuffie", "Over-ear", 50.00, 5.00));
        // TODO: aggiungi altri
    }

    private void start() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server on port " + port);
            startNextRound(); // avvia prima asta
            while (true) {
                Socket socket = serverSocket.accept();
                ClientHandler h = new ClientHandler(socket, this);
                clients.add(h);
                new Thread(h).start();
            }
        }
    }

    private void startNextRound() {
        synchronized (auctionLock) {
            currentItem = items.poll();
            if (currentItem == null) {
                broadcast("SYSTEM Fine oggetti. Asta terminata.");
                scheduler.shutdown();
                return;
            }
            currentPrice = currentItem.startPrice;
            topBidder = null;
            broadcast("SYSTEM Nuova asta: " + currentItem.name + " — " + currentItem.desc);
            sendInfoToAll();
            scheduler.schedule(this::endRound, 120, TimeUnit.SECONDS); // ~2 minuti
        }
    }

    private void endRound() {
        synchronized (auctionLock) {
            String winner = (topBidder == null) ? "Nessuno" : topBidder;
            broadcast("WIN " + currentItem.name + "|" + String.format("%.2f", currentPrice) + "|" + winner);
        }
        startNextRound();
    }

    void sendInfoToAll() {
        synchronized (auctionLock) {
            String info = String.format("INFO %s|%.2f|%.2f",
                    currentItem.name, currentPrice, currentItem.minInc);
            broadcast(info);
        }
    }

    void broadcast(String line) {
        for (ClientHandler c : clients) c.send(line);
    }

    boolean registerBid(String bidder, double value, ClientHandler src) {
        synchronized (auctionLock) {
            double min = currentPrice + currentItem.minInc;
            if (value >= min) {
                currentPrice = value;
                topBidder = bidder;
                broadcast("BIDOK " + String.format("%.2f", currentPrice) + "|" + topBidder);
                sendInfoToAll();
                return true;
            } else {
                src.send("BIDFAIL Offerta minima " + String.format("%.2f", min));
                return false;
            }
        }
    }

    void remove(ClientHandler h, String nickname) {
        clients.remove(h);
        if (nickname != null) broadcast("SYSTEM " + nickname + " ha lasciato l'asta");
    }

    static class AuctionItem {
        final String name, desc;
        final double startPrice, minInc;
        AuctionItem(String n, String d, double s, double m){ name=n; desc=d; startPrice=s; minInc=m; }
    }

    class ClientHandler implements Runnable {
        private final Socket socket;
        private final Server server;
        private PrintWriter out;
        private BufferedReader in;
        private String nickname;

        ClientHandler(Socket s, Server srv) { this.socket = s; this.server = srv; }

        public void run() {
            try (Socket s = socket) {
                out = new PrintWriter(new OutputStreamWriter(s.getOutputStream()), true);
                in  = new BufferedReader(new InputStreamReader(s.getInputStream()));
                out.println("SYSTEM Benvenuto! Inserisci: JOIN <nickname>");
                String line;
                while ((line = in.readLine()) != null) {
                    handle(line.trim());
                }
            } catch (IOException ignored) {
            } finally {
                server.remove(this, nickname);
            }
        }

        void handle(String line) {
            if (line.isEmpty()) return;
            if (line.startsWith("JOIN ")) {
                String nick = line.substring(5).trim();
                if (nick.isEmpty() || nick.contains("|")) { out.println("SYSTEM Nick non valido"); return; }
                // verifica unicità
                boolean exists = clients.stream().anyMatch(c -> c != this && nick.equals(c.nickname));
                if (exists) { out.println("SYSTEM Nick in uso"); return; }
                this.nickname = nick;
                out.println("SYSTEM Ciao " + nickname);
                sendInfoToAll();
                broadcast("SYSTEM " + nickname + " è entrato");
            } else if ("QUIT".equals(line)) {
                send("SYSTEM Arrivederci");
                try { socket.close(); } catch (IOException ignored) {}
            } else if (line.startsWith("MSG ")) {
                if (nickname == null) { out.println("SYSTEM Fai prima JOIN"); return; }
                broadcast(nickname + ": " + line.substring(4));
            } else if (line.startsWith("BID ")) {
                if (nickname == null) { out.println("SYSTEM Fai prima JOIN"); return; }
                try {
                    double val = Double.parseDouble(line.substring(4).trim());
                    registerBid(nickname, val, this);
                } catch (NumberFormatException e) { out.println("BIDFAIL Valore non numerico"); }
            } else {
                out.println("SYSTEM Comando sconosciuto");
            }
        }

        void send(String msg) { if (out != null) out.println(msg); }
    }
}
