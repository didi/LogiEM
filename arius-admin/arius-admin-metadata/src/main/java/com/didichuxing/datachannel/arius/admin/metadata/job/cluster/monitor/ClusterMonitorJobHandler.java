package com.didichuxing.datachannel.arius.admin.metadata.job.cluster.monitor;

import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import com.didichuxing.datachannel.arius.admin.client.bean.dto.cluster.ESClusterDTO;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.app.App;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ClusterPhy;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.stats.*;
import com.didichuxing.datachannel.arius.admin.common.component.SpringTool;
import com.didichuxing.datachannel.arius.admin.common.constant.PercentilesEnum;
import com.didichuxing.datachannel.arius.admin.common.constant.arius.AriusUser;
import com.didichuxing.datachannel.arius.admin.common.constant.cluster.ClusterHealthEnum;
import com.didichuxing.datachannel.arius.admin.common.event.metrics.MetricsMonitorClusterEvent;
import com.didichuxing.datachannel.arius.admin.common.util.*;
import com.didichuxing.datachannel.arius.admin.core.service.app.AppService;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.monitorTask.ClusterMonitorTaskService;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.physic.ClusterPhyService;
import com.didichuxing.datachannel.arius.admin.core.service.es.ESClusterService;
import com.didichuxing.datachannel.arius.admin.core.service.template.physic.TemplatePhyService;
import com.didichuxing.datachannel.arius.admin.metadata.job.AbstractMetaDataJob;
import com.didichuxing.datachannel.arius.admin.metadata.job.cluster.monitor.esmonitorjob.MonitorMetricsSender;
import com.didichuxing.datachannel.arius.admin.persistence.es.index.dao.stats.AriusStatsClusterTaskInfoESDAO;
import com.didichuxing.datachannel.arius.admin.persistence.es.index.dao.stats.AriusStatsIndexInfoESDAO;
import com.didichuxing.datachannel.arius.admin.persistence.es.index.dao.stats.AriusStatsNodeInfoESDAO;
import com.didichuxing.datachannel.arius.admin.persistence.es.index.dao.template.TemplateAccessESDAO;
import com.didiglobal.logi.elasticsearch.client.response.cluster.ESClusterHealthResponse;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

import static com.didichuxing.datachannel.arius.admin.common.constant.AdminConstant.JOB_SUCCESS;
import static com.didichuxing.datachannel.arius.admin.common.constant.ClusterConstant.PHY_CLUSTER;

/**
 * ??????????????????????????????????????? es?????????????????????es??????tps/qps????????????
 */
@Component
public class ClusterMonitorJobHandler extends AbstractMetaDataJob {

    private static final double      SLA               = 0.9999;

    @Autowired
    private ClusterPhyService        clusterPhyService;

    @Autowired
    private TemplatePhyService       templatePhyService;

    @Autowired
    private AppService               appService;

    @Autowired
    private ESClusterService         esClusterService;

    @Autowired
    private MonitorMetricsSender     monitorMetricsSender;

    @Autowired
    private AriusStatsIndexInfoESDAO ariusStatsIndexInfoEsDao;

    @Autowired
    private AriusStatsNodeInfoESDAO  ariusStatsNodeInfoEsDao;

    @Autowired
    private AriusStatsClusterTaskInfoESDAO ariusStatsClusterTaskInfoESDAO;

    @Autowired
    private ClusterMonitorTaskService clusterMonitorTaskService;

    @Autowired
    private TemplateAccessESDAO templateAccessESDAO;

    private String  hostName     = HttpHostUtil.HOST_NAME;

    @Value("${monitorJob.thread.initsize:20}")
    private int  poolSize;

    /**
     * maxPoolSize?????????monitorjob???????????????????????????????????????
     * ??????maxPoolSize?????????????????????????????????maxPoolSize???????????????????????????
     */
    @Value("${monitorJob.thread.maxsize:30}")
    private int  maxPoolSize;

    private ThreadPoolExecutor threadPool;

    private FutureUtil<Void> futureUtil;

    private long timestamp = CommonUtils.monitorTimestamp2min(System.currentTimeMillis());

