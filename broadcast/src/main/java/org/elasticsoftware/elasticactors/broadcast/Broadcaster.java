package org.elasticsoftware.elasticactors.broadcast;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.hash.Hashing;
import org.elasticsoftware.elasticactors.Actor;
import org.elasticsoftware.elasticactors.ActorRef;
import org.elasticsoftware.elasticactors.ActorSystem;
import org.elasticsoftware.elasticactors.MessageHandler;
import org.elasticsoftware.elasticactors.MessageHandlers;
import org.elasticsoftware.elasticactors.MethodActor;
import org.elasticsoftware.elasticactors.base.serialization.JacksonSerializationFramework;
import org.elasticsoftware.elasticactors.broadcast.handlers.RehashHandlers;
import org.elasticsoftware.elasticactors.broadcast.messages.Add;
import org.elasticsoftware.elasticactors.broadcast.messages.LeafNodesRequest;
import org.elasticsoftware.elasticactors.broadcast.messages.LeafNodesResponse;
import org.elasticsoftware.elasticactors.broadcast.messages.Remove;
import org.elasticsoftware.elasticactors.broadcast.messages.Throttled;
import org.elasticsoftware.elasticactors.broadcast.messages.ThrottledMessage;
import org.elasticsoftware.elasticactors.broadcast.messages.UpdateThrottleConfig;
import org.elasticsoftware.elasticactors.broadcast.state.BroadcasterState;
import org.elasticsoftware.elasticactors.broadcast.state.ThrottledBroadcastSession;
import org.elasticsoftware.elasticactors.state.PersistenceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;

import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.google.common.collect.Sets.newHashSet;
import static org.elasticsoftware.elasticactors.state.ActorLifecycleStep.CREATE;

import static java.lang.String.format;

/**
 * @author Joost van de Wijgerd
 */
@Actor(stateClass = BroadcasterState.class, serializationFramework = JacksonSerializationFramework.class)
@PersistenceConfig(persistOnMessages = false, included = {Add.class, Remove.class}, persistOn = {CREATE})
@MessageHandlers(RehashHandlers.class)
@Configurable
public final class Broadcaster extends MethodActor {
    private static final Logger logger = LoggerFactory.getLogger(Broadcaster.class);
    private JacksonSerializationFramework serializationFramework;

    @Override
    public void postCreate(ActorRef creator) throws Exception {
        BroadcasterState state = getState(BroadcasterState.class);
        // see if we need to scale out
        if(state.getLeaves().size() > state.getBucketSize()) {
            rehash(state);
        }
    }

    @Override
    public void postActivate(String previousVersion) throws Exception {

    }

    @Override
    public void preDestroy(ActorRef destroyer) throws Exception {
        super.preDestroy(destroyer);

        BroadcasterState state = getState(BroadcasterState.class);

        if (!state.isLeafNode()) {
            for (ActorRef actorRef : state.getNodes()) {
                getSystem().stop(actorRef);
            }
        }
    }

    @Autowired
    public void setSerializationFramework(JacksonSerializationFramework serializationFramework) {
        this.serializationFramework = serializationFramework;
    }

    @MessageHandler
    public void handleRemove(Remove remove,BroadcasterState state) {
        if (state.getCurrentlyRehashing()) {
            if (state.getRehashRoot()) {
                logger.info("Received remove request, but broadcaster <{}> is currently rehashing. Saving message for when rehashing will be done", getSelf().getActorId());
                state.getReceivedDuringRehashing().add(remove);
            } else {
                logger.error("Received remove request, but broadcaster <{}> is currently rehashing and is not the root!. This should not happen, ignoring remove request.", getSelf().getActorId());
            }

            return;
        }

        if(state.isLeafNode()) {
            state.getLeaves().removeAll(remove.getMembers());
        } else {
            // hash and send to other nodes
            Multimap<ActorRef, ActorRef> sendMap = mapToBucket(remove.getMembers(), state);
            // now send em all a message
            for (ActorRef actorRef : sendMap.keys()) {
                actorRef.tell(new Remove(sendMap.get(actorRef)),getSelf());
            }
            // this is an approximation
            state.decrementSize(remove.getMembers().size());
        }
    }

