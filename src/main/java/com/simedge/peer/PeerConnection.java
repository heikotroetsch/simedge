package com.simedge.peer;

import org.drasyl.node.DrasylConfig;
import org.drasyl.node.DrasylException;
import org.drasyl.node.DrasylNode;
import org.drasyl.node.event.Event;
import org.drasyl.node.event.MessageEvent;
import org.drasyl.node.event.NodeOnlineEvent;

import com.simedge.protocols.PeerMessage;
import com.simedge.protocols.PeerProtocol;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Starts a {@link DrasylNode} which sends one message to given address and
 * echoes back any received message to
 * the sender. Based on the <a href="https://tools.ietf.org/html/rfc862">Echo
 * Protocol</a>.
 *
 * @see EchoServerNode
 */
@SuppressWarnings({ "java:S106", "java:S112", "java:S125", "java:S126", "java:S2096" })
public class PeerConnection extends DrasylNode {
    // private static final int SIZE = Integer.parseInt(System.getProperty("size",
    // "256"));

    private static final String IDENTITY = System.getProperty("identity", "client-b.identity");
    final static DrasylConfig config = DrasylConfig.newBuilder()
            .identityPath(Path.of(IDENTITY))
            // .remoteMessageArmApplicationEnabled(false)
            // reliable
            .remoteMessageArqEnabled(false)
            .build();
    final CompletableFuture<Void> online = new CompletableFuture<>();

    static int resultQuant = 0;

    protected PeerConnection() throws DrasylException {
        super(config);
        this.initialize();
    }

    void initialize() throws DrasylException {

        this.start().toCompletableFuture().join();

        // CLIENT CONNECTIONS
        this.online.join();
        System.out.println("PeerConnection started");
        System.out.println("PeerConnection listening on address " + this.identity().getAddress());

    }

    @Override
    public void onEvent(final @org.drasyl.util.internal.NonNull Event event) {
        // hier keine Blokierenden sachen weil während dessn empfängt man keine Messages
        if (event instanceof NodeOnlineEvent) {
            online.complete(null);
        } else if (event instanceof MessageEvent) {

            PeerMessage peerMessage = new PeerMessage((byte[]) ((MessageEvent) event).getPayload());

            PeerProtocol.handleMessage(peerMessage,
                    ((MessageEvent) event).getSender());

            if (peerMessage.messageType == PeerMessage.MessageType.RESULT) {
                resultQuant++;
            }

        }

    }

    /**
     * Send PeerMessage over drasyl network
     * 
     * @param recipient_identity Identity String of recipient
     * @param peerMessage        Peer message to be sent
     */
    public void sendMessage(String recipient_identity, PeerMessage peerMessage) {
        System.out
                .println("Sent Message Number: \t"
                        + peerMessage.messageNumber + "\t\tSending to " + recipient_identity);
        ConnectionPool.scheduler.addToMessageController(recipient_identity, peerMessage.messageNumber);

        this.send(recipient_identity, peerMessage.getMessageBytes()).exceptionally(e -> {
            throw new RuntimeException("Unable to process message.", e);
        });

    }

    /**
     * Own method for sending results. Sending results is always possible even if
     * Message controller is full. Ensures instant sendback.
     * 
     * @param recipient_identity String Adress of node to send to
     * @param peerMessage        Message to be sent
     */
    public void sendResultMessage(String recipient_identity, PeerMessage peerMessage) {
        this.send(recipient_identity, peerMessage.getMessageBytes()).exceptionally(e -> {
            throw new RuntimeException("Unable to process message.", e);
        });

    }

}
