package org.elasticsoftware.elasticactors.broadcast;

import com.google.common.base.Charsets;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.hash.Hashing;
import org.elasticsoftware.elasticactors.*;
import org.elasticsoftware.elasticactors.base.serialization.JacksonSerializationFramework;
import org.elasticsoftware.elasticactors.broadcast.messages.Add;
import org.elasticsoftware.elasticactors.broadcast.messages.Remove;
import org.elasticsoftware.elasticactors.broadcast.state.BroadcasterState;
import org.elasticsoftware.elasticactors.state.PersistenceConfig;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static java.lang.String.format;

/**
 * @author Joost van de Wijgerd
 */
@Actor(stateClass = BroadcasterState.class, serializationFramework = JacksonSerializationFramework.class)
@PersistenceConfig(persistOnMessages = false, included = {Add.class, Remove.class})
public final class Broadcaster extends MethodActor {

    @Override
    public void postCreate(ActorRef creator) throws Exception {
        BroadcasterState state = getState(BroadcasterState.class);
        // see if we need to scale out
        if(state.getLeaves().size() > state.getBucketSize()) {
            rehash(state);
        }
    }

    @MessageHandler
    public void handleRemove(Remove remove,BroadcasterState state) {
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

    @Override
    protected void onUnhandled(ActorRef sender, Object message) {
        BroadcasterState state = getState(BroadcasterState.class);
        // pass the message on
        if(state.isLeafNode()) {
            for (ActorRef actorRef : state.getLeaves()) {
                actorRef.tell(message,sender);
            }
        } else {
            for (ActorRef actorRef : state.getNodes()) {
                actorRef.tell(message,sender);
            }
        }
    }

    private Multimap<ActorRef, ActorRef> mapToBucket(Set<ActorRef> members, BroadcasterState state) {
        Multimap<ActorRef,ActorRef> sendMap = ArrayListMultimap.create();
        for (ActorRef actorRef : members) {
            int idx = Math.abs(Hashing.murmur3_32().hashString(format("%s:%s",getSelf().getActorId(),actorRef.toString()), Charsets.UTF_8).asInt()) % state.getBucketsPerNode();
            sendMap.put(state.getNodes().get(idx), actorRef);
        }
        return sendMap;
    }

    private Multimap<String, ActorRef> mapToBucket(Set<ActorRef> members, List<String> nodeIds) {
        Multimap<String,ActorRef> sendMap = ArrayListMultimap.create();
        for (ActorRef actorRef : members) {
            int idx = Math.abs(Hashing.murmur3_32().hashString(format("%s:%s", getSelf().getActorId(), actorRef.toString()), Charsets.UTF_8).asInt()) % nodeIds.size();
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
        for (String actorId : sendMap.asMap().keySet()) {
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
