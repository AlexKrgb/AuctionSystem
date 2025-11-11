package it.unibz.auction.rmi;

import it.unibz.auction.rmi.dto.AuctionState;
import it.unibz.auction.rmi.dto.BidOutcome;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Canale di callback che il server utilizza per notificare il client.
 */
public interface ClientCallback extends Remote {

    void onSystemMessage(String message) throws RemoteException;

    void onAuctionUpdate(AuctionState state) throws RemoteException;

    void onBidOutcome(BidOutcome outcome) throws RemoteException;
}


