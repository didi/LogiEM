package com.didichuxing.datachannel.arius.admin.biz.workorder.handler;

import com.alibaba.fastjson.JSON;
import com.didichuxing.datachannel.arius.admin.biz.workorder.BaseWorkOrderHandler;
import com.didichuxing.datachannel.arius.admin.biz.workorder.content.TemplateAuthContent;
import com.didichuxing.datachannel.arius.admin.biz.workorder.notify.TemplateAuthNotify;
import com.didichuxing.datachannel.arius.admin.client.bean.common.Result;
import com.didichuxing.datachannel.arius.admin.client.constant.app.AppClusterLogicAuthEnum;
import com.didichuxing.datachannel.arius.admin.client.constant.app.AppTemplateAuthEnum;
import com.didichuxing.datachannel.arius.admin.client.constant.result.ResultType;
import com.didichuxing.datachannel.arius.admin.client.constant.workorder.WorkOrderTypeEnum;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.app.AppTemplateAuth;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.arius.AriusUserInfo;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ClusterLogic;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.workorder.WorkOrder;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.workorder.detail.AbstractOrderDetail;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.workorder.detail.TemplateAuthOrderDetail;
import com.didichuxing.datachannel.arius.admin.common.bean.po.order.WorkOrderPO;
import com.didichuxing.datachannel.arius.admin.common.exception.AdminOperateException;
import com.didichuxing.datachannel.arius.admin.common.util.AriusObjUtils;
import com.didichuxing.datachannel.arius.admin.common.util.ConvertUtil;
import com.didichuxing.datachannel.arius.admin.core.notify.NotifyTaskTypeEnum;
import com.didichuxing.datachannel.arius.admin.core.notify.info.auth.ImportantTemplateAuthNotifyInfo;
import com.didichuxing.datachannel.arius.admin.core.notify.service.NotifyService;
import com.didichuxing.datachannel.arius.admin.core.service.app.AppClusterLogicAuthService;
import com.didichuxing.datachannel.arius.admin.core.service.app.AppLogicTemplateAuthService;
import com.didichuxing.datachannel.arius.admin.core.service.template.logic.TemplateLogicService;
import com.didichuxing.datachannel.arius.admin.metadata.service.TemplateLabelService;
import com.google.common.collect.Lists;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.didichuxing.datachannel.arius.admin.core.notify.NotifyTaskTypeEnum.WORK_ORDER_TEMPLATE_AUTH;

/**
 * @author d06679
 * @date 2019/4/29
 */
@NoArgsConstructor
@Service("templateAuthHandler")
public class TemplateAuthHandler extends BaseWorkOrderHandler {

    @Value("${admin.url.console}")
    private String                                      adminUrlConsole;

    @Autowired
    private TemplateLogicService                        templateLogicService;

    @Autowired
    private AppLogicTemplateAuthService                 appLogicTemplateAuthService;

    @Autowired
    private TemplateLabelService                        templateLabelService;

    @Autowired
    private NotifyService                               notifyService;

    @Autowired
    private AppLogicTemplateAuthService                 logicTemplateAuthService;

    @Autowired
    private AppClusterLogicAuthService logicClusterAuthService;

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
        TemplateAuthContent content = JSON.parseObject(extensions, TemplateAuthContent.class);
        return ConvertUtil.obj2Obj(content, TemplateAuthOrderDetail.class);
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

    /**************************************** protected method ****************************************************/

