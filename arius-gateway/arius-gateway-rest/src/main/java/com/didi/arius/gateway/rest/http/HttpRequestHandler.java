/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.didi.arius.gateway.rest.http;

import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.http.netty.NettyHttpRequest;
import org.elasticsearch.http.netty.pipelining.OrderedUpstreamMessageEvent;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.HttpRequest;

/**
 *
 */
@ChannelHandler.Sharable
public class HttpRequestHandler extends SimpleChannelUpstreamHandler {

    private final NettyHttpServerTransport serverTransport;
    private final boolean httpPipeliningEnabled;
    private final boolean detailedErrorsEnabled;

    protected final ESLogger logger = Loggers.getLogger(HttpRequestHandler.class);

    public HttpRequestHandler(NettyHttpServerTransport serverTransport, boolean detailedErrorsEnabled) {
        this.serverTransport = serverTransport;
        this.httpPipeliningEnabled = serverTransport.pipelining;
        this.detailedErrorsEnabled = detailedErrorsEnabled;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
    	long start = System.currentTimeMillis();

        HttpRequest request;
        OrderedUpstreamMessageEvent oue = null;
        if (this.httpPipeliningEnabled && e instanceof OrderedUpstreamMessageEvent) {
            oue = (OrderedUpstreamMessageEvent) e;
            request = (HttpRequest) oue.getMessage();
        } else {
            request = (HttpRequest) e.getMessage();
        }

        // the netty HTTP handling always copy over the buffer to its own buffer, either in NioWorker internally
        // when reading, or using a cumalation buffer
        NettyHttpRequest httpRequest = new NettyHttpRequest(request, e.getChannel());
        if (oue != null) {
            serverTransport.dispatchRequest(httpRequest, new NettyHttpChannel(serverTransport, httpRequest, oue, detailedErrorsEnabled));
        } else {
            serverTransport.dispatchRequest(httpRequest, new NettyHttpChannel(serverTransport, httpRequest, detailedErrorsEnabled));
        }
        super.messageReceived(ctx, e);
        
        long cost = System.currentTimeMillis() - start;
        if (cost > 1000) {
        	logger.warn("http messageReceived cost too long, cost={}, uri={}", cost, httpRequest.uri());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        serverTransport.exceptionCaught(ctx, e);
    }
}
