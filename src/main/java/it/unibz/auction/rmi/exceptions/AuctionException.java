package it.unibz.auction.rmi.exceptions;

import java.io.Serial;

/**
 * Eccezione applicativa per errori nel flusso d'asta.
 */
public class AuctionException extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;

    public AuctionException(String message) {
        super(message);
    }
}


