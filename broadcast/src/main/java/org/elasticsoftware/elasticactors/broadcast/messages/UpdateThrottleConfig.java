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
 * DEPRECATED: starting from version 2.1.0, this message has been deprecated. Using it will not
 * change the throttling configuration of the Broadcaster actor anymore. This has been superseded
 * by the per-message configuration provided by {@link Throttled}.
 *
 * @author Joost van de Wijgerd
 */
@Message(serializationFramework = JacksonSerializationFramework.class, immutable = true)
@JsonInclude(NON_NULL)
@Deprecated
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
