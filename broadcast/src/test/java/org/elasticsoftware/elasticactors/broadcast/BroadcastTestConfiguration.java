package org.elasticsoftware.elasticactors.broadcast;

import org.elasticsoftware.elasticactors.test.configuration.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;

@Import(TestConfiguration.class)
@PropertySource("classpath:application-test.properties")
public class BroadcastTestConfiguration {

}
