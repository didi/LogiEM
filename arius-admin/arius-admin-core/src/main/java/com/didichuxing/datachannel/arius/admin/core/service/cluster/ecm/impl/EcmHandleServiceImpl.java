package com.didichuxing.datachannel.arius.admin.core.service.cluster.ecm.impl;

import com.alibaba.fastjson.JSON;
import com.didichuxing.datachannel.arius.admin.client.bean.common.Result;
import com.didichuxing.datachannel.arius.admin.client.bean.common.ecm.EcmParamBase;
import com.didichuxing.datachannel.arius.admin.client.bean.common.ecm.EsConfigAction;
import com.didichuxing.datachannel.arius.admin.client.bean.common.ecm.EsPluginAction;
import com.didichuxing.datachannel.arius.admin.client.bean.common.ecm.elasticcloud.ElasticCloudCommonActionParam;
import com.didichuxing.datachannel.arius.admin.client.bean.common.ecm.host.HostsParamBase;
import com.didichuxing.datachannel.arius.admin.client.bean.common.ecm.response.EcmOperateAppBase;
import com.didichuxing.datachannel.arius.admin.client.bean.common.ecm.response.EcmSubTaskLog;
import com.didichuxing.datachannel.arius.admin.client.bean.common.ecm.response.EcmTaskStatus;
import com.didichuxing.datachannel.arius.admin.client.constant.resource.ESClusterNodeRoleEnum;
import com.didichuxing.datachannel.arius.admin.client.constant.resource.ESClusterTypeEnum;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ClusterPhy;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ecm.RoleCluster;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ecm.RoleClusterHost;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.espackage.ESPackage;
import com.didichuxing.datachannel.arius.admin.common.constant.CloudClusterCreateParamConstant;
import com.didichuxing.datachannel.arius.admin.common.constant.ClusterConstant;
import com.didichuxing.datachannel.arius.admin.common.threadpool.AriusTaskThreadPool;
import com.didichuxing.datachannel.arius.admin.common.util.AriusObjUtils;
import com.didichuxing.datachannel.arius.admin.common.util.ConvertUtil;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.ecm.ESPackageService;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.ecm.EcmHandleService;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.ecm.impl.handler.AbstractEcmBaseHandle;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.ecm.impl.handler.EcmDockerHandler;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.ecm.impl.handler.EcmHostHandler;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.physic.ClusterPhyService;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.physic.RoleClusterHostService;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.physic.RoleClusterService;
import com.didichuxing.datachannel.arius.admin.core.service.common.AriusUserInfoService;
import com.didichuxing.datachannel.arius.admin.core.service.common.OperateRecordService;
import com.didichuxing.datachannel.arius.admin.remote.elasticcloud.bean.bizenum.EcmActionEnum;
import com.didichuxing.datachannel.arius.admin.remote.monitor.RemoteMonitorService;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

import static com.didichuxing.datachannel.arius.admin.client.constant.operaterecord.ModuleEnum.CLUSTER;
import static com.didichuxing.datachannel.arius.admin.client.constant.operaterecord.OperationEnum.EXE;
import static com.didichuxing.datachannel.arius.admin.client.constant.resource.ESClusterNodeRoleEnum.MASTER_NODE;
import static com.didichuxing.datachannel.arius.admin.client.constant.resource.ESClusterTypeEnum.ES_DOCKER;
import static com.didichuxing.datachannel.arius.admin.client.constant.resource.ESClusterTypeEnum.ES_HOST;
import static com.didichuxing.datachannel.arius.admin.common.constant.CloudClusterCreateParamConstant.ODIN_CATEGORY_LEVEL_1;
import static com.didichuxing.datachannel.arius.admin.common.constant.CloudClusterCreateParamConstant.ODIN_CATEGORY_LEVEL_2;
import static java.util.Objects.nonNull;

/**
 * ES????????? ???????????????
 * @author didi
 * @since 2020-08-24
 */
@Service
@NoArgsConstructor
public class EcmHandleServiceImpl implements EcmHandleService {

    private static final Logger                 LOGGER                           = LoggerFactory
        .getLogger(EcmHandleServiceImpl.class);

    @Autowired
    private ClusterPhyService                   esClusterPhyService;

    @Autowired
    private RoleClusterService                  roleClusterService;

