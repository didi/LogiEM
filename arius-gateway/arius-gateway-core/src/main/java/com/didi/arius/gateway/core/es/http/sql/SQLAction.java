package com.didi.arius.gateway.core.es.http.sql;

import com.didi.arius.gateway.common.consts.QueryConsts;
import com.didi.arius.gateway.common.consts.RestConsts;
import com.didi.arius.gateway.common.metadata.FetchFields;
import com.didi.arius.gateway.common.metadata.IndexTemplate;
import com.didi.arius.gateway.common.metadata.JoinLogContext;
import com.didi.arius.gateway.common.metadata.QueryContext;
import com.didi.arius.gateway.common.utils.Convert;
import com.didi.arius.gateway.core.es.http.HttpRestHandler;
import com.didi.arius.gateway.elasticsearch.client.ESClient;
import com.didi.arius.gateway.elasticsearch.client.gateway.search.ESSearchRequest;
import com.didi.arius.gateway.elasticsearch.client.gateway.search.ESSearchResponse;
import org.apache.commons.collections.CollectionUtils;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.common.collect.Tuple;
import org.nlpcn.es4sql.query.SqlElasticRequestBuilder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.didi.arius.gateway.common.utils.CommonUtil.isIndexType;

@Component("sqlAction")
public class SQLAction extends HttpRestHandler {

	@Override
	public String name() {
		return "sql";
	}

	@Override
	public void handleRequest(QueryContext queryContext) throws Exception {

		checkFlowLimit(queryContext);
		SqlElasticRequestBuilder requestBuilder = buildRequest(queryContext.getPostBody());
		SearchRequest searchRequest = (SearchRequest) requestBuilder.request();

		List<String> indices = Arrays.asList(searchRequest.indices());
		queryContext.setIndices(indices);
		queryContext.setTypeNames(searchRequest.types());
		checkIndicesAndTemplateBlockRead(queryContext);

        ESSearchRequest esSearchRequest = new ESSearchRequest();
        esSearchRequest.indices(searchRequest.indices());

		IndexTemplate indexTemplate = null;
		if (isIndexType(queryContext)) {
		    if (indices.size() == 1) {
                indexTemplate = getTemplateByIndex(indices, queryContext);
            }

		    if (indexTemplate != null) {
				if(!isAliasGet(indexTemplate, indices)){
					String dateFrom = queryContext.getRequest().param(RestConsts.SEARCH_DATE_FROM_PARAMS);
					String dateTo = queryContext.getRequest().param(RestConsts.SEARCH_DATE_TO_PARAMS);

					esSearchRequest.indices(getQueryIndices(indexTemplate, dateFrom, dateTo));
				}
            } else {
		        indexTemplate = getTemplateByIndexTire(indices, queryContext);
            }

		    // ???????????????????????????type??????????????????????????????????????????
		    if (isNeedChangeIndexName(queryContext, indexTemplate)) {
				Tuple<IndexTemplate/*dest template*/, String[]/*dest indexNames*/> changeResult =
						handleChangeIndexName(queryContext, indexTemplate, searchRequest.indices(), searchRequest.types());

				// ????????????????????????????????????
				esSearchRequest.indices(changeResult.v2());
				// ???????????????????????????
				indexTemplate = changeResult.v1();
			}

		}

        esSearchRequest.types(searchRequest.types());
        esSearchRequest.source(searchRequest.source());
		Map<String, String> params = queryContext.getRequest().params();
		params.remove("source");
		params.remove("index");
		params.remove("type");
		params.remove("filter_path");
		params.remove(RestConsts.SEARCH_DATE_FROM_PARAMS);
		params.remove(RestConsts.SEARCH_DATE_TO_PARAMS);
		params.put(QueryConsts.SEARCH_IGNORE_THROTTLED, "false");
		setRouteAndScroll(searchRequest, params);

		esSearchRequest.setParams(params);

        esSearchRequest.putHeader("requestId", queryContext.getRequestId());
        esSearchRequest.putHeader("Authorization", queryContext.getRequest().getHeader("Authorization"));

		// ??????????????????*??????????????????????????????????????????
		Convert.convertIndices(esSearchRequest);

		ESClient client = esClusterService.getClient(queryContext, indexTemplate, actionName);

		// pre process
		preSearchProcess(queryContext, client, esSearchRequest);

		FetchFields fetchFields = formFetchFields(esSearchRequest);
		queryContext.setFetchFields(fetchFields);

		if (queryContext.isDetailLog()) {
			JoinLogContext joinLogContext = queryContext.getJoinLogContext();
			joinLogContext.setBeforeCost(System.currentTimeMillis() - queryContext.getRequestTime());
		}

		ActionListener<ESSearchResponse> listener = newSearchListener(queryContext);
		client.search(esSearchRequest, listener);
	}

	private void setRouteAndScroll(SearchRequest searchRequest, Map<String, String> params) {
		if (searchRequest.routing() != null && !params.containsKey("routing")) {
			params.put("routing", searchRequest.routing());
		}

		if (searchRequest.scroll() != null && searchRequest.scroll().keepAlive() != null && !params.containsKey("scroll")) {
			params.put("scroll", searchRequest.scroll().keepAlive().toString());
		}
	}

	private boolean isAliasGet(IndexTemplate indexTemplate, List<String> indexs){
		if(CollectionUtils.isEmpty(indexTemplate.getAliases())){
			return false;
		}

		for(String index : indexs){
			if(indexTemplate.getAliases().contains( index )){
				return true;
			}
		}

		return false;
	}
}
