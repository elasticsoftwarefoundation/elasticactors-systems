package org.elasticsoftware.elasticactors.broadcast.messages;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.elasticsoftware.elasticactors.ActorRef;
import org.elasticsoftware.elasticactors.base.serialization.JacksonSerializationFramework;
import org.elasticsoftware.elasticactors.serialization.Message;

import java.util.Set;

/**
 * @author Joost van de Wijgerd
 */
@Message(serializationFramework = JacksonSerializationFramework.class, durable = false, immutable = true)
public final class LeafNodesResponse {
    private final String broadcastId;
    private final Set<ActorRef> leafNodes;

    @JsonCreator
    public LeafNodesResponse(@JsonProperty("broadcastId") String broadcastId,
                             @JsonProperty("leafNodes") Set<ActorRef> leafNodes) {
        this.broadcastId = broadcastId;
        this.leafNodes = leafNodes;
    }

    public String getBroadcastId() {
        return broadcastId;
    }

    public Set<ActorRef> getLeafNodes() {
        return leafNodes;
    }
}