    @Autowired
    private RoleClusterHostService              roleClusterHostService;

    @Autowired
    private EcmDockerHandler                    ecmDockerHandler;

    @Autowired
    private EcmHostHandler                      ecmHostHandler;

    @Autowired
    private OperateRecordService                operateRecordService;

    @Autowired
    private ESPackageService                    esPackageService;

    @Autowired
    private RemoteMonitorService                remoteMonitorService;

    @Autowired
    private AriusUserInfoService                ariusUserInfoService;

    private AriusTaskThreadPool                 ariusTaskThreadPool;

    private Map<Integer, AbstractEcmBaseHandle> ecmBaseHandleMap                 = new HashMap<>();

    private static final int                    DELETE_ODIN_TREE_MAX_RETRY_TIMES = 1 << 6;

    private static final String CLUSTER_NOT_EXIST = "%d??????????????????????????????";

    private static final String UNKNOWN_TYPE = "?????????????????????????????????(docker/host)";

    @PostConstruct
    public void init() {
        ariusTaskThreadPool = new AriusTaskThreadPool();
        ariusTaskThreadPool.init(10, "EcmHandleServiceImpl", 100);
        ecmBaseHandleMap.put(ecmHostHandler.getEsClusterTypeEnum().getCode(), ecmHostHandler);
        ecmBaseHandleMap.put(ecmDockerHandler.getEsClusterTypeEnum().getCode(), ecmDockerHandler);
    }

    @Override
    public Result<Long> saveESCluster(List<EcmParamBase> ecmParamBaseList) {
        Result<Void> checkResult = checkValidForEsCluster(ecmParamBaseList);
        if (checkResult.failed()) {
            return Result.buildFrom(checkResult);
        }

        AbstractEcmBaseHandle ecmBaseHandle = getByClusterType(ecmParamBaseList.get(0).getType());
        if (null == ecmBaseHandle) {
            return Result.buildNotExist(UNKNOWN_TYPE);
        }

        Result<Long> saveResult = ecmBaseHandle.saveESCluster(ecmParamBaseList);
        if (saveResult.failed()) {
            return Result.buildFrom(saveResult);
        }
        return Result.buildSucc(saveResult.getData());
    }

    @Override
    public Result<EcmOperateAppBase> startESCluster(EcmParamBase ecmParamBase, String operator) {
        // ????????????
        return callESClusterBaseHandle("????????????", ecmParamBase.getPhyClusterId(), operator, ecmParamBase,
            (withoutUsed, esClusterBaseHandle) -> esClusterBaseHandle.startESCluster(ecmParamBase));
    }

    @Override
    public Result<Void> deleteESCluster(Long clusterId, String operator) {
        Result<Void> checkResult = validityCheck(clusterId.intValue(), operator);
        if (checkResult.failed()) {
            return checkResult;
        }

        ClusterPhy clusterPhy = esClusterPhyService.getClusterById(clusterId.intValue());
        if (clusterPhy == null) {
            return Result.buildFail("clusterPhy is empty");
        }
        List<RoleCluster> allRoles = roleClusterService.getAllRoleClusterByClusterId(clusterId.intValue());
        if (CollectionUtils.isEmpty(allRoles)) {
            return Result.buildFail("the role of cluster is empty");
        }

        Result<List<ElasticCloudCommonActionParam>> deleteOdinResult = deleteOdinMachine(clusterPhy, allRoles,
            operator);

        if (deleteOdinResult.success()) {
            ariusTaskThreadPool
                .run(() -> deleteOdinTreeNodeAndLocalDbInfo(deleteOdinResult.getData(), clusterPhy, operator));
        }

        return Result.buildSuccWithMsg(deleteOdinResult.getMessage());
    }

    @Override
    public Result<EcmOperateAppBase> scaleESCluster(EcmParamBase actionParamBase, String operator) {
        // ????????????
        Result<EcmParamBase> actionParamBaseResult = supplyCommonActionParamBase(actionParamBase.getPhyClusterId(),
            actionParamBase.getRoleName(), actionParamBase);
        if (actionParamBaseResult.failed()) {
            return Result.buildFrom(actionParamBaseResult);
        }

        // ????????????
        return callESClusterBaseHandle("???????????????", actionParamBase.getPhyClusterId(), operator,
            actionParamBaseResult.getData(),
            (withoutUsed, esClusterBaseHandle) -> esClusterBaseHandle.scaleESCluster(actionParamBaseResult.getData()));
    }

