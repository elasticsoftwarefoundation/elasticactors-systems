package org.elasticsoftware.elasticactors.broadcast;

import org.elasticsoftware.elasticactors.Actor;
import org.elasticsoftware.elasticactors.ActorRef;
import org.elasticsoftware.elasticactors.TypedActor;
import org.elasticsoftware.elasticactors.base.serialization.JacksonSerializationFramework;
import org.elasticsoftware.elasticactors.broadcast.messages.HelloThrottledProperty;

@Actor(serializationFramework = JacksonSerializationFramework.class)
public class SessionThrottledPropertyActor extends TypedActor<HelloThrottledProperty> {

    @Override
    public void onReceive(ActorRef sender, HelloThrottledProperty message) throws Exception {
        sender.tell(new HelloThrottledProperty("I'm fine thank you"), getSelf());
    }
}
