package com.didichuxing.datachannel.arius.admin.persistence.component;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.didichuxing.datachannel.arius.admin.common.Tuple;
import com.didichuxing.datachannel.arius.admin.common.constant.ESConstant;
import com.didichuxing.datachannel.arius.admin.common.exception.AriusGatewayException;
import com.didichuxing.datachannel.arius.admin.common.util.BaseHttpUtil;
import com.didichuxing.datachannel.arius.admin.common.util.CommonUtils;
import com.didichuxing.datachannel.arius.admin.common.util.EnvUtil;
import com.didichuxing.datachannel.arius.admin.common.util.ListUtils;
import com.didiglobal.logi.elasticsearch.client.ESClient;
import com.didiglobal.logi.elasticsearch.client.gateway.document.ESGetRequest;
import com.didiglobal.logi.elasticsearch.client.gateway.document.ESGetResponse;
import com.didiglobal.logi.elasticsearch.client.gateway.document.ESIndexRequest;
import com.didiglobal.logi.elasticsearch.client.request.query.query.ESQueryRequest;
import com.didiglobal.logi.elasticsearch.client.request.query.query.ESQueryRequestBuilder;
import com.didiglobal.logi.elasticsearch.client.request.query.scroll.ESQueryScrollRequest;
import com.didiglobal.logi.elasticsearch.client.response.query.query.ESQueryResponse;
import com.didiglobal.logi.elasticsearch.client.response.query.query.aggs.ESAggrMap;
import com.didiglobal.logi.log.ILog;
import com.didiglobal.logi.log.LogFactory;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * @Author: zqr
 * es??????????????????????????????ESGatewayClient, ??????gateway???????????????
 */
@Component
@NoArgsConstructor
@Data
public class ESGatewayClient {

    private static final ILog                                   LOGGER                = LogFactory
        .getLog(ESGatewayClient.class);

    /**
     * ??????gateway??????
     */
    @Value("${es.gateway.url}")
    private String                                               gatewayUrl;
    /**
     * ??????gateway ?????????
     */
    @Value("${es.gateway.port}")
    private Integer                                              gatewayPort;

    @Value("${es.client.io.thread.count:0}")
    private Integer                                              ioThreadCount;

    /**
     * ???????????????appid
     */
    @Value("${es.appid}")
    private String                                               appid;
    /**
     * ?????????????????????
     */
    @Value("${es.password}")
    private String                                               password;

    @Value("${scroll.timeout}")
    private String                                               scrollTimeOut;

    private static final String                                  COMMA                 = ",";

    /**
     * ??????es????????????
     */
    private Map<String/*appId*/, ESClient>                       queryClientMap        = Maps.newLinkedHashMap();
    /**
     * ???????????????????????????appid?????????
     */
    private Map<String/*access template name*/, String/*appId*/> accessTemplateNameMap = Maps.newHashMap();
    /**
     * ??????header
     */
    private Map<String/*appId*/, Header>                         appidHeaderMap        = Maps.newTreeMap();

    /**
     * ???????????????es?????????
     *
     */
    @PostConstruct
    public void init() {
        LOGGER.info("class=ESGatewayClient||method=init||ESGatewayClient init start.");
        // ??????appid
        String[] appids = StringUtils.splitByWholeSeparatorPreserveAllTokens(appid, COMMA);
        String[] passwords = StringUtils.splitByWholeSeparatorPreserveAllTokens(password, COMMA);

        if (appids == null || passwords == null || appids.length != passwords.length) {
            throw new AriusGatewayException("please check cn gateway appid,password");
        }

        // ?????????????????????header
        Header accessHeader = null;
        ESClient esClient   = null;

        for (int i = 0; i < appids.length; ++i) {
            accessHeader = BaseHttpUtil.buildHttpHeader(appids[i], passwords[i]);
            esClient = buildGateWayClient(this.gatewayUrl, this.gatewayPort, accessHeader, "gateway");

            queryClientMap.put(appids[i], esClient);
            appidHeaderMap.put(appids[i], accessHeader);
        }
        LOGGER.info("class=ESGatewayClient||method=init||ESGatewayClient init finished.");
    }

