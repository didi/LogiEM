package com.didichuxing.datachannel.arius.admin.metadata.job.cluster.monitor.esmonitorjob;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.alibaba.fastjson.JSON;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.MulityTypeTemplatesInfo;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ClusterPhy;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.stats.*;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.template.IndexTemplatePhyWithLogic;
import com.didichuxing.datachannel.arius.admin.common.component.SpringTool;
import com.didichuxing.datachannel.arius.admin.common.event.metrics.MetricsMonitorCollectTimeEvent;
import com.didichuxing.datachannel.arius.admin.common.event.metrics.MetricsMonitorIndexEvent;
import com.didichuxing.datachannel.arius.admin.common.event.metrics.MetricsMonitorNodeEvent;
import com.didichuxing.datachannel.arius.admin.common.exception.ESRunTimeException;
import com.didichuxing.datachannel.arius.admin.common.util.CommonUtils;
import com.didichuxing.datachannel.arius.admin.common.util.EnvUtil;
import com.didichuxing.datachannel.arius.admin.common.util.FutureUtil;
import com.didichuxing.datachannel.arius.admin.common.util.HttpHostUtil;
import com.didichuxing.datachannel.arius.admin.core.service.common.AriusConfigInfoService;
import com.didichuxing.datachannel.arius.admin.metadata.job.cluster.monitor.esmonitorjob.index.ESIndexStatsAction;
import com.didichuxing.datachannel.arius.admin.metadata.job.cluster.monitor.esmonitorjob.index.ESIndexStatsResponse;
import com.didichuxing.datachannel.arius.admin.metadata.job.cluster.monitor.esmonitorjob.metrics.CollectMetrics;
import com.didichuxing.datachannel.arius.admin.metadata.job.cluster.monitor.esmonitorjob.metrics.DCDRMetrics;
import com.didichuxing.datachannel.arius.admin.metadata.job.cluster.monitor.esmonitorjob.metrics.ESNodeToIndexComputer;
import com.didichuxing.datachannel.arius.admin.metadata.job.cluster.monitor.esmonitorjob.metrics.MetricsRegister;
import com.didichuxing.datachannel.arius.admin.metadata.job.cluster.monitor.esmonitorjob.node.ESNodesAction;
import com.didichuxing.datachannel.arius.admin.metadata.job.cluster.monitor.esmonitorjob.node.ESNodesResponse;
import com.didichuxing.datachannel.arius.admin.metadata.job.cluster.monitor.esmonitorjob.node.ESNodesStatsAction;
import com.didichuxing.datachannel.arius.admin.metadata.job.cluster.monitor.esmonitorjob.node.ESNodesStatsResponse;
import com.didichuxing.datachannel.arius.admin.metadata.utils.MonitorUtil;
import com.didiglobal.logi.elasticsearch.client.ESClient;
import com.didiglobal.logi.elasticsearch.client.request.dcdr.DCDRIndexStats;
import com.didiglobal.logi.elasticsearch.client.request.dcdr.DCDRStats;
import com.didiglobal.logi.elasticsearch.client.request.index.stats.IndicesStatsLevel;
import com.didiglobal.logi.elasticsearch.client.response.cluster.nodesstats.ClusterNodeStats;
import com.didiglobal.logi.elasticsearch.client.response.cluster.nodesstats.ESClusterNodesStatsResponse;
import com.didiglobal.logi.elasticsearch.client.response.dcdr.ESGetDCDRStatsResponse;
import com.didiglobal.logi.elasticsearch.client.response.indices.catindices.CatIndexResult;
import com.didiglobal.logi.elasticsearch.client.response.indices.catindices.ESIndicesCatIndicesResponse;
import com.didiglobal.logi.elasticsearch.client.response.indices.stats.IndexNodes;
import com.didiglobal.logi.elasticsearch.client.response.model.indices.CommonStat;
import com.didiglobal.logi.elasticsearch.client.response.model.node.NodeAttributes;
import com.didiglobal.logi.log.ILog;
import com.didiglobal.logi.log.LogFactory;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.common.StopWatch;
import org.springframework.beans.BeanUtils;

import static com.didichuxing.datachannel.arius.admin.metadata.job.cluster.monitor.esmonitorjob.node.ESNodesStatsRequest.HTTP;

/**
 * ???????????????????????????
 */
public class MonitorClusterJob {

    protected static final ILog LOGGER = LogFactory.getLog(MonitorClusterJob.class);

    private static final String     HOST_NAME               = HttpHostUtil.HOST_NAME;
    private static final long       CLIENT_TO_WITH_MILLS    = 50 * 1000L;

    private static final String[]   DATA_FORMATS = new String[] {"_YYYYMM", "YYYYMM", "YYYYMMdd", "_YYYYMMdd", "YYYY-MM-dd", "_YYYY-MM-dd",
                                                                 "MMdd", "YYMM", "_YYMM", "YY-MM", "_YYYY-MM", "YYYY",  "_YYYY.MM.dd", "YYYY.MM.dd"};

    private Pattern pattern   = Pattern.compile("(.*)(_v[1-9]\\d*)(.*)");

    private static final String COMPUTE       = "compute";
    private static final String ES_NODE       = "es.node.";
    private static final String TIME_OUT      = "collect task already timeout";
    private static final String ES_INDICES    = "es.indices.";

    //key: cluster@templateName
    private Cache<String, IndexTemplatePhyWithLogic> indexTemplateCache = CacheBuilder.newBuilder()
            .expireAfterWrite(60, TimeUnit.MINUTES).maximumSize(10000).build();

    //????????????
    private List<CollectMetrics> indexWorkOrders;
    private List<CollectMetrics> indexToNodeWorkOrders;
    private List<CollectMetrics> nodeWorkOrders;
    private List<CollectMetrics> nodeToIndexWorkOrders;
    private List<CollectMetrics> ingestWorkOrders;
    private List<CollectMetrics> dcdrWorkOrders;

    //????????????????????????????????????
    private List<ESIndexToNodeTempBean> indexToNodeTemps      = new CopyOnWriteArrayList<>();

    //??????nodeid ??? ?????????es???????????????????????????
    private Map<String, ESNodeStats>    nodeIdEsNodeStatsMap  = new ConcurrentHashMap<>();

    private List<IndexTemplatePhyWithLogic>      indexTemplates        = new CopyOnWriteArrayList<>();

    private ESClient                    esClient;

    private MonitorMetricsSender        monitorMetricsSender;

    private MetricsRegister             metricsRegister;

    private Set<String>                 clusterNodeIps = Collections.synchronizedSet(new HashSet<>());

    private ClusterPhy                  clusterPhy;

    // ???type?????? ???????????????????????????
    private MulityTypeTemplatesInfo     mulityTypeTemplatesInfo;

    private AriusConfigInfoService      ariusConfigInfoService;

    private String                      clusterName;

    private static final FutureUtil<ESNodesStatsResponse>   nodeStatsFuture   = FutureUtil.init("MonitorClusterJob-nodeStats",  10,10,20);
    private static final FutureUtil<ESIndexStatsResponse>   indexStatsFuture  = FutureUtil.init("MonitorClusterJob-indexStats",  10,10,20);

    private StopWatch indexStopWatch        = new StopWatch();
    private StopWatch nodeStopWatch         = new StopWatch();
    private StopWatch dcdrStopWatch         = new StopWatch();
    private StopWatch index2NodeStopWatch   = new StopWatch();

    // ?????????
    private static final String CONFIG_VALUE_GROUP = "arius.meta.monitor";

    // ??????????????????????????????
    private static final String CONFIG_NAME_NODESTAT_COLLECT_CONCURRENT = "nodestat.collect.concurrent";

    // ??????????????????????????????
    private static final String CONFIG_NAME_INDEXSTAT_COLLECT_CONCURRENT = "indexstat.collect.concurrent";

