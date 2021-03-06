package com.didichuxing.datachannel.arius.admin.biz.workorder.handler;

import com.alibaba.fastjson.JSON;
import com.didichuxing.datachannel.arius.admin.biz.workorder.BaseWorkOrderHandler;
import com.didichuxing.datachannel.arius.admin.biz.workorder.content.ClusterOpBaseContent;
import com.didichuxing.datachannel.arius.admin.biz.workorder.content.ClusterOpNewDockerContent;
import com.didichuxing.datachannel.arius.admin.biz.workorder.content.ClusterOpNewHostContent;
import com.didichuxing.datachannel.arius.admin.biz.workorder.notify.ClusterOpNewNotify;
import com.didichuxing.datachannel.arius.admin.biz.workorder.utils.WorkOrderTaskConverter;
import com.didichuxing.datachannel.arius.admin.biz.worktask.WorkTaskManager;
import com.didichuxing.datachannel.arius.admin.client.bean.common.Result;
import com.didichuxing.datachannel.arius.admin.client.bean.common.ecm.ESClusterRoleDocker;
import com.didichuxing.datachannel.arius.admin.client.bean.common.ecm.ESClusterRoleHost;
import com.didichuxing.datachannel.arius.admin.client.bean.common.ecm.EcmParamBase;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.task.WorkTaskDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.task.ecm.EcmTaskDTO;
import com.didichuxing.datachannel.arius.admin.client.constant.ecm.EcmTaskTypeEnum;
import com.didichuxing.datachannel.arius.admin.client.constant.resource.ESClusterTypeEnum;
import com.didichuxing.datachannel.arius.admin.client.constant.result.ResultType;
import com.didichuxing.datachannel.arius.admin.client.constant.task.WorkTaskTypeEnum;
import com.didichuxing.datachannel.arius.admin.client.constant.workorder.WorkOrderTypeEnum;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.arius.AriusUserInfo;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.task.WorkTask;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.workorder.WorkOrder;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.workorder.detail.AbstractOrderDetail;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.workorder.detail.ClusterOpNewDockerOrderDetail;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.workorder.detail.ClusterOpNewHostOrderDetail;
import com.didichuxing.datachannel.arius.admin.common.bean.po.order.WorkOrderPO;
import com.didichuxing.datachannel.arius.admin.common.constant.ClusterConstant;
import com.didichuxing.datachannel.arius.admin.common.exception.AdminOperateException;
import com.didichuxing.datachannel.arius.admin.common.util.AriusObjUtils;
import com.didichuxing.datachannel.arius.admin.common.util.ConvertUtil;
import com.didichuxing.datachannel.arius.admin.common.util.EnvUtil;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.ecm.ESPackageService;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.physic.ClusterPhyService;
import com.google.common.collect.Maps;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.didichuxing.datachannel.arius.admin.client.constant.resource.ESClusterNodeRoleEnum.*;
import static com.didichuxing.datachannel.arius.admin.client.constant.resource.ESClusterTypeEnum.ES_DOCKER;
import static com.didichuxing.datachannel.arius.admin.client.constant.resource.ESClusterTypeEnum.ES_HOST;
import static com.didichuxing.datachannel.arius.admin.common.constant.ClusterConstant.CREATE_MASTER_NODE_MIN_NUMBER;
import static com.didichuxing.datachannel.arius.admin.core.notify.NotifyTaskTypeEnum.WORK_ORDER_CLUSTER_OP_NEW;

@Service("clusterOpNewHandler")
public class ClusterOpNewHandler extends BaseWorkOrderHandler {

    @Autowired
    private WorkTaskManager     workTaskManager;

    @Autowired
    private ClusterPhyService esClusterPhyService;

    @Autowired
    private ESPackageService esPackageService;

    private static final String PARAM_ILLEGAL_TIPS = "?????????????????????%s?????????";