    /**
     * ??????gateway????????????
     */
    public String getGatewayAddress() {
        List<String> urls = ListUtils.string2StrList(gatewayUrl);
        List<String> gateWayList = Lists.newArrayList();
        urls.forEach(url -> gateWayList.add(url + ":" + gatewayPort));
        return ListUtils.strList2String(gateWayList);
    }

    /**
     * ????????????gateway????????????
     */
    public String getSingleGatewayAddress() {
        List<String> urls = ListUtils.string2StrList(gatewayUrl);
        Random random = new Random();
        int n = random.nextInt(urls.size());
        return urls.get(n) +":" + gatewayPort;
    }

    /**
     * ??????sql??????
     * @param sql
     * @return
     */
    @Nullable
    public ESQueryResponse performSQLRequest(String indexName, String sql,
                                             String orginalQuery) {
        return performSQLRequest(null, indexName, sql, orginalQuery);
    }

    /**
     * ??????sql??????
     * @param sql
     * @return
     */
    @Nullable
    public ESQueryResponse performSQLRequest(String clusterName, String indexName,
                                             String sql, String orginalQuery) {
        Tuple<String, ESClient> gatewayClientTuple = null;
        try {
            gatewayClientTuple = getGatewayClientByDataCenterAndIndexName(clusterName, indexName);

            return gatewayClientTuple.v2().prepareSQL(sql).get(new TimeValue(120, TimeUnit.SECONDS));
        } catch (Exception e) {
            LOGGER.warn(
                "class=GatewayClient||method=performSQLRequest||dataCenter={}||gatewayClientTuple={}||clusterName={}||sql={}||md5={}||errMsg=query error. ",
                EnvUtil.getDC(), JSON.toJSONString(gatewayClientTuple), clusterName, sql, CommonUtils.getMD5(orginalQuery), e);
            return null;
        }
    }

    /**
     * ??????????????????????????????
     *
     * @param indexName
     * @param typeName
     * @param queryDsl
     * @return
     */
    public String performRequestAndGetResponse(String indexName, String typeName,
                                               String queryDsl) {

        return performRequestAndGetResponse(null, indexName, typeName, queryDsl);
    }

    /**
     * ??????????????????????????????
     *
     * @param indexName
     * @param typeName
     * @param queryDsl
     * @return
     */
    public String performRequestAndGetResponse(String clusterName, String indexName,
                                               String typeName, String queryDsl) {

        ESQueryResponse esQueryResponse = doQuery(clusterName, indexName,
            new ESQueryRequest().indices(indexName).types(typeName).source(queryDsl));
        if (esQueryResponse == null) {
            return null;
        }

        return esQueryResponse.toJson().toJSONString();
    }

    /**
     * ????????????
     *
     * @param indexName
     * @param typeName
     * @param queryDsl
     * @return
     * @throws IOException
     */
    public ESQueryResponse performRequest(String indexName, String typeName,
                                          String queryDsl) {

        return performRequest(null, indexName, typeName, queryDsl);
    }

    /**
     * ????????????
     *
     * @param indexName
     * @param typeName
     * @param queryDsl
     * @return
     * @throws IOException
     */
    public ESQueryResponse performRequest(String clusterName, String indexName,
                                          String typeName, String queryDsl) {
        return doQuery(clusterName, indexName,
            new ESQueryRequest().indices(indexName).types(typeName).source(queryDsl));
    }

    public ESQueryResponse performRequestWithRouting(String clusterName, String routing, String indexName,
                                                     String typeName, String queryDsl) {
        return doQuery(clusterName, indexName,
                new ESQueryRequest().indices(indexName).routing(routing).types(typeName).source(queryDsl));
    }

