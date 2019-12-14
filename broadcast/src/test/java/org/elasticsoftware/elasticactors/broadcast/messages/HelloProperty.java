package org.elasticsoftware.elasticactors.broadcast.messages;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.elasticsoftware.elasticactors.base.serialization.JacksonSerializationFramework;
import org.elasticsoftware.elasticactors.serialization.Message;

@Message(serializationFramework = JacksonSerializationFramework.class, durable = false)
@Throttled(maxPerSecond = "${ea.broadcast.test.maxPerSecond}")
public final class HelloProperty {

    private final String message;

    @JsonCreator
    public HelloProperty(@JsonProperty("message") String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

}
