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

package org.elasticsoftware.elasticactors.http.codec;

import org.elasticsoftware.elasticactors.http.messages.ServerSentEvent;
import org.elasticsoftware.elasticactors.http.messages.SseResponse;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.codec.oneone.OneToOneEncoder;

import java.util.List;
import java.util.Map;

import static org.jboss.netty.buffer.ChannelBuffers.dynamicBuffer;
import static org.jboss.netty.handler.codec.http.HttpConstants.COLON;
import static org.jboss.netty.handler.codec.http.HttpConstants.CR;
import static org.jboss.netty.handler.codec.http.HttpConstants.LF;
import static org.jboss.netty.handler.codec.http.HttpConstants.SP;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author Joost van de Wijgerd
 */
public final class ServerSentEventEncoder extends OneToOneEncoder {

    private static final byte[] EVENT = "event".getBytes(UTF_8);
    private static final byte[] DATA = "data".getBytes(UTF_8);
    private static final byte[] ID = "id".getBytes(UTF_8);

    @Override
    protected Object encode(ChannelHandlerContext ctx, Channel channel, Object msg) throws Exception {
        if(msg instanceof SseResponse) {
            SseResponse m = (SseResponse) msg;
            ChannelBuffer header = dynamicBuffer(channel.getConfig().getBufferFactory());
            encodeInitialLine(header);
            encodeHeaders(header, m);
            header.writeByte(CR);
            header.writeByte(LF);
            return header;
        } else if(msg instanceof ServerSentEvent) {
            ServerSentEvent m = (ServerSentEvent) msg;
            ChannelBuffer event = dynamicBuffer(channel.getConfig().getBufferFactory());
            if(m.getEvent() != null) {
                event.writeBytes(EVENT);
                event.writeByte(COLON);
                event.writeByte(SP);
                event.writeBytes(m.getEvent().getBytes(UTF_8));
                event.writeByte(CR);
                event.writeByte(LF);
            }
            if(m.getData() != null && !m.getData().isEmpty()) {
                for (String data : m.getData()) {
                    event.writeBytes(DATA);
                    event.writeByte(COLON);
                    event.writeByte(SP);
                    event.writeBytes(data.getBytes(UTF_8));
                    event.writeByte(CR);
                    event.writeByte(LF);
                }
            }
            if(m.getId() != null) {
                event.writeBytes(ID);
                event.writeByte(COLON);
                event.writeByte(SP);
                event.writeBytes(m.getId().getBytes(UTF_8));
                event.writeByte(CR);
                event.writeByte(LF);
            }
            // end with empty line
            event.writeByte(CR);
            event.writeByte(LF);
            return event;
        } else {
            return msg;
        }
    }

    private void encodeInitialLine(ChannelBuffer buf) {
        buf.writeBytes(HttpVersion.HTTP_1_1.toString().getBytes(US_ASCII));
        buf.writeByte(SP);
        buf.writeBytes(String.valueOf(HttpResponseStatus.OK.getCode()).getBytes(US_ASCII));
        buf.writeByte(SP);
        buf.writeBytes(String.valueOf(HttpResponseStatus.OK.getReasonPhrase()).getBytes(US_ASCII));
        buf.writeByte(CR);
        buf.writeByte(LF);
    }

    private static void encodeHeaders(ChannelBuffer buf, SseResponse message) {
        for (Map.Entry<String, List<String>> entry : message.getHeaders().entrySet()) {
            for (String value : entry.getValue()) {
                encodeHeader(buf,entry.getKey(),value);
            }
        }
    }

    private static void encodeHeader(ChannelBuffer buf, String header, String value) {
        buf.writeBytes(header.getBytes(US_ASCII));
        buf.writeByte(COLON);
        buf.writeByte(SP);
        buf.writeBytes(value.getBytes(US_ASCII));
        buf.writeByte(CR);
        buf.writeByte(LF);
    }
}