    /**
     * ??????????????????
     *
     * @param indexName
     * @param typeName
     * @param queryDsl
     * @return
     */
    public Long performRequestAndGetTotalCount(String indexName, String typeName, String queryDsl) {
        return performRequestAndGetTotalCount(null, indexName, typeName, queryDsl);
    }
    /**
     * ??????????????????
     *
     * @param indexName
     * @param typeName
     * @param queryDsl
     * @return
     */
    public Long performRequestAndGetTotalCount(String clusterName, String indexName,
        String typeName, String queryDsl, int tryTimes) {
        return performRequest(clusterName, indexName, typeName, queryDsl,
            esQueryResponse -> {
                if (null == esQueryResponse || esQueryResponse.getHits() == null) {
                    return 0L;
                }
                return Long
                    .valueOf(esQueryResponse.getHits().getUnusedMap()
                        .getOrDefault(ESConstant.HITS_TOTAL, "0").toString());
            }
            , tryTimes);
    }
    

    /**
     * ??????????????????
     *
     * @param indexName
     * @param typeName
     * @param queryDsl
     * @return
     */
    public Long performRequestAndGetTotalCount(String clusterName, String indexName,
                                               String typeName, String queryDsl) {
        ESQueryResponse esQueryResponse = performRequest(clusterName, indexName, typeName, queryDsl);
        if (null == esQueryResponse || esQueryResponse.getHits() == null) {
            return 0L;
        }

        return Long
            .valueOf(esQueryResponse.getHits().getUnusedMap().getOrDefault(ESConstant.HITS_TOTAL, "0").toString());
    }

    /**
     * ??????????????????????????????
     *
     * @param indexName
     * @param typeName
     * @param queryDsl
     * @param clzz
     * @param <T>
     * @return
     */
    public <T> List<T> performRequest(String indexName, String typeName, String queryDsl,
                                      Class<T> clzz) {
        return performRequest(null, indexName, typeName, queryDsl, clzz);
    }

    /**
     * ??????dsl???????????????
     *
     * @param templateName ????????????
     * @param typeName ??????
     * @param dsl ??????
     */
    public void performWriteRequest(String templateName, String typeName, String dsl) {
        performWriteRequest(null, templateName, typeName, dsl);
    }

    /**
     * ??????dsl???????????????
     *
     * @param clusterName ????????????
     * @param templateName ???????????????????????????gateway??????????????????????????????????????????????????????????????????
     * @param typeName ??????
     * @param dsl ??????
     */
    public void performWriteRequest(String clusterName, String templateName, String typeName, String dsl) {
        doWrite(clusterName, templateName, new ESIndexRequest().index(templateName).type(typeName).source(dsl));
    }

    /**
     * ??????????????????????????????
     *
     * @param indexName
     * @param typeName
     * @param queryDsl
     * @param clzz
     * @param <T>
     * @return
     */
    public <T> List<T> performRequest(String clusterName, String indexName, String typeName, String queryDsl, Class<T> clzz) {
        ESQueryResponse esQueryResponse = doQuery(clusterName, indexName,
            new ESQueryRequest().indices(indexName).types(typeName).source(queryDsl).clazz(clzz));
        if (esQueryResponse == null) {
            return new ArrayList<>();
        }

        List<Object> objectList = esQueryResponse.getSourceList();
        if (CollectionUtils.isEmpty(objectList)) {
            return new ArrayList<>();
        }

        List<T> hits = Lists.newLinkedList();
        for (Object obj : objectList) {
            hits.add((T) obj);
        }

        return hits;
    }

    public <R> R performRequest(String indexName, String typeName, String queryDsl,
                                Function<ESQueryResponse, R> func, int tryTimes) {
        return performRequest(null, indexName, typeName, queryDsl, func, tryTimes);
    }

    public <R> R performRequest(String clusterName, String indexName, String typeName,
                                String queryDsl, Function<ESQueryResponse, R> func, int tryTimes) {
        ESQueryResponse esQueryResponse;
        do {
            esQueryResponse = doQuery(clusterName, indexName,
                new ESQueryRequest().indices(indexName).types(typeName).source(queryDsl));
        } while (tryTimes-- > 0 && null == esQueryResponse);

        if(!EnvUtil.isOnline()){
        LOGGER.warn("class=GatewayClient||method=performRequest||dataCenter={}||indexName={}||queryDsl={}||ret={}",
            EnvUtil.getDC(), indexName, queryDsl, JSON.toJSONString(esQueryResponse));
                }

        return func.apply(esQueryResponse);
    }

