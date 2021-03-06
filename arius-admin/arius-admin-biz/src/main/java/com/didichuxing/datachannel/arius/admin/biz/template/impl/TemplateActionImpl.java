package com.didichuxing.datachannel.arius.admin.biz.template.impl;

import static com.didichuxing.datachannel.arius.admin.client.constant.result.ResultType.NO_CAPACITY_PLAN;

import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ClusterPhy;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.template.IndexTemplateLogicWithPhyTemplates;
import com.didichuxing.datachannel.arius.admin.common.constant.template.TemplateServiceEnum;
import com.didichuxing.datachannel.arius.admin.common.util.AriusObjUtils;
import com.didichuxing.datachannel.arius.admin.common.util.ListUtils;
import com.didichuxing.datachannel.arius.admin.core.service.app.AppClusterLogicAuthService;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.logic.ClusterLogicService;
import java.util.List;
import java.util.UUID;

import com.didichuxing.datachannel.arius.admin.biz.template.TemplateAction;
import com.didichuxing.datachannel.arius.admin.biz.template.TemplateLogicManager;
import com.didichuxing.datachannel.arius.admin.biz.component.DistributorUtils;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.physic.ClusterPhyService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.didichuxing.datachannel.arius.admin.client.bean.common.Result;
import com.didichuxing.datachannel.arius.admin.client.bean.common.TemplateDistributedRack;
import com.didichuxing.datachannel.arius.admin.client.bean.common.TemplateResourceConfig;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.template.IndexTemplateLogicDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.template.IndexTemplatePhysicalDTO;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ClusterLogic;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.template.IndexTemplateLogic;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.template.IndexTemplatePhy;
import com.didichuxing.datachannel.arius.admin.common.exception.AdminOperateException;
import com.didichuxing.datachannel.arius.admin.biz.extend.intfc.ExtendServiceFactory;
import com.didichuxing.datachannel.arius.admin.biz.extend.intfc.TemplateClusterConfigProvider;
import com.didichuxing.datachannel.arius.admin.biz.extend.intfc.TemplateClusterDistributor;
import com.didichuxing.datachannel.arius.admin.core.service.template.logic.TemplateLogicService;
import com.didichuxing.datachannel.arius.admin.core.service.template.physic.TemplatePhyService;
import com.didiglobal.logi.log.ILog;
import com.didiglobal.logi.log.LogFactory;

/**
 * @author d06679
 * @date 2019-08-04
 */
@Service
public class TemplateActionImpl implements TemplateAction {

    private static final ILog          LOGGER = LogFactory.getLog(TemplateActionImpl.class);

    @Autowired
    private TemplateLogicService       templateLogicService;

    @Autowired
    private TemplatePhyService         templatePhyService;

    @Autowired
    private ExtendServiceFactory       extendServiceFactory;

    @Autowired
    private ClusterPhyService clusterPhyService;

    @Autowired
    private ClusterLogicService clusterLogicService;

    @Autowired
    private DistributorUtils           distributorUtils;

    @Autowired
    private AppClusterLogicAuthService logicClusterAuthService;

    @Autowired
    private TemplateLogicManager       templateLogicManager;

    /**
     * ??????????????????
     *
     * @param logicDTO ??????
     * @param operator ?????????
     * @return result
     * @throws AdminOperateException exception
     */
    @Override
    public Result<Integer> createWithAutoDistributeResource(IndexTemplateLogicDTO logicDTO,
                                                            String operator) throws AdminOperateException {
        // ????????????????????????
        if (CollectionUtils.isEmpty(logicDTO.getPhysicalInfos())) {
            return Result.buildFail("?????????????????????");
        }

        Long logicClusterId = logicDTO.getPhysicalInfos().get(0).getResourceId();
        if (!logicClusterAuthService.canCreateLogicTemplate(logicDTO.getAppId(), logicClusterId)) {
            return Result.buildFail(String.format("APP[%s]?????????????????????[%s]????????????????????????", logicDTO.getAppId(), logicClusterId));
        }

        return handleCreateWithAutoDistributeResource(logicDTO, operator);

    }

