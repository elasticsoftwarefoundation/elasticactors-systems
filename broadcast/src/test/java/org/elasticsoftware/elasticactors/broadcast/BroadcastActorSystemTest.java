package org.elasticsoftware.elasticactors.broadcast;

import org.elasticsoftware.elasticactors.ActorRef;
import org.elasticsoftware.elasticactors.ActorSystem;
import org.elasticsoftware.elasticactors.base.actors.ActorDelegate;
import org.elasticsoftware.elasticactors.base.actors.ReplyActor;
import org.elasticsoftware.elasticactors.broadcast.messages.Add;
import org.elasticsoftware.elasticactors.broadcast.messages.Hello;
import org.elasticsoftware.elasticactors.broadcast.messages.UpdateThrottleConfig;
import org.elasticsoftware.elasticactors.broadcast.messages.rehash.RehashComplete;
import org.elasticsoftware.elasticactors.broadcast.messages.rehash.RehashRequest;
import org.elasticsoftware.elasticactors.broadcast.state.BroadcasterState;
import org.elasticsoftware.elasticactors.broadcast.state.ThrottleConfig;
import org.elasticsoftware.elasticactors.test.TestActorSystem;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import static java.lang.String.format;

/**
 * @author Joost van de Wijgerd
 */
public class BroadcastActorSystemTest {
    private TestActorSystem testActorSystem;

    @BeforeMethod(enabled = true)
    public void setUp() {
        // decrease the verbosity of logs a little bit
        testActorSystem = new TestActorSystem();
        testActorSystem.initialize();
    }

    @AfterMethod(enabled = true)
    public void tearDown() {
        if(testActorSystem != null) {
            testActorSystem.destroy();
            testActorSystem = null;
        }
    }

    @Test(enabled = true)
    public void testInContainer() throws Exception {
        ActorSystem broadcastActorSystem = testActorSystem.getActorSystem();

        ActorRef sessionList = broadcastActorSystem.actorOf("sessionList",Broadcaster.class,new BroadcasterState(8,32));

        // @todo: the default shard cache is set to 1024!
        int NUM_SESSIONS = 1000;

        List<ActorRef> sessions = new LinkedList<>();
        // create a lot of session actors
        for (int i = 0; i < NUM_SESSIONS; i++) {
            sessions.add(broadcastActorSystem.actorOf(format("session-%d", i + 1), SessionActor.class));
        }

        final CountDownLatch waitLatch = new CountDownLatch(NUM_SESSIONS);

        // reply actor
        ActorRef replyActor = broadcastActorSystem.tempActorOf(ReplyActor.class, new ActorDelegate<Hello>(false) {
            @Override
            public ActorDelegate<Hello> getBody() {
                return this;
            }

            @Override
            public void onReceive(ActorRef sender, Hello message) throws Exception {
                waitLatch.countDown();
            }
        });

        // add them to the session list
        sessionList.tell(new Add(sessions),replyActor);

        // now send a message
        sessionList.tell(new Hello("How are you?"),replyActor);

        // wait until we're done
        assertTrue(waitLatch.await(30, TimeUnit.SECONDS));
    }

    @Test(enabled = true)
    public void testAddingMultipleTimes() throws Exception {
        ActorSystem broadcastActorSystem = testActorSystem.getActorSystem();

        ActorRef sessionList = broadcastActorSystem.actorOf("sessionList",Broadcaster.class,new BroadcasterState(8,32));

        // @todo: the default shard cache is set to 1024!
        int NUM_SESSIONS = 1000;

        List<ActorRef> sessions = new LinkedList<>();
        // create a lot of session actors
        for (int i = 0; i < NUM_SESSIONS; i++) {
            sessions.add(broadcastActorSystem.actorOf(format("session-%d", i + 1), SessionActor.class));
        }

        final CountDownLatch waitLatch = new CountDownLatch(NUM_SESSIONS);
        final AtomicInteger counter = new AtomicInteger();

        // reply actor
        ActorRef replyActor = broadcastActorSystem.tempActorOf(ReplyActor.class, new ActorDelegate<Hello>(false) {
            @Override
            public ActorDelegate<Hello> getBody() {
                return this;
            }

            @Override
            public void onReceive(ActorRef sender, Hello message) throws Exception {
                waitLatch.countDown();
                counter.incrementAndGet();
            }
        });

        // add them to the session list twice (testing for the same actoref being added to two separate buckets)
        sessionList.tell(new Add(sessions),replyActor);
        sessionList.tell(new Add(sessions),replyActor);

        // now send a message
        sessionList.tell(new Hello("How are you?"),replyActor);

        // wait for a while for all messages to be processed
        assertTrue(waitLatch.await(30, TimeUnit.SECONDS));

        // give the system a chance to process any extra messages
        // no guarantees here, this test might succeed on a very slow machine
        // even when there's a problem with the code
        Thread.sleep(2000);

        // make sure no extra replies have been processed
        assertEquals(counter.intValue(), NUM_SESSIONS);
    }