    @Override
    public Result<EcmOperateAppBase> upgradeESCluster(EcmParamBase ecmParamBase, String operator) {
        // ??????????????????
        Result<EcmParamBase> actionParamBaseResult = supplyCommonActionParamBase(ecmParamBase.getPhyClusterId(),
            ecmParamBase.getRoleName(), ecmParamBase);

        if (actionParamBaseResult.failed()) {
            return Result.buildFrom(actionParamBaseResult);
        }

        // ????????????
        return callESClusterBaseHandle("????????????", ecmParamBase.getPhyClusterId(), operator,
            actionParamBaseResult.getData(), (withoutUsed, esClusterBaseHandle) -> esClusterBaseHandle
                .upgradeESCluster(actionParamBaseResult.getData()));
    }

    @Override
    public Result<EcmOperateAppBase> restartESCluster(EcmParamBase ecmParamBase, String operator) {
        // ??????????????????
        Result<EcmParamBase> actionParamBaseResult = supplyCommonActionParamBase(ecmParamBase.getPhyClusterId(),
            ecmParamBase.getRoleName(), ecmParamBase);
        if (actionParamBaseResult.failed()) {
            return Result.buildFrom(actionParamBaseResult);
        }

        // ????????????
        return callESClusterBaseHandle("????????????", ecmParamBase.getPhyClusterId(), operator,
            actionParamBaseResult.getData(), (withoutUsed, esClusterBaseHandle) -> esClusterBaseHandle
                .restartESCluster(actionParamBaseResult.getData()));
    }

    @Override
    public Result<EcmOperateAppBase> actionUnfinishedESCluster(EcmActionEnum ecmActionEnum, EcmParamBase ecmParamBase, String hostname,
                                            String operator) {
        // ??????????????????
        Result<EcmParamBase> actionParamBaseResult = supplyCommonActionParamBase(ecmParamBase.getPhyClusterId(),
            ecmParamBase.getRoleName(), ecmParamBase);
        if (actionParamBaseResult.failed()) {
            return Result.buildFrom(actionParamBaseResult);
        }

        // ????????????
        return callESClusterBaseHandle("??????" + ecmActionEnum.getAction(), ecmParamBase.getPhyClusterId(), operator,
            actionParamBaseResult.getData(), (withoutUsed, esClusterBaseHandle) -> esClusterBaseHandle
                .actionNotFinishedTask(actionParamBaseResult.getData(), ecmActionEnum, hostname));
    }

    @Override
    public Result<String> infoESCluster(Long clusterId, String operator) {
        // ??????????????????
        Result<EcmParamBase> actionParamBaseResult = buildActionParamBase(clusterId, null);
        if (actionParamBaseResult.failed()) {
            return Result.buildFrom(actionParamBaseResult);
        }

        return callESClusterBaseHandle("??????????????????", clusterId, operator, actionParamBaseResult.getData(),
            (actionParamBase, esClusterBaseHandle) -> esClusterBaseHandle.infoESCluster(actionParamBase));
    }

    @Override
    public Result<EcmSubTaskLog> getSubTaskLog(EcmParamBase ecmParamBase, String hostname, String operator) {
        return callESClusterBaseHandle("??????????????????", ecmParamBase.getPhyClusterId(), operator, ecmParamBase,
            (withoutUsed, esClusterBaseHandle) -> esClusterBaseHandle
                .getSubTaskLog(ecmParamBase.getTaskId().longValue(), hostname, ecmParamBase));
    }

    @Override
    public Result<List<EcmTaskStatus>> getESClusterStatus(EcmParamBase ecmParamBase, Integer orderType,
                                                          String operator) {
        return callESClusterBaseHandle("????????????", ecmParamBase.getPhyClusterId(), operator, ecmParamBase,
            (withoutUsed, esClusterBaseHandle) -> esClusterBaseHandle.getTaskStatus(ecmParamBase, orderType), false);
    }

