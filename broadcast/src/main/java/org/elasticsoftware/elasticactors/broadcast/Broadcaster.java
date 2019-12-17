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
import org.elasticsoftware.elasticactors.broadcast.state.ThrottleConfig;
import org.elasticsoftware.elasticactors.broadcast.state.ThrottledBroadcastSession;
import org.elasticsoftware.elasticactors.state.PersistenceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.core.env.Environment;

import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.collect.Sets.newHashSet;
import static org.elasticsoftware.elasticactors.state.ActorLifecycleStep.CREATE;

import static java.lang.String.format;

/**
 * @author Joost van de Wijgerd
 */
@Actor(stateClass = BroadcasterState.class, serializationFramework = JacksonSerializationFramework.class)
@PersistenceConfig(persistOnMessages = false, included = {Add.class, Remove.class, UpdateThrottleConfig.class}, persistOn = {CREATE})
@MessageHandlers(RehashHandlers.class)
@Configurable
public final class Broadcaster extends MethodActor {
    private static final Logger logger = LoggerFactory.getLogger(Broadcaster.class);
    private static final Pattern EXPRESSION_PATTERN = Pattern.compile(
            "^\\$\\{([^:]+)(?::([^:]+))?}$");
    private JacksonSerializationFramework serializationFramework;
    private Environment environment;
    private final Map<Class<?>, ThrottleConfig> throttleConfigCache = new ConcurrentHashMap<>();

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

    private ThrottleConfig getThrottleConfig(Object message) {
        return throttleConfigCache.computeIfAbsent(message.getClass(), this::resolveThrottleConfig);
    }

    private ThrottleConfig resolveThrottleConfig(Class<?> messageClass) {
        ThrottleConfig throttleConfig = new ThrottleConfig(getMaxMessagesPerSecond(messageClass));
        logger.debug(
                "Resolved broadcast throttling config {} for class {}",
                throttleConfig,
                messageClass.getName());
        return throttleConfig;
    }

    private int getMaxMessagesPerSecond(Class<?> messageClass) {
        Throttled throttled = messageClass.getAnnotation(Throttled.class);
        if (throttled != null) {
            try {
                Matcher m = EXPRESSION_PATTERN.matcher(throttled.maxPerSecond());
                if (m.matches()) {
                    try {
                        return environment.getRequiredProperty(m.group(1), Integer.class);
                    } catch (IllegalStateException e) {
                        String defaultValue = m.group(2);
                        if (defaultValue != null) {
                            return Integer.parseInt(defaultValue);
                        } else {
                            throw e;
                        }
                    }
                }
                return Integer.parseInt(throttled.maxPerSecond());
            } catch (Exception e) {
                logger.error(
                        "Could not parse throttling configuration for message class {}",
                        messageClass.getName(),
                        e);
            }
        }
        return 0;
    }

    @Autowired
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Autowired
    public void setSerializationFramework(JacksonSerializationFramework serializationFramework) {
        this.serializationFramework = serializationFramework;
    }

