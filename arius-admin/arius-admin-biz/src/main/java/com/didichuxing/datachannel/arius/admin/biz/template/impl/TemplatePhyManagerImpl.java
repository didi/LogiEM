package com.didichuxing.datachannel.arius.admin.biz.template.impl;

import java.util.*;
import java.util.stream.Collectors;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.didichuxing.datachannel.arius.admin.biz.template.TemplatePhyManager;
import com.didichuxing.datachannel.arius.admin.biz.template.srv.capacityplan.IndexPlanManager;
import com.didichuxing.datachannel.arius.admin.biz.template.srv.precreate.TemplatePreCreateManager;
import com.didichuxing.datachannel.arius.admin.client.bean.common.IndexTemplatePhysicalConfig;
import com.didichuxing.datachannel.arius.admin.client.bean.common.Result;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.template.IndexTemplateLogicDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.template.IndexTemplatePhysicalDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.template.TemplatePhysicalCopyDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.template.TemplatePhysicalUpgradeDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.vo.template.ConsoleTemplatePhyVO;
import com.didichuxing.datachannel.arius.admin.client.bean.vo.template.IndexTemplatePhysicalVO;
import com.didichuxing.datachannel.arius.admin.client.constant.app.AppTemplateAuthEnum;
import com.didichuxing.datachannel.arius.admin.client.constant.operaterecord.OperationEnum;
import com.didichuxing.datachannel.arius.admin.client.constant.result.ResultType;
import com.didichuxing.datachannel.arius.admin.client.constant.template.TemplateDeployRoleEnum;
import com.didichuxing.datachannel.arius.admin.client.constant.template.TemplatePhysicalStatusEnum;
import com.didichuxing.datachannel.arius.admin.client.mapping.AriusIndexTemplateSetting;
import com.didichuxing.datachannel.arius.admin.common.Tuple;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ClusterPhy;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.operaterecord.template.TemplateOperateRecord;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.template.IndexTemplateLogic;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.template.IndexTemplateLogicWithPhyTemplates;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.template.IndexTemplatePhy;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.template.IndexTemplatePhyWithLogic;
import com.didichuxing.datachannel.arius.admin.common.component.SpringTool;
import com.didichuxing.datachannel.arius.admin.common.constant.TemplateOperateRecordEnum;
import com.didichuxing.datachannel.arius.admin.common.event.template.PhysicalTemplateAddEvent;
import com.didichuxing.datachannel.arius.admin.common.event.template.PhysicalTemplateModifyEvent;
import com.didichuxing.datachannel.arius.admin.common.exception.AdminOperateException;
import com.didichuxing.datachannel.arius.admin.common.exception.ESOperateException;
import com.didichuxing.datachannel.arius.admin.common.util.*;
import com.didichuxing.datachannel.arius.admin.core.notify.NotifyTaskTypeEnum;
import com.didichuxing.datachannel.arius.admin.core.notify.info.cluster.ClusterTemplatePhysicalMetaErrorNotifyInfo;
import com.didichuxing.datachannel.arius.admin.core.notify.info.template.TemplatePhysicalMetaErrorNotifyInfo;
import com.didichuxing.datachannel.arius.admin.core.notify.service.NotifyService;
import com.didichuxing.datachannel.arius.admin.core.service.app.AppLogicTemplateAuthService;
import com.didichuxing.datachannel.arius.admin.core.service.app.AppService;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.physic.ClusterPhyService;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.region.RegionRackService;
import com.didichuxing.datachannel.arius.admin.core.service.common.AriusConfigInfoService;
import com.didichuxing.datachannel.arius.admin.core.service.common.OperateRecordService;
import com.didichuxing.datachannel.arius.admin.core.service.es.ESTemplateService;
import com.didichuxing.datachannel.arius.admin.core.service.template.logic.TemplateLogicService;
import com.didichuxing.datachannel.arius.admin.core.service.template.physic.TemplatePhyService;
import com.didichuxing.datachannel.arius.admin.core.service.template.physic.impl.TemplatePhyServiceImpl;
import com.didichuxing.datachannel.arius.admin.metadata.service.TemplateLabelService;
import com.didiglobal.logi.elasticsearch.client.response.setting.common.MappingConfig;
import com.didiglobal.logi.elasticsearch.client.response.setting.template.TemplateConfig;
import com.didiglobal.logi.log.ILog;
import com.didiglobal.logi.log.LogFactory;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static com.didichuxing.datachannel.arius.admin.client.constant.operaterecord.ModuleEnum.TEMPLATE;
import static com.didichuxing.datachannel.arius.admin.client.constant.operaterecord.OperationEnum.COPY;
import static com.didichuxing.datachannel.arius.admin.client.constant.operaterecord.OperationEnum.EDIT;
import static com.didichuxing.datachannel.arius.admin.client.constant.template.TemplateDeployRoleEnum.MASTER;
import static com.didichuxing.datachannel.arius.admin.client.constant.template.TemplateDeployRoleEnum.SLAVE;
import static com.didichuxing.datachannel.arius.admin.common.constant.AdminConstant.MILLIS_PER_DAY;
import static com.didichuxing.datachannel.arius.admin.common.constant.AriusConfigConstant.ARIUS_COMMON_GROUP;
import static com.didichuxing.datachannel.arius.admin.common.util.IndexNameFactory.genIndexNameClear;
import static com.didichuxing.datachannel.arius.admin.persistence.constant.ESOperateContant.*;

@Component
public class TemplatePhyManagerImpl implements TemplatePhyManager {

    private static final ILog           LOGGER                    = LogFactory.getLog(TemplatePhyServiceImpl.class);

    public static final Integer         NOT_CHECK                 = -100;
    private static final Integer        INDEX_OP_OK               = 0;
    private static final Integer        TOMORROW_INDEX_NOT_CREATE = 1;
    private static final Integer        EXPIRE_INDEX_NOT_DELETE   = 2;
    private static final Integer        INDEX_ALL_ERR             = TOMORROW_INDEX_NOT_CREATE + EXPIRE_INDEX_NOT_DELETE;

    private static final String TEMPLATE_PHYSICAL_ID_IS_NULL = "????????????id??????";

    private static final String TEMPLATE_PHYSICAL_NOT_EXISTS = "?????????????????????";

    private static final String CHECK_FAIL_MSG = "check fail||msg={}";

    @Autowired
    private OperateRecordService        operateRecordService;

    @Autowired
    private ClusterPhyService           clusterPhyService;

    @Autowired
    private TemplateLabelService        templateLabelService;

    @Autowired
    private ESTemplateService           esTemplateService;

    @Autowired
    private TemplatePreCreateManager    templatePreCreateManager;

    @Autowired
    private IndexPlanManager indexPlanManager;

    @Autowired
    private RegionRackService           regionRackService;

    @Autowired
    private TemplateLogicService        templateLogicService;

    @Autowired
    private TemplatePhyService          templatePhyService;

