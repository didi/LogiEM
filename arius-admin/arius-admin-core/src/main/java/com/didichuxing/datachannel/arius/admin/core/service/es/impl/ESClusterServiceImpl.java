package com.didichuxing.datachannel.arius.admin.core.service.es.impl;

import static com.didichuxing.datachannel.arius.admin.persistence.constant.ESOperateContant.*;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.didichuxing.datachannel.arius.admin.common.bean.po.stats.ESClusterThreadPO;
import com.didichuxing.datachannel.arius.admin.common.constant.cluster.ClusterConnectionStatus;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.compress.utils.Sets;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.rest.RestStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.didichuxing.datachannel.arius.admin.client.bean.common.NodeAllocationInfo;
import com.didichuxing.datachannel.arius.admin.client.bean.common.NodeAttrInfo;
import com.didichuxing.datachannel.arius.admin.client.bean.common.Result;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.setting.ESClusterGetSettingsAllResponse;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.stats.ECSegmentsOnIps;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.stats.ESClusterStatsResponse;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.stats.ESClusterTaskStatsResponse;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.stats.ESClusterThreadStats;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.stats.dashboard.ClusterThreadPoolQueueMetrics;
import com.didichuxing.datachannel.arius.admin.common.constant.cluster.ClusterHealthEnum;
import com.didichuxing.datachannel.arius.admin.common.exception.ESOperateException;
import com.didichuxing.datachannel.arius.admin.common.util.AriusObjUtils;
import com.didichuxing.datachannel.arius.admin.common.util.ConvertUtil;
import com.didichuxing.datachannel.arius.admin.common.util.ListUtils;
import com.didichuxing.datachannel.arius.admin.common.util.SizeUtil;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.physic.ClusterPhyService;
import com.didichuxing.datachannel.arius.admin.core.service.es.ESClusterService;
import com.didichuxing.datachannel.arius.admin.persistence.component.ESOpTimeoutRetry;
import com.didichuxing.datachannel.arius.admin.persistence.constant.ESOperateContant;
import com.didichuxing.datachannel.arius.admin.persistence.es.cluster.ESClusterDAO;
import com.didiglobal.logi.elasticsearch.client.ESClient;
import com.didiglobal.logi.elasticsearch.client.gateway.direct.DirectRequest;
import com.didiglobal.logi.elasticsearch.client.gateway.direct.DirectResponse;
import com.didiglobal.logi.elasticsearch.client.response.cluster.ESClusterHealthResponse;
import com.didiglobal.logi.elasticsearch.client.response.cluster.nodes.ClusterNodeInfo;
import com.didiglobal.logi.elasticsearch.client.response.cluster.nodessetting.ClusterNodeSettings;
import com.didiglobal.logi.elasticsearch.client.response.cluster.nodessetting.ESClusterNodesSettingResponse;
import com.didiglobal.logi.elasticsearch.client.response.indices.getalias.ESIndicesGetAliasResponse;
import com.didiglobal.logi.log.ILog;
import com.didiglobal.logi.log.LogFactory;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * @author d06679
 * @date 2019/5/8
 */
@Service
public class ESClusterServiceImpl implements ESClusterService {

    private static final ILog LOGGER = LogFactory.getLog(ESClusterServiceImpl.class);

    @Autowired
    private ESClusterDAO      esClusterDAO;

    @Autowired
    private ClusterPhyService clusterPhyService;

    /**
     * ????????????re balance
     * @param cluster    ??????
     * @param retryCount ????????????
     * @return result
     * @throws ESOperateException
     */
    @Override
    public boolean syncCloseReBalance(String cluster, Integer retryCount) throws ESOperateException {
        return ESOpTimeoutRetry.esRetryExecute("syncCloseReBalance", retryCount,
            () -> esClusterDAO.configReBalanceOperate(cluster, "none"));
    }

