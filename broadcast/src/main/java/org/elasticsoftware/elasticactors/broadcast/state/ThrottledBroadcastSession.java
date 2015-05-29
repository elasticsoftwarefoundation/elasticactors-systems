package org.elasticsoftware.elasticactors.broadcast.state;

import org.elasticsoftware.elasticactors.ActorRef;
import org.elasticsoftware.elasticactors.broadcast.messages.LeafNodesResponse;

import java.util.Set;
import java.util.UUID;

import static com.google.common.collect.Sets.newHashSet;

/**
 * @author Joost van de Wijgerd
 */
public final class ThrottledBroadcastSession {
    private final String id;
    private final ActorRef parent;
    private final Object message;
    private final ActorRef sender;
    private final Set<ActorRef> leafNodes = newHashSet();
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
            this.leafNodes.addAll(response.getLeafNodes());
            this.receivedResponses += 1;
        }
    }

    public boolean isReady(int numberOfNodes) {
        return (numberOfNodes == receivedResponses);
    }

    public ThrottleConfig getThrottleConfig() {
        return throttleConfig;
    }
}
