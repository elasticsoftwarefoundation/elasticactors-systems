package org.elasticsoftware.elasticactors.broadcast;

import org.apache.log4j.BasicConfigurator;
import org.elasticsoftware.elasticactors.ActorRef;
import org.elasticsoftware.elasticactors.ActorSystem;
import org.elasticsoftware.elasticactors.base.actors.ActorDelegate;
import org.elasticsoftware.elasticactors.base.actors.ReplyActor;
import org.elasticsoftware.elasticactors.broadcast.messages.Add;
import org.elasticsoftware.elasticactors.broadcast.messages.Hello;
import org.elasticsoftware.elasticactors.broadcast.state.BroadcasterState;
import org.elasticsoftware.elasticactors.test.TestActorSystem;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;
import static org.testng.Assert.assertTrue;

/**
 * @author Joost van de Wijgerd
 */
public class BroadcastActorSystemTest {
    private TestActorSystem testActorSystem;

    @BeforeMethod(enabled = true)
    public void setUp() {
        BasicConfigurator.resetConfiguration();
        BasicConfigurator.configure();
        testActorSystem = new TestActorSystem();
        testActorSystem.initialize();
    }

    @AfterMethod(enabled = true)
    public void tearDown() {
        if(testActorSystem != null) {
            testActorSystem.destroy();
            testActorSystem = null;
        }
        BasicConfigurator.resetConfiguration();
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

        assertTrue(waitLatch.await(1, TimeUnit.HOURS));
    }
}
