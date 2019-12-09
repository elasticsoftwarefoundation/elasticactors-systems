package org.elasticsoftware.elasticactors.broadcast.messages;

import org.elasticsoftware.elasticactors.broadcast.state.ThrottleConfig;

public interface Throttled {

    ThrottleConfig getThrottleConfig();

}
