package it.unibz.auction.rmi.dto;

import java.io.Serial;
import java.io.Serializable;

/**
 * Rappresenta un oggetto messo all'asta.
 *
 * @param name            nome breve
 * @param description     descrizione estesa
 * @param startPrice      prezzo di partenza
 * @param minIncrement    incremento minimo consentito
 * @param durationSeconds durata della sessione in secondi
 */
public record AuctionItem(
        String name,
        String description,
        double startPrice,
        double minIncrement,
        int durationSeconds
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}