    public MonitorClusterJob(ESClient esClient,
                             String ariusClusterName,
                             ClusterPhy clusterPhy,
                             List<IndexTemplatePhyWithLogic> indexTemplates,
                             MetricsRegister metricsRegister,
                             MonitorMetricsSender monitorMetricsSender,
                             List<CollectMetrics> indexWorkOrders,
                             List<CollectMetrics> nodeWorkOrders,
                             List<CollectMetrics> indexToNodeWorkOrders,
                             List<CollectMetrics> nodeToIndexWorkOrders,
                             List<CollectMetrics> ingestWorkOrders,
                             List<CollectMetrics> dcdrWorkOrders,
                             MulityTypeTemplatesInfo mulityTypeTemplatesInfo,
                             AriusConfigInfoService  ariusConfigInfoService
                             ) {
        this.esClient                 = esClient;
        this.clusterPhy               = clusterPhy;
        this.metricsRegister          = metricsRegister;
        this.monitorMetricsSender     = monitorMetricsSender;
        this.indexWorkOrders          = indexWorkOrders;
        this.nodeWorkOrders           = nodeWorkOrders;
        this.indexToNodeWorkOrders    = indexToNodeWorkOrders;
        this.nodeToIndexWorkOrders    = nodeToIndexWorkOrders;
        this.ingestWorkOrders         = ingestWorkOrders;
        this.dcdrWorkOrders           = dcdrWorkOrders;
        this.mulityTypeTemplatesInfo  = mulityTypeTemplatesInfo;
        this.clusterName              = ariusClusterName;
        this.ariusConfigInfoService   = ariusConfigInfoService;
        this.indexTemplates.addAll(indexTemplates);
    }

    public void collectData(String ariusClusterName) {
        collectNodeData(ariusClusterName, esClient, metricsRegister);
        collectIndexData(ariusClusterName, esClient, metricsRegister);
        //collectIndexToNodeData();

        LOGGER.info("class=MonitorClusterJob||method=collectData||clusterName={}||indexStopWatch={}||nodeStopWatch={}||dcdrStopWatch={}||index2NodeStopWatch={}",
                ariusClusterName, indexStopWatch.toString(), nodeStopWatch.toString(), dcdrStopWatch.toString(), index2NodeStopWatch.toString());
    }

    /**************************************** private methods ****************************************/
    /**
     * ??????clientNode?????????????????????????????????stats
     * @param esClient esClient
     * @return ????????????stats???key-??????ID???value-??????stats
     */
    private Map<String, ClusterNodeStats> getNodeStatsByOnce(ESClient esClient) {
        ESClusterNodesStatsResponse response = esClient.admin().cluster().prepareNodeStats()
                .level(IndicesStatsLevel.INDICES.getStr())
                .execute()
                .actionGet(CLIENT_TO_WITH_MILLS);
        return response.getNodes();
    }

    /**
     * ??????????????????????????????ID
     * @param esClient esClient
     */
    private List<String> getClusterNodeIds(ESClient esClient, long timeLimitMillis) {
        ESNodesResponse nodesResponse = esClient.admin().cluster().prepareExecute(ESNodesAction.INSTANCE).
                addFlag(HTTP).execute().actionGet(timeLimitMillis);
        if (nodesResponse.getFailedNodes() > 0) {
            LOGGER.warn("class=MonitorClusterJob||method=getClusterNodeIds||collect node id has part of the failure, failed nodes:[{}]", nodesResponse.getFailedNodes());
        }
        return new ArrayList<>(nodesResponse.getNodes().keySet());
    }

    /**
     * ??????datanode?????????????????????stats
     * @param esClient
     * @return
     * @throws ESRunTimeException
     */
    private Map<String, ClusterNodeStats> getClusterNodeStatsConcurrently(ESClient esClient) throws ESRunTimeException{
        try {
            int batchSize = 5;
            long startTime = System.currentTimeMillis();
            long expectEndTime = startTime + CLIENT_TO_WITH_MILLS;

            // 1.??????????????????????????????ID
            List<String> clusterNodeIds = getClusterNodeIds(esClient, CLIENT_TO_WITH_MILLS);

            // 2.????????????
            List<List<String>> nodeIdBatches = Lists.partition(clusterNodeIds, batchSize);

            // 3.?????????????????????????????????
            for (List<String> nodeIdBatch : nodeIdBatches) {
                // ???????????????????????????????????????????????????
                nodeStatsFuture.callableTask(() -> {
                    ESNodesStatsResponse response = new ESNodesStatsResponse();
                    response.setNodes(new HashMap<>());
                    try {
                        if (System.currentTimeMillis() > expectEndTime) {
                            // ????????????????????????????????????
                            throw new ESRunTimeException(TIME_OUT);
                        }
                        response = esClient.admin().cluster().prepareExecute(ESNodesStatsAction.INSTANCE)
                                .setNodesIds(nodeIdBatch.toArray(new String[0]))
                                .level(IndicesStatsLevel.INDICES.getStr()).execute().actionGet(CLIENT_TO_WITH_MILLS);
                    } catch (Exception e) {
                        LOGGER.error("class=MonitorClusterJob||method=getClusterNodeStatsConcurrently||batch get node stats execute error", e);
                    }

                    return response;
                });
            }

            // 4.????????????????????????
            List<ESNodesStatsResponse> nodeStatsResponseList = nodeStatsFuture.waitResult();

            // 5.??????????????????
            Map<String, ClusterNodeStats> clusterNodeStatsMap = new HashMap<>();
            for (ESNodesStatsResponse nodesStatsResponse : nodeStatsResponseList) {
                clusterNodeStatsMap.putAll(nodesStatsResponse.getNodes());
            }

            // 6.?????????????????????,??????????????????
            int failedNode = clusterNodeIds.size() - clusterNodeStatsMap.size();
            if (failedNode != 0) {
                LOGGER.warn("class=MonitorClusterJob||method=getClusterNodeStatsConcurrently||batch get node stats has part of the failure, failed nodes:[{}]", failedNode);
            }

            return clusterNodeStatsMap;
        } catch (Exception e) {
            LOGGER.error("class=MonitorClusterJob||method=getClusterNodeStatsConcurrently||getClusterNodeStats for cluster {} error, e->", esClient.getClusterName(), e);
            throw new ESRunTimeException(TIME_OUT, e.getCause());
        }
    }

    private Map<String, ClusterNodeStats> getClusterNodeStats(ESClient esClient) {
        boolean isConcurrentCollect = ariusConfigInfoService.booleanSetting(CONFIG_VALUE_GROUP,
                CONFIG_NAME_NODESTAT_COLLECT_CONCURRENT, false);

        Map<String, ClusterNodeStats> clusterNodeStatsMap;
        if (!isConcurrentCollect) {
            // ??????1???????????????????????????????????????
            nodeStopWatch.stop().start("stats_node_once");
            clusterNodeStatsMap = getNodeStatsByOnce(esClient);
        } else {
            // ??????2?????????????????????stats?????????key-??????ID???value-??????stats
            nodeStopWatch.stop().start("stats_node_concurrent");
            clusterNodeStatsMap = getClusterNodeStatsConcurrently(esClient);
        }

        LOGGER.info("class=MonitorClusterJob||method=getClusterNodeStats||clusterName={}||clusterNodeStatsMapSize={}",
                clusterName, clusterNodeStatsMap.size());

        return clusterNodeStatsMap;
    }

