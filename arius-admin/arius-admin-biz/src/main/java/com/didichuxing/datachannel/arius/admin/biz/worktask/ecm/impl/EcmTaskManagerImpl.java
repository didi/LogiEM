package com.didichuxing.datachannel.arius.admin.biz.worktask.ecm.impl;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.didichuxing.datachannel.arius.admin.biz.cluster.ClusterPhyManager;
import com.didichuxing.datachannel.arius.admin.biz.workorder.WorkOrderManager;
import com.didichuxing.datachannel.arius.admin.biz.workorder.content.ClusterOpHostContent;
import com.didichuxing.datachannel.arius.admin.biz.workorder.utils.WorkOrderTaskConverter;
import com.didichuxing.datachannel.arius.admin.biz.worktask.ecm.EcmTaskDetailManager;
import com.didichuxing.datachannel.arius.admin.biz.worktask.ecm.EcmTaskManager;
import com.didichuxing.datachannel.arius.admin.client.bean.common.Result;
import com.didichuxing.datachannel.arius.admin.client.bean.common.ecm.ESClusterRoleHost;
import com.didichuxing.datachannel.arius.admin.client.bean.common.ecm.EcmParamBase;
import com.didichuxing.datachannel.arius.admin.client.bean.common.ecm.EcmTaskBasic;
import com.didichuxing.datachannel.arius.admin.client.bean.common.ecm.EcmTaskDetail;
import com.didichuxing.datachannel.arius.admin.client.bean.common.ecm.elasticcloud.ElasticCloudCommonActionParam;
import com.didichuxing.datachannel.arius.admin.client.bean.common.ecm.host.HostsCreateActionParam;
import com.didichuxing.datachannel.arius.admin.client.bean.common.ecm.host.HostsParamBase;
import com.didichuxing.datachannel.arius.admin.client.bean.common.ecm.host.HostsScaleActionParam;
import com.didichuxing.datachannel.arius.admin.client.bean.common.ecm.response.EcmOperateAppBase;
import com.didichuxing.datachannel.arius.admin.client.bean.common.ecm.response.EcmTaskStatus;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.cluster.ESClusterDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.task.ecm.EcmTaskDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.vo.order.detail.OrderDetailBaseVO;
import com.didichuxing.datachannel.arius.admin.client.constant.ecm.EcmHostStatusEnum;
import com.didichuxing.datachannel.arius.admin.client.constant.ecm.EcmTaskStatusEnum;
import com.didichuxing.datachannel.arius.admin.client.constant.ecm.EcmTaskTypeEnum;
import com.didichuxing.datachannel.arius.admin.client.constant.operaterecord.OperationEnum;
import com.didichuxing.datachannel.arius.admin.client.constant.resource.ESClusterTypeEnum;
import com.didichuxing.datachannel.arius.admin.common.Tuple;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ClusterPhy;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ecm.RoleCluster;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ecm.RoleClusterHost;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.workorder.detail.BaseClusterHostOrderDetail;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.workorder.ecm.EcmTask;
import com.didichuxing.datachannel.arius.admin.common.bean.po.task.ecm.EcmTaskPO;
import com.didichuxing.datachannel.arius.admin.common.component.SpringTool;
import com.didichuxing.datachannel.arius.admin.common.constant.AdminConstant;
import com.didichuxing.datachannel.arius.admin.common.constant.ClusterConstant;
import com.didichuxing.datachannel.arius.admin.common.constant.arius.AriusUser;
import com.didichuxing.datachannel.arius.admin.common.event.ecm.EcmTaskEditEvent;
import com.didichuxing.datachannel.arius.admin.common.event.resource.ClusterPhyHealthEvent;
import com.didichuxing.datachannel.arius.admin.common.exception.EcmRemoteException;
import com.didichuxing.datachannel.arius.admin.common.threadpool.AriusScheduleThreadPool;
import com.didichuxing.datachannel.arius.admin.common.util.AriusObjUtils;
import com.didichuxing.datachannel.arius.admin.common.util.ConvertUtil;
import com.didichuxing.datachannel.arius.admin.common.util.ListUtils;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.ecm.EcmHandleService;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.physic.ClusterPhyService;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.physic.RoleClusterHostService;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.physic.RoleClusterService;
import com.didichuxing.datachannel.arius.admin.core.service.es.ESClusterService;
import com.didichuxing.datachannel.arius.admin.persistence.component.ESOpTimeoutRetry;
import com.didichuxing.datachannel.arius.admin.persistence.mysql.task.EcmTaskDAO;
import com.didichuxing.datachannel.arius.admin.remote.elasticcloud.bean.bizenum.EcmActionEnum;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.didichuxing.datachannel.arius.admin.client.constant.ecm.EcmHostStatusEnum.*;
import static com.didichuxing.datachannel.arius.admin.client.constant.ecm.EcmTaskStatusEnum.CANCEL;
import static com.didichuxing.datachannel.arius.admin.client.constant.operaterecord.OperationEnum.ADD;
import static com.didichuxing.datachannel.arius.admin.client.constant.operaterecord.OperationEnum.EDIT;
import static com.didichuxing.datachannel.arius.admin.client.constant.resource.ESClusterNodeRoleEnum.CLIENT_NODE;
import static com.didichuxing.datachannel.arius.admin.client.constant.resource.ESClusterNodeRoleEnum.MASTER_NODE;
import static com.didichuxing.datachannel.arius.admin.client.constant.resource.ESClusterTypeEnum.ES_DOCKER;
import static com.didichuxing.datachannel.arius.admin.client.constant.resource.ESClusterTypeEnum.ES_HOST;
import static com.didichuxing.datachannel.arius.admin.remote.zeus.bean.constant.ZeusClusterActionEnum.EXPAND;
import static com.didichuxing.datachannel.arius.admin.remote.zeus.bean.constant.ZeusClusterActionEnum.SHRINK;

/**
 * ES??????????????????
 * @author didi
 * @since 2020-08-24
 */
@Service
@NoArgsConstructor
public class EcmTaskManagerImpl implements EcmTaskManager {
    private static final Logger    LOGGER                                = LoggerFactory
        .getLogger(EcmTaskManagerImpl.class);

    @Value("${es.client.cluster.port}")
    private String                 esClusterClientPort;

    @Autowired
    private EcmTaskDAO             ecmTaskDao;

    @Autowired
    private EcmHandleService       ecmHandleService;

    @Autowired
    private ClusterPhyService      clusterPhyService;

    @Autowired
    private ClusterPhyManager      clusterPhyManager;

    @Autowired
    private RoleClusterService     roleClusterService;

    @Autowired
    private RoleClusterHostService roleClusterHostService;

    @Autowired
    private EcmTaskDetailManager   ecmTaskDetailManager;

    @Autowired
    private ESClusterService       esClusterService;

    @Autowired
    private AriusScheduleThreadPool ariusScheduleThreadPool;

    @Autowired
    private WorkOrderManager       workOrderManager;

    @Override
    public boolean existUnClosedEcmTask(Long phyClusterId) {
        List<EcmTaskPO> notFinishedTasks = ecmTaskDao.listUndoEcmTaskByClusterId(phyClusterId);
        return !AriusObjUtils.isEmptyList(notFinishedTasks);
    }