    @Autowired
    private NotifyService               notifyService;

    @Autowired
    private AriusConfigInfoService      ariusConfigInfoService;

    @Autowired
    private AppLogicTemplateAuthService appLogicTemplateAuthService;

    @Autowired
    private AppService                  appService;

    @Override
    public boolean checkMeta() {
        List<IndexTemplatePhy> templatePhysicals = templatePhyService.listTemplate();

        List<IndexTemplateLogic> templateLogics = templateLogicService.getAllLogicTemplates();
        Map<Integer, IndexTemplateLogic> logicId2IndexTemplateLogicMap = ConvertUtil.list2Map(templateLogics,
                IndexTemplateLogic::getId);

        Multimap<String, IndexTemplatePhy> cluster2IndexTemplatePhysicalMultiMap = ConvertUtil
                .list2MulMap(templatePhysicals, IndexTemplatePhy::getCluster);

        Set<String> esClusters = clusterPhyService.listAllClusters().stream().map( ClusterPhy::getCluster)
                .collect( Collectors.toSet());

        for (String cluster : cluster2IndexTemplatePhysicalMultiMap.keySet()) {
            int tomorrowIndexNotCreateCount = 0;
            int expireIndexNotDeleteCount = 0;

            Collection<IndexTemplatePhy> clusterTemplates = cluster2IndexTemplatePhysicalMultiMap.get(cluster);

            for (IndexTemplatePhy templatePhysical : clusterTemplates) {
                try {
                    Result<Void> result = checkMetaInner(templatePhysical, logicId2IndexTemplateLogicMap, esClusters);
                    if (result.success()) {
                        LOGGER.info("class=TemplatePhyManagerImpl||method=metaCheck||msg=succ||physicalId={}", templatePhysical.getId());
                    } else {
                        LOGGER.warn("class=TemplatePhyManagerImpl||method=metaCheck||msg=fail||physicalId={}||failMsg={}", templatePhysical.getId(),
                                result.getMessage());
                        notifyService.send( NotifyTaskTypeEnum.TEMPLATE_PHYSICAL_META_ERROR,
                                new TemplatePhysicalMetaErrorNotifyInfo(templatePhysical, result.getMessage()),
                                Arrays.asList());
                    }
                    int indexOpResult = checkIndexCreateAndExpire(templatePhysical, logicId2IndexTemplateLogicMap);
                    if (indexOpResult == TOMORROW_INDEX_NOT_CREATE || indexOpResult == INDEX_ALL_ERR) {
                        tomorrowIndexNotCreateCount++;
                    }
                    if (indexOpResult == EXPIRE_INDEX_NOT_DELETE || indexOpResult == INDEX_ALL_ERR) {
                        expireIndexNotDeleteCount++;
                    }

                } catch (Exception e) {
                    LOGGER.error("class=TemplatePhyServiceImpl||method=metaCheck||errMsg={}||physicalId={}||",
                            e.getMessage(), templatePhysical.getId(), e);
                }
            }

            List<String> errMsgs = Lists.newArrayList();
            if (tomorrowIndexNotCreateCount * 1.0 / clusterTemplates.size() > 0.7) {
                errMsgs.add("???" + tomorrowIndexNotCreateCount + "???????????????????????????????????????");
            }
            if (expireIndexNotDeleteCount * 1.0 / clusterTemplates.size() > 0.7) {
                errMsgs.add("???" + expireIndexNotDeleteCount + "???????????????????????????????????????");
            }

            if (CollectionUtils.isNotEmpty(errMsgs)) {
                notifyService.send(NotifyTaskTypeEnum.CLUSTER_TEMPLATE_PHYSICAL_META_ERROR,
                        new ClusterTemplatePhysicalMetaErrorNotifyInfo(cluster, String.join(",", errMsgs)),
                        Arrays.asList());
            }

        }

        return true;
    }

    @Override
    public void syncMeta(Long physicalId, int retryCount) throws ESOperateException {

        // ??????????????????????????????
        IndexTemplatePhy indexTemplatePhy = templatePhyService.getTemplateById(physicalId);
        if (indexTemplatePhy == null) {
            return;
        }

        // ???ES????????????????????????
        TemplateConfig templateConfig = esTemplateService.syncGetTemplateConfig(indexTemplatePhy.getCluster(),
                indexTemplatePhy.getName());

        if (templateConfig == null) {
            // es?????????????????????????????????
            esTemplateService.syncCreate(indexTemplatePhy.getCluster(), indexTemplatePhy.getName(), indexTemplatePhy.getExpression(),
                    indexTemplatePhy.getRack(), indexTemplatePhy.getShard(), indexTemplatePhy.getShardRouting(), retryCount);

        } else {
            // ???????????????
            if (
                    !indexTemplatePhy.getExpression().equals(templateConfig.getTemplate()) &&
                            esTemplateService.syncUpdateExpression(indexTemplatePhy.getCluster(), indexTemplatePhy.getName(),
                                    indexTemplatePhy.getExpression(), retryCount)
            ) {
                // ??????????????????????????????????????????????????????ES??????
                LOGGER.info("class=TemplatePhyManagerImpl||method=syncMeta||msg=syncUpdateExpression succ||template={}||srcExp={}||tgtExp={}",
                        indexTemplatePhy.getName(), templateConfig.getTemplate(), indexTemplatePhy.getExpression());
            }

            // ??????shard???rack??????????????????
            boolean editShardOrRack = false;
            Map<String, String> settings = templateConfig.getSetttings();
            String rack = settings.get(TEMPLATE_INDEX_INCLUDE_RACK);
            String shardNum = settings.get(INDEX_SHARD_NUM);

            // ??????shard??????
            if (!String.valueOf(indexTemplatePhy.getShard()).equals(shardNum)) {
                editShardOrRack = true;
                shardNum = String.valueOf(indexTemplatePhy.getShard());
            }

            // ??????rack
            if (
                    StringUtils.isNotBlank(indexTemplatePhy.getRack()) &&
                            (!settings.containsKey(TEMPLATE_INDEX_INCLUDE_RACK)
                                    || !indexTemplatePhy.getRack().equals(settings.get(TEMPLATE_INDEX_INCLUDE_RACK)))
            ) {
                editShardOrRack = true;
                rack = indexTemplatePhy.getRack();
            }

            if (editShardOrRack && esTemplateService.syncUpdateRackAndShard(indexTemplatePhy.getCluster(), indexTemplatePhy.getName(), rack,
                    Integer.valueOf(shardNum), indexTemplatePhy.getShardRouting(), retryCount)) {
                // ???????????????ES??????
                    LOGGER.info(
                            "class=TemplatePhyManagerImpl||method=syncMeta||msg=syncUpdateRackAndShard succ||template={}||srcRack={}||srcShard={}||tgtRack={}||tgtShard={}",
                            indexTemplatePhy.getName(), settings.get(TEMPLATE_INDEX_INCLUDE_RACK), settings.get(INDEX_SHARD_NUM),
                            rack, shardNum);
            }
        }
    }

