package com.didichuxing.datachannel.arius.admin.biz.workorder.handler.clusterReStart;

import com.alibaba.fastjson.JSON;
import com.didichuxing.datachannel.arius.admin.biz.workorder.content.PhyClusterPluginOperationContent;
import com.didichuxing.datachannel.arius.admin.biz.workorder.notify.PhyClusterPluginNotify;
import com.didichuxing.datachannel.arius.admin.biz.worktask.WorkTaskManager;
import com.didichuxing.datachannel.arius.admin.biz.worktask.ecm.EcmTaskManager;
import com.didichuxing.datachannel.arius.admin.client.bean.common.Result;
import com.didichuxing.datachannel.arius.admin.client.bean.common.ecm.EcmParamBase;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.task.WorkTaskDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.task.ecm.EcmTaskDTO;
import com.didichuxing.datachannel.arius.admin.client.constant.ecm.EcmTaskTypeEnum;
import com.didichuxing.datachannel.arius.admin.client.constant.result.ResultType;
import com.didichuxing.datachannel.arius.admin.client.constant.task.WorkTaskTypeEnum;
import com.didichuxing.datachannel.arius.admin.client.constant.workorder.WorkOrderTypeEnum;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.arius.AriusUserInfo;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ClusterPhy;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ecm.RoleCluster;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.task.WorkTask;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.workorder.WorkOrder;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.workorder.detail.AbstractOrderDetail;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.workorder.detail.PhyClusterPluginOperationOrderDetail;
import com.didichuxing.datachannel.arius.admin.common.bean.po.esplugin.PluginPO;
import com.didichuxing.datachannel.arius.admin.common.bean.po.order.WorkOrderPO;
import com.didichuxing.datachannel.arius.admin.common.constant.order.OperationTypeEnum;
import com.didichuxing.datachannel.arius.admin.common.exception.AdminOperateException;
import com.didichuxing.datachannel.arius.admin.common.util.AriusObjUtils;
import com.didichuxing.datachannel.arius.admin.common.util.ConvertUtil;
import com.didichuxing.datachannel.arius.admin.common.util.ListUtils;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.ecm.ESPluginService;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.physic.RoleClusterService;
import com.didichuxing.datachannel.arius.admin.core.service.es.ESClusterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.didichuxing.datachannel.arius.admin.core.notify.NotifyTaskTypeEnum.WORK_ORDER_PHY_CLUSTER_PLUGIN;

@Service("clusterOpPluginRestartHandler")
public class ClusterOpPluginRestartHandler extends ClusterOpRestartHandler {

    @Autowired
    private ESPluginService esPluginService;

    @Autowired
    private RoleClusterService roleClusterService;

    @Autowired
    private WorkTaskManager workTaskService;

    @Autowired
    private ESClusterService esClusterService;

    @Autowired
    private EcmTaskManager ecmTaskManager;

    @Override
    protected Result<Void> validateConsoleParam(WorkOrder workOrder) {
        PhyClusterPluginOperationContent content = ConvertUtil.obj2ObjByJSON(workOrder.getContentObj(),
                PhyClusterPluginOperationContent.class);

        // ????????????id
        if (AriusObjUtils.isNull(content.getPluginId())) {
            return Result.buildParamIllegal("??????id?????????");
        }
        PluginPO plugin = esPluginService.getESPluginById(content.getPluginId());
        if (AriusObjUtils.isNull(plugin)) {
            return Result.buildParamIllegal("??????????????????");
        }

        // ??????????????????
        if (AriusObjUtils.isNull(content.getOperationType())) {
            return Result.buildParamIllegal("???????????????????????????");
        }

        OperationTypeEnum operationType = OperationTypeEnum.valueOfCode(content.getOperationType());
        if (!operationType.equals(OperationTypeEnum.INSTALL) && !operationType.equals(OperationTypeEnum.UNINSTALL)) {
            return Result.buildParamIllegal("???????????????????????????(??????????????????????????????????????????)");
        }

        if (workTaskManager.existUnClosedTask(Integer.parseInt(plugin.getPhysicClusterId()), WorkTaskTypeEnum.CLUSTER_RESTART.getType())) {
            return Result.buildParamIllegal("????????????????????????????????????????????????");
        }

        // ????????????????????????????????????????????????????????????
        if (null != ecmTaskManager.getRunningEcmTaskByClusterId(Integer.parseInt(plugin.getPhysicClusterId()))) {
            return Result.buildFail("??????????????????????????????????????????????????????????????????????????????????????????????????????");
        }

        return Result.buildSucc();
    }

