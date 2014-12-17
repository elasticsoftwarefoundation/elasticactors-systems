package org.elasticsoftware.elasticactors.broadcast.messages;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.elasticsoftware.elasticactors.base.serialization.JacksonSerializationFramework;
import org.elasticsoftware.elasticactors.serialization.Message;

/**
 * @author Joost van de Wijgerd
 */
@Message(serializationFramework = JacksonSerializationFramework.class, durable = false, immutable = true)
public final class LeafNodesRequest {
    private final String broadcastId;

    @JsonCreator
    public LeafNodesRequest(@JsonProperty("broadcastId") String broadcastId) {
        this.broadcastId = broadcastId;
    }

    public String getBroadcastId() {
        return broadcastId;
    }

}