    /**
     * ????????????rebalance
     *
     * @param cluster   ??????
     * @param esVersion ??????
     * @return result
     * @throws ESOperateException
     */
    @Override
    public boolean syncOpenReBalance(String cluster, String esVersion) throws ESOperateException {
        return ESOpTimeoutRetry.esRetryExecute("syncOpenReBalance", 3,
            () -> esClusterDAO.configReBalanceOperate(cluster, "all"));
    }

    /**
     * ??????????????????
     *
     * @param cluster       ??????
     * @param remoteCluster ????????????
     * @param tcpAddresses  tcp??????
     * @param retryCount    ????????????
     * @return true/false
     */
    @Override
    public boolean syncPutRemoteCluster(String cluster, String remoteCluster, List<String> tcpAddresses,
                                        Integer retryCount) throws ESOperateException {
        return ESOpTimeoutRetry.esRetryExecute("syncPutRemoteCluster", retryCount,
            () -> esClusterDAO.putPersistentRemoteClusters(cluster,
                String.format(ESOperateContant.REMOTE_CLUSTER_FORMAT, remoteCluster), tcpAddresses));
    }

    /**
     * ?????????????????????
     *
     * @param cluster         ??????
     * @param settingFlatName setting??????
     * @return true/false
     */
    @Override
    public boolean hasSettingExist(String cluster, String settingFlatName) {
        Map<String, Object> clusterSettingMap = esClusterDAO.getPersistentClusterSettings(cluster);
        if (null == clusterSettingMap) { return false;}
        return clusterSettingMap.containsKey(settingFlatName);
    }

    /**
     * ?????????????????????????????????
     *
     * @param cluster    ??????
     * @param retryCount ????????????
     * @return true/false
     * @throws ESOperateException
     */
    @Override
    public boolean syncConfigColdDateMove(String cluster, int inGoing, int outGoing, String moveSpeed,
                                          int retryCount) throws ESOperateException {

        Map<String, Object> configMap = Maps.newHashMap();

        if (inGoing > 0) {
            configMap.put(CLUSTER_ROUTING_ALLOCATION_OUTGOING, outGoing);
        }

        if (outGoing > 0) {
            configMap.put(CLUSTER_ROUTING_ALLOCATION_INGOING, inGoing);
        }

        configMap.put(COLD_MAX_BYTES_PER_SEC_KEY, moveSpeed);

        return ESOpTimeoutRetry.esRetryExecute("syncConfigColdDateMove", retryCount,
            () -> esClusterDAO.putPersistentConfig(cluster, configMap));
    }

    @Override
    public Map<String, List<String>> syncGetNode2PluginsMap(String cluster) {
        return esClusterDAO.getNode2PluginsMap(cluster);
    }

    /**
     * ?????????????????????????????????????????????????????????
     *
     * @param cluster
     * @return
     */
    @Override
    public Map<String/*alias*/, Set<String>> syncGetAliasMap(String cluster) {
        Map<String, Set<String>> ret = new HashMap<>();

        try {
            ESIndicesGetAliasResponse response = esClusterDAO.getClusterAlias(cluster);
            if(response == null || response.getM() == null) {
                return ret;
            }
            for (String index : response.getM().keySet()) {
                for (String alias : response.getM().get(index).getAliases().keySet()) {
                    if (!ret.containsKey(alias)) {
                        ret.put(alias, new HashSet<>());
                    }
                    ret.get(alias).add(index);
                }
            }
            return ret;
        } catch (Exception t) {
            LOGGER.error("class=ClusterClientPool||method=syncGetAliasMap||clusterName={}||errMsg=fail to get alias", cluster, t);
            return ret;
        }
    }

    @Override
    public int syncGetClientAlivePercent(String cluster, String password, String clientAddresses) {
        if (null == cluster || StringUtils.isBlank(clientAddresses)) {
            return 0;
        }

        List<String> addresses = ListUtils.string2StrList(clientAddresses);

        int alive = 0;
        for (String address : addresses) {
            boolean isAlive = judgeClientAlive(cluster, password, address);
            if (isAlive) {
                alive++;
            }
        }

        return alive * 100 / addresses.size();
    }