    @MessageHandler
    public void handleRemove(Remove remove,BroadcasterState state) {
        if (state.getCurrentlyRehashing()) {
            if (state.getRehashRoot()) {
                logger.info("Received remove request, but broadcaster [{}] is currently rehashing. Saving message for when rehashing will be done", getSelf().getActorId());
                state.getReceivedDuringRehashing().add(remove);
            } else {
                logger.error("Received remove request, but broadcaster [{}] is currently rehashing and is not the root!. This should not happen, ignoring remove request.", getSelf().getActorId());
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
                logger.info("Received add request, but broadcaster [{}] is currently rehashing. Saving message for when rehashing will be done", getSelf().getActorId());
                state.getReceivedDuringRehashing().add(add);
            } else {
                logger.error("Received add request, but broadcaster [{}] is currently rehashing and is not the root!. This should not happen, ignoring add request.", getSelf().getActorId());
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
    public void handleUpdateThrottleConfig(
            UpdateThrottleConfig updateThrottleConfig,
            BroadcasterState state,
            ActorRef sender) {
        logger.warn("Received an attempt to update the throttle config from actor [{}]. "
                + "This has been deprecated and should not be used anymore.", sender);
        state.setThrottleConfig(updateThrottleConfig.getThrottleConfig());
    }

    @MessageHandler
    public void handleLeafNodesRequest(LeafNodesRequest request,BroadcasterState state, ActorRef parent) {
        ActorRef self = getSelf();
        if(state.isLeafNode()) {
            // return ourselves
            logger.debug(
                    "Node[{}]: broadcast [{}] reached leaf, sending myself to the parent [{}]",
                    self.getActorId(),
                    request.getBroadcastId(),
                    parent.getActorId());
            parent.tell(new LeafNodesResponse(request.getBroadcastId(), newHashSet(self)));
        } else {
            // start a session (so we know when to return the response to the parent node)
            logger.debug(
                    "Node [{}]: broadcast [{}] reached node, forwarding the request to children nodes",
                    self.getActorId(),
                    request.getBroadcastId());
            state.addThrottledBroadcastSession(new ThrottledBroadcastSession(request.getBroadcastId(),parent));
            // forward the request to the other nodes
            for (ActorRef actorRef : state.getNodes()) {
                logger.trace(
                        "Node [{}]: forwarding broadcast [{}] to children node [{}]",
                        self.getActorId(),
                        request.getBroadcastId(),
                        actorRef.getActorId());
                actorRef.tell(request, self);
            }
        }
    }

    @MessageHandler
    public void handleLeafNodesResponse(LeafNodesResponse response, BroadcasterState state, ActorSystem actorSystem, ActorRef child) {
        // the throttling session
        ThrottledBroadcastSession session = state.getThrottledBroadcastSession(response.getBroadcastId());
        ActorRef self = getSelf();
        if(session != null) {
            session.handleLeafNodesResponse(response);
            if(session.isReady(state.getNodes().size())) {
                // if the parent is set, create a new response and send it up the chain
                if(session.getParent() != null) {
                    logger.debug(
                            "Node [{}]: sending response of session [{}] to the parent [{}] with {} leaf nodes",
                            self.getActorId(),
                            session.getId(),
                            session.getParent().getActorId(),
                            session.getLeafNodes().size());
                    session.getParent().tell(new LeafNodesResponse(session.getId(),session.getLeafNodes()));
                } else {
                    // this is the actual throttling action
                    logger.debug(
                            "Node [{}]: got throttling action for session [{}]",
                            self.getActorId(),
                            session.getId());
                    throttle(session, state, actorSystem);
                }
                // and clear the session
                logger.debug(
                        "Node [{}]: removing the broadcast session [{}]",
                        self.getActorId(),
                        session.getId());
                state.removeThrottledBroadcastSession(session.getId());
            } else {
                logger.debug(
                        "Node [{}]: session [{}] is not ready yet. Number of nodes = {}, received = {}",
                        self.getActorId(),
                        session.getId(),
                        state.getNodes().size(),
                        session.getReceivedResponses());
            }
        } else {
            logger.warn(
                    "Node [{}]: got response from child node [{}], but couldn't find session [{}]",
                    self.getActorId(),
                    child,
                    response.getBroadcastId());
        }
    }

    @MessageHandler
    public void handleThrottledMessage(ThrottledMessage message) {
        try {
            logger.debug(
                    "Node [{}]: handling ThrottledMessage of class [{}] received from [{}]",
                    getSelf().getActorId(),
                    message.getMessageClass(),
                    message.getSender());

            // build up the original message
            Class<?> messageClass = Class.forName(message.getMessageClass());

            Object originalMessage = serializationFramework.getObjectMapper().readValue(message.getMessageData(), messageClass);

            // delegate to onUnhandled
            onUnhandled(message.getSender(), originalMessage);
        } catch(Exception e) {
            logger.error("Unexpected Exception scheduling throttled message of type [{}] from sender [{}]", message.getMessageClass(), message.getSender(), e);
        }
    }

    private void throttle(ThrottledBroadcastSession session, BroadcasterState state, ActorSystem actorSystem) {
        // first calculate the delay
        int maxPerSecond = session.getThrottleConfig().getMaxMessagesPerSecond();
        int maxPerBatch = state.getBucketSize();

        long delayInMillis = (long) ((1000.0d / maxPerSecond) * maxPerBatch);

        try {
            // serialize the original message
            String messageData = serializationFramework.getObjectMapper().writeValueAsString(session.getMessage());

            ThrottledMessage message = new ThrottledMessage(
                    session.getSender(),
                    session.getMessage().getClass().getName(),
                    messageData);

            // now schedule the delays
            long count = 0;
            ActorRef self = getSelf();
            for (ActorRef leafNode : session.getLeafNodes()) {
                long messageDelay = count * delayInMillis;
                logger.trace(
                        "Node [{}]: scheduling throttled message of type [{}] to leaf node [{}] in {} ms",
                        self.getActorId(),
                        session.getMessage().getClass().getName(),
                        leafNode.getActorId(),
                        messageDelay);
                actorSystem.getScheduler().scheduleOnce(self, message, leafNode, messageDelay, TimeUnit.MILLISECONDS);
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
        ActorRef self = getSelf();
        if(state.isLeafNode()) {
            logger.debug(
                    "Node [{}]: leaf got message of type [{}]",
                    self.getActorId(),
                    message.getClass().getName());
            for (ActorRef actorRef : state.getLeaves()) {
                logger.trace("Node [{}]: sending message of type [{}] to [{}]",
                        self.getActorId(),
                        message.getClass().getName(),
                        actorRef);
                actorRef.tell(message,sender);
            }
        } else {
            // see if we have a throttle config set
            ThrottleConfig throttleConfig = getThrottleConfig(message);
            if (throttleConfig.isValid()) {
                // create a new throttle session
                ThrottledBroadcastSession throttledBroadcastSession = new ThrottledBroadcastSession(
                        message,
                        sender,
                        throttleConfig);
                state.addThrottledBroadcastSession(throttledBroadcastSession);
                logger.debug(
                        "Node [{}]: initiating throttled broadcast [{}] with {} messages/sec for message of type [{}]",
                        self.getActorId(),
                        throttledBroadcastSession.getId(),
                        throttleConfig.getMaxMessagesPerSecond(),
                        message.getClass().getName());
                // and send a request to the nodes
                LeafNodesRequest request = new LeafNodesRequest(throttledBroadcastSession.getId());
                for (ActorRef actorRef : state.getNodes()) {
                    logger.trace("Node [{}]: sending leaf node request for broadcast [{}] to [{}]",
                            self.getActorId(),
                            request.getBroadcastId(),
                            actorRef.getActorId());
                    actorRef.tell(request, getSelf());
                }
            } else {
                // just broadcast
                logger.debug(
                        "Node [{}]: broadcasting message of type [{}]",
                        self.getActorId(),
                        message.getClass().getName());
                for (ActorRef actorRef : state.getNodes()) {
                    logger.trace("Node [{}]: sending message of type [{}] to [{}]",
                            self.getActorId(),
                            message.getClass().getName(),
                            actorRef.getActorId());
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
