package org.elasticsoftware.elasticactors.broadcast.messages.rehash;

import org.elasticsoftware.elasticactors.base.serialization.JacksonSerializationFramework;
import org.elasticsoftware.elasticactors.serialization.Message;

/**
 * This message is used internally by the rehash process. In order to start the rehash, use {@link RehashRequest}
 */
@Message(serializationFramework = JacksonSerializationFramework.class, durable = true, immutable = true)
public final class InternalRehashRequest {
}