    /**
     * ??????????????????
     * @param esClient
     * @param metricsRegister
     * @param ariusClusterName ???????????????????????????
     */
    private void collectNodeData(String ariusClusterName, ESClient esClient, MetricsRegister metricsRegister){
        long timestamp = CommonUtils.monitorTimestamp2min(System.currentTimeMillis());

        try {
            nodeStopWatch.start("stats_node_begin");
            Map<String, ClusterNodeStats> clusterNodeStatsMap = getClusterNodeStats(esClient);

            List<ESNodeStats> esNodeStatsList = new CopyOnWriteArrayList<>();

            nodeStopWatch.stop().start(COMPUTE);
            clusterNodeStatsMap.entrySet().parallelStream().forEach( entry -> {
                String nodeId = entry.getKey();
                try {
                    ClusterNodeStats clusterNodeStats = entry.getValue();
                    NodeAttributes   attributes       = clusterNodeStats.getAttributes();

                    ESDataTempBean base = new ESDataTempBean();
                    base.setDimension(ESDataTempBean.NODE_TYPE );
                    base.setCluster(ariusClusterName);
                    base.setTimestamp(timestamp);
                    base.setNode(clusterNodeStats.getName());
                    base.setIp(HttpHostUtil.getIpFromTransportAddress(clusterNodeStats.getTransportAddress()));
                    base.setPort(HttpHostUtil.getPortFromTransportAddress(clusterNodeStats.getTransportAddress()));
                    base.setRack(null == attributes ? "" : attributes.getRack());

                    clusterNodeIps.add(base.getIp());

                    Map map = JSON.parseObject(JSON.toJSONString(clusterNodeStats), Map.class);
                    // ??????nodeWorkOrders???????????????????????????????????????ESDataTempBean?????????esDataTempBeans???size??????nodeWorkOrders???size??????????????????
                    List<ESDataTempBean> esDataTempBeans = aggrAndComputeData(map, nodeWorkOrders, base, metricsRegister);

                    // ????????????
                    ESNodeStats esNodeStats = buildESNodeStats(base, esDataTempBeans);

                    esNodeStatsList.add(esNodeStats);
                    // ??????id??????????????????map
                    nodeIdEsNodeStatsMap.put(nodeId, esNodeStats);

                    //??????????????????????????????
                    SpringTool.publish(new MetricsMonitorNodeEvent(this, esDataTempBeans, esNodeStatsList, clusterPhy.getLevel(), HOST_NAME));
                } catch (Exception e) {
                    LOGGER.error("class=MonitorClusterJob||method=collectNodeData||nodeId={}||clusterName={}||msg=exception",
                            nodeId, clusterName, e);
                }
            } );

            monitorMetricsSender.sendNodeInfo(esNodeStatsList);
        } catch (Exception e) {
            LOGGER.error("class=MonitorClusterJob||method=collectNodeData||clusterName={}||msg=exception", clusterName, e);
        }

        //???????????????cache??????????????????es?????????????????????????????????
        SpringTool.publish(new MetricsMonitorCollectTimeEvent(this, "node",
                (double) System.currentTimeMillis() - timestamp, clusterName, clusterPhy.getLevel(), HOST_NAME));

        if (nodeStopWatch.isRunning()) {
            nodeStopWatch.stop();
        }
    }

    // ????????????????????????stat-????????????????????????
    private Map<String, IndexNodes> getIndexStatsByOnce(ESClient esClient) {
        ESIndexStatsResponse response = esClient.admin().indices().prepareExecute(ESIndexStatsAction.INSTANCE)
                .setLevel(IndicesStatsLevel.SHARDS).execute().actionGet(CLIENT_TO_WITH_MILLS);
        return response.getIndicesMap();
    }

    /**
     * ?????????????????????????????????
     * @param esClient esClient
     */
    private List<String> getClusterOpenIndexNames(ESClient esClient) {
        ESIndicesCatIndicesResponse esIndicesCatIndicesResponse = esClient.admin().indices().prepareCatIndices().execute().actionGet(CLIENT_TO_WITH_MILLS);
        return esIndicesCatIndicesResponse.getCatIndexResults().stream().filter(
                catIndexResult -> catIndexResult.getStatus().equalsIgnoreCase("open")).map( CatIndexResult::getIndex).collect( Collectors.toList());
    }

    /**
     * ??????????????????????????????
     * @param esClient
     * @return
     */
    private Map<String, IndexNodes> getIndexStatsConcurrently(ESClient esClient){
        try {
            int  batchSize = 30;
            long startTime = System.currentTimeMillis();
            long expectEndTime = startTime + CLIENT_TO_WITH_MILLS;

            // ????????????????????????????????????
            List<String> indexNames = getClusterOpenIndexNames(esClient);
            // ????????????
            List<List<String>> indexNameBatches = Lists.partition(indexNames, batchSize);

            // ?????????????????????????????????
            for (List<String> indexNameBatch : indexNameBatches) {
                // ???????????????????????????????????????????????????
                indexStatsFuture.callableTask(()  -> {
                    ESIndexStatsResponse response = new ESIndexStatsResponse();
                    response.setIndicesMap(new HashMap<>());
                    try {
                        if (System.currentTimeMillis() > expectEndTime) {
                            // ????????????????????????????????????
                            throw new ESRunTimeException(TIME_OUT);
                        }

                        response = esClient.admin().indices().prepareExecute( ESIndexStatsAction.INSTANCE)
                                .setIndices(indexNameBatch.toArray(new String[0]))
                                .setLevel(IndicesStatsLevel.SHARDS).execute().actionGet(CLIENT_TO_WITH_MILLS);
                    } catch (Exception e) {
                        LOGGER.error("class=MonitorClusterJob||method=getIndexStatsConcurrently||batch get index stats execute error", e);
                    }

                    return response;
                });
            }

            // ????????????????????????
            List<ESIndexStatsResponse> indicesStatsResponseList = indexStatsFuture.waitResult();

            // ??????????????????
            int shardFailedNum = 0;
            Map<String, IndexNodes> indexStatsMap = new HashMap<>();
            for (ESIndexStatsResponse indexStatsResponse : indicesStatsResponseList) {
                if (null != indexStatsResponse.getShards()) {
                    shardFailedNum += indexStatsResponse.getShards().getFailedShard();
                }
                indexStatsMap.putAll(indexStatsResponse.getIndicesMap());
            }

            //??????shard????????????
            if (shardFailedNum > 0) {
                LOGGER.warn("class=MonitorClusterJob||method=getIndexStatsConcurrently||batch get index stats has part of the failure, failed shards:[{}]", shardFailedNum);
            }

            return indexStatsMap;

        } catch (Exception e) {
            LOGGER.error("class=MonitorClusterJob||method=getIndexStatsConcurrently||getClusterNodeStats for cluster {} error, e->", esClient.getClusterName(), e);
            throw e;
        }
    }

    private Map<String, IndexNodes> getIndexStats(ESClient esClient){
        boolean isConcurrentCollect = ariusConfigInfoService.booleanSetting(CONFIG_VALUE_GROUP,
                CONFIG_NAME_INDEXSTAT_COLLECT_CONCURRENT, false);

        // key-????????????value-??????stats
        Map<String, IndexNodes> indexStatsMap;
        if(isConcurrentCollect){
            indexStopWatch.stop().start("stats_index_concurrent");
            indexStatsMap = getIndexStatsConcurrently(esClient);
        }else {
            indexStopWatch.stop().start("stats_index_once");
            indexStatsMap = getIndexStatsByOnce(esClient);
        }

        LOGGER.info("class=MonitorClusterJob||method=getIndexStats||clusterName={}||clusterNodeStatsMapSize={}",
                clusterName, indexStatsMap.size());

        return indexStatsMap;
    }