    /**
     * ?????????
     *
     * @param logicId          ??????id
     * @param expectHotTime    ???????????????????????????
     * @param expectExpireTime ??????????????????
     * @param expectQuota      ??????quota
     * @param submitor         ?????????
     * @return result
     */
    @Override
    public Result<Void> indecreaseWithAutoDistributeResource(Integer logicId, Integer expectHotTime, Integer expectExpireTime, Double expectQuota,
                                                       String submitor) throws AdminOperateException {
        IndexTemplateLogic templateLogic = templateLogicService.getLogicTemplateById(logicId);

        if (templateLogic == null) {
            return Result.buildParamIllegal("???????????????");
        }

        IndexTemplateLogicDTO logicDTO = new IndexTemplateLogicDTO();
        logicDTO.setId(logicId);
        logicDTO.setExpireTime(expectExpireTime);
        logicDTO.setQuota(expectQuota);

        //?????????????????????????????????????????????????????????
        Result<Void> validOpenColdAndHotServiceResult = validOpenColdAndHotServiceResult(logicId, expectHotTime, expectExpireTime);
        if (validOpenColdAndHotServiceResult.success()) {
            LOGGER.info("class=TemplateActionImpl||method=indecreaseWithAutoDistributeResource" +
                    "||msg={}", validOpenColdAndHotServiceResult.getMessage());
            logicDTO.setHotTime(expectHotTime);
        }

        List<IndexTemplatePhy> templatePhysicals = templatePhyService.getTemplateByLogicId(logicId);
        if (!CollectionUtils.isEmpty(templatePhysicals) && (expectQuota > templateLogic.getQuota())) {
            double deltaQuota = (expectQuota - templateLogic.getQuota()) / templatePhysicals.size();
            if (deltaQuota > 0) {
                for (IndexTemplatePhy templatePhysical : templatePhysicals) {
                    ClusterLogic clusterLogic = clusterLogicService
                        .getClusterLogicByRack(templatePhysical.getCluster(), templatePhysical.getRack());
                    Result<TemplateDistributedRack> distributorResult = increaseTemplateDistributedRack(
                        clusterLogic.getId(), templatePhysical.getCluster(), templatePhysical.getRack(),
                        deltaQuota);
                    if (distributorResult.failed()) {
                        LOGGER.warn(
                            "class=TemplateActionImpl||method=indecreaseWithAutoDistributeResource||resourceId={}||quota={}||msg=acquire cluster fail: {}",
                            clusterLogic.getId(), deltaQuota, distributorResult.getMessage());
                        return Result.buildFrom(distributorResult);
                    }
                }
            } else {
                LOGGER.info(
                    "class=TemplateActionImpl||method=indecreaseWithAutoDistributeResource||logicId={}||deltaQuota={}||msg=deltaQuota < 0",
                    logicId, deltaQuota);
            }
        }

        // ????????????quota?????????????????????
        return templateLogicService.editTemplate(logicDTO, submitor);
    }

    /**
     * ??????????????????????????????
     *
     * @param physicalId ????????????id
     * @return result
     */
    @Override
    public TemplateResourceConfig getPhysicalTemplateResourceConfig(Long physicalId) {
        TemplateClusterConfigProvider extendConfigProvider = null;
        Result<TemplateClusterConfigProvider> extendResult = extendServiceFactory
            .getExtend(TemplateClusterConfigProvider.class);
        if (extendResult.success()) {
            extendConfigProvider = extendResult.getData();
        } else {
            LOGGER.warn("class=TemplateActionImpl||method=createWithAutoDistributeResource||msg=extendConfigProvider not find");
        }

        TemplateClusterConfigProvider defaultConfigProvider = extendServiceFactory
            .getDefault(TemplateClusterConfigProvider.class);

        Result<TemplateResourceConfig> configResult = null;

        if (extendConfigProvider != null) {
            configResult = extendConfigProvider.getTemplateResourceConfig(physicalId);
        }

        if (configResult == null || configResult.getCode().equals(NO_CAPACITY_PLAN.getCode())) {
            configResult = defaultConfigProvider.getTemplateResourceConfig(physicalId);
        }

        if (configResult.failed()) {
            return new TemplateResourceConfig();
        }

        return configResult.getData();
    }

    /**************************************** private methods ****************************************/
    /**
     * ????????????
     * @param resourceId ??????
     * @param cluster ??????
     * @param quota rack
     * @return result
     */
    private Result<TemplateDistributedRack> increaseTemplateDistributedRack(Long resourceId, String cluster,
                                                                            String rack, double quota) {
        TemplateClusterDistributor extendDistributor = null;

        Result<TemplateClusterDistributor> extendResult = extendServiceFactory
            .getExtend(TemplateClusterDistributor.class);
        if (extendResult.success()) {
            extendDistributor = extendResult.getData();
        } else {
            LOGGER.warn("class=TemplateActionImpl||method=getTemplateResourceInner||msg=extendDistributor not find");
        }

        TemplateClusterDistributor defaultDistributor = extendServiceFactory
            .getDefault(TemplateClusterDistributor.class);

        Result<TemplateDistributedRack> distributedRackResult = null;
        if (extendDistributor != null) {
            distributedRackResult = extendDistributor.indecrease(resourceId, cluster, rack, quota);
        }

        if (distributedRackResult == null || distributedRackResult.getCode().equals(NO_CAPACITY_PLAN.getCode())) {
            distributedRackResult = defaultDistributor.indecrease(resourceId, cluster, rack, quota);
        }

        if (distributedRackResult.failed()) {
            return distributedRackResult;
        }

        return distributedRackResult;
    }

