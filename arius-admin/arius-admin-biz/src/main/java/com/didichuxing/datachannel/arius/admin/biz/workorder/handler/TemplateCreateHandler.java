package com.didichuxing.datachannel.arius.admin.biz.workorder.handler;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.didichuxing.datachannel.arius.admin.biz.cluster.ClusterContextManager;
import com.didichuxing.datachannel.arius.admin.biz.template.TemplateAction;
import com.didichuxing.datachannel.arius.admin.biz.template.srv.mapping.TemplateLogicMappingManager;
import com.didichuxing.datachannel.arius.admin.biz.template.srv.mapping.TemplatePhyMappingManager;
import com.didichuxing.datachannel.arius.admin.biz.workorder.BaseWorkOrderHandler;
import com.didichuxing.datachannel.arius.admin.biz.workorder.content.TemplateCreateContent;
import com.didichuxing.datachannel.arius.admin.biz.workorder.notify.TemplateCreateNotify;
import com.didichuxing.datachannel.arius.admin.client.bean.common.LogicResourceConfig;
import com.didichuxing.datachannel.arius.admin.client.bean.common.Result;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.template.IndexTemplateLogicDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.template.IndexTemplatePhysicalDTO;
import com.didichuxing.datachannel.arius.admin.client.constant.operaterecord.OperationEnum;
import com.didichuxing.datachannel.arius.admin.client.constant.quota.NodeSpecifyEnum;
import com.didichuxing.datachannel.arius.admin.client.constant.resource.ResourceLogicTypeEnum;
import com.didichuxing.datachannel.arius.admin.client.constant.result.ResultType;
import com.didichuxing.datachannel.arius.admin.client.constant.template.TemplateDeployRoleEnum;
import com.didichuxing.datachannel.arius.admin.client.constant.workorder.WorkOrderTypeEnum;
import com.didichuxing.datachannel.arius.admin.client.mapping.AriusIndexTemplateSetting;
import com.didichuxing.datachannel.arius.admin.client.mapping.AriusTypeProperty;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.arius.AriusUserInfo;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ClusterLogic;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ClusterLogicContext;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.workorder.WorkOrder;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.workorder.detail.AbstractOrderDetail;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.workorder.detail.TemplateCreateOrderDetail;
import com.didichuxing.datachannel.arius.admin.common.bean.po.order.WorkOrderPO;
import com.didichuxing.datachannel.arius.admin.common.constant.AdminConstant;
import com.didichuxing.datachannel.arius.admin.common.exception.AdminOperateException;
import com.didichuxing.datachannel.arius.admin.common.util.AriusObjUtils;
import com.didichuxing.datachannel.arius.admin.common.util.ConvertUtil;
import com.didichuxing.datachannel.arius.admin.common.util.TemplateUtils;
import com.didichuxing.datachannel.arius.admin.core.component.QuotaTool;
import com.didichuxing.datachannel.arius.admin.core.service.app.AppClusterLogicAuthService;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.logic.ClusterLogicService;
import com.didichuxing.datachannel.arius.admin.core.service.common.AriusConfigInfoService;
import com.didichuxing.datachannel.arius.admin.core.service.template.logic.TemplateLogicService;
import com.google.common.collect.Lists;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static com.didichuxing.datachannel.arius.admin.common.constant.AdminConstant.*;
import static com.didichuxing.datachannel.arius.admin.common.constant.AriusConfigConstant.ARIUS_COMMON_GROUP;
import static com.didichuxing.datachannel.arius.admin.core.component.QuotaTool.TEMPLATE_QUOTA_MIN;
import static com.didichuxing.datachannel.arius.admin.core.notify.NotifyTaskTypeEnum.WORK_ORDER_TEMPLATE_CREATE;
import static com.didichuxing.datachannel.arius.admin.core.service.template.physic.impl.TemplatePhyServiceImpl.NOT_CHECK;

/**
 * @author d06679
 * @date 2019/4/29
 */
@Service("templateCreateHandler")
public class TemplateCreateHandler extends BaseWorkOrderHandler {

    @Autowired
    private TemplateLogicService        templateLogicService;

    @Autowired
    private QuotaTool                   quotaTool;

    @Autowired
    private TemplatePhyMappingManager   templatePhyMappingManager;

    @Autowired
    private TemplateAction              templateAction;

    @Autowired
    private ClusterLogicService         clusterLogicService;

    @Autowired
    private AriusConfigInfoService      ariusConfigInfoService;

    @Autowired
    private AppClusterLogicAuthService  logicClusterAuthService;

