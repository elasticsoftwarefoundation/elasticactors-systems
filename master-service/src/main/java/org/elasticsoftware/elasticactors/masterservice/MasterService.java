package org.elasticsoftware.elasticactors.masterservice;

import org.elasticsoftware.elasticactors.ActorRef;
import org.elasticsoftware.elasticactors.ActorSystem;
import org.elasticsoftware.elasticactors.MethodActor;
import org.elasticsoftware.elasticactors.PhysicalNode;
import org.elasticsoftware.elasticactors.ServiceActor;
import org.elasticsoftware.elasticactors.cluster.ClusterEventListener;
import org.elasticsoftware.elasticactors.cluster.ClusterService;
import org.elasticsoftware.elasticactors.masterservice.messages.MasterElected;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Joost van de Wijgerd
 */
public abstract class MasterService extends MethodActor implements ClusterEventListener {
    // @todo: we need this because leadership events are generated before the ElasticActors runtime is up
    private final AtomicReference<MasterElected> pendingMasterElected = new AtomicReference<>(null);
    private final AtomicBoolean activated = new AtomicBoolean(false);
    protected final ActorSystem actorSystem;
    protected final ClusterService clusterService;
    protected final ActorRef self;
    private ActorRef masterRef;

    protected MasterService(ActorSystem actorSystem, ClusterService clusterService) {
        this.actorSystem = actorSystem;
        this.clusterService = clusterService;
        // get the name from the ServiceActor
        ServiceActor serviceActor = getClass().getAnnotation(ServiceActor.class);
        if(serviceActor != null) {
            self = actorSystem.serviceActorFor(serviceActor.value());
        } else {
            throw new IllegalArgumentException("This class needs to be inherited by a concrete class annotated with @ServiceActor");
        }
        this.clusterService.addEventListener(this);
    }

    private String getServiceActorName() {
        ServiceActor serviceActor = getClass().getAnnotation(ServiceActor.class);
        return serviceActor != null ? serviceActor.value() : null;
    }

    @Override
    public final void postActivate(String previousVersion) throws Exception {
        doActivate(previousVersion);
        // @todo: this is a race condition
        if(activated.compareAndSet(false,true)) {
            MasterElected masterElected = pendingMasterElected.getAndSet(null);
            if(masterElected != null) {
                getSelf().tell(masterElected,getSelf());
            }
        }
    }

    protected abstract void doActivate(String previousVersion) throws Exception;

    @Override
    public final void onTopologyChanged(List<PhysicalNode> topology) throws Exception {
        // ignore
    }

    @Override
    public final void onMasterElected(PhysicalNode masterNode) throws Exception {
        // @todo: see if we are master, otherwise relay all messages to trading service on master
        logger.info("New Master Elected: {}",masterNode);
        // @todo: probably send message to self in order to become the master
        // @todo: at startup this is too early
        // self.tell(new MasterElected(masterNode.getId(),masterNode.isLocal()),self);
        // handleMasterElected(new MasterElected(masterNode.getId(),masterNode.isLocal()));
        if(!activated.get()) {
            this.pendingMasterElected.set(new MasterElected(masterNode.getId(),masterNode.isLocal()));
        } else {
            // when we are activated the
            self.tell(new MasterElected(masterNode.getId(), masterNode.isLocal()), self);
        }
    }

    @Override
    public final void onReceive(ActorRef sender, Object message) throws Exception {
        if(message instanceof MasterElected) {
            handleMasterElected((MasterElected) message);
        } else if(masterRef != null && !masterRef.equals(self)) {
            // forward the message to the master service
            masterRef.tell(message,sender);
        } else {
            // handle it
            super.onReceive(sender, message);
        }
    }

    private final void handleMasterElected(MasterElected masterElected) throws Exception {
        this.masterRef = actorSystem.serviceActorFor(masterElected.getId(),getServiceActorName());
        onMasterElected(masterElected.isLocal());
    }

    protected abstract void onMasterElected(boolean local) throws Exception;

}
