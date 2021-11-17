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

package org.elasticsoftware.elasticactors.geoevents.actors;

import ch.hsr.geohash.GeoHash;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import org.elasticsoftware.elasticactors.Actor;
import org.elasticsoftware.elasticactors.ActorRef;
import org.elasticsoftware.elasticactors.UntypedActor;
import org.elasticsoftware.elasticactors.base.serialization.JacksonSerializationFramework;
import org.elasticsoftware.elasticactors.base.state.JacksonActorState;
import org.elasticsoftware.elasticactors.geoevents.LengthUnit;
import org.elasticsoftware.elasticactors.geoevents.messages.DeRegisterInterest;
import org.elasticsoftware.elasticactors.geoevents.messages.EnterEvent;
import org.elasticsoftware.elasticactors.geoevents.messages.LeaveEvent;
import org.elasticsoftware.elasticactors.geoevents.messages.PublishLocation;
import org.elasticsoftware.elasticactors.geoevents.messages.RegisterInterest;
import org.elasticsoftware.elasticactors.geoevents.messages.ScanRequest;
import org.elasticsoftware.elasticactors.geoevents.messages.ScanResponse;
import org.elasticsoftware.elasticactors.geoevents.messages.UnpublishLocation;
import org.elasticsoftware.elasticactors.geoevents.util.GeoHashUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * @author Joost van de Wijgerd
 */
@Actor(stateClass = Region.State.class, serializationFramework = JacksonSerializationFramework.class)
public final class Region extends UntypedActor {

    private final static Logger staticLogger = LoggerFactory.getLogger(Region.class);

    @Override
    protected Logger initLogger() {
        return staticLogger;
    }

    @JsonTypeName()
    public static final class State extends JacksonActorState<Region.State> {

        private final GeoHash geoHash;
        private final List<RegisterInterest> listeners;
        private final SortedMap<Long,PublishLocation> publishedLocations;

        public State(GeoHash geoHash) {
            this(geoHash, new LinkedList<>(), new TreeMap<>());
        }

        @Override
        public State getBody() {
            return this;
        }

        @JsonCreator
        public State(@JsonProperty("geoHash") GeoHash geoHash,
                     @JsonProperty("listeners") List<RegisterInterest> listeners,
                     @JsonProperty("publishedLocations") SortedMap<Long,PublishLocation> publishedLocations) {
            this.geoHash = geoHash;
            this.listeners = listeners;
            this.publishedLocations = publishedLocations;
        }

        @JsonProperty("geoHash")
        public GeoHash getId() {
            return geoHash;
        }

        @JsonProperty("listeners")
        public List<RegisterInterest> getListeners() {
            return listeners;
        }

        @JsonProperty("publishedLocations")
        public SortedMap<Long,PublishLocation> getPublishedLocations() {
            return publishedLocations;
        }

        public SortedMap<Long,PublishLocation> prunePublishedLocations(Long now) {
            SortedMap<Long,PublishLocation> tailMap = publishedLocations.tailMap(now);
            // prune the head
            Iterator itr = publishedLocations.headMap(now).entrySet().iterator();
            while(itr.hasNext()) {
                itr.next();
                itr.remove();
            }
            return tailMap;
        }
    }

    @Override
    public void onReceive(ActorRef sender, Object message) throws Exception {
        if(message instanceof PublishLocation) {
            handle((PublishLocation) message);
        } else if(message instanceof RegisterInterest) {
            handle((RegisterInterest) message);
        } else if(message instanceof DeRegisterInterest) {
            handle((DeRegisterInterest) message);
        } else if(message instanceof UnpublishLocation) {
            handle((UnpublishLocation) message);
        } else if(message instanceof ScanRequest) {
            handle((ScanRequest) message,sender);
        } else {
            // @Todo log error
        }
    }

    private void handle(ScanRequest message,ActorRef receiver) {
        State state = getState(State.class);
        long lastSeen = System.currentTimeMillis();
        SortedMap<Long,PublishLocation> publishedLocations = state.prunePublishedLocations(lastSeen);
        List<ScanResponse.ScanResult> scanResults = new LinkedList<>();
        for (PublishLocation publishedLocation : publishedLocations.values()) {
            double distance = publishedLocation.getLocation().distance(message.getLocation(), LengthUnit.METRES);
            if(distance <= message.getRadiusInMetres()) {
                scanResults.add(new ScanResponse.ScanResult(publishedLocation,(int)distance));
            }
        }
        receiver.tell(new ScanResponse(message.getId(),scanResults),getSelf());
    }

