package com.didichuxing.datachannel.arius.admin.biz.cluster.impl;

import java.util.*;
import java.util.stream.Collectors;

import com.didichuxing.datachannel.arius.admin.biz.app.AppClusterPhyAuthManager;
import com.didichuxing.datachannel.arius.admin.biz.cluster.ClusterContextManager;
import com.didichuxing.datachannel.arius.admin.biz.cluster.ClusterLogicManager;
import com.didichuxing.datachannel.arius.admin.biz.cluster.ClusterNodeManager;
import com.didichuxing.datachannel.arius.admin.biz.cluster.ClusterPhyManager;
import com.didichuxing.datachannel.arius.admin.biz.page.ClusterPhyPageSearchHandle;
import com.didichuxing.datachannel.arius.admin.biz.template.TemplatePhyManager;
import com.didichuxing.datachannel.arius.admin.biz.template.srv.TemplateSrvManager;
import com.didichuxing.datachannel.arius.admin.biz.template.srv.mapping.TemplatePhyMappingManager;
import com.didichuxing.datachannel.arius.admin.biz.template.srv.pipeline.TemplatePipelineManager;
import com.didichuxing.datachannel.arius.admin.client.bean.common.PaginationResult;
import com.didichuxing.datachannel.arius.admin.client.bean.common.Result;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.cluster.*;
import com.didichuxing.datachannel.arius.admin.client.bean.vo.cluster.*;
import com.didichuxing.datachannel.arius.admin.client.constant.app.AppClusterLogicAuthEnum;
import com.didichuxing.datachannel.arius.admin.client.constant.app.AppClusterPhyAuthEnum;
import com.didichuxing.datachannel.arius.admin.client.constant.operaterecord.ModuleEnum;
import com.didichuxing.datachannel.arius.admin.client.constant.operaterecord.OperationEnum;
import com.didichuxing.datachannel.arius.admin.client.constant.resource.*;
import com.didichuxing.datachannel.arius.admin.common.Triple;
import com.didichuxing.datachannel.arius.admin.common.Tuple;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.app.App;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.app.AppClusterPhyAuth;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.*;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ecm.ClusterTags;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ecm.RoleCluster;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ecm.RoleClusterHost;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.setting.ESClusterGetSettingsAllResponse;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.region.ClusterRegion;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.stats.ESClusterStatsResponse;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.template.IndexTemplateLogic;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.template.IndexTemplatePhy;
import com.didichuxing.datachannel.arius.admin.common.component.BaseHandle;
import com.didichuxing.datachannel.arius.admin.common.component.HandleFactory;
import com.didichuxing.datachannel.arius.admin.common.component.SpringTool;
import com.didichuxing.datachannel.arius.admin.common.constant.AdminConstant;
import com.didichuxing.datachannel.arius.admin.common.constant.RunModeEnum;
import com.didichuxing.datachannel.arius.admin.common.constant.arius.AriusUser;
import com.didichuxing.datachannel.arius.admin.common.constant.cluster.ClusterConnectionStatus;
import com.didichuxing.datachannel.arius.admin.common.constant.cluster.ClusterDynamicConfigsEnum;
import com.didichuxing.datachannel.arius.admin.common.constant.cluster.ClusterDynamicConfigsTypeEnum;
import com.didichuxing.datachannel.arius.admin.common.constant.cluster.ClusterHealthEnum;
import com.didichuxing.datachannel.arius.admin.common.event.resource.ClusterPhyEvent;
import com.didichuxing.datachannel.arius.admin.common.exception.AdminOperateException;
import com.didichuxing.datachannel.arius.admin.common.exception.ESOperateException;
import com.didichuxing.datachannel.arius.admin.common.threadpool.AriusScheduleThreadPool;
import com.didichuxing.datachannel.arius.admin.common.util.*;
import com.didichuxing.datachannel.arius.admin.core.service.app.AppClusterLogicAuthService;
import com.didichuxing.datachannel.arius.admin.core.service.app.AppService;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.logic.ClusterLogicService;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.physic.ClusterPhyService;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.physic.RoleClusterHostService;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.physic.RoleClusterService;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.region.RegionRackService;
import com.didichuxing.datachannel.arius.admin.core.service.common.OperateRecordService;
import com.didichuxing.datachannel.arius.admin.core.service.es.ESClusterNodeService;
import com.didichuxing.datachannel.arius.admin.core.service.es.ESClusterService;
import com.didichuxing.datachannel.arius.admin.core.service.es.ESTemplateService;
import com.didichuxing.datachannel.arius.admin.core.service.template.logic.TemplateLogicService;
import com.didichuxing.datachannel.arius.admin.core.service.template.physic.TemplatePhyService;
import com.didichuxing.datachannel.arius.admin.persistence.component.ESGatewayClient;
import com.didichuxing.datachannel.arius.admin.persistence.component.ESOpClient;
import com.didiglobal.logi.elasticsearch.client.response.setting.common.MappingConfig;
import com.didiglobal.logi.log.ILog;
import com.didiglobal.logi.log.LogFactory;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import javax.annotation.PostConstruct;

import static com.didichuxing.datachannel.arius.admin.client.constant.resource.ESClusterNodeRoleEnum.*;
import static com.didichuxing.datachannel.arius.admin.client.constant.resource.ResourceLogicTypeEnum.PRIVATE;
import static com.didichuxing.datachannel.arius.admin.common.constant.ClusterConstant.*;
import static com.didichuxing.datachannel.arius.admin.common.constant.DataCenterEnum.CN;
import static com.didichuxing.datachannel.arius.admin.common.constant.PageSearchHandleTypeEnum.CLUSTER_PHY;

@Component
public class ClusterPhyManagerImpl implements ClusterPhyManager {

    private static final ILog                                LOGGER                        = LogFactory
        .getLog(ClusterPhyManagerImpl.class);

    private static final String                              NODE_NOT_EXISTS_TIPS          = "?????????????????????%s?????????";

    private static final String                              IP_DUPLICATE_TIPS             = "??????ip:%s??????, ???????????????";

    private static final Map<String/*cluster*/, Triple<Long/*diskUsage*/, Long/*diskTotal*/, Double/*diskUsagePercent*/>> clusterName2ESClusterStatsTripleMap = Maps.newConcurrentMap();

    @Autowired
    private ClusterPhyManager                                clusterPhyManager;

    @Autowired
    private ESTemplateService                                esTemplateService;

    @Autowired
    private ClusterPhyService                                clusterPhyService;

    @Autowired
    private ClusterLogicService                              clusterLogicService;
    
    @Autowired
    private ClusterLogicManager                              clusterLogicManager;

    @Autowired
    private RoleClusterService                               roleClusterService;

    @Autowired
    private RoleClusterHostService                           roleClusterHostService;

    @Autowired
    private TemplatePhyService                               templatePhyService;

    @Autowired
    private TemplateSrvManager                               templateSrvManager;

    @Autowired
    private TemplatePhyMappingManager                        templatePhyMappingManager;

    @Autowired
    private TemplatePipelineManager                          templatePipelineManager;

    @Autowired
    private TemplateLogicService                             templateLogicService;

    @Autowired
    private TemplatePhyManager                               templatePhyManager;

    @Autowired
    private RegionRackService                                regionRackService;

    @Autowired
    private AppClusterLogicAuthService                       appClusterLogicAuthService;

    @Autowired
    private ESGatewayClient                                  esGatewayClient;

    @Autowired
    private ClusterNodeManager                               clusterNodeManager;

    @Autowired
    private ClusterContextManager                            clusterContextManager;

    @Autowired
    private AppService                                       appService;

    @Autowired
    private OperateRecordService                             operateRecordService;

    @Autowired
    private ESClusterNodeService                             esClusterNodeService;

    @Autowired
    private ESClusterService                                 esClusterService;

    @Autowired
    private HandleFactory                                    handleFactory;

    @Autowired
    private AppClusterPhyAuthManager                         appClusterPhyAuthManager;

    @Autowired
    private AriusScheduleThreadPool                          ariusScheduleThreadPool;

    @Autowired
    private ESOpClient                                       esOpClient;

    @PostConstruct
    private void init(){
        ariusScheduleThreadPool.submitScheduleAtFixedDelayTask(this::refreshClusterDistInfo,60,180);
    }

    private static final FutureUtil<Void> futureUtil = FutureUtil.init("ClusterPhyManagerImpl",20, 40,100);

    @Override
    public boolean copyMapping(String cluster, int retryCount) {
        // ??????????????????????????????????????????
        List<IndexTemplatePhy> physicals = templatePhyService.getNormalTemplateByCluster(cluster);
        if (CollectionUtils.isEmpty(physicals)) {
            LOGGER.info("class=ESClusterPhyServiceImpl||method=copyMapping||cluster={}||msg=copyMapping no template",
                cluster);
            return true;
        }

        int succeedCount = 0;
        // ?????????????????????copy mapping
        for (IndexTemplatePhy physical : physicals) {
            try {
                // ???????????????????????????????????????
                IndexTemplateLogic templateLogic = templateLogicService.getLogicTemplateById(physical.getLogicId());
                // ???????????????mapping?????????
                Result<MappingConfig> result = templatePhyMappingManager.syncMappingConfig(cluster, physical.getName(),
                    physical.getExpression(), templateLogic.getDateFormat());

                if (result.success()) {
                    succeedCount++;
                    if (!setTemplateSettingSingleType(cluster, physical.getName())) {
                        LOGGER.error(
                            "class=ESClusterPhyServiceImpl||method=copyMapping||errMsg=failedUpdateSingleType||cluster={}||template={}",
                            cluster, physical.getName());
                    }
                } else {
                    LOGGER.warn(
                        "class=ESClusterPhyServiceImpl||method=copyMapping||cluster={}||template={}||msg=copyMapping fail",
                        cluster, physical.getName());
                }
            } catch (Exception e) {
                LOGGER.error("class=ESClusterPhyServiceImpl||method=copyMapping||errMsg={}||cluster={}||template={}",
                    e.getMessage(), cluster, physical.getName(), e);
            }
        }

        return succeedCount * 1.0 / physicals.size() > 0.7;
    }