    @PostConstruct
    public void init() {
        futureUtil = FutureUtil.init("ClusterMonitorJobHandler", 3 * maxPoolSize, 3 * maxPoolSize, 100);
    }

    @Override
    public Object handleJobTask(String params) {
        LOGGER.info("class=ClusterMonitorJobHandler||method=handleJobTask||params={}", params);
        // ?????????????????????????????????
        List<ESClusterStats> esClusterStatsList = Lists.newCopyOnWriteArrayList();

        // ???????????????????????????,???????????????????????????DB, ?????????????????????????????????????????????
        Map<ClusterPhy, ESClusterHealthResponse> clusterHealthResponseMap = handlePhysicalClusterStats(esClusterStatsList);

        SpringTool.publish(new MetricsMonitorClusterEvent(this, esClusterStatsList, clusterHealthResponseMap, hostName));

        return JOB_SUCCESS;
    }

    /**************************************** private methods ****************************************/
    private Map<ClusterPhy, ESClusterHealthResponse> handlePhysicalClusterStats(List<ESClusterStats> esClusterStatsList) {
        this.timestamp = CommonUtils.monitorTimestamp2min(System.currentTimeMillis());

        // ???????????????????????????????????????????????????, ??????????????????????????????????????????????????????
        List<ClusterPhy> monitorCluster = clusterMonitorTaskService.getSingleMachineMonitorCluster(hostName);

        final Map<String, Integer> clusterPhyName2TemplateCountMap = templatePhyService.getClusterTemplateCountMap();

        int appIdCount = calcAppNu();

        Map<ClusterPhy, ESClusterHealthResponse> clusterHealthResponseMap = Maps.newConcurrentMap();
        int clusterSize = monitorCluster.size();
        Map<String,Future> futureMap = new HashMap<>();
        // 1. build multiple clusters status
        monitorCluster.forEach(dataSource -> {
            if (checkThreadPool()) {
                futureMap.put(dataSource.getCluster(),threadPool.submit( () -> {
                    try {
                        if (EnvUtil.getDC().getCode().equals(dataSource.getDataCenter())) {
                            ESClusterHealthResponse clusterHealthResponse = esClusterService.syncGetClusterHealth(dataSource.getCluster());
                            List<ESClusterStats> esClusterStatusList = buildEsClusterStatusWithPercentiles(clusterSize, dataSource,
                                    clusterPhyName2TemplateCountMap, appIdCount, clusterHealthResponse);

                            monitorMetricsSender.sendClusterStats(esClusterStatusList);
                            buildAndSendTaskStats(timestamp, dataSource);

                            if (clusterHealthResponse == null) {
                                clusterHealthResponse = new ESClusterHealthResponse();
                                clusterHealthResponse.setClusterName(dataSource.getCluster());
                                clusterHealthResponse.setStatus(ClusterHealthEnum.UNKNOWN.getDesc());
                                clusterHealthResponse.setActiveShards(0);
                            }
                            clusterHealthResponseMap.put(dataSource, clusterHealthResponse);

                            esClusterStatsList.addAll(esClusterStatusList);

                            // ??????????????????????????????????????????????????????????????????
                            handleSaveClusterHealthToDB(dataSource, clusterHealthResponse);
                        } else {
                            LOGGER.error(
                                    "class=ClusterMonitorJobHandler||method=handlePhysicalClusterStats||clusterPhyName={}||clusterPhyDataCenter={}"
                                            + "||errMsg= dataSource mismatch",
                                    dataSource.getCluster(), dataSource.getDataCenter());
                        }
                    } catch (Exception e) {
                        LOGGER.error(
                                "class=ClusterMonitorJobHandler||method=handlePhysicalClusterStats||clusterPhyName={}||clusterPhyDataCenter={}"
                                        + "||errMsg= dataSource mismatch",
                                dataSource.getCluster(), dataSource.getDataCenter(), e);
                    }
                } ));
            }
        });

        if (MapUtils.isNotEmpty(futureMap)) {
            futureMap.forEach((key, val) -> {
                try {
                    val.get(50,TimeUnit.SECONDS);
                } catch (Exception e) {
                    val.cancel(true);
                    LOGGER.error("class=ClusterMonitorJobHandler||method=handlePhysicalClusterStats||clusterPhyName={}||errMsg= dataSource mismatch", key);
                }
            });
        }

        return clusterHealthResponseMap;
    }

