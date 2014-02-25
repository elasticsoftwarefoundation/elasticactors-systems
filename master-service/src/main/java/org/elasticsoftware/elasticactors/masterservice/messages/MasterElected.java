package org.elasticsoftware.elasticactors.masterservice.messages;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.elasticsoftware.elasticactors.base.serialization.JacksonSerializationFramework;
import org.elasticsoftware.elasticactors.serialization.Message;

/**
 * @author Joost van de Wijgerd
 */
@Message(serializationFramework = JacksonSerializationFramework.class, durable = false)
public final class MasterElected {
    private final String id;
    private final boolean local;

    @JsonCreator
    public MasterElected(@JsonProperty("id") String id, @JsonProperty("local") boolean local) {
        this.id = id;
        this.local = local;
    }

    public String getId() {
        return id;
    }

    public boolean isLocal() {
        return local;
    }
}

