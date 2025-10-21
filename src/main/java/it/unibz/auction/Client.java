package it.unibz.auction;

import java.io.*;
import java.net.*;
import java.util.Scanner;

public class Client {
    public static void main(String[] args) throws Exception {
        String host = (args.length > 0) ? args[0] : "127.0.0.1";
        int port = (args.length > 1) ? Integer.parseInt(args[1]) : 5000;

        try (Socket socket = new Socket(host, port)) {
            System.out.println("Connesso a " + host + ":" + port);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

            // Thread lettore (server → console)
            Thread reader = new Thread(() -> {
                try {
                    String line;
                    while ((line = in.readLine()) != null) {
                        System.out.println(line);
                    }
                } catch (IOException ignored) {}
                System.out.println("Connessione chiusa.");
                System.exit(0);
            });
            reader.setDaemon(true);
            reader.start();

            // Thread scrittore (console → server)
            Scanner sc = new Scanner(System.in);
            System.out.println("Usa: JOIN <nick>, MSG <testo>, BID <valore>, QUIT");
            while (true) {
                String cmd = sc.nextLine();
                out.println(cmd);
                if ("QUIT".equalsIgnoreCase(cmd.trim())) break;
            }
        }
    }
}
