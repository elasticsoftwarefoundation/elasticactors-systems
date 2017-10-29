/*
 * Copyright 2013 - 2014 The Original Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.elasticsoftware.elasticactors.http.actors;

import com.google.common.base.Charsets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsoftware.elasticactors.Actor;
import org.elasticsoftware.elasticactors.ActorRef;
import org.elasticsoftware.elasticactors.TypedActor;
import org.elasticsoftware.elasticactors.base.serialization.JacksonSerializationFramework;
import org.elasticsoftware.elasticactors.http.messages.HttpRequest;
import org.elasticsoftware.elasticactors.http.messages.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Joost van de Wijgerd
 */
@Actor(serializationFramework = JacksonSerializationFramework.class)
public final class User extends TypedActor<HttpRequest> {
    private static final Logger logger = LogManager.getLogger(User.class);

    @Override
    public void postActivate(String previousVersion) throws Exception {
        // register ourselves with the http server
        //ActorRef httpServer = getSystem().getParent().get("Http").serviceActorFor("httpServer");
        //httpServer.tell(new RegisterRouteMessage(String.format("/%s", getSelf().getActorId()),getSelf()),getSelf());
    }

    @Override
    public void onReceive(ActorRef sender, HttpRequest message) throws Exception {
        logger.info("Got request");
        // send something back
        Map<String,List<String>> headers = new HashMap<String,List<String>>();
        headers.put(HttpHeaders.Names.CONTENT_TYPE, Arrays.asList("text/plain"));
        sender.tell(new HttpResponse(200, headers, "HelloWorld".getBytes(Charsets.UTF_8)), getSelf());
    }
}
