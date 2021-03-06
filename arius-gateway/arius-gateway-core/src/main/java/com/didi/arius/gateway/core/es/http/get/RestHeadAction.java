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

package com.didi.arius.gateway.core.es.http.get;

import java.util.List;
import java.util.Map;

import com.didi.arius.gateway.common.metadata.IndexTemplate;
import com.didi.arius.gateway.common.metadata.JoinLogContext;
import com.didi.arius.gateway.common.metadata.QueryContext;
import com.didi.arius.gateway.common.metadata.WrapESGetResponse;
import com.didi.arius.gateway.core.es.http.ESAction;
import com.didi.arius.gateway.core.es.http.RestActionListenerImpl;
import com.didi.arius.gateway.elasticsearch.client.ESClient;
import com.didi.arius.gateway.elasticsearch.client.gateway.document.ESGetRequest;
import com.didi.arius.gateway.elasticsearch.client.gateway.document.ESGetResponse;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.common.Strings;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestStatus;
import org.springframework.stereotype.Component;

import static com.didi.arius.gateway.common.utils.CommonUtil.isIndexType;

/**
 *
 */
@Component("restHeadAction")
public class RestHeadAction extends ESAction {

	@Override
	public String name() {
		return "head";
	}

    @Override
	public void handleInterRequest(QueryContext queryContext, RestRequest request, RestChannel channel)
			throws Exception {
        String index = request.param("index");

        IndexTemplate indexTemplate = null;
        if (isIndexType(queryContext)) {
            List<String> indices = queryContext.getIndices();
            indexTemplate = getTemplateByIndexTire(indices, queryContext);

            // ???????????????????????????type??????????????????????????????????????????
            if (isNeedChangeIndexName(queryContext, indexTemplate)) {
                String sourceIndexName = index;
                String sourceTemplateName = indexTemplate.getName();
                Map<String/*typeName*/,String/*templateName*/> typeIndexMapping = indexTemplate.getMasterInfo().getTypeIndexMapping();

                // ????????????type??????????????????gateway???????????????type??????????????????type???????????????????????????type????????????????????????es?????????GET indexName/type1/_search   ????????? GET type1@indexName/type1/_search???
                String typeName = request.param("type");
                String destTemplateName = typeIndexMapping.get(typeName);
                if (StringUtils.isNoneBlank(destTemplateName)) {
                    // ??????????????????
                    index = index.replace(sourceTemplateName, destTemplateName);
                    // ???????????????????????????
                    indexTemplate = indexTemplateService.getIndexTemplate(destTemplateName);
                }

                if (queryContext.isDetailLog()) {
                    JoinLogContext joinLogContext = queryContext.getJoinLogContext();
                    joinLogContext.setSourceIndexNames(sourceIndexName);
                    joinLogContext.setTypeName(typeName);
                    joinLogContext.setDestIndexName(index);
                    joinLogContext.setSourceTemplateName(sourceTemplateName);
                    joinLogContext.setDestTemplateName(destTemplateName);
                }
            }
        }

        ESClient readClient = esClusterService.getClient(queryContext, indexTemplate, actionName);

    	int indexVersion = indexTemplateService.getIndexVersion(index, queryContext.getCluster());

    	if (indexVersion > 0) {
    		getVersionResponse(queryContext, indexVersion, index, request, readClient, WrapESGetResponse.ResultType.HEAD);
    	} else {
            final ESGetRequest getRequest = new ESGetRequest(index, request.param("type") == null ? "_doc" : request.param("type"), request.param("id"));
            getRequest.refresh(request.param("refresh"));
            getRequest.routing(request.param("routing"));  // order is important, set it after routing, so it will set the routing
            getRequest.parent(request.param("parent"));
            getRequest.preference(request.param("preference"));
            getRequest.realtime(request.paramAsBoolean("realtime", null));
            // don't get any fields back...
            getRequest.fields(Strings.EMPTY_ARRAY);

            getRequest.putHeader("requestId", queryContext.getRequestId());
            getRequest.putHeader("Authorization", request.getHeader("Authorization"));


            readClient.get(getRequest, new RestActionListenerImpl<ESGetResponse>(queryContext) {
                @Override
                public void onResponse(ESGetResponse response) {
                    if (response.isExists()) {
                        super.onResponse(new BytesRestResponse(RestStatus.OK));
                    } else {
                        super.onResponse(new BytesRestResponse(RestStatus.NOT_FOUND));
                    }
                }
            });
    	}
    }
}