    /**
     * ??????????????????
     * @param esClient
     * @param metricsRegister
     */
    private void collectIndexData(String ariusClusterName, ESClient esClient, MetricsRegister metricsRegister) {
        long timestamp = CommonUtils.monitorTimestamp2min(System.currentTimeMillis());

        try {
            indexStopWatch.start("stats_index_begin");
            Map<String, IndexNodes> indexStatsMap = getIndexStats(esClient);

            // ?????????type?????? ???????????????????????????????????????????????????????????????type?????????????????????????????????
            Map<String/*source templateName*/, List<ESIndexStats>/*dest index stats*/> destTemplateIndexStatsMap = Maps.newHashMap();

            // ?????????type?????? ??????????????????????????????
            Map<String/*source templateName*/, ESIndexStats/*source index stats*/> sourceTemplateSampleIndexStatsMap = Maps.newHashMap();

            List<ESIndexStats> esIndexStatsList = new CopyOnWriteArrayList<>();

            indexStopWatch.stop().start(COMPUTE);
            indexStatsMap.entrySet().parallelStream().forEach( entry -> {
                String index = entry.getKey();
                try {

                    IndexTemplatePhyWithLogic indexTemplate = getTemplateNameForCache(ariusClusterName, index);
                    IndexNodes indexStats = entry.getValue();

                    ESDataTempBean base = new ESDataTempBean();
                    base.setDimension(ESDataTempBean.INDEX_TYPE );
                    base.setCluster(ariusClusterName);
                    base.setTimestamp(timestamp);
                    base.setIndex(index);

                    //??????????????????????????????, ??????????????????Arius??????????????????
                    buildForOriginalESIndicesInfo(base, indexTemplate);

                    base.setShardNu(null == indexStats.getShards() ? 0 : indexStats.getShards().size());

                    Map map = JSON.parseObject(JSON.toJSONString(indexStats.getPrimaries()), Map.class);
                    List<ESDataTempBean> esDataTempBeans = aggrAndComputeData(map, indexWorkOrders, base, metricsRegister);

                    esDataTempBeans.addAll(genIndexTotalCommonStatsMetric(indexStats.getTotal().getStore().getSizeInBytes(),
                        indexStats.getTotal().getDocs().getCount(), base));

                    ESIndexStats esIndexStats = buildESIndexStats(base, esDataTempBeans);

                    // ??????????????????type?????????????????????type??????
                    if (Objects.nonNull(mulityTypeTemplatesInfo) && null != indexTemplate) {

                        // ???????????????????????????????????????type???????????????????????????
                        if (MapUtils.isNotEmpty(mulityTypeTemplatesInfo.getDest2SourceTemplateMap()) &&
                            mulityTypeTemplatesInfo.getDest2SourceTemplateMap().containsKey(indexTemplate.getName())) {
                            String sourceTemplateName = mulityTypeTemplatesInfo.getDest2SourceTemplateMap().get(indexTemplate.getName());

                            ESIndexStats esIndexStatsCopy = new ESIndexStats();
                            BeanUtils.copyProperties(esIndexStats, esIndexStatsCopy);
                            Map<String, String> metricsCopy = Maps.newHashMap();
                            metricsCopy.putAll(esIndexStats.getMetrics());
                            esIndexStatsCopy.setMetrics(metricsCopy);

                            destTemplateIndexStatsMap.computeIfAbsent(sourceTemplateName, key -> Lists.newArrayList()).add(esIndexStatsCopy);
                        }

                        // ???????????????????????????????????????
                        if (MapUtils.isNotEmpty(mulityTypeTemplatesInfo.getSource2DestTemplateMap()) &&
                            mulityTypeTemplatesInfo.getSource2DestTemplateMap().containsKey(indexTemplate.getName())) {
                            // ??????????????????????????????????????????????????????????????????
                            sourceTemplateSampleIndexStatsMap.put(indexTemplate.getName(), esIndexStats);
                            return;
                        }
                    }

                    esIndexStatsList.add(esIndexStats);

                    // ?????????????????????????????????
                    //achieveIndexToNodeInfo(base, indexStats);

                    // ???????????????????????????
                    SpringTool.publish(new MetricsMonitorIndexEvent(this, esDataTempBeans, esIndexStatsList, clusterPhy.getLevel(), HOST_NAME));
                } catch (Exception e) {
                    LOGGER.error("class=MonitorClusterJob||method=collectIndexData||index={}||clusterName={}||msg=exception",
                        index, clusterName, e);
                }
            } );

            addSourceTemplateIndexStats(destTemplateIndexStatsMap, sourceTemplateSampleIndexStatsMap, esIndexStatsList);

            // ????????????????????????ES???
            monitorMetricsSender.sendIndexInfo(esIndexStatsList);
        } catch (Exception e) {
            LOGGER.error("class=MonitorClusterJob||method=collectIndexData||clusterName={}||msg=exception", clusterName, e);
        }

        //???????????????cache??????????????????es?????????????????????????????????
        SpringTool.publish(new MetricsMonitorCollectTimeEvent(this, "index",
                (double) System.currentTimeMillis() - timestamp, clusterName, clusterPhy.getLevel(), HOST_NAME));

        if (indexStopWatch.isRunning()) {
            indexStopWatch.stop();
        }
    }

    private void buildForOriginalESIndicesInfo(ESDataTempBean base, IndexTemplatePhyWithLogic indexTemplate) {
        if (null != indexTemplate && null != indexTemplate.getName()) {
            base.setTemplate(indexTemplate.getName());
        }

        if (null != indexTemplate && null != indexTemplate.getId()) {
            base.setTemplateId(indexTemplate.getId());
        }

        if (null != indexTemplate && null != indexTemplate.getLogicId()) {
            base.setLogicTemplateId(indexTemplate.getLogicId());
        }
    }

    /**
     * ??????????????????????????????
     */
    private void collectIndexToNodeData() {
        try {
            index2NodeStopWatch.start("index_node");
            List<ESIndexToNodeStats> esIndexToNodeStatsList = new ArrayList<>();
            for (ESIndexToNodeTempBean temp : indexToNodeTemps) {

                for (String nodeId : temp.getNodes()) {
                    ESNodeStats esNodeStats = nodeIdEsNodeStatsMap.get(nodeId);
                    esIndexToNodeStatsList.add(build(temp, esNodeStats, indexToNodeWorkOrders));
                }
            }

            monitorMetricsSender.sendIndexToNodeStats(esIndexToNodeStatsList);
        } catch (Exception e) {
            LOGGER.error("class=MonitorClusterJob||method=collectIndexToNodeData||clusterName={}||msg=exception", clusterName, e);
        }
        if (index2NodeStopWatch.isRunning()) {
            index2NodeStopWatch.stop();
        }
    }

    /**
     * ??????DCDR????????????
     * @param esClient
     * @param metricsRegister
     */
    private void collectDCDRData(ESClient esClient, MetricsRegister metricsRegister) {
        try {
            dcdrStopWatch.start("dcdr_begin");

            ESGetDCDRStatsResponse response = esClient.admin().indices().prepareGetDCDRStats().execute().actionGet(CLIENT_TO_WITH_MILLS);
            long timestamp = System.currentTimeMillis();
            String cluster = esClient.getClusterName();

            dcdrStopWatch.stop().start(COMPUTE);
            List<ESIndexDCDRStats> esIndexDCDRStatsList = new ArrayList<>();
            response.getIndicesStats().forEach((index, indexStats) -> {
                try {

                    IndexTemplatePhyWithLogic indexTemplate = getTemplateNameForCache(cluster, index);
                    if (null == indexTemplate) {
                        return;
                    }

                    ESDataTempBean base = new ESDataTempBean();
                    base.setDimension(ESDataTempBean.INDEX_TYPE );
                    base.setCluster(cluster);
                    base.setTimestamp(timestamp);
                    base.setIndex(index);
                    base.setTemplate(indexTemplate.getName());
                    base.setTemplateId(indexTemplate.getId());
                    base.setLogicTemplateId(indexTemplate.getLogicId());
                    base.setShardNu(indexStats.getDcdrStats().size());

                    Map<String, DCDRMetrics> indexStatsByCluster = aggrAndComputeDCDRIndexData(indexStats);
                    indexStatsByCluster.forEach((replicaCluster, dcdrMetrics) -> {
                        Map map = (Map) JSON.toJSON(dcdrMetrics);
                        List<ESDataTempBean> esDataTempBeans = aggrAndComputeData(map, dcdrWorkOrders, base, metricsRegister);
                        ESIndexDCDRStats esIndexDCDRStats = buildESIndexDCDRStats(base, replicaCluster, esDataTempBeans);
                        esIndexDCDRStatsList.add(esIndexDCDRStats);

                        //??????dcdr?????????????????????
                    });
                } catch (Exception e) {
                    LOGGER.error("class=MonitorClusterJob||method=collectDcdrData||index={}||clusterName={}||msg=exception",
                            index, clusterName, e);
                }
            });

            monitorMetricsSender.sendDCDRStats(esIndexDCDRStatsList);
        } catch (Exception e) {
            LOGGER.error("class=MonitorClusterJob||method=collectDcdrData||clusterName={}||msg=dcdr_exception", clusterName, e);
        }

        if (dcdrStopWatch.isRunning()) {
            dcdrStopWatch.stop();
        }
    }

