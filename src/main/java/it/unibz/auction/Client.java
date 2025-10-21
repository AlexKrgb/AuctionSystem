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
    }
}