    @MessageHandler
    public void handleAdd(Add add,BroadcasterState state) throws Exception {
        if (state.getCurrentlyRehashing()) {
            if (state.getRehashRoot()) {
                logger.info("Received add request, but broadcaster <{}> is currently rehashing. Saving message for when rehashing will be done", getSelf().getActorId());
                state.getReceivedDuringRehashing().add(add);
            } else {
                logger.error("Received add request, but broadcaster <{}> is currently rehashing and is not the root!. This should not happen, ignoring add request.", getSelf().getActorId());
            }

            return;
        }

        if(state.isLeafNode()) {
            // add to leaves
            state.getLeaves().addAll(add.getMembers());
            // see if we need to scale out
            if(state.getLeaves().size() > state.getBucketSize()) {
                rehash(state);
            }
        } else {
            // hash and send to other nodes
            Multimap<ActorRef, ActorRef> sendMap = mapToBucket(add.getMembers(), state);
            // now send em all a message
            for (ActorRef actorRef : sendMap.asMap().keySet()) {
                actorRef.tell(new Add(sendMap.get(actorRef)),getSelf());
            }
            // this is an approximation!
            state.incrementSize(add.getMembers().size());
        }
    }

    @MessageHandler
    public void handleUpdateThrottleConfig(UpdateThrottleConfig updateThrottleConfig, ActorRef sender) {
        logger.warn("Received an attempt to update the throttle config from actor [{}]. This should not happen!", sender);
    }

    @MessageHandler
    public void handleLeafNodesRequest(LeafNodesRequest request,BroadcasterState state, ActorRef parent) {
        if(state.isLeafNode()) {
            // return ourselves
            parent.tell(new LeafNodesResponse(request.getBroadcastId(), newHashSet(getSelf())));
        } else {
            // start a session (so we know when to return the response to the parent node)
            state.addThrottledBroadcastSession(new ThrottledBroadcastSession(request.getBroadcastId(),parent));
            // forward the request to the other nodes
            for (ActorRef actorRef : state.getNodes()) {
                actorRef.tell(request,getSelf());
            }
        }
    }

    @MessageHandler
    public void handleLeafNodesResponse(LeafNodesResponse response, BroadcasterState state, ActorSystem actorSystem, ActorRef child) {
        // the throttling session
        ThrottledBroadcastSession session = state.getThrottledBroadcastSession(response.getBroadcastId());
        if(session != null) {
            session.handleLeafNodesResponse(response);
            if(session.isReady(state.getNodes().size())) {
                // if the parent is set, create a new response and send it up the chain
                if(session.getParent() != null) {
                    session.getParent().tell(new LeafNodesResponse(session.getId(),session.getLeafNodes()));
                } else {
                    // this is the actual throttling action
                    throttle(session, state, actorSystem);
                }
                // and clear the session
                state.removeThrottledBroadcastSession(session.getId());
            }
        }
    }

    @MessageHandler
    public void handleThrottledMessage(ThrottledMessage message) {
        try {
            // build up the original message
            Class messageClass = Class.forName(message.getMessageClass());

            Object originalMessage = serializationFramework.getObjectMapper().readValue(message.getMessageData(), messageClass);

            // delegate to onUnhandled
            onUnhandled(message.getSender(), originalMessage);
        } catch(Exception e) {
            logger.error("Unexpected Exception scheduling throttled message of type [{}] from sender [{}]", message.getMessageClass(), message.getSender(), e);
        }
    }

