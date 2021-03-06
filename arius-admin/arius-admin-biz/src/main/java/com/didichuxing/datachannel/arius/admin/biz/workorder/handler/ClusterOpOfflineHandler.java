package com.didichuxing.datachannel.arius.admin.biz.workorder.handler;

import com.alibaba.fastjson.JSON;
import com.didichuxing.datachannel.arius.admin.biz.workorder.BaseWorkOrderHandler;
import com.didichuxing.datachannel.arius.admin.biz.workorder.content.ClusterOpOfflineContent;
import com.didichuxing.datachannel.arius.admin.biz.workorder.notify.ClusterOpOfflineNotify;
import com.didichuxing.datachannel.arius.admin.biz.worktask.WorkTaskManager;
import com.didichuxing.datachannel.arius.admin.client.bean.common.Result;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.task.WorkTaskDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.task.ecm.EcmTaskDTO;
import com.didichuxing.datachannel.arius.admin.client.constant.result.ResultType;
import com.didichuxing.datachannel.arius.admin.client.constant.task.WorkTaskTypeEnum;
import com.didichuxing.datachannel.arius.admin.client.constant.workorder.WorkOrderTypeEnum;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.arius.AriusUserInfo;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ClusterPhy;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.task.WorkTask;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.workorder.WorkOrder;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.workorder.detail.AbstractOrderDetail;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.workorder.detail.ClusterOpOfflineOrderDetail;
import com.didichuxing.datachannel.arius.admin.common.bean.po.order.WorkOrderPO;
import com.didichuxing.datachannel.arius.admin.common.exception.AdminOperateException;
import com.didichuxing.datachannel.arius.admin.common.util.AriusObjUtils;
import com.didichuxing.datachannel.arius.admin.common.util.ConvertUtil;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.physic.ClusterPhyService;
import com.didichuxing.datachannel.arius.admin.core.service.common.AriusUserInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

import static com.didichuxing.datachannel.arius.admin.core.notify.NotifyTaskTypeEnum.WORK_ORDER_CLUSTER_OP_OFFLINE;

@Service("clusterOpOfflineHandler")
public class ClusterOpOfflineHandler extends BaseWorkOrderHandler {

    @Autowired
    private AriusUserInfoService ariusUserInfoService;

    @Autowired
    private ClusterPhyService esClusterPhyService;

    @Autowired
    private WorkTaskManager workTaskManager;

    /**
     * ????????????????????????
     *
     * @param workOrder ????????????
     * @return result
     */
    @Override
    public boolean canAutoReview(WorkOrder workOrder) {
        return false;
    }

    @Override
    public AbstractOrderDetail getOrderDetail(String extensions) {
        ClusterOpOfflineContent content = JSON.parseObject(extensions, ClusterOpOfflineContent.class);

        return ConvertUtil.obj2Obj(content, ClusterOpOfflineOrderDetail.class);
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

    /**************************************** protected method ******************************************/

    /**
     * ???????????????????????????
     *
     * @param workOrder ??????
     * @return result
     */
    @Override
    protected Result<Void> validateConsoleParam(WorkOrder workOrder) {
        ClusterOpOfflineContent content = ConvertUtil.obj2ObjByJSON(workOrder.getContentObj(),
            ClusterOpOfflineContent.class);

        if (AriusObjUtils.isNull(content.getPhyClusterId())) {
            return Result.buildParamIllegal("????????????id??????");
        }

        ClusterPhy clusterPhy = esClusterPhyService.getClusterById(content.getPhyClusterId().intValue());
        if (AriusObjUtils.isNull(clusterPhy)) {
            return Result.buildParamIllegal("?????????????????????");
        }

        if (workTaskManager.existUnClosedTask(content.getPhyClusterId().intValue(), WorkTaskTypeEnum.CLUSTER_OFFLINE.getType())) {
            return Result.buildParamIllegal("????????????????????????????????????");
        }

        return Result.buildSucc();
    }

    @Override
    protected String getTitle(WorkOrder workOrder) {
        ClusterOpOfflineContent content = ConvertUtil.obj2ObjByJSON(workOrder.getContentObj(),
            ClusterOpOfflineContent.class);
        WorkOrderTypeEnum workOrderTypeEnum = WorkOrderTypeEnum.valueOfName(workOrder.getType());
        if (workOrderTypeEnum == null) {
            return "";
        }
        return content.getPhyClusterName() + workOrderTypeEnum.getMessage();
    }

    /**
     * ????????????????????????????????????
     *
     * @param workOrder ????????????
     * @return result
     */
    @Override
    protected Result<Void> validateConsoleAuth(WorkOrder workOrder) {
        if (!ariusUserInfoService.isOPByDomainAccount(workOrder.getSubmitor())) {
            return Result.buildOpForBidden("?????????????????????????????????????????????");
        }

        return Result.buildSucc();
    }

    /**
     * ??????????????????
     *
     * @param workOrder ????????????
     * @return result
     */
    @Override
    protected Result<Void> validateParam(WorkOrder workOrder) {
        return Result.buildSucc();
    }

    /**
     * ????????????
     *
     * @param workOrder ??????
     * @return result
     */
    @Override
    protected Result<Void> doProcessAgree(WorkOrder workOrder, String approver) throws AdminOperateException {
        ClusterOpOfflineContent content = ConvertUtil.obj2ObjByJSON(workOrder.getContentObj(),
            ClusterOpOfflineContent.class);

        EcmTaskDTO ecmTaskDTO = new EcmTaskDTO();
        ecmTaskDTO.setPhysicClusterId(content.getPhyClusterId());
        ecmTaskDTO.setWorkOrderId(workOrder.getId());
        ecmTaskDTO.setTitle(content.getPhyClusterName() + "??????????????????");
        ecmTaskDTO.setOrderType(5);
        ecmTaskDTO.setCreator(workOrder.getSubmitor());

        WorkTaskDTO workTaskDTO = new WorkTaskDTO();
        workTaskDTO.setCreator(workOrder.getSubmitor());
        workTaskDTO.setExpandData(JSON.toJSONString(ecmTaskDTO));
        workTaskDTO.setTaskType(WorkTaskTypeEnum.CLUSTER_OFFLINE.getType());
        Result<WorkTask> result = workTaskManager.addTask(workTaskDTO);
        if (null == result || result.failed()) {
            return Result.buildFail("????????????????????????????????????!");
        }

        sendNotify(WORK_ORDER_CLUSTER_OP_OFFLINE,
            new ClusterOpOfflineNotify(workOrder.getSubmitorAppid(), content.getPhyClusterName(), approver),
            Arrays.asList(workOrder.getSubmitor()));

        return Result.buildSucc();
    }
}
