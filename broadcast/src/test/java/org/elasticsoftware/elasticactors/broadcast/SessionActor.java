package org.elasticsoftware.elasticactors.broadcast;

import org.elasticsoftware.elasticactors.Actor;
import org.elasticsoftware.elasticactors.ActorRef;
import org.elasticsoftware.elasticactors.TypedActor;
import org.elasticsoftware.elasticactors.base.serialization.JacksonSerializationFramework;
import org.elasticsoftware.elasticactors.broadcast.messages.Hello;

/**
 * @author Joost van de Wijgerd
 */
@Actor(serializationFramework = JacksonSerializationFramework.class)
public class SessionActor extends TypedActor<Hello> {
    @Override
    public void onReceive(ActorRef sender, Hello message) throws Exception {
        sender.tell(new Hello("I'm fine thank you"),getSelf());
    }
}