    @Override
    public Result<List<EcmParamBase>> buildEcmParamBaseListWithConfigAction(Integer phyClusterId,
                                                                            List<String> roleNameList,
                                                                            Multimap<String, Long> role2ConfigIdsMultiMap,
                                                                            Integer actionType) {
        return buildEcmParamBaseListInner(phyClusterId, roleNameList, role2ConfigIdsMultiMap, actionType,null);
    }

    @Override
    public Result<List<EcmParamBase>> buildEcmParamBaseListWithEsPluginAction(Integer phyClusterId,
                                                                              List<String> roleNameList,
                                                                              Long esPluginId,
                                                                              Integer actionType) {
        return buildEcmParamBaseListInner(phyClusterId, roleNameList, null, actionType, esPluginId);
    }

    @Override
    public Result<List<EcmParamBase>> buildEcmParamBaseList(Integer phyClusterId, List<String> roleNameList) {
        return buildEcmParamBaseListInner(phyClusterId, roleNameList, null, null,null);
    }

    private Result<List<EcmParamBase>> buildEcmParamBaseListInner(Integer phyClusterId, List<String> roleNameList,
                                                                  Multimap<String, Long> role2ConfigIdsMultiMap,
                                                                  Integer actionType, Long pluginId) {
        if (AriusObjUtils.isEmptyList(roleNameList)) {
            return Result.buildSucc(new ArrayList<>());
        }

        ClusterPhy clusterPhy = esClusterPhyService.getClusterById(phyClusterId);
        if (AriusObjUtils.isNull(clusterPhy)) {
            return Result.buildFail(String.format(CLUSTER_NOT_EXIST, phyClusterId));
        }

        List<String> masterHostList = roleClusterHostService
            .getHostNamesByRoleAndClusterId(clusterPhy.getId().longValue(), MASTER_NODE.getDesc());

        List<EcmParamBase> ecmParamBaseList = new ArrayList<>();
        for (String roleName : roleNameList) {
            String newRoleName = roleName;
            if (newRoleName.startsWith(clusterPhy.getCluster())) {
                newRoleName = newRoleName.replaceFirst(clusterPhy.getCluster() + "-", "");
            }

            if (ES_DOCKER.getCode() == clusterPhy.getType()) {
                Result<EcmParamBase> buildElasticCloudParamBase = buildElasticCloudParamBase(clusterPhy,
                        newRoleName, role2ConfigIdsMultiMap, actionType);
                if(buildElasticCloudParamBase.failed()) {
                    return Result.buildFrom(buildElasticCloudParamBase);
                }
                ecmParamBaseList.add(buildElasticCloudParamBase.getData());
            } else if (ES_HOST.getCode() == clusterPhy.getType()) {
                Result<EcmParamBase> buildHostParamBase = buildHostParamBase(clusterPhy,
                        newRoleName, masterHostList, role2ConfigIdsMultiMap, pluginId, actionType);
                if(buildHostParamBase.failed()) {
                    return Result.buildFrom(buildHostParamBase);
                }
                ecmParamBaseList.add(buildHostParamBase.getData());
            }
        }
        return Result.buildSucc(ecmParamBaseList);
    }

    /**************************************** private method ****************************************************/

