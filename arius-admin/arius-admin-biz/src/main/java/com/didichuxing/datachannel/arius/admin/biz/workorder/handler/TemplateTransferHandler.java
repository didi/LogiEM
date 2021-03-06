package com.didichuxing.datachannel.arius.admin.biz.workorder.handler;

import com.alibaba.fastjson.JSON;
import com.didichuxing.datachannel.arius.admin.client.bean.common.Result;
import com.didichuxing.datachannel.arius.admin.client.constant.operaterecord.ModuleEnum;
import com.didichuxing.datachannel.arius.admin.client.constant.operaterecord.OperationEnum;
import com.didichuxing.datachannel.arius.admin.client.constant.result.ResultType;
import com.didichuxing.datachannel.arius.admin.client.constant.workorder.WorkOrderTypeEnum;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.arius.AriusUserInfo;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.operaterecord.template.TemplateOperateRecord;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.template.IndexTemplateLogic;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.workorder.WorkOrder;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.workorder.detail.AbstractOrderDetail;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.workorder.detail.TemplateTransferOrderDetail;
import com.didichuxing.datachannel.arius.admin.common.bean.po.order.WorkOrderPO;
import com.didichuxing.datachannel.arius.admin.common.constant.TemplateOperateRecordEnum;
import com.didichuxing.datachannel.arius.admin.common.exception.AdminOperateException;
import com.didichuxing.datachannel.arius.admin.common.util.AriusObjUtils;
import com.didichuxing.datachannel.arius.admin.common.util.ConvertUtil;
import com.didichuxing.datachannel.arius.admin.core.service.app.AppService;
import com.didichuxing.datachannel.arius.admin.core.service.common.AriusUserInfoService;
import com.didichuxing.datachannel.arius.admin.core.service.template.logic.TemplateLogicService;
import com.didichuxing.datachannel.arius.admin.biz.workorder.BaseWorkOrderHandler;
import com.didichuxing.datachannel.arius.admin.biz.workorder.content.TemplateTransferContent;
import com.didichuxing.datachannel.arius.admin.biz.workorder.notify.TemplateTransferNotify;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

import static com.didichuxing.datachannel.arius.admin.core.notify.NotifyTaskTypeEnum.WORK_ORDER_TEMPLATE_TRANSFER;

/**
 * @author d06679
 * @date 2019/4/29
 */
@Service("templateTransferHandler")
public class TemplateTransferHandler extends BaseWorkOrderHandler {

    @Autowired
    private TemplateLogicService    templateLogicService;

    @Autowired
    private AriusUserInfoService    ariusUserInfoService;

    @Autowired
    private AppService              appService;

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
        return JSON.parseObject(extensions, TemplateTransferOrderDetail.class);
    }

    @Override
    public List<AriusUserInfo> getApproverList(AbstractOrderDetail detail) {
        return getRDOrOPList();
    }

    @Override
    public Result<Void> checkAuthority(WorkOrderPO orderPO, String userName) {
        if (isRDOrOP(userName)) {
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
        TemplateTransferContent content = ConvertUtil.obj2ObjByJSON(workOrder.getContentObj(),
            TemplateTransferContent.class);

        if (AriusObjUtils.isNull(content.getId())) {
            return Result.buildParamIllegal("??????id??????");
        }

        if (AriusObjUtils.isNull(content.getName())) {
            return Result.buildParamIllegal("??????????????????");
        }

        if (AriusObjUtils.isNull(content.getTgtAppId())) {
            return Result.buildParamIllegal("??????id??????");
        }

        if (AriusObjUtils.isNull(content.getTgtResponsible())) {
            return Result.buildParamIllegal("???????????????");
        }

        if (AriusObjUtils.isNull(ariusUserInfoService.getByDomainAccount(content.getTgtResponsible()))) {
            return Result.buildParamIllegal("???????????????");
        }

        if (AriusObjUtils.isNull(templateLogicService.getLogicTemplateById(content.getId()))) {
            return Result.buildNotExist("???????????????");
        }

        if (AriusObjUtils.isNull(appService.getAppById(content.getTgtAppId()))) {
            return Result.buildNotExist("???????????????");
        }

        return Result.buildSucc();
    }

    @Override
    protected String getTitle(WorkOrder workOrder) {
        TemplateTransferContent content = ConvertUtil.obj2ObjByJSON(workOrder.getContentObj(),
            TemplateTransferContent.class);

        WorkOrderTypeEnum workOrderTypeEnum = WorkOrderTypeEnum.valueOfName(workOrder.getType());
        if (workOrderTypeEnum == null) {
            return "";
        }
        return content.getName() + workOrderTypeEnum.getMessage();
    }

    /**
     * ????????????????????????????????????
     */
    @Override
    protected Result<Void> validateConsoleAuth(WorkOrder workOrder) {
        TemplateTransferContent content = ConvertUtil.obj2ObjByJSON(workOrder.getContentObj(),
            TemplateTransferContent.class);

        if (AriusObjUtils.isNull(content.getSourceAppId())) {
            return Result.buildParamIllegal("???appId??????");
        }

        if (AriusObjUtils.isNull(content.getTgtAppId())) {
            return Result.buildParamIllegal("??????appId??????");
        }

        if (content.getTgtAppId().equals(content.getSourceAppId())) {
            return Result.buildFail("????????????, ????????????Id???????????????ID??????");
        }

        if (appService.isSuperApp(workOrder.getSubmitorAppid())) {
            return Result.buildSucc();
        }

        IndexTemplateLogic templateLogic = templateLogicService.getLogicTemplateById(content.getId());
        if (!templateLogic.getAppId().equals(workOrder.getSubmitorAppid())) {
            return Result.buildOpForBidden("???????????????????????????????????????");
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
        TemplateTransferContent content = ConvertUtil.obj2ObjByJSON(workOrder.getContentObj(),
            TemplateTransferContent.class);

        Result<Void> result = templateLogicService.turnOverLogicTemplate(content.getId(), content.getTgtAppId(),
            content.getTgtResponsible(), workOrder.getSubmitor());

        if (result.success()) {
            operateRecordService.save(ModuleEnum.TEMPLATE, OperationEnum.EDIT, content.getId(), JSON.toJSONString(
                    new TemplateOperateRecord(TemplateOperateRecordEnum.TRANSFER.getCode(), "????????? appId:" + content.getSourceAppId() + "????????? appId:" + content.getTgtAppId())), approver);
        }

        sendNotify(WORK_ORDER_TEMPLATE_TRANSFER,
            new TemplateTransferNotify(workOrder.getSubmitorAppid(), content.getTgtAppId(), content.getName()),
            Arrays.asList(workOrder.getSubmitor()));

        return Result.buildFrom(result);
    }
}