    @Autowired
    private ClusterContextManager       clusterContextManager;

    /**
     * ????????????????????????
     *
     * @param workOrder ????????????
     * @return result
     */
    @Override
    public boolean canAutoReview(WorkOrder workOrder) {
        TemplateCreateContent content = ConvertUtil.obj2ObjByJSON(workOrder.getContentObj(),
                TemplateCreateContent.class);
        ClusterLogic clusterLogic = clusterLogicService.getClusterLogicById(content.getResourceId());

        if (!clusterLogic.getType().equals(ResourceLogicTypeEnum.PUBLIC.getCode())) {
            return false;
        }

        LogicResourceConfig resourceConfig = clusterLogicService
                .genClusterLogicConfig(clusterLogic.getConfigJson());
        if (!resourceConfig.getTemplateCreateWorkOrderAutoProcess()) {
            return false;
        }

        Double autoProcessDiskMaxG = ariusConfigInfoService.doubleSetting(ARIUS_COMMON_GROUP,
                "arius.wo.auto.process.create.template.disk.maxG", 10.0);

        return content.getDiskQuota() < autoProcessDiskMaxG;
    }

    @Override
    public AbstractOrderDetail getOrderDetail(String extensions) {
        TemplateCreateContent content = JSON.parseObject(extensions, TemplateCreateContent.class);
        TemplateCreateOrderDetail templateCreateOrderDetail = ConvertUtil.obj2Obj(content, TemplateCreateOrderDetail.class);
        ClusterLogicContext clusterLogicContext = clusterContextManager.getClusterLogicContext(content.getResourceId());
        if (null != clusterLogicContext) {
            templateCreateOrderDetail.setClusterLogicName(clusterLogicContext.getClusterLogicName());
            templateCreateOrderDetail.setClusterPhyNameList(clusterLogicContext.getAssociatedClusterPhyNames());
        }

        return templateCreateOrderDetail;
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
        TemplateCreateContent content = ConvertUtil.obj2ObjByJSON(workOrder.getContentObj(),
                TemplateCreateContent.class);

        if (AriusObjUtils.isNull(content.getResponsible())) {
            return Result.buildParamIllegal("???????????????");
        }

        if (AriusObjUtils.isNull(content.getCyclicalRoll())) {
            return Result.buildParamIllegal("????????????????????????");
        }

        if (AriusObjUtils.isNull(content.getDiskQuota())) {
            return Result.buildParamIllegal("????????????????????????");
        }

        ClusterLogic clusterLogic = clusterLogicService.getClusterLogicById(content.getResourceId());
        if (clusterLogic == null) {
            return Result.buildParamIllegal("???????????????");
        }

        if (content.getCyclicalRoll() && AriusObjUtils.isNull(content.getDateField())) {
            return Result.buildParamIllegal("??????????????????");
        }

        // ??????????????????
        if (!logicClusterAuthService.canCreateLogicTemplate(workOrder.getSubmitorAppid(), content.getResourceId())) {
            return Result.buildFail(
                    String.format("APP[%s]?????????????????????[%s]????????????????????????", workOrder.getSubmitorAppid(), content.getResourceId()));
        }

        Result<Void> checkBaseInfoResult = templateLogicService
                .validateTemplate(buildTemplateLogicDTO(content, workOrder.getSubmitorAppid()), OperationEnum.ADD);
        if (checkBaseInfoResult.failed()) {
            return checkBaseInfoResult;
        }

        if (content.getMapping() != null) {
            Result<Void> checkMapping = templatePhyMappingManager.checkMappingForNew(content.getName(),
                    genTypeProperty(content.getMapping()));
            if (checkMapping.failed()) {
                return checkMapping;
            }
        }

        // ??????????????????????????????
        if (!content.getDataCenter().equals(clusterLogic.getDataCenter())) {
            return Result.buildParamIllegal("????????????????????????");
        }

        // ????????????????????????????????????

        return Result.buildSucc();
    }