    private Map<String, DCDRMetrics> aggrAndComputeDCDRIndexData(DCDRIndexStats indexStats) {
        Map<String, DCDRMetrics> indexStatsByCluster = new HashMap<>();
        indexStats.getDcdrStats().forEach((shardId, statsList) ->
            statsList.forEach(dcdrStats -> setIndexDCDRMetrics(indexStatsByCluster, dcdrStats))
        );

        return indexStatsByCluster;
    }

    private void setIndexDCDRMetrics(Map<String, DCDRMetrics> indexStatsByCluster, DCDRStats dcdrStats) {
        DCDRMetrics indexDCDRMetrics;
        if (indexStatsByCluster.containsKey(dcdrStats.getReplicaCluster())) {
            indexDCDRMetrics = indexStatsByCluster.get(dcdrStats.getReplicaCluster());
        } else {
            indexDCDRMetrics = new DCDRMetrics();
            indexStatsByCluster.put(dcdrStats.getReplicaCluster(), indexDCDRMetrics);
        }

        if (dcdrStats.getReplicaGlobalCheckpoint() >= 0) {
            indexDCDRMetrics.setGlobalCheckpointDelay(indexDCDRMetrics.getGlobalCheckpointDelay()
                    + dcdrStats.getPrimaryGlobalCheckpoint() - dcdrStats.getReplicaGlobalCheckpoint());
        }
        if (dcdrStats.getReplicaMaxSeqNo() >= 0) {
            indexDCDRMetrics.setMaxSeqNoDelay(indexDCDRMetrics.getMaxSeqNoDelay()
                    + dcdrStats.getPrimaryMaxSeqNo() - dcdrStats.getReplicaMaxSeqNo());
        }
        if (dcdrStats.getAvailableSendBulkNumber() < indexDCDRMetrics.getMinAvailableSendBulkNumber()) {
            indexDCDRMetrics.setMinAvailableSendBulkNumber(dcdrStats.getAvailableSendBulkNumber());
        }
        indexDCDRMetrics.setTotalSendTimeMillis(indexDCDRMetrics.getTotalSendTimeMillis()
                + dcdrStats.getTotalSendTimeMillis());
        indexDCDRMetrics.setTotalSendRequests(indexDCDRMetrics.getTotalSendRequests()
                + dcdrStats.getSuccessfulSendRequests() + dcdrStats.getFailedSendRequests());
        indexDCDRMetrics.setFailedSendRequests(indexDCDRMetrics.getFailedRecoverCount()
                + dcdrStats.getFailedSendRequests());
        indexDCDRMetrics.setOperationsSend(indexDCDRMetrics.getOperationsSend()
                + dcdrStats.getOperationsSends());
        indexDCDRMetrics.setBytesSend(indexDCDRMetrics.getBytesSend()
                + dcdrStats.getBytesSend());
        if (dcdrStats.getTimeSinceLastSendMillis() < indexDCDRMetrics.getMinTimeSinceLastSendMillis()) {
            indexDCDRMetrics.setMinTimeSinceLastSendMillis(dcdrStats.getTimeSinceLastSendMillis());
        }
        if (dcdrStats.getTimeSinceLastSendMillis() > indexDCDRMetrics.getMaxTimeSinceLastSendMillis()
                && dcdrStats.getReplicaGlobalCheckpoint() >= 0
                && dcdrStats.getPrimaryGlobalCheckpoint() != dcdrStats.getReplicaGlobalCheckpoint()) {
            indexDCDRMetrics.setMaxTimeSinceLastSendMillis(dcdrStats.getTimeSinceLastSendMillis());
        }
        if (dcdrStats.getTimeSinceUpdateReplicaCheckPoint() > indexDCDRMetrics.getMaxTimeSinceLastSendMillis()
                && dcdrStats.getReplicaGlobalCheckpoint() >= 0
                && dcdrStats.getPrimaryGlobalCheckpoint() != dcdrStats.getReplicaGlobalCheckpoint()) {
            indexDCDRMetrics.setMaxTimeSinceUpdateReplicaCheckPoint(dcdrStats.getTimeSinceUpdateReplicaCheckPoint());
        }

        indexDCDRMetrics.setSuccessRecoverCount(indexDCDRMetrics.getSuccessRecoverCount()
                + dcdrStats.getSuccessRecoverCount());
        indexDCDRMetrics.setFailedRecoverCount(indexDCDRMetrics.getFailedRecoverCount()
                + dcdrStats.getFailedRecoverCount());

        int inSyncSize;
        if (dcdrStats.getInSyncOffset().size() == 1 && dcdrStats.getInSyncOffset().get(0) != null) {
            inSyncSize = dcdrStats.getInSyncOffset().get(0).size();
        } else {
            inSyncSize = dcdrStats.getInSyncOffset().size();
        }
        indexDCDRMetrics.setInSyncTranslogOffsetSize(indexDCDRMetrics.getInSyncTranslogOffsetSize()
                + inSyncSize);
        indexDCDRMetrics.setRecoverTotalTimeMillis(indexDCDRMetrics.getRecoverTotalTimeMillis()
                + dcdrStats.getRecoverTotalTimeMillis());
    }

    private ESIndexDCDRStats buildESIndexDCDRStats(ESDataTempBean bean, String replicaCluster, List<ESDataTempBean> metricsList) {
        ESIndexDCDRStats esIndexDCDRStats = new ESIndexDCDRStats();
        esIndexDCDRStats.setTimestamp(bean.getTimestamp());
        esIndexDCDRStats.setCluster(bean.getCluster());
        esIndexDCDRStats.setReplicaCluster(replicaCluster);
        esIndexDCDRStats.setTemplate(bean.getTemplate());
        esIndexDCDRStats.setTemplateId(bean.getTemplateId());
        esIndexDCDRStats.setLogicTemplateId(bean.getLogicTemplateId());
        esIndexDCDRStats.setIndex(bean.getIndex());
        esIndexDCDRStats.setShardNu(bean.getShardNu());
        esIndexDCDRStats.setMetrics(Maps.newHashMap());

        for (ESDataTempBean esDataTempBean : metricsList) {
            if (StringUtils.isEmpty(esDataTempBean.getComputeValue()) || "null".equals(esDataTempBean.getComputeValue())
                    || "Infinity".equals(esDataTempBean.getComputeValue()) || "NaN".equals(esDataTempBean.getComputeValue())) {
                continue;
            }
            esIndexDCDRStats.putMetrics(esDataTempBean.getValueName().substring(ES_INDICES.length()).replace(".", "-"),
                    esDataTempBean.getComputeValue());
        }

        return esIndexDCDRStats;
    }


    private void achieveIndexToNodeInfo(ESDataTempBean base, IndexNodes indexStats) {
        try {
            ESIndexToNodeTempBean temp = new ESIndexToNodeTempBean();
            temp.setTimestamp(base.getTimestamp());
            temp.setCluster(base.getCluster());
            temp.setTemplate(base.getTemplate());
            temp.setTemplateId(base.getTemplateId());
            temp.setLogicTemplateId(base.getLogicTemplateId());
            temp.setIndex(base.getIndex());

            Map<String, List<CommonStat>> shards = indexStats.getShards();

            Set<String> nodeIds = Sets.newHashSet();
            for (Map.Entry<String, List<CommonStat>> entry : shards.entrySet()) {
                for (CommonStat stat : entry.getValue()) {
                    nodeIds.add(stat.getRouting().getNode());
                }
            }

            temp.setNodes(nodeIds);
            indexToNodeTemps.add(temp);
        } catch (Exception e) {
            LOGGER.error("class=MonitorClusterJob||method=achieveIndexToNodeInfo||clusterName={}||msg=exception", clusterName, e);
        }
    }

    private void achieveAndSendNodeToIndexInfo(ESDataTempBean node, Map map, MetricsRegister metricsRegister) {
        try {
            Map indicesTotalMap = (Map) map.get("indices");
            Map indicesMap      = (Map) indicesTotalMap.get("indices");

            List<ESNodeToIndexStats> esNodeToIndexStatsList  = new ArrayList<>();
            Map<String, List<ESNodeToIndexTempBean>> dataBeanMap = collectNodeToIndexData(node, indicesMap, metricsRegister);
            for (List<ESNodeToIndexTempBean> beanList : dataBeanMap.values()) {
                esNodeToIndexStatsList.add(buildEsNodeToIndexStats(beanList.get(0), beanList, node.getTimestamp()));
            }

            monitorMetricsSender.sendESNodeToIndexStats(esNodeToIndexStatsList);
        } catch (Exception e) {
            LOGGER.error("class=MonitorClusterJob||method=achieveAndSendNodeToIndexInfo||clusterName={}||msg=exception", clusterName, e);
        }
    }

