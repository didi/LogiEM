package com.didichuxing.datachannel.arius.admin.biz.workorder.handler;

import com.alibaba.fastjson.JSON;
import com.didichuxing.datachannel.arius.admin.biz.workorder.BaseWorkOrderHandler;
import com.didichuxing.datachannel.arius.admin.biz.workorder.content.LogicClusterIndecreaseContent;
import com.didichuxing.datachannel.arius.admin.biz.workorder.notify.LogicClusterIndecencyNotify;
import com.didichuxing.datachannel.arius.admin.client.bean.common.Result;
import com.didichuxing.datachannel.arius.admin.client.constant.app.AppClusterLogicAuthEnum;
import com.didichuxing.datachannel.arius.admin.client.constant.operaterecord.OperationEnum;
import com.didichuxing.datachannel.arius.admin.client.constant.result.ResultType;
import com.didichuxing.datachannel.arius.admin.client.constant.workorder.WorkOrderTypeEnum;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.arius.AriusUserInfo;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ClusterLogic;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.workorder.WorkOrder;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.workorder.detail.AbstractOrderDetail;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.workorder.detail.LogicClusterIndecreaseOrderDetail;
import com.didichuxing.datachannel.arius.admin.common.bean.po.order.WorkOrderPO;
import com.didichuxing.datachannel.arius.admin.common.util.AriusObjUtils;
import com.didichuxing.datachannel.arius.admin.common.util.ConvertUtil;
import com.didichuxing.datachannel.arius.admin.core.service.app.AppClusterLogicAuthService;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.logic.ClusterLogicService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import static com.didichuxing.datachannel.arius.admin.client.constant.operaterecord.ModuleEnum.CLUSTER;
import static com.didichuxing.datachannel.arius.admin.core.notify.NotifyTaskTypeEnum.WORK_ORDER_LOGIC_CLUSTER_INDECREASE;

/**
 * @author d06679
 * @date 2019/4/29
 */
@Service("logicClusterIndecreaseHandler")
public class LogicClusterIndecreaseHandler extends BaseWorkOrderHandler {

    @Autowired
    private ClusterLogicService clusterLogicService;

    @Autowired
    private AppClusterLogicAuthService appClusterLogicAuthService;

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
        LogicClusterIndecreaseContent content = JSON.parseObject(extensions, LogicClusterIndecreaseContent.class);

        return ConvertUtil.obj2Obj(content, LogicClusterIndecreaseOrderDetail.class);
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
        LogicClusterIndecreaseContent content = ConvertUtil.obj2ObjByJSON(workOrder.getContentObj(),
            LogicClusterIndecreaseContent.class);

        if (AriusObjUtils.isNull(content.getLogicClusterId())) {
            return Result.buildParamIllegal("??????id??????");
        }

        ClusterLogic clusterLogic = clusterLogicService.getClusterLogicById(content.getLogicClusterId());
        if (clusterLogic == null) {
            return Result.buildParamIllegal("???????????????");
        }

        return Result.buildSucc();
    }

    @Override
    protected String getTitle(WorkOrder workOrder) {
        LogicClusterIndecreaseContent content = ConvertUtil.obj2ObjByJSON(workOrder.getContentObj(),
            LogicClusterIndecreaseContent.class);

        WorkOrderTypeEnum workOrderTypeEnum = WorkOrderTypeEnum.valueOfName(workOrder.getType());
        if (workOrderTypeEnum == null) {
            return "";
        }
        return content.getLogicClusterName() + workOrderTypeEnum.getMessage();
    }

    /**
     * ????????????????????????????????????
     * ???????????????????????????appId????????????
     *
     * @param workOrder ????????????
     * @return result
     */
    @Override
    protected Result<Void> validateConsoleAuth(WorkOrder workOrder) {
        LogicClusterIndecreaseContent content = ConvertUtil.obj2ObjByJSON(workOrder.getContentObj(),
            LogicClusterIndecreaseContent.class);

        AppClusterLogicAuthEnum logicClusterAuthEnum = appClusterLogicAuthService
            .getLogicClusterAuthEnum(workOrder.getSubmitorAppid(), content.getLogicClusterId());

        switch (logicClusterAuthEnum) {
            case ALL:
            case OWN:
                return Result.buildSucc();
            case ACCESS:
                return Result.buildParamIllegal("??????appid??????????????????????????????");
            case NO_PERMISSIONS:
            default:
                return Result.buildParamIllegal("??????appid???????????????????????????");
        }
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
    protected Result<Void> doProcessAgree(WorkOrder workOrder, String approver) {
        LogicClusterIndecreaseContent content = ConvertUtil.obj2ObjByJSON(workOrder.getContentObj(),
            LogicClusterIndecreaseContent.class);

        operateRecordService.save(CLUSTER, OperationEnum.ADD, content.getLogicClusterId(),
            workOrder.getSubmitor() + "??????" + content.getLogicClusterName() + "????????????????????????????????????"
                                                                                           + JSON.toJSONString(content),
            approver);

        sendNotify(WORK_ORDER_LOGIC_CLUSTER_INDECREASE,
            new LogicClusterIndecencyNotify(workOrder.getSubmitorAppid(), content.getLogicClusterName(), approver),
            Arrays.asList(workOrder.getSubmitor()));

        List<String> administrators = getOPList().stream().map(AriusUserInfo::getName).collect(
                Collectors.toList());
        return Result.buildSuccWithMsg(String.format("?????????????????????%s?????????????????????", administrators.get(new Random().nextInt(administrators.size()))));
    }
}