    private Result<EcmParamBase> buildHostParamBase(ClusterPhy clusterPhy, String roleName, List<String> masterHostList,
                                            Multimap<String, Long> role2ConfigIdsMultiMap, Long pluginId, Integer actionType) {
        HostsParamBase hostsParamBase = new HostsParamBase();
        hostsParamBase.setPhyClusterId(clusterPhy.getId().longValue());
        hostsParamBase.setPhyClusterName(clusterPhy.getCluster());
        hostsParamBase.setRoleName(roleName);
        hostsParamBase.setType(clusterPhy.getType());
        hostsParamBase.setMasterHostList(masterHostList);

        // ES????????????????????????, ????????????????????????
        if (!AriusObjUtils.isNull(role2ConfigIdsMultiMap) && !AriusObjUtils.isNull(actionType)) {
            List<Long> configs = (List<Long>) role2ConfigIdsMultiMap.get(roleName);
            hostsParamBase.setEsConfigAction(new EsConfigAction(actionType, configs));
        }

        // ES????????????????????????, ????????????????????????
        if (!AriusObjUtils.isNull(pluginId)) {
            hostsParamBase.setEsPluginAction(new EsPluginAction(actionType, pluginId));
        }

        RoleCluster roleCluster = roleClusterService.getByClusterIdAndRole(clusterPhy.getId().longValue(),
                roleName);
        if (null == roleCluster) {
            hostsParamBase.setEsVersion(clusterPhy.getEsVersion());
            // ?????????????????????????????????????????????????????????
            ESPackage esPackage = esPackageService.getByVersionAndType(clusterPhy.getEsVersion(), clusterPhy.getType());
            if (AriusObjUtils.isNull(esPackage)) {
                return Result.buildParamIllegal(String.format("??????????????????: %s ??????", clusterPhy.getEsVersion()));
            }
            hostsParamBase.setImageName(esPackage.getUrl());
            return Result.buildSucc(hostsParamBase);
        }

        // ??????role_cluster????????????ecm?????????es???????????????
        ESPackage esPackage = esPackageService.getByVersionAndType(roleCluster.getEsVersion(), ES_HOST.getCode());
        if (AriusObjUtils.isNull(esPackage)) {
            return Result.buildParamIllegal(String.format("??????????????????: %s ??????", roleCluster.getEsVersion()));
        }

        hostsParamBase.setPidCount(roleCluster.getPidCount());
        hostsParamBase.setEsVersion(roleCluster.getEsVersion());
        hostsParamBase.setImageName(esPackage.getUrl());

        List<String> hostList = roleClusterHostService
                .getHostNamesByRoleAndClusterId(clusterPhy.getId().longValue(), roleName);
        hostsParamBase.setHostList(hostList);
        if (!CollectionUtils.isEmpty(hostList)) {
            hostsParamBase.setNodeNumber(hostList.size());
        } else {
            hostsParamBase.setNodeNumber(0);
        }

        return Result.buildSucc(hostsParamBase);
    }

    private Result<EcmParamBase> buildElasticCloudParamBase(ClusterPhy clusterPhy, String roleName,
                                                    Multimap<String, Long> role2ConfigIdsMultiMap, Integer actionType) {
        ElasticCloudCommonActionParam elasticCloudCommonActionParam = ConvertUtil.obj2Obj(
				clusterPhy,
            ElasticCloudCommonActionParam.class);

        if (!AriusObjUtils.isNull(role2ConfigIdsMultiMap) && !AriusObjUtils.isNull(actionType)) {
            List<Long> configs = (List<Long>) role2ConfigIdsMultiMap.get(roleName);
            elasticCloudCommonActionParam.setEsConfigActions(new EsConfigAction(actionType, configs));
        }

        elasticCloudCommonActionParam.setPhyClusterId(clusterPhy.getId().longValue());
        elasticCloudCommonActionParam.setPhyClusterName(clusterPhy.getCluster());

        elasticCloudCommonActionParam.setRoleName(roleName);
        elasticCloudCommonActionParam.setMachineRoom(clusterPhy.getIdc());

        RoleCluster roleCluster = roleClusterService.getByClusterIdAndRole(clusterPhy.getId().longValue(),
                roleName);
        ESPackage esPackage = esPackageService.getByVersionAndType(roleCluster.getEsVersion(), ES_DOCKER.getCode());
        if (AriusObjUtils.isNull(esPackage)) {
            return Result.buildParamIllegal(String.format("??????????????????: %s ??????", roleCluster.getEsVersion()));
        }

        elasticCloudCommonActionParam.setNodeNumber(roleCluster.getPidCount());
        elasticCloudCommonActionParam.setEsVersion(roleCluster.getEsVersion());
        elasticCloudCommonActionParam.setImageName(esPackage.getUrl());

        return Result.buildSucc(elasticCloudCommonActionParam);
    }

