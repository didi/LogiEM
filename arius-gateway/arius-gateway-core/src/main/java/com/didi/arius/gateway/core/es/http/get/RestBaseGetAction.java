package com.didi.arius.gateway.core.es.http.get;

import java.util.List;
import java.util.Map;

import com.alibaba.fastjson.JSON;
import com.didi.arius.gateway.common.consts.QueryConsts;
import com.didi.arius.gateway.common.metadata.*;
import com.didi.arius.gateway.core.es.http.ESAction;
import com.didi.arius.gateway.core.es.http.RestActionListenerImpl;
import com.didi.arius.gateway.elasticsearch.client.ESClient;
import com.didi.arius.gateway.elasticsearch.client.gateway.document.ESGetRequest;
import com.didi.arius.gateway.elasticsearch.client.gateway.document.ESGetResponse;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.VersionType;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.rest.action.support.RestActions;
import org.elasticsearch.search.fetch.source.FetchSourceContext;

import static com.didi.arius.gateway.common.utils.CommonUtil.isIndexType;

public abstract class RestBaseGetAction extends ESAction {
    protected void handleInterGetRequest(QueryContext queryContext, RestRequest request, final WrapESGetResponse.ResultType resultType) {
        IndexTemplate template = null;

        String index = request.param("index");

        if (isIndexType(queryContext)) {
            List<String> indices = queryContext.getIndices();
            template = getTemplateByIndexTire(indices, queryContext);

            // 该索引模板需要根据type名称进行映射到对应的索引模板
            if (isNeedChangeIndexName(queryContext, template)) {
                String sourceIndexName = index;
                String sourceTemplateName = template.getName();
                Map<String/*typeName*/,String/*templateName*/> typeIndexMapping = template.getMasterInfo().getTypeIndexMapping();

                // 用户指定type方式查询时，gateway需要将该多type索引和指定的type名称映射为对应的单type索引，然后转发到es。例如GET indexName/type1/_search   改写为 GET type1@indexName/type1/_search。
                String typeName = request.param("type");
                String destTemplateName = typeIndexMapping.get(typeName);
                if (StringUtils.isNoneBlank(destTemplateName)) {
                    // 替换索引名称
                    index = index.replace(sourceTemplateName, destTemplateName);
                    // 再替换索引模板对象
                    template = indexTemplateService.getIndexTemplate(destTemplateName);
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

        ESClient readClient = esClusterService.getClient(queryContext, template, actionName);

        int indexVersion = indexTemplateService.getIndexVersion(index, queryContext.getCluster());

        final FetchFields fetchFields = new FetchFields();
        fetchFields.setFetchSourceContext(FetchSourceContext.parseFromRestRequest(request));
        String sField = request.param("fields");
        if (sField != null) {
            dealField(fetchFields, sField);
        }

        if (indexVersion > 0) {
            getVersionResponse(queryContext, indexVersion, index, request, readClient, resultType);
        } else {
            final ESGetRequest getRequest = new ESGetRequest(index, request.param("type") == null ? "_doc" : request.param("type"), request.param("id"));
            getRequest.refresh(request.param("refresh"));
            getRequest.routing(request.param("routing"));  // order is important, set it after routing, so it will set the routing
            getRequest.parent(request.param("parent"));
            getRequest.preference(request.param("preference"));
            getRequest.realtime(request.paramAsBoolean("realtime", null));
            getRequest.ignoreErrorsOnGeneratedFields(request.paramAsBoolean("ignore_errors_on_generated_fields", false));

            setFieldValue(request, getRequest);

            getRequest.putHeader("requestId", queryContext.getRequestId());
            getRequest.putHeader("Authorization", queryContext.getRequest().getHeader("Authorization"));

            getRequest.version(RestActions.parseVersion(request));
            getRequest.versionType(VersionType.fromString(request.param("version_type"), getRequest.versionType()));

            getRequest.fetchSourceContext(FetchSourceContext.parseFromRestRequest(request));

            ActionListener<ESGetResponse> listener = getEsGetResponseActionListener(queryContext, resultType);
            readClient.get(getRequest, listener);
        }
    }

    private ActionListener<ESGetResponse> getEsGetResponseActionListener(QueryContext queryContext, WrapESGetResponse.ResultType resultType) {
        return new RestActionListenerImpl<ESGetResponse>(queryContext) {
            @Override
            public void onResponse(ESGetResponse response) {
                WrapESGetResponse wrapESGetResponse = new WrapESGetResponse();
                wrapESGetResponse.setEsGetResponse(response);
                wrapESGetResponse.setResultType(resultType);
                switch (wrapESGetResponse.getResultType()) {
                    case ALL:
                        super.onResponse(response);
                        break;
                    case HEAD:
                        if (response.isExists()) {
                            super.onResponse(new BytesRestResponse(RestStatus.OK));
                        } else {
                            super.onResponse(new BytesRestResponse(RestStatus.NOT_FOUND));
                        }
                        break;
                    case SOURCE:
                        if (response.isExists()) {
                            super.onResponse(new BytesRestResponse(RestStatus.OK, XContentType.JSON.restContentType(), JSON.toJSONString(response.getSource())));
                        } else {
                            super.onResponse(new BytesRestResponse(RestStatus.NOT_FOUND));
                        }

                        break;
                    default:
                }
            }
        };
    }

    private void setFieldValue(RestRequest request, ESGetRequest getRequest) {
        String fieldStr = request.param("fields");
        if (fieldStr != null) {
            String[] sFields = Strings.splitStringByCommaToArray(fieldStr);
            if (sFields != null) {
                getRequest.fields(sFields);
            }
        }

        final String fieldsParam = request.param("stored_fields");
        if (fieldsParam != null) {
            final String[] fields = Strings.splitStringByCommaToArray(fieldsParam);
            if (fields != null) {
                getRequest.storedFields(fields);
            }
        }
    }

    private void dealField(FetchFields fetchFields, String sField) {
        String[] sFields = Strings.splitStringByCommaToArray(sField);
        if (sFields != null) {
            fetchFields.setFields(sFields);
            for (String field : sFields) {
                if (field.equals(QueryConsts.MESSAGE_FIELD)) {
                    fetchFields.setHasMessageField(true);
                    break;
                }
            }
        }
    }
}