    public <R> R performRequestWithRouting(String clusterName, String routingValue, String indexName, String typeName,
                                String queryDsl, Function<ESQueryResponse, R> func, int tryTimes) {
        ESQueryResponse esQueryResponse;
        do {
            esQueryResponse = doQuery(clusterName, indexName,
                    new ESQueryRequest().routing(routingValue).indices(indexName).types(typeName).source(queryDsl));
        } while (tryTimes-- > 0 && null == esQueryResponse);

        if(!EnvUtil.isOnline()){
            LOGGER.warn("class=GatewayClient||method=performRequestWithRouting||dataCenter={}||indexName={}||queryDsl={}||ret={}",
                    EnvUtil.getDC(), indexName, queryDsl, JSON.toJSONString(esQueryResponse));
        }

        return func.apply(esQueryResponse);
    }

    /**
     * ??????????????????????????????
     *
     * @param indexName
     * @param typeName
     * @param queryDsl
     * @param clzz
     * @param <T>
     * @return
     */
    public <T> T performRequestAndTakeFirst(String indexName, String typeName, String queryDsl, Class<T> clzz) {
        return performRequestAndTakeFirst(null, indexName, typeName, queryDsl, clzz);
    }

    /**
     * ??????????????????????????????
     *
     * @param indexName
     * @param typeName
     * @param queryDsl
     * @param clzz
     * @param <T>
     * @return
     */
    public <T> T performRequestAndTakeFirst(String clusterName, String indexName,
                                            String typeName, String queryDsl, Class<T> clzz) {
        List<T> hits = performRequest(clusterName, indexName, typeName, queryDsl, clzz);

        if (CollectionUtils.isEmpty(hits)) {
            return null;
        }

        return hits.get(0);
    }

    /**
     * ????????????????????????????????????
     *
     * @param indexName
     * @param typeName
     * @param queryDsl
     * @param clzz
     * @param <T>
     * @return
     */
    public <T> Tuple<Long, T> performRequestAndGetTotalCount(String indexName, String typeName, String queryDsl, Class<T> clzz) {
        return performRequestAndGetTotalCount(null, indexName, typeName, queryDsl, clzz);
    }

    /**
     * ????????????????????????????????????
     *
     * @param indexName
     * @param typeName
     * @param queryDsl
     * @param clzz
     * @param <T>
     * @return
     */
    public <T> Tuple<Long, T> performRequestAndGetTotalCount(String clusterName, String indexName, String typeName, String queryDsl,
                                                             Class<T> clzz) {
        ESQueryResponse esQueryResponse = doQuery(clusterName, indexName,
            new ESQueryRequest().indices(indexName).types(typeName).source(queryDsl).clazz(clzz));
        if (esQueryResponse == null) {
            return null;
        }

        List<Object> objectList = esQueryResponse.getSourceList();
        if (CollectionUtils.isEmpty(objectList)) {
            return null;
        }

        return new Tuple<>(
            Long.valueOf(esQueryResponse.getHits().getUnusedMap().getOrDefault(ESConstant.HITS_TOTAL, "0").toString()),
            (T) objectList.get(0));
    }

    /**
     * ????????????
     *
     * @param indexName
     * @param typeName
     * @param queryDsl
     * @return
     * @throws IOException
     */
    @Nullable
    public ESAggrMap performAggRequest(String indexName, String typeName, String queryDsl) {
        return performAggRequest(null, indexName, typeName, queryDsl);
    }