    private void throttle(ThrottledBroadcastSession session, BroadcasterState state, ActorSystem actorSystem) {
        // first calculate the delay
        int maxPerSecond = session.getMessage().getThrottleConfig().getMaxMessagesPerSecond();
        int maxPerBatch = state.getBucketSize();

        long delayInMillis = (long) ((1000.0d / maxPerSecond) * maxPerBatch);

        try {
            // serialize the original message
            String messageData = serializationFramework.getObjectMapper().writeValueAsString(session.getMessage());

            ThrottledMessage message = new ThrottledMessage(
                    session.getSender(),
                    session.getMessage().getClass().getName(),
                    messageData,
                    session.getMessage().getThrottleConfig());

            // now schedule the delays
            long count = 0;
            for (ActorRef leafNode : session.getLeafNodes()) {
                actorSystem.getScheduler().scheduleOnce(getSelf(), message, leafNode, (count * delayInMillis), TimeUnit.MILLISECONDS);
                count += 1;
            }
        } catch(Exception e) {
            logger.error("Unexpected Exception scheduling throttled message of type [{}] from sender [{}]", session.getMessage().getClass().getName(), session.getSender(), e);
        }
    }

    @Override
    protected void onUnhandled(ActorRef sender, Object message) {
        BroadcasterState state = getState(BroadcasterState.class);
        // pass the message on
        if(state.isLeafNode()) {
            for (ActorRef actorRef : state.getLeaves()) {
                actorRef.tell(message,sender);
            }
        } else {
            // see if we have a throttle config set
            if (message instanceof Throttled && ((Throttled) message).getThrottleConfig() != null) {
                // create a new throttle session
                ThrottledBroadcastSession throttledBroadcastSession = new ThrottledBroadcastSession(
                        (Throttled) message,
                        sender);
                state.addThrottledBroadcastSession(throttledBroadcastSession);
                // and send a request to the nodes
                LeafNodesRequest request = new LeafNodesRequest(throttledBroadcastSession.getId());
                for (ActorRef actorRef : state.getNodes()) {
                    actorRef.tell(request, getSelf());
                }
            } else {
                // just broadcast
                for (ActorRef actorRef : state.getNodes()) {
                    actorRef.tell(message, sender);
                }
            }
        }
    }

    private Multimap<ActorRef, ActorRef> mapToBucket(Set<ActorRef> members, BroadcasterState state) {
        Multimap<ActorRef,ActorRef> sendMap = ArrayListMultimap.create();
        for (ActorRef actorRef : members) {
            int idx = Math.abs(Hashing.murmur3_32().hashString(format("%s:%s",getSelf().getActorId(),actorRef), StandardCharsets.UTF_8).asInt()) % state.getBucketsPerNode();
            sendMap.put(state.getNodes().get(idx), actorRef);
        }
        return sendMap;
    }

    private Multimap<String, ActorRef> mapToBucket(Set<ActorRef> members, List<String> nodeIds) {
        Multimap<String,ActorRef> sendMap = ArrayListMultimap.create();
        for (ActorRef actorRef : members) {
            int idx = Math.abs(Hashing.murmur3_32().hashString(format("%s:%s", getSelf().getActorId(), actorRef), StandardCharsets.UTF_8).asInt()) % nodeIds.size();
            sendMap.put(nodeIds.get(idx), actorRef);
        }
        return sendMap;
    }

    private void rehash(BroadcasterState state) throws Exception {
        List<String> nodeIds = new LinkedList<>();
        // create nodes
        for (int i = 0; i < state.getBucketsPerNode(); i++) {
            nodeIds.add(format("%s/%d",getSelf().getActorId(),i));
        }
        // map to buckets
        Multimap<String,ActorRef> sendMap = mapToBucket(state.getLeaves(),nodeIds);
        ActorSystem actorSystem = getSystem();
        // now create the new leave nodes
        for (String actorId : nodeIds) {
            ActorRef actorRef = actorSystem.actorOf(actorId,Broadcaster.class,new BroadcasterState(state.getBucketsPerNode(),state.getBucketSize(),sendMap.get(actorId)));
            state.getNodes().add(actorRef);
        }
        // store size
        state.incrementSize(state.getLeaves().size());
        // clear leaves
        state.getLeaves().clear();
        state.setLeafNode(false);
    }
}
