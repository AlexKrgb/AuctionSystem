package it.unibz.auction.rmi;

import it.unibz.auction.rmi.dto.AuctionState;
import it.unibz.auction.rmi.exceptions.AuctionException;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * API remota esposta dal server RMI dell'asta.
 */
public interface AuctionService extends Remote {

    /**
     * Registra un nuovo client all'asta.
     *
     * @param nickname nickname proposto
     * @param callback callback remoto per notifiche push
     * @throws RemoteException   problemi di comunicazione RMI
     * @throws AuctionException  validazione nickname o stato asta non disponibile
     */
    void registerClient(String nickname, ClientCallback callback) throws RemoteException, AuctionException;

    /**
     * Deregistra un client. È idempotente.
     *
     * @param nickname nickname da rimuovere
     * @throws RemoteException problemi di comunicazione RMI
     */
    void unregisterClient(String nickname) throws RemoteException;

    /**
     * Restituisce lo stato corrente dell'asta.
     *
     * @return stato dell'asta
     * @throws RemoteException problemi di comunicazione RMI
     */
    AuctionState getCurrentState() throws RemoteException;

    /**
     * Sottomette un'offerta per conto del client indicato.
     *
     * @param nickname nickname del client che offre
     * @param amount   importo dell'offerta
     * @throws RemoteException  problemi di comunicazione RMI
     * @throws AuctionException validazione dell'offerta fallita
     */
    void submitBid(String nickname, double amount) throws RemoteException, AuctionException;

    /**
     * Invia un messaggio di chat agli altri partecipanti.
     *
     * @param nickname autore del messaggio
     * @param message  testo del messaggio
     * @throws RemoteException  problemi di comunicazione RMI
     * @throws AuctionException validazione del messaggio fallita
     */
    void sendChatMessage(String nickname, String message) throws RemoteException, AuctionException;

}


