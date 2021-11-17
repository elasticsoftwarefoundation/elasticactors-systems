package org.elasticsoftware.elasticactors.broadcast;

import org.elasticsoftware.elasticactors.Actor;
import org.elasticsoftware.elasticactors.ActorRef;
import org.elasticsoftware.elasticactors.TypedActor;
import org.elasticsoftware.elasticactors.base.serialization.JacksonSerializationFramework;
import org.elasticsoftware.elasticactors.broadcast.messages.Hello;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Joost van de Wijgerd
 */
@Actor(serializationFramework = JacksonSerializationFramework.class)
public class SessionActor extends TypedActor<Hello> {

    private final static Logger staticLogger = LoggerFactory.getLogger(SessionActor.class);

    @Override
    public void onReceive(ActorRef sender, Hello message) throws Exception {
        sender.tell(new Hello("I'm fine thank you"),getSelf());
    }

    @Override
    protected Logger initLogger() {
        return staticLogger;
    }
}
