package org.elasticsoftware.elasticactors.broadcast.handlers;

import org.elasticsoftware.elasticactors.ActorRef;
import org.elasticsoftware.elasticactors.MessageHandler;
import org.elasticsoftware.elasticactors.MethodActor;
import org.elasticsoftware.elasticactors.broadcast.messages.Add;
import org.elasticsoftware.elasticactors.broadcast.messages.rehash.InternalRehashRequest;
import org.elasticsoftware.elasticactors.broadcast.messages.rehash.RehashComplete;
import org.elasticsoftware.elasticactors.broadcast.messages.rehash.RehashRequest;
import org.elasticsoftware.elasticactors.broadcast.messages.rehash.RehashResponse;
import org.elasticsoftware.elasticactors.broadcast.state.BroadcasterState;

import java.util.ArrayList;
import java.util.HashSet;

public final class RehashHandlers extends MethodActor {

    @MessageHandler
    public void handle(RehashRequest rehashRequest, BroadcasterState state, ActorRef sender) {
        if (state.getCurrentlyRehashing()) {
            logger.warn("Broadcaster actor <{}> received rehash request, but is already in the process of rehashing. Ignoring.", getSelf().getActorId());
            return;
        }

        if (state.isLeafNode()) {
            // if the root is a leaf node, there is nothing to do, as the rehash only works if the root has children nodes
            logger.warn("Broadcaster actor <{}> received rehash request, but it only contains a leaf node. Ignoring.", getSelf().getActorId());
        } else {
            state.setCurrentlyRehashing(true);
            state.setRehashRoot(true);
            state.setRehashReplyTo(sender);
            state.setRehashMembers(new HashSet<>());
            state.setExpectedRehashingReplies(state.getNodes().size());
            state.setReceivedRehashingReplies(0);

            for (ActorRef actorRef : state.getNodes()) {
                actorRef.tell(new InternalRehashRequest());
            }
        }
    }

    @MessageHandler
    public void handle(InternalRehashRequest rehashRequest, BroadcasterState state, ActorRef sender) {
        if (state.getCurrentlyRehashing()) {
            logger.warn("Broadcaster actor <{}> received rehash request, but it only contains a leaf node. Ignoring.", getSelf().getActorId());
            return;
        }

        if (state.isLeafNode()) {
            sender.tell(new RehashResponse(state.getLeaves()));
        } else {
            state.setCurrentlyRehashing(true);
            state.setRehashReplyTo(sender);
            state.setRehashMembers(new HashSet<>());
            state.setExpectedRehashingReplies(state.getNodes().size());
            state.setReceivedRehashingReplies(0);

            for (ActorRef actorRef : state.getNodes()) {
                actorRef.tell(new InternalRehashRequest());
            }
        }
    }

    @MessageHandler
    public void handle(RehashResponse rehashResponse, BroadcasterState state) throws Exception {
        state.incrementReceivedRehashingReplies();
        state.getRehashMembers().addAll(rehashResponse.getMembers());

        if (state.getReceivedRehashingReplies().equals(state.getExpectedRehashingReplies())) {
            if (state.getRehashRoot()) {
                // this node is the root of the broadcaster, once all replies are received
                // the tree is deleted & recreated
                for (ActorRef actorRef : state.getNodes()) {
                    getSystem().stop(actorRef);
                }

                state.setLeafNode(true);
                state.getLeaves().clear();
                state.getNodes().clear();

                logger.info("Rehashing of broadcaster <{}> is now completed, re-adding all members to the tree", getSelf().getActorId());

                getSelf().tell(new Add(state.getRehashMembers()));

                // replay the messages received during the rehashing process (both add and remove)
                for (Object message : state.getReceivedDuringRehashing()) {
                    getSelf().tell(message);
                }

                if (state.getRehashReplyTo() != null) {
                    state.getRehashReplyTo().tell(new RehashComplete());
                }
            } else {
                // this node is not the root of the broadcaster, send all it's children further up the tree
                state.getRehashReplyTo().tell(new RehashResponse(state.getRehashMembers()));
            }

            // this node has received all replies it was waiting for, time to mark the state as such
            state.setCurrentlyRehashing(false);
            state.setRehashReplyTo(null);
            state.setRehashMembers(new HashSet<>());
            state.setExpectedRehashingReplies(0);
            state.setReceivedRehashingReplies(0);
            state.setReceivedDuringRehashing(new ArrayList<>());
        }
    }
}
