package org.elasticsoftware.elasticactors.broadcast.messages;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.elasticsoftware.elasticactors.base.serialization.JacksonSerializationFramework;
import org.elasticsoftware.elasticactors.broadcast.state.ThrottleConfig;
import org.elasticsoftware.elasticactors.serialization.Message;

import javax.annotation.Nullable;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

/**
 * @author Joost van de Wijgerd
 */
@Message(serializationFramework = JacksonSerializationFramework.class, durable = true, immutable = true)
@JsonInclude(NON_NULL)
public final class UpdateThrottleConfig {
    private final ThrottleConfig throttleConfig;

    @JsonCreator
    public UpdateThrottleConfig(@JsonProperty("throttleConfig") @Nullable ThrottleConfig throttleConfig) {
        this.throttleConfig = throttleConfig;
    }

    public ThrottleConfig getThrottleConfig() {
        return throttleConfig;
    }
}
