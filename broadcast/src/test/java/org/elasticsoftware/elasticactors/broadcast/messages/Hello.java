package org.elasticsoftware.elasticactors.broadcast.messages;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.elasticsoftware.elasticactors.base.serialization.JacksonSerializationFramework;
import org.elasticsoftware.elasticactors.broadcast.state.ThrottleConfig;
import org.elasticsoftware.elasticactors.serialization.Message;

/**
 * @author Joost van de Wijgerd
 */
@Message(serializationFramework = JacksonSerializationFramework.class, durable = false)
public final class Hello implements Throttled {

    private final String message;
    private final ThrottleConfig throttleConfig;

    public Hello(@JsonProperty("message") String message) {
        this(message, null);
    }

    @JsonCreator
    public Hello(
            @JsonProperty("message") String message,
            @JsonProperty("throttleConfig") ThrottleConfig throttleConfig) {
        this.message = message;
        this.throttleConfig = throttleConfig;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public ThrottleConfig getThrottleConfig() {
        return throttleConfig;
    }
}
