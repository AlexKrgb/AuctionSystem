package it.unibz.auction.rmi.dto;

import java.io.Serial;
import java.io.Serializable;

/**
 * Risposta inviata tramite callback dopo una proposta d'asta.
 *
 * @param accepted         true se l'offerta è stata accettata
 * @param amount           importo proposto dal client
 * @param minimumRequired  minimo accettabile al momento della proposta
 * @param message          motivazione o messaggio user-friendly
 * @param stateSnapshot    stato dell'asta dopo la valutazione
 */
public record BidOutcome(
        boolean accepted,
        double amount,
        double minimumRequired,
        String message,
        AuctionState stateSnapshot
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}


