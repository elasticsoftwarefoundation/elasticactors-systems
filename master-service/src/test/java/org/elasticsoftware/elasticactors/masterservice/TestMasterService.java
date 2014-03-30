package org.elasticsoftware.elasticactors.masterservice;

import org.elasticsoftware.elasticactors.ActorSystem;
import org.elasticsoftware.elasticactors.ServiceActor;
import org.elasticsoftware.elasticactors.cluster.ClusterService;

import javax.inject.Inject;

import static java.lang.String.format;

/**
 * @author Joost van de Wijgerd
 */
@ServiceActor("testMasterService")
public final class TestMasterService extends MasterService {
    @Inject
    public TestMasterService(ActorSystem actorSystem, ClusterService clusterService) {
        super(actorSystem, clusterService);
    }

    @Override
    protected void doActivate(String previousVersion) throws Exception {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    protected void onMasterElected(boolean local) throws Exception {
        System.out.println(format("GOT MASTER ELECTED: LOCAL = %s",String.valueOf(local)));
    }
}