    private Result<EcmParamBase> supplyCommonActionParamBase(Long clusterId, String roleName,
                                                             EcmParamBase actionParamBase) {
        ClusterPhy clusterPhy = esClusterPhyService.getClusterById(clusterId.intValue());
        if (null == clusterPhy) {
            return Result.buildNotExist(String.format(CLUSTER_NOT_EXIST, clusterId.intValue()));
        }

        AbstractEcmBaseHandle esClusterBaseHandle = getByClusterType(clusterPhy.getType());
        if (null == esClusterBaseHandle) {
            return Result.buildNotExist(UNKNOWN_TYPE);
        }

        if (actionParamBase instanceof HostsParamBase) {
            // ????????????????????????????????????
            HostsParamBase hostsParamBase = (HostsParamBase) actionParamBase;
            Result<String> result = getPortFromHost(clusterId, hostsParamBase.getRoleName());
            if(result.failed()) {
                return Result.buildFrom(result);
            }
            hostsParamBase.setPort(result.getData());
        }

        actionParamBase.setPhyClusterId(clusterId);
        actionParamBase.setPhyClusterName(clusterPhy.getCluster());
        if (AriusObjUtils.isBlank(roleName) || ESClusterTypeEnum.ES_HOST.getCode() == clusterPhy.getType()) {
            return Result.buildSucc(actionParamBase);
        }

        // ??????????????? ??????????????????
        String newRoleName = roleName.startsWith(clusterPhy.getCluster()) ? roleName
            : clusterPhy.getCluster() + "-" + roleName;
        ElasticCloudCommonActionParam elasticCloudCommonActionParam = (ElasticCloudCommonActionParam) actionParamBase;
        RoleCluster roleCluster = roleClusterService
                .getByClusterIdAndClusterRole(clusterId, newRoleName);
        if (null == roleCluster) {
            return Result.buildNotExist(String.format("%d?????????????????????%s???????????????", clusterId, roleName));
        }
        elasticCloudCommonActionParam.setMachineRoom(clusterPhy.getIdc());
        elasticCloudCommonActionParam.setNsTree(clusterPhy.getNsTree());
        elasticCloudCommonActionParam.setRoleName(roleCluster.getRole());

        return Result.buildSucc(elasticCloudCommonActionParam);
    }

    private Result<String> getPortFromHost(Long clusterId, String roleName) {
        List<RoleClusterHost> roleClusterHosts = roleClusterHostService.getByRoleAndClusterId(clusterId, roleName);
        if (CollectionUtils.isEmpty(roleClusterHosts)) {
            return Result.buildSucc(ClusterConstant.DEFAULT_PORT, "????????????????????????");
        }
        return Result.buildSucc(roleClusterHosts.get(0).getPort(), "????????????????????????");
    }

    private Result<EcmParamBase> buildActionParamBase(Long clusterId, String roleName) {
        ClusterPhy clusterPhy = esClusterPhyService.getClusterById(clusterId.intValue());
        if (null == clusterPhy) {
            return Result.buildNotExist(String.format(CLUSTER_NOT_EXIST, clusterId));
        }

        RoleCluster roleCluster = roleClusterService
                .getByClusterIdAndClusterRole(clusterId, roleName);
        if (null == roleCluster) {
            return Result.buildNotExist(String.format("%d?????????????????????%s???????????????", clusterId, roleName));
        }

        AbstractEcmBaseHandle esClusterBaseHandle = getByClusterType(clusterPhy.getType());
        if (null == esClusterBaseHandle) {
            return Result.buildNotExist(UNKNOWN_TYPE);
        }
        ElasticCloudCommonActionParam elasticCloudCommonActionParam = new ElasticCloudCommonActionParam(clusterId,
            roleName);

        elasticCloudCommonActionParam.setPhyClusterId(clusterId);
        elasticCloudCommonActionParam.setPhyClusterName(clusterPhy.getCluster());
        elasticCloudCommonActionParam.setType(clusterPhy.getType());

        elasticCloudCommonActionParam.setRoleName(roleCluster.getRole());
        elasticCloudCommonActionParam.setNsTree(clusterPhy.getNsTree());
        elasticCloudCommonActionParam.setMachineRoom(clusterPhy.getIdc());

        return Result.buildSucc(elasticCloudCommonActionParam);
    }

    private <T> Result<T> callESClusterBaseHandle(String methodName, Long clusterId, String operator,
                                           EcmParamBase ecmParamBase,
                                           BiFunction<EcmParamBase, AbstractEcmBaseHandle, Result<T>> function) {
        return callESClusterBaseHandle(methodName, clusterId, operator, ecmParamBase, function, true);
    }

