package org.elasticsoftware.elasticactors.broadcast;

import org.elasticsoftware.elasticactors.Actor;
import org.elasticsoftware.elasticactors.ActorRef;
import org.elasticsoftware.elasticactors.TypedActor;
import org.elasticsoftware.elasticactors.base.serialization.JacksonSerializationFramework;
import org.elasticsoftware.elasticactors.broadcast.messages.HelloThrottled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Joost van de Wijgerd
 */
@Actor(serializationFramework = JacksonSerializationFramework.class)
public class SessionThrottledActor extends TypedActor<HelloThrottled> {

    private final static Logger staticLogger = LoggerFactory.getLogger(SessionThrottledActor.class);

    @Override
    public void onReceive(ActorRef sender, HelloThrottled message) throws Exception {
        sender.tell(new HelloThrottled("I'm fine thank you"), getSelf());
    }

    @Override
    protected Logger initLogger() {
        return staticLogger;
    }
}
