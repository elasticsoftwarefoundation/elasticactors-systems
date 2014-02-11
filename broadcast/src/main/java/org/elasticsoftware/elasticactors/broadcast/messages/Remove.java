package org.elasticsoftware.elasticactors.broadcast.messages;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Sets;
import org.elasticsoftware.elasticactors.ActorRef;
import org.elasticsoftware.elasticactors.base.serialization.JacksonSerializationFramework;
import org.elasticsoftware.elasticactors.serialization.Message;

import java.util.Collection;
import java.util.Set;

/**
 * @author Joost van de Wijgerd
 */
@Message(serializationFramework = JacksonSerializationFramework.class, durable = true)
public class Remove {
    private final Set<ActorRef> members;

    public Remove(Collection<ActorRef> members) {
        this(Sets.newHashSet(members));
    }

    public Remove(ActorRef... members) {
        this(Sets.newHashSet(members));
    }

    @JsonCreator
    public Remove(@JsonProperty("members") Set<ActorRef> members) {
        this.members = members;
    }

    public Set<ActorRef> getMembers() {
        return members;
    }
}