    private void achieveAndSendIngestInfo(ESDataTempBean node, Map map, MetricsRegister metricsRegister) {
        try {
            Map ingest = (Map) map.get("ingest");
            if (ingest == null) {
                return ;
            }

            Map pipelines = (Map) ingest.get("pipelines");
            if (pipelines == null) {
                return ;
            }

            List<ESIngestStats> esIngestStatsList  = new ArrayList<>();
            Map<String, List<ESNodeToIndexTempBean>> dataBeanMap = collectIngestData(node, pipelines, metricsRegister);
            for (List<ESNodeToIndexTempBean> beanList : dataBeanMap.values()) {
                esIngestStatsList.add(buildESIngestStats(beanList.get(0), beanList, node.getTimestamp()));
            }

            monitorMetricsSender.sendIngestStats(esIngestStatsList);
        } catch (Exception e) {
            LOGGER.error("class=MonitorClusterJob||method=achieveAndSendIngestInfo||clusterName={}||msg=exception", clusterName, e);
        }
    }

    /**
     * ??????ingest??????
     * @param node ??????
     * @param pipelines ingest pipelines??????
     * @param metricsRegister
     * @return
     */
    private Map<String, List<ESNodeToIndexTempBean>> collectIngestData(ESDataTempBean node, Map pipelines, MetricsRegister metricsRegister) {
        Map<String, List<ESNodeToIndexTempBean>> pipelinesStats = Maps.newHashMap();
        for (Object entry : pipelines.entrySet()) {
            Map.Entry ingestEntry = (Map.Entry) entry;
            String template = (String) ingestEntry.getKey();

            if (template.startsWith(".")) {
                continue ;
            }

            IndexTemplatePhyWithLogic indexTemplate = getIndexTemplateByTemplateName(node.getCluster(), template);
            if (null == indexTemplate) {
                continue ;
            }

            Map pipelineStats = (Map) ingestEntry.getValue();

            ESNodeToIndexTempBean base = new ESNodeToIndexTempBean();
            base.setTimestamp(node.getTimestamp());
            base.setCluster(node.getCluster());
            base.setNode(node.getNode());
            base.setPort(node.getPort());
            base.setIndex(template);
            base.setTemplate(indexTemplate.getName());
            base.setTemplateId(indexTemplate.getId());
            base.setLogicTemplateId(indexTemplate.getLogicId());

            List<ESNodeToIndexTempBean> dataBeans = aggrAndComputeNodeToIndexData(pipelineStats, ingestWorkOrders, base, metricsRegister);
            pipelinesStats.put(template, dataBeans);
        }

        return pipelinesStats;
    }

    /**
     * ???????????????????????????????????????
     */
    private Map<String, List<ESNodeToIndexTempBean>> collectNodeToIndexData(ESDataTempBean node, Map indexStatMap, MetricsRegister metricsRegister) {
        Map<String, List<ESNodeToIndexTempBean>> indexStats = Maps.newHashMap();
        for (Object entry : indexStatMap.entrySet()) {
            Map.Entry indexStatEntry = (Map.Entry) entry;
            String indexName = (String) indexStatEntry.getKey();

            IndexTemplatePhyWithLogic indexTemplate = getTemplateNameForCache(node.getCluster(), indexName);
            if(null == indexTemplate){continue;}

            Map indexStat = (Map) indexStatEntry.getValue();

            ESNodeToIndexTempBean base = new ESNodeToIndexTempBean();
            base.setTimestamp(node.getTimestamp());
            base.setCluster(node.getCluster());
            base.setNode(node.getNode());
            base.setPort(node.getPort());
            base.setIndex(indexName);
            base.setTemplate(indexTemplate.getName());
            base.setTemplateId(indexTemplate.getId());
            base.setLogicTemplateId(indexTemplate.getLogicId());

            List<ESNodeToIndexTempBean> dataBeans = aggrAndComputeNodeToIndexData(indexStat, nodeToIndexWorkOrders, base, metricsRegister);
            indexStats.put(indexName, dataBeans);
        }
        return indexStats;
    }

    private List<ESNodeToIndexTempBean> aggrAndComputeNodeToIndexData(Map indexStat,
                                                                      List<CollectMetrics> workOrders,
                                                                      ESNodeToIndexTempBean base,
                                                                      MetricsRegister metricsRegister) {
        List<ESNodeToIndexTempBean> result = Lists.newArrayList();
        for (CollectMetrics workOrder : workOrders) {
            try {
                String valueName  = workOrder.getValueName();
                String valueRoute = workOrder.getValueRoute();
                Double value      = MonitorUtil.obj2Double( MonitorUtil.getValueByRoute(indexStat, valueRoute));

                ESNodeToIndexTempBean dataBean = new ESNodeToIndexTempBean();
                BeanUtils.copyProperties(base, dataBean);
                dataBean.setValueName(valueName);
                dataBean.setValue(value);
                dataBean.setDeriveParam(workOrder.getDeriveParam());
                dataBean.setTimestamp(base.getTimestamp());

                ESNodeToIndexComputer computer = new ESNodeToIndexComputer(workOrder.getComputeType(), metricsRegister);
                String computerValue = computer.compute(dataBean);

                dataBean.setComputeValue(computerValue);

                result.add(dataBean);
            }catch (Exception e){
                LOGGER.error("class=MonitorClusterJob||method=aggrAndComputeNodeToIndexData||clusterName={}||msg=exception", clusterName, e);
            }
        }
        return result;
    }

    /**
     * ???map???????????????????????????????????????
     * workOrders???????????????????????????workOrders????????????????????????
     * @param base
     */
    private List<ESDataTempBean> aggrAndComputeData(Map map, List<CollectMetrics> workOrders,
                                                    ESDataTempBean base, MetricsRegister metricsRegister) {
        List<ESDataTempBean> result = Lists.newArrayList();
        for (CollectMetrics workOrder : workOrders) {
            try {
                String valueName  = workOrder.getValueName();
                String valueRoute = workOrder.getValueRoute();
                Double value      = MonitorUtil.obj2Double( MonitorUtil.getValueByRoute(map, valueRoute));

                ESDataTempBean esDataTempBean = new ESDataTempBean();
                BeanUtils.copyProperties(base, esDataTempBean);
                esDataTempBean.setValueName(valueName);
                esDataTempBean.setValue(value);
                esDataTempBean.setDeriveParam(workOrder.getDeriveParam());
                esDataTempBean.setSendToN9e(workOrder.isSendToN9e());
                esDataTempBean.setComputeValue(workOrder.getComputeType().getComputer(metricsRegister).compute(esDataTempBean));

                result.add(esDataTempBean);
            }catch (Exception e){
                LOGGER.error("class=MonitorClusterJob||method=aggrAndComputeData||clusterName={}||msg=exception", clusterName, e);
            }
        }
        return result;
    }

    private ESIndexToNodeStats build(ESIndexToNodeTempBean temp, ESNodeStats esNodeStats,
                                     List<CollectMetrics> indexToNodeWorkOrders) {
        Set<String> metricsNameSet = Sets.newHashSet();
        for (CollectMetrics workOrder : indexToNodeWorkOrders) {
            metricsNameSet.add(workOrder.getValueName().substring(ES_NODE.length()).replace(".", "-"));
        }

        ESIndexToNodeStats indexToNodeStats = new ESIndexToNodeStats();
        BeanUtils.copyProperties(temp, indexToNodeStats);
        Map<String, String> metrics = Maps.newHashMap();

        if (esNodeStats != null) {
            indexToNodeStats.setNode(esNodeStats.getNode());
            indexToNodeStats.setPort(esNodeStats.getPort());
            indexToNodeStats.setRack(esNodeStats.getRack());

            Map<String, String> metricsAll = esNodeStats.getMetrics();

            for (Map.Entry<String, String> entry : metricsAll.entrySet()) {
                if (StringUtils.isEmpty(entry.getValue()) || "null".equals(entry.getValue())
                        || "Infinity".equals(entry.getValue()) || "NaN".equals(entry.getValue())) {
                    continue;
                }
                String key = entry.getKey();
                if (metricsNameSet.contains(key)) {
                    metrics.put(key, entry.getValue());
                }
            }
        }
        indexToNodeStats.setMetrics(metrics);

        return indexToNodeStats;
    }