    /**
     * ????????????
     *
     * @param indexName
     * @param typeName
     * @param queryDsl
     * @return
     * @throws IOException
     */
    @Nullable
    public ESAggrMap performAggRequest(String clusterName, String indexName, String typeName, String queryDsl) {
        ESQueryResponse esQueryResponse = doQuery(clusterName, indexName,
            new ESQueryRequest().indices(indexName).types(typeName).source(queryDsl));
        if (esQueryResponse == null || esQueryResponse.getAggs() == null) {
            return null;
        }

        return esQueryResponse.getAggs();
    }

    /**
     * ????????????
     *
     * @param indexName
     * @param typeName
     * @param queryDsl
     * @return
     * @throws IOException
     */
    @Nullable
    public ESAggrMap performAggRequestWithPreference(String indexName, String typeName, String queryDsl, String preference) {
        return performAggRequest(null, indexName, typeName, queryDsl, preference);
    }

    /**
     * ???????????????????????????????????????
     *
     * @param indexName
     * @param typeName
     * @param queryDsl
     * @param clzz
     * @param <T>
     * @return
     */
    public <T> Tuple<Long, List<T>> performRequestListAndGetTotalCount(String clusterName,String indexName, String typeName, String queryDsl, Class<T> clzz) {
        ESQueryResponse esQueryResponse = doQuery(clusterName, indexName,
                new ESQueryRequest().indices(indexName).types(typeName).source(queryDsl).clazz(clzz));
        if (esQueryResponse == null) {
            return null;
        }

        List<Object> objectList = esQueryResponse.getSourceList();
        if (CollectionUtils.isEmpty(objectList)) {
            return null;
        }

        List<T> hits = Lists.newLinkedList();
        for (Object obj : objectList) {
            hits.add((T)obj);
        }

        return new Tuple<>(Long.valueOf(esQueryResponse.getHits().getUnusedMap().getOrDefault(ESConstant.HITS_TOTAL, "0").toString()),
                hits);
    }

    /**
     * ????????????
     *
     * @param indexName
     * @param typeName
     * @param queryDsl
     * @return
     * @throws IOException
     */
    @Nullable
    public ESAggrMap performAggRequest(String clusterName, String indexName, String typeName, String queryDsl, String preference) {
        ESQueryResponse esQueryResponse = doQuery(clusterName, indexName,
            new ESQueryRequest().indices(indexName).types(typeName).source(queryDsl).preference(preference));
        if (esQueryResponse == null || esQueryResponse.getAggs() == null) {
            return null;
        }

        return esQueryResponse.getAggs();
    }

    /**
     * ??????????????????
     *
     * @param indexName
     * @param typeName
     * @param queryDsl
     * @param clzz
     * @param scrollResultVisitor
     * @param <T>
     * @return
     */
    public <T> ESQueryResponse prepareScrollQuery(String indexName, String typeName,
                                                  String queryDsl, String preference, Class<T> clzz,
                                                  ScrollResultVisitor<T> scrollResultVisitor) {

        return prepareScrollQuery(null, indexName, typeName, queryDsl, preference, clzz,
            scrollResultVisitor);
    }

    /**
     * ??????????????????
     *
     * @param indexName
     * @param typeName
     * @param queryDsl
     * @param clzz
     * @param scrollResultVisitor
     * @param <T>
     * @return
     */
    public <T> ESQueryResponse prepareScrollQuery(String clusterName, String indexName,
                                                  String typeName, String queryDsl, String preference, Class<T> clzz,
                                                  ScrollResultVisitor<T> scrollResultVisitor) {
        ESQueryResponse esQueryResponse = null;
        ESQueryRequestBuilder builder = null;

        Tuple<String, ESClient> gatewayClientTuple = null;
        gatewayClientTuple = getGatewayClientByDataCenterAndIndexName(clusterName, indexName);
        builder = gatewayClientTuple.v2().prepareQuery(indexName).setTypes(typeName).setClazz(clzz).setSource(queryDsl)
            .setScroll(new TimeValue(60000));

        // ???????????????preference
        if (StringUtils.isNotBlank(preference)) {
            builder = builder.preference(preference);
        }
        esQueryResponse = builder.execute().actionGet(120, TimeUnit.SECONDS);

        if (esQueryResponse == null) {
            return null;
        }

        List<Object> objectList = esQueryResponse.getSourceList();
        if (objectList == null) {
            return esQueryResponse;
        }

        List<T> hits = Lists.newLinkedList();
        for (Object obj : objectList) {
            hits.add((T) obj);
        }

        scrollResultVisitor.handleScrollResult(hits);

        return esQueryResponse;
    }

