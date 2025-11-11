package it.unibz.auction.rmi.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Optional;

/**
 * Snapshot serializzabile dello stato corrente dell'asta.
 *
 * @param itemName         nome dell'articolo
 * @param itemDescription  descrizione dell'articolo
 * @param currentPrice     prezzo corrente
 * @param minIncrement     incremento minimo richiesto
 * @param topBidder        miglior offerente (facoltativo)
 * @param roundEndTime     timestamp in cui termina il round, se attivo
 * @param active           true se è in corso un round
 */
public record AuctionState(
        String itemName,
        String itemDescription,
        double currentPrice,
        double minIncrement,
        String topBidder,
        Instant roundEndTime,
        boolean active
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public Optional<String> topBidderOpt() {
        return Optional.ofNullable(topBidder);
    }

    public Optional<Instant> roundEndOpt() {
        return Optional.ofNullable(roundEndTime);
    }
}


