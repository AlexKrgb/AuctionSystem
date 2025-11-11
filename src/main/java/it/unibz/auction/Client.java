package it.unibz.auction;

import java.io.*;
import java.net.*;
import java.util.Scanner;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public class Client {
    private static final AtomicReference<String> currentItem = new AtomicReference<>("N/D");
    private static final AtomicReference<String> currentPrice = new AtomicReference<>("0.00");
    private static final AtomicReference<String> currentTopBidder = new AtomicReference<>("Nessuno");
    private static final AtomicReference<String> nickname = new AtomicReference<>("Anonimo");

    public static void main(String[] args) throws Exception {
        String host = (args.length > 0) ? args[0] : "127.0.0.1";
        int port = (args.length > 1) ? Integer.parseInt(args[1]) : 5000;

        try (Socket socket = new Socket(host, port)) {
            System.out.println("Connesso al server " + host + ":" + port);

            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

            // Thread per la lettura dei messaggi dal server
            Thread reader = new Thread(() -> {
                try {
                    String line;
                    while ((line = in.readLine()) != null) {
                        processServerMessage(line);
                    }
                } catch (IOException ignored) {}
                System.out.println("Connessione chiusa dal server.");
                System.exit(0);
            });
            reader.setDaemon(true);
            reader.start();

            // Thread per aggiornare automaticamente la barra di stato ogni 10 secondi
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.scheduleAtFixedRate(Client::printStatusBar, 10, 10, TimeUnit.SECONDS);

          
            try (Scanner sc = new Scanner(System.in)) {
                printHelp();

                while (true) {
                    String cmd = sc.nextLine().trim();

                    // --- /help locale ---
                    if (cmd.equalsIgnoreCase("/help")) {
                        printHelp();
                        continue;
                    }

                    // --- /info locale ---
                    if (cmd.equalsIgnoreCase("/info")) {
                        out.println("INFO_REQUEST");
                        continue;
                    }

                    // --- Aggiorna nickname se JOIN ---
                    if (cmd.toUpperCase().startsWith("JOIN ")) {
                        String[] parts = cmd.split("\\s+", 2);
                        if (parts.length > 1) nickname.set(parts[1]);
                    }

                    out.println(cmd);
                    if ("QUIT".equalsIgnoreCase(cmd)) {
                        scheduler.shutdownNow(); // ferma l’aggiornamento automatico
                        break;
                    }
                }
            }
        }
    }

    /**
     * Gestisce i messaggi provenienti dal server e aggiorna la barra di stato
     */
    private static void processServerMessage(String line) {
        if (line.startsWith("INFO ")) {
            // formato: INFO item|prezzo|minInc|topBidder (ultimo opzionale)
            String data = line.substring(5).trim();
            String[] parts = data.split("\\|");

            if (parts.length >= 3) {
                currentItem.set(parts[0].trim());
                currentPrice.set(parts[1].trim());
                if (parts.length >= 4)
                    currentTopBidder.set(parts[3].trim());
                else
                    currentTopBidder.set("Nessuno");
            }
            printStatusBar();
        } else {
            System.out.println(line);
        }
    }

    /**
     * Mostra la lista dei comandi disponibili
     */
    private static void printHelp() {
        System.out.println("──────────────────────────────");
        System.out.println("COMANDI DISPONIBILI:");
        System.out.println("──────────────────────────────");
        System.out.println("JOIN <nick>       → Entra nell'asta con un nickname unico");
        System.out.println("MSG <testo>       → Invia un messaggio nella chat pubblica");
        System.out.println("BID <valore>      → Fai un'offerta (>= prezzo attuale + incremento)");
        System.out.println("/info             → Mostra le informazioni sull'asta e la porta attiva");
        System.out.println("/help             → Mostra questa guida dei comandi");
        System.out.println("QUIT              → Esci dal server e chiudi il client");
        System.out.println("──────────────────────────────");
    }

    /**
     * Stampa la barra di stato corrente del client
     */
    private static synchronized void printStatusBar() {
        System.out.println("──────────────────────────────");
        System.out.printf("Utente: %s\n", nickname.get());
        System.out.printf("Oggetto in asta: %s\n", currentItem.get());
        System.out.printf("Prezzo attuale: %s\n", currentPrice.get());
        System.out.printf("Miglior offerente: %s\n", currentTopBidder.get());
        System.out.println("──────────────────────────────");
    }
}
