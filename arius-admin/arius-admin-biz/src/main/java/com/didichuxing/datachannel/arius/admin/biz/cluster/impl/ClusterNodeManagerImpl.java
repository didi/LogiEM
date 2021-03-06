package com.didichuxing.datachannel.arius.admin.biz.cluster.impl;

import com.didichuxing.datachannel.arius.admin.biz.cluster.ClusterNodeManager;
import com.didichuxing.datachannel.arius.admin.client.bean.common.RackMetaMetric;
import com.didichuxing.datachannel.arius.admin.client.bean.common.Result;
import com.didichuxing.datachannel.arius.admin.client.bean.vo.cluster.ESRoleClusterHostVO;
import com.didichuxing.datachannel.arius.admin.client.constant.quota.NodeSpecifyEnum;
import com.didichuxing.datachannel.arius.admin.client.constant.quota.Resource;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ClusterLogic;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ClusterLogicRackInfo;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ecm.RoleClusterHost;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.region.ClusterRegion;
import com.didichuxing.datachannel.arius.admin.common.constant.AdminConstant;
import com.didichuxing.datachannel.arius.admin.common.util.AriusObjUtils;
import com.didichuxing.datachannel.arius.admin.common.util.ConvertUtil;
import com.didichuxing.datachannel.arius.admin.common.util.ListUtils;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.logic.ClusterLogicService;