    private void buildAndSendTaskStats(long timestamp, ClusterPhy dataSource) {
        List<ESClusterTaskStatsResponse> taskStatsResponses = esClusterService.syncGetClusterTaskStats(dataSource.getCluster());

        taskStatsResponses.sort((stats1, stats2) -> (int) (stats1.getRunningTime() - stats2.getRunningTime()));

        List<ESClusterTaskStats> ESClusterTaskStatsList = taskStatsResponses.stream().map(x->{
            ESClusterTaskStats ESClusterTaskStats = new ESClusterTaskStats();
            ESClusterTaskStats.setCluster(dataSource.getCluster());
            ESClusterTaskStats.setDataCenter(dataSource.getDataCenter());
            ESClusterTaskStats.setPhysicCluster(PHY_CLUSTER);
            ESClusterTaskStats.setTimestamp(timestamp);
            ESClusterTaskStats.setMetrics(x);
            return ESClusterTaskStats;
        }).collect(Collectors.toList());

        monitorMetricsSender.sendClusterTaskStats(ESClusterTaskStatsList);
    }

    /**
     * ?????? ?????????ES???????????? ????????????
     * @param clusterNum
     * @param dataSource
     * @param clusterPhyName2TemplateCountMap
     * @param appIdCount
     * @return
     */
    private List<ESClusterStats> buildEsClusterStatusWithPercentiles(Integer clusterNum, ClusterPhy dataSource,
                                                                     Map<String, Integer> clusterPhyName2TemplateCountMap,
                                                                     Integer appIdCount, ESClusterHealthResponse healthResponse) {
        
        List<ESClusterStats> esClusterStatsList = Lists.newArrayList();

        //??????????????????????????????
        Map<String, ESClusterStatsCells> percentilesType2ESClusterStatsCellsMap = getPhysicalClusterStatsPercentiles(
            dataSource, healthResponse, clusterPhyName2TemplateCountMap, appIdCount, clusterNum);

        percentilesType2ESClusterStatsCellsMap.forEach((percentilesType, esClusterStatsCells) -> {
            ESClusterStats esClusterStats = new ESClusterStats();
            esClusterStats.setStatis(esClusterStatsCells);
            esClusterStats.setCluster(dataSource.getCluster());
            esClusterStats.setPercentilesType(percentilesType);
            esClusterStats.setPhysicCluster(PHY_CLUSTER);
            esClusterStats.setTimestamp(this.timestamp);
            esClusterStats.setDataCenter(dataSource.getDataCenter());

            esClusterStatsList.add(esClusterStats);
        });

        return esClusterStatsList;
    }