    /**
     * ??????es client????????????
     *
     * @param cluster
     * @param address
     * @return
     */
    @Override
    public boolean judgeClientAlive(String cluster,String password ,String address) {

        String[] hostAndPortStr = StringUtils.split(address, ":");
        if (null == hostAndPortStr || hostAndPortStr.length <= 1) {
            LOGGER.info("class=ClusterClientPool||method=getNotSniffESClient||addresses={}||msg=clusterClientError",
                address);
            return false;
        }

        String host = hostAndPortStr[0];
        String port = hostAndPortStr[1];
        List<InetSocketTransportAddress> transportAddresses = Lists.newArrayList();
        ESClient esClient = new ESClient();
        try  {
            transportAddresses.add(new InetSocketTransportAddress(InetAddress.getByName(host), Integer.parseInt(port)));
            esClient.addTransportAddresses(transportAddresses.toArray(new TransportAddress[0]));
            esClient.setClusterName(cluster);
            if(StringUtils.isNotBlank(password)){
                esClient.setPassword(password);
            }
            esClient.start();

            ESClusterHealthResponse response = esClient.admin().cluster().prepareHealth().execute().actionGet(30,
                TimeUnit.SECONDS);
            return !response.isTimedOut();
        } catch (Exception e) {
            esClient.close();
            LOGGER.error(
                "class=ESClusterServiceImpl||method=judgeClientAlive||cluster={}||client={}||msg=judgeAlive is excepjudgeClientAlivetion!",
                cluster, address, e);
            return false;
        }finally {
            esClient.close();
        }
    }

	@Override
    public ESClusterHealthResponse syncGetClusterHealth(String clusterName) {
        return esClusterDAO.getClusterHealth(clusterName);
    }

    @Override
    public List<ESClusterTaskStatsResponse> syncGetClusterTaskStats(String clusterName) {
        return esClusterDAO.getClusterTaskStats(clusterName);
    }

    @Override
    public ClusterHealthEnum syncGetClusterHealthEnum(String clusterName) {
        ESClusterHealthResponse clusterHealthResponse = esClusterDAO.getClusterHealth(clusterName);

        ClusterHealthEnum clusterHealthEnum = ClusterHealthEnum.UNKNOWN;
        if (clusterHealthResponse != null) {
            clusterHealthEnum = ClusterHealthEnum.valuesOf(clusterHealthResponse.getStatus());
        }
        return clusterHealthEnum;
    }

    @Override
    public ESClusterStatsResponse syncGetClusterStats(String clusterName) {
        return esClusterDAO.getClusterStats(clusterName);
    }

    @Override
    public ESClusterGetSettingsAllResponse syncGetClusterSetting(String cluster) {
        return esClusterDAO.getClusterSetting(cluster);
    }

    @Override
    public Map<String, Integer> synGetSegmentsOfIpByCluster(String clusterName) {
        Map<String, Integer> segmentsOnIpMap = Maps.newHashMap();
        for (ECSegmentsOnIps ecSegmentsOnIp : esClusterDAO.getSegmentsOfIpByCluster(clusterName)) {
            if (segmentsOnIpMap.containsKey(ecSegmentsOnIp.getIp())) {
                Integer newSegments = segmentsOnIpMap.get(ecSegmentsOnIp.getIp()) + Integer.parseInt(ecSegmentsOnIp.getSegment());
                segmentsOnIpMap.put(ecSegmentsOnIp.getIp(), newSegments);
            } else {
                segmentsOnIpMap.put(ecSegmentsOnIp.getIp(), Integer.valueOf(ecSegmentsOnIp.getSegment()));
            }
        }
        return segmentsOnIpMap;
    }

    @Override
    public boolean syncPutPersistentConfig(String cluster, Map<String, Object> configMap) {
        return esClusterDAO.putPersistentConfig(cluster, configMap);
    }

    @Override
    public String synGetESVersionByCluster(String cluster) {
        return esClusterDAO.getESVersionByCluster(cluster);
    }

