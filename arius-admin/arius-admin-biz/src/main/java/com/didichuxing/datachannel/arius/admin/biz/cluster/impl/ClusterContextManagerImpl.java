package com.didichuxing.datachannel.arius.admin.biz.cluster.impl;

import static com.didichuxing.datachannel.arius.admin.client.constant.resource.ESClusterNodeRoleEnum.DATA_NODE;
import static com.didichuxing.datachannel.arius.admin.client.constant.resource.ResourceLogicTypeEnum.*;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.apache.commons.collections4.CollectionUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.didichuxing.datachannel.arius.admin.biz.cluster.ClusterContextManager;
import com.didichuxing.datachannel.arius.admin.client.bean.common.Result;
import com.didichuxing.datachannel.arius.admin.client.constant.resource.ResourceLogicTypeEnum;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.app.App;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ClusterLogic;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ClusterLogicContext;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ClusterPhy;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ClusterPhyContext;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ecm.RoleClusterHost;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.region.ClusterRegion;
import com.didichuxing.datachannel.arius.admin.common.constant.AdminConstant;
import com.didichuxing.datachannel.arius.admin.common.threadpool.AriusScheduleThreadPool;
import com.didichuxing.datachannel.arius.admin.common.util.AriusObjUtils;
import com.didichuxing.datachannel.arius.admin.common.util.ConvertUtil;
import com.didichuxing.datachannel.arius.admin.common.util.FutureUtil;
import com.didichuxing.datachannel.arius.admin.common.util.ListUtils;
import com.didichuxing.datachannel.arius.admin.core.service.app.AppService;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.logic.ClusterLogicService;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.physic.ClusterPhyService;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.physic.RoleClusterHostService;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.region.RegionRackService;
import com.didiglobal.logi.log.ILog;
import com.didiglobal.logi.log.LogFactory;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * ??????????????????, ??????????????????:
 * 1. ??????????????????????????????????????????????????????????????????????????????region???rack???node???App????????????
 * 2. ????????????????????????????????????????????????????????????????????????
 * 3. ???????????? ????????????> ????????????????????????????????????????????????
 *
 * Created by linyunan on 2021-06-08
 */
@Service
public class ClusterContextManagerImpl implements ClusterContextManager {
    private static final ILog              LOGGER                           = LogFactory
        .getLog(ClusterContextManagerImpl.class);

    /**
     * key-> ????????????Id
     */
    private Map<Long, ClusterLogicContext> id2ClusterLogicContextMap        = Maps.newConcurrentMap();

    /**
     * key-> ??????????????????, value ???????????????
     */
    private Map<String, ClusterPhyContext> name2ClusterPhyContextMap        = Maps.newConcurrentMap();

    private static final Integer           LOGIC_ASSOCIATED_PHY_MAX_NUMBER  = 2 << 9;

    private static final Integer           PHY_ASSOCIATED_LOGIC_MAX_NUMBER  = 2 << 9;


    @Autowired
    private ClusterLogicService            clusterLogicService;

    @Autowired
    private ClusterPhyService              clusterPhyService;

    @Autowired
    private RegionRackService              regionRackService;

    @Autowired
    private RoleClusterHostService         roleClusterHostService;

    @Autowired
    private AriusScheduleThreadPool        ariusScheduleThreadPool;

    @Autowired
    private AppService                     appService;

    private FutureUtil<Void> loadClusterLogicContextFutureUtil = FutureUtil.init("loadClusterLogicContextFutureUtil",10,10,100);
    private FutureUtil<Void> loadClusterPhyContextFutureUtil   = FutureUtil.init("loadClusterPhyContextFutureUtil",10, 10,100);

    @PostConstruct
    private void init(){
        ariusScheduleThreadPool.submitScheduleAtFixedDelayTask(this::flushClusterLogicContexts, 60, 120);
        ariusScheduleThreadPool.submitScheduleAtFixedDelayTask(this::flushClusterPhyContexts, 120, 120);
    }

    @Override
    public ClusterPhyContext flushClusterPhyContext(String clusterPhyName) {
        try {
            ClusterPhyContext clusterPhyContext = getClusterPhyContext(clusterPhyName);
            if (null != clusterPhyContext) {
                name2ClusterPhyContextMap.put(clusterPhyContext.getClusterName(), clusterPhyContext);
                return clusterPhyContext;
            }
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            LOGGER.error("class=ClusterContextManagerImpl||method=flushClusterPhyContext||clusterPhyName={}||errMsg={}",
                clusterPhyName, e.getMessage(), e);
        }

        return null;
    }