    /**
     * ??????????????????
     *
     * @param scrollId
     * @param clzz
     * @param scrollResultVisitor
     * @param <T>
     * @return
     */
    public <T> ESQueryResponse queryScrollQuery(String indexName, String scrollId,
                                                Class<T> clzz, ScrollResultVisitor<T> scrollResultVisitor) {
        return queryScrollQuery(null, indexName, scrollId, clzz, scrollResultVisitor);
    }

    /**
     * ??????????????????
     *
     * @param scrollId
     * @param clzz
     * @param scrollResultVisitor
     * @param <T>
     * @return
     */
    public <T> ESQueryResponse queryScrollQuery(String clusterName, String indexName,
                                                String scrollId, Class<T> clzz,
                                                ScrollResultVisitor<T> scrollResultVisitor) {
        ESQueryResponse esQueryResponse = null;
        ESQueryScrollRequest queryScrollRequest = new ESQueryScrollRequest();
        queryScrollRequest.setScrollId(scrollId).scroll(new TimeValue(60000));
        queryScrollRequest.clazz(clzz);

        Tuple<String, ESClient> gatewayClientTuple = null;
        gatewayClientTuple = getGatewayClientByDataCenterAndIndexName(clusterName, indexName);
        esQueryResponse = gatewayClientTuple.v2().queryScroll(queryScrollRequest).actionGet(120, TimeUnit.SECONDS);

        if (esQueryResponse == null) {
            return null;
        }

        List<Object> objectList = esQueryResponse.getSourceList();
        if (objectList == null) {
            return esQueryResponse;
        }

        List<T> hits = Lists.newLinkedList();
        for (Object obj : objectList) {
            hits.add((T) obj);
        }

        scrollResultVisitor.handleScrollResult(hits);

        return esQueryResponse;
    }

    /**
     * ????????????????????????
     *
     * @param indexName
     * @param typeName
     * @param queryDsl
     * @param scrollSize
     * @param clzz
     * @return
     */
    public <T> void queryWithScroll(String indexName, String typeName, String queryDsl,
                                    int scrollSize, String preference, Class<T> clzz,
                                    ScrollResultVisitor<T> scrollResultVisitor) {
        queryWithScroll(null, indexName, typeName, queryDsl, scrollSize, preference, clzz,
            scrollResultVisitor);
    }

    /**
     * ????????????????????????
     *
     * @param indexName
     * @param typeName
     * @param queryDsl
     * @param scrollSize
     * @param clzz
     * @return
     */
    public <T> void queryWithScroll(String clusterName, String indexName,
                                    String typeName, String queryDsl, int scrollSize, String preference, Class<T> clzz,
                                    ScrollResultVisitor<T> scrollResultVisitor) {
        ESQueryResponse esQueryResponse = null;
        try {
            esQueryResponse = prepareScrollQuery(clusterName, indexName, typeName, queryDsl, preference,
                clzz, scrollResultVisitor);
        } catch (Exception e) {
            LOGGER.warn(
                "class=GatewayClient||method=queryWithScroll||dataCenter={}||indexName={}||queryDsl={}||errMsg=query error. ",
                EnvUtil.getDC(), indexName, queryDsl, e);
        }

        if (esQueryResponse == null) {
            return;
        }

        long totalCount = Long
            .parseLong(esQueryResponse.getHits().getUnusedMap().getOrDefault(ESConstant.HITS_TOTAL, "0").toString());
        int scrollCnt = (int) Math.ceil((double) totalCount / scrollSize);

        for (int scrollIndex = 0; scrollIndex < scrollCnt - 1; ++scrollIndex) {
            if (esQueryResponse == null) {continue;}

            String scrollId = esQueryResponse.getUnusedMap().get("_scroll_id").toString();

            try {
                esQueryResponse = queryScrollQuery(clusterName, indexName, scrollId, clzz,
                    scrollResultVisitor);
            } catch (Exception e) {
                LOGGER.warn(
                    "class=GatewayClient||method=queryWithScroll||dataCenter={}||scrollId={}||errMsg=query error. ",
                        EnvUtil.getDC(), scrollId, e);
            }
        }

    }