    @Override
    public Map<String, ClusterNodeInfo> syncGetAllSettingsByCluster(String cluster) {
        return esClusterDAO.getAllSettingsByCluster(cluster);
    }

    @Override
    public Map<String, ClusterNodeSettings> syncGetPartOfSettingsByCluster(String cluster) {
        return esClusterDAO.getPartOfSettingsByCluster(cluster);
    }

    @Override
    public Set<String> syncGetAllNodesAttributes(String cluster) {
        Set<String> nodeAttributes = Sets.newHashSet();
        List<NodeAttrInfo> nodeAttrInfos = esClusterDAO.syncGetAllNodesAttributes(cluster);
        if (CollectionUtils.isEmpty(nodeAttrInfos)) {
            return nodeAttributes;
        }

        //????????????????????????????????????????????????
        nodeAttrInfos.forEach(nodeAttrInfo -> nodeAttributes.add(nodeAttrInfo.getAttribute()));

        return nodeAttributes;
    }

    /**
     * rack?????????????????????????????? key->rack value->diskSize
     */
    @Override
    public Map<String, Float> getAllocationInfoOfRack(String cluster) {
        //??????????????????????????????????????????????????????
        Map<String, String> canUseDiskOnNodeMap = ConvertUtil.list2Map(esClusterDAO.getNodeAllocationInfoByCluster(cluster),
                NodeAllocationInfo::getNode, NodeAllocationInfo::getTotalDiskSize);
        if (MapUtils.isEmpty(canUseDiskOnNodeMap)) {
            LOGGER.warn("class=ESClusterServiceImpl||method=getAllocationInfoOfRack||msg=cant get node allocation");
            return null;
        }

        //??????????????????????????????attr?????????
        Map<String, Float> allocationInfoOfRackMap = Maps.newHashMap();
        List<NodeAttrInfo> nodeAttrInfos = esClusterDAO.syncGetAllNodesAttributes(cluster);
        if (CollectionUtils.isEmpty(nodeAttrInfos)) {
            LOGGER.warn("class=ESClusterServiceImpl||method=getAllocationInfoOfRack||msg=cant get node attributes");
            return null;
        }

        //????????????rack??????????????????????????????????????????rack???????????????????????????
        nodeAttrInfos.stream().filter(nodeAttrInfo -> "rack".equals(nodeAttrInfo.getAttribute())).forEach(
                nodeAttrInfo -> {
                    //??????rack?????????????????????
                    String attrRack = nodeAttrInfo.getValue();
                    //??????rack????????????????????????????????????????????????,es???????????????raw???????????????gb???????????????123.5gb
                    String canUseDiskDateOfNode = canUseDiskOnNodeMap.get(nodeAttrInfo.getNode());
                    //??????????????????????????????????????????????????????
                    if (StringUtils.isBlank(canUseDiskDateOfNode)) {
                        return;
                    }

                    //????????????rack??????????????????????????????????????????
                    Float newDiskSize = Float.valueOf(SizeUtil.getUnitSize(canUseDiskDateOfNode));
                    if (allocationInfoOfRackMap.containsKey(attrRack)) {
                        allocationInfoOfRackMap.put(attrRack, newDiskSize + allocationInfoOfRackMap.get(attrRack));
                    } else {
                        allocationInfoOfRackMap.put(attrRack, newDiskSize);
                    }

                    //???????????????allocation_map??????????????????
                    canUseDiskOnNodeMap.remove(nodeAttrInfo.getNode());
                }
        );

        if(MapUtils.isEmpty(canUseDiskOnNodeMap)) {
            return allocationInfoOfRackMap;
        }

        // ?????????????????????rack?????????????????????rack???*???????????????
        final float[] diskNumber = {0f};
        canUseDiskOnNodeMap.values().forEach(s -> diskNumber[0] = SizeUtil.getUnitSize(s) + diskNumber[0]);
        allocationInfoOfRackMap.put("*", diskNumber[0]);

        return allocationInfoOfRackMap;
    }