import com.didichuxing.datachannel.arius.admin.core.service.cluster.physic.RoleClusterHostService;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.region.RegionRackService;
import com.didichuxing.datachannel.arius.admin.metadata.service.NodeStatisService;
import com.didiglobal.logi.log.ILog;
import com.didiglobal.logi.log.LogFactory;
import com.google.common.collect.*;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class ClusterNodeManagerImpl implements ClusterNodeManager {

    private static final ILog      LOGGER = LogFactory.getLog(ClusterNodeManager.class);

    @Autowired
    private NodeStatisService      nodeStatisService;

    @Autowired
    private RoleClusterHostService roleClusterHostService;

    @Autowired
    private ClusterLogicService    clusterLogicService;

    @Autowired
    private RegionRackService      regionRackService;

    /**
     * ????????????????????????
     *
     * @param clusterNodes       ??????????????????
     * @return
     */
    @Override
    public List<ESRoleClusterHostVO> convertClusterLogicNodes(List<RoleClusterHost> clusterNodes) {
        List<ESRoleClusterHostVO> result = Lists.newArrayList();

        List<ClusterLogicRackInfo> clusterRacks = regionRackService.listAllLogicClusterRacks();

        Multimap<String, ClusterLogic> rack2LogicClusters = getRack2LogicClusterMappings(clusterRacks,
                clusterLogicService.listAllClusterLogics());
        Map<String, ClusterLogicRackInfo> rack2ClusterRacks = getRack2ClusterRacks(clusterRacks);

        for (RoleClusterHost node : clusterNodes) {
            ESRoleClusterHostVO nodeVO = ConvertUtil.obj2Obj(node, ESRoleClusterHostVO.class);

            String clusterRack = createClusterRackKey(node.getCluster(), node.getRack());
            ClusterLogicRackInfo rackInfo = rack2ClusterRacks.get(clusterRack);
            if (rackInfo != null) {
                nodeVO.setRegionId(rackInfo.getRegionId());
            }

            Collection<ClusterLogic> clusterLogics = rack2LogicClusters.get(clusterRack);
            if (!CollectionUtils.isEmpty(clusterLogics)) {
                nodeVO.setLogicDepart(ListUtils.strList2String(clusterLogics.stream().map(ClusterLogic::getName).collect(Collectors.toList())));
            }

            result.add(nodeVO);
        }

        return result;
    }

    /**
     * ??????rack?????????????????????
     *
     * @param clusterName ????????????
     * @param racks       racks
     * @return list
     */
    @Override
    public Result<List<RackMetaMetric>> metaAndMetric(String clusterName, Collection<String> racks) {

        // ???ams??????rack??????????????????
        Result<List<RackMetaMetric>> result = nodeStatisService.getRackStatis(clusterName, racks);
        if (result.failed()) {
            return result;
        }

        List<RackMetaMetric> rackMetaMetrics = result.getData();
        if (racks.size() != rackMetaMetrics.size()) {
            LOGGER.warn("class=ClusterNodeManagerImpl||method=metaAndMetric||racksSize={}||resultSize={}", racks.size(), rackMetaMetrics.size());
        }

        // ????????????????????????????????????ECM???????????????datanode?????????
        Result<Resource> dataNodeSpecifyResult = getDataNodeSpecify();
        if (dataNodeSpecifyResult.failed()) {
            return Result.buildFrom(dataNodeSpecifyResult);
        }
        Resource dataNodeSpecify = dataNodeSpecifyResult.getData();

        // ??????????????????
        List<RoleClusterHost> clusterNodes = roleClusterHostService.getOnlineNodesByCluster(clusterName);
        // rack??????????????????map
        Multimap<String, RoleClusterHost> rack2ESClusterNodeMultiMap = ConvertUtil.list2MulMap(clusterNodes,
            RoleClusterHost::getRack);
        // rack???rack???????????????map
        Map<String, RackMetaMetric> rack2RackMetaMetricMap = ConvertUtil.list2Map(rackMetaMetrics,
            RackMetaMetric::getName);

        for (String rack : racks) {
            RackMetaMetric rackMetaMetric = rack2RackMetaMetricMap.get(rack);

            if (rackMetaMetric == null) {
                return Result.buildParamIllegal("AMS rack?????????????????????" + rack);
            }

            // rack????????????
            if (rack2ESClusterNodeMultiMap.containsKey(rackMetaMetric.getName())) {
                rackMetaMetric.setNodeCount(rack2ESClusterNodeMultiMap.get(rackMetaMetric.getName()).size());
            } else {
                LOGGER.warn("class=ClusterNodeManagerImpl||method=metaAndMetric||rack={}||msg=offline", rackMetaMetric.getName());
                rackMetaMetric.setNodeCount(0);
            }

            // rack???cpu???
            rackMetaMetric.setCpuCount(dataNodeSpecify.getCpu().intValue() * rackMetaMetric.getNodeCount());
            // rack?????????????????????
            rackMetaMetric.setTotalDiskG(dataNodeSpecify.getDisk() * 1.0 * rackMetaMetric.getNodeCount());
        }

        // ??????????????????????????????
        Result<Boolean> checkResult = checkRackMetrics(rackMetaMetrics, dataNodeSpecify);
        if (checkResult.failed()) {
            return Result.buildParamIllegal("AMS rack?????????????????????" + checkResult.getMessage());
        }

        return Result.buildSucc(rackMetaMetrics);
    }

    /**
     * ??????rack????????????
     *
     * @param clusterName ????????????
     * @param racks       rack
     * @return list
     */
    @Override
    public Result<List<RackMetaMetric>> meta(String clusterName, Collection<String> racks) {

        List<RackMetaMetric> rackMetas = Lists.newArrayList();
        Set<String> rackSet = Sets.newHashSet(racks);

        // ????????????????????????????????????ECM???????????????datano?????????
        Result<Resource> dataNodeSpecifyResult = getDataNodeSpecify();
        if (dataNodeSpecifyResult.failed()) {
            return Result.buildFrom(dataNodeSpecifyResult);
        }
        Resource dataNodeSpecify = dataNodeSpecifyResult.getData();

        // ??????????????????
        List<RoleClusterHost> clusterNodes = roleClusterHostService.getOnlineNodesByCluster(clusterName);
        // rack???rack????????????map
        Multimap<String, RoleClusterHost> rack2ESClusterNodeMultiMap = ConvertUtil.list2MulMap(clusterNodes,
            RoleClusterHost::getRack);

        // ??????rack
        for (Map.Entry<String, Collection<RoleClusterHost>> entry : rack2ESClusterNodeMultiMap.asMap().entrySet()) {
            if (!rackSet.contains(entry.getKey())) {
                continue;
            }
            RackMetaMetric rackMeta = new RackMetaMetric();
            rackMeta.setCluster(clusterName);
            rackMeta.setName(entry.getKey());
            // rack???????????????
            if (rack2ESClusterNodeMultiMap.containsKey(rackMeta.getName())) {
                rackMeta.setNodeCount(rack2ESClusterNodeMultiMap.get(rackMeta.getName()).size());
            } else {
                LOGGER.warn("class=ClusterNodeManagerImpl||method=meta||rack={}||msg=offline", entry.getKey());
                rackMeta.setNodeCount(0);
            }

            // rack??????CPU???
            rackMeta.setCpuCount(dataNodeSpecify.getCpu().intValue() * rackMeta.getNodeCount());
            // rack??????????????????
            rackMeta.setTotalDiskG(dataNodeSpecify.getDisk() * 1.0 * rackMeta.getNodeCount());
            rackMetas.add(rackMeta);
        }

        return Result.buildSucc(rackMetas);
    }

    @Override
    public List<ESRoleClusterHostVO> convertClusterPhyNodes(List<RoleClusterHost> roleClusterHosts,
                                                            String clusterPhyName) {
        List<ESRoleClusterHostVO> esRoleClusterHostVOS = ConvertUtil.list2List(roleClusterHosts,
            ESRoleClusterHostVO.class);

        //??????host??????regionId
        List<ClusterRegion> regions = regionRackService.listPhyClusterRegions(clusterPhyName);
        esRoleClusterHostVOS.forEach(esRoleClusterHostVO->{
            buildHostRegionIdAndLogicName(esRoleClusterHostVO, regions);
        });

        return esRoleClusterHostVOS;
    }

    /**************************************** private method ***************************************************/
    private Result<Resource> getDataNodeSpecify() {
        return Result.buildSucc(NodeSpecifyEnum.DOCKER.getResource());
    }

    private Result<Boolean> checkRackMetrics(List<RackMetaMetric> rackMetaMetrics, Resource dataNodeSpecify) {
        for (RackMetaMetric metaMetric : rackMetaMetrics) {
            if (AriusObjUtils.isNull(metaMetric.getName())) {
                return Result.buildParamIllegal("rack????????????");
            }

            if (AriusObjUtils.isNull(metaMetric.getDiskFreeG())) {
                return Result.buildParamIllegal(metaMetric.getName() + "diskFreeG??????");
            }

            if (metaMetric.getDiskFreeG() < 0
                || metaMetric.getDiskFreeG() > (dataNodeSpecify.getDisk() * metaMetric.getNodeCount())) {

                LOGGER.warn("class=ESClusterPhyRackStatisServiceImpl||method=checkTemplateMetrics||errMsg=diskFree??????"
                            + "||metaMetric={}||dataNodeSpecify={}",
                    metaMetric, dataNodeSpecify);

                return Result.buildParamIllegal(metaMetric.getName() + "???diskFreeG??????");
            }
        }

        return Result.buildSucc(true);
    }

    /**************************************** private method ***************************************************/
    /**
     * ??????Rack???????????????Rack????????????
     * @param clusterRacks ??????Rack??????
     * @return
     */
    private Map<String, ClusterLogicRackInfo> getRack2ClusterRacks(List<ClusterLogicRackInfo> clusterRacks) {
        Map<String, ClusterLogicRackInfo> rack2ClusterRacks = new HashMap<>(1);
        if (CollectionUtils.isNotEmpty(clusterRacks)) {
            for (ClusterLogicRackInfo rackInfo : clusterRacks) {
                rack2ClusterRacks.put(createClusterRackKey(rackInfo.getPhyClusterName(), rackInfo.getRack()), rackInfo);
            }
        }

        return rack2ClusterRacks;
    }

    /**
     * ??????????????????Rack???????????????????????????????????????Rack??????????????????????????????
     *
     * @param logicClusterRacks ????????????Racks
     * @param logicClusters     ??????????????????
     * @return
     */
    private Multimap<String, ClusterLogic> getRack2LogicClusterMappings(List<ClusterLogicRackInfo> logicClusterRacks,
                                                                        List<ClusterLogic> logicClusters) {

        Map<Long, ClusterLogic> logicClusterMappings = ConvertUtil.list2Map(logicClusters, ClusterLogic::getId);

        // ??????rack????????????????????????????????????
        Multimap<String, ClusterLogic> logicClusterId2RackInfoMap = ArrayListMultimap.create();
        for (ClusterLogicRackInfo rackInfo : logicClusterRacks) {
            List<Long> logicClusterIds = ListUtils.string2LongList(rackInfo.getLogicClusterIds());
            if (CollectionUtils.isEmpty(logicClusterIds)) {
                continue;
            }
            logicClusterIds.forEach(logicClusterId -> logicClusterId2RackInfoMap.put(createClusterRackKey(rackInfo.getPhyClusterName(),
                    rackInfo.getRack()), logicClusterMappings.get(logicClusterId)));
        }

        return logicClusterId2RackInfoMap;
    }

    /**
     * ????????????Rack?????????
     * @param cluster ????????????
     * @param rack Rack
     * @return
     */
    private String createClusterRackKey(String cluster, String rack) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.isNotBlank(cluster)) {
            builder.append(cluster);
        }

        builder.append(AdminConstant.CLUSTER_RACK_COMMA);

        if (StringUtils.isNotBlank(rack)) {
            builder.append(rack);
        }

        return builder.toString();
    }

    private void buildHostRegionIdAndLogicName(ESRoleClusterHostVO esRoleClusterHostVO, List<ClusterRegion> regions) {
        Map<Long/*regionId*/, List<String>/*racks*/> regionId2RacksMap = ConvertUtil.list2Map(regions,
                ClusterRegion::getId, region -> ListUtils.string2StrList(region.getRacks()));

        regionId2RacksMap.forEach((key, value) -> {
            if (value.contains(esRoleClusterHostVO.getRack())) {
                esRoleClusterHostVO.setRegionId(key);
                // ??????region??????data???????????????????????????????????????????????????host????????????
                buildHostLogicName(esRoleClusterHostVO, key);
            }
        });
    }

    private void buildHostLogicName(ESRoleClusterHostVO esRoleClusterHostVO, Long key) {
        ClusterRegion clusterRegion = regionRackService.getRegionById(key);
        if (clusterRegion == null) {
            LOGGER.error("class=ClusterNodeManagerImpl||method=buildHostRegionIdAndLogicName||errMsg=clusterRegion doesn't exit!");
            return;
        }

        List<Long> logicClusterIds = ListUtils.string2LongList(clusterRegion.getLogicClusterIds());
        // region???????????????????????????????????????????????????????????????
        if (CollectionUtils.isEmpty(logicClusterIds) ||
                logicClusterIds.get(0).equals(Long.parseLong(AdminConstant.REGION_NOT_BOUND_LOGIC_CLUSTER_ID))) {
            return;
        }

        //???????????????????????????????????????
        List<String> clusterLogicNames = logicClusterIds.stream()
                .map(logicClusterId -> clusterLogicService.getClusterLogicById(logicClusterId))
                .map(ClusterLogic::getName)
                .collect(Collectors.toList());

        esRoleClusterHostVO.setClusterLogicNames(ListUtils.strList2String(clusterLogicNames));
    }
}
