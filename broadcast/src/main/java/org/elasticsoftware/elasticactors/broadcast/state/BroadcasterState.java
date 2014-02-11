package org.elasticsoftware.elasticactors.broadcast.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Sets;
import org.elasticsoftware.elasticactors.ActorRef;
import org.elasticsoftware.elasticactors.base.state.JacksonActorState;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * @author Joost van de Wijgerd
 */
public final class BroadcasterState extends JacksonActorState<BroadcasterState> {
    private final int bucketsPerNode;
    private final int bucketSize;
    private final List<ActorRef> nodes;
    private final Set<ActorRef> leaves;
    private boolean leafNode = true;

    public BroadcasterState(int bucketsPerNode, int bucketSize) {
        this.bucketsPerNode = bucketsPerNode;
        this.bucketSize = bucketSize;
        this.leaves = Sets.newHashSet();
        this.nodes = new LinkedList<>();
    }

    public BroadcasterState(int bucketsPerNode, int bucketSize, Collection<ActorRef> leaves) {
        this.bucketsPerNode = bucketsPerNode;
        this.bucketSize = bucketSize;
        this.leaves = Sets.newHashSet(leaves);
        this.nodes = new LinkedList<>();
    }

    @JsonCreator
    public BroadcasterState(@JsonProperty("bucketsPerNode") int bucketsPerNode,
                            @JsonProperty("bucketSize") int bucketSize,
                            @JsonProperty("nodes") List<ActorRef> nodes,
                            @JsonProperty("leaves") Set<ActorRef> leaves) {
        this.bucketsPerNode = bucketsPerNode;
        this.bucketSize = bucketSize;
        this.nodes = nodes;
        this.leaves = leaves;
    }

    @Override
    public BroadcasterState getBody() {
        return this;
    }

    public int getBucketsPerNode() {
        return bucketsPerNode;
    }

    public int getBucketSize() {
        return bucketSize;
    }

    public boolean isLeafNode() {
        return leafNode;
    }

    public void setLeafNode(boolean leafNode) {
        this.leafNode = leafNode;
    }

    public List<ActorRef> getNodes() {
        return nodes;
    }

    public Set<ActorRef> getLeaves() {
        return leaves;
    }
}
