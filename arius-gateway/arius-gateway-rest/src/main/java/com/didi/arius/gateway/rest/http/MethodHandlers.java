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

import org.elasticsearch.common.Nullable;
import org.elasticsearch.rest.RestRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Encapsulate multiple handlers for the same path, allowing different handlers for different HTTP verbs.
 */
final class MethodHandlers {

    private final String path;
    private final Map<RestRequest.Method, IRestHandler> restHandlerMap;

    MethodHandlers(String path, IRestHandler handler, RestRequest.Method... methods) {
        this.path = path;
        this.restHandlerMap = new HashMap<>(methods.length);
        for (RestRequest.Method method : methods) {
            restHandlerMap.put(method, handler);
        }
    }

    /**
     * Add a handler for an additional array of methods. Note that {@code MethodHandlers}
     * does not allow replacing the handler for an already existing method.
     */
    MethodHandlers addMethods(IRestHandler handler, RestRequest.Method... methods) {
        for (RestRequest.Method method : methods) {
            IRestHandler existing = restHandlerMap.putIfAbsent(method, handler);
            if (existing != null) {
                throw new IllegalArgumentException("Cannot replace existing handler for [" + path + "] for method: " + method);
            }
        }
        return this;
    }

    /**
     * Returns the handler for the given method or {@code null} if none exists.
     */
    @Nullable
    IRestHandler getHandler(RestRequest.Method method) {
        return restHandlerMap.get(method);
    }

    /**
     * Return a set of all valid HTTP methods for the particular path
     */
    Set<RestRequest.Method> getValidMethods() {
        return restHandlerMap.keySet();
    }
}
