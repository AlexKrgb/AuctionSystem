package it.unibz.auction;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public class Server {

    private int activePort; // Porta effettivamente in uso

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private final Object auctionLock = new Object();

    private double currentPrice;
    private String topBidder;
}