    @Override
    public ClusterLogicContext flushClusterLogicContext(Long clusterLogicId) {
        try {
            ClusterLogicContext clusterLogicContext = getClusterLogicContext(clusterLogicId);
            if (null != clusterLogicContext) {
                id2ClusterLogicContextMap.put(clusterLogicContext.getClusterLogicId(), clusterLogicContext);
                return clusterLogicContext;
            }

        } catch (Exception e) {
            Thread.currentThread().interrupt();
            LOGGER.error(
                "class=ClusterContextManagerImpl||method=flushClusterLogicContext||clusterLogicId={}||errMsg={}",
                clusterLogicId, e.getMessage(), e);

        }

        return null;
    }

    @Override
    public void flushClusterContextByClusterRegion(ClusterRegion clusterRegion) {
        if (null == clusterRegion) {
            return;
        }

        flushClusterPhyContext(clusterRegion.getPhyClusterName());

        // ????????????????????????????????????????????????
        List<Long> logicClusterIds = ListUtils.string2LongList(clusterRegion.getLogicClusterIds());
        if (!CollectionUtils.isEmpty(logicClusterIds)) {
            logicClusterIds.forEach(this::flushClusterLogicContext);
        }
    }

    @Override
    public Result<Boolean> canClusterLogicAssociatedPhyCluster(Long clusterLogicId, String clusterPhyName,
                                                               Long regionId, Integer clusterLogicType) {
        //?????????clusterLogicId??????, ??????NPE
        if (AriusObjUtils.isNull(clusterLogicId)) {
            clusterLogicId = -1L;
        }

        ClusterLogicContext clusterLogicContext = getClusterLogicContext(clusterLogicId);
        ClusterPhyContext   clusterPhyContext   = getClusterPhyContext(clusterPhyName);

        int associatedPhyNum = 0;
        int associatedLogicNum = 0;
        if (null != clusterLogicContext) {
            associatedPhyNum = clusterLogicContext.getAssociatedPhyNum();
        }
        if (null != clusterPhyContext) {
            associatedLogicNum = clusterPhyContext.getAssociatedLogicNum();
        }

        return doValid(associatedPhyNum, associatedLogicNum, clusterLogicId, clusterPhyName, regionId, clusterLogicType);
    }

    /**
     *   1. Type?????????, LP = 1, PL = 1
     *   2. Type?????????, LP = n, PL = 1
     * 	 3. Type?????????, LP = n, PL = 1
     */
    @Override
    public Result<List<String>> getCanBeAssociatedClustersPhys(Integer clusterLogicType, Long clusterLogicId) {
        if (!ResourceLogicTypeEnum.isExist(clusterLogicType)) {
            return Result.buildParamIllegal("????????????????????????");
        }

        List<String> canBeAssociatedClustersPhyNames = Lists.newArrayList();

        if (PRIVATE.getCode() == clusterLogicType) {
            handleClusterLogicTypePrivate(clusterLogicId, canBeAssociatedClustersPhyNames);
        }

        if (PUBLIC.getCode() == clusterLogicType) {
            handleClusterLogicTypePublic(clusterLogicId, canBeAssociatedClustersPhyNames);
        }

        if (EXCLUSIVE.getCode() == clusterLogicType) {
            handleClusterLogicTypeExclusive(clusterLogicId, canBeAssociatedClustersPhyNames);
        }

        return Result.buildSucc(canBeAssociatedClustersPhyNames);
    }