    /**
     * ???????????????????????????
     *
     * @param workOrder ??????
     * @return result
     */
    @Override
    protected Result<Void> validateConsoleParam(WorkOrder workOrder) {
        TemplateAuthContent content = ConvertUtil.obj2ObjByJSON(workOrder.getContentObj(), TemplateAuthContent.class);

        if (AriusObjUtils.isNull(content.getId())) {
            return Result.buildParamIllegal("??????id??????");
        }

        if (AriusObjUtils.isNull(content.getName())) {
            return Result.buildParamIllegal("??????????????????");
        }

        if (AriusObjUtils.isNull(content.getAuthCode())) {
            return Result.buildParamIllegal("??????????????????");
        }

        AppTemplateAuthEnum authEnum = AppTemplateAuthEnum.valueOf(content.getAuthCode());
        if (AppTemplateAuthEnum.NO_PERMISSION.equals(authEnum)) {
            return Result.buildParamIllegal("??????????????????");
        }

        if (authEnum.equals(AppTemplateAuthEnum.OWN)
                && AriusObjUtils.isNull(content.getResponsible())) {
            return Result.buildParamIllegal("?????????????????????");
        }

        List<AppTemplateAuth> auths = appLogicTemplateAuthService.getTemplateAuthsByAppId(workOrder.getSubmitorAppid());
        Map<Integer, AppTemplateAuth> logicId2AppTemplateAuthMap = ConvertUtil.list2Map(auths,
            AppTemplateAuth::getTemplateId);
        AppTemplateAuth templateAuth = logicId2AppTemplateAuthMap.get(content.getId());
        if (templateAuth != null && templateAuth.getType() <= content.getAuthCode()) {
            return Result.buildParamIllegal("????????????????????????");
        }

        return Result.buildSucc();
    }

    @Override
    protected String getTitle(WorkOrder workOrder) {
        TemplateAuthContent content = ConvertUtil.obj2ObjByJSON(workOrder.getContentObj(), TemplateAuthContent.class);

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
        TemplateAuthContent content = ConvertUtil.obj2ObjByJSON(workOrder.getContentObj(), TemplateAuthContent.class);
        Integer logicTemplateId = content.getId();

        AppTemplateAuthEnum authEnum = AppTemplateAuthEnum.valueOf(content.getAuthCode());

        if (authEnum.equals(AppTemplateAuthEnum.OWN)) {
            // ??????????????????
            return Result.buildFrom(templateLogicService.turnOverLogicTemplate(logicTemplateId, workOrder.getSubmitorAppid(),
                    content.getResponsible(), workOrder.getSubmitor()));
        } else {
            // ??????????????????????????????????????????????????????????????????????????????????????????
            // ??????????????????
            ClusterLogic clusterLogic = templateLogicService
                .getLogicTemplateWithClusterAndMasterTemplate(logicTemplateId).getLogicCluster();

            if (clusterLogic == null) {
                // ???????????????????????????????????????
                return Result.buildFail(String.format("???????????????%s?????????????????????", logicTemplateId));
            }
            AppClusterLogicAuthEnum logicClusterAuthEnum = logicClusterAuthService
                .getLogicClusterAuthEnum(workOrder.getSubmitorAppid(), clusterLogic.getId());

            boolean addClusterAuth = false;
            // ???????????????????????????????????????
            if (logicClusterAuthEnum == AppClusterLogicAuthEnum.NO_PERMISSIONS) {
                logicClusterAuthService.ensureSetLogicClusterAuth(workOrder.getSubmitorAppid(), clusterLogic
								.getId(),
                    AppClusterLogicAuthEnum.ACCESS, workOrder.getSubmitor(), workOrder.getSubmitor());
                addClusterAuth = true;
            }

            // ????????????????????????
            Result<Void> result = logicTemplateAuthService.ensureSetLogicTemplateAuth(workOrder.getSubmitorAppid(),
                logicTemplateId, authEnum, workOrder.getSubmitor(), workOrder.getSubmitor());

            // ????????????
            if (result.success()) {
                sendNotify(WORK_ORDER_TEMPLATE_AUTH, new TemplateAuthNotify(workOrder.getSubmitorAppid(),
                    content.getName(), addClusterAuth, clusterLogic.getName()),
                    Arrays.asList(workOrder.getSubmitor()));

                // ?????????????????????????????????????????????
                if (templateLabelService.isImportantIndex(content.getId())) {
                    notifyService.send(NotifyTaskTypeEnum.IMPORTANT_TEMPLATE_AUTH,
                        new ImportantTemplateAuthNotifyInfo(logicTemplateId, content.getName(), adminUrlConsole),
                        Lists.newArrayList(workOrder.getSubmitor()));
                }
            }

            return Result.buildFrom(result);
        }
    }
}
