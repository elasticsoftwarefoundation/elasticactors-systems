package org.elasticsoftware.elasticactors.broadcast.messages;

import org.elasticsoftware.elasticactors.broadcast.state.ThrottleConfig;

/**
 * Interface that messages that are to be throttled have to implement. The message should provide
 * the manner in which it will be throttled using the {@link Throttled#getThrottleConfig()}
 * method.
 */
public interface Throttled {

    /**
     * The throttling configuration to be used for the message
     */
    ThrottleConfig getThrottleConfig();

}