    @Override
    public Result<Void> delTemplate(Long physicalId, String operator) throws ESOperateException {
        return templatePhyService.delTemplate(physicalId, operator);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> delTemplateByLogicId(Integer logicId, String operator) throws ESOperateException {
        List<IndexTemplatePhy> indexTemplatePhys = templatePhyService.getTemplateByLogicId(logicId);

        boolean succ = true;
        if (CollectionUtils.isEmpty(indexTemplatePhys)) {
            LOGGER.info("class=TemplatePhyManagerImpl||method=delTemplateByLogicId||logicId={}||msg=template no physical info!", logicId);
        } else {
            LOGGER.info("class=TemplatePhyManagerImpl||method=delTemplateByLogicId||logicId={}||physicalSize={}||msg=template has physical info!",
                    logicId, indexTemplatePhys.size());
            for (IndexTemplatePhy indexTemplatePhy : indexTemplatePhys) {
                if (delTemplate(indexTemplatePhy.getId(), operator).failed()) {
                    succ = false;
                }

            }
        }

        return Result.build(succ);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> upgradeTemplate(TemplatePhysicalUpgradeDTO param, String operator) throws ESOperateException {
        Result<Void> checkResult = checkUpgradeParam(param);
        if (checkResult.failed()) {
            LOGGER.warn("class=TemplatePhyManagerImpl||method=upgradeTemplate||msg={}", CHECK_FAIL_MSG + checkResult.getMessage());
            return checkResult;
        } else {
            operateRecordService.save(TEMPLATE, EDIT, param.getLogicId(), JSON.toJSONString(new TemplateOperateRecord(TemplateOperateRecordEnum.UPGRADE.getCode(),
                    "????????????????????????" + param.getVersion())), operator);
        }

        return upgradeTemplateWithCheck(param, operator, 0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> rolloverUpgradeTemplate(TemplatePhysicalUpgradeDTO param, String operator) throws ESOperateException {
        //rollover ??????????????????????????????????????????
        return upgradeTemplateWithCheck(param, operator, 0);
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Boolean> upgradeMultipleTemplate(List<TemplatePhysicalUpgradeDTO> params,
                                                   String operator) throws ESOperateException {
        if (CollectionUtils.isEmpty(params)) {
            Result.buildFail("????????????");
        }

        for (TemplatePhysicalUpgradeDTO param : params) {
            Result<Void> ret = upgradeTemplate(param, operator);
            if (ret.failed()) {
                throw new ESOperateException(ret.getMessage());
            }
        }
        return Result.buildSucc(true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> copyTemplate(TemplatePhysicalCopyDTO param, String operator) throws AdminOperateException {
        Result<Void> checkResult = checkCopyParam(param);
        if (checkResult.failed()) {
            LOGGER.warn("class=TemplatePhyManagerImpl||method=copyTemplate||msg={}", CHECK_FAIL_MSG + checkResult.getMessage());
            return checkResult;
        }

        IndexTemplatePhy indexTemplatePhy = templatePhyService.getTemplateById(param.getPhysicalId());
        IndexTemplatePhysicalDTO tgtTemplateParam = ConvertUtil.obj2Obj(indexTemplatePhy, IndexTemplatePhysicalDTO.class);
        tgtTemplateParam.setCluster(param.getCluster());
        tgtTemplateParam.setRack(param.getRack());
        tgtTemplateParam.setRole(SLAVE.getCode());
        tgtTemplateParam.setShard(param.getShard());
        tgtTemplateParam.setVersion(indexTemplatePhy.getVersion());

        Result<Long> addResult = addTemplateWithoutCheck(tgtTemplateParam);
        if (addResult.failed()) {
            return Result.buildFrom(addResult);
        }

        // ??????????????????
        operateRecordService.save(TEMPLATE, COPY, indexTemplatePhy.getLogicId(),
                String.format("?????????%s?????????????????????%s???", indexTemplatePhy.getCluster(), param.getCluster()), operator);

        if (esTemplateService.syncCopyMappingAndAlias(indexTemplatePhy.getCluster(), indexTemplatePhy.getName(),
                tgtTemplateParam.getCluster(), tgtTemplateParam.getName(), 0)) {
            LOGGER.info("class=TemplatePhyManagerImpl||methood=copyTemplate||TemplatePhysicalCopyDTO={}||msg=syncCopyMappingAndAlias succ", param);
        } else {
            LOGGER.warn("class=TemplatePhyManagerImpl||methood=copyTemplate||TemplatePhysicalCopyDTO={}||msg=syncCopyMappingAndAlias fail", param);
        }

        return Result.buildSucWithTips("????????????????????????!???????????????APP????????????????????????????????????rack\n????????????????????????????????????quota????????????");
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> editTemplate(IndexTemplatePhysicalDTO param, String operator) throws ESOperateException {
        Result<Void> checkResult = validateTemplate(param, EDIT);
        if (checkResult.failed()) {
            LOGGER.warn("class=TemplatePhyManagerImpl||method=editTemplate||msg={}", CHECK_FAIL_MSG + checkResult.getMessage());
            return checkResult;
        }

        IndexTemplatePhy oldIndexTemplatePhy = templatePhyService.getTemplateById(param.getId());
        Result<Void> result = editTemplateWithoutCheck(param, operator, 0);
        if (result.success()) {
            String editContent = AriusObjUtils.findChangedWithClear(oldIndexTemplatePhy, param);
            if (StringUtils.isNotBlank(editContent)) {
                operateRecordService.save(TEMPLATE, EDIT, param.getLogicId(),
                        JSON.toJSONString(new TemplateOperateRecord(TemplateOperateRecordEnum.CONFIG.getCode(), editContent)), operator);
            }
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Boolean> editMultipleTemplate(List<IndexTemplatePhysicalDTO> params,
                                                String operator) throws ESOperateException {
        if (CollectionUtils.isEmpty(params)) {
            Result.buildFail("????????????");
        }

        for (IndexTemplatePhysicalDTO param : params) {
            Result<Void> ret = editTemplate(param, operator);
            if (ret.failed()) {
                throw new ESOperateException(String.format("????????????:%s??????", param.getName()));
            }
        }

        return Result.buildSucc(true);
    }

    @Override
    public Result<Void> validateTemplate(IndexTemplatePhysicalDTO param, OperationEnum operation) {
        if (AriusObjUtils.isNull(param)) {
            return Result.buildParamIllegal("????????????????????????");
        }
        if (operation == OperationEnum.ADD) {
            Result<Void> result = handleValidateTemplateAdd(param);
            if (result.failed()) {return result;}
        } else if (operation == EDIT) {
            Result<Void> result = handleValidateTemplateEdit(param);
            if (result.failed()) {return result;}
        }

        Result<Void> result = handleValidateTemplate(param);
        if (result.failed()) {return result;}

        return Result.buildSucc();
    }

    @Override
    public Result<Void> validateTemplates(List<IndexTemplatePhysicalDTO> params, OperationEnum operation) {
        if (AriusObjUtils.isNull(params)) {
            return Result.buildParamIllegal("????????????????????????");
        }

        Set<String> deployClusterSet = Sets.newTreeSet();
        for (IndexTemplatePhysicalDTO param : params) {
            Result<Void> checkResult = validateTemplate(param, operation);
            if (checkResult.failed()) {
                LOGGER.warn("class=TemplatePhyManagerImpl||method=validateTemplates||msg={}", CHECK_FAIL_MSG + checkResult.getMessage());
                checkResult
                        .setMessage(checkResult.getMessage() + "; ??????:" + param.getCluster() + ",??????:" + param.getName());
                return checkResult;
            }

            if (deployClusterSet.contains(param.getCluster())) {
                return Result.buildParamIllegal("??????????????????");
            } else {
                deployClusterSet.add(param.getCluster());
            }

        }

        return Result.buildSucc();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> addTemplatesWithoutCheck(Integer logicId,
                                           List<IndexTemplatePhysicalDTO> physicalInfos) throws AdminOperateException {
        for (IndexTemplatePhysicalDTO param : physicalInfos) {
            param.setLogicId(logicId);
            param.setPhysicalInfos(physicalInfos);
            Result<Long> result = addTemplateWithoutCheck(param);
            if (result.failed()) {
                result.setMessage(result.getMessage() + "; ??????:" + param.getCluster() + ",??????:" + param.getName());
                return Result.buildFrom(result);
            }
        }
        return Result.buildSucc();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Long> addTemplateWithoutCheck(IndexTemplatePhysicalDTO param) throws AdminOperateException {
        if (null != templatePhyService.getTemplateByClusterAndName(param.getCluster(), param.getName())) {
            return Result.buildParamIllegal("??????????????????");
        }

        initParamWhenAdd(param);

        // ?????????????????????????????????????????????????????????shard????????????????????????????????????shard?????????????????????shard??????
        indexPlanManager.initShardRoutingAndAdjustShard(param);
        Result<Long> result = templatePhyService.insert(param);
        Long physicalId = result.getData();
        if (result.success()) {
            //????????????????????????????????????
            templatePhyService.deleteDirtyByClusterAndName(param.getCluster(), param.getName());

            //??????????????????
            syncCreateIndexTemplateWithEs(param);

            SpringTool.publish(new PhysicalTemplateAddEvent(this, templatePhyService.getTemplateById(physicalId),
                    buildIndexTemplateLogicWithPhysicalForNew(param)));
        }

        return Result.buildSucc(physicalId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> editTemplateFromLogic(IndexTemplateLogicDTO param, String operator) throws ESOperateException {
        List<IndexTemplatePhy> indexTemplatePhys = templatePhyService.getTemplateByLogicId(param.getId());
        if (CollectionUtils.isEmpty(indexTemplatePhys)) {
            return Result.buildSucc();
        }

        for (IndexTemplatePhy indexTemplatePhy : indexTemplatePhys) {
            if (AriusObjUtils.isChanged(param.getExpression(), indexTemplatePhy.getExpression())) {
                Result<Void> result = templatePhyService.updateTemplateExpression(indexTemplatePhy, param.getExpression(), operator);
                if (result.failed()) {
                    return result;
                }
            }

            if (isValidShardNum(param.getShardNum())
                    && AriusObjUtils.isChanged(param.getShardNum(), indexTemplatePhy.getShard())) {
                Result<Void> result = templatePhyService.updateTemplateShardNum(indexTemplatePhy, param.getShardNum(), operator);
                if (result.failed()) {
                    return result;
                }
            }
        }

        return Result.buildSucc();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> switchMasterSlave(Integer logicId, Long expectMasterPhysicalId, String operator) {
        List<IndexTemplatePhy> indexTemplatePhys = templatePhyService.getTemplateByLogicId(logicId);
        if (CollectionUtils.isEmpty(indexTemplatePhys)) {
            return Result.buildNotExist("???????????????");
        }

        IndexTemplatePhy oldMaster = null;
        IndexTemplatePhy newMaster = null;

        for (IndexTemplatePhy indexTemplatePhy : indexTemplatePhys) {
            if (indexTemplatePhy.getRole().equals(MASTER.getCode())) {
                if (oldMaster != null) {
                    LOGGER.error("class=TemplatePhyServiceImpl||method=switchMasterSlave||errMsg=no master||logicId={}", logicId);
                }
                oldMaster = indexTemplatePhy;
            } else {
                if (expectMasterPhysicalId == null && newMaster == null) {
                    newMaster = indexTemplatePhy;
                }

                if (indexTemplatePhy.getId().equals(expectMasterPhysicalId)) {
                    newMaster = indexTemplatePhy;
                }
            }
        }

        if (newMaster == null) {
            return Result.buildNotExist("?????????????????????");
        }

        boolean succ = true;

        if (oldMaster == null) {
            LOGGER.error("class=TemplatePhyServiceImpl||method=switchMasterSlave||errMsg=no master||logicId={}", logicId);
        } else {
            succ = templatePhyService.updateTemplateRole(oldMaster,SLAVE,operator).success();
        }

        succ = succ && (templatePhyService.updateTemplateRole(newMaster,MASTER,operator).success());


        return Result.build(succ);
    }

    @Override
    public Result<Void> editTemplateRackWithoutCheck(Long physicalId, String tgtRack, String operator,
                                               int retryCount) throws ESOperateException {
        IndexTemplatePhysicalDTO updateParam = new IndexTemplatePhysicalDTO();
        updateParam.setId(physicalId);
        updateParam.setRack(tgtRack);
        return editTemplateWithoutCheck(updateParam, operator, retryCount);
    }

    @Override
    public Result<Void> upgradeTemplateVersion(Long physicalId, String operator, int retryCount) throws ESOperateException {
        IndexTemplatePhy indexTemplatePhy = templatePhyService.getTemplateById(physicalId);
        if (indexTemplatePhy == null) {
            return Result.buildNotExist("???????????????");
        }

        int version = indexTemplatePhy.getVersion() + 1;
        if (version > 9) {
            version = 0;
        }

        IndexTemplatePhysicalDTO updateParam = new IndexTemplatePhysicalDTO();
        updateParam.setId(indexTemplatePhy.getId());
        updateParam.setVersion(version);
        return editTemplateWithoutCheck(updateParam, operator, retryCount);
    }

    @Override
    public Result<Void> editTemplateWithoutCheck(IndexTemplatePhysicalDTO param, String operator,
                                           int retryCount) throws ESOperateException {
        IndexTemplatePhy oldIndexTemplatePhy = templatePhyService.getTemplateById(param.getId());

        if (param.getShard() != null && !oldIndexTemplatePhy.getShard().equals(param.getShard())) {
            indexPlanManager.initShardRoutingAndAdjustShard(param);
        }

        boolean succ = templatePhyService.update(param).success();
        String tips = "";
        if (succ) {
            if (AriusObjUtils.isChanged(param.getRack(), oldIndexTemplatePhy.getRack())
                    || AriusObjUtils.isChanged(param.getShard(), oldIndexTemplatePhy.getShard())) {
                esTemplateService.syncUpdateRackAndShard(oldIndexTemplatePhy.getCluster(), oldIndexTemplatePhy.getName(), param.getRack(),
                        param.getShard(), param.getShardRouting(), retryCount);
                if (AriusObjUtils.isChanged(param.getRack(), oldIndexTemplatePhy.getRack())) {
                    tips = "????????????rack??????!???????????????APP??????????????????????????????rack";
                }
            }

            SpringTool.publish(new PhysicalTemplateModifyEvent(this, ConvertUtil.obj2Obj(oldIndexTemplatePhy, IndexTemplatePhy.class),
                    templatePhyService.getTemplateById(oldIndexTemplatePhy.getId()),
                    templateLogicService.getLogicTemplateWithPhysicalsById(oldIndexTemplatePhy.getLogicId())));
        }

        return Result.buildWithTips(succ, tips);
    }

    @Override
    public Tuple</*????????????????????????*/Set<String>,/*????????????????????????*/Set<String>> getHotAndColdIndexByBeforeDay(IndexTemplatePhyWithLogic physicalWithLogic, int days) {
        try {
            IndexTemplateLogic logicTemplate = physicalWithLogic.getLogicTemplate();

            if (!physicalWithLogic.getExpression().endsWith("*")) {
                return new Tuple<>();
            }

            if (!TemplateUtils.isSaveByDay(logicTemplate.getDateFormat())
                    && !TemplateUtils.isSaveByMonth(logicTemplate.getDateFormat())) {
                return new Tuple<>();
            }

            List<String> indices = templatePhyService.getMatchIndexNames(physicalWithLogic.getId());
            if (CollectionUtils.isEmpty(indices)) {
                LOGGER.info("class=TemplatePhyManagerImpl||method=getIndexByBeforeDay||template={}||msg=no match indices", logicTemplate.getName());
                return new Tuple<>();
            }

            return getHotAndColdIndexSet(physicalWithLogic, days, logicTemplate, indices);
        } catch (Exception e) {
            LOGGER.warn("class=TemplatePhyManagerImpl||method=getIndexByBeforeDay||templateName={}||errMsg={}", physicalWithLogic.getName(),
                    e.getMessage(), e);
        }

        return new Tuple<>();
    }

    @Override
    public Set<String> getIndexByBeforeDay(IndexTemplatePhyWithLogic physicalWithLogic, int days) {
        try {
            IndexTemplateLogic logicTemplate = physicalWithLogic.getLogicTemplate();

            if (!physicalWithLogic.getExpression().endsWith("*")) {
                return Sets.newHashSet();
            }

            if (!TemplateUtils.isSaveByDay(logicTemplate.getDateFormat())
                    && !TemplateUtils.isSaveByMonth(logicTemplate.getDateFormat())) {
                return Sets.newHashSet();
            }

            List<String> indices = templatePhyService.getMatchIndexNames(physicalWithLogic.getId());
            if (CollectionUtils.isEmpty(indices)) {
                LOGGER.info("class=TemplatePhyManagerImpl||method=getIndexByBeforeDay||template={}||msg=no match indices", logicTemplate.getName());
                return Sets.newHashSet();
            }

            return getFinalIndexSet(physicalWithLogic, days, logicTemplate, indices);
        } catch (Exception e) {
            LOGGER.warn("class=TemplatePhyManagerImpl||method=getIndexByBeforeDay||templateName={}||errMsg={}", physicalWithLogic.getName(),
                    e.getMessage(), e);
        }

        return Sets.newHashSet();
    }

    @Override
    public List<ConsoleTemplatePhyVO> getConsoleTemplatePhyVOS(IndexTemplatePhysicalDTO param, Integer appId) {
        List<ConsoleTemplatePhyVO> consoleTemplatePhyVOS = ConvertUtil.list2List(templatePhyService.getByCondt(param),
            ConsoleTemplatePhyVO.class);

        buildConsoleTemplatePhyVO(consoleTemplatePhyVOS, appId);

        return consoleTemplatePhyVOS;
    }

    @Override
    public List<String> getTemplatePhyNames(Integer appId) {
        return getConsoleTemplatePhyVOS(null, appId).parallelStream().map(ConsoleTemplatePhyVO::getName)
            .collect(Collectors.toList());
    }

    @Override
    public List<String> getCanCopyTemplatePhyClusterPhyNames(Long templatePhyId) {
        List<String> canCopyClusterPhyNames = Lists.newArrayList();
        IndexTemplatePhy templatePhy = templatePhyService.getTemplateById(templatePhyId);
        if (null != templatePhy && null != templatePhy.getCluster()) {
            clusterPhyService.listAllClusters()
                    .stream()
                    .filter(clusterPhy -> !templatePhy.getCluster().equals(clusterPhy.getCluster()))
                    .forEach(clusterPhy -> canCopyClusterPhyNames.add(clusterPhy.getCluster()));
        }

        return canCopyClusterPhyNames;
    }

    @Override
    public Result<List<IndexTemplatePhysicalVO>> getTemplatePhies(Integer logicId) {
        if (!templateLogicService.exist(logicId)) {
            return Result.buildFail("??????Id?????????");
        }
        return Result.buildSucc(
            ConvertUtil.list2List(templatePhyService.getTemplateByLogicId(logicId), IndexTemplatePhysicalVO.class));
    }

    /**************************************** private method ****************************************************/
    private void initParamWhenAdd(IndexTemplatePhysicalDTO param) {
        IndexTemplateLogic logic = templateLogicService.getLogicTemplateById(param.getLogicId());

        if (param.getName() == null) {
            param.setName(logic.getName());
        }
        if (param.getExpression() == null) {
            param.setExpression(logic.getExpression());
        }
        if (param.getStatus() == null) {
            param.setStatus(TemplatePhysicalStatusEnum.NORMAL.getCode());
        }

        if (param.getRack() == null) {
            param.setRack("");
        }

        if (param.getVersion() == null) {
            param.setVersion(0);
        }

        if (param.getConfig() == null) {
            param.setConfig("");
        }

        IndexTemplatePhysicalConfig indexTemplatePhysicalConfig = new IndexTemplatePhysicalConfig();
        if (StringUtils.isNotBlank(param.getConfig())) {
            indexTemplatePhysicalConfig = JSON.parseObject(param.getConfig(), IndexTemplatePhysicalConfig.class);
        }

        indexTemplatePhysicalConfig.setGroupId(param.getGroupId());
        indexTemplatePhysicalConfig.setDefaultWriterFlags(param.getDefaultWriterFlags());

        param.setConfig(JSON.toJSONString(indexTemplatePhysicalConfig));
    }

    private Result<Void> checkUpgradeParam(TemplatePhysicalUpgradeDTO param) {
        if (AriusObjUtils.isNull(param)) {
            return Result.buildParamIllegal("???????????????????????????");
        }
        if (AriusObjUtils.isNull(param.getPhysicalId())) {
            return Result.buildParamIllegal(TEMPLATE_PHYSICAL_ID_IS_NULL);
        }
        if (AriusObjUtils.isNull(param.getVersion())) {
            return Result.buildParamIllegal("????????????????????????");
        }

        IndexTemplatePhy oldIndexTemplatePhy = templatePhyService.getTemplateById(param.getPhysicalId());
        if (oldIndexTemplatePhy == null) {
            return Result.buildNotExist(TEMPLATE_PHYSICAL_NOT_EXISTS);
        }
        if (Objects.equals(param.getVersion(), oldIndexTemplatePhy.getVersion())
                || (param.getVersion() > 0 && param.getVersion() < oldIndexTemplatePhy.getVersion())) {
            return Result.buildParamIllegal("????????????????????????");
        }
        if (param.getRack() != null && !clusterPhyService.isRacksExists(oldIndexTemplatePhy.getCluster(), param.getRack())) {
            return Result.buildParamIllegal("????????????rack??????");
        }
        if (param.getShard() != null) {
            if (param.getShard() >= 1) {
                return Result.buildSucc();
            }
            return Result.buildParamIllegal("shard????????????");
        }

        IndexTemplateLogic logic = templateLogicService.getLogicTemplateById(oldIndexTemplatePhy.getLogicId());
        if (TemplateUtils.isOnly1Index(logic.getExpression())) {
            return Result.buildParamIllegal("?????????????????????????????????????????????");
        }

        return Result.buildSucc();
    }

    private Result<Void> upgradeTemplateWithCheck(TemplatePhysicalUpgradeDTO param, String operator,
                                            int retryCount) throws ESOperateException {
        IndexTemplatePhy indexTemplatePhy = templatePhyService.getTemplateById(param.getPhysicalId());
        if (templateLabelService.hasDeleteDoc(indexTemplatePhy.getLogicId())) {
            return Result.buildParamIllegal("?????????????????????,???????????????");
        }

        IndexTemplateLogic logic = templateLogicService.getLogicTemplateById(indexTemplatePhy.getLogicId());
        LOGGER.info("class=TemplatePhyManagerImpl||method=upgradeTemplateWithCheck||name={}||rack={}||shard={}||version={}", logic.getName(), param.getRack(),
                param.getShard(), param.getVersion());

        IndexTemplatePhysicalDTO updateParam = new IndexTemplatePhysicalDTO();
        updateParam.setId(indexTemplatePhy.getId());
        updateParam.setRack(param.getRack());
        updateParam.setShard(param.getShard());
        updateParam.setVersion(param.getVersion());
        Result<Void> editResult = editTemplateWithoutCheck(updateParam, operator, retryCount);

        if (editResult.failed()) {
            return editResult;
        }

        templatePreCreateManager.asyncCreateTodayAndTomorrowIndexByPhysicalId(indexTemplatePhy.getId(), 3);

        return Result.buildSucc();
    }

    private Result<Void> checkCopyParam(TemplatePhysicalCopyDTO param) {
        if (AriusObjUtils.isNull(param)) {
            return Result.buildParamIllegal("??????????????????");
        }
        if (AriusObjUtils.isNull(param.getPhysicalId())) {
            return Result.buildParamIllegal(TEMPLATE_PHYSICAL_ID_IS_NULL);
        }
        if (AriusObjUtils.isNull(param.getCluster())) {
            return Result.buildParamIllegal("??????????????????");
        }
        if (AriusObjUtils.isNull(param.getShard())) {
            return Result.buildParamIllegal("shard??????");
        }

        IndexTemplatePhy oldIndexTemplatePhy = templatePhyService.getTemplateById(param.getPhysicalId());
        if (oldIndexTemplatePhy == null) {
            return Result.buildNotExist(TEMPLATE_PHYSICAL_NOT_EXISTS);
        }

        if (!clusterPhyService.isClusterExists(param.getCluster())) {
            return Result.buildNotExist("?????????????????????");
        }

        if (oldIndexTemplatePhy.getCluster().equals(param.getCluster())) {
            return Result.buildParamIllegal("????????????????????????????????????");
        }

        if (StringUtils.isNotEmpty(param.getRack())
                && !clusterPhyService.isRacksExists(param.getCluster(), param.getRack())) {
            return Result.buildNotExist("rack?????????");
        }

        if (param.getShard() < 1) {
            return Result.buildParamIllegal("shard??????");
        }

        return Result.buildSucc();
    }

    private boolean needOperateAhead(IndexTemplatePhyWithLogic physicalWithLogic) {
        Set<String> clusterSet = ariusConfigInfoService.stringSettingSplit2Set(ARIUS_COMMON_GROUP,
                "delete.expire.index.ahead.clusters", "", ",");
        return clusterSet.contains(physicalWithLogic.getCluster());
    }

    private Result<Void> checkMetaInner(IndexTemplatePhy templatePhysical,
                                  Map<Integer, IndexTemplateLogic> logicId2IndexTemplateLogicMap,
                                  Set<String> esClusters) {
        List<String> errMsgs = Lists.newArrayList();

        if (!esClusters.contains(templatePhysical.getCluster())) {
            errMsgs.add("????????????????????????" + templatePhysical.getName() + "(" + templatePhysical.getId() + ")");
        }

        if (!logicId2IndexTemplateLogicMap.containsKey(templatePhysical.getLogicId())) {
            errMsgs.add("????????????????????????" + templatePhysical.getName() + "(" + templatePhysical.getId() + ")");
        }

        TemplateConfig templateConfig = esTemplateService.syncGetTemplateConfig(templatePhysical.getCluster(),
                templatePhysical.getName());

        if (templateConfig == null) {
            errMsgs.add("es??????????????????" + templatePhysical.getName() + "(" + templatePhysical.getId() + ")");
        }

        if (CollectionUtils.isEmpty(errMsgs)) {
            return Result.buildSucc();
        }

        return Result.build( ResultType.ADMIN_META_ERROR.getCode(), String.join(",", errMsgs));

    }

    private int checkIndexCreateAndExpire(IndexTemplatePhy templatePhysical,
                                          Map<Integer, IndexTemplateLogic> logicId2IndexTemplateLogicMap) {
        int result = INDEX_OP_OK;
        if (templatePhysical.getCreateTime().before(AriusDateUtils.getZeroDate())) {
            Set<String> indices = Sets.newHashSet( templatePhyService.getMatchNoVersionIndexNames(templatePhysical.getId()));

            IndexTemplateLogic templateLogic = logicId2IndexTemplateLogicMap.get(templatePhysical.getLogicId());
            String tomorrowIndexName = IndexNameFactory.getNoVersion(templateLogic.getExpression(),
                    templateLogic.getDateFormat(), 1);
            String expireIndexName = IndexNameFactory.getNoVersion(templateLogic.getExpression(),
                    templateLogic.getDateFormat(), -1 * templateLogic.getExpireTime());

            if (!indices.contains(tomorrowIndexName)) {
                LOGGER.warn("class=TemplatePhyManagerImpl||method=checkIndexCreateAndExpire||cluster={}||template={}||msg=TOMORROW_INDEX_NOT_CREATE",
                        templatePhysical.getCluster(), templatePhysical.getName());
                result = result + TOMORROW_INDEX_NOT_CREATE;
            }

            if (TemplateUtils.isSaveByDay(templateLogic.getDateFormat()) && indices.contains(expireIndexName)) {
                LOGGER.warn("class=TemplatePhyManagerImpl||method=checkIndexCreateAndExpire||cluster={}||template={}||msg=EXPIRE_INDEX_NOT_DELETE",
                        templatePhysical.getCluster(), templatePhysical.getName());
                result = result + EXPIRE_INDEX_NOT_DELETE;
            }
        }
        return result;
    }

    private IndexTemplateLogicWithPhyTemplates buildIndexTemplateLogicWithPhysicalForNew(IndexTemplatePhysicalDTO param) {
        IndexTemplateLogicWithPhyTemplates logicWithPhysical = templateLogicService
                .getLogicTemplateWithPhysicalsById(param.getLogicId());
        if (CollectionUtils.isNotEmpty(param.getPhysicalInfos())) {
            List<IndexTemplatePhy> physicals = ConvertUtil.list2List(param.getPhysicalInfos(), IndexTemplatePhy.class);
            logicWithPhysical.setPhysicals(physicals);
        }
        return logicWithPhysical;
    }

    /**
     * ????????????????????????shard number.
     *
     * @param shardNum
     * @return
     */
    private boolean isValidShardNum(Integer shardNum) {
        return  (shardNum != null && shardNum > 0);
    }

    private void buildConsoleTemplatePhyVO(List<ConsoleTemplatePhyVO> params, Integer currentAppId) {
        
        Map<Integer, String> appId2AppNameMap = Maps.newHashMap();

        for (ConsoleTemplatePhyVO consoleTemplatePhyVO : params) {

            IndexTemplateLogic logicTemplate = templateLogicService.getLogicTemplateById(consoleTemplatePhyVO.getLogicId());
            if (AriusObjUtils.isNull(logicTemplate)) {
                LOGGER.error(
                        "class=TemplatePhyServiceImpl||method=buildConsoleTemplatePhyVO||errMsg=IndexTemplateLogic is empty||logicId={}",
                        consoleTemplatePhyVO.getLogicId());
                continue;
            }

            handleIndexTemplateLogic(currentAppId, appId2AppNameMap, consoleTemplatePhyVO, logicTemplate);

        }
    }

    private void handleIndexTemplateLogic(Integer currentAppId, Map<Integer, String> appId2AppNameMap, ConsoleTemplatePhyVO consoleTemplatePhyVO, IndexTemplateLogic logicTemplate) {
        //????????????????????????
        Integer appIdFromLogicTemplate = logicTemplate.getAppId();
        if (!AriusObjUtils.isNull(appIdFromLogicTemplate)) {
            consoleTemplatePhyVO.setAppId(appIdFromLogicTemplate);

            if (appId2AppNameMap.containsKey(appIdFromLogicTemplate)) {
                consoleTemplatePhyVO.setAppName(appId2AppNameMap.get(logicTemplate.getAppId()));
            } else {
                String appName = appService.getAppName(logicTemplate.getAppId());
                if (!AriusObjUtils.isNull(appName)) {
                    consoleTemplatePhyVO.setAppName(appName);
                    appId2AppNameMap.put(appIdFromLogicTemplate, appName);
                }
            }
        }

        //????????????????????????
        consoleTemplatePhyVO.setLogicName(logicTemplate.getName());

        //??????????????????, ??????????????????????????????
        consoleTemplatePhyVO.setMemo(logicTemplate.getDesc());

        //????????????
        if (AriusObjUtils.isNull(currentAppId)) {
            consoleTemplatePhyVO.setAuthType(AppTemplateAuthEnum.NO_PERMISSION.getCode());
            return;
        }
        if (currentAppId.equals(appIdFromLogicTemplate)) {
            consoleTemplatePhyVO.setAuthType(AppTemplateAuthEnum.OWN.getCode());
        } else {
            AppTemplateAuthEnum authEnum = appLogicTemplateAuthService.getAuthEnumByAppIdAndLogicId(currentAppId,
                    appIdFromLogicTemplate);
            consoleTemplatePhyVO.setAuthType(authEnum.getCode());
        }
    }

    private Result<Void> handleValidateTemplate(IndexTemplatePhysicalDTO param) {
        if (param.getCluster() != null && !clusterPhyService.isClusterExists(param.getCluster())) {
            return Result.buildParamIllegal("???????????????");
        }
        if (StringUtils.isNotEmpty(param.getRack())) {
            if (!clusterPhyService.isRacksExists(param.getCluster(), param.getRack())) {
                return Result.buildParamIllegal("??????rack?????????");
            }
           /* // ??????rack???????????????????????????region
            if (regionRackService.countRackMatchedRegion(param.getCluster(), param.getRack()) != 1) {
                return Result.buildParamIllegal("??????rack???????????????????????????");
            }*/
        }
        if (param.getShard() != null && param.getShard() < 1) {
            return Result.buildParamIllegal("shard????????????");
        }
        if (param.getRole() != null
                && TemplateDeployRoleEnum.UNKNOWN.equals(TemplateDeployRoleEnum.valueOf(param.getRole()))) {
            return Result.buildParamIllegal("??????????????????");
        }
        if (param.getLogicId() != null && !Objects.equals(param.getLogicId(), NOT_CHECK)) {
            IndexTemplateLogic logic = templateLogicService.getLogicTemplateById(param.getLogicId());
            if (logic == null) {
                return Result.buildNotExist("?????????????????????");
            }
        }
        return Result.buildSucc();
    }

    private Result<Void> handleValidateTemplateEdit(IndexTemplatePhysicalDTO param) {
        if (AriusObjUtils.isNull(param.getId())) {
            return Result.buildParamIllegal(TEMPLATE_PHYSICAL_ID_IS_NULL);
        }
        IndexTemplatePhy indexTemplatePhy = templatePhyService.getTemplateById(param.getId());
        if (indexTemplatePhy == null) {
            return Result.buildNotExist(TEMPLATE_PHYSICAL_NOT_EXISTS);
        }
        return Result.buildSucc();
    }

    private Result<Void> handleValidateTemplateAdd(IndexTemplatePhysicalDTO param) {
        if (AriusObjUtils.isNull(param.getLogicId())) {
            return Result.buildParamIllegal("????????????id??????");
        }
        if (AriusObjUtils.isNull(param.getCluster())) {
            return Result.buildParamIllegal("????????????");
        }

        if (AriusObjUtils.isNull(param.getShard())) {
            return Result.buildParamIllegal("shard??????");
        }
        if (AriusObjUtils.isNull(param.getRole())) {
            return Result.buildParamIllegal("??????????????????");
        }

        IndexTemplatePhy indexTemplatePhy = templatePhyService.getTemplateByClusterAndName(param.getCluster(), param.getName());
        if (indexTemplatePhy != null) {
            return Result.buildDuplicate("????????????????????????");
        }
        return Result.buildSucc();
    }

    private Set<String> getFinalIndexSet(IndexTemplatePhyWithLogic physicalWithLogic, int days, IndexTemplateLogic logicTemplate, List<String> indices) {
        Set<String> finalIndexSet = Sets.newHashSet();
        for (String indexName : indices) {
            if (StringUtils.isBlank(indexName)) {
                continue;
            }

            Date indexTime = IndexNameFactory.genIndexTimeByIndexName(
                    genIndexNameClear(indexName, logicTemplate.getExpression()), logicTemplate.getExpression(),
                    logicTemplate.getDateFormat());

            if (indexTime == null) {
                LOGGER.warn(
                        "class=TemplatePhyManagerImpl||method=getIndexByBeforeDay||template={}||indexName={}||msg=template parse index time fail",
                        logicTemplate.getName(), indexName);
                continue;
            }

            if (TemplateUtils.isSaveByMonth(logicTemplate.getDateFormat())) {
                // ???????????????????????????????????????????????? ??????????????????????????????????????????????????????
                indexTime = AriusDateUtils.getLastDayOfTheMonth(indexTime);
            }

            if (needOperateAhead(physicalWithLogic)) {
                int aheadSeconds = ariusConfigInfoService.intSetting(ARIUS_COMMON_GROUP,
                        "operate.index.ahead.seconds", 2 * 60 * 60);
                indexTime = AriusDateUtils.getBeforeSeconds(indexTime, aheadSeconds);
            }

            long timeIntervalDay = (System.currentTimeMillis() - indexTime.getTime()) / MILLIS_PER_DAY;
            if (timeIntervalDay < days) {
                LOGGER.info(
                        "class=TemplatePhyManagerImpl||method=getIndexByBeforeDay||template={}||indexName={}||timeIntervalDay={}||msg=index not match",
                        logicTemplate.getName(), indexName, timeIntervalDay);
                continue;
            }

            LOGGER.info("class=TemplatePhyManagerImpl||method=getIndexByBeforeDay||indexName={}||indexTime={}||timeIntervalDay={}", indexName,
                    indexTime, timeIntervalDay);

            finalIndexSet.add(indexName);
        }
        return finalIndexSet;
    }

    private void syncCreateIndexTemplateWithEs(IndexTemplatePhysicalDTO param) throws ESOperateException {
        IndexTemplateLogic logic = templateLogicService.getLogicTemplateById(param.getLogicId());
        MappingConfig mappings = null;
        Result result = AriusIndexMappingConfigUtils.parseMappingConfig(param.getMappings());
        if (result.success()) {
            mappings = (MappingConfig) result.getData();
        }
        Map<String, String> settingsMap = getSettingsMap(param.getCluster(), param.getRack(), param.getShard(), param.getShardRouting(), param.getSettings());
        boolean ret;
        if (null != mappings || null != param.getSettings()) {
            ret = esTemplateService.syncCreate(settingsMap, param.getCluster(), param.getName(), logic.getExpression(), mappings, 0);
        } else {
            ret = esTemplateService.syncCreate(param.getCluster(), param.getName(), logic.getExpression(), param.getRack(), param.getShard(), param.getShardRouting(), 0);
        }
        if (!ret) {
            throw new ESOperateException("failed to create template!");
        }
    }

    private Map<String, String> getSettingsMap(String cluster, String rack, Integer shard, Integer shardRouting, AriusIndexTemplateSetting settings) {
        Map<String, String> settingsMap = new HashMap<>();
        if (StringUtils.isNotBlank(rack)) {
            settingsMap.put(TEMPLATE_INDEX_INCLUDE_RACK, rack);
        }
        if (shard != null && shard > 0) {
            settingsMap.put(INDEX_SHARD_NUM, String.valueOf(shard));
        }
        /*if (shardRouting != null && shardRoutingEnableClusters.contains(cluster)) {
            settingsMap.put(INDEX_SHARD_ROUTING_NUM, String.valueOf(shardRouting));
        }*/
        settingsMap.put(SINGLE_TYPE, "true");

        //????????????????????????????????????????????????translog????????????
        if (null != settings) {
            settingsMap.putAll(settings.toJSON());
        }
        return settingsMap;
    }

    private Tuple</*????????????????????????*/Set<String>,/*????????????????????????*/Set<String>> getHotAndColdIndexSet(IndexTemplatePhyWithLogic physicalWithLogic,
                                                                                         int days, IndexTemplateLogic logicTemplate, List<String> indices) {
        Set<String> finalColdIndexSet = Sets.newHashSet();
        Set<String> finalHotIndexSet = Sets.newHashSet();
        for (String indexName : indices) {
            if (StringUtils.isBlank(indexName)) {
                continue;
            }

            Date indexTime = IndexNameFactory.genIndexTimeByIndexName(
                    genIndexNameClear(indexName, logicTemplate.getExpression()), logicTemplate.getExpression(),
                    logicTemplate.getDateFormat());

            if (indexTime == null) {
                LOGGER.warn(
                        "class=TemplatePhyManagerImpl||method=getIndexByBeforeDay||template={}||indexName={}||msg=template parse index time fail",
                        logicTemplate.getName(), indexName);
                continue;
            }

            if (TemplateUtils.isSaveByMonth(logicTemplate.getDateFormat())) {
                // ???????????????????????????????????????????????? ??????????????????????????????????????????????????????
                indexTime = AriusDateUtils.getLastDayOfTheMonth(indexTime);
            }

            long timeIntervalDay = (System.currentTimeMillis() - indexTime.getTime()) / MILLIS_PER_DAY;
            if (timeIntervalDay < days) {
                LOGGER.info(
                        "class=TemplatePhyManagerImpl||method=getIndexByBeforeDay||template={}||indexName={}||timeIntervalDay={}||msg=index not match",
                        logicTemplate.getName(), indexName, timeIntervalDay);
                finalHotIndexSet.add(indexName);
                continue;
            }

            LOGGER.info("class=TemplatePhyManagerImpl||method=getIndexByBeforeDay||indexName={}||indexTime={}||timeIntervalDay={}", indexName,
                    indexTime, timeIntervalDay);

            finalColdIndexSet.add(indexName);
        }
        return new Tuple<>(finalColdIndexSet, finalHotIndexSet);
    }
}
