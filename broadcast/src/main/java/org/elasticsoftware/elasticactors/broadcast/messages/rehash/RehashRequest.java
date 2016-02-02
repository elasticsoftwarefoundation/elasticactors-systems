package org.elasticsoftware.elasticactors.broadcast.messages.rehash;

import org.elasticsoftware.elasticactors.base.serialization.JacksonSerializationFramework;
import org.elasticsoftware.elasticactors.serialization.Message;

@Message(serializationFramework = JacksonSerializationFramework.class, durable = true, immutable = true)
public final class RehashRequest {
}