    @Test
    public void testTreeAfterRehash() throws Exception {
        ActorSystem broadcastActorSystem = testActorSystem.getActorSystem();

        ActorRef sessionList = broadcastActorSystem.actorOf("sessionList",Broadcaster.class,new BroadcasterState(8,32));

        // @todo: the default shard cache is set to 1024!
        int NUM_SESSIONS = 1000;

        List<ActorRef> sessions = new LinkedList<>();
        // create a lot of session actors
        for (int i = 0; i < NUM_SESSIONS; i++) {
            sessions.add(broadcastActorSystem.actorOf(format("session-%d", i + 1), SessionActor.class));
        }

        final CountDownLatch waitLatch = new CountDownLatch(NUM_SESSIONS);
        final AtomicInteger counter = new AtomicInteger();

        // reply actor
        ActorRef replyActor = broadcastActorSystem.tempActorOf(ReplyActor.class, new ActorDelegate<Hello>(false) {
            @Override
            public ActorDelegate<Hello> getBody() {
                return this;
            }

            @Override
            public void onReceive(ActorRef sender, Hello message) throws Exception {
                waitLatch.countDown();
                counter.incrementAndGet();
            }
        });

        final CountDownLatch rehashLatch = new CountDownLatch(1);

        // reply actor for rehashing
        ActorRef rehashingReplyActor = broadcastActorSystem.tempActorOf(ReplyActor.class, new ActorDelegate<RehashComplete>(false) {
            @Override
            public ActorDelegate<RehashComplete> getBody() {
                return this;
            }

            @Override
            public void onReceive(ActorRef sender, RehashComplete message) throws Exception {
                rehashLatch.countDown();
            }
        });

        // add them to the session list twice (testing for the same actoref being added to two separate buckets)
        sessionList.tell(new Add(sessions),replyActor);

        // send the rehash command & wait for completion
        sessionList.tell(new RehashRequest(), rehashingReplyActor);
        assertTrue(rehashLatch.await(30, TimeUnit.SECONDS));

        // now send a message
        sessionList.tell(new Hello("How are you?"),replyActor);

        // wait for a while for all messages to be processed
        assertTrue(waitLatch.await(30, TimeUnit.SECONDS));

        // give the system a chance to process any extra messages
        // no guarantees here, this test might succeed on a very slow machine
        // even when there's a problem with the code
        Thread.sleep(2000);

        // make sure no extra replies have been processed
        assertEquals(counter.intValue(), NUM_SESSIONS);
    }

    @Test(enabled = true)
    public void testWithThrottleConfig() throws Exception {
        ActorSystem broadcastActorSystem = testActorSystem.getActorSystem();

        ActorRef sessionList = broadcastActorSystem.actorOf("sessionList",Broadcaster.class,new BroadcasterState(8,32,new ThrottleConfig(500)));

        // @todo: the default shard cache is set to 1024!
        int NUM_SESSIONS = 1000;

        List<ActorRef> sessions = new LinkedList<>();
        // create a lot of session actors
        for (int i = 0; i < NUM_SESSIONS; i++) {
            sessions.add(broadcastActorSystem.actorOf(format("session-%d", i + 1), SessionActor.class));
        }

        final CountDownLatch waitLatch = new CountDownLatch(NUM_SESSIONS);

        // reply actor
        ActorRef replyActor = broadcastActorSystem.tempActorOf(ReplyActor.class, new ActorDelegate<Hello>(false) {
            @Override
            public ActorDelegate<Hello> getBody() {
                return this;
            }

            @Override
            public void onReceive(ActorRef sender, Hello message) throws Exception {
                waitLatch.countDown();
            }
        });

        // add them to the session list
        sessionList.tell(new Add(sessions),replyActor);

        // now send a message
        sessionList.tell(new Hello("How are you?"),replyActor);

        // wait until we're done

        assertTrue(waitLatch.await(30, TimeUnit.SECONDS));
    }

    @Test(enabled = true)
    public void testWithUpdateThrottleConfig() throws Exception {
        ActorSystem broadcastActorSystem = testActorSystem.getActorSystem();

        ActorRef sessionList = broadcastActorSystem.actorOf("sessionList",Broadcaster.class,new BroadcasterState(8,32));

        // @todo: the default shard cache is set to 1024!
        int NUM_SESSIONS = 1000;

        List<ActorRef> sessions = new LinkedList<>();
        // create a lot of session actors
        for (int i = 0; i < NUM_SESSIONS; i++) {
            sessions.add(broadcastActorSystem.actorOf(format("session-%d", i + 1), SessionActor.class));
        }

        final CountDownLatch waitLatch1 = new CountDownLatch(NUM_SESSIONS);

        // reply actor
        ActorRef replyActor = broadcastActorSystem.tempActorOf(ReplyActor.class, new ActorDelegate<Hello>(false) {
            @Override
            public ActorDelegate<Hello> getBody() {
                return this;
            }

            @Override
            public void onReceive(ActorRef sender, Hello message) throws Exception {
                waitLatch1.countDown();
            }
        });

        // add them to the session list
        sessionList.tell(new Add(sessions),replyActor);

        // now send a message
        sessionList.tell(new Hello("How are you?"),replyActor);

        // wait until we're done

        assertTrue(waitLatch1.await(30, TimeUnit.SECONDS));

        // now set the throttle config
        sessionList.tell(new UpdateThrottleConfig(new ThrottleConfig(250)), null);

        final CountDownLatch waitLatch2 = new CountDownLatch(NUM_SESSIONS);

        // reply actor
        replyActor = broadcastActorSystem.tempActorOf(ReplyActor.class, new ActorDelegate<Hello>(false) {
            @Override
            public ActorDelegate<Hello> getBody() {
                return this;
            }

            @Override
            public void onReceive(ActorRef sender, Hello message) throws Exception {
                waitLatch2.countDown();
            }
        });

        // add them to the session list
        sessionList.tell(new Add(sessions),replyActor);

        // now send a message
        sessionList.tell(new Hello("How are you?"),replyActor);

        // wait until we're done

        assertTrue(waitLatch2.await(30, TimeUnit.SECONDS));
    }
}