    @Override
    protected Result<Void> validateConsoleParam(WorkOrder workOrder) {
        Result<Void> initResult = initParam(workOrder);
        if(initResult.failed()) {
            return Result.buildFrom(initResult);
        }

        ClusterOpBaseContent content = ConvertUtil.obj2ObjByJSON(workOrder.getContentObj(), ClusterOpBaseContent.class);
        Result<Void> doValidateHostTypeResult = validateClusterMasterNodeNumber(content, workOrder);
        if (doValidateHostTypeResult.failed()) {
            return doValidateHostTypeResult;
        }

        if (AriusObjUtils.isNull(content.getPhyClusterName())) {
            return Result.buildParamIllegal("????????????????????????");
        }

        if (esClusterPhyService.isClusterExists(content.getPhyClusterName())) {
            return Result.buildParamIllegal("??????????????????????????????");
        }

        //ES????????????????????????????????????????????????ip???port???????????????
        Result<Void> doValidRoleClusterPort = validRoleClusterPort(workOrder);
        if (doValidRoleClusterPort.failed()) {
            return Result.buildFrom(doValidRoleClusterPort);
        }

        ClusterOpNewHostContent clusterOpNewHostContent = ConvertUtil.obj2ObjByJSON(workOrder.getContentObj(), ClusterOpNewHostContent.class);
        // es?????????
        if (null == esPackageService.getByVersionAndType(clusterOpNewHostContent.getEsVersion(), ES_HOST.getCode())) {
            return Result.buildFail(ES_HOST.getDesc() + "???????????????" + clusterOpNewHostContent.getEsVersion() + "?????????????????????");
        }

        return Result.buildSucc();
    }

    @Override
    protected String getTitle(WorkOrder workOrder) {
        ClusterOpBaseContent content = ConvertUtil.obj2ObjByJSON(workOrder.getContentObj(), ClusterOpBaseContent.class);
        WorkOrderTypeEnum workOrderTypeEnum = WorkOrderTypeEnum.valueOfName(workOrder.getType());
        if (workOrderTypeEnum == null) {
            return "";
        }
        return content.getPhyClusterName() + workOrderTypeEnum.getMessage();
    }

    @Override
    protected Result<Void> validateConsoleAuth(WorkOrder workOrder) {
        if (!isOP(workOrder.getSubmitor())) {
            return Result.buildOpForBidden("?????????????????????????????????????????????");
        }

        return Result.buildSucc();
    }

    @Override
    protected Result<Void> validateParam(WorkOrder workOrder) {
        return Result.buildSucc();
    }

    @Override
    protected Result<Void> doProcessAgree(WorkOrder workOrder, String approver) throws AdminOperateException {
        ClusterOpBaseContent clusterOpBaseContent = ConvertUtil.obj2ObjByJSON(workOrder.getContentObj(),
            ClusterOpBaseContent.class);

        EcmTaskDTO ecmTaskDTO = new EcmTaskDTO();
        ecmTaskDTO.setWorkOrderId(workOrder.getId());
        ecmTaskDTO.setTitle(workOrder.getTitle());
        ecmTaskDTO.setOrderType(EcmTaskTypeEnum.NEW.getCode());
        ecmTaskDTO.setType(clusterOpBaseContent.getType());
        ecmTaskDTO.setCreator(workOrder.getSubmitor());
        ecmTaskDTO.setPhysicClusterId(ClusterConstant.INVALID_VALUE);

        // ???????????? ??? handle data
        List<EcmParamBase> ecmParamBaseList = null;
        if (ES_DOCKER.getCode() == clusterOpBaseContent.getType()) {
            ecmParamBaseList = WorkOrderTaskConverter.convert2EcmParamBaseList(ESClusterTypeEnum.ES_DOCKER,
                EcmTaskTypeEnum.NEW,
                ConvertUtil.obj2ObjByJSON(workOrder.getContentObj(), ClusterOpNewDockerContent.class));
        } else if (ES_HOST.getCode() == clusterOpBaseContent.getType()) {
            // ?????????????????????????????????????????????????????????????????????
            ClusterOpNewHostContent clusterOpNewHostContent = ConvertUtil.obj2ObjByJSON(workOrder.getContentObj(), ClusterOpNewHostContent.class);
            clusterOpNewHostContent.setCreator(workOrder.getSubmitor());

            ecmParamBaseList = WorkOrderTaskConverter.convert2EcmParamBaseList(ESClusterTypeEnum.ES_HOST,
                EcmTaskTypeEnum.NEW, clusterOpNewHostContent);
        } else {
            return Result.buildFail("????????????(Docker|Host)??????");
        }

        ecmTaskDTO.setEcmParamBaseList(ecmParamBaseList);

        WorkTaskDTO workTaskDTO = new WorkTaskDTO();
        workTaskDTO.setTaskType(WorkTaskTypeEnum.CLUSTER_NEW.getType());
        workTaskDTO.setCreator(workOrder.getSubmitor());
        workTaskDTO.setExpandData(ConvertUtil.obj2Json(ecmTaskDTO));

        Result<WorkTask> result = workTaskManager.addTask(workTaskDTO);
        if (null == result || result.failed()) {
            return Result.buildFail("????????????????????????????????????!");
        }

        if (EnvUtil.isOnline()) {
            sendNotify(WORK_ORDER_CLUSTER_OP_NEW, new ClusterOpNewNotify(workOrder.getSubmitorAppid(),
                clusterOpBaseContent.getPhyClusterName(), approver), Arrays.asList(workOrder.getSubmitor()));
        }

        return Result.buildSucc();
    }

