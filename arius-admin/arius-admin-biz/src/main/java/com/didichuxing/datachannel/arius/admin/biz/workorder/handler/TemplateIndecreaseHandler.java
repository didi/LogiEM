package com.didichuxing.datachannel.arius.admin.biz.workorder.handler;

import com.alibaba.fastjson.JSON;
import com.didichuxing.datachannel.arius.admin.client.bean.common.Result;
import com.didichuxing.datachannel.arius.admin.client.constant.result.ResultType;
import com.didichuxing.datachannel.arius.admin.client.constant.workorder.WorkOrderTypeEnum;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.arius.AriusUserInfo;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.template.IndexTemplateLogic;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.workorder.WorkOrder;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.workorder.detail.AbstractOrderDetail;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.workorder.detail.TemplateIndecreaseOrderDetail;
import com.didichuxing.datachannel.arius.admin.common.bean.po.order.WorkOrderPO;
import com.didichuxing.datachannel.arius.admin.common.exception.AdminOperateException;
import com.didichuxing.datachannel.arius.admin.common.util.AriusObjUtils;
import com.didichuxing.datachannel.arius.admin.common.util.ConvertUtil;
import com.didichuxing.datachannel.arius.admin.biz.template.TemplateAction;
import com.didichuxing.datachannel.arius.admin.core.service.template.logic.TemplateLogicService;
import com.didichuxing.datachannel.arius.admin.biz.template.srv.quota.TemplateQuotaManager;
import com.didichuxing.datachannel.arius.admin.biz.workorder.BaseWorkOrderHandler;
import com.didichuxing.datachannel.arius.admin.biz.workorder.content.TemplateIndecreaseContent;
import com.didichuxing.datachannel.arius.admin.biz.workorder.notify.TemplateIndecencyNotify;
import com.didichuxing.datachannel.arius.admin.metadata.service.TemplateLabelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

import static com.didichuxing.datachannel.arius.admin.core.notify.NotifyTaskTypeEnum.WORK_ORDER_TEMPLATE_INDECREASE;

/**
 * ?????????????????????
 * @author d06679
 * @date 2019/4/29
 */
@Service("templateIndecreaseHandler")
public class TemplateIndecreaseHandler extends BaseWorkOrderHandler {

    private static final Logger        LOGGER = LoggerFactory.getLogger(TemplateIndecreaseHandler.class);

    @Autowired
    private TemplateLabelService        templateLabelService;

    @Autowired
    private TemplateLogicService       templateLogicService;

    @Autowired
    private TemplateAction             templateAction;

    @Autowired
    private TemplateQuotaManager templateQuotaManager;

    /**
     * ????????????????????????
     *
     * @param workOrder ????????????
     * @return result
     */
    @Override
    public boolean canAutoReview(WorkOrder workOrder) {
        return true;
    }

    @Override
    public AbstractOrderDetail getOrderDetail(String extensions) {
        TemplateIndecreaseContent content = JSON.parseObject(extensions, TemplateIndecreaseContent.class);

        return ConvertUtil.obj2Obj(content, TemplateIndecreaseOrderDetail.class);
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
        TemplateIndecreaseContent content = ConvertUtil.obj2ObjByJSON(workOrder.getContentObj(),
            TemplateIndecreaseContent.class);

        if (AriusObjUtils.isNull(content.getId())) {
            return Result.buildParamIllegal("??????id??????");
        }

        if (AriusObjUtils.isNull(content.getName())) {
            return Result.buildParamIllegal("??????????????????");
        }

        if (AriusObjUtils.isNull(content.getExpectQuota())) {
            return Result.buildParamIllegal("??????Quota??????");
        }

        if(AriusObjUtils.isNull(content.getExpectHotTime())) {
            return Result.buildParamIllegal("??????HotDay????????????");
        }

        return Result.buildSucc();
    }

    @Override
    protected String getTitle(WorkOrder workOrder) {
        TemplateIndecreaseContent content = ConvertUtil.obj2ObjByJSON(workOrder.getContentObj(),
            TemplateIndecreaseContent.class);

        WorkOrderTypeEnum workOrderTypeEnum = WorkOrderTypeEnum.valueOfName(workOrder.getType());
        if (workOrderTypeEnum == null) {
            return "";
        }
        return content.getName() + workOrderTypeEnum.getMessage();
    }

    /**
     * ????????????????????????????????????
     *
     * @param workOrder ????????????
     * @return result
     */
    @Override
    protected Result<Void> validateConsoleAuth(WorkOrder workOrder) {
        TemplateIndecreaseContent content = ConvertUtil.obj2ObjByJSON(workOrder.getContentObj(),
            TemplateIndecreaseContent.class);

        if (templateLabelService.isImportantIndex(content.getId())) {
            return Result.buildOpForBidden("????????????????????????????????????Arius???????????????");
        }

        IndexTemplateLogic templateLogic = templateLogicService.getLogicTemplateById(content.getId());
        if (!templateLogic.getAppId().equals(workOrder.getSubmitorAppid())) {
            return Result.buildOpForBidden("??????????????????????????????????????????");
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
        TemplateIndecreaseContent content = ConvertUtil.obj2ObjByJSON(workOrder.getContentObj(),
            TemplateIndecreaseContent.class);

        // ????????????quota????????????????????????????????????????????????
        Result<Void> result = templateAction.indecreaseWithAutoDistributeResource(content.getId(), content.getExpectHotTime(),
                content.getExpectExpireTime(), content.getExpectQuota(), workOrder.getSubmitor());

        //Quota??????
        if (!templateQuotaManager.controlAndPublish(content.getId())) {
            LOGGER.warn(
                "class=TemplateIndecreaseHandler||method=doProcessAgree||templateLogicId={}||msg=template quota publish failed!",
                content.getId());
        }

        sendNotify(WORK_ORDER_TEMPLATE_INDECREASE,
            new TemplateIndecencyNotify(workOrder.getSubmitorAppid(), content.getName()),
            Arrays.asList(workOrder.getSubmitor()));

        return result;
    }
}
