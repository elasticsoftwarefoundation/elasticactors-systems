package org.elasticsoftware.elasticactors.broadcast;

import org.elasticsoftware.elasticactors.Actor;
import org.elasticsoftware.elasticactors.ActorRef;
import org.elasticsoftware.elasticactors.TypedActor;
import org.elasticsoftware.elasticactors.base.serialization.JacksonSerializationFramework;
import org.elasticsoftware.elasticactors.broadcast.messages.HelloThrottledMissingProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Actor(serializationFramework = JacksonSerializationFramework.class)
public class SessionThrottledMissingPropertyActor extends TypedActor<HelloThrottledMissingProperty> {

    private final static Logger staticLogger = LoggerFactory.getLogger(SessionThrottledMissingPropertyActor.class);

    @Override
    public void onReceive(ActorRef sender, HelloThrottledMissingProperty message) throws Exception {
        sender.tell(new HelloThrottledMissingProperty("I'm fine thank you"), getSelf());
    }

    @Override
    protected Logger initLogger() {
        return staticLogger;
    }
}
