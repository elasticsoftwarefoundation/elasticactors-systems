package org.elasticsoftware.elasticactors.broadcast;

import org.elasticsoftware.elasticactors.Actor;
import org.elasticsoftware.elasticactors.ActorRef;
import org.elasticsoftware.elasticactors.TypedActor;
import org.elasticsoftware.elasticactors.base.serialization.JacksonSerializationFramework;
import org.elasticsoftware.elasticactors.broadcast.messages.HelloThrottledMissingProperty;

@Actor(serializationFramework = JacksonSerializationFramework.class)
public class SessionThrottledMissingPropertyActor extends TypedActor<HelloThrottledMissingProperty> {

    @Override
    public void onReceive(ActorRef sender, HelloThrottledMissingProperty message) throws Exception {
        sender.tell(new HelloThrottledMissingProperty("I'm fine thank you"), getSelf());
    }
}
