package com.didichuxing.datachannel.arius.admin.biz.workorder;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.didichuxing.datachannel.arius.admin.client.bean.common.Result;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.workorder.WorkOrderProcessDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.vo.order.WorkOrderVO;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.arius.AriusUserInfo;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.workorder.WorkOrder;
import com.didichuxing.datachannel.arius.admin.common.bean.po.order.WorkOrderPO;
import com.didichuxing.datachannel.arius.admin.common.constant.arius.AriusUserRoleEnum;
import com.didichuxing.datachannel.arius.admin.common.constant.order.OrderStatusEnum;
import com.didichuxing.datachannel.arius.admin.common.exception.AdminOperateException;
import com.didichuxing.datachannel.arius.admin.common.util.ConvertUtil;
import com.didichuxing.datachannel.arius.admin.core.notify.NotifyInfo;
import com.didichuxing.datachannel.arius.admin.core.notify.NotifyTaskTypeEnum;
import com.didichuxing.datachannel.arius.admin.core.notify.service.NotifyService;
import com.didichuxing.datachannel.arius.admin.core.service.common.AriusUserInfoService;
import com.didichuxing.datachannel.arius.admin.core.service.common.OperateRecordService;
import com.didiglobal.logi.log.ILog;
import com.didiglobal.logi.log.LogFactory;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author d06679
 * @date 2019/4/29
 */
public abstract class BaseWorkOrderHandler implements WorkOrderHandler {

    protected static final ILog    LOGGER = LogFactory.getLog(BaseWorkOrderHandler.class);

    @Autowired
    private WorkOrderManager       workOrderManager;

    @Autowired
    private AriusUserInfoService   ariusUserInfoService;

    @Autowired
    private NotifyService          notifyService;

    @Autowired
    protected OperateRecordService operateRecordService;

    /**
     * ??????????????????
     * 1???????????????????????????????????????
     * 2???????????????
     */
    @Override
    public Result<WorkOrderPO> submit(WorkOrder workOrder) throws AdminOperateException {

        Result<Void> checkAuth = validateConsoleAuth(workOrder);
        if (checkAuth.failed()) {
            LOGGER.warn("class=BaseWorkOrderHandler||method=submit||msg=checkAuth fail||type={}||content={}",
                workOrder.getType(), ConvertUtil.obj2Json(workOrder.getContentObj()));

            return Result.buildFrom(checkAuth);
        }

        Result<Void> checkDuplicateOrder = validDuplicateOrder(workOrder);
        if(checkDuplicateOrder.failed()) {
            LOGGER.warn("class=BaseWorkOrderHandler||method=submit||msg=checkDuplicateOrder fail||type={}||content={}",
                    workOrder.getType(), ConvertUtil.obj2Json(workOrder.getContentObj()));
            return Result.buildFrom(checkDuplicateOrder);
        }

        Result<Void> checkParam = validateConsoleParam(workOrder);
        if (checkParam.failed()) {
            LOGGER.warn("class=BaseWorkOrderHandler||method=submit||msg=checkParam fail||type={}||content={}",
                workOrder.getType(), ConvertUtil.obj2Json(workOrder.getContentObj()));
            return Result.buildFrom(checkParam);
        }

        workOrder.setTitle(getTitle(workOrder));

        WorkOrderPO workOrderPO = buildOrderPO(workOrder);
        workOrderManager.insert(workOrderPO);

        return Result.buildSuccWithTips(workOrderPO, "?????????????????????");
    }

    /**
     * ????????????,??????????????????????????????????????????????????????
     *
     * @param workOrder ????????????
     * @return result
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> processAgree(WorkOrder workOrder, String approver, String opinion) throws AdminOperateException {
        Result<Void> checkParamResult = validateParam(workOrder);

        if (checkParamResult.failed()) {
            LOGGER.warn("class=BaseWorkOrderHandler||method=processAgree||msg=checkParam fail||type={}||content={}",
                workOrder.getType(), ConvertUtil.obj2Json(workOrder.getContentObj()));

            return checkParamResult;
        }

        return handleProcessAgree(workOrder, approver, opinion);
    }

    /**
     * ?????????????????????????????????????????????????????????????????????
     *
     * @param orderPO ????????????
     * @return result
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> processDisagree(WorkOrderPO orderPO, WorkOrderProcessDTO processDTO) {
        return doProcessDisagree(orderPO, processDTO);
    }

    /*************************************** protected method ************************************/
    /**
     * ???????????????
     * @param orderPO
     * @return
     */
    protected Result<Void> doProcessDisagree(WorkOrderPO orderPO, WorkOrderProcessDTO processDTO) {
        orderPO.setApprover(processDTO.getAssignee());
        orderPO.setApproverAppId(processDTO.getAssigneeAppid());
        orderPO.setOpinion(processDTO.getComment());
        orderPO.setStatus(OrderStatusEnum.REFUSED.getCode());

        if (workOrderManager.updateOrderById(orderPO) > 0) {
            return Result.buildSucc();
        }
        return Result.buildFail("???????????????");
    }