    private <T> Result<T> callESClusterBaseHandle(String methodName, Long clusterId, String operator,
                                           EcmParamBase ecmParamBase,
                                           BiFunction<EcmParamBase, AbstractEcmBaseHandle, Result<T>> function,
                                           boolean recordOperate) {
        // ???????????????handler
        AbstractEcmBaseHandle abstractEcmBaseHandle = getByClusterType(ecmParamBase.getType());
        if (null == abstractEcmBaseHandle) {
            return Result.buildNotExist(UNKNOWN_TYPE);
        }

        // ????????????????????????
        Result<T> result = function.apply(ecmParamBase, abstractEcmBaseHandle);

        String infoLog = String.format(
                "class=ESClusterHandleServiceImpl||method=callESClusterBaseHandle||methodName={%s}||clusterId={%d}||result={%s}",
                methodName,
                clusterId,
                JSON.toJSONString(result)
        );
        LOGGER.info(infoLog);

        // ????????????
        if (recordOperate) {
            operateRecordService.save(CLUSTER, EXE, String.valueOf(clusterId),
                String.format("???????????? %s ???????????? %s ??????", clusterId, methodName), operator);
        }

        return result;
    }

    private AbstractEcmBaseHandle getByClusterType(Integer clusterType) {
        return ecmBaseHandleMap.get(clusterType);
    }

    private void deleteOdinTreeNodeAndLocalDbInfo(List<ElasticCloudCommonActionParam> elasticCloudActionParams,
                                                  ClusterPhy clusterPhy, String operator) {

        AtomicBoolean deleteAllTreeNodeFlag = deleteOdinTreeNode(elasticCloudActionParams);
        if (deleteAllTreeNodeFlag.get()) {
            LOGGER.info("class=ElasticClusterServiceImpl||method=delOdinTree||clusterId={}||clusterName={}||"
                        + "msg=success to delete cluster treeNode!",
                clusterPhy.getId(), clusterPhy.getCluster());
            //??????odin???????????????????????????
            deleteLocalClusterInfo(clusterPhy, operator);
        }
    }

    private AtomicBoolean deleteOdinTreeNode(List<ElasticCloudCommonActionParam> actionParam) {
        //??????????????????????????????
        int retryTime = 0;
        AtomicBoolean deleteAllTreeNodeFlag = new AtomicBoolean(false);
        while (retryTime++ < DELETE_ODIN_TREE_MAX_RETRY_TIMES) {
            actionParam.stream().filter(Objects::nonNull).forEach(param -> {
                try {
                    //??????2s??????odin??????????????????, ????????????tree??????
                    Thread.sleep(2 * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOGGER.error("class=ElasticClusterServiceImpl||method=deleteOdinTreeNode||errMsg=exception", e);
                }

                // ??????odin???????????????
                String ns = param.getRoleName() + "." + param.getPhyClusterName() + "." + param.getNsTree();
                remoteMonitorService.deleteTreeNode(ns, CloudClusterCreateParamConstant.ODIN_CATEGORY_SERVICE, ns,
                    ODIN_CATEGORY_LEVEL_2);

                if (param.getRoleName().equals(ESClusterNodeRoleEnum.DATA_NODE.getDesc())) {
                    // ??????odin?????????
                    String fatherNs = param.getPhyClusterName() + "." + param.getNsTree();
                    Result<Void> deleteFatherTreeNodeResult = remoteMonitorService.deleteTreeNode(fatherNs,
                        CloudClusterCreateParamConstant.ODIN_CATEGORY_GROUP, fatherNs, ODIN_CATEGORY_LEVEL_1);

                    if (deleteFatherTreeNodeResult.success()) {
                        LOGGER.info("class=ElasticClusterServiceImpl||method=delOdinTree||clusterName={}||role={}||"
                                    + "msg=success to delete cluster treeNode",
                            param.getPhyClusterName(), param.getRoleName());
                        deleteAllTreeNodeFlag.set(true);
                    }
                }
            });

            LOGGER.info("class=ElasticClusterServiceImpl||method=delOdinTree||clusterName={}||retryTime={}||"
                        + "msg=try to delete the Odin cluster",
                actionParam.get(0).getPhyClusterName(), retryTime);

            if (deleteAllTreeNodeFlag.get()) {
                return deleteAllTreeNodeFlag;
            }
        }

        return deleteAllTreeNodeFlag;
    }

    private void deleteLocalClusterInfo(ClusterPhy clusterPhy, String operator) {
        Result<Boolean> deleteClusterResult = esClusterPhyService.deleteClusterById(clusterPhy.getId(), operator);
        if (deleteClusterResult.failed()) {
            LOGGER
                .error("class=ElasticClusterServiceImpl||method=deleteLocalClusterInfo||clusterId={}||clusterName={}||"
                       + "msg=failed to delete local db cluster info",
                    clusterPhy.getId(), clusterPhy.getCluster());
        }

        //????????????
        Result<Void> deleteRoleClusterResult = roleClusterService.deleteRoleClusterByClusterId(clusterPhy.getId());
        if (deleteRoleClusterResult.failed()) {
            LOGGER.error("class=ElasticClusterServiceImpl||method=deleteLocalClusterInfo||clusterName={}||"
                         + "msg=failed to delete local db role cluster info",
                clusterPhy.getCluster());
        }

        //????????????
        Result<Void> deleteRoleClusterHostResult = roleClusterHostService.deleteByCluster(clusterPhy.getCluster());
        if (deleteRoleClusterHostResult.failed()) {
            LOGGER.error(
                "class=ElasticClusterServiceImpl||method=deleteLocalClusterInfo||roleClusterName=={}||roleClusterName={}||"
                         + "msg=failed to delete role host cluster info",
                clusterPhy.getCluster(), clusterPhy.getCluster());
        }
    }

    private Result<Void> validityCheck(Integer clusterId, String operator) {
        if (!ariusUserInfoService.isOPByDomainAccount(operator)) {
            return Result.buildFail("User has no permissions");
        }

        if(LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                    String.format("class=EcmHandleServiceImpl||method=validityCheck||debugMsg={clusterId={%d}, operator={%s}},", clusterId, operator)
            );
        }

         Result<Boolean> checkClusterPhyExitTemplateResult = checkClusterPhyExitTemplate(clusterId);
        if (checkClusterPhyExitTemplateResult.failed()) {
            return Result.buildFrom(checkClusterPhyExitTemplateResult);
        }

        return Result.buildSucc();
    }