    private ESNodeStats buildESNodeStats(ESDataTempBean bean, List<ESDataTempBean> metricsList) {
        ESNodeStats esNodeStats = new ESNodeStats();
        esNodeStats.setTimestamp(bean.getTimestamp());
        esNodeStats.setCluster(bean.getCluster());
        esNodeStats.setNode(bean.getNode());
        esNodeStats.setIp(bean.getIp());
        esNodeStats.setPort(bean.getPort());
        esNodeStats.setMetrics(Maps.newHashMap());
        esNodeStats.setRack(bean.getRack());

        for (ESDataTempBean esDataTempBean : metricsList) {
            if (StringUtils.isEmpty(esDataTempBean.getComputeValue()) || "null".equals(esDataTempBean.getComputeValue())
                    || "Infinity".equals(esDataTempBean.getComputeValue()) || "NaN".equals(esDataTempBean.getComputeValue())) {
                continue;
            }

            esNodeStats.putMetrics(esDataTempBean.getValueName().substring(ES_NODE.length()).replace(".", "-"),
                    esDataTempBean.getComputeValue());
        }

        return esNodeStats;
    }

    private ESIndexStats buildESIndexStats(ESDataTempBean bean, List<ESDataTempBean> metricsList) {
        ESIndexStats esIndexStats = new ESIndexStats();
        esIndexStats.setTimestamp(bean.getTimestamp());
        esIndexStats.setCluster(bean.getCluster());
        esIndexStats.setTemplate(bean.getTemplate());
        esIndexStats.setTemplateId(bean.getTemplateId());
        esIndexStats.setLogicTemplateId(bean.getLogicTemplateId());
        esIndexStats.setIndex(bean.getIndex());
        esIndexStats.setShardNu(bean.getShardNu());
        esIndexStats.setMetrics(Maps.newHashMap());

        for (ESDataTempBean esDataTempBean : metricsList) {
            if (StringUtils.isEmpty(esDataTempBean.getComputeValue()) || "null".equals(esDataTempBean.getComputeValue())
                    || "Infinity".equals(esDataTempBean.getComputeValue()) || "NaN".equals(esDataTempBean.getComputeValue())) {
                continue;
            }

            esIndexStats.putMetrics(esDataTempBean.getValueName().substring(ES_INDICES.length()).replace(".", "-"),
                    esDataTempBean.getComputeValue());
        }

        esIndexStats.putMetrics("shardNu", String.valueOf(bean.getShardNu()));

        return esIndexStats;
    }

    private ESNodeToIndexStats buildEsNodeToIndexStats(ESNodeToIndexTempBean base, List<ESNodeToIndexTempBean> metricsList, long timestamp) {
        ESNodeToIndexStats esNodeToIndexStats = new ESNodeToIndexStats();
        esNodeToIndexStats.setCluster(base.getCluster());
        esNodeToIndexStats.setIndex(base.getIndex());
        esNodeToIndexStats.setNode(base.getNode());
        esNodeToIndexStats.setPort(base.getPort());
        esNodeToIndexStats.setTemplate(base.getTemplate());
        esNodeToIndexStats.setTemplateId(base.getTemplateId());
        esNodeToIndexStats.setLogicTemplateId(base.getLogicTemplateId());
        esNodeToIndexStats.setTimestamp(timestamp);
        for (ESNodeToIndexTempBean bean : metricsList) {
            if (StringUtils.isEmpty(bean.getComputeValue()) || "null".equals(bean.getComputeValue())
                    || "Infinity".equals(bean.getComputeValue()) || "NaN".equals(bean.getComputeValue())) {
                continue;
            }

            esNodeToIndexStats.putMetrics(bean.getValueName().substring("es.node.index.".length()).replace(".", "-"),
                    bean.getComputeValue());
        }

        return esNodeToIndexStats;
    }

    /**
     * ??????ingest?????????
     * @param base base??????
     * @param metricsList ????????????
     * @param timestamp ?????????
     * @return ESIngestStats??????
     */
    private ESIngestStats buildESIngestStats(ESNodeToIndexTempBean base, List<ESNodeToIndexTempBean> metricsList, long timestamp) {
        ESIngestStats ingestStats = new ESIngestStats();
        ingestStats.setCluster(base.getCluster());
        ingestStats.setNode(base.getNode());
        ingestStats.setPort(base.getPort());
        ingestStats.setTemplate(base.getTemplate());
        ingestStats.setTemplateId(base.getTemplateId());
        ingestStats.setLogicTemplateId(base.getLogicTemplateId());
        ingestStats.setTimestamp(timestamp);
        for (ESNodeToIndexTempBean bean : metricsList) {
            if (StringUtils.isEmpty(bean.getComputeValue()) || "null".equals(bean.getComputeValue())
                    || "Infinity".equals(bean.getComputeValue()) || "NaN".equals(bean.getComputeValue())) {
                continue;
            }

            ingestStats.putMetrics(bean.getValueName().substring(ES_NODE.length()).replace(".", "-"),
                    bean.getComputeValue());
        }

        return ingestStats;
    }

    /**
     * ??????????????????????????????
     * @param cluster
     * @param indexName
     * @return
     */
    private IndexTemplatePhyWithLogic getTemplateNameForCache(String cluster, String indexName) {
        try {
            return indexTemplateCache.get(cluster + "@" + indexName, () -> getTemplateName(cluster, indexName));
        } catch (Exception e) {
            if(EnvUtil.isTest()){
                LOGGER.warn("class=MonitorJobHandler||method=getTemplateNameForCache||cluster={}||indexName={}" +
                                "||msg=exception, indexName`s IndexTemplate is null!",
                        cluster, indexName);
            }
        }
        return null;
    }

    /**
     * ?????????????????????????????????IndexTemplate??????
     * @param cluster ????????????
     * @param templateName ????????????
     * @return IndexTemplate??????
     */
    private IndexTemplatePhyWithLogic getIndexTemplateByTemplateName(String cluster, String templateName) {
        for(IndexTemplatePhyWithLogic indexTemplate : indexTemplates) {
            String indexTemplateCluster = indexTemplate.getCluster();
            String name = indexTemplate.getName();

            if (indexTemplateCluster.equals(cluster) && name.equals(templateName)) {
                return indexTemplate;
            }
        }

        return null;
    }

    private IndexTemplatePhyWithLogic getTemplateName(String cluster, String indexName) {
        for(IndexTemplatePhyWithLogic indexTemplate : indexTemplates){
            String indexTemplateCluster      = indexTemplate.getCluster();
            String expression                = indexTemplate.getExpression();
            String expressionWhoutAsterisk  = "";

            if (expression.endsWith("*")) {
                expressionWhoutAsterisk = expression.substring(0, expression.length() - 1);
            } else {
                expressionWhoutAsterisk = expression;
            }

            if (indexName.startsWith(expressionWhoutAsterisk)) {
                IndexTemplatePhyWithLogic indexTemplate1 = getIndexTemplatePhyWithLogic(cluster, indexName, indexTemplate, indexTemplateCluster, expression);
                if (indexTemplate1 != null) {
                    return indexTemplate1;
                }
            }
        }

        return null;
    }