    @Override
    protected String getTitle(WorkOrder workOrder) {
        PhyClusterPluginOperationContent content = ConvertUtil.obj2ObjByJSON(workOrder.getContentObj(),
                PhyClusterPluginOperationContent.class);

        WorkOrderTypeEnum workOrderTypeEnum = WorkOrderTypeEnum.valueOfName(workOrder.getType());
        if (workOrderTypeEnum == null) {
            return "";
        }
        OperationTypeEnum operationType = OperationTypeEnum.valueOfCode(content.getOperationType());
        PluginPO pluginPO = esPluginService.getESPluginById(content.getPluginId());
        ClusterPhy cluster = esClusterPhyService.getClusterById(Integer.parseInt(pluginPO.getPhysicClusterId()));
        return cluster.getCluster() + " " + pluginPO.getName() + pluginPO.getVersion() + " "
                + workOrderTypeEnum.getMessage() + "-" + operationType.getMessage();
    }

    @Override
    protected Result<Void> validateConsoleAuth(WorkOrder workOrder) {
        if(!isOP(workOrder.getSubmitor())){
            return Result.buildOpForBidden("??????????????????????????????????????????????????????????????????");
        }

        return Result.buildSucc();
    }

    @Override
    protected Result<Void> validateParam(WorkOrder workOrder) {
        return Result.buildSucc();
    }

    @Override
    protected Result<Void> doProcessAgree(WorkOrder workOrder, String approver) throws AdminOperateException {
        PhyClusterPluginOperationContent content = ConvertUtil.obj2ObjByJSON(workOrder.getContentObj(),
                PhyClusterPluginOperationContent.class);

        // ???????????????????????????????????????????????????????????????????????????????????????????????????????????????
        PluginPO pluginPO = esPluginService.getESPluginById(content.getPluginId());

        ClusterPhy clusterPhy = esClusterPhyService.getClusterById(Integer.parseInt(pluginPO.getPhysicClusterId()));
        List<RoleCluster> roleClusterList = roleClusterService.getAllRoleClusterByClusterId(
				clusterPhy.getId());
        if (CollectionUtils.isEmpty(roleClusterList)) {
            return Result.buildFail("???????????????????????????");
        }

        List<String> roleNameList = new ArrayList<>();
        for (RoleCluster roleCluster : roleClusterList) {
            roleNameList.add(roleCluster.getRole());
        }
        List<EcmParamBase> ecmParamBaseList = ecmHandleService.buildEcmParamBaseListWithEsPluginAction(clusterPhy.getId(),
                roleNameList, content.getPluginId(), content.getOperationType()).getData();

        // ??????????????????
        EcmTaskDTO ecmTaskDTO = new EcmTaskDTO();
        ecmTaskDTO.setPhysicClusterId(Long.parseLong(pluginPO.getPhysicClusterId()));
        ecmTaskDTO.setWorkOrderId(workOrder.getId());
        ecmTaskDTO.setTitle(workOrder.getTitle());
        ecmTaskDTO.setOrderType(EcmTaskTypeEnum.RESTART.getCode());
        ecmTaskDTO.setCreator(workOrder.getSubmitor());
        ecmTaskDTO.setType(clusterPhy.getType());
        ecmTaskDTO.setEcmParamBaseList(ecmParamBaseList);
        ecmTaskDTO.setClusterNodeRole(ListUtils.strList2String(roleNameList));

        WorkTaskDTO workTaskDTO = new WorkTaskDTO();
        workTaskDTO.setExpandData(JSON.toJSONString(ecmTaskDTO));
        workTaskDTO.setTaskType(WorkTaskTypeEnum.CLUSTER_RESTART.getType());
        workTaskDTO.setCreator(workOrder.getSubmitor());
        Result<WorkTask> result = workTaskService.addTask(workTaskDTO);
        if(null == result || result.failed()){
            return Result.buildFail("??????????????????????????????????????????!");
        }

        // ??????????????????
        sendNotify(WORK_ORDER_PHY_CLUSTER_PLUGIN, new PhyClusterPluginNotify(workOrder.getSubmitorAppid(),
                clusterPhy.getCluster(), approver), Arrays.asList(workOrder.getSubmitor()));

        return Result.buildSucc();
    }

    @Override
    public boolean canAutoReview(WorkOrder workOrder) {
        return false;
    }

    @Override
    public AbstractOrderDetail getOrderDetail(String extensions) {
        PhyClusterPluginOperationContent content = JSON.parseObject(extensions,
                PhyClusterPluginOperationContent.class);
        return ConvertUtil.obj2Obj(content, PhyClusterPluginOperationOrderDetail.class);
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
        return Result.buildFail( ResultType.OPERATE_FORBIDDEN_ERROR.getMessage());
    }
}