    @Override
    public Result<Set<String>> getClusterRackByHttpAddress(String addresses, String password) {
        Set<String> racks = new HashSet<>();
        ESClient client = new ESClient();
        client.addTransportAddresses(addresses);

        if (StringUtils.isNotBlank(password)) {
            client.setPassword(password);
        }

        try {
            client.start();
            ESClusterNodesSettingResponse response = client.admin().cluster().prepareNodesSetting().execute()
                    .actionGet(ES_OPERATE_TIMEOUT, TimeUnit.SECONDS);

            if (RestStatus.OK.getStatus() == response.getRestStatus().getStatus()) {
                for (Map.Entry<String, ClusterNodeSettings> entry : response.getNodes().entrySet()) {
                    // ???????????????roles????????????
                    List<String> roles = JSONArray.parseArray(JSON.toJSONString(entry.getValue().getRoles()), String.class);
                    // ?????????(2.3.3)????????????nodes????????????roles???????????????????????????????????????node???settings?????????
                    if (CollectionUtils.isEmpty(roles)) {
                        // ??????setting.node??????????????????
                        JSONObject nodeRole = entry.getValue().getSettings().getJSONObject("node");
                        if (AriusObjUtils.isNull(nodeRole) || !nodeRole.containsKey(ES_ROLE_DATA) || nodeRole.getBoolean(ES_ROLE_DATA)) {
                            setRacksForDateRole(racks, entry);
                        }
                        continue;
                    }

                    // ?????????????????????node?????????roles????????????????????????
                    if (roles.contains(ES_ROLE_DATA)) {
                        setRacksForDateRole(racks, entry);
                    }
                }

                return Result.buildSucc(racks);
            } else {
                return Result.buildParamIllegal(String.format("????????????:%s??????rack??????", addresses));
            }
        } catch (Exception e) {
            LOGGER.error("class=ESClusterServiceImpl||method=getClusterRackByHttpAddress||msg=get rack error||httpAddress={}||msg=client start error", addresses);
            return Result.buildParamIllegal(String.format("????????????:%s??????rack??????", addresses));
        } finally {
            client.close();
        }
    }

    @Override
    public Result<Void> checkSameCluster(String password, List<String> addresses){
        Set<String> clusters = new HashSet<>();
        for(String address: addresses) {
            ESClient client = new ESClient();
            try {
                if (StringUtils.isNotBlank(password)) {
                    client.setPassword(password);
                }
                client.addTransportAddresses(address);
                client.start();
                ESClusterNodesSettingResponse response = client.admin().cluster().prepareNodesSetting().execute()
                        .actionGet(ES_OPERATE_TIMEOUT, TimeUnit.SECONDS);
                if (RestStatus.OK.getStatus() == response.getRestStatus().getStatus()) {
                    clusters.add(response.getClusterName());
                }
            } catch (Exception e) {
                LOGGER.error("class=ESClusterServiceImpl||method=getClusterRackByHttpAddress||msg=get rack error||httpAddress={}||msg=client start error", addresses);
            } finally {
                client.close();
            }
        }
        return clusters.size() > 1 ? Result.buildFail() : Result.buildSucc();
    }

    @Override
    public String synGetESVersionByHttpAddress(String addresses, String password) {
        ESClient client = new ESClient();
        client.addTransportAddresses(addresses);

        if (StringUtils.isNotBlank(password)) {
            client.setPassword(password);
        }
        String esVersion = null;
        try {
            client.start();
            DirectRequest directRequest = new DirectRequest("GET", "");
            DirectResponse directResponse = client.direct(directRequest).actionGet(30, TimeUnit.SECONDS);
            if (directResponse.getRestStatus() == RestStatus.OK
                    && StringUtils.isNoneBlank(directResponse.getResponseContent())) {
                JSONObject version = JSONObject.parseObject(directResponse.getResponseContent()).getJSONObject(VERSION);
                esVersion = version.getString(VERSION_NUMBER);
                if (version.containsKey(VERSION_INNER_NUMBER)) {
                    String innerVersion = version.getString(VERSION_INNER_NUMBER).split("\\.")[0].trim();
                    esVersion = Strings.isNullOrEmpty(innerVersion) ? esVersion : esVersion + "." + innerVersion;
                }
            }
            return esVersion;
        } catch (Exception e) {
            LOGGER.warn("class=ESClusterServiceImpl||method=synGetESVersionByHttpAddress||address={}||mg=get es segments fail", addresses, e);
            return null;
        } finally {
            client.close();
        }
    }