    @Override
    public Result<Long> saveEcmTask(EcmTaskDTO ecmTaskDTO) {
        //?????????nodeNumber ???0?????????
        List<EcmParamBase> filteredEcmParamBaseList = ecmTaskDTO.getEcmParamBaseList().stream()
            .filter(elem -> elem.getNodeNumber() != null && elem.getNodeNumber() > 0).collect(Collectors.toList());

        EcmTaskPO ecmTaskPO = ConvertUtil.obj2Obj(ecmTaskDTO, EcmTaskPO.class);
        ecmTaskPO.setClusterNodeRole(ListUtils.strList2String(
            filteredEcmParamBaseList.stream().map(EcmParamBase::getRoleName).collect(Collectors.toList())));
        ecmTaskPO.setHandleData(ConvertUtil.obj2Json(filteredEcmParamBaseList));
        //?????????????????? ?????????
        ecmTaskPO.setStatus(EcmTaskStatusEnum.WAITING.getValue());
        if (ecmTaskDao.save(ecmTaskPO) < 1) {
            // ????????????
            return Result.buildFail(ecmTaskPO.getTitle());
        }
        return Result.buildSucc(ecmTaskPO.getId());
    }

    @Override
    public List<EcmTask> listEcmTask() {
        return ConvertUtil.list2List(ecmTaskDao.listAll(), EcmTask.class);
    }

    @Override
    public List<EcmTask> listRunningEcmTask() {
        return ConvertUtil.list2List(ecmTaskDao.listRunningTasks(), EcmTask.class);
    }

