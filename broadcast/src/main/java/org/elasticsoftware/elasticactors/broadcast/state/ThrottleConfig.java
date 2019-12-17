package org.elasticsoftware.elasticactors.broadcast.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * @author Joost van de Wijgerd
 */
public final class ThrottleConfig {

    private final Integer maxMessagesPerSecond;

    @JsonCreator
    public ThrottleConfig(@JsonProperty("maxMessagesPerSecond") Integer maxMessagesPerSecond) {
        this.maxMessagesPerSecond = maxMessagesPerSecond;
    }

    @JsonIgnore
    public boolean isValid() {
        return maxMessagesPerSecond != null && maxMessagesPerSecond > 0;
    }

    public Integer getMaxMessagesPerSecond() {
        return maxMessagesPerSecond;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ThrottleConfig)) {
            return false;
        }
        ThrottleConfig that = (ThrottleConfig) o;
        return Objects.equals(maxMessagesPerSecond, that.maxMessagesPerSecond);
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxMessagesPerSecond);
    }

    @Override
    public String toString() {
        return "ThrottleConfig{" +
                "maxMessagesPerSecond=" + maxMessagesPerSecond +
                '}';
    }
}
