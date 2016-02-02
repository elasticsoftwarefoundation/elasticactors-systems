package org.elasticsoftware.elasticactors.broadcast.messages.rehash;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.elasticsoftware.elasticactors.ActorRef;
import org.elasticsoftware.elasticactors.base.serialization.JacksonSerializationFramework;
import org.elasticsoftware.elasticactors.serialization.Message;

import java.util.Set;

@Message(serializationFramework = JacksonSerializationFramework.class, durable = true, immutable = true)
public final class RehashResponse {

    private final Set<ActorRef> members;

    @JsonCreator
    public RehashResponse(@JsonProperty("members") Set<ActorRef> members) {
        this.members = members;
    }

    public Set<ActorRef> getMembers() {
        return members;
    }
}
