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

package org.elasticsoftware.elasticactors.http;


import com.google.common.base.Charsets;
import io.netty.handler.codec.http.HttpHeaders;
import org.asynchttpclient.AsyncHandler;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.HttpResponseBodyPart;
import org.asynchttpclient.HttpResponseStatus;
import org.asynchttpclient.ListenableFuture;
import org.asynchttpclient.Response;
import org.elasticsoftware.elasticactors.ActorRef;
import org.elasticsoftware.elasticactors.ActorSystem;
import org.elasticsoftware.elasticactors.http.actors.EventStreamer;
import org.elasticsoftware.elasticactors.http.actors.User;
import org.elasticsoftware.elasticactors.test.TestActorSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

/**
 * @author Joost van de Wijgerd
 */
public class HttpActorSystemTest {
    private TestActorSystem testActorSystem;

    @BeforeMethod(enabled = true)
    public void setUp() {
        testActorSystem = new TestActorSystem();
        testActorSystem.initialize();
    }

    @AfterMethod(enabled = true)
    public void tearDown() {
        testActorSystem.destroy();
    }

    @Test(enabled = true)
    public void testInContainer() throws Exception {
        ActorSystem testSystem = testActorSystem.getActorSystem("http");

        // create a couple of users
        ActorRef user1Ref = testSystem.actorOf("users/1", User.class);
        ActorRef user2Ref = testSystem.actorOf("users/2", User.class);
        ActorRef user3Ref = testSystem.actorOf("users/3", User.class);

        AsyncHttpClient testClient = new DefaultAsyncHttpClient();
        for (int i = 1; i < 4; i++) {
            ListenableFuture<Response> responseFuture = testClient.prepareGet(String.format("http://localhost:9080/users/%d", i)).execute();
            Response response = responseFuture.get();

            assertEquals(response.getContentType(), "text/plain");
            assertEquals(response.getResponseBody(StandardCharsets.UTF_8), "HelloWorld");

        }

        // remove users
        testSystem.stop(user1Ref);
        testSystem.stop(user2Ref);
        testSystem.stop(user3Ref);

        // do it again an see if we get 404
        ListenableFuture<Response> responseFuture = testClient.prepareGet("http://localhost:9080/users/1").execute();
        Response response = responseFuture.get();

        assertEquals(response.getStatusCode(), 404);
    }

    @Test(enabled = false)
    public void testURIParsing() throws Exception {
        URI asbsoluteUri = new URI("http://localhost:9080/events/testing");
        assertNotNull(asbsoluteUri);
        assertEquals(asbsoluteUri.getPath(), "/events/testing");
        assertEquals(asbsoluteUri.getHost(), "localhost");
        URI uri = new URI("/events/testing");
        assertNotNull(uri);
        assertEquals(uri.getPath(), "/events/testing");
        assertNull(uri.getHost());
    }

    /*@Test(enabled = true)
    public void testEventStreamingWithEventSourceClient() throws Exception {
        ActorSystem httpSystem = TestActorSystem.create(new HttpActorSystem());
        ActorSystem testSystem = TestActorSystem.create(new HttpTestActorSystem());

        // create a stream
        ActorRef steamer = testSystem.actorOf("events/testing",EventStreamer.class);

        final Logger logger = Logger.getLogger(EventSourceHandler.class);
        final CountDownLatch waitLatch = new CountDownLatch(1);
        EventSource eventSource = new EventSource("http://localhost:8080/events/testing",
              new EventSourceHandler() {
                  @Override
                  public void onConnect() throws Exception {
                    logger.info("CONNECTED");
                  }

                  @Override
                  public void onMessage(String event, MessageEvent messageEvent) throws Exception {
                    logger.info(String.format("origin[%s]:data[%s]",messageEvent.origin,messageEvent.data));
                  }

                  @Override
                  public void onError(Throwable throwable) {
                      logger.error(throwable);
                      waitLatch.countDown();
                  }
              });
        eventSource.connect();
        waitLatch.await(1, TimeUnit.MINUTES);
    }*/

    @Test(enabled = false)
    public void testEventStreamingWithAsyncHttpClient() throws Exception {
        ActorSystem testSystem = testActorSystem.getActorSystem("http");

        // create a stream
        ActorRef steamer = testSystem.actorOf("events/testing", EventStreamer.class);

        AsyncHttpClient testClient = new DefaultAsyncHttpClient();
        final CountDownLatch waitLatch = new CountDownLatch(1);
        testClient.prepareGet("http://localhost:9080/events/testing").execute(new ServerSentEventsHandler(waitLatch));
        waitLatch.await(1, TimeUnit.MINUTES);
    }

    private static final class ServerSentEventsHandler implements AsyncHandler<Object> {
        private static final Logger logger = LoggerFactory.getLogger(ServerSentEventsHandler.class);
        private final CountDownLatch waitLatch;

        private ServerSentEventsHandler(CountDownLatch waitLatch) {
            this.waitLatch = waitLatch;
        }

        @Override
        public void onThrowable(Throwable t) {
            logger.error("Received throwable", t);
            waitLatch.countDown();
        }

        @Override
        public State onBodyPartReceived(HttpResponseBodyPart bodyPart) throws Exception {
            logger.info(new String(bodyPart.getBodyPartBytes(), Charsets.UTF_8));
            return State.CONTINUE;
        }

        @Override
        public State onStatusReceived(HttpResponseStatus responseStatus) throws Exception {
            logger.info("Received status code: {}", responseStatus.getStatusCode());
            return State.CONTINUE;
        }

        @Override
        public State onHeadersReceived(HttpHeaders headers) throws Exception {
            return State.CONTINUE;
        }

        @Override
        public Object onCompleted() throws Exception {
            logger.info("onCompleted");
            waitLatch.countDown();
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }
    }


}