    private IndexTemplatePhyWithLogic getIndexTemplatePhyWithLogic(String cluster, String indexName,
                                                                   IndexTemplatePhyWithLogic indexTemplate,
                                                                   String clusterName, String expression) {
        String dataFormat   = indexTemplate.getLogicTemplate().getDateFormat();

        if(!expression.endsWith("*") || StringUtils.isEmpty(dataFormat) || "null".equals(dataFormat)
            && (indexName.equals(expression) && cluster.equals(clusterName))){
            return indexTemplate;
        }

        if(isMatch(indexName, expression, dataFormat) && cluster.equals(clusterName)){return indexTemplate;}

        //??????????????????
        for (String otherFormat : DATA_FORMATS) {
            if(otherFormat.equals(dataFormat)){continue;}
            if(isMatch(indexName, expression, otherFormat) && cluster.equals(clusterName)){return indexTemplate;}
        }
        return null;
    }

    private String genIndexNameClear(String indexName, String expression, String dateFormat) {
        Matcher m = pattern.matcher(indexName);

        if (!m.find() || StringUtils.isNotBlank(m.group(3))) {
            //??????????????????????????????
            if (indexName.length() != (expression.length() - 1 + dateFormat.length())) {
                return "";
            }
            return indexName;
        }

        String indexNameClear = m.group(1);
        if (indexNameClear.length() != (expression.length() - 1 + dateFormat.length())) {
            return "";
        }

        return indexNameClear;
    }

    private boolean isMatch(String indexName, String expression, String dataFormat) {
        String indexNameNoVersion = genIndexNameClear(indexName, expression, dataFormat);

        if (StringUtils.isNotBlank(indexNameNoVersion)) {
            String indexNameForTemplate = expression.replace("*", "") + dataFormat;
            if (indexNameNoVersion.length() == indexNameForTemplate.length()) {
                return true;
            }
        }

        return false;
    }

    private List<ESDataTempBean> genIndexTotalCommonStatsMetric(long totalSize, long totalDocCount, ESDataTempBean base) {
        ESDataTempBean totalSizeBean = new ESDataTempBean();
        BeanUtils.copyProperties(base, totalSizeBean);
        totalSizeBean.setValueName("es.indices.store.size_in_bytes.total");
        totalSizeBean.setValue((double)totalSize);
        totalSizeBean.setComputeValue(String.valueOf(totalSizeBean.getValue()));

        ESDataTempBean totalDocsBean = new ESDataTempBean();
        BeanUtils.copyProperties(base, totalDocsBean);
        totalDocsBean.setValueName("es.indices.docs.count.total");
        totalDocsBean.setValue((double)totalDocCount);
        totalDocsBean.setComputeValue(String.valueOf(totalDocsBean.getValue()));

        return Lists.newArrayList(totalSizeBean, totalDocsBean);
    }

    private boolean indexSkip(String indexName){
        return (indexName.startsWith(".monitoring")
                || indexName.startsWith(".marvel")
                || indexName.startsWith(".kibana"));
    }

    /**
     * ????????????????????????????????????
     *
     * @param destTemplateIndexStatsMap
     * @param sourceTemplateSampleIndexStatsMap
     * @param esIndexStatsList
     */
    private void addSourceTemplateIndexStats(Map<String, List<ESIndexStats>> destTemplateIndexStatsMap,
                                             Map<String, ESIndexStats> sourceTemplateSampleIndexStatsMap,
                                             List<ESIndexStats> esIndexStatsList) {
        try {
            // ?????????type?????? ???????????????????????????????????????????????????????????????type?????????????????????????????????
            for (Map.Entry<String/*source templateName*/, List<ESIndexStats>/*dest index stats*/> entry : destTemplateIndexStatsMap.entrySet()) {
                // ???????????????????????????????????????????????????
                Map<String/*source index name*/, ESIndexStats/*dest index merge stats*/> sourceIndexNameIndexStatsMap = Maps.newHashMap();
                // ???????????????????????????????????????
                Map<String/*source index name*/, AtomicInteger> sourceIndexNameCountMap = Maps.newHashMap();

                ESIndexStats destMergeIndexStats = null;
                ESIndexStats sourceIndexStats = null;
                for (ESIndexStats destIndexStats : entry.getValue()) {
                    // ????????????????????????????????????????????????????????????
                    String sourceIndexName = destIndexStats.getIndex().replaceFirst(destIndexStats.getTemplate(), entry.getKey());
                    destMergeIndexStats = sourceIndexNameIndexStatsMap.get(sourceIndexName);
                    if (Objects.isNull(destMergeIndexStats)) {
                        sourceIndexStats = sourceTemplateSampleIndexStatsMap.get(entry.getKey());
                        if (Objects.nonNull(sourceIndexStats)) {
                            destIndexStats.setLogicTemplateId(sourceIndexStats.getLogicTemplateId());
                            destIndexStats.setTemplate(sourceIndexStats.getTemplate());
                            destIndexStats.setTemplateId(sourceIndexStats.getTemplateId());
                            destIndexStats.setShardNu(sourceIndexStats.getShardNu());
                        }
                        destIndexStats.setIndex(sourceIndexName);
                        sourceIndexNameIndexStatsMap.put(sourceIndexName, destIndexStats);
                        sourceIndexNameCountMap.put(sourceIndexName, new AtomicInteger(1));
                        continue;
                    }
                    // ??????????????????
                    destMergeIndexStats = mergeIndexStats(destMergeIndexStats, destIndexStats);
                    sourceIndexNameIndexStatsMap.put(sourceIndexName, destMergeIndexStats);
                    sourceIndexNameCountMap.get(sourceIndexName).incrementAndGet();
                }

                // ???????????????????????????????????????????????????????????????????????????
                // ???????????????????????????
                handleAvgIndexStats(esIndexStatsList, sourceIndexNameIndexStatsMap, sourceIndexNameCountMap);
            }
        } catch (Exception e) {
            LOGGER.error("class=MonitorClusterJob||method=addSourceTemplateIndexStats||clusterName={}||msg=exception", clusterName, e);
        }
    }

    private void handleAvgIndexStats(List<ESIndexStats> esIndexStatsList, Map<String, ESIndexStats>
            sourceIndexNameIndexStatsMap, Map<String, AtomicInteger> sourceIndexNameCountMap) {
        for (Map.Entry<String/*source index name*/, ESIndexStats/*dest index merge stats*/> indexNameEntry : sourceIndexNameIndexStatsMap.entrySet()) {
            Iterator<Map.Entry<String/*metrics name*/, String/*metrics vale*/>> iterator = indexNameEntry.getValue().getMetrics().entrySet().iterator();
            Map.Entry<String/*metrics name*/, String/*metrics vale*/> metricsEntry = null;
            Integer count = sourceIndexNameCountMap.get(indexNameEntry.getKey()).get();

            while (iterator.hasNext()) {
                metricsEntry = iterator.next();
                if (metricsEntry.getKey().contains("avg")) {
                    Double sum = Double.valueOf(metricsEntry.getValue());
                    metricsEntry.setValue(String.valueOf(sum / count));
                }
            }
            esIndexStatsList.add(indexNameEntry.getValue());
        }
    }

    /**
     * ????????????????????????
     *
     * @param mergeIndexStats
     * @param indexStats
     * @return
     */
    private ESIndexStats mergeIndexStats(ESIndexStats mergeIndexStats, ESIndexStats indexStats) {
        String num1 = "";
        String num2 = "";
        String key = "";
        try {
            Iterator<Map.Entry<String/*metrics name*/, String/*metrics vale*/>> iterator = mergeIndexStats.getMetrics().entrySet().iterator();
            Map.Entry<String/*metrics name*/, String/*metrics vale*/> entry = null;
            while (iterator.hasNext()) {
                entry = iterator.next();
                key = entry.getKey();
                num1 = entry.getValue();
                num2 = indexStats.getMetrics().get(key);
                if (StringUtils.isBlank(num1)) {
                    num1 = "0";
                }
                if (StringUtils.isBlank(num2)) {
                    num2 = "0";
                }
                Double sum = Double.valueOf(num1) + Double.valueOf(num2);
                entry.setValue(String.valueOf(sum));
            }
        } catch (Exception e) {
            LOGGER.error("class=MonitorClusterJob||method=mergeIndexStats||clusterName={}||key={}||num1={}||num2={}||msg=exception",
                    clusterName, key, num1, num2, e);
        }

        return mergeIndexStats;
    }
}
