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

package org.elasticsoftware.elasticactors.http;

import org.elasticsoftware.elasticactors.ActorRef;
import org.elasticsoftware.elasticactors.ActorSystem;
import org.elasticsoftware.elasticactors.http.actors.HttpService;
import org.elasticsoftware.elasticactors.http.actors.HttpServiceResponseHandler;
import org.elasticsoftware.elasticactors.http.codec.ServerSentEventEncoder;
import org.elasticsoftware.elasticactors.http.messages.HttpRequest;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.jboss.netty.channel.Channels.pipeline;

/**
 * @author Joost van de Wijgerd
 */
public final class HttpServer extends SimpleChannelUpstreamHandler implements ChannelPipelineFactory {
    private static final Logger logger = LoggerFactory.getLogger(HttpServer.class);
    private final ServerSentEventEncoder sseEventEncoder = new ServerSentEventEncoder();
    private final ServerSocketChannelFactory channelFactory;
    private final ActorSystem actorSystem;
    private final HttpService httpService;
    private volatile Channel serverChannel;
    private final int listenPort;

    public HttpServer(ServerSocketChannelFactory channelFactory,
                      HttpService httpService,
                      ActorSystem actorSystem,
                      int listenPort) {
        this.channelFactory = channelFactory;
        this.actorSystem = actorSystem;
        this.httpService = httpService;
        this.listenPort = listenPort;
    }


    @PostConstruct
    public void init() {
        ServerBootstrap bootstrap = new ServerBootstrap(channelFactory);
        bootstrap.setPipelineFactory(this);
        this.serverChannel = bootstrap.bind(new InetSocketAddress(listenPort));
    }

    @PreDestroy
    public void destroy() {
        serverChannel.close();
    }

    @Override
    public ChannelPipeline getPipeline() throws Exception {
        // Create a default pipeline implementation.
        ChannelPipeline pipeline = pipeline();

      // Uncomment the following line if you want HTTPS
      //SSLEngine engine = SecureChatSslContextFactory.getServerContext().createSSLEngine();
     //engine.setUseClientMode(false);
      //pipeline.addLast("ssl", new SslHandler(engine));

      pipeline.addLast("decoder", new HttpRequestDecoder());
      //pipeline.addLast("aggregator", new HttpChunkAggregator(65536));
      pipeline.addLast("encoder", new HttpResponseEncoder());
       pipeline.addLast("sseEventEncoder", sseEventEncoder);
      //pipeline.addLast("chunkedWriter", new ChunkedWriteHandler());

      pipeline.addLast("handler", this);
      return pipeline;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        org.jboss.netty.handler.codec.http.HttpRequest nettyRequest = (org.jboss.netty.handler.codec.http.HttpRequest) e.getMessage();
        // convert request to our internal request
        Map<String,List<String>> headers = new HashMap<>();
        for (String headerName : nettyRequest.headers().names()) {
            headers.put(headerName,nettyRequest.headers().getAll(headerName));
        }
        // see if we have a body
        byte[] content = null;
        if(nettyRequest.getContent().hasArray() && nettyRequest.getContent().array().length > 0) {
            content = nettyRequest.getContent().array();
        } else if(nettyRequest.getContent().readableBytes() > 0) {
            // netty content not backed by array, need to copy
            content = new byte[nettyRequest.getContent().readableBytes()];
            nettyRequest.getContent().readBytes(content);
        }

        HttpRequest request = new HttpRequest(nettyRequest.getMethod().getName(),new URI(nettyRequest.getUri()).getPath(),headers,content);
        // create a temp actor to handle the response
        ActorRef replyActor = actorSystem.tempActorOf(HttpServiceResponseHandler.class,
                                                      new HttpServiceResponseHandler.State(ctx.getChannel()));
        // put the actor in the attachment to propagate disconnects
        ctx.setAttachment(replyActor);
        // async handling
        if(!httpService.doDispatch(request,replyActor)) {
            // send 404
            ctx.getChannel().write(new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.NOT_FOUND)).addListener(ChannelFutureListener.CLOSE);
            actorSystem.stop(replyActor);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        logger.error("Exception caught",e.getCause());
        ctx.getChannel().write(new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.INTERNAL_SERVER_ERROR)).addListener(ChannelFutureListener.CLOSE);
    }

}