    /**
     * ????????????????????????????????????????????????,?????????????????????
     * @param workOrder workorder??????
     * @return ????????????????????????
     */
    protected Result<Void> validDuplicateOrder(WorkOrder workOrder) {
        // ?????????????????????????????????????????????????????????
        Result<List<WorkOrderVO>> orderToApproveResult = workOrderManager.getOrderApplyList(workOrder.getSubmitor(), OrderStatusEnum.WAIT_DEAL.getCode());
        if (orderToApproveResult.failed()) {
            return Result.buildFrom(orderToApproveResult);
        }

        List<WorkOrderVO> applyListResultData = orderToApproveResult.getData();
        // ???????????????????????????????????????????????????????????????
        if (CollectionUtils.isEmpty(applyListResultData)) {
            return Result.buildSucc();
        }

        // ????????????????????????????????????????????????????????????????????????????????????????????????
        for (WorkOrderVO param : applyListResultData) {
            if (workOrder.getType().equals(param.getType())
                    && JSON.toJSONString(workOrder.getContentObj()).equals(param.getExtensions())) {
                return Result.buildFail("????????????????????????");
            }
        }

        return Result.buildSucc();
    }

    /**
     * ???????????????????????????
     * @param workOrder ??????
     * @return result
     */
    protected abstract Result<Void> validateConsoleParam(WorkOrder workOrder);

    /**
     * ????????????
     * @param workOrder ??????
     * @return result
     */
    protected abstract String getTitle(WorkOrder workOrder);

    /**
     * ????????????????????????????????????
     * @param workOrder ????????????
     * @return result
     */
    protected abstract Result<Void> validateConsoleAuth(WorkOrder workOrder);

    /**
     * ??????????????????
     * @param workOrder ????????????
     * @return result
     */
    protected abstract Result<Void> validateParam(WorkOrder workOrder);

    /**
     * ????????????
     * @param workOrder ??????
     * @return result
     */
    protected abstract Result<Void> doProcessAgree(WorkOrder workOrder, String approver) throws AdminOperateException;

    protected List<AriusUserInfo> getRDOrOPList() {
        return ariusUserInfoService
            .listByRoles(Arrays.asList(AriusUserRoleEnum.OP.getRole(), AriusUserRoleEnum.RD.getRole()));
    }

    protected List<AriusUserInfo> getOPList() {
        return ariusUserInfoService.listByRoles(Collections.singletonList(AriusUserRoleEnum.OP.getRole()));
    }

    protected boolean isRDOrOP(String userName) {
        return ariusUserInfoService.isOPByDomainAccount(userName) || ariusUserInfoService.isRDByDomainAccount(userName);
    }

    protected boolean isOP(String userName) {
        return ariusUserInfoService.isOPByDomainAccount(userName);
    }

    protected void sendNotify(NotifyTaskTypeEnum type, NotifyInfo data, List<String> receivers) {
        // ??????????????????
        notifyService.sendAsync(type, data, receivers);
    }

    /*************************************** privete method ************************************/
    /**
     * ??????????????????
     *
     * @param workOrder ??????
     * @return result
     */
    private WorkOrderPO buildOrderPO(WorkOrder workOrder) {
        WorkOrderPO orderPo = new WorkOrderPO();
        orderPo.setApplicant(workOrder.getSubmitor());
        orderPo.setDescription(workOrder.getDescription());
        orderPo.setExtensions(JSON.toJSONString(workOrder.getContentObj()));
        orderPo.setType(workOrder.getType());
        orderPo.setTitle(workOrder.getTitle());
        orderPo.setStatus(OrderStatusEnum.WAIT_DEAL.getCode());
        orderPo.setApplicantAppId(workOrder.getSubmitorAppid());
        return orderPo;
    }

    private Result<Void> handleProcessAgree(WorkOrder workOrder, String approver,
                                      String opinion) throws AdminOperateException {
        Result<Void> result = doProcessAgree(workOrder, approver);

        if (result.success()) {
            Result<Void> updateResult = updateWorkOrderStatus(workOrder, approver, opinion);
            if (updateResult.failed()) {
                return updateResult;
            }
        }

        return result;
    }

    private Result<Void> updateWorkOrderStatus(WorkOrder workOrder, String approver, String opinion) {
        WorkOrderPO orderPO = new WorkOrderPO();
        orderPO.setId(workOrder.getId());
        orderPO.setApprover(approver);
        orderPO.setOpinion(opinion);
        orderPO.setStatus(OrderStatusEnum.PASSED.getCode());
        Result<Void> processResult = workOrderManager.processOrder(orderPO);
        if (processResult.failed()) {
            return processResult;
        }
        return Result.buildSucc();
    }
}