    /**
     * ??????????????????
     *
     * @param indexName
     * @param typeName
     * @param id
     * @param clzz
     * @param <T>
     * @return
     */
    public <T> T doGet(String indexName, String typeName, String id, Class<T> clzz) {
        return doGet(null, indexName, typeName, id, clzz);
    }

    /**
     * ??????????????????
     *
     * @param indexName
     * @param typeName
     * @param id
     * @param clzz
     * @param <T>
     * @return
     */
    public <T> T doGet(String clusterName, String indexName, String typeName, String id, Class<T> clzz) {
        ESGetRequest request = new ESGetRequest();
        request.index(indexName).type(typeName).id(id);

        ESGetResponse response = null;
        Tuple<String, ESClient> gatewayClientTuple = null;
        try {
            gatewayClientTuple = getGatewayClientByDataCenterAndIndexName(clusterName, indexName);
            response = gatewayClientTuple.v2().get(request).actionGet(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOGGER.warn(
                "class=GatewayClient||method=doGet||dataCenter={}||gatewayClientTuple={}||indexName={}||typeName={}||id={}||errMsg=get error. ",
                    EnvUtil.getDC(), JSON.toJSONString(gatewayClientTuple), indexName, typeName, id, e);
        }

        if (response == null) {
            return null;
        }

        T obj = null;
        try {
            obj = JSON.parseObject(JSON.toJSONString(response.getSource()), clzz);
        } catch (JSONException e) {
            LOGGER.warn(
                "class=GatewayClient||method=doGet||dataCenter={}||indexName={}||typeName={}||id={}||clzz={}||errMsg=fail to parse json. ",
                    EnvUtil.getDC(), indexName, typeName, id, clzz, e);
        }

        return obj;
    }

    /**
     * ????????????
     * @param queryRequest
     * @return
     */
    @Nullable
    private ESQueryResponse doQuery(String clusterName, String indexName, ESQueryRequest queryRequest) {
        Tuple<String, ESClient> gatewayClientTuple = null;
        try {
            gatewayClientTuple = getGatewayClientByDataCenterAndIndexName(clusterName, indexName);

            return gatewayClientTuple.v2().query(queryRequest).actionGet(120, TimeUnit.SECONDS);
        } catch (Exception e) {
            String queryDsl = bytesReferenceConvertDsl(queryRequest.source());
            LOGGER.warn(
                "class=GatewayClient||method=doQuery||dataCenter={}||gatewayClientTuple={}||clusterName={}||indexName={}||queryDsl={}||md5={}||errMsg=query error. ",
                    EnvUtil.getDC(), JSON.toJSONString(gatewayClientTuple), clusterName, queryRequest.indices(), queryDsl,
                CommonUtils.getMD5(queryDsl), e);
            return null;
        }
    }

    /**
     * @param clusterName ????????????
     * @param templateName ???????????????????????????gateway??????????????????????????????????????????????????????????????????
     * @param indexRequest indexRequest
     */
    private void doWrite(String clusterName, String templateName, ESIndexRequest indexRequest) {
        Tuple<String, ESClient> gatewayClientTuple = null;
        try {
            gatewayClientTuple = getGatewayClientByDataCenterAndIndexName(clusterName, templateName);
            gatewayClientTuple.v2().index(indexRequest);
        } catch (Exception e) {
            String dsl = bytesReferenceConvertDsl(indexRequest.source());
            LOGGER.warn(
                    "class=GatewayClient||method=doWrite||dataCenter={}||gatewayClientTuple={}||clusterName={}||indexName={}||queryDsl={}||md5={}||errMsg=query error. ",
                    EnvUtil.getDC(), JSON.toJSONString(gatewayClientTuple), clusterName, indexRequest.index(), dsl,
                    CommonUtils.getMD5(dsl), e);
        }
    }

    /**
     * ???????????????????????????????????????gateway?????????
     *
     * @param indexName
     * @return
     */
    private Tuple<String, ESClient> getGatewayClientByDataCenterAndIndexName(String clusterName, String indexName) {
        // ???????????????appId
        String appId = queryClientMap.keySet().iterator().next();

        // ????????????appid?????????????????????????????????????????????????????????????????????appid??????????????????????????????????????????
        if (queryClientMap.size() > 1 && StringUtils.isNotBlank(indexName)) {
            for (Map.Entry<String/*access template name*/, String/*appId*/> entry : accessTemplateNameMap
                .entrySet()) {
                // ??????????????????????????????*???????????????????????????????????????????????????????????????appId
                String accessTemplateName = StringUtils.removeEnd(entry.getKey(), "*");
                if (StringUtils.isNotBlank(accessTemplateName) && indexName.startsWith(accessTemplateName)) {
                    appId = entry.getValue();
                    break;
                }
            }
        }

        ESClient esClient = queryClientMap.get(appId);

        if (!EnvUtil.isOnline()) {
            LOGGER.info("class=GatewayClient||method=getGatewayClientByDataCenterAndIndexName||appId={}||indexName={}",
                appId, indexName);
        }

        Header appidHeader = appidHeaderMap.get(appId);
        if (esClient != null) {
            List<Header> headers = Lists.newArrayList();
            if (appidHeader != null) {
                // ???????????????
                headers.add(appidHeader);

                // ???????????????????????????
                if (StringUtils.isNotBlank(clusterName)) {
                    Header clusterNameHeader = new BasicHeader("CLUSTER_ID", clusterName);
                    headers.add(clusterNameHeader);
                }

                esClient.setHeaders(headers);
            }
        }

        return new Tuple<>(appId, esClient);
    }

    private ESClient buildGateWayClient(String url, Integer port, Header header, String clusterName) {
        String[] ipArray = null;
        TransportAddress[] transportAddresses = null;
        ESClient esClient = null;
        // ?????????????????????
        ipArray = StringUtils.splitByWholeSeparatorPreserveAllTokens(url, COMMA);
        if (ipArray != null && ipArray.length > 0) {
            try {
                esClient = new ESClient();
                transportAddresses = new TransportAddress[ipArray.length];
                for (int j = 0; j < ipArray.length; ++j) {
                    transportAddresses[j] = new InetSocketTransportAddress(new InetSocketAddress(ipArray[j], port));
                }
                esClient.addTransportAddresses(transportAddresses);
                if (header != null) {
                    esClient.setHeader(header);
                }
                if (StringUtils.isNotBlank(clusterName)) {
                    esClient.setClusterName(clusterName);
                }

                if (ioThreadCount > 0) {
                    esClient.setIoThreadCount(ioThreadCount);
                }

                // ??????http??????
                esClient.setRequestConfigCallback(builder -> builder.setConnectTimeout(10000).setSocketTimeout(120000)
                    .setConnectionRequestTimeout(120000));
                esClient.start();
            } catch (Exception e) {
                if(null != esClient){
                    esClient.close();
                }

                LOGGER.error("class=ESGatewayClient||method=buildGateWayClient||errMsg={}||url={}||port={}",
                    e.getMessage(), url, port, e);
                return null;
            }
        }

        return esClient;
    }

    /**
     * ??????dsl??????
     *
     * @param bytes
     * @return
     */
    private String bytesReferenceConvertDsl(BytesReference bytes) {
        try {
            return XContentHelper.convertToJson(bytes, false);
        } catch (IOException e) {
            LOGGER.warn("class=CommonUtils||method=bytesReferenceConvertDsl||errMsg=fail to covert", e);
        }

        return "";
    }
}