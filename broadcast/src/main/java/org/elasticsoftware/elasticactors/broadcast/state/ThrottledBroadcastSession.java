package org.elasticsoftware.elasticactors.broadcast.state;

import org.elasticsoftware.elasticactors.ActorRef;
import org.elasticsoftware.elasticactors.broadcast.messages.LeafNodesResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * @author Joost van de Wijgerd
 */
public final class ThrottledBroadcastSession {

    private static final Logger logger = LoggerFactory.getLogger(ThrottledBroadcastSession.class);

    private final String id;
    private final ActorRef parent;
    private final Object message;
    private final ActorRef sender;
    private final Set<ActorRef> leafNodes = new HashSet<>();
    private final ThrottleConfig throttleConfig;
    private int receivedResponses = 0;

    public ThrottledBroadcastSession(Object message, ActorRef sender, ThrottleConfig throttleConfig) {
        this(UUID.randomUUID().toString(), message, sender, throttleConfig);
    }

    public ThrottledBroadcastSession(ActorRef parent) {
        this(UUID.randomUUID().toString(), parent);
    }

    public ThrottledBroadcastSession(String id, Object message, ActorRef sender, ThrottleConfig throttleConfig) {
        this.id = id;
        this.message = message;
        this.sender = sender;
        this.throttleConfig = throttleConfig;
        this.parent = null;
    }

    public ThrottledBroadcastSession(String id, ActorRef parent) {
        this.id = id;
        this.parent = parent;
        this.message = null;
        this.sender = null;
        this.throttleConfig = null;
    }

    public String getId() {
        return id;
    }

    public ActorRef getParent() {
        return parent;
    }

    public Object getMessage() {
        return message;
    }

    public ActorRef getSender() {
        return sender;
    }

    public Set<ActorRef> getLeafNodes() {
        return leafNodes;
    }

    public void handleLeafNodesResponse(LeafNodesResponse response) {
        if(response.getBroadcastId().equals(this.id)) {
            logger.trace(
                    "Adding {} leaf nodes to broadcast session [{}]",
                    response.getLeafNodes().size(),
                    this.id);
            this.leafNodes.addAll(response.getLeafNodes());
            this.receivedResponses += 1;
        } else {
            logger.trace(
                    "Mismatching broadcast ids: own ({}) != received ({})",
                    this.id,
                    response.getBroadcastId());
        }
    }

    public int getReceivedResponses() {
        return receivedResponses;
    }

    public boolean isReady(int numberOfNodes) {
        return numberOfNodes == receivedResponses;
    }

    public ThrottleConfig getThrottleConfig() {
        return throttleConfig;
    }

}