    @Override
    public boolean canAutoReview(WorkOrder workOrder) {
        return false;
    }

    @Override
    public AbstractOrderDetail getOrderDetail(String extensions) {
        ClusterOpBaseContent clusterOpBaseContent = ConvertUtil.obj2ObjByJSON(JSON.parse(extensions),
            ClusterOpBaseContent.class);

        if (ES_DOCKER.getCode() == clusterOpBaseContent.getType()) {
            ClusterOpNewDockerContent content = JSON.parseObject(JSON.parse(extensions).toString(),
                ClusterOpNewDockerContent.class);

            return ConvertUtil.obj2Obj(content, ClusterOpNewDockerOrderDetail.class);
        } else if (ES_HOST.getCode() == clusterOpBaseContent.getType()) {
            ClusterOpNewHostContent content = JSON.parseObject(JSON.parse(extensions).toString(),
                ClusterOpNewHostContent.class);

            return ConvertUtil.obj2Obj(content, ClusterOpNewHostOrderDetail.class);
        } else {
            return null;
        }
    }

    @Override
    public List<AriusUserInfo> getApproverList(AbstractOrderDetail detail) {
        return getOPList();
    }

    @Override
    public Result<Void> checkAuthority(WorkOrderPO orderPO, String userName) {
        if (isOP(userName)) {
            return Result.buildSucc();
        }
        return Result.buildFail(ResultType.OPERATE_FORBIDDEN_ERROR.getMessage());
    }
    
    /*******************************************private************************************************/

    private Result<Void> validateClusterMasterNodeNumber(ClusterOpBaseContent content, WorkOrder workOrder) {
        if (ES_HOST.getCode() == content.getType()) {
            return validateClusterMasterNodeNumberESHost(workOrder);
        } else if (ES_DOCKER.getCode() == content.getType()) {
            return validateClusterMasterNodeNumberESDocker(workOrder);
        }
        return Result.buildSucc();
    }

    private Result<Void> initParam(WorkOrder workOrder) {
        ClusterOpNewHostContent clusterOpNewHostContent = ConvertUtil.obj2ObjByJSON(workOrder.getContentObj(), ClusterOpNewHostContent.class);

        // ??????pid_count??????????????????????????????,?????????null?????????????????????1
        if (null == clusterOpNewHostContent.getPidCount()) {
            clusterOpNewHostContent.setPidCount(ClusterConstant.DEFAULT_CLUSTER_PAID_COUNT);
        }

        // ??????address????????????ip?????????????????????
        List<ESClusterRoleHost> roleClusterHosts = clusterOpNewHostContent.getRoleClusterHosts();

        for (ESClusterRoleHost esClusterRoleHost : roleClusterHosts) {
            if(null == esClusterRoleHost.getAddress()) {
                return Result.buildFail("???????????????address???????????????");
            }
            // ???ip???port???hostname???????????????
            String[] ipAndPort = esClusterRoleHost.getAddress().split(":");
            if(ipAndPort.length<2)  {
                return Result.buildFail("???????????????address???????????????ip:port?????????");
            }
            esClusterRoleHost.setHostname(ipAndPort[0]);
            esClusterRoleHost.setIp(ipAndPort[0]);
            esClusterRoleHost.setPort(ipAndPort[1]);
        }

        workOrder.setContentObj(JSON.toJSON(clusterOpNewHostContent));
        return Result.buildSucc();
    }

