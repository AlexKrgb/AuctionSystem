package it.unibz.auction.rmi;

import it.unibz.auction.rmi.dto.AuctionItem;
import it.unibz.auction.rmi.dto.AuctionState;
import it.unibz.auction.rmi.dto.BidOutcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RMIAuctionServerIT {

    private Registry registry;
    private RMIAuctionServer server;
    private AuctionService stub;

    @BeforeEach
    void setUp() throws Exception {
        registry = LocateRegistry.createRegistry(findFreePort());
        server = new RMIAuctionServer(List.of(
                new AuctionItem("TestItem", "Descrizione", 100.0, 5.0, 10),
                new AuctionItem("SecondItem", "Descrizione 2", 200.0, 10.0, 10)
        ));
        registry.rebind("AuctionServiceTest", server);
        stub = (AuctionService) registry.lookup("AuctionServiceTest");
        server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (stub != null) {
            try {
                stub.unregisterClient("alice");
                stub.unregisterClient("bob");
            } catch (Exception ignored) {
            }
        }
        if (server != null) {
            server.shutdown();
        }
        if (registry != null) {
            try {
                registry.unbind("AuctionServiceTest");
            } catch (Exception ignored) {
            }
            UnicastRemoteObject.unexportObject(registry, true);
        }
    }

    @Test
    void testClientRegistrationAndBidFlow() throws Exception {
        CountDownLatch updateLatch = new CountDownLatch(1);
        CountDownLatch outcomeLatch = new CountDownLatch(1);
        AtomicReference<AuctionState> lastState = new AtomicReference<>();
        AtomicReference<BidOutcome> lastOutcome = new AtomicReference<>();

        TestCallback callback = new TestCallback(updateLatch, outcomeLatch, lastState, lastOutcome);
        stub.registerClient("alice", callback);

        assertTrue(updateLatch.await(2, TimeUnit.SECONDS), "Lo stato iniziale non è stato ricevuto");
        AuctionState initial = lastState.get();
        assertNotNull(initial);
        assertTrue(initial.active());
        double minimum = initial.currentPrice() + initial.minIncrement();

        stub.submitBid("alice", minimum);

        assertTrue(outcomeLatch.await(2, TimeUnit.SECONDS), "Il risultato dell'offerta non è stato ricevuto");
        BidOutcome outcome = lastOutcome.get();
        assertNotNull(outcome);
        assertTrue(outcome.accepted());
        assertEquals(minimum, outcome.amount());
        AuctionState updated = outcome.stateSnapshot();
        assertNotNull(updated);
        assertEquals("alice", updated.topBidder());
        assertEquals(minimum, updated.currentPrice());

        callback.close();
    }

    private static int findFreePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static class TestCallback extends UnicastRemoteObject implements ClientCallback {
        private final CountDownLatch updateLatch;
        private final CountDownLatch outcomeLatch;
        private final AtomicReference<AuctionState> stateRef;
        private final AtomicReference<BidOutcome> outcomeRef;

        protected TestCallback(CountDownLatch updateLatch,
                               CountDownLatch outcomeLatch,
                               AtomicReference<AuctionState> stateRef,
                               AtomicReference<BidOutcome> outcomeRef) throws java.rmi.RemoteException {
            super();
            this.updateLatch = updateLatch;
            this.outcomeLatch = outcomeLatch;
            this.stateRef = stateRef;
            this.outcomeRef = outcomeRef;
        }

        @Override
        public void onSystemMessage(String message) {
            // ignorato nel test
        }

        @Override
        public void onAuctionUpdate(AuctionState state) {
            stateRef.set(state);
            updateLatch.countDown();
        }

        @Override
        public void onBidOutcome(BidOutcome outcome) {
            outcomeRef.set(outcome);
            outcomeLatch.countDown();
        }

        void close() throws java.rmi.RemoteException {
            UnicastRemoteObject.unexportObject(this, true);
        }
    }
}