    private Result<Integer> handleCreateWithAutoDistributeResource(IndexTemplateLogicDTO logicDTO, String operator) throws AdminOperateException {
        int indexDefaultWriterSetFlags = -1;
        for (IndexTemplatePhysicalDTO physicalDTO : logicDTO.getPhysicalInfos()) {
            if (StringUtils.isNotBlank(physicalDTO.getCluster()) && physicalDTO.getRack() != null) {
                handleIndexTemplatePhysical(physicalDTO);
                continue;
            }
            if (indexDefaultWriterSetFlags == -1) {
                indexDefaultWriterSetFlags = 0;
            }
            Result<TemplateDistributedRack> distributedRackResult = distributorUtils.getTemplateDistributedRack(physicalDTO.getResourceId(), logicDTO.getQuota());
            if (distributedRackResult.failed()) {
                LOGGER.warn("class=TemplateActionImpl||method=createWithAutoDistributeResource||msg=distributedRackResult fail");
                return Result.buildFrom(distributedRackResult);
            }
            physicalDTO.setCluster(distributedRackResult.getData().getCluster());
            physicalDTO.setRack(distributedRackResult.getData().getRack());
            physicalDTO.setDefaultWriterFlags(false);
            if (indexDefaultWriterSetFlags <= 0 && distributedRackResult.getData().isResourceMatched()) {
                physicalDTO.setDefaultWriterFlags(true);
                indexDefaultWriterSetFlags = 1;
            }
            if (StringUtils.isBlank(physicalDTO.getGroupId())) {
                physicalDTO.setGroupId(UUID.randomUUID().toString());
            }
        }
        if (indexDefaultWriterSetFlags == 0) {
            return Result.buildFail("????????????????????????");
        }

        return templateLogicManager.createLogicTemplate(logicDTO, operator);
    }

    private void handleIndexTemplatePhysical(IndexTemplatePhysicalDTO physicalDTO) {
        if (physicalDTO.getDefaultWriterFlags() == null) {
            physicalDTO.setDefaultWriterFlags(true);
        }
        if (StringUtils.isBlank(physicalDTO.getGroupId())) {
            physicalDTO.setGroupId(UUID.randomUUID().toString());
        }
    }

    /**
     * ??????????????????????????????????????????????????????????????????
     *
     * @param logicId          ???????????????id
     * @param expectHotTime    ?????????????????????
     * @param expectExpireTime ??????????????????????????????
     * @return ????????????
     */
    private Result<Void> validOpenColdAndHotServiceResult(Integer logicId, Integer expectHotTime, Integer expectExpireTime) {
        IndexTemplateLogicWithPhyTemplates logicTemplateWithPhysicalsById = templateLogicService.getLogicTemplateWithPhysicalsById(logicId);
        if (AriusObjUtils.isNull(logicTemplateWithPhysicalsById) ||
                CollectionUtils.isEmpty(logicTemplateWithPhysicalsById.getPhysicals())) {
            return Result.buildFail("??????????????????????????????");
        }

        //?????????????????????????????????????????????
        IndexTemplatePhy indexTemplatePhy = logicTemplateWithPhysicalsById.getPhysicals().get(0);
        if (AriusObjUtils.isNull(indexTemplatePhy)) {
            return Result.buildFail("????????????????????????");
        }

        //????????????????????????????????????
        ClusterPhy clusterPhyByName = clusterPhyService.getClusterByName(indexTemplatePhy.getCluster());
        if (AriusObjUtils.isNull(clusterPhyByName)) {
            return Result.buildFail("????????????????????????");
        }

        //????????????????????????????????????????????????
        String templateSrvs = clusterPhyByName.getTemplateSrvs();
        List<String> templateSrvIds = ListUtils.string2StrList(templateSrvs);
        if (CollectionUtils.isEmpty(templateSrvIds) ||
                !templateSrvIds.contains(TemplateServiceEnum.TEMPLATE_COLD.getCode().toString())) {
            return Result.buildFail("??????????????????????????????????????????");
        }

        //??????????????????????????????????????????????????????????????????????????????
        if (expectExpireTime != -1 || expectExpireTime < expectHotTime) {
            return Result.buildFail("????????????????????????????????????????????????????????????");
        }

        return Result.buildSucc();
    }
}