    /**
     * ????????????????????????????????????
     * @param dataSource
     * @param healthResponse
     * @param clusterPhyName2TemplateCountMap
     * @param appIdCount
     * @param clusterNum
     * @return
     */
    private Map<String, ESClusterStatsCells> getPhysicalClusterStatsPercentiles(ClusterPhy dataSource, ESClusterHealthResponse healthResponse,
                                                                                Map<String, Integer> clusterPhyName2TemplateCountMap,
                                                                                Integer appIdCount, Integer clusterNum) {
        Map<String, ESClusterStatsCells> percentilesType2ESClusterStatsCellsMap = Maps.newHashMap();
        String clusterName = dataSource.getCluster();
        if (AriusObjUtils.isNull(clusterName)) {
            LOGGER.warn("class=ClusterMonitorJobHandler||method=getPhysicalClusterStatsPercentiles||errMsg=clusterName is empty");
            return percentilesType2ESClusterStatsCellsMap;
        }

        ESClusterStatsCells esClusterStatsCells = buildForBasicInfo(dataSource, healthResponse,
                clusterPhyName2TemplateCountMap, appIdCount, clusterNum);

        AtomicReference<Map<String, Double>> clusterCpuAvgAndPercentilesAtomic                  = new AtomicReference<>();
        AtomicReference<Map<String, Double>> clusterDiskFreeUsagePercentAvgAndPercentilesAtomic = new AtomicReference<>();
        AtomicReference<Map<String, Double>> clusterSearchLatencyAvgAndPercentilesAtomic        = new AtomicReference<>();
        AtomicReference<Map<String, Double>> clusterIndexingLatencyAvgAndPercentilesAtomic      = new AtomicReference<>();
        AtomicReference<Map<String, Double>> clusterCpuLoad1MinAvgAndPercentilesAtomic          = new AtomicReference<>();
        AtomicReference<Map<String, Double>> clusterCpuLoad5MinAvgAndPercentilesAtomic          = new AtomicReference<>();
        AtomicReference<Map<String, Double>> clusterCpuLoad15MinAvgAndPercentilesAtomic         = new AtomicReference<>();
        AtomicReference<Map<String, Double>> clusterTaskCostMinAvgAndPercentilesAtomic          = new AtomicReference<>();

        futureUtil.runnableTask(() -> clusterCpuAvgAndPercentilesAtomic.set(ariusStatsNodeInfoEsDao.getClusterCpuAvgAndPercentiles(clusterName)))
                  .runnableTask(() -> clusterDiskFreeUsagePercentAvgAndPercentilesAtomic.set(ariusStatsNodeInfoEsDao.getClusterDiskFreeUsagePercentAvgAndPercentiles(clusterName)))
                  .runnableTask(() -> clusterSearchLatencyAvgAndPercentilesAtomic.set(ariusStatsNodeInfoEsDao.getClusterSearchLatencyAvgAndPercentiles(clusterName)))
                  .runnableTask(() -> clusterIndexingLatencyAvgAndPercentilesAtomic.set(ariusStatsNodeInfoEsDao.getClusterIndexingLatencyAvgAndPercentiles(clusterName)))
                  .runnableTask(() -> clusterCpuLoad1MinAvgAndPercentilesAtomic.set(ariusStatsNodeInfoEsDao.getClusterCpuLoad1MinAvgAndPercentiles(clusterName)))
                  .runnableTask(() -> clusterCpuLoad5MinAvgAndPercentilesAtomic.set(ariusStatsNodeInfoEsDao.getClusterCpuLoad5MinAvgAndPercentiles(clusterName)))
                  .runnableTask(() -> clusterCpuLoad15MinAvgAndPercentilesAtomic.set(ariusStatsNodeInfoEsDao.getClusterCpuLoad15MinAvgAndPercentiles(clusterName)))
                  .runnableTask(()-> clusterTaskCostMinAvgAndPercentilesAtomic.set(ariusStatsClusterTaskInfoESDAO.getTaskCostMinAvgAndPercentiles(clusterName)))
                  .waitExecute();

        for (String type : PercentilesEnum.listUsefulType()) {
            ESClusterStatsCells esClusterStatsCellDeepCopy = ConvertUtil.obj2Obj(esClusterStatsCells, ESClusterStatsCells.class);

            buildForPercentiles(esClusterStatsCellDeepCopy, type,
                    clusterCpuAvgAndPercentilesAtomic.get(),
                    clusterCpuLoad1MinAvgAndPercentilesAtomic.get(),
                    clusterCpuLoad5MinAvgAndPercentilesAtomic.get(),
                    clusterCpuLoad15MinAvgAndPercentilesAtomic.get(),
                    clusterDiskFreeUsagePercentAvgAndPercentilesAtomic.get(),
                    clusterSearchLatencyAvgAndPercentilesAtomic.get(),
                    clusterIndexingLatencyAvgAndPercentilesAtomic.get(),
                    clusterTaskCostMinAvgAndPercentilesAtomic.get());

            percentilesType2ESClusterStatsCellsMap.put(type, esClusterStatsCellDeepCopy);

        }

        return percentilesType2ESClusterStatsCellsMap;
    }