    @Override
    public List<String> getClusterPhyAssociatedClusterLogicNames(String clusterPhyName) {
        ClusterPhyContext clusterPhyContext = getClusterPhyContext(clusterPhyName);
        if (null == clusterPhyContext) {
            return Lists.newArrayList();
        }

        List<Long> clusterLogicIds = clusterPhyContext.getAssociatedClusterLogicIds();
        if (CollectionUtils.isEmpty(clusterLogicIds)) {
            return Lists.newArrayList();
        }

        return clusterLogicIds.stream()
                .map(r -> clusterLogicService.getClusterLogicById(r))
                .map(ClusterLogic::getName)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public ClusterPhyContext getClusterPhyContext(String clusterPhyName) {
        ClusterPhy clusterPhy = clusterPhyService.getClusterByName(clusterPhyName);
        if (null == clusterPhy) {
            LOGGER.error(
                    "class=ClusterContextManagerImpl||method=flushClusterPhyContext||clusterPhyName={}||msg=clusterPhy is empty",
                    clusterPhyName);
            return null;
        }

        ClusterPhyContext build = ClusterPhyContext.builder()
                .clusterPhyId(clusterPhy.getId().longValue())
                .clusterName(clusterPhy.getCluster())
                .associatedLogicNumMax(PHY_ASSOCIATED_LOGIC_MAX_NUMBER)
                .build();

        setClusterPhyNodeInfo(build);
        setRegionAndClusterLogicInfoAndAppId(build);
        return build;
    }

    @Override
    public ClusterPhyContext getClusterPhyContextCache(String cluster) {
        return name2ClusterPhyContextMap.get(cluster);
    }

    @Override
    public Map<String, ClusterPhyContext> listClusterPhyContextMap() {
        return name2ClusterPhyContextMap;
    }

    @Override
    public ClusterLogicContext getClusterLogicContextCache(Long clusterLogicId) {
        return id2ClusterLogicContextMap.get(clusterLogicId);
    }

    @Override
    public ClusterLogicContext getClusterLogicContext(Long clusterLogicId) {
        ClusterLogic clusterLogic = clusterLogicService.getClusterLogicById(clusterLogicId);
        if (null == clusterLogic) {
            LOGGER.error(
                    "class=ClusterContextManagerImpl||method=flushClusterLogicContext||clusterLogicId={}||msg=clusterLogic is empty",
                    clusterLogicId);
            return null;
        }

        ClusterLogicContext build = buildInitESClusterLogicContextByType(clusterLogic);
        setAssociatedClusterPhyInfo(build);
        setRegionAndAssociatedClusterPhyDataNodeInfo(build);
        return build;
    }
    /***********************************************private*********************************************/

    private void flushClusterPhyContexts() {
        LOGGER.info("class=ClusterContextManagerImpl||method=flushClusterPhyContexts||msg=start...");
        long currentTimeMillis = System.currentTimeMillis();
        List<ClusterPhy> clusterPhyList = clusterPhyService.listAllClusters();
        if (CollectionUtils.isEmpty(clusterPhyList)) {
            LOGGER.info("class=ClusterContextManagerImpl||method=flushClusterLogicContexts||msg=finish...||consumingTime={}",
                    System.currentTimeMillis() - currentTimeMillis);
            return;
        }

        // regionk????????????cluster?????????
        List<ClusterRegion> clusterRegionList = regionRackService.listAllBoundRegions();
        Map<String/*phyClusterName*/, List<ClusterRegion>> phyClusterName2ClusterLogicRackListMap = ConvertUtil.
                list2MapOfList(clusterRegionList, ClusterRegion::getPhyClusterName, ClusterRegion -> ClusterRegion);

        // clusterLogic?????????????????????
        List<ClusterLogic> clusterLogicList = clusterLogicService.listAllClusterLogics();
        Map<Long, ClusterLogic> id2ClusterLogicMap = ConvertUtil.list2Map(clusterLogicList, ClusterLogic::getId);

        // host????????????cluster?????????
        List<RoleClusterHost>      roleClusterHosts = roleClusterHostService.listAllNode();
        Map<String, List<RoleClusterHost>> cluster2RoleListMap = ConvertUtil.list2MapOfList(roleClusterHosts, 
                RoleClusterHost::getCluster, RoleClusterHost -> RoleClusterHost);

        // app????????????
        List<App> apps = appService.listApps();
        Map<Integer/*appId*/, String/*appName*/> appId2AppNameMap = ConvertUtil.list2Map(apps, App::getId, App::getName);

        for (ClusterPhy phy : clusterPhyList) {
            // ?????????
            ClusterPhyContext clusterPhyContext = ClusterPhyContext.builder()
                    .clusterPhyId(phy.getId().longValue())
                    .clusterName(phy.getCluster())
                    .associatedLogicNumMax(PHY_ASSOCIATED_LOGIC_MAX_NUMBER)
                    .build();

            List<RoleClusterHost> hostList = cluster2RoleListMap.get(phy.getCluster());
            if (CollectionUtils.isEmpty(hostList)) {
                name2ClusterPhyContextMap.put(phy.getCluster(), clusterPhyContext);
                continue;
            }

            // ???????????????????????????host??????, ????????????????????????????????????es???????????????
            List<RoleClusterHost> dataNodes = hostList.stream().filter(r -> DATA_NODE.getCode() == r.getRole())
                    .collect(Collectors.toList());

            clusterPhyContext.setAssociatedDataNodeNum(dataNodes.size());
            clusterPhyContext.setAssociatedDataNodeIps(dataNodes.stream().map(RoleClusterHost::getIp).collect(Collectors.toList()));
            clusterPhyContext.setAssociatedNodeIps(hostList.stream().map(RoleClusterHost::getIp).collect(Collectors.toList()));
            clusterPhyContext.setAssociatedRacks(
                dataNodes.stream().map(RoleClusterHost::getRack).distinct().collect(Collectors.toList()));

            // ??????region??????
            List<ClusterRegion> clusterRegions = phyClusterName2ClusterLogicRackListMap.get(phy.getCluster());
            if (CollectionUtils.isEmpty(clusterRegions)) {
                name2ClusterPhyContextMap.put(phy.getCluster(), clusterPhyContext);
                continue;
            }

            clusterPhyContext.setAssociatedRegionIds(clusterRegions.stream().map(ClusterRegion::getId).collect(Collectors.toList()));

            // ??????????????????????????????
            List<String> associatedClusterLogicIdsStr = clusterRegions.stream()
                    .filter(r -> !AdminConstant.REGION_NOT_BOUND_LOGIC_CLUSTER_ID.equals(r.getLogicClusterIds()))
                    .map(ClusterRegion::getLogicClusterIds).distinct().collect(Collectors.toList());
            Set<Long> associatedClusterLogicIds = Sets.newHashSet();
            for (String associatedClusterLogicIdStr : associatedClusterLogicIdsStr) {
                associatedClusterLogicIds.addAll(ListUtils.string2LongList(associatedClusterLogicIdStr));
            }
            clusterPhyContext.setAssociatedClusterLogicIds(Lists.newArrayList(associatedClusterLogicIds));
            clusterPhyContext.setAssociatedLogicNum(associatedClusterLogicIds.size());

            // ??????app??????
            Set<Integer> appIdSet  = Sets.newHashSet();
            Set<String> appNameSet = Sets.newHashSet();
            for (Long associatedClusterLogicId : associatedClusterLogicIds) {
                ClusterLogic clusterLogic = id2ClusterLogicMap.get(associatedClusterLogicId);
                if (null == clusterLogic) { continue;}
                appIdSet.add(clusterLogic.getAppId());

                String appName = appId2AppNameMap.get(clusterLogic.getAppId());
                if (AriusObjUtils.isBlack(appName)) { continue;}
                appNameSet.add(appName);
            }
            clusterPhyContext.setAssociatedAppIds(Lists.newArrayList(appIdSet));
            clusterPhyContext.setAssociatedAppNames(Lists.newArrayList(appNameSet));

            name2ClusterPhyContextMap.put(phy.getCluster(), clusterPhyContext);
        }
        LOGGER.info("class=ClusterContextManagerImpl||method=flushClusterPhyContexts||msg=finish...||consumingTime={}",
                System.currentTimeMillis() - currentTimeMillis);
    }

    /**
     * ?????????????????????????????????????????? ?????????????????????????????? region????????? host?????????
     */
    private void flushClusterLogicContexts() {
        LOGGER.info("class=ClusterContextManagerImpl||method=flushClusterLogicContexts||msg=start...");
        long currentTimeMillis = System.currentTimeMillis();

        List<ClusterLogic> clusterLogics = clusterLogicService.listAllClusterLogics();
        if (CollectionUtils.isEmpty(clusterLogics)) {
            LOGGER.info("class=ClusterContextManagerImpl||method=flushClusterLogicContexts||msg=finish...||consumingTime={}",
                    System.currentTimeMillis() - currentTimeMillis);
            return;
        }

        // ?????????????????????????????????Region??????
        Map<Long, List<ClusterRegion>> clusterLogicId2ClusterLogicRackListMap = getClusterLogicId2ClusterRegionListMap();

        // host????????????cluster@rack?????????
        Map<String, List<RoleClusterHost>> phyRack2HostListMap = getPhyRack2HostListMap();

        for (ClusterLogic clusterLogic : clusterLogics) {
            // ????????????????????????, ???????????????????????????????????????????????????
            ClusterLogicContext clusterLogicContext = buildInitESClusterLogicContextByType(clusterLogic);

            // ????????????????????????
            List<ClusterRegion> clusterRegions = clusterLogicId2ClusterLogicRackListMap.get(clusterLogic.getId());
            if (CollectionUtils.isEmpty(clusterRegions)) {
                id2ClusterLogicContextMap.put(clusterLogic.getId(), clusterLogicContext);
                continue;
            }

            List<String> associatedClusterPhyNameList = clusterRegions.stream().
                    map(ClusterRegion::getPhyClusterName).distinct().collect(Collectors.toList());

            clusterLogicContext.setAssociatedClusterPhyNames(associatedClusterPhyNameList);
            clusterLogicContext.setAssociatedPhyNum(associatedClusterPhyNameList.size());
            // ???????????????????????????????????????????????????
            if (clusterLogicContext.getAssociatedPhyNumMax() < associatedClusterPhyNameList.size()) {
                LOGGER.error("class=ClusterContextManagerImpl||method=flushClusterLogicContexts"
                                + "||logicClusterType={}||esClusterLogicId={}||msg=????????????????????????????????????{}, ?????????",
                        clusterLogicContext.getLogicClusterType(), clusterLogicContext.getClusterLogicId(),
                        clusterLogicContext.getAssociatedPhyNumMax());
            }

            // ??????????????????????????????Region??????
            clusterLogicContext.setAssociatedRegionIds(clusterRegions.stream().map(ClusterRegion::getId).collect(Collectors.toList()));

            // ????????????????????????region??????rack????????????
            List<RoleClusterHost> associatedRackClusterHosts = Lists.newArrayList();
            for (ClusterRegion clusterRegion : clusterRegions) {
                List<String> rackList = ListUtils.string2StrList(clusterRegion.getRacks());
                for (String rack : rackList) {
                    List<RoleClusterHost> associatedHosts = phyRack2HostListMap.get(clusterRegion.getPhyClusterName() + "@" + rack);
                    if (CollectionUtils.isEmpty(associatedHosts)) { continue;}

                    associatedRackClusterHosts.addAll(associatedHosts);
                }
            }

            //????????????????????????
            clusterLogicContext.setAssociatedDataNodeNum(associatedRackClusterHosts.size());

            //??????????????????Ip??????
            clusterLogicContext.setAssociatedDataNodeIps(associatedRackClusterHosts.stream().map(RoleClusterHost::getIp)
                    .collect(Collectors.toList()));

            id2ClusterLogicContextMap.put(clusterLogic.getId(), clusterLogicContext);
        }

        LOGGER.info("class=ClusterContextManagerImpl||method=flushClusterLogicContexts||msg=finish...||consumingTime={}",
                System.currentTimeMillis() - currentTimeMillis);
    }

    /**
     * ?????????????????????????????????Region??????
     * @return  key -> clusterLogicId   value -> List<ClusterRegion>
     */
    @NotNull
    private Map<Long, List<ClusterRegion>> getClusterLogicId2ClusterRegionListMap() {
        // Rack????????????????????????Ids?????????
        List<ClusterRegion> clusterRegionList = regionRackService.listAllBoundRegions();
        Map<String/*clusterLogicIds ????????????*/, List<ClusterRegion>> clusterLogicIds2ClusterLogicRackListMap = ConvertUtil.
                list2MapOfList(clusterRegionList, ClusterRegion::getLogicClusterIds, ClusterRegion -> ClusterRegion);

        // Rack????????????????????????Id?????????, ????????????table???clusterLogicIds????????????
        Map<Long/*clusterLogicId*/, List<ClusterRegion>> clusterLogicId2ClusterLogicRackListMap = Maps.newHashMap();
        for (Map.Entry<String, List<ClusterRegion>> e : clusterLogicIds2ClusterLogicRackListMap.entrySet()) {
            String key                             = e.getKey();
            List<ClusterRegion> clusterRegions     = e.getValue();
            List<Long>          clusterLogicIdList = ListUtils.string2LongList(key);
            for (Long clusterLogicId : clusterLogicIdList) {
                clusterLogicId2ClusterLogicRackListMap.put(clusterLogicId, clusterRegions);
            }
        }
        return clusterLogicId2ClusterLogicRackListMap;
    }

    /**
     * host????????????cluster@rack?????????
     * @return key -> cluster@rack  value -> List<RoleClusterHost>
     */
    @NotNull
    private Map<String, List<RoleClusterHost>> getPhyRack2HostListMap() {
        List<RoleClusterHost>      roleClusterHosts = roleClusterHostService.listAllNode();
        return ConvertUtil.list2MapOfList(roleClusterHosts,
                host -> host.getCluster() + "@" + host.getRack(), RoleClusterHost -> RoleClusterHost);
    }

    /**
     *   ????????????:
     *   1. Type?????????, LP = 1, PL = 1
     *   2. Type?????????, LP = n, PL = 1 ,  1 <= n <= 1024   ??????????????????????????????????????????, ?????????????????????????????????????????????region,??????????????????????????????
     *                                                    ???????????????????????????????????????region???
     *   3. Type?????????, LP = 1, PL = n ,  1 <= n <= 1024   ??????????????????????????????????????????????????????
     */
    private ClusterLogicContext buildInitESClusterLogicContextByType(ClusterLogic clusterLogic) {

        if (PRIVATE.getCode() == clusterLogic.getType() || EXCLUSIVE.getCode() == clusterLogic.getType()) {
            return ClusterLogicContext.builder()
                    .clusterLogicName(clusterLogic.getName())
                    .clusterLogicId(clusterLogic.getId())
                    .logicClusterType(clusterLogic.getType())
                    .associatedPhyNumMax(1)
                    .build();
        } else if (PUBLIC.getCode() == clusterLogic.getType()) {
            return ClusterLogicContext.builder()
                    .clusterLogicName(clusterLogic.getName())
                    .clusterLogicId(clusterLogic.getId())
                    .logicClusterType(clusterLogic.getType())
                    .associatedPhyNumMax(LOGIC_ASSOCIATED_PHY_MAX_NUMBER)
                    .build();
        } else {
            LOGGER.error(
                "class=ClusterContextManagerImpl||method=buildInitESClusterLogicContextByType||esClusterLogicId={}||msg={}",
                    clusterLogic.getId(), String.format("?????????????????????%s??????????????????", clusterLogic.getType()));

            return ClusterLogicContext.builder()
                    .clusterLogicName(clusterLogic.getName())
                    .clusterLogicId(clusterLogic.getId())
                    .logicClusterType(clusterLogic.getType())
                    .associatedPhyNumMax(-1)
                    .build();
        }
    }

    private void setAssociatedClusterPhyInfo(ClusterLogicContext build) {
        List<String> clusterPhyNames = regionRackService.listPhysicClusterNames(build.getClusterLogicId());
        if (build.getAssociatedPhyNumMax() < clusterPhyNames.size()) {
            LOGGER.error("class=ClusterContextManagerImpl||method=setAssociatedClusterPhyInfo"
                         + "||logicClusterType={}||esClusterLogicId={}||msg=????????????????????????????????????{}, ?????????",
                build.getLogicClusterType(), build.getClusterLogicId(), build.getAssociatedPhyNumMax());
            return;
        }

        build.setAssociatedClusterPhyNames(clusterPhyNames);
        build.setAssociatedPhyNum(clusterPhyNames.size());
    }

    private void setRegionAndAssociatedClusterPhyDataNodeInfo(ClusterLogicContext build) {
        //??????????????????????????????Region??????
        List<ClusterRegion> regions = regionRackService.listLogicClusterRegions(build.getClusterLogicId());
        build.setAssociatedRegionIds(regions.stream().map(ClusterRegion::getId).collect(Collectors.toList()));

        //????????????????????????region??????rack????????????
        List<RoleClusterHost> associatedRackClusterHosts = Lists.newArrayList();
        for (ClusterRegion region : regions) {
            List<RoleClusterHost> roleClusterHosts = roleClusterHostService.listRacksNodes(region.getPhyClusterName(), region.getRacks());
            associatedRackClusterHosts.addAll(roleClusterHosts);
        }

        //????????????????????????
        build.setAssociatedDataNodeNum(associatedRackClusterHosts.size());

        //??????????????????Ip??????
        build.setAssociatedDataNodeIps(associatedRackClusterHosts.stream().map(RoleClusterHost::getIp).collect(Collectors.toList()));
    }

    private void setRegionAndClusterLogicInfoAndAppId(ClusterPhyContext build) {
        // 1. set region
        List<ClusterRegion> regions = regionRackService.listPhyClusterRegions(build.getClusterName());
        build.setAssociatedRegionIds(regions.stream().map(ClusterRegion::getId).collect(Collectors.toList()));

        // 2. set ClusterLogicInfo
        Set<Long> associatedClusterLogicIds = Sets.newHashSet();
        for (ClusterRegion clusterRegion : regions) {
            // ???????????????????????????????????????region???????????????????????????
            List<Long> logicClusterIds = ListUtils.string2LongList(clusterRegion.getLogicClusterIds());
               if(!CollectionUtils.isEmpty(logicClusterIds)
                    && !logicClusterIds.contains(Long.parseLong(AdminConstant.REGION_NOT_BOUND_LOGIC_CLUSTER_ID))) {
                associatedClusterLogicIds.addAll(logicClusterIds);
            }
        }

        build.setAssociatedClusterLogicIds(Lists.newArrayList(associatedClusterLogicIds));
        build.setAssociatedLogicNum(associatedClusterLogicIds.size());

        // 3. set appId
        Set<Integer> appIdSet   = new HashSet<>();
        Set<String>  appNameSet = new HashSet<>();
        if (!CollectionUtils.isEmpty(associatedClusterLogicIds)) {
            for (Long associatedClusterLogicId : associatedClusterLogicIds) {
                ClusterLogic clusterLogic = clusterLogicService.getClusterLogicById(associatedClusterLogicId);
                if (null != clusterLogic && null != clusterLogic.getAppId() && null != clusterLogic.getName()) {
                    appIdSet.add(clusterLogic.getAppId());
                    appNameSet.add(clusterLogic.getName());
                }
            }
        }

        build.setAssociatedAppIds(Lists.newArrayList(appIdSet));
        build.setAssociatedAppNames(Lists.newArrayList(appNameSet));
    }

    private void setClusterPhyNodeInfo(ClusterPhyContext build) {
        List<RoleClusterHost> nodes = roleClusterHostService.getNodesByCluster(build.getClusterName());
        List<RoleClusterHost> dataNodes = nodes.stream().filter(r -> DATA_NODE.getCode() == r.getRole())
            .collect(Collectors.toList());

        build.setAssociatedDataNodeNum(dataNodes.size());
        build.setAssociatedDataNodeIps(dataNodes.stream().map(RoleClusterHost::getIp).collect(Collectors.toList()));
        build.setAssociatedNodeIps(nodes.stream().map(RoleClusterHost::getIp).collect(Collectors.toList()));
        build.setAssociatedRacks(
            dataNodes.stream().map(RoleClusterHost::getRack).distinct().collect(Collectors.toList()));
    }

    /**
     * ??????????????????
     * @param associatedPhyNumber    ????????????????????????????????????
     * @param associatedLogicNumber  ????????????????????????????????????
     * @param regionId               ???????????????regionId
     * @param clusterLogicType       ??????????????????
     */
    private Result<Boolean> doValid(int associatedPhyNumber, int associatedLogicNumber, Long clusterLogicId,
                                    String clusterPhyName, Long regionId, Integer clusterLogicType) {

        if (AriusObjUtils.isNull(clusterLogicType)) {
            return Result.buildParamIllegal("????????????????????????");
        }

        if (UNKNOWN.getCode() == ResourceLogicTypeEnum.valueOf(clusterLogicType).getCode()) {
            return Result.buildParamIllegal("??????????????????????????????");
        }

        ClusterPhy clusterPhy = clusterPhyService.getClusterByName(clusterPhyName);
        if (AriusObjUtils.isNull(clusterPhy)) {
            return Result.buildFail("?????????????????????");
        }

        if (PRIVATE.getCode() == clusterLogicType
            && !canClusterLogicBoundRegion(regionId, clusterPhyName, clusterLogicId)) {
            //?????????logic -> phy, ????????????region??????????????????????????????????????????????????????????????????
            if (associatedPhyNumber > 0) {
                return Result.buildParamIllegal(String.format("????????????????????? ,?????????????????????%s????????????????????????", clusterLogicId));
            }
            //?????????phy -> logic
            if (associatedLogicNumber > 0) {
                return Result.buildFail(String.format("?????????????????????, ????????????%s????????????????????????", clusterPhyName));
            }
        }

        return Result.buildSucc(Boolean.TRUE);
    }

    /**
     * ?????????????????????????????????region????????????????????????????????????
     * @param regionId regionId
     * @param clusterPhyName ??????????????????
     * @param clusterLogicId ????????????id
     * @return
     */
    private boolean canClusterLogicBoundRegion(Long regionId, String clusterPhyName, Long clusterLogicId) {
        ClusterRegion region                =  regionRackService.getRegionById(regionId);
        ClusterPhyContext clusterPhyContext =  getClusterPhyContext(clusterPhyName);
        List<Long> clusterLogicIds          =  clusterPhyContext.getAssociatedClusterLogicIds();
        if (CollectionUtils.isNotEmpty(clusterLogicIds) && !clusterLogicIds.contains(clusterLogicId)) {
            return false;
        }

        if (!region.getLogicClusterIds().equals(AdminConstant.REGION_NOT_BOUND_LOGIC_CLUSTER_ID)) {
            return false;
        }

        return region.getPhyClusterName().equals(clusterPhyName);
    }

    /**
     * LP = 1 , PL = 1
     * @param clusterLogicId    ????????????Id
     * @param clusterPhyContext ?????????????????????
     * @return                  true/false
     */
    private boolean checkForPrivate(Long clusterLogicId, ClusterPhyContext clusterPhyContext) {
        //?????????associatedLogicNumber ?????????null??????
        if (null == clusterPhyContext.getAssociatedLogicNum()) {
            return true;
        }
        if (null == clusterLogicId) {
            if (0 == clusterPhyContext.getAssociatedLogicNum()) {
                return true;
            } else if (clusterPhyContext.getAssociatedLogicNum() >= 1) {
                return false;
            }

            return false;
        } else {
            ClusterLogicContext clusterLogicContext = getClusterLogicContext(clusterLogicId);
            if (0 == clusterLogicContext.getAssociatedPhyNum() && 0 == clusterPhyContext.getAssociatedLogicNum()) {
                return true;
            } else {
                return 1 == clusterLogicContext.getAssociatedPhyNum() && clusterLogicContext
                    .getAssociatedClusterPhyNames().contains(clusterPhyContext.getClusterName());
            }
        }
    }

    /**
     * ??????????????????????????????????????????????????????????????????????????????
     * @param clusterLogicId    ????????????Id
     * @param clusterPhyContext ?????????????????????
     * @return                  true/false
     */
    private boolean checkForPublic(Long clusterLogicId, ClusterPhyContext clusterPhyContext) {
        //?????????associatedLogicNumber ?????????null??????
        if (null == clusterPhyContext.getAssociatedLogicNum()) {
            return true;
        }
        if (null == clusterLogicId) {
            if (0 == clusterPhyContext.getAssociatedLogicNum()) {
                return true;
            } else {
                return !hasClusterPhyContextAssociatedLogicTypeIsCode(clusterPhyContext, PRIVATE.getCode());
            }
        } else {
            ClusterLogicContext clusterLogicContext = id2ClusterLogicContextMap.get(clusterLogicId);
            //???????????????????????????????????????????????????
            if (1 < clusterLogicContext.getAssociatedPhyNum()) {
                return false;
            }

            //????????????????????????????????????0 ??? ?????????????????????????????????
            if (0 == clusterLogicContext.getAssociatedPhyNum() && 0 == clusterPhyContext.getAssociatedLogicNum()) {
                return true;
            } else {
                return clusterLogicContext.getAssociatedPhyNum() == 1
                        && hasBelongClusterLogicContextAssociatedClusterNames(clusterLogicContext, clusterPhyContext.getClusterName())
                        || clusterPhyContext.getAssociatedLogicNum() > 0
                        && !hasClusterPhyContextAssociatedLogicTypeIsCode(clusterPhyContext, PRIVATE.getCode());
            }
        }
    }

    /**
     * ??????????????????????????????????????????????????????????????????????????????
     * @param clusterLogicId    ????????????Id
     * @param clusterPhyContext ?????????????????????
     * @return                  true/false
     */
    private  boolean checkForExclusive(Long clusterLogicId, ClusterPhyContext clusterPhyContext) {
        //?????????associatedLogicNumber ?????????null??????
        if (null == clusterPhyContext.getAssociatedLogicNum()) {
            return true;
        }
        if (null == clusterLogicId) {
            if (0 == clusterPhyContext.getAssociatedLogicNum()) {
                return true;
            } else {
                return !hasClusterPhyContextAssociatedLogicTypeIsCode(clusterPhyContext, PRIVATE.getCode());
            }
        } else {
            ClusterLogicContext clusterLogicContext = id2ClusterLogicContextMap.get(clusterLogicId);
            //???????????????????????????????????????????????????
            if (1 < clusterLogicContext.getAssociatedPhyNum()) {
                return false;
            }

            //????????????????????????????????????0 ??? ?????????????????????????????????
            if (0 == clusterLogicContext.getAssociatedPhyNum() && 0 == clusterPhyContext.getAssociatedLogicNum()) {
                return true;
            } else {
                return clusterLogicContext.getAssociatedPhyNum() == 1
                        && hasBelongClusterLogicContextAssociatedClusterNames(clusterLogicContext, clusterPhyContext.getClusterName())
                        || clusterPhyContext.getAssociatedLogicNum() > 0
                        && !hasClusterPhyContextAssociatedLogicTypeIsCode(clusterPhyContext, PRIVATE.getCode());
            }
        }
    }

    /**
     * ?????????????????????????????????????????????????????????????????????
     * @param clusterPhyContext  ?????????????????????
     * @param code               ??????????????????
     * @return                   true/false
     */
    private boolean hasClusterPhyContextAssociatedLogicTypeIsCode(ClusterPhyContext clusterPhyContext, int code) {
        Set<Integer> typeSet = clusterPhyContext.getAssociatedClusterLogicIds()
                .stream()
                .map(this::getClusterLogicContext)
                .map(ClusterLogicContext::getLogicClusterType)
                .collect(Collectors.toSet());

        return 1 == typeSet.size() && typeSet.contains(code);
    }

    /**
     * ???????????????????????????????????????????????????
     * @param clusterLogicContext  ?????????????????????
     * @param clusterName          ??????????????????
     * @return                     true/false
     */
    private boolean hasBelongClusterLogicContextAssociatedClusterNames(ClusterLogicContext clusterLogicContext,
                                                                       String clusterName) {
        return clusterLogicContext.getAssociatedClusterPhyNames().contains(clusterName);
    }

    private void handleClusterLogicTypeExclusive(Long clusterLogicId, List<String> canBeAssociatedClustersPhyNames) {
        if (hasClusterLogicContextMapEmpty()) { return; }
        if (hasClusterPhyContextMapEmpty())   { return; }

        for (ClusterPhyContext clusterPhyContext : name2ClusterPhyContextMap.values()) {
            if (checkForExclusive(clusterLogicId, clusterPhyContext)) {
                canBeAssociatedClustersPhyNames.add(clusterPhyContext.getClusterName());
            }
        }
    }

    private void handleClusterLogicTypePublic(Long clusterLogicId, List<String> canBeAssociatedClustersPhyNames) {
        if (hasClusterLogicContextMapEmpty()) { return; }
        if (hasClusterPhyContextMapEmpty())   { return; }

        for (ClusterPhyContext clusterPhyContext : name2ClusterPhyContextMap.values()) {
            if (checkForPublic(clusterLogicId, clusterPhyContext)) {
                canBeAssociatedClustersPhyNames.add(clusterPhyContext.getClusterName());
            }
        }
    }

    private void handleClusterLogicTypePrivate(Long clusterLogicId, List<String> canBeAssociatedClustersPhyNames) {
        if (hasClusterLogicContextMapEmpty()) { return; }
        if (hasClusterPhyContextMapEmpty())   { return; }

        for (ClusterPhyContext clusterPhyContext : name2ClusterPhyContextMap.values()) {
            if (checkForPrivate(clusterLogicId, clusterPhyContext)) {
                canBeAssociatedClustersPhyNames.add(clusterPhyContext.getClusterName());
            }
        }
    }

    private boolean hasClusterPhyContextMapEmpty() {
        return name2ClusterPhyContextMap.isEmpty();
    }

    private boolean hasClusterLogicContextMapEmpty() {
        return id2ClusterLogicContextMap.isEmpty();
    }
}
