package org.elasticsoftware.elasticactors.broadcast.messages;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to tell the broadcaster to throttle messages of a given class
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Throttled {

    /**
     * The maximum number of messages per second. This can be an integer (represented as a string)
     * or a property, similar to {@link org.springframework.beans.factory.annotation.Value}.
     * <br/>
     * <strong>Using a value <= 0 will disable throttling.</strong>
     */
    String maxPerSecond();

}