    private Result<Void> validateClusterMasterNodeNumberESDocker(WorkOrder workOrder) {
        ClusterOpBaseContent content = ConvertUtil.obj2ObjByJSON(workOrder.getContentObj(), ClusterOpNewDockerContent.class);

        List<ESClusterRoleDocker> roleClusterDockers = ((ClusterOpNewDockerContent) content).getRoleClusters();
        if (CollectionUtils.isEmpty(roleClusterDockers)) {
            return Result.buildParamIllegal("??????????????????");
        }

        Set<String> dockerRoles = roleClusterDockers.stream().map(ESClusterRoleDocker::getRole)
            .collect(Collectors.toSet());
        if (!dockerRoles.contains(MASTER_NODE.getDesc())) {
            return Result.buildParamIllegal(String.format(PARAM_ILLEGAL_TIPS, MASTER_NODE.getDesc()));
        }

        Integer masterNodesNumber = roleClusterDockers
                                    .stream()
                                    .filter(r -> MASTER_NODE.getDesc().equals(r.getRole()))
                                    .map(ESClusterRoleDocker::getPodNumber)
                                    .collect(Collectors.toList()).get(0);

        if (masterNodesNumber < CREATE_MASTER_NODE_MIN_NUMBER) {
            return Result.buildParamIllegal("masternode??????pod????????????????????????1");
        }
        return Result.buildSucc();
    }

    private Result<Void> validateClusterMasterNodeNumberESHost(WorkOrder workOrder) {
        ClusterOpBaseContent content = ConvertUtil.obj2ObjByJSON(workOrder.getContentObj(), ClusterOpNewHostContent.class);

        List<ESClusterRoleHost> roleClusterHosts = ((ClusterOpNewHostContent) content).getRoleClusterHosts();
        if (CollectionUtils.isEmpty(roleClusterHosts)) {
            return Result.buildParamIllegal("??????????????????");
        }

        Set<String> hostRoles = roleClusterHosts.stream().map(ESClusterRoleHost::getRole)
            .collect(Collectors.toSet());
        if (!hostRoles.contains(MASTER_NODE.getDesc())) {
            return Result.buildParamIllegal(String.format(PARAM_ILLEGAL_TIPS, MASTER_NODE.getDesc()));
        }

        List<ESClusterRoleHost> masterNodes = roleClusterHosts.stream()
            .filter(r -> MASTER_NODE.getDesc().equals(r.getRole())).collect(Collectors.toList());

        if (masterNodes.size() < CREATE_MASTER_NODE_MIN_NUMBER) {
            return Result.buildParamIllegal("masternode??????ip????????????????????????1");
        }
        return Result.buildSucc();
    }

    private Result<Void> validRoleClusterPort(WorkOrder workOrder) {
        ClusterOpNewHostContent clusterOpNewHostContent = ConvertUtil.obj2ObjByJSON(workOrder.getContentObj(), ClusterOpNewHostContent.class);
        List<ESClusterRoleHost> roleClusterHosts = clusterOpNewHostContent.getRoleClusterHosts();

        Map<Object, Object> roleClusterPortMap = Maps.newHashMap();
        for (ESClusterRoleHost esClusterRoleHost : roleClusterHosts) {
            // ??????map??????????????????ip??????????????????put
            if (!roleClusterPortMap.containsKey(esClusterRoleHost.getRole())) {
                roleClusterPortMap.put(esClusterRoleHost.getRole(), esClusterRoleHost.getPort());
                continue;
            }

            if (roleClusterPortMap.containsKey(esClusterRoleHost.getRole())
                    && !roleClusterPortMap.get(esClusterRoleHost.getRole()).equals(esClusterRoleHost.getPort())) {
                return Result.buildFail("??????????????????????????????????????????????????????");
            }
        }

        return Result.buildSucc();
    }
}
