package org.elasticsoftware.elasticactors.broadcast.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.elasticsoftware.elasticactors.ActorRef;
import org.elasticsoftware.elasticactors.base.state.JacksonActorState;

import java.util.*;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.google.common.collect.Maps.newHashMap;

/**
 * @author Joost van de Wijgerd
 */
@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class BroadcasterState extends JacksonActorState<BroadcasterState> {
    private final int bucketsPerNode;
    private final int bucketSize;
    private final List<ActorRef> nodes;
    private final Set<ActorRef> leaves;
    private boolean leafNode = true;
    private ThrottleConfig throttleConfig;
    private final transient Map<String,ThrottledBroadcastSession> throttledBroadcasts = newHashMap();
    private int size = 0;

    public BroadcasterState(int bucketsPerNode, int bucketSize) {
        this.bucketsPerNode = bucketsPerNode;
        this.bucketSize = bucketSize;
        this.leaves = Sets.newHashSet();
        this.nodes = new LinkedList<>();
        this.throttleConfig = null;
    }

    public BroadcasterState(int bucketsPerNode, int bucketSize, ThrottleConfig throttleConfig) {
        this.bucketsPerNode = bucketsPerNode;
        this.bucketSize = bucketSize;
        this.leaves = Sets.newHashSet();
        this.nodes = new LinkedList<>();
        this.throttleConfig = throttleConfig;
    }

    public BroadcasterState(int bucketsPerNode, int bucketSize, Collection<ActorRef> leaves) {
        this.bucketsPerNode = bucketsPerNode;
        this.bucketSize = bucketSize;
        this.leaves = Sets.newHashSet(leaves);
        this.nodes = new LinkedList<>();
        this.size = this.leaves.size();
        this.throttleConfig = null;
    }

    @JsonCreator
    public BroadcasterState(@JsonProperty("bucketsPerNode") int bucketsPerNode,
                            @JsonProperty("bucketSize") int bucketSize,
                            @JsonProperty("nodes") List<ActorRef> nodes,
                            @JsonProperty("leaves") Set<ActorRef> leaves,
                            @JsonProperty("size") int size,
                            @JsonProperty("throttleConfig") ThrottleConfig throttleConfig) {
        this.bucketsPerNode = bucketsPerNode;
        this.bucketSize = bucketSize;
        this.nodes = nodes;
        this.leaves = leaves;
        this.size = size;
        this.throttleConfig = throttleConfig;
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

    public int getSize() {
        return leafNode ? leaves.size() : size;
    }

    public ThrottleConfig getThrottleConfig() {
        return throttleConfig;
    }

    public void setThrottleConfig(ThrottleConfig throttleConfig) {
        this.throttleConfig = throttleConfig;
    }

    public void incrementSize(int increment) {
        size += increment;
    }

    public void decrementSize(int decrement) {
        size -= decrement;
    }

    public void addThrottledBroadcastSession(ThrottledBroadcastSession session) {
        this.throttledBroadcasts.put(session.getId(),session);
    }

    public ThrottledBroadcastSession getThrottledBroadcastSession(String id) {
        return this.throttledBroadcasts.get(id);
    }

    public ThrottledBroadcastSession removeThrottledBroadcastSession(String id) {
        return this.throttledBroadcasts.remove(id);
    }
}