    /**
     * @param esClusterStatsCellDeepCopy                       ????????????????????????
     * @param type                                             avg???????????????(99???95???75???55)
     * @param clusterCpuAvgAndPercentiles                      ??????cpu?????????????????????(key:99, value:???)
     * @param clusterDiskFreeUsagePercentAvgAndPercentiles     ????????????????????????????????????????????????(key:99, value:???)
     * @param clusterSearchLatencyAvgAndPercentiles            ?????????????????????????????????????????????(key:99, value:???)
     * @param clusterIndexingLatencyAvgAndPercentiles          ?????????????????????????????????????????????(key:99, value:???)
     * @param clusterCpuLoad1MinAvgAndPercentiles              ??????cpu load1?????????????????????(key:99, value:???)
     * @param clusterCpuLoad5MinAvgAndPercentiles              ??????cpu load5?????????????????????(key:99, value:???)
     * @param clusterCpuLoad15MinAvgAndPercentiles             ??????cpu load15?????????????????????(key:99, value:???)
     * @param clusterTaskCostMinAvgAndPercentiles              ??????task cost?????????????????????(key:99, value:???)
     */
    private void buildForPercentiles(ESClusterStatsCells esClusterStatsCellDeepCopy, String type,
                                     Map<String, Double> clusterCpuAvgAndPercentiles,
                                     Map<String, Double> clusterCpuLoad1MinAvgAndPercentiles,
                                     Map<String, Double> clusterCpuLoad5MinAvgAndPercentiles,
                                     Map<String, Double> clusterCpuLoad15MinAvgAndPercentiles,
                                     Map<String, Double> clusterDiskFreeUsagePercentAvgAndPercentiles,
                                     Map<String, Double> clusterSearchLatencyAvgAndPercentiles,
                                     Map<String, Double> clusterIndexingLatencyAvgAndPercentiles,
                                     Map<String, Double> clusterTaskCostMinAvgAndPercentiles) {
        if (null != clusterCpuAvgAndPercentiles && null != clusterCpuAvgAndPercentiles.get(type)) {
            esClusterStatsCellDeepCopy.setCpuUsage(clusterCpuAvgAndPercentiles.get(type));
        }

        if (null != clusterCpuLoad1MinAvgAndPercentiles && null != clusterCpuLoad1MinAvgAndPercentiles.get(type)) {
            esClusterStatsCellDeepCopy.setCpuLoad1M(clusterCpuLoad1MinAvgAndPercentiles.get(type));
        }

        if (null != clusterCpuLoad5MinAvgAndPercentiles && null != clusterCpuLoad5MinAvgAndPercentiles.get(type)) {
            esClusterStatsCellDeepCopy.setCpuLoad5M(clusterCpuLoad5MinAvgAndPercentiles.get(type));
        }

        if (null != clusterCpuLoad15MinAvgAndPercentiles && null != clusterCpuLoad15MinAvgAndPercentiles.get(type)) {
            esClusterStatsCellDeepCopy.setCpuLoad15M(clusterCpuLoad15MinAvgAndPercentiles.get(type));
        }

        String realType = convertSpecialTypeForDiskFreeUsage(type);
        if (null != clusterDiskFreeUsagePercentAvgAndPercentiles && null != clusterDiskFreeUsagePercentAvgAndPercentiles.get(realType)) {
            esClusterStatsCellDeepCopy.setDiskUsage(1 - clusterDiskFreeUsagePercentAvgAndPercentiles.get(realType));
        }

        if (null != clusterSearchLatencyAvgAndPercentiles && null != clusterSearchLatencyAvgAndPercentiles.get(type)) {
            esClusterStatsCellDeepCopy.setSearchLatency(clusterSearchLatencyAvgAndPercentiles.get(type));
        }

        if (null != clusterIndexingLatencyAvgAndPercentiles && null != clusterIndexingLatencyAvgAndPercentiles.get(type)) {
            esClusterStatsCellDeepCopy.setIndexingLatency(clusterIndexingLatencyAvgAndPercentiles.get(type));
        }

        if (null != clusterTaskCostMinAvgAndPercentiles && null != clusterTaskCostMinAvgAndPercentiles.get(type)) {
            esClusterStatsCellDeepCopy.setTaskCost(clusterTaskCostMinAvgAndPercentiles.get(type));
        }
    }