    @Override
    public void syncTemplateMetaData(String cluster, int retryCount) {
        // ??????????????????????????????????????????
        List<IndexTemplatePhy> physicals = templatePhyService.getNormalTemplateByCluster(cluster);
        if (CollectionUtils.isEmpty(physicals)) {
            LOGGER.info(
                "class=ESClusterPhyServiceImpl||method=syncTemplateMetaData||cluster={}||msg=syncTemplateMetaData no template",
                cluster);
            return;
        }

        // ??????????????????
        for (IndexTemplatePhy physical : physicals) {
            try {
                // ????????????????????????ES???????????????ES?????????????????????
                templatePhyManager.syncMeta(physical.getId(), retryCount);
                // ????????????????????????ES??????pipeline
                templatePipelineManager.syncPipeline(physical,
                    templateLogicService.getLogicTemplateWithPhysicalsById(physical.getLogicId()));
            } catch (Exception e) {
                LOGGER.error(
                    "class=ESClusterPhyServiceImpl||method=syncTemplateMetaData||errMsg={}||cluster={}||template={}",
                    e.getMessage(), cluster, physical.getName(), e);
            }
        }
    }

    @Override
    public boolean isClusterExists(String clusterName) {
        return clusterPhyService.isClusterExists(clusterName);
    }

    @Override
    public Result<Void> releaseRacks(String cluster, String racks, int retryCount) {
        if (!isClusterExists(cluster)) {
            return Result.buildNotExist("???????????????");
        }

        Set<String> racksToRelease = Sets.newHashSet(racks.split(AdminConstant.RACK_COMMA));

        // ???????????????????????????rack??????????????????
        List<IndexTemplatePhy> templatePhysicals = templatePhyService.getNormalTemplateByClusterAndRack(cluster,
            racksToRelease);

        // ????????????????????????????????????rack???
        if (CollectionUtils.isEmpty(templatePhysicals)) {
            return Result.buildSucc();
        }

        List<String> errMsgList = Lists.newArrayList();
        // ??????????????????????????????rack??????
        for (IndexTemplatePhy templatePhysical : templatePhysicals) {
            // ??????????????????rack????????????racks
            String tgtRack = RackUtils.removeRacks(templatePhysical.getRack(), racksToRelease);

            LOGGER.info("class=ClusterPhyManagerImpl||method=releaseRack||template={}||srcRack={}||tgtRack={}", templatePhysical.getName(),
                templatePhysical.getRack(), tgtRack);

            try {
                // ????????????
                Result<Void> result = templatePhyManager.editTemplateRackWithoutCheck(templatePhysical.getId(), tgtRack,
                    AriusUser.SYSTEM.getDesc(), retryCount);

                if (result.failed()) {
                    errMsgList.add(templatePhysical.getName() + "?????????" + result.getMessage() + ";");
                }

            } catch (Exception e) {
                errMsgList.add(templatePhysical.getName() + "?????????" + e.getMessage() + ";");
                LOGGER.warn("class=ClusterPhyManagerImpl||method=releaseRack||template={}||srcRack={}||tgtRack={}||errMsg={}",
                    templatePhysical.getName(), templatePhysical.getRack(), tgtRack, e.getMessage(), e);
            }
        }

        if (CollectionUtils.isEmpty(errMsgList)) {
            return Result.buildSucc();
        }

        return Result.buildFail(String.join(",", errMsgList));
    }

    // @Cacheable(cacheNames = CACHE_GLOBAL_NAME, key = "#currentAppId + '@' + 'getConsoleClusterPhyVOS'")
    @Override
    public List<ConsoleClusterPhyVO> getConsoleClusterPhyVOS(ESClusterDTO param, Integer currentAppId) {

        List<ClusterPhy> esClusterPhies = clusterPhyService.listClustersByCondt(param);

        return buildConsoleClusterPhy(esClusterPhies, currentAppId);
    }

    @Override
    public List<ConsoleClusterPhyVO> getConsoleClusterPhyVOS(ESClusterDTO param) {

        List<ClusterPhy> phyClusters = clusterPhyService.listClustersByCondt(param);
        List<ConsoleClusterPhyVO> consoleClusterPhyVOS = ConvertUtil.list2List(phyClusters, ConsoleClusterPhyVO.class);

        consoleClusterPhyVOS.parallelStream()
                .forEach(this::buildClusterRole);

        Collections.sort(consoleClusterPhyVOS);

        return consoleClusterPhyVOS;
    }


    @Override
    public List<ConsoleClusterPhyVO> buildClusterInfo(List<ClusterPhy> clusterPhyList, Integer appId) {
        if (CollectionUtils.isEmpty(clusterPhyList)) {
            return Lists.newArrayList();
        }

        // ??????????????????????????????????????????
        List<AppClusterPhyAuth> appClusterPhyAuthList      = appClusterPhyAuthManager.getByClusterPhyListAndAppIdFromCache(appId, clusterPhyList);
        Map<String, Integer>    clusterPhyName2AuthTypeMap = ConvertUtil.list2Map(appClusterPhyAuthList, AppClusterPhyAuth::getClusterPhyName, AppClusterPhyAuth::getType);

        List<ConsoleClusterPhyVO> consoleClusterPhyVOList = ConvertUtil.list2List(clusterPhyList, ConsoleClusterPhyVO.class);

        //1. ????????????????????????
        consoleClusterPhyVOList.forEach(consoleClusterPhyVO -> consoleClusterPhyVO.setCurrentAppAuth(clusterPhyName2AuthTypeMap.get(consoleClusterPhyVO.getCluster())));

        //2.??????????????????????????????????????????AppId
        long timeForBuildClusterAppInfo = System.currentTimeMillis();
        consoleClusterPhyVOList.forEach(consoleClusterPhyVO -> {
            futureUtil.runnableTask(() -> {
                ClusterPhyContext clusterPhyContext = clusterContextManager.getClusterPhyContext(consoleClusterPhyVO.getCluster());
                consoleClusterPhyVO.setBelongAppIds(  null != clusterPhyContext ? clusterPhyContext.getAssociatedAppIds()   : null);
                consoleClusterPhyVO.setBelongAppNames(null != clusterPhyContext ? clusterPhyContext.getAssociatedAppNames() : null);

                // ???????????????
                consoleClusterPhyVO.setBelongAppId((null != clusterPhyContext &&
                        CollectionUtils.isNotEmpty(clusterPhyContext.getAssociatedAppIds())) ?
                        clusterPhyContext.getAssociatedAppIds().get(0) : null);
                // ???????????????
                consoleClusterPhyVO.setBelongAppName(null != clusterPhyContext &&
                        CollectionUtils.isNotEmpty(clusterPhyContext.getAssociatedAppNames()) ?
                        clusterPhyContext.getAssociatedAppNames().get(0) : null);
            });
        });
        futureUtil.waitExecute();

        LOGGER.info("class=ClusterPhyManagerImpl||method=buildClusterInfo||msg=time to build clusters belongAppIds and AppName is {} ms",
                System.currentTimeMillis() - timeForBuildClusterAppInfo);

        List<Integer> clusterIds = consoleClusterPhyVOList.stream().map(ConsoleClusterPhyVO::getId).collect(Collectors.toList());
        Map<Long, List<RoleCluster>> roleListMap = roleClusterService.getAllRoleClusterByClusterIds(clusterIds);

        //3. ???????????????????????????????????????????????????
        long timeForBuildClusterDiskInfo = System.currentTimeMillis();
        for (ConsoleClusterPhyVO consoleClusterPhyVO : consoleClusterPhyVOList) {
            futureUtil.runnableTask(() -> clusterPhyManager.buildClusterRole(consoleClusterPhyVO, roleListMap.get(consoleClusterPhyVO.getId().longValue())));
        }
        futureUtil.waitExecute();
        LOGGER.info("class=ClusterPhyManagerImpl||method=buildClusterInfo||msg=consumed build cluster belongAppIds and AppName time is {} ms",
                System.currentTimeMillis() - timeForBuildClusterDiskInfo);

        return consoleClusterPhyVOList;
    }

    @Override
    public ConsoleClusterPhyVO getConsoleClusterPhyVO(Integer clusterId, Integer currentAppId) {
        if (AriusObjUtils.isNull(clusterId)) {
            return null;
        }

        //????????????clusterLogicManager?????????spring????????????
        List<ConsoleClusterPhyVO> consoleClusterPhyVOS = clusterPhyManager.getConsoleClusterPhyVOS(null, currentAppId);
        if (CollectionUtils.isNotEmpty(consoleClusterPhyVOS)) {
            for (ConsoleClusterPhyVO consoleClusterPhyVO : consoleClusterPhyVOS) {
                if (clusterId.equals(consoleClusterPhyVO.getId())) {
                    return consoleClusterPhyVO;
                }
            }
        }

        return null;
    }

    @Override
    public ConsoleClusterPhyVO getConsoleClusterPhy(Integer clusterId, Integer currentAppId) {
        // ??????????????????
        ClusterPhy clusterPhy = clusterPhyService.getClusterById(clusterId);
        if(clusterPhy == null) {
            return new ConsoleClusterPhyVO();
        }
        ConsoleClusterPhyVO consoleClusterPhyVO = ConvertUtil.obj2Obj(clusterPhy, ConsoleClusterPhyVO.class);

        // ??????overView??????
        buildWithOtherInfo(consoleClusterPhyVO, currentAppId);
        buildPhyClusterStatics(consoleClusterPhyVO);
        buildClusterRole(consoleClusterPhyVO);
        return consoleClusterPhyVO;
    }

