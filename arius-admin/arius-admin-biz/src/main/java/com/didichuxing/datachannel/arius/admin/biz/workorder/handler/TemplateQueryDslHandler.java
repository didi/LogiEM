package com.didichuxing.datachannel.arius.admin.biz.workorder.handler;

import com.alibaba.fastjson.JSON;
import com.didichuxing.datachannel.arius.admin.client.bean.common.Result;
import com.didichuxing.datachannel.arius.admin.client.constant.result.ResultType;
import com.didichuxing.datachannel.arius.admin.client.constant.workorder.WorkOrderTypeEnum;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.app.AppTemplateAuth;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.arius.AriusUserInfo;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.workorder.WorkOrder;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.workorder.detail.AbstractOrderDetail;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.workorder.detail.TemplateQueryDslOrderDetail;
import com.didichuxing.datachannel.arius.admin.common.bean.po.order.WorkOrderPO;
import com.didichuxing.datachannel.arius.admin.common.exception.AdminOperateException;
import com.didichuxing.datachannel.arius.admin.common.util.AriusObjUtils;
import com.didichuxing.datachannel.arius.admin.common.util.ConvertUtil;
import com.didichuxing.datachannel.arius.admin.core.service.app.AppLogicTemplateAuthService;
import com.didichuxing.datachannel.arius.admin.biz.workorder.BaseWorkOrderHandler;
import com.didichuxing.datachannel.arius.admin.biz.workorder.content.TemplateQueryDslContent;
import com.didichuxing.datachannel.arius.admin.biz.workorder.notify.TemplateQueryDslNotify;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.dsl.AuditDsls;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.dsl.DslInfo;
import com.didichuxing.datachannel.arius.admin.metadata.service.DslStatisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.didichuxing.datachannel.arius.admin.core.notify.NotifyTaskTypeEnum.WORK_ORDER_TEMPLATE_QUERY_DSL;

/**
 * @author d06679
 * @date 2019/4/29
 */
@Service("templateQueryDslHandler")
public class TemplateQueryDslHandler extends BaseWorkOrderHandler {

    @Autowired
    private AppLogicTemplateAuthService appLogicTemplateAuthService;

    @Autowired
    private DslStatisService dslStatisService;

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
        TemplateQueryDslContent content = JSON.parseObject(extensions, TemplateQueryDslContent.class);

        return ConvertUtil.obj2Obj(content, TemplateQueryDslOrderDetail.class);
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

    /**
     * ???????????????????????????
     *
     * @param workOrder ??????
     * @return result
     */
    @Override
    protected Result<Void> validateConsoleParam(WorkOrder workOrder) {
        TemplateQueryDslContent content = ConvertUtil.obj2ObjByJSON(workOrder.getContentObj(),
            TemplateQueryDslContent.class);

        if (AriusObjUtils.isNull(content.getId())) {
            return Result.buildParamIllegal("??????id??????");
        }

        if (AriusObjUtils.isNull(content.getName())) {
            return Result.buildParamIllegal("??????????????????");
        }

        if (AriusObjUtils.isNull(content.getDsl())) {
            return Result.buildParamIllegal("DSL????????????");
        }

        if (AriusObjUtils.isNull(content.getMemo())) {
            return Result.buildParamIllegal("DSL??????????????????");
        }

        return Result.buildSucc();
    }

    @Override
    protected String getTitle(WorkOrder workOrder) {
        TemplateQueryDslContent content = ConvertUtil.obj2ObjByJSON(workOrder.getContentObj(),
            TemplateQueryDslContent.class);

        WorkOrderTypeEnum workOrderTypeEnum = WorkOrderTypeEnum.valueOfName(workOrder.getType());
        if (workOrderTypeEnum == null) {
            return "";
        }
        return content.getName() + workOrderTypeEnum.getMessage();
    }

    /**************************************** protected method ******************************************/

    /**
     * ????????????????????????????????????
     *
     * @param workOrder ????????????
     * @return result
     */
    @Override
    protected Result<Void> validateConsoleAuth(WorkOrder workOrder) {
        TemplateQueryDslContent content = ConvertUtil.obj2ObjByJSON(workOrder.getContentObj(),
            TemplateQueryDslContent.class);

        List<AppTemplateAuth> appTemplateAuths = appLogicTemplateAuthService
            .getTemplateAuthsByLogicTemplateId(content.getId());
        Map<Integer, AppTemplateAuth> appId2AppTemplateAuthMap = ConvertUtil.list2Map(appTemplateAuths,
            AppTemplateAuth::getAppId);

        if (appId2AppTemplateAuthMap.containsKey(workOrder.getSubmitorAppid())) {
            return Result.buildSucc();
        }

        return Result.buildParamIllegal("??????APP?????????????????????????????????????????????????????????");
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
        TemplateQueryDslContent content = ConvertUtil.obj2ObjByJSON(workOrder.getContentObj(),
            TemplateQueryDslContent.class);

        DslInfo dslInfo = new DslInfo();
        dslInfo.setDsl(content.getDsl());
        dslInfo.setMemo(content.getMemo());

        List<DslInfo> dslInfos = new ArrayList<>();
        dslInfos.add(dslInfo);

        // ????????????quota?????????????????????
        AuditDsls auditDsls = new AuditDsls(workOrder.getSubmitorAppid(), workOrder.getSubmitor(), dslInfos);
        Result<String> result = dslStatisService.auditDsl(auditDsls);

        sendNotify(WORK_ORDER_TEMPLATE_QUERY_DSL,
            new TemplateQueryDslNotify(workOrder.getSubmitorAppid(), content.getName()),
            Arrays.asList(workOrder.getSubmitor()));

        return Result.buildFrom(result);
    }
}