    @Override
    protected String getTitle(WorkOrder workOrder) {
        TemplateCreateContent content = ConvertUtil.obj2ObjByJSON(workOrder.getContentObj(),
                TemplateCreateContent.class);

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
        if (!isOP(workOrder.getSubmitor())) {
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
        TemplateCreateContent content = ConvertUtil.obj2ObjByJSON(workOrder.getContentObj(),
                TemplateCreateContent.class);

        //????????????????????????
        Result<Void> checkBaseInfoResult = templateLogicService
                .validateTemplate(buildTemplateLogicDTO(content, workOrder.getSubmitorAppid()), OperationEnum.ADD);
        if (checkBaseInfoResult.failed()) {
            return checkBaseInfoResult;
        }

        return Result.buildSucc();
    }

    /**
     * ???????????? ?????????????????????????????????????????????????????????,????????????;????????????????????????????????????
     *
     * @param workOrder ??????
     * @return result
     */
    @Override
    protected Result<Void> doProcessAgree(WorkOrder workOrder, String approver) throws AdminOperateException {
        TemplateCreateContent content = ConvertUtil.obj2ObjByJSON(workOrder.getContentObj(),
                TemplateCreateContent.class);

        IndexTemplateLogicDTO logicDTO = buildTemplateLogicDTO(content, workOrder.getSubmitorAppid());

        logicDTO.setPhysicalInfos(Lists.newArrayList(buildTemplatePhysicalDTO(content, logicDTO)));
        logicDTO.setShardNum(fetchMaxShardNum(logicDTO.getPhysicalInfos()));

        Result<Integer> result = templateAction.createWithAutoDistributeResource(logicDTO, workOrder.getSubmitor());

        if (result.success()) {
            // ????????????????????????????????????mapping?????????mapping
            /**
             * ???????????????????????????mapping??????
             */
            /*if (StringUtils.isNoneBlank(content.getMapping()) && templateLogicMappingManager
                    .updateMappingForNew(result.getData(), genTypeProperty(content.getMapping())).failed()) {
                throw new AdminOperateException("??????mapping??????");
            }*/

            sendNotify(WORK_ORDER_TEMPLATE_CREATE,
                    new TemplateCreateNotify(workOrder.getSubmitorAppid(), content.getName()),
                    Arrays.asList(workOrder.getSubmitor()));
        }

        return Result.buildFrom(result);
    }

    /**************************************** private method ****************************************************/
    /**
     * ?????????????????????????????????????????????shard???
     * @param physicals ??????????????????
     * @return
     */
    private int fetchMaxShardNum(List<IndexTemplatePhysicalDTO> physicals) {
        int maxShardNum = -1;
        if (CollectionUtils.isNotEmpty(physicals)) {
            for (IndexTemplatePhysicalDTO physical : physicals) {
                if (physical.getShard() != null && physical.getShard() > maxShardNum) {
                    maxShardNum = physical.getShard();
                }
            }
        }
        return maxShardNum;
    }

    /**
     * ????????????????????????????????????DTO
     * @param content ????????????
     * @param submitorAppid appid
     * @return dto
     */
    private IndexTemplateLogicDTO buildTemplateLogicDTO(TemplateCreateContent content, Integer submitorAppid) {
        IndexTemplateLogicDTO logicDTO = ConvertUtil.obj2Obj(content, IndexTemplateLogicDTO.class);

        handleIndexTemplateLogic(content, logicDTO);

        if (content.getDiskQuota() < 0) {
            content.setDiskQuota(1024.0);
        }

        if (content.getPreCreateFlags() == null) {
            content.setPreCreateFlags(AdminConstant.DEFAULT_PRE_CREATE_FLAGS);
        }

        if (content.getDisableSourceFlags() == null) {
            content.setDisableSourceFlags(AdminConstant.DISABLE_SOURCE_FLAGS);
        }

        if (content.getShardNum() == null) {
            content.setShardNum(AdminConstant.DEFAULT_SHARD_NUM);
        }

        logicDTO.setQuota(quotaTool.getQuotaCountByDisk(NodeSpecifyEnum.DOCKER.getCode(), content.getDiskQuota() * 1.2,
                TEMPLATE_QUOTA_MIN));
        logicDTO.setAppId(submitorAppid);
        return logicDTO;
    }

    private void handleIndexTemplateLogic(TemplateCreateContent content, IndexTemplateLogicDTO logicDTO) {
        if (!content.getCyclicalRoll()) {
            // ???????????????
            logicDTO.setExpression(logicDTO.getName());
            logicDTO.setDateFormat("");
            logicDTO.setExpireTime(-1);
            logicDTO.setDateField("");
        } else {
            // ????????????
            logicDTO.setExpression(logicDTO.getName() + "*");

            // ???????????????????????????????????????
            if (content.getExpireTime() < 0) {
                logicDTO.setDateFormat(AdminConstant.YY_MM_DATE_FORMAT);
            } else {
                //???????????????????????????200G????????????????????????30??? ????????????
                double incrementPerDay = content.getDiskQuota() / content.getExpireTime();
                if (incrementPerDay >= 200.0 || content.getExpireTime() <= 30) {
                    if (StringUtils.isNotBlank(logicDTO.getDateField())
                            && !AdminConstant.MM_DD_DATE_FORMAT.equals(logicDTO.getDateField())) {
                        logicDTO.setDateFormat(AdminConstant.YY_MM_DD_DATE_FORMAT);
                    }
                } else {
                    logicDTO.setDateFormat(AdminConstant.YY_MM_DATE_FORMAT);
                }
            }
        }
    }

    /**
     * ????????????????????????????????????DTO
     * ?????????????????????????????????????????????????????????????????????????????????????????????????????????????????????master
     * @param content ????????????
     * @param logicDTO ????????????DTO
     * @return dto
     */
    private IndexTemplatePhysicalDTO buildTemplatePhysicalDTO(TemplateCreateContent content,
                                                              IndexTemplateLogicDTO logicDTO) {
        IndexTemplatePhysicalDTO physicalDTO = new IndexTemplatePhysicalDTO();

        physicalDTO.setLogicId(NOT_CHECK);
        physicalDTO.setName(logicDTO.getName());
        physicalDTO.setExpression(logicDTO.getExpression());
        physicalDTO.setGroupId(UUID.randomUUID().toString());
        physicalDTO.setRole(TemplateDeployRoleEnum.MASTER.getCode());
        physicalDTO.setResourceId(content.getResourceId());
        physicalDTO.setCluster(content.getCluster());
        physicalDTO.setRack(content.getRack());

        AriusIndexTemplateSetting settings = new AriusIndexTemplateSetting();
        if (StringUtils.isNotBlank(content.getCustomerAnalysis())) {
            settings.setAnalysis(JSON.parseObject(content.getCustomerAnalysis()));
            physicalDTO.setSettings(settings);
        }
        if (content.isCancelCopy()) {
            settings.setReplicasNum(0);
            physicalDTO.setSettings(settings);
        }
        if (content.isAsyncTranslog()) {
            settings.setTranslogDurability(AriusIndexTemplateSetting.ASYNC);
            physicalDTO.setSettings(settings);
        }
        if (StringUtils.isNotBlank(content.getDynamicTemplates())
                || StringUtils.isNotBlank(content.getMapping())) {
            AriusTypeProperty ariusTypeProperty = genTypeProperty(content.getMapping(), content.getDynamicTemplates());
            // ???????????????????????????type?????????????????????
            physicalDTO.setMappings(ariusTypeProperty.toMappingJSON().getJSONObject(DEFAULT_INDEX_MAPPING_TYPE).toJSONString());
        }

        setTemplateShard(physicalDTO, content, logicDTO);

        return physicalDTO;
    }

    private Integer genShardNumBySize(Double size) {
        double shardNumCeil = Math.ceil(size / G_PER_SHARD);
        return (int) shardNumCeil;
    }

    private AriusTypeProperty genTypeProperty(String mapping) {
        return genTypeProperty(mapping, null);
    }

    private AriusTypeProperty genTypeProperty(String mapping, String dynamicTemplates) {
        AriusTypeProperty typeProperty = new AriusTypeProperty();
        typeProperty.setTypeName(DEFAULT_INDEX_MAPPING_TYPE);
        // ?????????mapping????????????{}?????????json??????
        if (StringUtils.isBlank(mapping)) {
            mapping = "{}";
        }
        typeProperty.setProperties(JSON.parseObject(mapping));
        if (StringUtils.isNotBlank(dynamicTemplates)) {
            JSONArray dynamicTemplateArrays = JSONArray.parseArray(dynamicTemplates);
            if (CollectionUtils.isNotEmpty(dynamicTemplateArrays)) {
                typeProperty.setDynamicTemplates(dynamicTemplateArrays);
            }
        }
        return typeProperty;
    }

    private void setTemplateShard(IndexTemplatePhysicalDTO physicalDTO, TemplateCreateContent content,
                                  IndexTemplateLogicDTO logicDTO) {
        if (content.getCyclicalRoll()) {
            int expireTime = content.getExpireTime();
            if (expireTime < 0) {
                // ??????????????????????????????????????????180?????????????????????????????????????????????????????????shard
                expireTime = 180;
            }

            if (TemplateUtils.isSaveByDay(logicDTO.getDateFormat())) {
                // ????????????
                physicalDTO.setShard(genShardNumBySize(content.getDiskQuota() / expireTime));
            } else {
                // ????????????
                physicalDTO.setShard(genShardNumBySize((content.getDiskQuota() / expireTime) * 30));
            }
        } else {
            physicalDTO.setShard(genShardNumBySize(content.getDiskQuota()));
        }
    }

}