    @Override
    public Result<List<String>> listCanBeAssociatedRegionOfClustersPhys(Integer clusterLogicType, Long clusterLogicId) {
        return clusterContextManager.getCanBeAssociatedClustersPhys(clusterLogicType, clusterLogicId);
    }

    @Override
    public Result<List<String>> listCanBeAssociatedClustersPhys(Integer clusterLogicType) {
        return clusterContextManager.getCanBeAssociatedClustersPhys(clusterLogicType, null);
    }

    @Override
    public Result<List<ESRoleClusterHostVO>> getClusterPhyRegionInfos(Integer clusterId) {
        ClusterPhy clusterPhy = clusterPhyService.getClusterById(clusterId);
        if (AriusObjUtils.isNull(clusterPhy)) {
            return Result.buildFail(String.format("??????[%s]?????????", clusterId));
        }

        List<RoleClusterHost> nodesInfo = roleClusterHostService.getNodesByCluster(clusterPhy.getCluster());
        return Result.buildSucc(clusterNodeManager.convertClusterPhyNodes(nodesInfo, clusterPhy.getCluster()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Tuple<Long, String>> clusterJoin(ClusterJoinDTO param, String operator) {
        try {
            Result<Void> checkResult = validCheckAndInitForClusterJoin(param, operator);
            if (checkResult.failed())  { return Result.buildFail(checkResult.getMessage()); }

            Result<Tuple<Long, String>> doClusterJoinResult = doClusterJoin(param, operator);
            if (doClusterJoinResult.success()) {
                SpringTool.publish(new ClusterPhyEvent(param.getCluster(), param.getAppId()));
                
                postProcessingForClusterJoin(param, doClusterJoinResult.getData(), operator);
            }

            return doClusterJoinResult;
        } catch (Exception e) {
            LOGGER.error("class=ClusterPhyManagerImpl||method=clusterJoin||logicCluster={}||clusterPhy={}||errMsg={}", param.getLogicCluster(),
                param.getCluster(), e.getMessage());
            // ??????????????????????????????
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return Result.buildFail("????????????, ??????????????????");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> deleteClusterJoin(Integer clusterId, String operator) {
        ClusterPhy clusterPhy = clusterPhyService.getClusterById(clusterId);
        if (AriusObjUtils.isNull(clusterPhy)) {
            return Result.buildParamIllegal("?????????????????????");
        }

        try {
            doDeleteClusterJoin(clusterPhy, operator);
        } catch (AdminOperateException e) {
            LOGGER.error("class=ClusterPhyManagerImpl||method=deleteClusterJoin||errMsg={}||e={}||clusterId={}",
                e.getMessage(), e, clusterId);
            // ??????????????????????????????????????????
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return Result.buildFail(e.getMessage());
        }

        return Result.buildSucc();
    }

    @Override
    public Result<List<PluginVO>> listPlugins(String cluster) {
        return Result.buildSucc(ConvertUtil.list2List(clusterPhyService.listClusterPlugins(cluster), PluginVO.class));
    }

    @Override
    public Result<Map<ClusterDynamicConfigsTypeEnum, Map<String, Object>>> getPhyClusterDynamicConfigs(String cluster) {
        if (isClusterExists(cluster)) {
            Result.buildFail(String.format("??????[%s]?????????", cluster));
        }

        ESClusterGetSettingsAllResponse clusterSetting = esClusterService.syncGetClusterSetting(cluster);
        if (null == clusterSetting) {
            return Result.buildFail(String.format("????????????????????????????????????, ?????????????????????[%s]????????????", cluster));
        }

        // ??????defaults???persistent??????????????????transient????????????????????????????????????????????????
        Map<String, Object> clusterConfigMap = new HashMap<>();
        clusterConfigMap.putAll(ConvertUtil.directFlatObject(clusterSetting.getDefaults()));
        clusterConfigMap.putAll(ConvertUtil.directFlatObject(clusterSetting.getPersistentObj()));

        // Map<ClusterDynamicConfigsTypeEnum, Map<String, Object>>???Map???String??????????????????????????????????????????cluster.routing.allocation.awareness.attributes
        // Object????????????????????????????????????
        Map<ClusterDynamicConfigsTypeEnum, Map<String, Object>> clusterDynamicConfigsTypeEnumMapMap = initClusterDynamicConfigs();
        for (ClusterDynamicConfigsEnum param : ClusterDynamicConfigsEnum.valuesWithoutUnknown()) {
            Map<String, Object> dynamicConfig = clusterDynamicConfigsTypeEnumMapMap
                .get(param.getClusterDynamicConfigsType());
            dynamicConfig.put(param.getName(), clusterConfigMap.get(param.getName()));
        }

        return Result.buildSucc(clusterDynamicConfigsTypeEnumMapMap);
    }

    @Override
    public Result<Boolean> updatePhyClusterDynamicConfig(ClusterSettingDTO param) {
        return clusterPhyService.updatePhyClusterDynamicConfig(param);
    }

    @Override
    public Result<Set<String>> getRoutingAllocationAwarenessAttributes(String cluster) {
        return Result.buildSucc(clusterPhyService.getRoutingAllocationAwarenessAttributes(cluster));
    }

    @Override
    public List<String> getAppClusterPhyNames(Integer appId) {
        if(appService.isSuperApp(appId)){
            //??????appId?????????????????????
            List<ClusterPhy> phyList = clusterPhyService.listAllClusters();
            return phyList.stream().map(ClusterPhy::getCluster).distinct().sorted(Comparator.naturalOrder()).collect(Collectors.toList());
        }
        List<Long> appAuthLogicClusters = clusterLogicService.getHasAuthClusterLogicIdsByAppId(appId);
        Set<String> names = new HashSet<>();
        for (Long logicClusterId : appAuthLogicClusters) {
            ClusterLogicContext clusterLogicContext = clusterContextManager.getClusterLogicContextCache(logicClusterId);
            if (clusterLogicContext != null) {
                names.addAll(clusterLogicContext.getAssociatedClusterPhyNames());
            }
        }
        List<String> appClusterPhyNames = Lists.newArrayList(names);
        appClusterPhyNames.sort(Comparator.naturalOrder());
        return appClusterPhyNames;
    }

    @Override
    public List<String> getAppClusterPhyNodeNames(String clusterPhyName) {
        if (null == clusterPhyName) {
            LOGGER.error("class=ESClusterPhyServiceImpl||method=getAppClusterPhyNodeNames||cluster={}||errMsg=??????????????????",
                clusterPhyName);
            return Lists.newArrayList();
        }
        return esClusterNodeService.syncGetNodeNames(clusterPhyName);
    }

    @Override
    public List<String> getAppNodeNames(Integer appId) {
        List<String> appAuthNodeNames = Lists.newCopyOnWriteArrayList();

        List<String> appClusterPhyNames = getAppClusterPhyNames(appId);
        appClusterPhyNames
            .forEach(clusterPhyName -> appAuthNodeNames.addAll(esClusterNodeService.syncGetNodeNames(clusterPhyName)));

        return appAuthNodeNames;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Boolean> deleteClusterInfo(Integer clusterPhyId, String operator, Integer appId) {
        ClusterPhy  clusterPhy  = clusterPhyService.getClusterById(clusterPhyId);
        if (null == clusterPhy) {
            return Result.buildFail(String.format("????????????Id[%s]?????????", clusterPhyId));
        }

        try {
            List<RoleClusterHost> roleClusterHosts = roleClusterHostService.getNodesByCluster(clusterPhy.getCluster());
            // ???????????????????????????host???????????????????????????
            if (!CollectionUtils.isEmpty(roleClusterHosts)) {
                Result<Void> deleteHostResult = roleClusterHostService.deleteByCluster(clusterPhy.getCluster());
                if (deleteHostResult.failed()) {
                    throw new AdminOperateException(String.format("????????????[%s]??????????????????", clusterPhy.getCluster()));
                }
            }

            Result<Void> deleteRoleResult = roleClusterService.deleteRoleClusterByClusterId(clusterPhy.getId());
            if (deleteRoleResult.failed()) {
                throw new AdminOperateException(String.format("????????????[%s]??????????????????", clusterPhy.getCluster()));
            }

            Result<Boolean> deleteClusterResult  = clusterPhyService.deleteClusterById(clusterPhyId, operator);
            if (deleteClusterResult.failed()) {
                throw new AdminOperateException(String.format("????????????[%s]????????????", clusterPhy.getCluster()));
            }

            List<ClusterRegion> clusterRegionList = regionRackService.listPhyClusterRegions(clusterPhy.getCluster());
            if(!AriusObjUtils.isEmptyList(clusterRegionList)) {
                // ??????????????????Region?????????
                Result<Void> deletePhyClusterRegionResult = regionRackService.deleteByClusterPhy(clusterPhy.getCluster(), operator);
                if (deletePhyClusterRegionResult.failed()) {
                    throw new AdminOperateException(String.format("????????????[%s]Region?????????", clusterPhy.getCluster()));
                }
            }
        } catch (AdminOperateException e) {
            LOGGER.error("class=ClusterPhyManagerImpl||method=deleteClusterInfo||clusterName={}||errMsg={}||e={}",
                clusterPhy.getCluster(), e.getMessage(), e);
            // ??????????????????????????????????????????
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return Result.buildFail("????????????????????????");
        }

        SpringTool.publish(new ClusterPhyEvent(clusterPhy.getCluster(), appId));

        return Result.buildSucc(true);
    }

    @Override
    public Result<Boolean> addCluster(ESClusterDTO param, String operator, Integer appId) {
        Result<Boolean> result = clusterPhyService.createCluster(param, operator);

        if (result.success()) {
            SpringTool.publish(new ClusterPhyEvent(param.getCluster(), appId));
            operateRecordService.save(ModuleEnum.CLUSTER, OperationEnum.ADD, param.getCluster(), null, operator);
        }
        return result;
    }

    @Override
    public Result<Boolean> editCluster(ESClusterDTO param, String operator, Integer appId) {
        return clusterPhyService.editCluster(param, operator);
    }

    @Override
    public PaginationResult<ConsoleClusterPhyVO> pageGetConsoleClusterPhyVOS(ClusterPhyConditionDTO condition, Integer appId) {
        BaseHandle baseHandle     = handleFactory.getByHandlerNamePer(CLUSTER_PHY.getPageSearchType());
        if (baseHandle instanceof ClusterPhyPageSearchHandle) {
            ClusterPhyPageSearchHandle handle =   (ClusterPhyPageSearchHandle) baseHandle;
            return handle.doPageHandle(condition, condition.getAuthType(), appId);
        }

        LOGGER.warn("class=ClusterPhyManagerImpl||method=pageGetConsoleClusterVOS||msg=failed to get the ClusterPhyPageSearchHandle");

        return PaginationResult.buildFail("????????????????????????????????????");
    }

    @Override
    public List<ClusterPhy> getClusterPhyByAppIdAndAuthType(Integer appId, Integer authType) {
        App app = appService.getAppById(appId);
        if (!appService.isAppExists(app)) {
            return Lists.newArrayList();
        }

        boolean isSuperApp = appService.isSuperApp(app);
        //?????????????????????????????????????????????
        if (isSuperApp && !AppClusterPhyAuthEnum.OWN.getCode().equals(authType)) {
            return Lists.newArrayList();
        }

        if (!AppClusterPhyAuthEnum.isExitByCode(authType)) {
            return Lists.newArrayList();
        }

        switch (AppClusterPhyAuthEnum.valueOf(authType)) {
            case OWN:
                if (isSuperApp) {
                    return clusterPhyService.listAllClusters();
                } else {
                    return getAppOwnAuthClusterPhyList(appId);
                }
            case ACCESS:
                return getAppAccessClusterPhyList(appId);

            case NO_PERMISSIONS:
                List<Integer> appOwnAuthClusterPhyIdList = getAppOwnAuthClusterPhyList(appId)
                                                            .stream()
                                                            .map(ClusterPhy::getId)
                                                            .collect(Collectors.toList());

                List<Integer> appAccessAuthClusterPhyIdList = getAppAccessClusterPhyList(appId)
                                                                .stream()
                                                                .map(ClusterPhy::getId)
                                                                .collect(Collectors.toList());

                List<ClusterPhy> allClusterPhyList  =  clusterPhyService.listAllClusters();

                return allClusterPhyList.stream()
                        .filter(clusterPhy -> !appAccessAuthClusterPhyIdList.contains(clusterPhy.getId())
                                           && !appOwnAuthClusterPhyIdList.contains(clusterPhy.getId()))
                        .collect(Collectors.toList());
            default:
                return Lists.newArrayList();

        }
    }

    @Override
    public List<ClusterPhy> getAppAccessClusterPhyList(Integer appId) {
        List<AppClusterPhyAuth> appAccessClusterPhyAuths = appClusterPhyAuthManager.getAppAccessClusterPhyAuths(appId);
        return appAccessClusterPhyAuths
                                .stream()
                                .map(r -> clusterPhyService.getClusterByName(r.getClusterPhyName()))
                                .collect(Collectors.toList());
    }

    @Override
    public List<ClusterPhy> getAppOwnAuthClusterPhyList(Integer appId) {
        List<ClusterPhy> appAuthClusterPhyList = Lists.newArrayList();

        List<ClusterLogic> clusterLogicList = clusterLogicService.getOwnedClusterLogicListByAppId(appId);
        if (CollectionUtils.isEmpty(clusterLogicList)) {
            return appAuthClusterPhyList;
        }

        //??????????????????????????????????????????????????????????????????
        List<List<String>> appAuthClusterNameList = clusterLogicList
                            .stream()
                            .map(ClusterLogic::getId)
                            .map(clusterContextManager::getClusterLogicContextCache)
                            .map(ClusterLogicContext::getAssociatedClusterPhyNames)
                            .collect(Collectors.toList());

        for (List<String> clusterNameList : appAuthClusterNameList) {
            clusterNameList.forEach(cluster -> appAuthClusterPhyList.add(clusterPhyService.getClusterByName(cluster)));
        }

        return appAuthClusterPhyList;
    }

    /**
     * ?????????????????????????????????: ???????????????
     */
    @Override
    public void buildPhyClusterStatics(ConsoleClusterPhyVO cluster) {
        try {
            Triple<Long, Long, Double> esClusterStaticInfoTriple = getESClusterStaticInfoTriple(cluster.getCluster());
            cluster.setDiskTotal(esClusterStaticInfoTriple.v1());
            cluster.setDiskUsage(esClusterStaticInfoTriple.v2());
            cluster.setDiskUsagePercent(esClusterStaticInfoTriple.v3());
        } catch (Exception e) {
            LOGGER.warn("class=ClusterPhyManagerImpl||method=buildPhyClusterResourceUsage||logicClusterId={}",
                    cluster.getId(), e);
        }
    }

    @Override
    public void buildClusterRole(ConsoleClusterPhyVO cluster) {
        try {
            List<RoleCluster> roleClusters = roleClusterService.getAllRoleClusterByClusterId(cluster.getId());

            buildClusterRole(cluster, roleClusters);
        } catch (Exception e) {
            LOGGER.warn("class=ClusterPhyManagerImpl||method=buildClusterRole||logicClusterId={}", cluster.getId(), e);
        }
    }

    @Override
    public void buildClusterRole(ConsoleClusterPhyVO cluster, List<RoleCluster> roleClusters) {
        try {
            List<ESRoleClusterVO> roleClusterVOS = ConvertUtil.list2List(roleClusters, ESRoleClusterVO.class);

            List<Long> roleClusterIds = roleClusterVOS.stream().map(ESRoleClusterVO::getId).collect( Collectors.toList());
            Map<Long, List<RoleClusterHost>> roleIdsMap = roleClusterHostService.getByRoleClusterIds(roleClusterIds);

            for (ESRoleClusterVO esRoleClusterVO : roleClusterVOS) {
                List<RoleClusterHost> roleClusterHosts = roleIdsMap.get(esRoleClusterVO.getId());
                List<ESRoleClusterHostVO> esRoleClusterHostVOS = ConvertUtil.list2List(roleClusterHosts, ESRoleClusterHostVO.class);
                esRoleClusterVO.setEsRoleClusterHostVO(esRoleClusterHostVOS);
            }

            cluster.setEsRoleClusterVOS(roleClusterVOS);
        } catch (Exception e) {
            LOGGER.warn("class=ClusterPhyManagerImpl||method=buildClusterRole||logicClusterId={}", cluster.getId(), e);
        }
    }

    @Override
    public boolean updateClusterHealth(String clusterPhyName, String operator) {
        ClusterPhy clusterPhy = clusterPhyService.getClusterByName(clusterPhyName);
        if (null == clusterPhy) {
            LOGGER.warn("class=ClusterPhyManagerImpl||method=updateClusterHealth||clusterPhyName={}||msg=clusterPhy is empty", clusterPhyName);
            return false;
        }

        ESClusterDTO      esClusterDTO      = new ESClusterDTO();
        ClusterHealthEnum clusterHealthEnum = esClusterService.syncGetClusterHealthEnum(clusterPhyName);

        esClusterDTO.setId(clusterPhy.getId());
        esClusterDTO.setHealth(clusterHealthEnum.getCode());
        Result<Boolean> editClusterResult = clusterPhyService.editCluster(esClusterDTO, operator);
        if (editClusterResult.failed()) {
            LOGGER.error("class=ClusterPhyManagerImpl||method=updateClusterHealth||clusterPhyName={}||errMsg={}",
                clusterPhyName, editClusterResult.getMessage());
            return false;
        }

        return true;
    }

    @Override
    public boolean updateClusterInfo(String cluster, String operator) {
        ClusterPhy clusterPhy = clusterPhyService.getClusterByName(cluster);
        if (null == clusterPhy) {
            LOGGER.warn("class=ClusterPhyManagerImpl||method=updateClusterInfo||clusterPhyName={}||msg=clusterPhy is empty", cluster);
            return false;
        }

        ESClusterStatsResponse clusterStats = esClusterService.syncGetClusterStats(cluster);
        long totalFsBytes      = clusterStats.getTotalFs().getBytes();
        long usageFsBytes      = clusterStats.getTotalFs().getBytes() - clusterStats.getFreeFs().getBytes();

        double diskFreePercent = clusterStats.getFreeFs().getGbFrac() / clusterStats.getTotalFs().getGbFrac();
        diskFreePercent = CommonUtils.formatDouble(1 - diskFreePercent, 5);

        ESClusterDTO esClusterDTO = new ESClusterDTO();
        esClusterDTO.setId(clusterPhy.getId());
        esClusterDTO.setDiskTotal(totalFsBytes);
        esClusterDTO.setDiskUsage(usageFsBytes);
        esClusterDTO.setDiskUsagePercent(diskFreePercent);
        Result<Boolean> editClusterResult = clusterPhyService.editCluster(esClusterDTO, operator);
        if (editClusterResult.failed()) {
            LOGGER.error("class=ClusterPhyManagerImpl||method=updateClusterInfo||clusterPhyName={}||errMsg={}",
                    cluster, editClusterResult.getMessage());
            return false;
        }
        
        return true;
    }

    @Override
    public Result<Boolean> checkClusterHealth(String clusterPhyName, String operator) {
        ClusterPhy clusterPhy = clusterPhyService.getClusterByName(clusterPhyName);
        if (null == clusterPhy) {
            return Result.buildFail();
        }

        if (ClusterHealthEnum.GREEN.getCode().equals(clusterPhy.getHealth()) ||
                ClusterHealthEnum.YELLOW.getCode().equals(clusterPhy.getHealth())) {
            return Result.buildSucc(true);
        }

        updateClusterHealth(clusterPhyName, operator);
        return Result.buildSucc();
    }

    @Override
    public Result<Boolean> checkClusterIsExit(String clusterPhyName, String operator) {
        return Result.build(clusterPhyService.isClusterExists(clusterPhyName));
    }

    @Override
    public Result<Boolean> deleteClusterExit(String clusterPhyName, Integer appId, String operator) {
        if  (!appService.isSuperApp(appId)) {
            return Result.buildFail("?????????????????????");
        }

        ClusterPhy clusterPhy = clusterPhyService.getClusterByName(clusterPhyName);
        if (null == clusterPhy) {
            return Result.buildSucc(true);
        }

        return clusterPhyManager.deleteClusterInfo(clusterPhy.getId(), operator, appId);
    }

    @Override
    public void buildBelongAppIdsAndNames(ConsoleClusterPhyVO consoleClusterPhyVO) {
        ClusterPhyContext clusterPhyContext = clusterContextManager.getClusterPhyContextCache(consoleClusterPhyVO.getCluster());
        consoleClusterPhyVO.setBelongAppIds(  null != clusterPhyContext ? clusterPhyContext.getAssociatedAppIds()   : null);
        consoleClusterPhyVO.setBelongAppNames(null != clusterPhyContext ? clusterPhyContext.getAssociatedAppNames() : null);

        // ???????????????
        consoleClusterPhyVO.setBelongAppId((null != clusterPhyContext &&
                CollectionUtils.isNotEmpty(clusterPhyContext.getAssociatedAppIds())) ?
                clusterPhyContext.getAssociatedAppIds().get(0) : null);
        // ???????????????
        consoleClusterPhyVO.setBelongAppName(null != clusterPhyContext &&
                CollectionUtils.isNotEmpty(clusterPhyContext.getAssociatedAppNames()) ?
                clusterPhyContext.getAssociatedAppNames().get(0) : null);
    }

    @Override
    public Result<List<String>> getPhyClusterNameWithSameEsVersion(Integer clusterLogicType,/*???????????????????????????????????????????????????????????????*/String hasSelectedClusterNameWhenBind) {
        //?????????????????????????????????????????????
        Result<List<String>> canBeAssociatedClustersPhyNamesResult = validLogicAndReturnPhyNamesWhenBindPhy(null, clusterLogicType);
        if (canBeAssociatedClustersPhyNamesResult.failed()) {
            return Result.buildFrom(canBeAssociatedClustersPhyNamesResult);
        }

        //???????????????????????????????????????????????????????????????????????????????????????
        if(AriusObjUtils.isNull(hasSelectedClusterNameWhenBind)) {
            return canBeAssociatedClustersPhyNamesResult;
        }

        //???????????????????????????????????????????????????
        return Result.buildSucc(getPhyClusterNameWithSameEsVersion(hasSelectedClusterNameWhenBind, canBeAssociatedClustersPhyNamesResult.getData()));
    }

    @Override
    public Result<List<String>> getPhyClusterNameWithSameEsVersionAfterBuildLogic(Long clusterLogicId) {
        //?????????????????????????????????????????????
        Result<List<String>> canBeAssociatedClustersPhyNamesResult = validLogicAndReturnPhyNamesWhenBindPhy(clusterLogicId, null);
        if (canBeAssociatedClustersPhyNamesResult.failed()) {
            return Result.buildFrom(canBeAssociatedClustersPhyNamesResult);
        }

        //????????????????????????????????????????????????
        List<ClusterLogicRackInfo> clusterLogicRackInfos = regionRackService.listLogicClusterRacks(clusterLogicId);
        if (CollectionUtils.isEmpty(clusterLogicRackInfos)) {
            return canBeAssociatedClustersPhyNamesResult;
        }

        //???????????????????????????????????????????????????
        String hasSelectedPhyClusterName = clusterLogicRackInfos.get(0).getPhyClusterName();
        return Result.buildSucc(getPhyClusterNameWithSameEsVersion(hasSelectedPhyClusterName, canBeAssociatedClustersPhyNamesResult.getData()));
    }

    @Override
    public Result<Boolean> checkTemplateServiceWhenJoin(ClusterJoinDTO clusterJoinDTO, String strId, String operator) {
        if (AriusObjUtils.isNull(clusterJoinDTO)) {
            return Result.buildFail("?????????????????????");
        }

        //???????????????????????????????????????????????????httpAddress
        String httpAddresses = buildClusterReadAndWriteAddressWhenJoin(clusterJoinDTO);
        if (StringUtils.isBlank(httpAddresses)) {
            return Result.buildFail("????????????????????????????????????");
        }

        return templateSrvManager.checkTemplateSrvWhenJoin(httpAddresses, clusterJoinDTO.getPassword(), strId);
    }

    /**
     * ??????????????????????????????????????????????????????????????????rack??????
     *
     * @param clusterPhyName   ??????????????????
     * @param templateSize     ?????????????????????????????????
     * @param clusterLogicName ??????????????????
     * @return ???????????????rack??????
     */
    @Override
    public Result<Set<String>> getValidRacksListByTemplateSize(String clusterPhyName, String clusterLogicName, String templateSize) {
        //???????????????????????????????????????????????????,????????????????????????
        float beSetDiskSize = Float.valueOf(SizeUtil.getUnitSize(templateSize + "gb"));
        ClusterLogic clusterLogic = clusterLogicService.getClusterLogicByName(clusterLogicName);
        if (AriusObjUtils.isNull(clusterLogic)) {
            return Result.buildFail("??????????????????????????????");
        }

        //??????????????????????????????????????????region????????????
        List<ClusterRegion> logicBindRegions = regionRackService.listRegionsByLogicAndPhyCluster(clusterLogic.getId(), clusterPhyName);
        if (CollectionUtils.isEmpty(logicBindRegions)) {
            return Result.buildFail("?????????????????????????????????region??????");
        }
        //TODO: wkp ???????????? ?????????region??????, ????????????
       /*
        //???????????????????????????r??????rack???????????????????????????
        Map<*//*rack*//*String, *//*rack??????????????????*//*Float> allocationInfoOfRackMap = esClusterService.getAllocationInfoOfRack(clusterPhyName);
        if (MapUtils.isEmpty(allocationInfoOfRackMap)) {
            return Result.buildFail("??????????????????????????????????????????rack?????????????????????");
        }

        //?????????????????????????????????????????????region??????
        //tuple(v1:region??????????????????????????????????????????????????????????????????,v2:region??????rack??????)
        Set<String> canCreateTemplateRegionLists = logicBindRegions
                .stream()
                .map(clusterRegion -> new Tuple<>(clusterPhyService.getSurplusDiskSizeOfRacks(clusterRegion.getPhyClusterName(),
                        clusterRegion.getRacks(), allocationInfoOfRackMap), clusterRegion.getRacks()))
                .filter(floatStringTuple -> floatStringTuple.getV1() > beSetDiskSize)
                .sorted(Comparator.comparing(Tuple::getV1, Comparator.reverseOrder()))
                .map(Tuple::getV2)
                .collect(Collectors.toSet()); */

        Set<String> canCreateTemplateRegionLists = logicBindRegions
                .stream()
                .map(ClusterRegion::getRacks)
                .collect(Collectors.toSet());

        if (CollectionUtils.isEmpty(canCreateTemplateRegionLists)) {
            return Result.buildFail("??????????????????????????????????????????region");
        }

        //?????????????????????????????????????????????
        return Result.buildSucc(canCreateTemplateRegionLists);
    }

/**************************************** private method ***************************************************/
    /**
     * ??????????????????setting single_type???true
     * @param cluster  ??????
     * @param template ????????????
     * @return
     */
    private boolean setTemplateSettingSingleType(String cluster, String template) {
        Map<String, String> setting = new HashMap<>();
        setting.put(AdminConstant.SINGLE_TYPE_KEY, AdminConstant.DEFAULT_SINGLE_TYPE);
        try {
            return esTemplateService.syncUpsertSetting(cluster, template, setting, 3);
        } catch (ESOperateException e) {
            LOGGER.warn(
                "class=ClusterPhyManagerImpl||method=setTemplateSettingSingleType||errMsg={}||e={}||cluster={}||template={}",
                e.getMessage(), e, cluster, template);
        }

        return false;
    }

    /**
     * ?????????????????????????????????????????????es??????
     * @param clusterJoinDTO ??????????????????
     * @return ?????????es??????
     */
    private String buildClusterReadAndWriteAddressWhenJoin(ClusterJoinDTO clusterJoinDTO) {
        // ?????????????????????client-node???master-node?????????????????????
        List<ESRoleClusterHostDTO> roleClusterHosts = clusterJoinDTO.getRoleClusterHosts();
        if (CollectionUtils.isEmpty(roleClusterHosts)) {
            return null;
        }

        //????????????????????????master???client????????????
        List<String> clientHttpAddresses = Lists.newArrayList();
        List<String> masterHttpAddresses = Lists.newArrayList();
        for (ESRoleClusterHostDTO roleClusterHost : roleClusterHosts) {
            if (roleClusterHost.getRole().equals(CLIENT_NODE.getCode())) {
                clientHttpAddresses.add(roleClusterHost.getIp() + ":" + roleClusterHost.getPort());
            }
            if (roleClusterHost.getRole().equals(MASTER_NODE.getCode())) {
                masterHttpAddresses.add(roleClusterHost.getIp() + ":" + roleClusterHost.getPort());
            }
        }

        // ??????client?????????????????????????????????client?????????ip??????, ????????????master????????????
        if (!CollectionUtils.isEmpty(clientHttpAddresses)) {
            return ListUtils.strList2String(clientHttpAddresses);
        } else {
            return ListUtils.strList2String(masterHttpAddresses);
        }
    }

    /**
     * ??????????????????????????????????????????????????????????????????????????????,???????????????????????????????????????????????????
     * @param clusterLogicId ????????????id
     * @param clusterLogicType ??????????????????
     * @return ???????????????????????????????????????
     */
    Result<List<String>> validLogicAndReturnPhyNamesWhenBindPhy(Long clusterLogicId, Integer clusterLogicType) {
        if (clusterLogicId == null && clusterLogicType == null) {
            return Result.buildFail("?????????????????????");
        }

        if (clusterLogicId != null) {
            ClusterLogic clusterLogicById = clusterLogicService.getClusterLogicById(clusterLogicId);
            if (clusterLogicById == null) {
                return Result.buildFail("??????????????????????????????");
            }
            clusterLogicType = clusterLogicById.getType();
        }

        if (!ResourceLogicTypeEnum.isExist(clusterLogicType)) {
            return Result.buildParamIllegal("????????????????????????");
        }

        Result<List<String>> canBeAssociatedClustersPhyNames = clusterContextManager.getCanBeAssociatedClustersPhys(clusterLogicType, clusterLogicId);
        if (canBeAssociatedClustersPhyNames.failed()) {
            LOGGER.warn("class=ClusterPhyManagerImpl||method=getPhyClusterNameWithSameEsVersionAfterBuildLogic||errMsg={}",
                    canBeAssociatedClustersPhyNames.getMessage());
            Result.buildFail("?????????????????????????????????????????????");
        }

        return canBeAssociatedClustersPhyNames;
    }

    /**
     * ????????????????????????????????????????????????????????????????????????????????????????????????
     * @param hasSelectedPhyClusterName ?????????????????????????????????
     * @param canBeAssociatedClustersPhyNames ????????????????????????????????????????????????????????????
     * @return ????????????????????????
     */
    private List<String> getPhyClusterNameWithSameEsVersion(String hasSelectedPhyClusterName, List<String> canBeAssociatedClustersPhyNames) {
        //?????????????????????????????????????????????
        ClusterPhy hasSelectedCluster = clusterPhyService.getClusterByName(hasSelectedPhyClusterName);
        //????????????????????????????????????null?????????????????????????????????????????????
        if (AriusObjUtils.isNull(hasSelectedPhyClusterName)
                || AriusObjUtils.isNull(hasSelectedCluster)
                || CollectionUtils.isEmpty(canBeAssociatedClustersPhyNames)) {
            return null;
        }

        //???????????????????????????????????????????????????????????????????????????????????????
        List<String> canBeAssociatedPhyClusterNameWithSameEsVersion = Lists.newArrayList();
        for (String canBeAssociatedClustersPhyName : canBeAssociatedClustersPhyNames) {
            ClusterPhy canBeAssociatedClustersPhy = clusterPhyService.getClusterByName(canBeAssociatedClustersPhyName);
            if (!AriusObjUtils.isNull(canBeAssociatedClustersPhy)
                    && !AriusObjUtils.isNull(canBeAssociatedClustersPhy.getEsVersion())
                    && !AriusObjUtils.isNull(canBeAssociatedClustersPhy.getCluster())
                    && canBeAssociatedClustersPhy.getEsVersion().equals(hasSelectedCluster.getEsVersion())) {
                canBeAssociatedPhyClusterNameWithSameEsVersion.add(canBeAssociatedClustersPhy.getCluster());
            }
        }

        return canBeAssociatedPhyClusterNameWithSameEsVersion;
    }

    /**
     * ????????????????????????
     * @param phyClusters ???????????????????????????
     * @param currentAppId ??????????????????
     */
    private List<ConsoleClusterPhyVO> buildConsoleClusterPhy(List<ClusterPhy> phyClusters, Integer currentAppId) {

        List<ConsoleClusterPhyVO> consoleClusterPhyVOS = ConvertUtil.list2List(phyClusters, ConsoleClusterPhyVO.class);

        consoleClusterPhyVOS.parallelStream()
            .forEach(consoleClusterPhyVO -> buildPhyCluster(consoleClusterPhyVO, currentAppId));

        Collections.sort(consoleClusterPhyVOS);

        return consoleClusterPhyVOS;
    }

    /**
     * ????????????????????????
     * @param consoleClusterPhyVO ???????????????????????????
     * @return
     */
    private void buildPhyCluster(ConsoleClusterPhyVO consoleClusterPhyVO, Integer currentAppId) {
        if (!AriusObjUtils.isNull(consoleClusterPhyVO)) {
            buildPhyClusterStatics(consoleClusterPhyVO);
            buildPhyClusterTemplateSrv(consoleClusterPhyVO);
            buildClusterRole(consoleClusterPhyVO);
            buildWithOtherInfo(consoleClusterPhyVO, currentAppId);
        }
    }

    private void buildPhyClusterTemplateSrv(ConsoleClusterPhyVO cluster) {
        try {
            Result<List<ClusterTemplateSrv>> listResult = templateSrvManager
                .getPhyClusterTemplateSrv(cluster.getCluster());
            if (null != listResult && listResult.success()) {
                cluster.setEsClusterTemplateSrvVOS(
                    ConvertUtil.list2List(listResult.getData(), ESClusterTemplateSrvVO.class));
            }
        } catch (Exception e) {
            LOGGER.warn("class=ClusterPhyManagerImpl||method=buildPhyClusterTemplateSrv||logicClusterId={}",
                cluster.getId(), e);
        }
    }

    /**
     * 1. ??????gateway??????
     * 2. ??????App???????????????
     * 3. ?????????????????????
     */
    private void buildWithOtherInfo(ConsoleClusterPhyVO cluster, Integer currentAppId) {
        cluster.setGatewayAddress(esGatewayClient.getGatewayAddress());

        if (appService.isSuperApp(currentAppId)) {
            cluster.setCurrentAppAuth(AppClusterLogicAuthEnum.ALL.getCode());
        }

        //???????????????????????????????????????
        ClusterLogic clusterLogic = getClusterLogicByClusterPhyName(cluster.getCluster());
        if(clusterLogic == null) {
            return;
        }

        //TODO:  ??????????????????, ??????????????????????????????????????????????????????????????????appId
        cluster.setBelongAppIds(Lists.newArrayList(clusterLogic.getAppId()));
        cluster.setResponsible(clusterLogic.getResponsible());

        App app = appService.getAppById(clusterLogic.getAppId());
        if (!AriusObjUtils.isNull(app)) {
            cluster.setBelongAppNames(Lists.newArrayList(app.getName()));
        }

        //TODO:  ??????????????????, auth table??? ???type?????????????????????????????????????????????
        AppClusterLogicAuthEnum logicClusterAuthEnum = appClusterLogicAuthService.getLogicClusterAuthEnum(currentAppId, clusterLogic.getId());
        cluster.setCurrentAppAuth(logicClusterAuthEnum.getCode());

        if (appService.isSuperApp(currentAppId)) {
            cluster.setCurrentAppAuth(AppClusterLogicAuthEnum.ALL.getCode());
        }
    }

    /**
     * ???????????????????????????????????????????????????
     */
    private ClusterLogic getClusterLogicByClusterPhyName(String phyClusterName) {
        ClusterPhyContext clusterPhyContext = clusterContextManager.getClusterPhyContext(phyClusterName);
        List<Long> clusterLogicIds = Lists.newArrayList();
        if (!AriusObjUtils.isNull(clusterPhyContext)
                && !AriusObjUtils.isNull(clusterPhyContext.getAssociatedClusterLogicIds())) {
            clusterLogicIds = clusterPhyContext.getAssociatedClusterLogicIds();
        }

        if (CollectionUtils.isEmpty(clusterLogicIds)) {
            return null;
        }

        //???????????????????????????????????????, ????????????
        ClusterLogic clusterLogic = clusterLogicService.getClusterLogicById(clusterLogicIds.get(0));
        if (AriusObjUtils.isNull(clusterLogic)) {
            LOGGER.warn(
                    "class=ClusterPhyManagerImpl||method=getClusterLogicByPhyClusterName||clusterName={}||msg=the associated logical cluster is empty",
                    phyClusterName);
            return null;
        }
        return clusterLogic;
    }

    private Result<Tuple<Long/*????????????id*/, String/*??????????????????*/>> doClusterJoin(ClusterJoinDTO param, String operator) throws AdminOperateException {
        Tuple<Long, String> clusterLogicIdAndClusterPhyNameTuple = new Tuple<>();

        // 1.????????????????????????(????????????????????????)
        Result<Void> saveClusterResult = saveClusterPhyInfo(param, operator);
        if (saveClusterResult.failed()) {
            throw new AdminOperateException(saveClusterResult.getMessage());
        }
        clusterLogicIdAndClusterPhyNameTuple.setV2(param.getCluster());

        //?????????????????????????????????????????????region???????????????????????????
        if (StringUtils.isBlank(param.getLogicCluster())) {
            return Result.buildSucc(clusterLogicIdAndClusterPhyNameTuple);
        }

        // 2.??????region??????
        List<Long> regionIds = Lists.newArrayList();
        for (String racks : param.getRegionRacks()) {
            //?????????regionRacks??????cold???????????????????????????region???
            racks = filterColdRackFromRegionRacks(racks);

            if (StringUtils.isBlank(racks)) {
                continue;
            }
            Result<Long> createPayClusterRegionResult = regionRackService.createPhyClusterRegion(param.getCluster(),
                racks, null, operator);
            if (createPayClusterRegionResult.failed()) {
                throw new AdminOperateException(createPayClusterRegionResult.getMessage());
            }

            if (createPayClusterRegionResult.success()) {
                regionIds.add(createPayClusterRegionResult.getData());
            }
        }

        // 3.????????????????????????
        Result<Long> saveClusterLogicResult = saveClusterLogic(param, operator);
        if (saveClusterLogicResult.failed()) {
            throw new AdminOperateException(saveClusterLogicResult.getMessage());
        }

        // 4.??????Region
        Long clusterLogicId = saveClusterLogicResult.getData();
        for (Long regionId : regionIds) {
            Result<Void> bindRegionResult = regionRackService.bindRegion(regionId, clusterLogicId, null, operator);
            if (bindRegionResult.failed()) {
                throw new AdminOperateException(bindRegionResult.getMessage());
            }
        }

        clusterLogicIdAndClusterPhyNameTuple.setV1(clusterLogicId);

        return Result.buildSucc(clusterLogicIdAndClusterPhyNameTuple);
    }

    //??????rack??????cold????????????
    private String filterColdRackFromRegionRacks(String racks) {
        List<String> rackList = RackUtils.racks2List(racks);
        if(CollectionUtils.isEmpty(rackList)) {
            return null;
        }

        rackList.removeIf(AdminConstant.DEFAULT_COLD_RACK::equals);
        return RackUtils.list2Racks(rackList);
    }

    private Result<Void> saveClusterPhyInfo(ClusterJoinDTO param, String operator) {
        //??????????????????
        ESClusterDTO    clusterDTO    =  buildClusterPhy(param, operator);
        Result<Boolean> addClusterRet =  clusterPhyService.createCluster(clusterDTO, operator);
        if (addClusterRet.failed()) { return Result.buildFrom(addClusterRet);}
        return Result.buildSucc();
    }

    private ESClusterDTO buildClusterPhy(ClusterJoinDTO param, String operator) {
        ESClusterDTO clusterDTO = ConvertUtil.obj2Obj(param, ESClusterDTO.class);

        String clientAddress = roleClusterHostService.buildESClientHttpAddressesStr(param.getRoleClusterHosts());

        clusterDTO.setDesc(param.getPhyClusterDesc());
        clusterDTO.setDataCenter(CN.getCode());
        clusterDTO.setHttpAddress(clientAddress);
        clusterDTO.setHttpWriteAddress(clientAddress);
        clusterDTO.setIdc(DEFAULT_CLUSTER_IDC);
        clusterDTO.setLevel(ResourceLogicLevelEnum.NORMAL.getCode());
        clusterDTO.setImageName("");
        clusterDTO.setPackageId(-1L);
        clusterDTO.setNsTree("");
        clusterDTO.setPlugIds("");
        clusterDTO.setCreator(operator);
        clusterDTO.setRunMode(RunModeEnum.READ_WRITE_SHARE.getRunMode());
        clusterDTO.setHealth(DEFAULT_CLUSTER_HEALTH);
        return clusterDTO;
    }

    private Result<Long> saveClusterLogic(ClusterJoinDTO param, String operator) {
        ESLogicClusterDTO esLogicClusterDTO = new ESLogicClusterDTO();
        esLogicClusterDTO.setAppId(param.getAppId());
        esLogicClusterDTO.setResponsible(param.getResponsible());
        esLogicClusterDTO.setName(param.getLogicCluster());
        esLogicClusterDTO.setDataCenter(CN.getCode());
        esLogicClusterDTO.setType(PRIVATE.getCode());
        esLogicClusterDTO.setHealth(DEFAULT_CLUSTER_HEALTH);

        Long dataNodeNumber = param.getRoleClusterHosts().stream().filter(hosts -> DATA_NODE.getCode() == hosts.getRole()).count();

        esLogicClusterDTO.setDataNodeNu(dataNodeNumber.intValue());
        esLogicClusterDTO.setLibraDepartmentId("");
        esLogicClusterDTO.setLibraDepartment("");
        esLogicClusterDTO.setMemo(param.getPhyClusterDesc());

        Result<Long> result = clusterLogicService.createClusterLogic(esLogicClusterDTO);
        if (result.failed()) {
            return Result.buildFail("????????????????????????");
        }

        return result;
    }

    private Result<Void> validCheckAndInitForClusterJoin(ClusterJoinDTO param, String operator) {
        ClusterTags clusterTags = ConvertUtil.str2ObjByJson(param.getTags(), ClusterTags.class);
        if (AriusObjUtils.isNull(param)) {
            return Result.buildParamIllegal("????????????");
        }

        if (AriusObjUtils.isNull(operator)) {
            return Result.buildParamIllegal("??????????????????");
        }

        if (!ESClusterTypeEnum.validCode(param.getType())) {
            return Result.buildParamIllegal("????????????????????????");
        }

        if (!ESClusterResourceTypeEnum.validCode(clusterTags.getResourceType())) {
            return Result.buildParamIllegal("????????????????????????????????????");
        }

        if (ESClusterCreateSourceEnum.ES_IMPORT != ESClusterCreateSourceEnum.valueOf(clusterTags.getCreateSource())) {
            return Result.buildParamIllegal("?????????????????????");
        }

        if (!ESClusterImportRuleEnum.validCode(param.getImportRule())) {
            return Result.buildParamIllegal("????????????????????????");
        }

        List<ESRoleClusterHostDTO> roleClusterHosts = param.getRoleClusterHosts();
        if (CollectionUtils.isEmpty(roleClusterHosts)) {
            return Result.buildParamIllegal("????????????????????????");
        }

        // ?????????????????????????????????????????????
        Set<String> wrongPortSet = roleClusterHosts.stream()
                .map(ESRoleClusterHostDTO::getPort)
                .filter(this::wrongPortDetect)
                .collect(Collectors.toSet());
        if (!CollectionUtils.isEmpty(wrongPortSet)) {
            return Result.buildParamIllegal("????????????????????????????????????" + wrongPortSet);
        }

        Set<Integer> roleForNode = roleClusterHosts.stream().map(ESRoleClusterHostDTO::getRole)
            .collect(Collectors.toSet());

        if (!roleForNode.contains(MASTER_NODE.getCode())) {
            return Result.buildParamIllegal(String.format(NODE_NOT_EXISTS_TIPS, MASTER_NODE.getDesc()));
        }

        Map<Integer, List<String>> role2IpsMap = ConvertUtil.list2MapOfList(roleClusterHosts,
            ESRoleClusterHostDTO::getRole, ESRoleClusterHostDTO::getIp);

        List<String> masterIps = role2IpsMap.get(MASTER_NODE.getCode());
        if (masterIps.size() < JOIN_MASTER_NODE_MIN_NUMBER) {
            return Result.buildParamIllegal(String.format("??????%s???masternode????????????????????????????????????1???????????????", param.getCluster()));
        }

        String duplicateIpForMaster = ClusterUtils.getDuplicateIp(masterIps);
        if (!AriusObjUtils.isBlack(duplicateIpForMaster)) {
            return Result.buildParamIllegal(String.format(IP_DUPLICATE_TIPS, duplicateIpForMaster));
        }

        String duplicateIpForClient = ClusterUtils.getDuplicateIp(role2IpsMap.get(CLIENT_NODE.getCode()));
        if (!AriusObjUtils.isBlack(duplicateIpForClient)) {
            return Result.buildParamIllegal(String.format(IP_DUPLICATE_TIPS, duplicateIpForClient));
        }

        String duplicateIpForData = ClusterUtils.getDuplicateIp(role2IpsMap.get(DATA_NODE.getCode()));
        if (!AriusObjUtils.isBlack(duplicateIpForData)) {
            return Result.buildParamIllegal(String.format(IP_DUPLICATE_TIPS, duplicateIpForData));
        }

        if (clusterPhyService.isClusterExists(param.getCluster())) {
            return Result.buildParamIllegal(String.format("??????????????????:%s?????????", param.getCluster()));
        }

        ClusterLogic clusterLogic = clusterLogicService.getClusterLogicByName(param.getLogicCluster());
        if (!AriusObjUtils.isNull(clusterLogic)) {
            return Result.buildParamIllegal(String.format("??????????????????:%s?????????", param.getLogicCluster()));
        }

        String esClientHttpAddressesStr = roleClusterHostService.buildESClientHttpAddressesStr(roleClusterHosts);

        //????????????
        Result<Void> passwdResult = checkClusterWithoutPasswd(param, esClientHttpAddressesStr);
        if (passwdResult.failed()) return passwdResult;

        //???????????????
        Result<Void> sameClusterResult = checkSameCluster(param.getPassword(), roleClusterHostService.buildESAllRoleHttpAddressesList(roleClusterHosts));
        if (sameClusterResult.failed()) return Result.buildParamIllegal("????????????????????????????????????????????????");

        //????????????rack
        Result<Void> rackSetResult = initRackValueForClusterJoin(param, esClientHttpAddressesStr);
        if (rackSetResult.failed()) return rackSetResult;

        //????????????es??????
        Result<Void> esVersionSetResult = initESVersionForClusterJoin(param, esClientHttpAddressesStr);
        if (esVersionSetResult.failed()) return esVersionSetResult;


        param.setResponsible(operator);
        return Result.buildSucc();
    }

    /**
     * ?????????????????????????????????????????????????????????????????????
     */
    private Result<Void> checkClusterWithoutPasswd(ClusterJoinDTO param, String esClientHttpAddressesStr) {
        ClusterConnectionStatus status = esClusterService.checkClusterPassword(esClientHttpAddressesStr, null);
        if (ClusterConnectionStatus.DISCONNECTED == status) {
            return Result.buildParamIllegal("????????????????????????");
        }

        if (!Strings.isNullOrEmpty(param.getPassword())) {
            if (ClusterConnectionStatus.NORMAL == status) {
                return Result.buildParamIllegal("???????????????????????????????????????????????????");
            }
            status = esClusterService.checkClusterPassword(esClientHttpAddressesStr, param.getPassword());
            if (ClusterConnectionStatus.UNAUTHORIZED == status) {
                return Result.buildParamIllegal("???????????????????????????");
            }
        } else {
            if (ClusterConnectionStatus.UNAUTHORIZED == status) {
                return Result.buildParamIllegal("?????????????????????????????????????????????");
            }
        }
        return Result.buildSucc();
    }

    private Result<Void> checkSameCluster(String passwd, List<String> esClientHttpAddressesList) {
        return esClusterService.checkSameCluster(passwd, esClientHttpAddressesList);
    }

    /**
     * ?????????????????????
     * @param param
     * @param esClientHttpAddressesStr
     * @return
     */
    private Result<Void> initESVersionForClusterJoin(ClusterJoinDTO param, String esClientHttpAddressesStr) {
        String esVersion = esClusterService.synGetESVersionByHttpAddress(esClientHttpAddressesStr, param.getPassword());
        if (Strings.isNullOrEmpty(esVersion)) {
            return Result.buildParamIllegal(String.format("????????????es??????", esClientHttpAddressesStr));
        }
        param.setEsVersion(esVersion);
        return Result.buildSucc();
    }

    /**
     * ?????????rack??????
     * @param param ClusterJoinDTO
     * @param esClientHttpAddressesStr  http????????????
     * @return
     */
    private Result<Void> initRackValueForClusterJoin(ClusterJoinDTO param, String esClientHttpAddressesStr) {
        if(CollectionUtils.isEmpty(param.getRegionRacks())) {
            Result<Set<String>> rackSetResult = esClusterService.getClusterRackByHttpAddress(esClientHttpAddressesStr,param.getPassword());
            if (rackSetResult.failed()) {
                return Result.buildFail(rackSetResult.getMessage());
            } else {
                param.setRegionRacks(new ArrayList<>(rackSetResult.getData()));
            }
        }
        return Result.buildSucc();
    }

    private void doDeleteClusterJoin(ClusterPhy clusterPhy, String operator) throws AdminOperateException {
        ClusterPhyContext clusterPhyContext = clusterContextManager.getClusterPhyContext(clusterPhy.getCluster());
        if (null == clusterPhyContext) {
            return;
        }

        List<Long> associatedRegionIds = clusterPhyContext.getAssociatedRegionIds();
        for (Long associatedRegionId : associatedRegionIds) {
            Result<Void> unbindRegionResult = regionRackService.unbindRegion(associatedRegionId, null, operator);
            if (unbindRegionResult.failed()) {
                throw new AdminOperateException(String.format("??????region(%s)??????", associatedRegionId));
            }

            Result<Void> deletePhyClusterRegionResult = regionRackService.deletePhyClusterRegion(associatedRegionId,
                operator);
            if (deletePhyClusterRegionResult.failed()) {
                throw new AdminOperateException(String.format("??????region(%s)??????", associatedRegionId));
            }
        }

        List<Long> clusterLogicIds = clusterPhyContext.getAssociatedClusterLogicIds();
        for (Long clusterLogicId : clusterLogicIds) {
            Result<Void> deleteLogicClusterResult = clusterLogicService.deleteClusterLogicById(clusterLogicId,
                operator);
            if (deleteLogicClusterResult.failed()) {
                throw new AdminOperateException(String.format("??????????????????(%s)??????", clusterLogicId));
            }
        }

        Result<Boolean> deleteClusterResult = clusterPhyService.deleteClusterById(clusterPhy.getId(), operator);
        if (deleteClusterResult.failed()) {
            throw new AdminOperateException(String.format("??????????????????(%s)??????", clusterPhy.getCluster()));
        }

        Result<Void> deleteRoleClusterResult = roleClusterService.deleteRoleClusterByClusterId(clusterPhy.getId());
        if (deleteRoleClusterResult.failed()) {
            throw new AdminOperateException(String.format("????????????????????????(%s)??????", clusterPhy.getCluster()));
        }

        Result<Void> deleteRoleClusterHostResult = roleClusterHostService.deleteByCluster(clusterPhy.getCluster());
        if (deleteRoleClusterHostResult.failed()) {
            throw new AdminOperateException(String.format("????????????????????????(%s)??????", clusterPhy.getCluster()));
        }
    }

    /**
     * ?????????????????????????????????
     * @return Map<ClusterDynamicConfigsTypeEnum, Map<String, Object>>???Map???String??????????????????????????????????????????cluster.routing.allocation.awareness.attributes
     * Object????????????????????????????????????
     */
    private Map<ClusterDynamicConfigsTypeEnum, Map<String, Object>> initClusterDynamicConfigs() {
        Map<ClusterDynamicConfigsTypeEnum, Map<String, Object>> esClusterPhyDynamicConfig = Maps.newHashMap();
        for (ClusterDynamicConfigsTypeEnum clusterDynamicConfigsTypeEnum : ClusterDynamicConfigsTypeEnum
            .valuesWithoutUnknown()) {
            esClusterPhyDynamicConfig.put(clusterDynamicConfigsTypeEnum, Maps.newHashMap());
        }

        return esClusterPhyDynamicConfig;
    }

    private Triple<Long/*diskTotal*/, Long/*diskUsage*/, Double/*diskUsagePercent*/> getESClusterStaticInfoTriple(String cluster) {
        Triple<Long, Long, Double> initTriple = buildInitTriple();
        if (!clusterPhyService.isClusterExists(cluster)) {
            LOGGER.error(
                "class=ClusterPhyManagerImpl||method=getESClusterStaticInfoTriple||clusterName={}||msg=cluster is empty",
                cluster);
            return initTriple;
        }

        return getClusterStatsTriple(cluster, initTriple);
    }

    private Triple<Long, Long, Double> getClusterStatsTriple(String cluster, Triple<Long, Long, Double> initTriple) {
        if (clusterName2ESClusterStatsTripleMap.containsKey(cluster)) {
            return clusterName2ESClusterStatsTripleMap.get(cluster);
        } else {
            ESClusterStatsResponse clusterStats = esClusterService.syncGetClusterStats(cluster);
            if (null != clusterStats && null != clusterStats.getFreeFs() && null != clusterStats.getTotalFs()
                    && clusterStats.getTotalFs().getBytes() > 0 && clusterStats.getFreeFs().getBytes() > 0) {
                initTriple.setV1(clusterStats.getTotalFs().getBytes());
                initTriple.setV2(clusterStats.getTotalFs().getBytes() - clusterStats.getFreeFs().getBytes());
                double diskFreePercent = clusterStats.getFreeFs().getGbFrac() / clusterStats.getTotalFs().getGbFrac();
                initTriple.setV3(1 - diskFreePercent);
            }

            clusterName2ESClusterStatsTripleMap.put(cluster, initTriple);
            return initTriple;
        }
    }

    private Triple<Long/*diskTotal*/, Long/*diskTotal*/, Double/*diskUsagePercent*/> buildInitTriple() {
        Triple<Long/*diskTotal*/, Long/*diskTotal*/, Double/*diskUsagePercent*/> triple = new Triple<>();
        triple.setV1(0L);
        triple.setV2(0L);
        triple.setV3(0d);
        return triple;
    }

    private void postProcessingForClusterJoin(ClusterJoinDTO param,
                                              Tuple<Long, String> clusterLogicIdAndClusterPhyIdTuple, String operator) {
        esOpClient.connect(param.getCluster());

        if (ESClusterImportRuleEnum.AUTO_IMPORT == ESClusterImportRuleEnum.valueOf(param.getImportRule())) {
            roleClusterHostService.collectClusterNodeSettings(param.getCluster());
        } else if (ESClusterImportRuleEnum.FULL_IMPORT == ESClusterImportRuleEnum.valueOf(param.getImportRule())) {
            //1.???????????????????????????????????????
            roleClusterHostService.saveClusterNodeSettings(param);
            //2.?????????es ????????????????????????????????????????????????????????????????????????
            roleClusterHostService.collectClusterNodeSettings(param.getCluster());
        }

        clusterPhyManager.updateClusterHealth(param.getCluster(), AriusUser.SYSTEM.getDesc());

        Long clusterLogicId = clusterLogicIdAndClusterPhyIdTuple.getV1();
        if (null != clusterLogicId) {
            clusterLogicManager.updateClusterLogicHealth(clusterLogicId);
        }

        operateRecordService.save(ModuleEnum.ES_CLUSTER_JOIN, OperationEnum.ADD, param.getCluster(),
            param.getPhyClusterDesc(), operator);
    }

    private void refreshClusterDistInfo() {
        List<String> clusterNameList = clusterPhyService.listAllClusters().stream().map(ClusterPhy::getCluster)
            .collect(Collectors.toList());
        for (String clusterName : clusterNameList) {
            Triple<Long, Long, Double> initTriple = buildInitTriple();
            ESClusterStatsResponse clusterStats = esClusterService.syncGetClusterStats(clusterName);
            if (null != clusterStats && null != clusterStats.getFreeFs() && null != clusterStats.getTotalFs()
                    && clusterStats.getTotalFs().getBytes() > 0 && clusterStats.getFreeFs().getBytes() > 0) {
                initTriple.setV1(clusterStats.getTotalFs().getBytes());
                initTriple.setV2(clusterStats.getTotalFs().getBytes() - clusterStats.getFreeFs().getBytes());
                double diskFreePercent = clusterStats.getFreeFs().getGbFrac() / clusterStats.getTotalFs().getGbFrac();
                initTriple.setV3(1 - diskFreePercent);
            }

            clusterName2ESClusterStatsTripleMap.put(clusterName, initTriple);
        }
    }

    /**
     * ?????????????????????????????????
     * @param port ?????????
     * @return ????????????
     */
    private boolean wrongPortDetect(String port) {
        try {
            int portValue = Integer.parseInt(port);
            return portValue < AdminConstant.MIN_BIND_PORT_VALUE || portValue > AdminConstant.MAX_BIND_PORT_VALUE;
        } catch (NumberFormatException e) {
            LOGGER.error(
                    "class=ClusterPhyManagerImpl||method=wrongPortDetect||port={}||msg=Integer format error",
                    port);
            return false;
        }
    }
}