    @Override
    public ClusterConnectionStatus checkClusterPassword(String addresses, String password) {
        ESClient client = new ESClient();
        client.addTransportAddresses(addresses);
        if (StringUtils.isNotBlank(password)) {
            client.setPassword(password);
        }

        try {
            client.start();
            DirectRequest directRequest = new DirectRequest("GET", "");
            DirectResponse directResponse = client.direct(directRequest).actionGet(30, TimeUnit.SECONDS);
            return ClusterConnectionStatus.NORMAL;
        } catch (Exception e) {
            LOGGER.warn("class=ESClusterServiceImpl||method=checkClusterWithoutPassword||address={}||mg=get es segments fail", addresses, e);
            if (e.getCause().getMessage().contains("Unauthorized")) {
                return ClusterConnectionStatus.UNAUTHORIZED;
            } else {
                return ClusterConnectionStatus.DISCONNECTED;
            }
        } finally {
            client.close();
        }
    }

    @Override
    public ESClusterThreadStats syncGetThreadStatsByCluster(String cluster) {
        List<ESClusterThreadPO> threadStats = esClusterDAO.syncGetThreadStatsByCluster(cluster);
        ESClusterThreadStats esClusterThreadStats = new ESClusterThreadStats(cluster, 0L, 0L, 0L, 0L, 0L, 0L);
        if (threadStats != null) {
            esClusterThreadStats.setManagement(threadStats.stream().filter(thread -> thread.getThreadName().equals("management")).mapToLong(ESClusterThreadPO::getQueueNum).sum());
            esClusterThreadStats.setRefresh(threadStats.stream().filter(thread -> thread.getThreadName().equals("refresh")).mapToLong(ESClusterThreadPO::getQueueNum).sum());
            esClusterThreadStats.setFlush(threadStats.stream().filter(thread -> thread.getThreadName().equals("flush")).mapToLong(ESClusterThreadPO::getQueueNum).sum());
            esClusterThreadStats.setMerge(threadStats.stream().filter(thread -> thread.getThreadName().equals("force_merge")).mapToLong(ESClusterThreadPO::getQueueNum).sum());
            esClusterThreadStats.setSearch(threadStats.stream().filter(thread -> thread.getThreadName().equals("search")).mapToLong(ESClusterThreadPO::getQueueNum).sum());
            esClusterThreadStats.setWrite(threadStats.stream().filter(thread -> thread.getThreadName().equals("write")).mapToLong(ESClusterThreadPO::getQueueNum).sum());
        }
        return esClusterThreadStats;
    }

    @Override
    public ESClusterHealthResponse syncGetClusterHealthAtIndicesLevel(String phyClusterName) {
        return esClusterDAO.getClusterHealthAtIndicesLevel(phyClusterName);
    }

    /***************************************** private method ****************************************************/
    /**
     * ??????node???atrributes?????????????????????rack????????????
     * @param racks rack??????
     * @param entry ??????node???entry
     */
    private void setRacksForDateRole(Set<String> racks, Map.Entry<String, ClusterNodeSettings> entry) {
        if (AriusObjUtils.isNull(entry.getValue().getAttributes())
                || AriusObjUtils.isNull(entry.getValue().getAttributes().getRack())) {
            racks.add("*");
        } else {
            racks.add(entry.getValue().getAttributes().getRack());
        }
    }
}