    private Result<Boolean> checkClusterPhyExitTemplate(Integer clusterId) {
        //???????????????????????????????????????
        return Result.buildSucc();
    }

    private Result<List<ElasticCloudCommonActionParam>> deleteOdinMachine(ClusterPhy clusterPhy,
                                                                          List<RoleCluster> allRoles,
                                                                          String operator) {
        List<ElasticCloudCommonActionParam> elasticCloudActionParams = Lists.newArrayList();
        allRoles.stream().filter(role -> nonNull(role) && role.getRole() != null).forEach(role -> {
            // ??????????????????
            Result<EcmParamBase> actionParamBaseResult = buildActionParamBase(clusterPhy.getId().longValue(), clusterPhy.getCluster() + "-" + role.getRole());
            if (actionParamBaseResult.failed()) {
                LOGGER.error(
                    "class=EcmHandleServiceImpl||method=deleteESCluster||msg=failed to build the actionParamBaseResult");
            }

            elasticCloudActionParams.add((ElasticCloudCommonActionParam) actionParamBaseResult.getData());
            // ???????????????????????????odin??????????????????
            Result<EcmOperateAppBase> r = callESClusterBaseHandle("????????????", clusterPhy.getId().longValue(), operator,
                actionParamBaseResult.getData(), (withoutUsed, esClusterBaseHandle) -> esClusterBaseHandle
                    .removeESCluster(actionParamBaseResult.getData()));

            if (r.failed()) {
                LOGGER.error("class=EcmHandleServiceImpl||method=deleteESCluster||clusterName={}||" + "msg={}",
                    actionParamBaseResult.getData().getPhyClusterName(), r.getMessage());
            }
        });

        return Result.buildSucc(elasticCloudActionParams);
    }

    private Result<Void> checkValidForEsCluster(List<EcmParamBase> ecmParamBaseList) {
        if (CollectionUtils.isEmpty(ecmParamBaseList)) {
            return Result.buildFail("ecm????????????");
        }

        ClusterPhy clusterByName = esClusterPhyService.getClusterByName(ecmParamBaseList.get(0).getPhyClusterName());
        if (!AriusObjUtils.isNull(clusterByName)) {
            return Result.buildDuplicate("?????????????????????");
        }

        return Result.buildSucc();
    }
}
