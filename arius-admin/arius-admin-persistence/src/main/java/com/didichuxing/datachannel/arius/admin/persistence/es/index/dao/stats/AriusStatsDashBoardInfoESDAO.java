package com.didichuxing.datachannel.arius.admin.persistence.es.index.dao.stats;

import static com.didichuxing.datachannel.arius.admin.common.constant.ClusterPhyMetricsContant.FIELD;
import static com.didichuxing.datachannel.arius.admin.common.constant.metrics.DashBoardMetricTopTypeEnum.listNoNegativeMetricTypes;
import static com.didichuxing.datachannel.arius.admin.common.constant.routing.ESRoutingConstant.CLUSTER_PHY_HEALTH_ROUTING;

import com.alibaba.fastjson.JSONObject;
import com.didichuxing.datachannel.arius.admin.common.Tuple;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.metrics.linechart.DashboardTopMetrics;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.metrics.linechart.MetricsContent;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.metrics.linechart.VariousLineChartMetrics;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.metrics.list.MetricList;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.metrics.list.MetricListContent;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.stats.dashboard.ClusterPhyHealthMetrics;
import com.didichuxing.datachannel.arius.admin.common.constant.AriusStatsEnum;
import com.didichuxing.datachannel.arius.admin.common.constant.metrics.DashBoardMetricListTypeEnum;
import com.didichuxing.datachannel.arius.admin.common.constant.metrics.DashBoardMetricOtherTypeEnum;
import com.didichuxing.datachannel.arius.admin.common.constant.metrics.DashBoardMetricTopTypeEnum;
import com.didichuxing.datachannel.arius.admin.common.constant.metrics.OneLevelTypeEnum;
import com.didichuxing.datachannel.arius.admin.common.util.*;
import com.didichuxing.datachannel.arius.admin.persistence.es.index.dsls.DslsConstant;
import com.didiglobal.logi.elasticsearch.client.response.query.query.ESQueryResponse;
import com.didiglobal.logi.elasticsearch.client.response.query.query.hits.ESHit;
import com.didiglobal.logi.elasticsearch.client.response.query.query.hits.ESHits;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class AriusStatsDashBoardInfoESDAO extends BaseAriusStatsESDAO {
    private static final FutureUtil<Void> FUTURE_UTIL = FutureUtil.init("AriusStatsDashBoardInfoESDAO",  10,10,500);
    public static final String            GTE         = "gte";
    public static final String            CLUSTER     = "cluster";
    
    @PostConstruct
    public void init() {
        super.indexName = dataCentreUtil.getAriusStatsDashBoardInfo();
        BaseAriusStatsESDAO.register(AriusStatsEnum.DASHBOARD_INFO, this);
    }

    /**
     * ??????dashboard??????list??????????????????????????? ??????dashboard_status ??? ???flag??????
     * @see                  DashBoardMetricListTypeEnum
     * @param oneLevelType  ???????????? ??????cluster node index template thread
     * @param metricsType   ????????????
     * @param aggType       ????????????
     * @param flag          ????????????????????? true ?????? false ???
     * @param sortType      ????????????   asc decs
     * @return              List<MetricList>
     */
    public MetricList fetchListFlagMetric(String oneLevelType, String metricsType, String aggType, String flag, String sortType) {
        String dsl = dslLoaderUtil.getFormatDslByFileName(DslsConstant.FETCH_LIST_FLAG_METRIC,
                oneLevelType, sortType, oneLevelType, oneLevelType,
                oneLevelType, oneLevelType, metricsType, flag, oneLevelType, NOW_6M, NOW_1M);
        String realIndex = IndexNameUtils.genCurrentDailyIndexName(indexName);

        return gatewayClient.performRequest(metadataClusterName, realIndex, TYPE, dsl,
                s -> fetchRespMetrics(s, oneLevelType, metricsType, /*?????????????????????????????????*/false), 3);
    }

    /**
     * ??????dashboard??????list??????????????????
     * @see                  DashBoardMetricListTypeEnum
     * @param oneLevelType  ???????????? ??????cluster node index template thread
     * @param metricsType   ????????????
     * @param aggType       ????????????
     * @param sortType      ????????????   asc decs
     * @return              List<MetricList>
     */
    public MetricList fetchListValueMetrics(String oneLevelType, String metricsType, String aggType, String sortType) {
        String dsl = dslLoaderUtil.getFormatDslByFileName(DslsConstant.FETCH_LIST_VALUE_METRIC,
                oneLevelType, oneLevelType, oneLevelType, oneLevelType, metricsType, oneLevelType,
                NOW_6M, NOW_1M, oneLevelType, metricsType, oneLevelType, metricsType, sortType);

        String realIndex = IndexNameUtils.genCurrentDailyIndexName(indexName);
        return gatewayClient.performRequest(metadataClusterName, realIndex, TYPE, dsl,
                s -> fetchRespMetrics(s, oneLevelType, metricsType, /*?????????????????????????????????*/true), 3);
    }

    /**
     * ??????????????????
     *
     * @param s              ????????????
     * @param oneLevelType   ???????????????
     * @param metricsType    ???????????????
     * @param hasGetValue    ??????????????????????????????
     * @return               MetricList
     */
    private MetricList fetchRespMetrics(ESQueryResponse s, String oneLevelType, String metricsType, boolean hasGetValue) {
        MetricList metricList = new MetricList();
        metricList.setType(metricsType);
        List<MetricListContent> metricListContents = Lists.newArrayList();
        // ????????????
        List<String> repeatList = Lists.newArrayList();
        ESHits hits = s.getHits();
        if (null != hits && CollectionUtils.isNotEmpty(hits.getHits())) {
            for (ESHit hit : hits.getHits()) {
                if (null != hit.getSource() &&
                        null != ((JSONObject) hit.getSource()).getJSONObject(oneLevelType) &&
                        null != ((JSONObject) hit.getSource()).getJSONObject(oneLevelType).getString(CLUSTER) &&
                        null != ((JSONObject) hit.getSource()).getJSONObject(oneLevelType).getString(oneLevelType)) {

                    JSONObject healthMetricsJb = ((JSONObject) hit.getSource()).getJSONObject(oneLevelType);
                    String cluster = healthMetricsJb.getString(CLUSTER);
                    String metricsTypeValue/*node template index thread-pool*/    = healthMetricsJb.getString(oneLevelType);
                    if (AriusObjUtils.isBlank(metricsTypeValue)) { continue;}

                    // ????????????
                    String repeatKey = cluster + "@" + metricsTypeValue;
                    if (repeatList.contains(repeatKey)) { continue;}
                    else { repeatList.add(repeatKey);}

                    MetricListContent metricListContent = new MetricListContent();
                    metricListContent.setClusterPhyName(cluster);
                    metricListContent.setName(metricsTypeValue);

                    // ???????????????????????????
                    if (hasGetValue) {
                        Double value   = healthMetricsJb.getDouble(metricsType);
                        value = Double.valueOf(String.format("%.2f", value));
                        metricListContent.setValue(value);
                    }
                    metricListContents.add(metricListContent);
                }
            }
        }

        metricList.setMetricListContents(metricListContents);
        metricList.setCurrentTime(System.currentTimeMillis());
        return metricList;
    }

    /**
     * ??????dashboard??????TopN????????????
     * @see   DashBoardMetricTopTypeEnum
     * @param oneLevelType        ??????????????? cluster node template index
     * @param metricsTypes        ??????????????????
     * @param topNu               5 10 15 20
     * @param aggType             ???????????????????????????
     * @param startTime           ????????????
     * @param endTime             ????????????
     * @return
     */
    public List<VariousLineChartMetrics> fetchTopMetric(String oneLevelType, List<String> metricsTypes, Integer topNu, String aggType,
                                                        Long startTime, Long endTime) {
        List<VariousLineChartMetrics> buildMetrics = Lists.newCopyOnWriteArrayList();
        //??????TopN????????????/??????/??????/???????????????
        List<DashboardTopMetrics> dashboardTopMetricsList = getTopMetricsForDashboard(oneLevelType, metricsTypes, topNu, aggType,
                esNodesMaxNum, startTime, endTime);
        
        //??????????????????TopN??????
        for (DashboardTopMetrics dashboardTopMetrics : dashboardTopMetricsList) {
            FUTURE_UTIL.runnableTask(() -> buildTopNSingleMetrics(buildMetrics, oneLevelType, aggType,
                    clusterMaxNum, startTime, endTime, dashboardTopMetrics));
        }
        FUTURE_UTIL.waitExecute();

        return buildMetrics;
    }

    /**
     * ??????dashboard????????????????????????
     * @see        DashBoardMetricOtherTypeEnum
     * @return     ClusterPhyHealthMetrics
     */
    public ClusterPhyHealthMetrics fetchClusterHealthInfo() {
        String key = DashBoardMetricOtherTypeEnum.CLUSTER_HEALTH.getType();
        String dsl = dslLoaderUtil.getFormatDslByFileName(DslsConstant.FETCH_CLUSTER_HEALTH_INFO, key, key);
        String realIndex = IndexNameUtils.genCurrentDailyIndexName(indexName);

        return gatewayClient.performRequestWithRouting(metadataClusterName, CLUSTER_PHY_HEALTH_ROUTING, realIndex, TYPE, dsl, s-> {
            ClusterPhyHealthMetrics clusterPhyHealthMetrics = new ClusterPhyHealthMetrics();
            if (null == s) {
                LOGGER.warn("class=AriusStatsDashBoardInfoESDAO||method=fetchClusterHealthInfo||msg=response is null");
                return clusterPhyHealthMetrics;
            }

            try {
                ESHits hits = s.getHits();
                if (null != hits && CollectionUtils.isNotEmpty(hits.getHits())) {
                    for (ESHit hit : hits.getHits()) {
                        if (null != hit.getSource()) {
                            JSONObject source = (JSONObject) hit.getSource();
                            JSONObject healthMetricsJb = source.getJSONObject(key);
                            return null == healthMetricsJb ? clusterPhyHealthMetrics :
                                    ConvertUtil.obj2ObjByJSON(healthMetricsJb, ClusterPhyHealthMetrics.class);
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.error(
                        "class=AriusStatsDashBoardInfoESDAO||method=fetchClusterHealthInfo||sumKey={}||value={}||response={}", e);
            }
            return clusterPhyHealthMetrics; }, 3);
    }

    /****************************************************private*************************************************************/
    /**
     *  ??????????????????????????????????????????TopN???????????????
     *  ???????????????????????????????????????????????????null????????????????????? - 1, ???????????????, ???????????????3??????
     *
     *
     * @param oneLevelType     ??????????????? cluster node template index
     * @param metricsTypes     ????????????
     * @param topNu            topN
     * @param aggType          ????????????
     * @param dashboardClusterMaxNum    ??????????????????????????????agg bucket number???
     * @param startTime        ????????????
     * @param endTime          ????????????
     * @return
     */
    private List<DashboardTopMetrics> getTopMetricsForDashboard(String oneLevelType, List<String> metricsTypes, Integer topNu,
                                                                String aggType, int dashboardClusterMaxNum, Long startTime, Long endTime) {
        // ????????????????????????????????????
        Long timePoint = getDashboardHasDataTime(oneLevelType, startTime, endTime);
        // ????????????
        if (null == timePoint) { return Lists.newArrayList();}

        Tuple<Long, Long> firstInterval = MetricsUtils.getSortInterval(endTime - startTime, timePoint);
        long startInterval              = firstInterval.getV1();
        long endInterval                = firstInterval.getV2();

        // ??????????????????MetricsUtils.getIntervalForDashBoard
        String interval      = MetricsUtils.getInterval(endTime - startTime);
        String realIndexName = IndexNameUtils.genDailyIndexName(indexName, startInterval, endInterval);
        
        Tuple<List<String>/*??????????????????*/, List<String>/*????????????????????????????????????*/> commonAndNoNegativeMetricsTuple
                = getCommonMetricsAndNoNegativeMetrics(metricsTypes);
        List<String>     commonMetricTypeList  = commonAndNoNegativeMetricsTuple.getV1();
        List<String>     noNegativeMetricsList = commonAndNoNegativeMetricsTuple.getV2();
        List<DashboardTopMetrics> finalTopMetrics       = Lists.newArrayList();

        // ??????????????????
        if (CollectionUtils.isNotEmpty(commonMetricTypeList)) {
            String aggsDsl = dynamicBuildDashboardAggsDSLForTop(oneLevelType, commonMetricTypeList, aggType);
            String dsl     = getFinalDslByOneLevelType(oneLevelType, startInterval, endInterval, dashboardClusterMaxNum, interval, aggsDsl);
            if (null == dsl) { return finalTopMetrics;}

            List<VariousLineChartMetrics> variousLineChartMetrics = gatewayClient.performRequest(metadataClusterName, realIndexName, TYPE, dsl,
                    s -> fetchMultipleAggMetrics(s, oneLevelType, commonMetricTypeList, topNu), 3);
            
            variousLineChartMetrics.stream().map(this::buildDashboardTopMetrics).forEach(finalTopMetrics::add);
        }
        
        // ????????????????????????
        if (CollectionUtils.isNotEmpty(noNegativeMetricsList)) {
            // ????????????????????????????????????agg_filter????????????
            String aggsDsl = dynamicBuildDashboardNoNegativeAggsDSLForTop(oneLevelType, noNegativeMetricsList, aggType);
            String dsl     = getFinalDslByOneLevelType(oneLevelType, startInterval, endInterval, dashboardClusterMaxNum, interval, aggsDsl);
            if (null == dsl) { return finalTopMetrics;}

            List<VariousLineChartMetrics> variousLineChartMetrics = gatewayClient.performRequest(metadataClusterName, realIndexName, TYPE, dsl,
                s -> fetchMultipleNoNegativeAggMetrics(s, oneLevelType,noNegativeMetricsList,
                    topNu), 3);
    
            variousLineChartMetrics.stream().map(this::buildDashboardTopMetrics).forEach(finalTopMetrics::add);
        }
        return finalTopMetrics;
    }

    DashboardTopMetrics buildDashboardTopMetrics(VariousLineChartMetrics variousLineChartMetrics) {
        DashboardTopMetrics dashboardTopMetrics = new DashboardTopMetrics();
        dashboardTopMetrics.setType(variousLineChartMetrics.getType());
        List<Tuple<String/*????????????*/, String/*????????????/????????????/????????????/????????????*/>> dashboardTopInfo = Lists.newArrayList();
        for (MetricsContent metricsContent : variousLineChartMetrics.getMetricsContents()) {
            Tuple<String/*????????????*/, String/*????????????/????????????/????????????/????????????*/> cluster2NameTuple = new Tuple<>();
            String cluster = metricsContent.getCluster();
            // ???????????????, ??????????????????
            if (!AriusObjUtils.isBlank(cluster)) { cluster2NameTuple.setV1(cluster);}
            else { cluster2NameTuple.setV1(metricsContent.getName());}

            cluster2NameTuple.setV2(metricsContent.getName());
            dashboardTopInfo.add(cluster2NameTuple);
        }

        dashboardTopMetrics.setDashboardTopInfo(dashboardTopInfo);
        return dashboardTopMetrics;
    }
    
    
    /**
     * ???????????????????????????????????????<em>query</em>????????????<em>filter</em>?????????????????????????????????????????????????????????
     * ????????????<em>agg</em>???<em>filter</em>???????????????????????????????????????????????????????????????????????????????????????????????????
     * ?????????{@linkplain #fetchMultipleNoNegativeAggMetrics(ESQueryResponse, List, Integer)}
     * ???????????????????????????
     * agg:
     *             "gatewaySucPer": {
     *               "filter": {
     *                 "range": {
     *                   "cluster.gatewaySucPer": {
     *                     "gte": 0
     *                   }
     *                 }
     *               },
     *               "aggs": {
     *                 "gatewaySucPer": {
     *                   "avg": {
     *                     "field": "cluster.gatewaySucPer",
     *                     "missing": 0
     *                   }
     *                 }
     *               }
     *             }
     * @param oneLevelType
     * @param noNegativeMetricsList
     * @param aggType ????????????avg???sum???avg?????????????????????
     * @return
     */
    private String dynamicBuildDashboardNoNegativeAggsDSLForTop(String oneLevelType,
        List<String> noNegativeMetricsList, String aggType) {
        List<String> dslList=Lists.newArrayList();
        for (String field : noNegativeMetricsList) {
            final String dsl = dslLoaderUtil.getFormatDslByFileName(
                DslsConstant.GET_AGG_FILTER_FRAGMENT,
                 field, oneLevelType, field, GTE, 0,  field, aggType,
                oneLevelType, field);
            dslList.add(dsl);
            
        }
        return String.join(",", dslList);
    }
    
    /**
     * ???????????????????????? ????????????????????????(@link CLUSTER_GATEWAY_FAILED_PER CLUSTER_GATEWAY_SUC_PER)
     * 
     * @param metricsTypes ??????????????????
     * @return Tuple
     */
    private Tuple<List<String>/*??????????????????*/, List<String>/*????????????????????????????????????*/> getCommonMetricsAndNoNegativeMetrics(List<String> metricsTypes) {
        Tuple<List<String>, List<String>> noNegativeMetricsAndCommonMetricsTuple = new Tuple<>();
        List<String> commonMetricTypeList     = Lists.newArrayList();
        List<String> noNegativeMetricTypeList = Lists.newArrayList();
        // ??????????????????????????????
        for (String metricsType : metricsTypes) {
            if (listNoNegativeMetricTypes().contains(metricsType)) { noNegativeMetricTypeList.add(metricsType);}
            else { commonMetricTypeList.add(metricsType);}
        }

        noNegativeMetricsAndCommonMetricsTuple.setV1(commonMetricTypeList);
        noNegativeMetricsAndCommonMetricsTuple.setV2(noNegativeMetricTypeList);
        return noNegativeMetricsAndCommonMetricsTuple;
    }

    /**
     * ??????????????????????????????????????????dsl??????
     * @param oneLevelType             ??????????????????
     * @param startInterval            ????????????
     * @param endInterval              ????????????
     * @param dashboardClusterMaxNum   ????????????????????????????????????????????????????????????????????????
     * @param interval                 agg?????????????????? ???1m, 5m 10m ??????
     * @param aggsDsl                  ???????????????agg????????????
     * @return  dsl??????
     */
    private String getFinalDslByOneLevelType(String oneLevelType, long startInterval, long endInterval,
                                             int dashboardClusterMaxNum, String interval, String aggsDsl) {
        // ?????? clusterThreadPoolQueue?????? ????????????dsl
        if (OneLevelTypeEnum.CLUSTER_THREAD_POOL_QUEUE.getType().equals(oneLevelType)) {
            return dslLoaderUtil.getFormatDslByFileName(DslsConstant.GET_AGG_DASHBOARD_CLUSTER_TOP_NAME_INFO, oneLevelType,
                startInterval, endInterval, oneLevelType, CLUSTER, dashboardClusterMaxNum, oneLevelType, interval,
                aggsDsl);
        }

        // ?????? cluster?????? ????????????dsl
        if (OneLevelTypeEnum.CLUSTER.getType().equals(oneLevelType)) {
            return dslLoaderUtil.getFormatDslByFileName(DslsConstant.GET_AGG_DASHBOARD_CLUSTER_TOP_NAME_INFO, oneLevelType,
                    startInterval, endInterval, oneLevelType, oneLevelType, dashboardClusterMaxNum, oneLevelType, interval,
                    aggsDsl);
        }

        // ?????? ???cluster?????? ????????????dsl
        if (OneLevelTypeEnum.listNoClusterOneLevelType().contains(oneLevelType)) {
            return dslLoaderUtil.getFormatDslByFileName(DslsConstant.GET_AGG_DASHBOARD_NO_CLUSTER_TOP_NAME_INFO, oneLevelType,
                    startInterval, endInterval, oneLevelType, oneLevelType, oneLevelType, dashboardClusterMaxNum, oneLevelType, interval,
                    aggsDsl);
        }
        return null;
    }

    /**
     *
     * @param oneLevelType     ??????????????????
     * @param topClustersStr   top????????????
     * @param topNameStr       top?????? ???????????????????????????????????????????????????????????????
     * @param startTime        ????????????
     * @param endTime          ????????????
     * @param dashboardClusterMaxNum   ????????????????????????
     * @param interval                 ????????????
     * @param aggsDsl                  ???????????????
     * @return
     */
    private String getFinalDslByOneLevelType(String oneLevelType, String topClustersStr, String topNameStr, Long startTime, Long endTime,
                                             int dashboardClusterMaxNum, String interval, String aggsDsl) {

        // ??????????????????dsl?????? clusterThreadPoolQueue???
        if (OneLevelTypeEnum.CLUSTER_THREAD_POOL_QUEUE.getType().equals(oneLevelType)) {
            return dslLoaderUtil.getFormatDslByFileName(DslsConstant.GET_TOP_DASHBOARD_CLUSTER_AGG_METRICS_INFO, oneLevelType,
                    CLUSTER, topNameStr, oneLevelType, startTime, endTime, oneLevelType, CLUSTER,
                    dashboardClusterMaxNum, oneLevelType, interval, startTime, endTime, aggsDsl);
        }

        // ?????? cluster?????? ????????????dsl
        if (OneLevelTypeEnum.CLUSTER.getType().equals(oneLevelType)) {
            return dslLoaderUtil.getFormatDslByFileName(DslsConstant.GET_TOP_DASHBOARD_CLUSTER_AGG_METRICS_INFO, oneLevelType,
                    oneLevelType, topNameStr, oneLevelType, startTime, endTime, oneLevelType, oneLevelType,
                    dashboardClusterMaxNum, oneLevelType, interval, startTime, endTime, aggsDsl);
        }

        // ?????? ???cluster?????? ????????????dsl
        if (OneLevelTypeEnum.listNoClusterOneLevelType().contains(oneLevelType)) {
            return dslLoaderUtil.getFormatDslByFileName(DslsConstant.GET_TOP_DASHBOARD_NO_CLUSTER_AGG_METRICS_INFO,
                    oneLevelType, topClustersStr,
                    oneLevelType, oneLevelType, topNameStr, oneLevelType, startTime, endTime,
                    oneLevelType, oneLevelType, oneLevelType,
                    dashboardClusterMaxNum, oneLevelType, interval, startTime, endTime, aggsDsl);
        }
        return null;
    }

    /**
     * ????????????TopN????????????????????????
     * @param buildMetrics              ????????????????????????
     * @param oneLevelType              ????????????
     * @param aggType                   ????????????
     * @param dashboardClusterMaxNum    dashboard????????????????????????
     * @param startTime                 ????????????
     * @param endTime                   ????????????
     * @param dashboardTopMetrics       ??????????????????????????????topN?????????????????????topN?????????/??????/??????/????????????
     */
    private void buildTopNSingleMetrics(List<VariousLineChartMetrics> buildMetrics, String oneLevelType,
                                        String aggType, int dashboardClusterMaxNum, Long startTime, Long endTime,
                                        DashboardTopMetrics dashboardTopMetrics) {
        List<Tuple<String, String>> dashboardTopInfo = dashboardTopMetrics.getDashboardTopInfo();
        // ????????????topN????????????
        List<String> topDistinctClusters = dashboardTopInfo.stream().map(Tuple::getV1).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        String topClustersStr = CollectionUtils.isNotEmpty(topDistinctClusters) ? buildTopNameStr(topDistinctClusters) : null;
        if (StringUtils.isBlank(topClustersStr)) { return;}

        // ????????????topN??????/??????/????????????
        List<String> topDistinctNames = dashboardTopInfo.stream().map(Tuple::getV2).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        String topNameStr = CollectionUtils.isNotEmpty(topDistinctNames) ? buildTopNameStr(topDistinctNames) : null;
        if (StringUtils.isBlank(topNameStr)) { return;}

        String interval          = MetricsUtils.getIntervalForDashBoard(endTime - startTime);
        List<String> metricsKeys = Lists.newArrayList(dashboardTopMetrics.getType());

        String aggsDsl = dynamicBuildDashboardAggsDSLForTop(oneLevelType, metricsKeys, aggType);

        String dsl = getFinalDslByOneLevelType(oneLevelType, topClustersStr, topNameStr, startTime, endTime, dashboardClusterMaxNum, interval, aggsDsl);
        if (null == dsl) { return;}

        String realIndexName = IndexNameUtils.genDailyIndexName(indexName, startTime, endTime);
        List<VariousLineChartMetrics> variousLineChartMetrics = gatewayClient.performRequestWithRouting(metadataClusterName,
                null, realIndexName, TYPE, dsl, s -> fetchMultipleAggMetrics(s, oneLevelType, metricsKeys, null), 3);

        // ???????????????????????????????????????????????????????????????/??????/?????????????????????
        filterValidMetricsInfo(variousLineChartMetrics, dashboardTopMetrics);

        buildMetrics.addAll(variousLineChartMetrics);
    }

    /**
     * ???????????????????????????????????????????????????????????????/??????/?????????????????????
     * @param variousLineChartMetrics    ???????????????
     * @param dashboardTopMetrics        ??????????????????????????????
     */
    private void filterValidMetricsInfo(List<VariousLineChartMetrics> variousLineChartMetrics, DashboardTopMetrics dashboardTopMetrics) {

        for (VariousLineChartMetrics variousLineChartMetric : variousLineChartMetrics) {
            if (!dashboardTopMetrics.getType().equals(variousLineChartMetric.getType())) { return;}

            List<MetricsContent> metricsContents = variousLineChartMetric.getMetricsContents();
            if (CollectionUtils.isEmpty(metricsContents)) { return;}

            List<Tuple<String, String>> dashboardTopInfo = dashboardTopMetrics.getDashboardTopInfo();
            List<String> validMetricInfo = Lists.newArrayList();
            for (Tuple<String, String> cluster2NameTuple : dashboardTopInfo) {
                String cluster      = cluster2NameTuple.getV1();
                String name         = cluster2NameTuple.getV2();
                validMetricInfo.add(CommonUtils.getUniqueKey(cluster, name));
            }

            List<MetricsContent> validMetricsContentList = Lists.newArrayList();
            for (MetricsContent metricsContent : metricsContents) {
                String cluster = metricsContent.getCluster();
                String name    = metricsContent.getName();
                if (validMetricInfo.contains(CommonUtils.getUniqueKey(cluster, name))) {
                    validMetricsContentList.add(metricsContent);
                }
            }
            // ??????
            variousLineChartMetric.setMetricsContents(validMetricsContentList);
        }
    }

    /**
     * ??????dashboard ?????????????????????????????????????????????
     * @param oneLevelType
     * @param startTime
     * @param endTime
     * @return
     */
    private Long getDashboardHasDataTime(String oneLevelType, long startTime, long endTime) {
        String dsl = dslLoaderUtil.getFormatDslByFileName(DslsConstant.GET_HAS_DASHBOARD_METRICS_DATA_TIME,
            oneLevelType, oneLevelType, startTime, endTime, oneLevelType);

        String realIndexName = IndexNameUtils.genDailyIndexName(indexName, startTime, endTime);

        return gatewayClient.performRequest(metadataClusterName, realIndexName, TYPE, dsl, s -> {
                    ESHits hits = s.getHits();
                    if (null != hits && CollectionUtils.isNotEmpty(hits.getHits())) {
                        for (ESHit hit : hits.getHits()) {
                            if (null != hit.getSource()) {
                                JSONObject source = (JSONObject) hit.getSource();
                                JSONObject healthMetricsJb = source.getJSONObject(oneLevelType);
                                return null == healthMetricsJb ? null : healthMetricsJb.getLongValue("timestamp");
                            }
                        }
                    }
                    return null;
                }, 3);
    }

    /**
     * ??????topN??????, ????????????agg??????
     * "cluster.indexingLatency": {
     *               "max": {
     *                 "field": "cluster.indexingLatency"
     *               }
     *             },
     *             "cluster.searchLatency": {
     *               "max": {
     *                 "field": "cluster.searchLatency"
     *               }
     *             }
     *
     * @param oneLevelType ??????????????????
     * @param metrics      ??????????????????
     * @param aggType      ????????????
     * @return             StringText
     */
    private String dynamicBuildDashboardAggsDSLForTop(String oneLevelType, List<String> metrics, String aggType) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < metrics.size(); i++) {
            String metricName = metrics.get(i);
            Map<String, String> aggsSubSubCellMap = Maps.newHashMap();
            aggsSubSubCellMap.put(FIELD, oneLevelType + "." + metricName);

            buildAggsDslMap(aggType, sb, metricName, aggsSubSubCellMap);
            if (i != metrics.size() - 1) {
                sb.append(",").append("\n");
            }
        }

        return sb.toString();
    }
}