    private String convertSpecialTypeForDiskFreeUsage(String type) {
        if (PercentilesEnum.ST99.getType().equals(type)) {
            return PercentilesEnum.ST1.getType();
        }

        if (PercentilesEnum.ST95.getType().equals(type)) {
            return PercentilesEnum.ST5.getType();
        }

        if (PercentilesEnum.ST75.getType().equals(type)) {
            return PercentilesEnum.ST25.getType();
        }

        if (PercentilesEnum.ST55.getType().equals(type)) {
            return PercentilesEnum.ST45.getType();
        }

        return PercentilesEnum.AVG.getType();
    }

    /**
     * ??????????????????????????????
     * @param dataSource
     * @param healthResponse
     * @param clusterPhyName2TemplateCountMap
     * @param appIdCount
     * @param clusterNum
     * @return
     */
    private ESClusterStatsCells buildForBasicInfo(ClusterPhy dataSource, ESClusterHealthResponse healthResponse,
                                                  Map<String, Integer> clusterPhyName2TemplateCountMap,
                                                  Integer appIdCount, Integer clusterNum) {
        ESClusterStatsCells esClusterStatsBean = new ESClusterStatsCells();
        if (null != healthResponse) {
            esClusterStatsBean.setStatus(healthResponse.getStatus());
            esClusterStatsBean.setStatusType(ClusterHealthEnum.valuesOf(healthResponse.getStatus()).getCode());
        }else {
            esClusterStatsBean.setStatus(ClusterHealthEnum.UNKNOWN.getDesc());
            esClusterStatsBean.setStatusType(ClusterHealthEnum.UNKNOWN.getCode());
        }

        if (null != dataSource) {
            esClusterStatsBean.setClusterName(dataSource.getCluster());
            esClusterStatsBean.setLevel(dataSource.getLevel());
            esClusterStatsBean.setClusterNu(clusterNum);
            esClusterStatsBean.setAppNu(appIdCount);
            esClusterStatsBean.setSla(SLA);

            int totalTemplateNu = clusterPhyName2TemplateCountMap.getOrDefault(dataSource.getCluster(), 0);
            esClusterStatsBean.setTotalTemplateNu(totalTemplateNu);

            handlePhysicalClusterStatsForSum(dataSource.getCluster(), esClusterStatsBean);
        }
        return esClusterStatsBean;
    }

    private void handlePhysicalClusterStatsForSum(String clusterName, ESClusterStatsCells esClusterStats) {
        // ??????????????????es??????????????????????????????http????????????
        futureUtil.runnableTask(()-> esClusterStats.setReadTps(ariusStatsIndexInfoEsDao.getClusterQps(clusterName)))
                .runnableTask(()->  esClusterStats.setWriteTps(ariusStatsIndexInfoEsDao.getClusterTps(clusterName)))
                .runnableTask(() -> esClusterStats.setRecvTransSize(ariusStatsNodeInfoEsDao.getClusterRx(clusterName)))
                .runnableTask(() -> esClusterStats.setSendTransSize(ariusStatsNodeInfoEsDao.getClusterTx(clusterName)))
                .runnableTask(() -> setClusterOtherStats(clusterName, esClusterStats))
                .runnableTask(() -> esClusterStats.setQueryTimesPreDay(templateAccessESDAO.getYesterDayAllTemplateAccess(clusterName)))
                .waitExecute();
    }

