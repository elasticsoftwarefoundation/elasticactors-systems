package org.elasticsoftware.elasticactors.broadcast.messages;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.elasticsoftware.elasticactors.ActorRef;
import org.elasticsoftware.elasticactors.base.serialization.JacksonSerializationFramework;
import org.elasticsoftware.elasticactors.broadcast.state.ThrottleConfig;
import org.elasticsoftware.elasticactors.serialization.Message;

/**
 * @author Joost van de Wijgerd
 */
@Message(serializationFramework = JacksonSerializationFramework.class, durable = true, immutable = true)
public final class ThrottledMessage implements Throttled {
    private final ActorRef sender;
    private final String messageClass;
    private final String messageData;
    private final ThrottleConfig throttleConfig;

    @JsonCreator
    public ThrottledMessage(
            @JsonProperty("sender") ActorRef sender,
            @JsonProperty("messageClass") String messageClass,
            @JsonProperty("messageData") String messageData,
            @JsonProperty("throttleConfig") ThrottleConfig throttleConfig) {
        this.sender = sender;
        this.messageClass = messageClass;
        this.messageData = messageData;
        this.throttleConfig = throttleConfig;
    }

    public ActorRef getSender() {
        return sender;
    }

    public String getMessageClass() {
        return messageClass;
    }

    public String getMessageData() {
        return messageData;
    }

    @Override
    public ThrottleConfig getThrottleConfig() {
        return throttleConfig;
    }
}
