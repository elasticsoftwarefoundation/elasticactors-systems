package org.elasticsoftware.elasticactors.broadcast.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Joost van de Wijgerd
 */
public final class ThrottleConfig {
    private final Integer maxMessagesPerSecond;

    @JsonCreator
    public ThrottleConfig(@JsonProperty("maxMessagesPerSecond") Integer maxMessagesPerSecond) {
        this.maxMessagesPerSecond = maxMessagesPerSecond;
    }

    public Integer getMaxMessagesPerSecond() {
        return maxMessagesPerSecond;
    }
}