    private void handle(UnpublishLocation message) {
        State state = getState(State.class);
        // generate lastSeen for new publish event
        long lastSeen = System.currentTimeMillis();
        SortedMap<Long,PublishLocation> publishedLocations = state.prunePublishedLocations(lastSeen);
        Iterator<Map.Entry<Long,PublishLocation>> entryIterator = publishedLocations.entrySet().iterator();
        while (entryIterator.hasNext()) {
            Map.Entry<Long, PublishLocation> entry = entryIterator.next();
            if(entry.getValue().getLocation().equals(message.getLocation()) &&
               entry.getValue().getRef().equals(message.getRef())) {
                entryIterator.remove();
                for (RegisterInterest listener : state.getListeners()) {
                    if(!listener.getActorRef().equals(message.getRef())) {
                        double distance = listener.getLocation().distance(message.getLocation(), LengthUnit.METRES);
                        if(distance <= listener.getRadiusInMetres()) {
                            fireLeaveEvent(listener.getActorRef(),message);
                        }
                    }
                }
            }
        }
    }

    private void handle(PublishLocation message) {
        // generate lastSeen for new publish event
        long lastSeen = System.currentTimeMillis();
        // iterate through all interested listeners
        State state = getState(State.class);
        for (RegisterInterest listener : state.getListeners()) {
            if(!listener.getActorRef().equals(message.getRef())) {
                double distance = listener.getLocation().distance(message.getLocation(), LengthUnit.METRES);
                if(distance <= listener.getRadiusInMetres()) {
                    fireEnterEvent(listener.getActorRef(),message,(int)distance,lastSeen);
                }
            }
        }
        // add to the events map
        state.publishedLocations.put(lastSeen+TimeUnit.MILLISECONDS.convert(message.getTtlInSeconds(),TimeUnit.SECONDS),message);
    }

    private void fireEnterEvent(ActorRef receiver,PublishLocation originalMessage,int distance,long lastSeen) {
        receiver.tell(new EnterEvent(originalMessage.getRef(),
                                     originalMessage.getLocation(),
                                     distance,
                                     lastSeen,
                                     originalMessage.getCustomProperties()),getSelf());
    }

    private void fireLeaveEvent(ActorRef receiver,UnpublishLocation originalMessage) {
        receiver.tell(new LeaveEvent(originalMessage.getRef()),getSelf());
    }

    private void handle(RegisterInterest message) {
        // add interest
        State state = getState(State.class);
        state.getListeners().add(message);
        // iterate over the (pruned) previously published locations
        state.prunePublishedLocations(System.currentTimeMillis());
    }
    
    private void handle(DeRegisterInterest message) {
        State state = getState(State.class);
        ListIterator<RegisterInterest> itr = state.getListeners().listIterator();
        while (itr.hasNext()) {
            RegisterInterest registeredInterest = itr.next();
            if(registeredInterest.getActorRef().equals(message.getActorRef()) &&
               registeredInterest.getLocation().equals(message.getLocation())) {
                itr.remove();
                if(message.isPropagate()) {
                    // remove without propagating
                    removeFromOtherRegions(state.getId(),registeredInterest,new DeRegisterInterest(message.getActorRef(),message.getLocation(),false));
                }
            }
        }
    }

    private void removeFromOtherRegions(GeoHash currentHash,RegisterInterest registeredInterest,DeRegisterInterest message) {
        //@todo: based on the radius, the location and the the current region find any other regions that
        //@todo: have registered the interest
        List<GeoHash> otherRegions = GeoHashUtils.getAllGeoHashesWithinRadius(registeredInterest.getLocation().getLatitude(),
                                                                              registeredInterest.getLocation().getLongitude(),
                                                                              registeredInterest.getRadiusInMetres(),LengthUnit.METRES,
                                                                              currentHash.significantBits()/5);
        // remove myself
        otherRegions.remove(currentHash);
        // deregister at the other locations
        for (GeoHash region : otherRegions) {
            getSystem().actorFor(String.format("regions/%s",region.toBase32())).tell(message,getSelf());
        }
    }

}
