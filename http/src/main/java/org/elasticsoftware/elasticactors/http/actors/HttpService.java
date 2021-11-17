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

package org.elasticsoftware.elasticactors.http.actors;

import org.elasticsoftware.elasticactors.ActorRef;
import org.elasticsoftware.elasticactors.ActorSystem;
import org.elasticsoftware.elasticactors.ActorSystemConfiguration;
import org.elasticsoftware.elasticactors.ServiceActor;
import org.elasticsoftware.elasticactors.UntypedActor;
import org.elasticsoftware.elasticactors.http.HttpServer;
import org.elasticsoftware.elasticactors.http.messages.HttpRequest;
import org.elasticsoftware.elasticactors.http.messages.RegisterRouteMessage;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author Joost van de Wijgerd
 */
// @todo: need to make sure spring can take the name from ServiceActor annotation
@ServiceActor("httpServer")
public final class HttpService extends UntypedActor {
    private static final Logger staticLogger = LoggerFactory.getLogger(HttpService.class);
    private final ConcurrentMap<String,ActorRef> routes = new ConcurrentHashMap<>();
    private final PathMatcher pathMatcher = new AntPathMatcher();
    private final ActorSystem actorSystem;
    private final ActorSystemConfiguration configuration;
    private HttpServer httpServer;

    @Inject
    public HttpService(ActorSystem actorSystem, ActorSystemConfiguration configuration) {
        this.actorSystem = actorSystem;
        this.configuration = configuration;
    }

    @PostConstruct
    public void init() {
        ExecutorService bossExecutor = Executors.newCachedThreadPool(Executors.defaultThreadFactory());
        ExecutorService workerExecutor = Executors.newCachedThreadPool(Executors.defaultThreadFactory());
        int workers = Runtime.getRuntime().availableProcessors();
        NioServerSocketChannelFactory channelFactory = new NioServerSocketChannelFactory(bossExecutor,workerExecutor,workers);
        httpServer = new HttpServer(channelFactory, this, actorSystem , configuration.getProperty(this.getClass(),"listenPort",Integer.class,8080));
        httpServer.init();
    }

    @PreDestroy
    public void destroy() {
        httpServer.destroy();
    }

    @Override
    public void onReceive(ActorRef sender, Object message) throws Exception {
        if(message instanceof RegisterRouteMessage) {
            RegisterRouteMessage registerRouteMessage = (RegisterRouteMessage) message;
            routes.putIfAbsent(registerRouteMessage.getPattern(),registerRouteMessage.getHandlerRef());
            logger.info("Adding Route with pattern [{}] for Actor [{}]",registerRouteMessage.getPattern(),registerRouteMessage.getHandlerRef());
        } else {
            logger.warn("Received a message that is not understood: {}", message.getClass().getSimpleName());
        }
    }

    public boolean doDispatch(HttpRequest request,ActorRef replyAddress) {
        logger.info("Dispatching Request [{}]",request.getUrl());
        // match for routes, for now no fancy matching
        ActorRef handlerRef = getHandler(request.getUrl());
        if(handlerRef != null) {
            logger.info("Found actor [{}]",handlerRef);
            handlerRef.tell(request,replyAddress);
            return true;
        } else {
            logger.info("No actor found to handle request");
            return false;
        }
    }

    private ActorRef getHandler(String urlPath) {
        // direct match?
        ActorRef handlerRef = routes.get(urlPath);
        if(handlerRef != null) {
            return handlerRef;
        }
        // Pattern match?
        List<String> matchingPatterns = new ArrayList<>();
        for (String registeredPattern : this.routes.keySet()) {
            if (pathMatcher.match(registeredPattern, urlPath)) {
                matchingPatterns.add(registeredPattern);
            }
        }
        String bestPatternMatch = null;
        Comparator<String> patternComparator = pathMatcher.getPatternComparator(urlPath);
        if (!matchingPatterns.isEmpty()) {
            matchingPatterns.sort(patternComparator);
            if (logger.isDebugEnabled()) {
                logger.debug("Matching patterns for request [{}] are {}", urlPath, matchingPatterns);
            }
            bestPatternMatch = matchingPatterns.get(0);
        }
        if (bestPatternMatch != null) {
            return routes.get(bestPatternMatch);
        }
        return null;
    }

    @Override
    protected Logger initLogger() {
        return staticLogger;
    }
}
