package org.elasticsoftware.elasticactors.broadcast;

import org.elasticsoftware.elasticactors.Actor;
import org.elasticsoftware.elasticactors.ActorRef;
import org.elasticsoftware.elasticactors.TypedActor;
import org.elasticsoftware.elasticactors.base.serialization.JacksonSerializationFramework;
import org.elasticsoftware.elasticactors.broadcast.messages.HelloProperty;

@Actor(serializationFramework = JacksonSerializationFramework.class)
public class SessionPropertyActor extends TypedActor<HelloProperty> {
    @Override
    public void onReceive(ActorRef sender, HelloProperty message) throws Exception {
        sender.tell(new HelloProperty("I'm fine thank you"),getSelf());
    }
}