    /**
     * ???????????????????????????
     *
     * @param clusterName
     * @param esClusterStats
     */
    private void setClusterOtherStats(String clusterName, ESClusterStatsCells esClusterStats) {
        ESClusterStatsResponse clusterStats = null;
        try {

            clusterStats = esClusterService.syncGetClusterStats(clusterName);
            if (Objects.isNull(clusterStats)) {
                return;
            }

            //????????????
            esClusterStats.setTotalIndicesNu(clusterStats.getIndexCount());
            esClusterStats.setShardNu(clusterStats.getTotalShard());
            esClusterStats.setTotalDocNu(clusterStats.getDocsCount());
            esClusterStats.setEsNodeNu(clusterStats.getTotalNodes());

            //????????????
            esClusterStats.setNumberNodes(clusterStats.getTotalNodes());
            esClusterStats.setNumberMasterNodes(clusterStats.getNumberMasterNodes());
            esClusterStats.setNumberClientNodes(clusterStats.getNumberClientNodes());
            esClusterStats.setNumberDataNodes(clusterStats.getNumberDataNodes());
            esClusterStats.setNumberIngestNodes(clusterStats.getNumberIngestNodes());

            //??????????????????
            esClusterStats.setTotalStoreSize(clusterStats.getTotalFs().getBytes());
            esClusterStats.setFreeStoreSize(clusterStats.getFreeFs().getBytes());

            esClusterStats.setStoreSize(esClusterStats.getTotalStoreSize() - esClusterStats.getFreeStoreSize());
            esClusterStats.setIndexStoreSize(esClusterStats.getTotalStoreSize() - esClusterStats.getFreeStoreSize());

            //??????????????????
            esClusterStats.setMemUsed(clusterStats.getMemUsed().getGb());
            esClusterStats.setMemFree(clusterStats.getMemFree().getGb());
            esClusterStats.setMemTotal(clusterStats.getMemTotal().getGb());
            esClusterStats.setMemFreePercent(clusterStats.getMemFreePercent());
            esClusterStats.setMemUsedPercent(clusterStats.getMemUsedPercent());

            //??????shard??????
            ESClusterHealthResponse clusterHealthResponse = esClusterService.syncGetClusterHealth(clusterName);
            if (null == clusterHealthResponse) {
                return;
            }
            esClusterStats.setUnAssignedShards(clusterHealthResponse.getUnassignedShards());
            esClusterStats.setNumberPendingTasks(clusterHealthResponse.getNumberOfPendingTasks());

            //??????task??????
            esClusterStats.setTaskCount(ariusStatsClusterTaskInfoESDAO.getTaskCount(clusterName));

        } catch (Exception e) {
            LOGGER.error("class=ClusterMonitorJobHandler||method=setClusterOtherStats||clusterName={}, clusterStats={}",
                    clusterName, clusterStats, e);
        }
    }


    //??????????????????
    private int calcAppNu() {
        List<App> queryApps = appService.listApps();
        return CollectionUtils.isEmpty(queryApps) ? 0 : queryApps.size();
    }

    private void handleSaveClusterHealthToDB(ClusterPhy clusterPhy, ESClusterHealthResponse eSClusterHealthResponse) {
        ESClusterDTO esClusterDTO = new ESClusterDTO();
        try {
            esClusterDTO.setId(clusterPhy.getId());

            if (null == eSClusterHealthResponse) {
                esClusterDTO.setHealth(ClusterHealthEnum.UNKNOWN.getCode());
                esClusterDTO.setActiveShardNum(0L);
            } else {
                ClusterHealthEnum clusterHealthEnum = ClusterHealthEnum.valuesOf(eSClusterHealthResponse.getStatus());
                esClusterDTO.setHealth(clusterHealthEnum.getCode());
                esClusterDTO.setActiveShardNum(eSClusterHealthResponse.getActiveShards());
            }
            clusterPhyService.editCluster(esClusterDTO, AriusUser.SYSTEM.getDesc());
        } catch (Exception e) {
            LOGGER.error(
                    "class=ClusterMonitorJobHandler||method=handleSaveClusterHealthToDB||clusterName={}, clusterStats={}",
                    clusterPhy.getCluster(), null != eSClusterHealthResponse.getStatus() ? eSClusterHealthResponse.getStatus() : null, e);
        }
    }

    /**
     * ??????????????????????????????
     * @return
     */
    private boolean checkThreadPool() {
        if (threadPool == null || threadPool.isShutdown()) {
            threadPool = new ThreadPoolExecutor(poolSize, maxPoolSize + 10,
                    0L, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>( 100 ),
                    new BasicThreadFactory.Builder().namingPattern("cluster-monitor-cluster-data-collect-%d").build());
        }

        long blockSize = threadPool.getQueue().size();
        if (blockSize > 10) {
            LOGGER.warn("class=ClusterMonitorJobHandler||method=checkThreadPool||blockSize={}||msg=collect thread pool has block task", blockSize);
        }

        if (blockSize > 30) {
            LOGGER.error("class=ClusterMonitorJobHandler||method=checkThreadPool||blockSize={}||msg=collect thread pool is too busy. thread pool recreate", blockSize);
            threadPool.shutdownNow();
            threadPool = null;
            return false;
        }

        return true;
    }
}