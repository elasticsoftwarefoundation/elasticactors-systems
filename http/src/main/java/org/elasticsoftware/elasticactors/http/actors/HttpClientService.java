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

import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsoftware.elasticactors.ActorRef;
import org.elasticsoftware.elasticactors.ServiceActor;
import org.elasticsoftware.elasticactors.TypedActor;
import org.elasticsoftware.elasticactors.http.messages.HttpRequest;
import org.elasticsoftware.elasticactors.http.messages.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpMethod;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.List;
import java.util.Map;

/**
 * @author Joost van de Wijgerd
 */
@ServiceActor("httpClient")
public final class HttpClientService extends TypedActor<HttpRequest> {
    private static final Logger logger = LogManager.getLogger(HttpClientService.class);
    private AsyncHttpClient httpClient;

    public HttpClientService() {
    }

    @PostConstruct
    public void init() {
        AsyncHttpClientConfig config =
                new AsyncHttpClientConfig.Builder().setCompressionEnabled(true).setFollowRedirects(true).build();
        httpClient = new AsyncHttpClient(config);
    }

    @PreDestroy
    public void destroy() {
        httpClient.close();;
    }

    @Override
    public void onReceive(ActorRef sender, HttpRequest message) throws Exception {
        // run the request via ning and return the response async
        HttpMethod method = HttpMethod.valueOf(message.getMethod());
        if(HttpMethod.GET.equals(method)) {
            setHeaders(httpClient.prepareGet(message.getUrl()),message.getHeaders())
                .execute(new ResponseHandler(getSelf(), sender));
        } else if(HttpMethod.POST.equals(method)) {
            setHeaders(httpClient.preparePost(message.getUrl()),message.getHeaders())
                    .setBody(message.getContent())
                    .execute(new ResponseHandler(getSelf(), sender));
        } else {
            logger.error("Unhandled request method: "+method);
        }
    }

    private AsyncHttpClient.BoundRequestBuilder setHeaders(AsyncHttpClient.BoundRequestBuilder requestBuilder,Map<String,List<String>> headers) {
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            for (String value : entry.getValue()) {
                requestBuilder.setHeader(entry.getKey(),value);
            }
        }
        return requestBuilder;
    }

    private static final class ResponseHandler extends AsyncCompletionHandler<Integer> {
        private final ActorRef serviceAddress;
        private final ActorRef replyAddress;

        private ResponseHandler(ActorRef serviceAddress, ActorRef replyAddress) {
            this.serviceAddress = serviceAddress;
            this.replyAddress = replyAddress;
        }

        @Override
        public Integer onCompleted(Response response) throws Exception {
            // get the headers

            Map<String,List<String>> headers = response.getHeaders();
            replyAddress.tell(new HttpResponse(response.getStatusCode(),
                                               headers,
                                               response.getResponseBodyAsBytes()),serviceAddress);
            return response.getStatusCode();
        }

        @Override
        public void onThrowable(Throwable t) {
            // @todo: send message back to replyAddress
            logger.error("Exception getting response",t);
        }
    }
}