    @Override
    public Result<EcmTaskBasic> getEcmTaskBasicByTaskId(Long taskId) {
        EcmTaskPO ecmTaskPO = ecmTaskDao.getById(taskId);
        if (ecmTaskPO == null) {
            return Result.buildFail("???????????????");
        }
        EcmTask ecmTask = ConvertUtil.obj2Obj(ecmTaskPO, EcmTask.class);
        EcmTaskBasic ecmTaskBasic = ConvertUtil.obj2Obj(ecmTaskPO, EcmTaskBasic.class);

        if (EcmTaskTypeEnum.NEW.getCode() == ecmTaskPO.getOrderType()
            && ESClusterTypeEnum.ES_HOST.getCode() == ecmTaskBasic.getType()) {
            // ?????????????????????, ???????????????????????????
            Map<String, EcmParamBase> ecmParamBaseMap = WorkOrderTaskConverter.convert2EcmParamBaseMap(ecmTask);
            HostsCreateActionParam ecmCreateParamBase = (HostsCreateActionParam) ecmParamBaseMap
                .getOrDefault(MASTER_NODE.getDesc(), new HostsCreateActionParam());
            ecmTaskBasic.setClusterName(ecmCreateParamBase.getPhyClusterName());
            ecmTaskBasic.setIdc(ecmCreateParamBase.getIdc());
            ecmTaskBasic.setNsTree(ecmCreateParamBase.getNsTree());
            ecmTaskBasic.setDesc(ecmCreateParamBase.getDesc());
            ecmTaskBasic.setEsVersion(ecmCreateParamBase.getEsVersion());
            ecmTaskBasic.setImageName(ecmCreateParamBase.getImageName());
            return Result.buildSucc(ecmTaskBasic);
        }

        // ????????????????????????, ????????????????????????
        ClusterPhy clusterPhy = clusterPhyService.getClusterById(ecmTaskPO.getPhysicClusterId().intValue());
        if (clusterPhy != null) {
            ecmTaskBasic.setClusterName(clusterPhy.getCluster());
            ecmTaskBasic.setIdc(clusterPhy.getIdc());
            ecmTaskBasic.setNsTree(clusterPhy.getNsTree());
            ecmTaskBasic.setDesc(clusterPhy.getDesc());
            ecmTaskBasic.setEsVersion(clusterPhy.getEsVersion());
            ecmTaskBasic.setImageName(clusterPhy.getImageName());
        }

        // ??????????????????????????????????????????
        ecmTaskBasic.setCreateTime(ecmTaskPO.getCreateTime());
        List<EcmTaskDetail> ecmTaskDetails = ecmTaskDetailManager.getEcmTaskDetailInOrder(taskId);
        Optional<Date> optionalDate = ecmTaskDetails.stream().map(EcmTaskDetail::getUpdateTime).distinct().max(Date::compareTo);
        optionalDate.ifPresent(ecmTaskBasic::setUpdateTime);

        return Result.buildSucc(ecmTaskBasic);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<EcmOperateAppBase> savaAndActionEcmTask(Long taskId, String operator) {
        //1. ??????ECM???????????????
        EcmTask ecmTask = getEcmTask(taskId);
        if (AriusObjUtils.isNull(ecmTask)) {
            return Result.buildFail("Ecm???????????????");
        }
        if (EcmTaskStatusEnum.RUNNING.getValue().equals(ecmTask.getStatus())) {
            return Result.buildFail("?????????????????????, ??????????????????");
        }
        List<EcmParamBase> ecmParamBaseList = WorkOrderTaskConverter.convert2EcmParamBaseList(ecmTask);
        if (CollectionUtils.isEmpty(ecmParamBaseList)) {
            return Result.buildFail("????????????????????????");
        }

        //2. ??????ES??????????????????
        Result<Long> saveResult = ecmHandleService.saveESCluster(ecmParamBaseList);
        if (saveResult.failed()) {
            return Result.buildFail("????????????????????????");
        }

        //3. ??????ES??????Id??????ECMTask
        ecmTask.setPhysicClusterId(saveResult.getData());
        ecmTask.setHandleData(ConvertUtil.obj2Json(ecmParamBaseList));
        updateEcmTask(ecmTask);
        
        //4. ????????????, ????????????master?????????ECM??????
        Result<EcmOperateAppBase> actionRet = actionEcmTaskForMasterNode(ecmParamBaseList, taskId, operator);
        if (actionRet.failed()) {
            //????????????????????????????????????
            throw new EcmRemoteException(actionRet.getMessage());
        }

        return actionRet;
    }

    @Override
    public Result<Void> retryClusterEcmTask(Long taskId, String operator) {
        //1. ??????ECM???????????????
        EcmTask ecmTask = getEcmTask(taskId);
        if (AriusObjUtils.isNull(ecmTask)) {
            return Result.buildFail("Ecm???????????????");
        }
        List<EcmParamBase> ecmParamBaseList = WorkOrderTaskConverter.convert2EcmParamBaseList(ecmTask);
        if (CollectionUtils.isEmpty(ecmParamBaseList)) {
            return Result.buildFail("????????????????????????");
        }

        //2.?????????????????????zeus??????id????????????
        for (EcmParamBase ecmParamBase : ecmParamBaseList) {
            HostsParamBase hostsParamBase = (HostsParamBase) ecmParamBase;
            hostsParamBase.setTaskId(null);
        }
        ecmTask.setHandleData(JSONArray.toJSONString(ecmParamBaseList));

        //3.??????task_detail???????????????
        ecmTaskDetailManager.deleteEcmTaskDetailsByTaskOrder(taskId);

        //4.???arius_work_task???es_work_order_task?????????????????????????????????waiting
        ecmTask.setStatus(EcmTaskStatusEnum.WAITING.getValue());
        updateEcmTask(ecmTask);

        return Result.buildSucc();
    }

    @Override
    public Result<EcmOperateAppBase> actionClusterEcmTask(Long taskId, String operator) {
        //1. ??????ECM???????????????
        EcmTask ecmTask = getEcmTask(taskId);
        if (AriusObjUtils.isNull(ecmTask)) {
            return Result.buildFail("Ecm???????????????");
        }

        if (EcmTaskStatusEnum.RUNNING.getValue().equals(ecmTask.getStatus())) {
            return Result.buildParamIllegal("?????????????????????, ??????????????????");
        }

        List<EcmParamBase> ecmParamBaseList = WorkOrderTaskConverter.convert2EcmParamBaseList(ecmTask);
        if (CollectionUtils.isEmpty(ecmParamBaseList)) {
            return Result.buildFail("????????????????????????");
        }

        //2. ????????????????????????????????????
        for (EcmParamBase ecmParamBase : ecmParamBaseList) {
            //2.1 ?????????????????????
            if (!AriusObjUtils.isNull(ecmParamBase.getTaskId())
                && isTaskActed(EcmTaskStatusEnum.SUCCESS, ecmParamBase, ecmTask.getOrderType(), operator)) {
                // ?????????????????????????????? & ??????????????????
                continue;
            } else if (!AriusObjUtils.isNull(ecmParamBase.getTaskId())
                       && !isTaskActed(EcmTaskStatusEnum.SUCCESS, ecmParamBase, ecmTask.getOrderType(), operator)) {
                // ?????????????????????????????? & ???????????????, ????????????return??????
                return Result.buildSucc();
            }

            //2.2 ??????ECM??????
            Result<EcmOperateAppBase> ret = runEcmTask(ecmParamBase, ecmTask.getOrderType(), operator);
            if (ret.failed()) {
                throw new EcmRemoteException(ret.getMessage());
            }

            //??????taskId???DB
            ecmParamBase.setTaskId(ret.getData().getTaskId());
            ecmTask.setStatus(EcmTaskStatusEnum.RUNNING.getValue());
            ecmTask.setHandleData(ConvertUtil.obj2Json(ecmParamBaseList));
            updateEcmTask(ecmTask);

            return Result.buildSucc(ret.getData());
        }

        return Result.buildSucc();
    }

    @Override
    public Result<EcmOperateAppBase> actionClusterEcmTask(Long taskId, EcmActionEnum ecmActionEnum, String hostname,
                                                          String operator) {
        EcmTaskPO ecmTask = ecmTaskDao.getById(taskId);
        if (AriusObjUtils.isNull(ecmTask)) {
            return Result.buildParamIllegal("?????????????????????");
        }

        //??????????????????
        if (EcmTaskStatusEnum.RUNNING.getValue().equals(ecmTask.getStatus())) {
            return Result.buildFail("??????????????????????????????, ??????????????????");
        }

        List<EcmParamBase> ecmParamBaseList = WorkOrderTaskConverter
            .convert2EcmParamBaseList(ConvertUtil.obj2Obj(ecmTask, EcmTask.class));
        for (EcmParamBase ecmParamBase : ecmParamBaseList) {
            if (!AriusObjUtils.isNull(ecmParamBase.getTaskId())
                && isTaskActed(EcmTaskStatusEnum.SUCCESS, ecmParamBase, ecmTask.getOrderType(), operator)) {
                // ?????????????????????????????? & ??????????????????
                continue;
            } else if (!AriusObjUtils.isNull(ecmParamBase.getTaskId())
                       && !isTaskActed(EcmTaskStatusEnum.SUCCESS, ecmParamBase, ecmTask.getOrderType(), operator)) {
                // ?????????????????????????????? & ?????????????????????
                // ??????ecm???????????????pause??????running
                ecmTask.setStatus(EcmTaskStatusEnum.RUNNING.getValue());
                updateEcmTask(ConvertUtil.obj2Obj(ecmTask, EcmTask.class));
                return ecmHandleService.actionUnfinishedESCluster(ecmActionEnum, ecmParamBase, hostname, operator);
            }

            // ???????????????
            if (EcmActionEnum.START.equals(ecmActionEnum)) {
                // ??????????????????????????????????????????, ??????continue??????, ???????????????????????????
                return actionClusterEcmTask(taskId, operator);
            }

            // ????????????????????????????????????
            return Result.buildFail("???????????????????????????, ????????????");
        }
        return Result.buildSucc();
    }

    /**
     * ?????????????????????ip???port???????????????rack???????????????
     *
     * @param clusterName ??????????????????
     * @param ip          ip??????
     * @return ????????????data?????????rack????????????????????????????????????cold???????????????*
     */
    @Override
    public String judgeColdRackFromEcmTaskOfClusterNewOrder(String clusterName, String ip) {
        Result<String> ecmTaskOrderDetailInfo = getEcmTaskOrderDetailInfo(clusterName);
        if (ecmTaskOrderDetailInfo.failed()) {
            return AdminConstant.DEFAULT_HOT_RACK;
        }
        ClusterOpHostContent clusterOpHostContent = ConvertUtil.str2ObjByJson(ecmTaskOrderDetailInfo.getData(),
                ClusterOpHostContent.class);

        //?????????????????????????????????http????????????
        Set<String> coldHttpAddress = clusterOpHostContent.getRoleClusterHosts()
                .stream()
                .filter(ESClusterRoleHost::getBeCold)
                .map(ESClusterRoleHost::getHostname)
                .collect(Collectors.toSet());

        //?????????????????????rack??????cold
        if (coldHttpAddress.contains(ip)) {
            return AdminConstant.DEFAULT_COLD_RACK;
        }

        return AdminConstant.DEFAULT_HOT_RACK;
    }

    @Override
    public Result<Void> cancelClusterEcmTask(Long taskId, String operator) {
        EcmTask ecmTask = getEcmTask(taskId);
        if (AriusObjUtils.isNull(ecmTask)) {
            return Result.buildParamIllegal("?????????????????????");
        }

        List<EcmParamBase> ecmParamBaseList = WorkOrderTaskConverter
                .convert2EcmParamBaseList(ConvertUtil.obj2Obj(ecmTask, EcmTask.class));
        for (EcmParamBase ecmParamBase : ecmParamBaseList) {
            if (!AriusObjUtils.isNull(ecmParamBase) && AriusObjUtils.isNull(ecmParamBase.getTaskId())) {
                // ?????????????????????zeus????????????cancel???????????????task_detail??????
                saveTaskDetailInfoWithoutZeusTaskId(ecmParamBase, taskId, CANCEL);
            } else if (!AriusObjUtils.isNull(ecmParamBase) && !AriusObjUtils.isNull(ecmParamBase.getTaskId())) {
                ecmHandleService.actionUnfinishedESCluster(EcmActionEnum.CANCEL, ecmParamBase, null, operator);
            }
        }

        //??????????????????
        ecmTask.setStatus(EcmTaskStatusEnum.CANCEL.getValue());
        updateEcmTask(ecmTask);
        return Result.buildSucc();
    }

    @Override
    public Result<Void> pauseClusterEcmTask(Long taskId, String operator) {
        EcmTaskPO ecmTask = ecmTaskDao.getById(taskId);
        if (AriusObjUtils.isNull(ecmTask)) {
            return Result.buildParamIllegal("?????????????????????");
        }

        //??????????????????
        if (!EcmTaskStatusEnum.RUNNING.getValue().equals(ecmTask.getStatus())) {
            return Result.buildFail("??????????????????????????????running??????, ????????????????????????");
        }

        List<EcmParamBase> ecmParamBaseList = WorkOrderTaskConverter
                .convert2EcmParamBaseList(ConvertUtil.obj2Obj(ecmTask, EcmTask.class));
        for (EcmParamBase ecmParamBase : ecmParamBaseList) {
            if (!AriusObjUtils.isNull(ecmParamBase)
                    && isTaskActed(EcmTaskStatusEnum.RUNNING, ecmParamBase, ecmTask.getOrderType(), operator)) {
                ecmHandleService.actionUnfinishedESCluster(EcmActionEnum.PAUSE, ecmParamBase, null, operator);
            }
        }

        // ????????????????????????????????????????????????????????????????????????
        ecmTask.setStatus(EcmTaskStatusEnum.PAUSE.getValue());
        updateEcmTask(ConvertUtil.obj2Obj(ecmTask, EcmTask.class));
        return Result.buildSucc();
    }

    @Override
    public EcmTask getEcmTask(Long id) {
        return ConvertUtil.obj2Obj(ecmTaskDao.getById(id), EcmTask.class);
    }

    @Override
    public boolean updateEcmTask(EcmTask ecmTask) {
        int ret = ecmTaskDao.update(ConvertUtil.obj2Obj(ecmTask, EcmTaskPO.class));
        if (ret > 0) {
            SpringTool.publish(new EcmTaskEditEvent(this, ecmTask));
        }
        return ret > 0;
    }

    @Override
    public EcmTaskPO getRunningEcmTaskByClusterId(Integer physicClusterId) {
        return ecmTaskDao.getUsefulEcmTaskByClusterId(physicClusterId);
    }

    @Override
    public Result<Void> actionClusterHostEcmTask(Long taskId, EcmActionEnum ecmActionEnum, String hostname, String operator) {
        EcmTaskPO ecmTask = ecmTaskDao.getById(taskId);
        if (AriusObjUtils.isNull(ecmTask)) {
            return Result.buildParamIllegal("?????????????????????");
        }

        List<EcmParamBase> ecmParamBaseList = WorkOrderTaskConverter
                .convert2EcmParamBaseList(ConvertUtil.obj2Obj(ecmTask, EcmTask.class));
        for (EcmParamBase ecmParamBase : ecmParamBaseList) {
            if (!AriusObjUtils.isNull(ecmParamBase)) {
                ecmHandleService.actionUnfinishedESCluster(ecmActionEnum, ecmParamBase, hostname, operator);
            }
        }

        // ????????????????????????RUNNING??????
        ecmTask.setStatus(EcmTaskStatusEnum.RUNNING.getValue());
        updateEcmTask(ConvertUtil.obj2Obj(ecmTask, EcmTask.class));
        return Result.buildSucc();
    }

    @Override
    public Result<EcmTask> getUsefulEcmTaskByClusterName(String clusterName) {
        ClusterPhy clusterPhy = clusterPhyService.getClusterByName(clusterName);
        if (AriusObjUtils.isNull(clusterPhy)) {
            return Result.buildFail("??????????????????????????????????????????");
        }
        return Result.buildSucc(ConvertUtil.obj2Obj(ecmTaskDao.getUsefulEcmTaskByClusterId(clusterPhy.getId()), EcmTask.class));
    }

    @Override
    public Result<String> getEcmTaskOrderDetailInfo(String cluster) {
        if (AriusObjUtils.isBlack(cluster)) {
            return Result.buildFail("cluster name ??????");
        }

        Result<EcmTask> usefulWorkOrderTaskByClusterName = getUsefulEcmTaskByClusterName(cluster);
        if (usefulWorkOrderTaskByClusterName.failed()) {
            return Result.buildFail("??????????????????????????????");
        }
        EcmTask task = usefulWorkOrderTaskByClusterName.getData();
        if (AriusObjUtils.isNull(task)) {
            return Result.buildFail("??????????????????????????????????????????");
        }

        List<EcmParamBase> ecmParamBases = WorkOrderTaskConverter.convert2EcmParamBaseList(task);
        if (CollectionUtils.isEmpty(ecmParamBases)) {
            return Result.buildFail("??????????????????????????????");
        }
        OrderDetailBaseVO orderDetailBaseVO = workOrderManager.getById(task.getWorkOrderId()).getData();

        return Result.buildSucc(orderDetailBaseVO.getDetail(), "ecm???????????????????????????????????????");
    }

    @Override
    public EcmTaskStatusEnum refreshEcmTask(EcmTask ecmTask) {
        if ((SUCCESS.getValue().equals(ecmTask.getStatus()) || CANCEL.getValue().equals(ecmTask.getStatus()))) {
            return EcmTaskStatusEnum.SUCCESS;
        }

        List<EcmParamBase> ecmParamBases = WorkOrderTaskConverter.convert2EcmParamBaseList(ecmTask);
        ecmParamBases.forEach(ecmParam -> ecmParam.setWorkOrderId(ecmTask.getId()));
        Set<EcmTaskStatusEnum> subOrderTaskStatus = Sets.newHashSet();

        long startTime = System.currentTimeMillis();
        ecmParamBases.forEach(ecmParam -> subOrderTaskStatus.add(doRefreshEcmTask(ecmParam, ecmTask)));
        LOGGER.info(
            "class=EcmTaskManagerImpl||method=refreshEcmTask||clusterId={}" + "||orderType={}||consumingTime={}",
            ecmTask.getPhysicClusterId(), ecmTask.getOrderType(), System.currentTimeMillis() - startTime);

        EcmTaskStatusEnum mergedStatusEnum = EcmTaskStatusEnum.calTaskStatus(subOrderTaskStatus);

        if(postProcess(ecmTask, mergedStatusEnum).failed()) {
            mergedStatusEnum = EcmTaskStatusEnum.FAILED;
        }
        
        ecmTask.setStatus(mergedStatusEnum.getValue());
        updateEcmTask(ecmTask);
        return mergedStatusEnum;
    }

    /*************************************** private method ***************************************/
    /**
     * ????????????master?????????ES??????
     * @param ecmParamBaseList    ES????????????
     * @param taskId              ??????Id
     * @param operator            ?????????
     * @return
     */
    private Result<EcmOperateAppBase> actionEcmTaskForMasterNode(List<EcmParamBase> ecmParamBaseList, Long taskId, String operator) {
        EcmTask ecmTask = getEcmTask(taskId);
        if (null == ecmTask) {
            return Result.buildFail("ECM????????????");
        }
        Map<String, EcmParamBase> role2EcmParamBaseMap = ConvertUtil.list2Map(ecmParamBaseList,
            EcmParamBase::getRoleName, ecmParamBase -> ecmParamBase);

        EcmParamBase ecmParamBase = role2EcmParamBaseMap.get(MASTER_NODE.getDesc());

        Result<EcmOperateAppBase> runEcmTaskForMasterNodeRet = runEcmTask(ecmParamBase, ecmTask.getOrderType(), operator);
        if (runEcmTaskForMasterNodeRet.success()) {
            //??????taskId???DB
            ecmParamBase.setTaskId(runEcmTaskForMasterNodeRet.getData().getTaskId());
            ecmTask.setStatus(EcmTaskStatusEnum.RUNNING.getValue());
            ecmTask.setHandleData(ConvertUtil.obj2Json(ecmParamBaseList));
            updateEcmTask(ecmTask);

            //??????es role cluster note??????
            updateRoleClusterNumber(ecmTask, ecmParamBase);
            return Result.buildSucc(runEcmTaskForMasterNodeRet.getData());
        }
        
        return Result.buildFail();
    }
    
    private Result<EcmOperateAppBase> runEcmTask(EcmParamBase ecmParamBase, Integer orderType, String operator) {
        Result<EcmOperateAppBase> result;
        if (EcmTaskTypeEnum.NEW.getCode() == orderType) {
            result = ecmHandleService.startESCluster(ecmParamBase, operator);
        } else if (EcmTaskTypeEnum.EXPAND.getCode() == orderType) {
            if (ecmParamBase instanceof HostsScaleActionParam) {
                HostsScaleActionParam hostScaleActionParam = (HostsScaleActionParam) ecmParamBase;
                hostScaleActionParam.setAction(EXPAND.getValue());
            }

            result = ecmHandleService.scaleESCluster(ecmParamBase, operator);
        } else if (EcmTaskTypeEnum.SHRINK.getCode() == orderType) {
            if (ecmParamBase instanceof HostsScaleActionParam) {
                HostsScaleActionParam hostScaleActionParam = (HostsScaleActionParam) ecmParamBase;
                hostScaleActionParam.setAction(SHRINK.getValue());
            }

            result = ecmHandleService.scaleESCluster(ecmParamBase, operator);
        } else if (EcmTaskTypeEnum.RESTART.getCode() == orderType) {
            result = ecmHandleService.restartESCluster(ecmParamBase, operator);
        } else if (EcmTaskTypeEnum.UPGRADE.getCode() == orderType) {
            result = ecmHandleService.upgradeESCluster(ecmParamBase, operator);
        } else {
            return Result.buildFail("??????????????????, ??????Code:" + orderType);
        }
        return result;
    }

    /**????????????????????????EcmTask??????????????????????????????*/
    private EcmTaskStatusEnum doRefreshEcmTask(EcmParamBase ecmParam, EcmTask ecmTask) {
        if (AriusObjUtils.isNull(ecmParam.getTaskId())) {
            return EcmTaskStatusEnum.PAUSE;
        }
        Result<List<EcmTaskStatus>> taskStatus;
        List<EcmTaskStatus> remoteStatuses;
        try {
            //1.????????????
            taskStatus = ecmHandleService.getESClusterStatus(ecmParam, ecmTask.getOrderType(), null);
            if (taskStatus.failed()) {
                return EcmTaskStatusEnum.FAILED;
            }

            remoteStatuses = taskStatus.getData();
            if (CollectionUtils.isEmpty(remoteStatuses)) {
                return EcmTaskStatusEnum.SUCCESS;
            }

            if (!checkEcmTaskStatusValid(remoteStatuses)) {
                return EcmTaskStatusEnum.RUNNING;
            }
            //2.??????taskDetail???
            updateTaskDetailByTaskStatus(ecmParam, remoteStatuses);

        } catch (Exception e) {
            LOGGER.error("class=EcmTaskManagerImpl||method=doRefreshEcmTask||ecmTaskId={}||msg={}", ecmTask.getId(), e.getStackTrace());
            return EcmTaskStatusEnum.FAILED;
        }

        //5.??????????????????
        Set<EcmTaskStatusEnum> ecmHostStatus = remoteStatuses.stream().map(r -> convertStatus(r.getStatusEnum())).collect(Collectors.toSet());
        return EcmTaskStatusEnum.calTaskStatus(ecmHostStatus);
    }

    /**????????????????????????????????????????????????*/
    private boolean isTaskActed(EcmTaskStatusEnum ecmTaskStatusEnum, EcmParamBase ecmParamBase, Integer orderType, String operator) {
        Result<List<EcmTaskStatus>> result = ecmHandleService.getESClusterStatus(ecmParamBase, orderType, operator);
        if (result.failed()) {
            // ????????????????????????, ???????????????false
            return false;
        }

        Set<EcmTaskStatusEnum> statusEnumSet = new HashSet<>();
        for (EcmTaskStatus ecmTaskStatus : result.getData()) {
            statusEnumSet.add(convertStatus(ecmTaskStatus.getStatusEnum()));
        }

        EcmTaskStatusEnum getEcmTaskStatusEnum = EcmTaskStatusEnum.calTaskStatus(statusEnumSet);
        return ecmTaskStatusEnum.equals(getEcmTaskStatusEnum);
    }

    /**??????taskDetail???*/
    private void updateTaskDetailByTaskStatus(EcmParamBase ecmParam, List<EcmTaskStatus> remoteStatuses) {
        Map<Long, EcmTaskStatus> id2ExistDetailMap = Maps.newHashMap();
        List<EcmTaskDetail> taskDetailsFromDb = ecmTaskDetailManager.getByOrderIdAndRoleAndTaskId(
            ecmParam.getWorkOrderId().intValue(), ecmParam.getRoleName(), ecmParam.getTaskId());

        //??????????????????Detail??????EcmTask
        remoteStatuses.stream().filter(Objects::nonNull)
            .forEach(r -> taskDetailsFromDb.stream().filter(Objects::nonNull).forEach(detailFromDb -> {
                if (detailFromDb.getHostname().equals(r.getHostname())) {
                    id2ExistDetailMap.put(detailFromDb.getId(), r);
                }
            }));

        if (MapUtils.isNotEmpty(id2ExistDetailMap)) {
            for (Map.Entry<Long, EcmTaskStatus> e : id2ExistDetailMap.entrySet()) {
                ecmTaskDetailManager.editEcmTaskDetail(buildEcmTaskDetail(e.getValue(), e.getKey(), ecmParam, EDIT));
            }
        } else {
            remoteStatuses.stream().filter(Objects::nonNull).forEach(
                status -> ecmTaskDetailManager.saveEcmTaskDetail(buildEcmTaskDetail(status, null, ecmParam, ADD)));
        }
    }

    /**
     * ????????????????????????????????????
     * @param ecmTask             ECM??????
     * @param mergedStatusEnum    ????????????
     * @return
     */
    private Result<Void> postProcess(EcmTask ecmTask, EcmTaskStatusEnum mergedStatusEnum) {
        if (!SUCCESS.getValue().equals(mergedStatusEnum.getValue()) && !hasRemoteTaskFailed(mergedStatusEnum)) {
            return Result.buildSucc();
        }

        List<EcmParamBase> ecmParamBases = WorkOrderTaskConverter.convert2EcmParamBaseList(ecmTask);

        //1. ????????????, ????????????role????????????data_source?????????
        //cleanUpUselessClusterInfoFromDB(mergedStatusEnum, ecmParamBases);
        if (hasRemoteTaskFailed(mergedStatusEnum)) {
            return Result.buildSucc();
        }

        //2. ????????????????????????, ????????????????????? ???????????????????????????
        if (updateClusterAddressWhenIsValid(ecmTask, mergedStatusEnum, ecmParamBases).failed()) {
            return Result.buildFail();
        }

        //3.??????ecm?????????????????????es_role_cluster_host??????
        saveOrEditHostInfoFromEcmTask(ecmTask, mergedStatusEnum);

        //4. ??????30s?????????????????????????????????????????????host??????
        delayCollectNodeSettingsTask(ecmParamBases);

        //5. ??????, ??????????????????
        updateEsClusterVersion(mergedStatusEnum, ecmTask);

        return Result.buildSucc();
    }

    /**
     * ???????????????????????????????????????????????????es_role_host?????????
     * @param ecmTask ecm??????
     * @param mergedStatusEnum ????????????????????????
     */
    private void saveOrEditHostInfoFromEcmTask(EcmTask ecmTask, EcmTaskStatusEnum mergedStatusEnum) {
        if (!EcmTaskStatusEnum.SUCCESS.equals(mergedStatusEnum)) {
            return;
        }

        switch (EcmTaskTypeEnum.valueOf(ecmTask.getOrderType())) {
            case EXPAND:
            case NEW: addHostInfoFromTaskOrder(ecmTask); break;
            case SHRINK: deleteRoleClusterAndHost(mergedStatusEnum,ecmTask); break;
            default: break;
        }
    }

    private void addHostInfoFromTaskOrder(EcmTask ecmTask) {
        // ???ecm?????????????????????????????????????????????
        Result<OrderDetailBaseVO> getOrderDetailResult = workOrderManager.getById(ecmTask.getWorkOrderId());
        if (getOrderDetailResult.failed()) {
            return;
        }
        BaseClusterHostOrderDetail baseClusterHostOrderDetail = (JSONObject.parseObject(getOrderDetailResult.getData().getDetail(),
                BaseClusterHostOrderDetail.class));

        // ???????????????????????????DB
        roleClusterHostService.createClusterNodeSettings(baseClusterHostOrderDetail.getRoleClusterHosts(), baseClusterHostOrderDetail.getPhyClusterName());

        // ??????es_role_cluster??????podNumber
        for (EcmParamBase ecmParamBase : WorkOrderTaskConverter.convert2EcmParamBaseList(ecmTask)) {
            HostsParamBase hostsParamBase = (HostsParamBase) ecmParamBase;
            if (CollectionUtils.isEmpty(hostsParamBase.getHostList())) {
                continue;
            }

            RoleCluster roleCluster = roleClusterService.getByClusterNameAndRole(baseClusterHostOrderDetail.getPhyClusterName(), hostsParamBase.getRoleName());
            if (roleCluster == null) {
                continue;
            }

            updatePodNumbers(ecmTask, roleCluster);
        }
    }

    private Result<Void> updateClusterAddressWhenIsValid(EcmTask ecmTask, EcmTaskStatusEnum mergedStatusEnum, List<EcmParamBase> ecmParamBases) {
        if (!hasCallBackRWAddress(mergedStatusEnum, ecmTask)) {
            return Result.buildSucc();
        }
        try {
            boolean succ = ESOpTimeoutRetry.esRetryExecuteWithGivenTime("?????????????????????????????????",
                    ClusterConstant.DEFAULT_RETRY_TIMES, () -> hasValidEsClusterReadAndWriteAddress(ecmTask, ecmParamBases), ClusterConstant::defaultRetryTime);
            if (succ) {
                updateClusterReadAndWriteAddress(ecmTask, ecmParamBases);
                // ????????????????????????????????????????????????es-client?????????
                SpringTool.publish(new ClusterPhyHealthEvent(this, getClusterPhyNameFromEcmParamBases(ecmParamBases)));
                return Result.buildSucc();
            }
        } catch (Exception e) {
            LOGGER.error("class=EcmTaskManagerImpl||method=postProcess||errMsg={}", e.getMessage());
        }
        return Result.buildFail();
    }

    private void delayCollectNodeSettingsTask(List<EcmParamBase> ecmParamBases) {
        ariusScheduleThreadPool.submitScheduleAtFixedDelayTask(() -> {
            String clusterPhyName = getClusterPhyNameFromEcmParamBases(ecmParamBases);
            roleClusterHostService.collectClusterNodeSettings(clusterPhyName);
        }, 30, 600);
    }

    private boolean hasValidEsClusterReadAndWriteAddress(EcmTask ecmTask, List<EcmParamBase> ecmParamBases) {
        List<String> clusterPhyRWAddress = Lists.newArrayList();
        if (ES_DOCKER.getCode() == ecmTask.getType()) {
            //docker???????????????
        } else if (ES_HOST.getCode() == ecmTask.getType()) {
            clusterPhyRWAddress = buildClusterReadAndWriteAddressForHost(ecmTask, ecmParamBases);
        }

        return esClusterService.syncGetClientAlivePercent(getClusterPhyNameFromEcmParamBases(ecmParamBases),
            null,ListUtils.strList2String(clusterPhyRWAddress)) > 0;
    }

    private String getClusterPhyNameFromEcmParamBases(List<EcmParamBase> ecmParamBases) {
        String clusterPhyName = null;
        for (EcmParamBase ecmParamBase : ecmParamBases) {
            if (StringUtils.isNotBlank(ecmParamBase.getPhyClusterName())) {
                clusterPhyName = ecmParamBase.getPhyClusterName();
            }
        }

        return clusterPhyName;
    }

    private void updateClusterReadAndWriteAddress(EcmTask ecmTask, List<EcmParamBase> ecmParamBases) {
        if (ES_DOCKER.getCode() == ecmTask.getType()) {
            //docker???????????????
        } else if (ES_HOST.getCode() == ecmTask.getType()) {
            List<String> clusterPhyRWAddress = buildClusterReadAndWriteAddressForHost(ecmTask, ecmParamBases);
            if (CollectionUtils.isNotEmpty(clusterPhyRWAddress)) {
                ESClusterDTO esClusterDTO = new ESClusterDTO();
                esClusterDTO.setId(ecmTask.getPhysicClusterId().intValue());
                esClusterDTO.setHttpAddress(ListUtils.strList2String(clusterPhyRWAddress));
                esClusterDTO.setHttpWriteAddress(ListUtils.strList2String(clusterPhyRWAddress));
                clusterPhyManager.editCluster(esClusterDTO, AriusUser.SYSTEM.getDesc(), null);
            }
        }
    }

    private List<String> buildClusterReadAndWriteAddressForHost(EcmTask ecmTask, List<EcmParamBase> ecmParamBases) {
        if (ecmTask.getOrderType().equals(EcmTaskTypeEnum.NEW.getCode())) {
            return buildClusterReadAndWriteAddressForHostWhenCreate(ecmParamBases);
        }

        if (ecmTask.getOrderType().equals(EcmTaskTypeEnum.SHRINK.getCode())
                || ecmTask.getOrderType().equals(EcmTaskTypeEnum.EXPAND.getCode())) {
            return buildClusterReadAndWriteAddressForHostWhenScale(ecmTask.getOrderType(),
                    ecmTask.getPhysicClusterId(),
                    ecmParamBases);
        }

        if(ecmTask.getOrderType().equals(EcmTaskTypeEnum.RESTART.getCode())) {
            return buildClusterReadAndWriteAddressForHostWhenRestart(ecmTask.getPhysicClusterId());
        }

        return new ArrayList<>();
    }

    private List<String> buildClusterReadAndWriteAddressForHostWhenRestart(Long physicClusterId) {
        ClusterPhy clusterPhy = clusterPhyService.getClusterById(Math.toIntExact(physicClusterId));
        if(AriusObjUtils.isNull(clusterPhy) || AriusObjUtils.isNull(clusterPhy.getHttpAddress())) {
            return Lists.newArrayList();
        }

        // ????????????????????????????????????http???????????????es????????????????????????????????????
        return ListUtils.string2StrList(clusterPhy.getHttpAddress());
    }

    private List<String> buildClusterReadAndWriteAddressForHostWhenCreate(List<EcmParamBase> ecmParamBases) {
        List<String> clusterPhyRWAddress = Lists.newArrayList();
        List<HostsParamBase> hostsParamBases = ConvertUtil.list2List(ecmParamBases, HostsParamBase.class);
        List<HostsParamBase> builds = hostsParamBases.stream()
                .filter(hostParam -> filterValidHttpAddressEcmParamBase(CLIENT_NODE.getDesc(), hostParam))
                .collect(Collectors.toList());
        //??????client??????, ???master??????????????????http????????????
        if (CollectionUtils.isEmpty(builds)) {
            builds = hostsParamBases.stream()
                    .filter(hostParam -> filterValidHttpAddressEcmParamBase(MASTER_NODE.getDesc(), hostParam))
                    .collect(Collectors.toList());
        }

        for (HostsParamBase hostsParamBase : builds) {
            List<String> hostList = hostsParamBase.getHostList();
            hostList.forEach(host -> clusterPhyRWAddress.add(host + ":" + hostsParamBase.getPort()));
        }

        return clusterPhyRWAddress;
    }

    private List<String> buildClusterReadAndWriteAddressForHostWhenScale(Integer orderType, Long physicClusterId, List<EcmParamBase> ecmParamBases) {
        // ?????????????????????clientnode???masternode?????????????????????
        List<String> clientHttpAddresses = getAddressesByByRoleAndClusterId(physicClusterId, CLIENT_NODE.getDesc());
        List<String> masterHttpAddresses = getAddressesByByRoleAndClusterId(physicClusterId, MASTER_NODE.getDesc());

        // ?????????????????????????????????????????????????????????????????????????????????
        List<HostsParamBase> hostsParamBases = ConvertUtil.list2List(ecmParamBases, HostsParamBase.class);
        for (HostsParamBase hostsParamBase : hostsParamBases) {
            if (CollectionUtils.isEmpty(hostsParamBase.getHostList())) {
                continue;
            }

            // ???????????????????????????????????????????????????
            List<String> shouldOperateAddresses = hostsParamBase
                    .getHostList()
                    .stream()
                    .map(hostname -> hostname + ":" + hostsParamBase.getPort())
                    .collect(Collectors.toList());

            // ????????????????????????????????????masternode???clientnode???????????????????????????
            if (hostsParamBase.getRoleName().equals(CLIENT_NODE.getDesc())) {
                if (orderType.equals(EcmTaskTypeEnum.SHRINK.getCode())) {
                    clientHttpAddresses.removeAll(shouldOperateAddresses);
                }

                if (orderType.equals(EcmTaskTypeEnum.EXPAND.getCode())) {
                    clientHttpAddresses.addAll(shouldOperateAddresses);
                }
            }

            if (hostsParamBase.getRoleName().equals(MASTER_NODE.getDesc())) {
                if (orderType.equals(EcmTaskTypeEnum.SHRINK.getCode())) {
                    masterHttpAddresses.removeAll(shouldOperateAddresses);
                }

                if (orderType.equals(EcmTaskTypeEnum.EXPAND.getCode())) {
                    masterHttpAddresses.addAll(shouldOperateAddresses);
                }
            }
        }

        // ??????client?????????????????????????????????client?????????ip??????, ????????????matser????????????
        if (!CollectionUtils.isEmpty(clientHttpAddresses)) {
            return clientHttpAddresses;
        } else {
            return masterHttpAddresses;
        }
    }

    private List<String> getAddressesByByRoleAndClusterId(Long clusterId, String role) {
        List<RoleClusterHost> roleClusterHosts = roleClusterHostService.getByRoleAndClusterId(clusterId, role);
        if (!CollectionUtils.isEmpty(roleClusterHosts)) {
            return roleClusterHosts
                    .stream()
                    .map(roleClusterHost -> roleClusterHost.getHostname() + ":" + roleClusterHost.getPort())
                    .collect(Collectors.toList());
        }
        return Lists.newArrayList();
    }

    private boolean filterValidHttpAddressEcmParamBase(String role, HostsParamBase hostsParamBase) {
        if (null == role) {
            return false;
        }

        return role.equals(hostsParamBase.getRoleName()) && CollectionUtils.isNotEmpty(hostsParamBase.getHostList())
               && null != hostsParamBase.getPort();
    }

    /**
     * ?????????????????????????????????????????????????????????????????????, ?????????????????????Table?????????
     * @param mergedStatusEnum ??????????????????
     * @return boolean
     */
    private boolean hasRemoteTaskFailed(EcmTaskStatusEnum mergedStatusEnum) {
        return FAILED.getValue().equals(mergedStatusEnum.getValue())
               || CANCELLED.getValue().equals(mergedStatusEnum.getValue())
               || KILL_FAILED.getValue().equals(mergedStatusEnum.getValue());
    }

    /**
     * ???????????????????????????????????????????????????????????????????????????
     *
     * @param mergedStatusEnum
     * @param ecmTask
     */
    private void deleteRoleClusterAndHost(EcmTaskStatusEnum mergedStatusEnum, EcmTask ecmTask) {
        for (EcmParamBase ecmParamBase : WorkOrderTaskConverter.convert2EcmParamBaseList(ecmTask)) {
            HostsParamBase hostsParamBase = (HostsParamBase) ecmParamBase;
            if (CollectionUtils.isEmpty(hostsParamBase.getHostList())) {
                continue;
            }

            RoleCluster roleCluster = roleClusterService.getByClusterNameAndRole(hostsParamBase.getPhyClusterName(), hostsParamBase.getRoleName());
            if (null == roleCluster) {
                continue;
            }

            // ??????es_role_cluster_host??????
            roleClusterHostService.deleteByHostNameAndRoleId(hostsParamBase.getHostList(), roleCluster.getId());

            // ??????es_role_cluster?????????pod????????? ????????????????????????????????????????????????????????????
            if (roleCluster.getPodNumber() < hostsParamBase.getHostList().size()) {
                return;
            }

            // ??????es_role_cluster?????????pod????????? ??????????????????????????????????????????????????????????????????
            updatePodNumbers(ecmTask, roleCluster);
        }
    }

    private void updatePodNumbers(EcmTask ecmTask, RoleCluster roleCluster) {
        RoleCluster updateRoleCluster = new RoleCluster();
        updateRoleCluster.setElasticClusterId(ecmTask.getPhysicClusterId());
        updateRoleCluster.setRole(roleCluster.getRole());
        updateRoleCluster.setPodNumber(roleClusterHostService.getPodNumberByRoleId(roleCluster.getId()));
        Result<Void> result = roleClusterService.updatePodByClusterIdAndRole(updateRoleCluster);
        if (result.failed()) {
            LOGGER.error(
                    "class=EcmTaskManagerImpl||method=deleteRoleCluster||clusterId={}||role={}"
                            + "msg=failed to update roleCluster",
                    ecmTask.getPhysicClusterId(), roleCluster.getRole());
        }
    }

    /**
     * ???????????????????????????????????????????????????
     * @param mergedStatusEnum
     * @param ecmTask
     */
    private void updateEsClusterVersion(EcmTaskStatusEnum mergedStatusEnum, EcmTask ecmTask) {
        if (!SUCCESS.getValue().equals(mergedStatusEnum.getValue())) {
            return;
        }
        
        if (EcmTaskTypeEnum.UPGRADE.getCode() != ecmTask.getOrderType()) {
            return;
        }

        ClusterPhy clusterPhy = clusterPhyService.getClusterById(ecmTask.getPhysicClusterId().intValue());
        if (AriusObjUtils.isNull(clusterPhy) || AriusObjUtils.isBlack(clusterPhy.getCluster())) {
            LOGGER.error("class=EcmTaskManagerImpl||method=callBackEsClusterVersion||clusterId={}||"
                         + "msg=the es cluster or the cluster name is empty",
                ecmTask.getPhysicClusterId());
            return;
        }

        List<EcmParamBase> ecmParamBases = WorkOrderTaskConverter.convert2EcmParamBaseList(ecmTask);

        Tuple<String, String> tuple = new Tuple<>();
        if (ecmTask.getType().equals(ESClusterTypeEnum.ES_HOST.getCode())) {
            tuple = getImageAndVersion(ecmParamBases, HostsParamBase::getImageName, HostsParamBase::getEsVersion,
                HostsParamBase.class);
        }

        if (ecmTask.getType().equals(ESClusterTypeEnum.ES_DOCKER.getCode())) {
            tuple = getImageAndVersion(ecmParamBases, ElasticCloudCommonActionParam::getImageName,
                ElasticCloudCommonActionParam::getEsVersion, ElasticCloudCommonActionParam.class);
        }

        //1??????????????????????????????
        for (String role : ecmTask.getClusterNodeRole().split(",")) {
            Result<Void> result = roleClusterService.updateVersionByClusterIdAndRole(ecmTask.getPhysicClusterId(), role,
                tuple.getV2());
            if (null != result && result.failed()) {
                LOGGER.error(
                    "class=EcmTaskManagerImpl||method=callBackEsClusterVersion||clusterId={}||role={}||version={}"
                             + "msg=failed to edit role cluster",
                    ecmTask.getPhysicClusterId(), role, tuple.getV2());
            }
        }

        //2????????????????????????
        ESClusterDTO esClusterDTO = new ESClusterDTO();
        esClusterDTO.setId(ecmTask.getPhysicClusterId().intValue());
        esClusterDTO.setImageName(tuple.getV1());
        esClusterDTO.setEsVersion(tuple.getV2());
        Result<Boolean> result = clusterPhyService.editCluster(esClusterDTO, AriusUser.SYSTEM.getDesc());
        if (null != result && result.failed()) {
            LOGGER.error("class=EcmTaskManagerImpl||method=callBackEsClusterVersion||clusterId={}||"
                         + "msg=failed to edit cluster",
                ecmTask.getPhysicClusterId());
        }
    }

    private <T> Tuple<String, String> getImageAndVersion(List<EcmParamBase> ecmParamBases, Function<T, String> funImage,
                                                         Function<T, String> funVersion, Class<T> type) {
        List<T> params = ConvertUtil.list2List(ecmParamBases, type);
        String changeImageName = params.stream()
            .filter(r -> !AriusObjUtils.isNull(r) && !AriusObjUtils.isBlack(funImage.apply(r))).map(funImage).findAny()
            .orElse(null);

        String changeEsVersion = params.stream()
            .filter(r -> !AriusObjUtils.isNull(r) && !AriusObjUtils.isBlack(funVersion.apply(r))).map(funVersion)
            .findAny().orElse(null);

        return new Tuple<>(changeImageName, changeEsVersion);
    }

    private EcmTaskDetail buildEcmTaskDetail(EcmTaskStatus status, Long detailId, EcmParamBase ecmParamBase,
                                             OperationEnum operation) {
        EcmTaskDetail ecmTaskDetail = new EcmTaskDetail();
        if (ADD.getCode() == operation.getCode()) {
            ecmTaskDetail.setWorkOrderTaskId(ecmParamBase.getWorkOrderId());
            ecmTaskDetail.setStatus(status.getStatusEnum().getValue());
            ecmTaskDetail.setHostname(status.getHostname());
            ecmTaskDetail.setRole(ecmParamBase.getRoleName());
            ecmTaskDetail.setGrp(status.getGroup());
            ecmTaskDetail.setIdx(status.getPodIndex());
            ecmTaskDetail.setTaskId(status.getTaskId().longValue());
        } else if (EDIT.getCode() == operation.getCode()) {
            ecmTaskDetail.setId(detailId);
            ecmTaskDetail.setStatus(status.getStatusEnum().getValue());
            ecmTaskDetail.setGrp(status.getGroup());
            ecmTaskDetail.setIdx(status.getPodIndex());
        }
        return ecmTaskDetail;
    }

    private boolean hasCallBackRWAddress(EcmTaskStatusEnum mergedStatusEnum, EcmTask ecmTask) {
        return SUCCESS.getValue().equals(mergedStatusEnum.getValue())
               && (EcmTaskTypeEnum.NEW.getCode() == ecmTask.getOrderType()
                   || EcmTaskTypeEnum.EXPAND.getCode() == ecmTask.getOrderType()
                   || EcmTaskTypeEnum.SHRINK.getCode() == ecmTask.getOrderType()
                   || EcmTaskTypeEnum.RESTART.getCode() == ecmTask.getOrderType());
    }

    private void updateRoleClusterNumber(EcmTask ecmTask, EcmParamBase ecmParamBase) {
        if (hasCallBackRoleNumber(ecmTask)) {
            RoleCluster roleCluster = ConvertUtil.obj2Obj(ecmParamBase, RoleCluster.class);
            roleCluster.setElasticClusterId(ecmParamBase.getPhyClusterId());
            roleCluster.setPodNumber(ecmParamBase.getNodeNumber());
            roleCluster.setRole(ecmParamBase.getRoleName());
            Result<Void> updateResult = roleClusterService.updatePodByClusterIdAndRole(roleCluster);
            if (updateResult.failed()) {
                LOGGER.error("class=EcmTaskManagerImpl||method=updateRoleClusterNumber||clusterId={}"
                             + "||msg=failed to update es role number",
                    ecmTask.getPhysicClusterId());
            }
        }
    }

    private boolean hasCallBackRoleNumber(EcmTask ecmTask) {
        return EcmTaskTypeEnum.EXPAND.getCode() == ecmTask.getOrderType()
               || EcmTaskTypeEnum.SHRINK.getCode() == ecmTask.getOrderType();
    }

    private EcmTaskStatusEnum convertStatus(EcmHostStatusEnum ecmHostStatusEnum) {
        if (EcmHostStatusEnum.SUCCESS.equals(ecmHostStatusEnum)) {
            return EcmTaskStatusEnum.SUCCESS;
        }
        if (EcmHostStatusEnum.UPDATED.equals(ecmHostStatusEnum)) {
            return EcmTaskStatusEnum.SUCCESS;
        }
        if (EcmHostStatusEnum.KILL_FAILED.equals(ecmHostStatusEnum)
            || EcmHostStatusEnum.TIMEOUT.equals(ecmHostStatusEnum)
            || EcmHostStatusEnum.FAILED.equals(ecmHostStatusEnum)) {
            return EcmTaskStatusEnum.FAILED;
        }
        if (EcmHostStatusEnum.KILLING.equals(ecmHostStatusEnum)
            || EcmHostStatusEnum.RUNNING.equals(ecmHostStatusEnum)) {
            return EcmTaskStatusEnum.RUNNING;
        }
        if (EcmHostStatusEnum.WAITING.equals(ecmHostStatusEnum)) {
            return EcmTaskStatusEnum.WAITING;
        }
        if (EcmHostStatusEnum.READY.equals(ecmHostStatusEnum)) {
            return EcmTaskStatusEnum.PAUSE;
        }
        if (EcmHostStatusEnum.IGNORE.equals(ecmHostStatusEnum)) {
            return EcmTaskStatusEnum.IGNORE;
        }
        if (EcmHostStatusEnum.CANCELLED.equals(ecmHostStatusEnum)) {
            return EcmTaskStatusEnum.CANCEL;
        }
        return EcmTaskStatusEnum.UNKNOWN;
    }

    private boolean checkEcmTaskStatusValid(List<EcmTaskStatus> ecmTaskStatuses) {
        for (EcmTaskStatus status : ecmTaskStatuses) {
            if (AriusObjUtils.isBlack(status.getPodIp()) && AriusObjUtils.isBlack(status.getHostname())) {
                return Boolean.FALSE;
            }
        }

        return Boolean.TRUE;
    }

    private void saveTaskDetailInfoWithoutZeusTaskId(EcmParamBase ecmParamBase, Long taskId, EcmTaskStatusEnum taskStatusEnum) {
        HostsParamBase hostParamBase = (HostsParamBase) ecmParamBase;
        for (String hostname : hostParamBase.getHostList()) {
            EcmTaskDetail ecmTaskDetail = new EcmTaskDetail();
            ecmTaskDetail.setWorkOrderTaskId(taskId);
            ecmTaskDetail.setStatus(taskStatusEnum.getValue());
            ecmTaskDetail.setHostname(hostname);
            ecmTaskDetail.setRole(ecmParamBase.getRoleName());
            ecmTaskDetail.setGrp(0);
            ecmTaskDetail.setIdx(0);
            ecmTaskDetail.setTaskId(0L);
            ecmTaskDetailManager.saveEcmTaskDetail(ecmTaskDetail);
        }
    }
}
