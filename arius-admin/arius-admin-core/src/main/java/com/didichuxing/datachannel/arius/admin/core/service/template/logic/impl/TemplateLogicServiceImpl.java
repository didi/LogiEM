package com.didichuxing.datachannel.arius.admin.core.service.template.logic.impl;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.alibaba.fastjson.JSON;
import com.didichuxing.datachannel.arius.admin.client.bean.common.Result;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.template.ConsoleTemplateRateLimitDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.template.IndexTemplateConfigDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.template.IndexTemplateLogicDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.template.TemplateConditionDTO;
import com.didichuxing.datachannel.arius.admin.client.constant.operaterecord.ModuleEnum;
import com.didichuxing.datachannel.arius.admin.client.constant.operaterecord.OperationEnum;
import com.didichuxing.datachannel.arius.admin.client.constant.template.DataTypeEnum;
import com.didichuxing.datachannel.arius.admin.client.constant.template.TemplateDeployRoleEnum;
import com.didichuxing.datachannel.arius.admin.common.Tuple;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.app.AppClusterLogicAuth;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.app.AppTemplateAuth;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ClusterLogic;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ClusterLogicRackInfo;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.operaterecord.template.TemplateOperateRecord;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ClusterPhy;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.template.*;
import com.didichuxing.datachannel.arius.admin.common.bean.po.template.TemplateConfigPO;
import com.didichuxing.datachannel.arius.admin.common.bean.po.template.TemplateLogicPO;
import com.didichuxing.datachannel.arius.admin.common.component.SpringTool;
import com.didichuxing.datachannel.arius.admin.common.constant.*;
import com.didichuxing.datachannel.arius.admin.common.constant.arius.AriusUser;
import com.didichuxing.datachannel.arius.admin.common.constant.template.TemplateServiceEnum;
import com.didichuxing.datachannel.arius.admin.common.event.template.LogicTemplateModifyEvent;
import com.didichuxing.datachannel.arius.admin.common.exception.AdminOperateException;
import com.didichuxing.datachannel.arius.admin.common.exception.ESOperateException;
import com.didichuxing.datachannel.arius.admin.common.util.AriusObjUtils;
import com.didichuxing.datachannel.arius.admin.common.util.ConvertUtil;
import com.didichuxing.datachannel.arius.admin.common.util.ListUtils;
import com.didichuxing.datachannel.arius.admin.common.util.TemplateUtils;
import com.didichuxing.datachannel.arius.admin.core.component.ResponsibleConvertTool;
import com.didichuxing.datachannel.arius.admin.core.service.app.AppClusterLogicAuthService;
import com.didichuxing.datachannel.arius.admin.core.service.app.AppLogicTemplateAuthService;
import com.didichuxing.datachannel.arius.admin.core.service.app.AppService;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.logic.ClusterLogicService;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.physic.ClusterPhyService;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.region.RegionRackService;
import com.didichuxing.datachannel.arius.admin.core.service.common.AriusUserInfoService;
import com.didichuxing.datachannel.arius.admin.core.service.common.OperateRecordService;
import com.didichuxing.datachannel.arius.admin.core.service.es.ESIndexService;
import com.didichuxing.datachannel.arius.admin.core.service.template.logic.TemplateLogicService;
import com.didichuxing.datachannel.arius.admin.core.service.template.physic.TemplatePhyService;
import com.didichuxing.datachannel.arius.admin.persistence.mysql.template.IndexTemplateConfigDAO;
import com.didichuxing.datachannel.arius.admin.persistence.mysql.template.IndexTemplateLogicDAO;
import com.didichuxing.datachannel.arius.admin.persistence.mysql.template.IndexTemplateTypeDAO;
import com.didiglobal.logi.log.ILog;
import com.didiglobal.logi.log.LogFactory;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.didichuxing.datachannel.arius.admin.client.constant.operaterecord.ModuleEnum.TEMPLATE;
import static com.didichuxing.datachannel.arius.admin.client.constant.operaterecord.ModuleEnum.TEMPLATE_CONFIG;
import static com.didichuxing.datachannel.arius.admin.client.constant.operaterecord.OperationEnum.*;
import static com.didichuxing.datachannel.arius.admin.common.constant.AdminConstant.yesOrNo;
import static com.didichuxing.datachannel.arius.admin.common.constant.TemplateConstant.*;

@Service
public class TemplateLogicServiceImpl implements TemplateLogicService {

    private static final ILog           LOGGER = LogFactory.getLog(TemplateLogicServiceImpl.class);

    @Autowired
    private IndexTemplateLogicDAO       indexTemplateLogicDAO;

    @Autowired
    private IndexTemplateConfigDAO      indexTemplateConfigDAO;

    @Autowired
    private IndexTemplateTypeDAO        indexTemplateTypeDAO;

    @Autowired
    private OperateRecordService        operateRecordService;

    @Autowired
    private TemplatePhyService          templatePhyService;

    @Autowired
    private AppService                  appService;

    @Autowired
    private AriusUserInfoService        ariusUserInfoService;

    @Autowired
    private ResponsibleConvertTool      responsibleConvertTool;

    @Autowired
    private ESIndexService              esIndexService;

    @Autowired
    private AppLogicTemplateAuthService logicTemplateAuthService;

    @Autowired
    private AppClusterLogicAuthService  logicClusterAuthService;

    @Autowired
    private ClusterLogicService         clusterLogicService;

    @Autowired
    private ClusterPhyService clusterPhyService;

    @Autowired
    private RegionRackService           regionRackService;

    private Cache<String, List<IndexTemplateLogic>> templateListCache = CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.MINUTES).maximumSize(10).build();

    /**
     * ????????????
     *
     * @param param ??????
     * @return ??????????????????
     */
    @Override
    public List<IndexTemplateLogic> getLogicTemplates(IndexTemplateLogicDTO param) {
        return responsibleConvertTool.list2List(
            indexTemplateLogicDAO.listByCondition(responsibleConvertTool.obj2Obj(param, TemplateLogicPO.class)),
            IndexTemplateLogic.class);
    }

    /**
     * ????????????????????????????????????
     *
     * @param param ??????????????????
     * @return
     */
    @Override
    public List<IndexTemplateLogic> fuzzyLogicTemplatesByCondition(IndexTemplateLogicDTO param) {
        return responsibleConvertTool.list2List(
            indexTemplateLogicDAO.likeByCondition(responsibleConvertTool.obj2Obj(param, TemplateLogicPO.class)),
            IndexTemplateLogic.class);
    }

    @Override
    public List<IndexTemplateLogic> pagingGetLogicTemplatesByCondition(TemplateConditionDTO param) {
        String sortTerm = null == param.getSortTerm() ? SortConstant.ID : param.getSortTerm();
        String sortType = param.getOrderByDesc() ? SortConstant.DESC : SortConstant.ASC;
        List<TemplateLogicPO> templateLogicPOS = Lists.newArrayList();
        try {
            templateLogicPOS = indexTemplateLogicDAO.pagingByCondition(param.getName(),
                    param.getDataType(), param.getHasDCDR(), (param.getPage() - 1) * param.getSize(), param.getSize(), sortTerm, sortType);
        } catch (Exception e) {
            LOGGER.error("class=TemplateLogicServiceImpl||method=pagingGetLogicTemplatesByCondition||err={}",
                e.getMessage(), e);
        }

        return responsibleConvertTool.list2List(templateLogicPOS, IndexTemplateLogic.class);
    }

    @Override
    public Long fuzzyLogicTemplatesHitByCondition(IndexTemplateLogicDTO param) {
        return indexTemplateLogicDAO
            .getTotalHitByCondition(responsibleConvertTool.obj2Obj(param, TemplateLogicPO.class));
    }

    /**
     * ??????????????????
     *
     * @param templateName ????????????
     * @return list
     */
    @Override
    public List<IndexTemplateLogic> getLogicTemplateByName(String templateName) {
        return responsibleConvertTool.list2List(indexTemplateLogicDAO.listByName(templateName),
            IndexTemplateLogic.class);
    }

    /**
     * ???????????????????????????
     *
     * @param logicTemplateId ??????id
     * @return ????????????  ???????????????null
     */
    @Override
    public IndexTemplateLogic getLogicTemplateById(Integer logicTemplateId) {
        return responsibleConvertTool.obj2Obj(indexTemplateLogicDAO.getById(logicTemplateId), IndexTemplateLogic.class);
    }

    /**
     * ??????????????????
     *
     * @param logicTemplateId  ??????id
     * @param operator ?????????
     * @return result
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> delTemplate(Integer logicTemplateId, String operator) throws AdminOperateException {
        TemplateLogicPO oldPO = indexTemplateLogicDAO.getById(logicTemplateId);
        if (oldPO == null) {
            return Result.buildNotExist(TEMPLATE_NOT_EXIST);
        }

        boolean succeed = (1 == indexTemplateLogicDAO.delete(logicTemplateId));

        if (succeed) {
            Result<Void> deleteTemplateAuthResult = logicTemplateAuthService.deleteTemplateAuthByTemplateId(oldPO.getId(), AriusUser.SYSTEM.getDesc());
            if (deleteTemplateAuthResult.failed()) {
                throw new AdminOperateException("??????????????????");
            } else {
                LOGGER.info("class=TemplateLogicServiceImpl||method=delTemplate||logicId={}||msg=deleteTemplateAuthByTemplateId succ", logicTemplateId);
            }

            Result<Void> result = templatePhyService.delTemplateByLogicId(logicTemplateId, operator);
            if (result.failed()) {
                throw new AdminOperateException("??????????????????");
            } else {
                operateRecordService.save(TEMPLATE, DELETE, logicTemplateId, String.format("??????%d??????", logicTemplateId), operator);
                LOGGER.info("class=TemplateLogicServiceImpl||method=delTemplate||logicId={}||msg=delTemplateByLogicId succ", logicTemplateId);
            }

        } else {
            throw new AdminOperateException("??????????????????");
        }
        return Result.buildSucc();
    }

    /**
     * ??????????????????????????????
     *
     * @param param     ??????
     * @param operation ??????
     * @return result
     */
    @Override
    public Result<Void> validateTemplate(IndexTemplateLogicDTO param, OperationEnum operation) {
        if (param == null) {
            return Result.buildParamIllegal("??????????????????");
        }

        String dateFormatFinal = null;
        String expressionFinal = null;
        String nameFinal = null;
        String dateFieldFinal = null;

        if (ADD.equals(operation)) {
            Result<Void> result = validateAdd(param);
            if (result.failed()) {return result;}

            dateFormatFinal = StringUtils.isBlank(param.getDateFormat()) ? null : param.getDateFormat();
            expressionFinal = param.getExpression();
            nameFinal = param.getName();
            dateFieldFinal = param.getDateField();
        } else if (EDIT.equals(operation)) {
            if (AriusObjUtils.isNull(param.getId())) {
                return Result.buildParamIllegal("??????id??????");
            }

            TemplateLogicPO oldPO = indexTemplateLogicDAO.getById(param.getId());
            if (oldPO == null) {
                return Result.buildNotExist(TEMPLATE_NOT_EXIST);
            }
            dateFormatFinal = getDateFormat(param, oldPO);
            expressionFinal = getExpression(param, oldPO);
            dateFieldFinal = getDateField(param, oldPO);
            nameFinal = oldPO.getName();
        }


        List<IndexTemplateLogic> indexTemplateLogicList = getLogicTemplateByName(param.getName());
        Result<Void> result = validateIndexTemplateLogicStep1(param, indexTemplateLogicList);
        if (result.failed()) {return result;}

        result = validateIndexTemplateLogicStep2(param, dateFormatFinal, expressionFinal, nameFinal, dateFieldFinal);
        if (result.failed()) {return result;}

        return Result.buildSucc();
    }

    /**
     * ??????????????????
     *
     * @param param    ??????
     * @param operator ?????????
     * @return result
     */
    @Override
    @Transactional
    public Result<Void> editTemplate(IndexTemplateLogicDTO param, String operator) throws AdminOperateException {
        Result<Void> checkResult = validateTemplate(param, EDIT);
        if (checkResult.failed()) {
            LOGGER.warn("class=TemplateLogicServiceImpl||method=editTemplate||msg={}", checkResult.getMessage());
            return checkResult;
        }

        return editTemplateWithoutCheck(param, operator);
    }

    @Override
    public Result<Void> addTemplateWithoutCheck(IndexTemplateLogicDTO param) throws AdminOperateException {
        TemplateLogicPO templatePO = responsibleConvertTool.obj2Obj(param, TemplateLogicPO.class);
        boolean succ;
        try {
            succ = (1 == indexTemplateLogicDAO.insert(templatePO));
        } catch (DuplicateKeyException e) {
            LOGGER.warn("class=TemplateLogicServiceImpl||method=addTemplateWithoutCheck||errMsg={}", e.getMessage());
            throw new AdminOperateException(String.format("?????????????????????%s????????????????????????????????????", templatePO.getName()));
        }

        param.setId(templatePO.getId());
        return Result.build(succ);
    }

    /**
     * ????????????????????????
     *
     * @param logicTemplateId ??????id
     * @return ????????????  ???????????????null
     */
    @Override
    public IndexTemplateConfig getTemplateConfig(Integer logicTemplateId) {
        return responsibleConvertTool.obj2Obj(indexTemplateConfigDAO.getByLogicId(logicTemplateId),
            IndexTemplateConfig.class);
    }

    /**
     * ??????????????????
     *
     * @param configDTO ????????????
     * @param operator  ?????????
     * @return result
     */
    @Override
    public Result<Void> updateTemplateConfig(IndexTemplateConfigDTO configDTO, String operator) {
        Result<Void> checkResult = checkConfigParam(configDTO);
        if (checkResult.failed()) {
            LOGGER.warn("class=TemplateLogicServiceImpl||method=updateTemplateConfig||msg={}",
                checkResult.getMessage());
            return checkResult;
        }

        TemplateLogicPO oldPO = indexTemplateLogicDAO.getById(configDTO.getLogicId());
        if (oldPO == null) {
            return Result.buildNotExist(TEMPLATE_NOT_EXIST);
        }

        TemplateConfigPO oldConfigPO = indexTemplateConfigDAO.getByLogicId(configDTO.getLogicId());

        boolean succ = 1 == indexTemplateConfigDAO
            .update(responsibleConvertTool.obj2Obj(configDTO, TemplateConfigPO.class));
        if (succ) {
            //?????????????????????record??? ???????????????????????????????????????????????????????????????????????????
            //operateRecordService.save(TEMPLATE_CONFIG, EDIT, configDTO.getLogicId(),AriusObjUtils.findChangedWithClear(oldConfigPO, configDTO), operator);
        }

        return Result.build(succ);
    }

    @Override
    public Result<Void> insertTemplateConfig(IndexTemplateConfig indexTemplateConfig) {
        return Result.build(1 == indexTemplateConfigDAO.insert(ConvertUtil.obj2Obj(indexTemplateConfig,TemplateConfigPO.class)));
    }

    /**
     * ??????????????????
     *
     * @param logicTemplateId  logicId
     * @param factor   factor
     * @param operator ?????????
     */
    @Override
    public void upsertTemplateShardFactor(Integer logicTemplateId, Double factor, String operator) {
        IndexTemplateConfig templateConfig = getTemplateConfig(logicTemplateId);
        if (templateConfig == null) {
            TemplateConfigPO configPO = getDefaultTemplateConfig(logicTemplateId);
            configPO.setAdjustRackShardFactor(factor);
            Result.build(1 == indexTemplateConfigDAO.insert(configPO));
        } else {
            IndexTemplateConfigDTO param = new IndexTemplateConfigDTO();
            param.setLogicId(logicTemplateId);
            param.setAdjustRackShardFactor(factor);
            updateTemplateConfig(param, operator);
        }
    }

    /**
     * ??????????????????
     *
     * @param logicTemplateId  logicId
     * @param factor   factor
     * @param operator ?????????
     */
    @Override
    public void updateTemplateShardFactorIfGreater(Integer logicTemplateId, Double factor, String operator) {
        IndexTemplateConfig templateConfig = getTemplateConfig(logicTemplateId);
        if (templateConfig == null) {
            TemplateConfigPO configPO = getDefaultTemplateConfig(logicTemplateId);
            configPO.setAdjustRackShardFactor(factor);
            Result.build(1 == indexTemplateConfigDAO.insert(configPO));
            return;
        } else if (templateConfig.getAdjustRackShardFactor() < factor) {
            IndexTemplateConfigDTO param = new IndexTemplateConfigDTO();
            param.setLogicId(logicTemplateId);
            param.setAdjustRackShardFactor(factor);
            updateTemplateConfig(param, operator);
            return;
        }
        Result.buildSucc();
    }

    /**
     * ????????????????????????
     *
     * @param logicTemplateId ??????id
     * @return true/false
     */
    @Override
    public boolean exist(Integer logicTemplateId) {
        return indexTemplateLogicDAO.getById(logicTemplateId) != null;
    }

    /**
     * ???????????????????????????
     *
     * @return map
     */
    @Override
    public Map<Integer, IndexTemplateLogic> getAllLogicTemplatesMap() {
        return getAllLogicTemplates().stream()
            .collect(Collectors.toMap(IndexTemplateLogic::getId, indexTemplateLogic -> indexTemplateLogic));
    }

    @Override
    public List<IndexTemplateLogic> getLogicTemplatesByIds(List<Integer> logicTemplateIds) {
        if (CollectionUtils.isEmpty(logicTemplateIds)) {
            return new ArrayList<>();
        }

        return responsibleConvertTool.list2List(indexTemplateLogicDAO.listByIds(logicTemplateIds),
            IndexTemplateLogic.class);
    }

    @Override
    public Map<Integer, IndexTemplateLogic> getLogicTemplatesMapByIds(List<Integer> logicTemplateIds) {
        return getLogicTemplatesByIds(logicTemplateIds).stream()
            .collect(Collectors.toMap(IndexTemplateLogic::getId, indexTemplateLogic -> indexTemplateLogic));
    }

    /**
     * ??????APP ID????????????
     *
     * @param appId APP ID
     * @return list
     */
    @Override
    public List<IndexTemplateLogic> getAppLogicTemplatesByAppId(Integer appId) {
        return responsibleConvertTool.list2List(indexTemplateLogicDAO.listByAppId(appId), IndexTemplateLogic.class);
    }

    /**
     * ?????????????????????????????????????????????
     * @param logicClusterId ????????????ID
     * @return
     */
    @Override
    public List<IndexTemplateLogic> getLogicClusterTemplates(Long logicClusterId) {
        List<IndexTemplateLogic> logicTemplates = Lists.newArrayList();

        if (logicClusterId != null) {
            List<IndexTemplateLogicWithCluster> indexTemplateLogicWithClusters = getLogicTemplateWithClustersByClusterId(
                logicClusterId);
            logicTemplates = ConvertUtil.list2List(indexTemplateLogicWithClusters, IndexTemplateLogic.class);
        }

        return logicTemplates;
    }

    /**
     * ?????????????????????????????????
     *
     * @param appId appId
     */
    @Override
    public Result<List<Tuple<String, String>>> getLogicTemplatesByAppId(Integer appId) {
        List<AppTemplateAuth> appTemplateAuths = logicTemplateAuthService.getTemplateAuthsByAppId(appId);
        if (CollectionUtils.isEmpty(appTemplateAuths)) {
            return Result.buildSucc();
        }

        List<Tuple<String, String>> indicesClusterTupleList = new ArrayList<>();

        appTemplateAuths.parallelStream().forEach(appTemplateAuth -> {
            IndexTemplateLogicWithPhyTemplates logicWithPhysical = getLogicTemplateWithPhysicalsById(
                appTemplateAuth.getTemplateId());

            if (null != logicWithPhysical && logicWithPhysical.hasPhysicals()) {
                IndexTemplatePhy indexTemplatePhysical = logicWithPhysical.getPhysicals().get(0);

                String cluster = indexTemplatePhysical.getCluster();
                Set<String> indices = esIndexService.syncGetIndexNameByExpression(cluster,
                    indexTemplatePhysical.getExpression());
                if (CollectionUtils.isNotEmpty(indices) && StringUtils.isNotBlank(cluster)) {
                    indices.forEach(i -> indicesClusterTupleList.add(new Tuple<>(i, cluster)));
                }
            }
        });

        LOGGER.info("class=TemplateLogicServiceImpl||method=getAllTemplateIndicesByAppid||appId={}||indicesList={}",
            appId, JSON.toJSONString(indicesClusterTupleList));

        return Result.buildSucc(indicesClusterTupleList);
    }

    /**
     * ????????????
     *
     * @param logicId        ??????id
     * @param tgtAppId       appid
     * @param tgtResponsible ?????????
     * @param operator       ?????????
     * @return Result
     */
    @Override
    @Transactional
    public Result<Void> turnOverLogicTemplate(Integer logicId, Integer tgtAppId, String tgtResponsible,
                                        String operator) throws AdminOperateException {

        IndexTemplateLogic templateLogic = getLogicTemplateById(logicId);
        if (templateLogic == null) {
            return Result.buildParamIllegal(TEMPLATE_NOT_EXIST);
        }

        IndexTemplateLogicDTO logicDTO = new IndexTemplateLogicDTO();
        logicDTO.setId(logicId);
        logicDTO.setAppId(tgtAppId);
        logicDTO.setResponsible(tgtResponsible);

        return editTemplate(logicDTO, operator);

    }

    /**
     * ??????????????????????????????????????????
     *
     * @return ????????????????????????????????????????????????
     */
    @Override
    public Map<Integer, Integer> getAllLogicTemplatesPhysicalCount() {
        return templatePhyService.getAllLogicTemplatesPhysicalCount();
    }

    /**
     * ?????????????????????
     *
     * @return
     */
    @Override
    public List<IndexTemplateLogic> getAllLogicTemplates() {
        return responsibleConvertTool.list2List(indexTemplateLogicDAO.listAll(), IndexTemplateLogic.class);
    }

    @Override
    public List<IndexTemplateLogic> getAllLogicTemplatesWithCache() {
        try {
            return templateListCache.get("getAllLogicTemplates", this::getAllLogicTemplates);
        } catch (Exception e) {
            return getAllLogicTemplates();
        }
    }

    /**
     * ??????type
     *
     * @param logicId ??????id
     * @return list
     */
    @Override
    public List<IndexTemplateType> getLogicTemplateTypes(Integer logicId) {
        return ConvertUtil.list2List(indexTemplateTypeDAO.listByIndexTemplateId(logicId), IndexTemplateType.class);
    }

    /**
     * ?????????????????????
     *
     * @param responsibleId ?????????id
     * @return list
     */
    @Override
    public List<IndexTemplateLogic> getTemplateByResponsibleId(Long responsibleId) {
        return responsibleConvertTool.list2List(indexTemplateLogicDAO.likeByResponsible(String.valueOf(responsibleId)),
            IndexTemplateLogic.class);
    }

    /**
     * ????????????????????????????????????name
     * ????????????????????????name
     *
     * @param param    ??????
     * @param operator ?????????
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> editTemplateName(IndexTemplateLogicDTO param, String operator) throws AdminOperateException {
        if (AriusObjUtils.isNull(param.getId())) {
            return Result.buildParamIllegal("??????ID??????");
        }

        if (AriusObjUtils.isNull(param.getName())) {
            return Result.buildParamIllegal("??????????????????");
        }

        TemplateLogicPO logicParam = new TemplateLogicPO();
        logicParam.setId(param.getId());
        logicParam.setName(param.getName());

        boolean succ = 1 == indexTemplateLogicDAO.update(logicParam);
        if (!succ) {
            return Result.buildFail("??????????????????????????????");
        }

        List<IndexTemplatePhy> physicals = templatePhyService.getTemplateByLogicId(param.getId());
        if (CollectionUtils.isNotEmpty(physicals)) {
            for (IndexTemplatePhy physical : physicals) {
                physical.setName(param.getName());
                Result<Void> result = templatePhyService.updateTemplateName(physical, operator);
                if (result.failed()) {
                    throw new AdminOperateException("??????????????????[" + physical.getId() + "]?????????" + result.getMessage());
                }
            }
        }

        return Result.buildSucc();
    }

    @Override
    public Result<Void> editTemplateInfoTODB(IndexTemplateLogicDTO param) {
        boolean succ = false;
        try {
            succ = 1 == indexTemplateLogicDAO.update(ConvertUtil.obj2Obj(param, TemplateLogicPO.class));
        } catch (Exception e) {
            LOGGER.error("class=TemplateLogicServiceImpl||method=editTemplateInfoTODB||||msg={}", e.getMessage(), e);
        }
        return succ ? Result.buildSucc() : Result.buildFail();
    }

    @Override
    public List<IndexTemplateLogic> getTemplatesByHasAuthCluster(Integer appId) {
        if (appId == null) {
            return new ArrayList<>();
        }

        // ????????????????????????id
        Set<Long> hasAuthLogicClusterIds = logicClusterAuthService.getAllLogicClusterAuths(appId).stream()
            .map(AppClusterLogicAuth::getLogicClusterId).collect(Collectors.toSet());

        // ????????????????????????
        return getLogicTemplateWithClusterAndMasterTemplateByClusters(hasAuthLogicClusterIds).stream()
                .map(IndexTemplateLogic.class::cast)
                .collect(Collectors.toList());
    }

    @Override
    public List<IndexTemplateLogic> getHasAuthTemplatesInLogicCluster(Integer appId, Long logicClusterId) {
        if (appId == null || logicClusterId == null) {
            return new ArrayList<>();
        }

        // ??????????????????????????????????????????
        List<IndexTemplateLogicWithClusterAndMasterTemplate> templatesInLogicCluster = getLogicTemplateWithClusterAndMasterTemplateByCluster(
            logicClusterId);

        // ??????app?????????????????????
        List<AppTemplateAuth> appTemplateAuths = logicTemplateAuthService.getTemplateAuthsByAppId(appId);
        Set<Integer> hasAuthTemplateIds = appTemplateAuths.stream().map(AppTemplateAuth::getTemplateId)
            .collect(Collectors.toSet());

        // ??????app????????????????????????
        return templatesInLogicCluster.stream()
            .filter(templateInLogicCluster -> hasAuthTemplateIds.contains(templateInLogicCluster.getId()))
            .map(IndexTemplateLogic.class::cast )
            .collect(Collectors.toList());
    }

    @Override
    public List<IndexTemplateLogicWithClusterAndMasterTemplate> getLogicTemplatesWithClusterAndMasterTemplate() {

        List<IndexTemplateLogicWithCluster> logicClusters = getAllLogicTemplateWithClusters();
        if (CollectionUtils.isEmpty(logicClusters)) {
            return new ArrayList<>();
        }

        return logicClusters.parallelStream().filter(Objects::nonNull).map(this::convert).collect(Collectors.toList());
    }

    @Override
    public IndexTemplateLogicWithClusterAndMasterTemplate getLogicTemplateWithClusterAndMasterTemplate(Integer logicTemplateId) {
        return convert(getLogicTemplateWithCluster(logicTemplateId));
    }

    @Override
    public List<IndexTemplateLogicWithClusterAndMasterTemplate> getLogicTemplatesWithClusterAndMasterTemplate(Set<Integer> logicTemplateIds) {

        List<IndexTemplateLogicWithCluster> logicClusters = getLogicTemplateWithClusters(logicTemplateIds);
        if (CollectionUtils.isEmpty(logicClusters)) {
            return new ArrayList<>();
        }

        return logicClusters.stream().filter(Objects::nonNull).map(this::convert).collect(Collectors.toList());
    }

    @Override
    public Map<Integer, IndexTemplateLogicWithClusterAndMasterTemplate> getLogicTemplatesWithClusterAndMasterTemplateMap(Set<Integer> logicTemplateIds) {
        return getLogicTemplatesWithClusterAndMasterTemplate(logicTemplateIds).stream()
            .collect(Collectors.toMap(IndexTemplateLogicWithClusterAndMasterTemplate::getId, template -> template));
    }

    @Override
    public List<IndexTemplateLogicWithClusterAndMasterTemplate> getLogicTemplateWithClusterAndMasterTemplateByClusters(Set<Long> logicClusterIds) {

        if (CollectionUtils.isEmpty(logicClusterIds)) {
            return new ArrayList<>();
        }

        // ??????????????????????????????
        return getLogicTemplatesWithClusterAndMasterTemplate().parallelStream()
            .filter(logicTemplateWithLogicCluster -> logicTemplateWithLogicCluster != null
                                                     && logicTemplateWithLogicCluster.getLogicCluster() != null
                                                     && logicClusterIds.contains(
                                                         logicTemplateWithLogicCluster.getLogicCluster().getId()))
            .collect(Collectors.toList());
    }

    @Override
    public List<IndexTemplateLogicWithClusterAndMasterTemplate> getLogicTemplateWithClusterAndMasterTemplateByCluster(Long logicClusterId) {
        if (logicClusterId == null) {
            return new ArrayList<>();
        }

        // ??????????????????????????????
        return getLogicTemplatesWithClusterAndMasterTemplate().parallelStream()
            .filter(logicTemplateWithLogicCluster -> logicTemplateWithLogicCluster != null
                                                     && logicTemplateWithLogicCluster.getLogicCluster() != null
                                                     && logicClusterId.equals(
                                                         logicTemplateWithLogicCluster.getLogicCluster().getId()))
            .collect(Collectors.toList());
    }

    /**
     * ????????????????????????????????????????????????
     * @param logicTemplateId ????????????ID
     * @return
     */
    @Override
    public IndexTemplateLogicWithCluster getLogicTemplateWithCluster(Integer logicTemplateId) {
        IndexTemplateLogicWithPhyTemplates physicalTemplates = getLogicTemplateWithPhysicalsById(logicTemplateId);

        if (physicalTemplates == null) {
            return null;
        }
        return convert2WithCluster(Arrays.asList(physicalTemplates)).stream().filter(Objects::nonNull).findFirst()
            .orElse(null);
    }

    @Override
    public List<IndexTemplateLogicWithCluster> getLogicTemplateWithClusters(Set<Integer> logicTemplateIds) {
        List<IndexTemplateLogicWithPhyTemplates> physicalTemplates = getLogicTemplateWithPhysicalsByIds(
            logicTemplateIds);

        return convert2WithCluster(physicalTemplates).stream().filter(Objects::nonNull).collect(Collectors.toList());
    }

    /**
     * ????????????????????????
     *
     * @return ?????????????????????????????????????????????
     */
    @Override
    public List<IndexTemplateLogicWithCluster> getAllLogicTemplateWithClusters() {
        List<IndexTemplateLogicWithPhyTemplates> logicTemplatesCombinePhysicals = getAllLogicTemplateWithPhysicals();

        return convert2WithCluster(logicTemplatesCombinePhysicals).stream().filter(Objects::nonNull)
            .collect(Collectors.toList());

    }

    /**
     * ????????????????????????
     *
     * @param logicClusterId ????????????ID
     * @return List<IndexTemplateLogicClusterMeta> ??????????????????
     */
    @Override
    public List<IndexTemplateLogicWithCluster> getLogicTemplateWithClustersByClusterId(Long logicClusterId) {
        List<IndexTemplateLogicWithCluster> allClusterMetas = getAllLogicTemplateWithClusters();

        List<IndexTemplateLogicWithCluster> currentClusterMetas = new ArrayList<>();
        for (IndexTemplateLogicWithCluster clusterMeta : allClusterMetas) {
            if (isLogicClusterIdWithin(clusterMeta.getLogicClusters(), logicClusterId)) {
                currentClusterMetas.add(clusterMeta);
            }
        }

        return currentClusterMetas;
    }

    /**
     * ?????????????????????????????????????????????????????????
     * @return
     */
    @Override
    public List<IndexTemplateLogicWithPhyTemplates> getAllLogicTemplateWithPhysicals() {
        return batchConvertLogicTemplateCombinePhysical(indexTemplateLogicDAO.listAll());
    }

    @Override
    public List<IndexTemplateLogicWithPhyTemplates> getLogicTemplateWithPhysicalsByIds(Set<Integer> logicTemplateIds) {
        if (CollectionUtils.isEmpty(logicTemplateIds)) {
            return new ArrayList<>();
        }
        return batchConvertLogicTemplateCombinePhysical(
            indexTemplateLogicDAO.listByIds(new ArrayList<>(logicTemplateIds)));
    }

    /**
     * ?????????????????????????????????????????????????????????????????????????????????
     * @param logicTemplateId ????????????ID
     * @return
     */
    @Override
    public IndexTemplateLogicWithPhyTemplates getLogicTemplateWithPhysicalsById(Integer logicTemplateId) {
        TemplateLogicPO templateLogic = indexTemplateLogicDAO.getById(logicTemplateId);
        if (templateLogic == null) {
            return null;
        }

        List<IndexTemplateLogicWithPhyTemplates> physicalTemplates = batchConvertLogicTemplateCombinePhysical(
            Arrays.asList(templateLogic));
        return physicalTemplates.stream().findFirst().orElse(null);
    }

    /**
     * ????????????????????????????????????
     * @param dataCenter ????????????
     * @return list
     */
    @Override
    public List<IndexTemplateLogicWithPhyTemplates> getTemplateWithPhysicalByDataCenter(String dataCenter) {
        return batchConvertLogicTemplateCombinePhysical(indexTemplateLogicDAO.listByDataCenter(dataCenter));
    }


    /**
     * ???????????????
     * @param logicId ????????????
     * @param blockRead  ????????????
     * @param operator  ?????????
     * @return
     * @throws AdminOperateException
     */
    @Override
    public Result updateBlockReadState(Integer logicId, Boolean blockRead, String operator) {
        if (null == logicId || null == blockRead) {
            return Result.buildFail("logicId or blockRead is null");
        }
        int row = indexTemplateLogicDAO.updateBlockReadState(logicId, blockRead);
        if (1 != row) {
            return Result.buildFail("????????????????????????");
        }
        operateRecordService.save(TEMPLATE, EDIT, logicId, JSON.toJSONString(new TemplateOperateRecord(TemplateOperateRecordEnum.READ.getCode(),
                "??????????????????:" + (blockRead ? "?????????" : "?????????"))), operator);
        return Result.buildSucc(row);
    }

    /**
     * ???????????????
     * @param logicId ????????????
     * @param blockWrite ????????????
     * @param operator ????????????
     * @return
     */
    @Override
    public Result updateBlockWriteState(Integer logicId, Boolean blockWrite, String operator) {
        if (null == logicId || null == blockWrite) {
            return Result.buildFail("logicId or blockWrite is null");
        }
        int row = indexTemplateLogicDAO.updateBlockWriteState(logicId, blockWrite);
        if (1 != row) {
            return Result.buildFail("????????????????????????");
        }
        operateRecordService.save(TEMPLATE, EDIT, logicId, JSON.toJSONString(new TemplateOperateRecord(TemplateOperateRecordEnum.WRITE.getCode(),
                "??????????????????:" + (blockWrite ? "?????????" : "?????????"))), operator);
        return Result.buildSucc(row);
    }

    @Override
    public Result updateTemplateWriteRateLimit(ConsoleTemplateRateLimitDTO dto) throws ESOperateException {
        List<IndexTemplatePhy> phyList = templatePhyService.getTemplateByLogicId(dto.getLogicId());
        for (IndexTemplatePhy indexTemplatePhy : phyList) {
            ClusterPhy clusterPhy = clusterPhyService.getClusterByName(indexTemplatePhy.getCluster());
            List<String> templateServices = ListUtils.string2StrList(clusterPhy.getTemplateSrvs());
            if (!templateServices.contains(TemplateServiceEnum.TEMPLATE_LIMIT_W.getCode().toString())) {
                return Result.buildFail("????????????????????????????????????????????????");
            }
        }
        TemplateLogicPO oldPO = indexTemplateLogicDAO.getById(dto.getLogicId());
        TemplateLogicPO editTemplate = responsibleConvertTool.obj2Obj(dto, TemplateLogicPO.class);
        editTemplate.setId(dto.getLogicId());
        editTemplate.setWriteRateLimit(dto.getAdjustRateLimit());
        int update = indexTemplateLogicDAO.update(editTemplate);
        if (update > 0) {
            IndexTemplateLogicDTO param = responsibleConvertTool.obj2Obj(editTemplate, IndexTemplateLogicDTO.class);
            param.setId(dto.getLogicId());
            // ??????????????????????????????
            Result editPhyResult = templatePhyService.editTemplateFromLogic(param, AriusUser.SYSTEM.getDesc());
            if (editPhyResult.failed()) {
                return Result.buildFail("???????????????????????????????????????");
            }
            operateRecordService.save(TEMPLATE, EDIT, dto.getLogicId(), String.format("??????????????????????????????%s->%s", dto.getCurRateLimit(), dto.getAdjustRateLimit()), dto.getSubmitor());
            SpringTool.publish(new LogicTemplateModifyEvent(this, responsibleConvertTool.obj2Obj(oldPO, IndexTemplateLogic.class), getLogicTemplateById(oldPO.getId())));
            return Result.buildSucc();
        }
        return Result.buildFail();
    }

    @Override
    public Result<Void> preCheckTemplateName(String name) {
        if (name == null) {
            return Result.buildParamIllegal("??????????????????");
        }
        List<String> pos = indexTemplateLogicDAO.listAllNames();
        for (String po : pos) {
            if (name.equals(po)) {
                return Result.buildDuplicate("????????????????????????");
            }
            if (name.startsWith(po) || po.startsWith(name)) {
                return Result.buildParamIllegal("????????????" + name + "??????" + po + "?????????,??????????????????,?????????????????????????????????");
            }
        }
        return Result.buildSuccWithMsg("????????????????????????");
    }


    /**************************************** private method ****************************************************/
    /**
     * ???????????????????????????????????????????????????????????????
     * @param logicTemplates ??????????????????
     * @return
     */
    private List<IndexTemplateLogicWithPhyTemplates> batchConvertLogicTemplateCombinePhysical(List<TemplateLogicPO> logicTemplates) {

        if (CollectionUtils.isEmpty(logicTemplates)) {
            return Lists.newArrayList();
        }

        // ??????????????????1?????????????????????
        Multimap<Integer, IndexTemplatePhy> logicId2PhysicalTemplatesMapping = ConvertUtil.list2MulMap(
            templatePhyService.getTemplateByLogicIds(
                logicTemplates.stream().map(TemplateLogicPO::getId).collect(Collectors.toList())),
            IndexTemplatePhy::getLogicId);

        List<IndexTemplateLogicWithPhyTemplates> indexTemplateCombinePhysicalTemplates = Lists.newArrayListWithCapacity(logicTemplates.size());

        for (TemplateLogicPO logicTemplate : logicTemplates) {
            IndexTemplateLogicWithPhyTemplates logicWithPhysical = responsibleConvertTool.obj2Obj(logicTemplate,
                IndexTemplateLogicWithPhyTemplates.class);
            logicWithPhysical
                .setPhysicals(Lists.newArrayList(logicId2PhysicalTemplatesMapping.get(logicTemplate.getId())));

            indexTemplateCombinePhysicalTemplates.add(logicWithPhysical);
        }

        return indexTemplateCombinePhysicalTemplates;
    }

    /**************************************** private method ****************************************************/
    /**
     *
     * @param esLogicClusters ??????????????????
     * @param logicClusterId ??????ID
     * @return
     */
    private boolean isLogicClusterIdWithin(List<ClusterLogic> esLogicClusters, Long logicClusterId) {
        if (CollectionUtils.isNotEmpty(esLogicClusters) && logicClusterId != null) {
            for (ClusterLogic logic : esLogicClusters) {
                if (logic.getId().equals(logicClusterId)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * ????????????????????????????????????????????????????????????
     *
     * @param logicClusterId2ClusterMeta     ????????????ID??????????????????????????????
     * @param clusterRackMeta2LogicClusterId ??????Rack??????????????????????????????ID?????????
     * @param templateLogicWithPhysical      ???????????????????????????????????????
     * @return
     */
    private IndexTemplateLogicWithCluster buildLogicTemplateWithLogicClusterMeta(Map<Long, ClusterLogic> logicClusterId2ClusterMeta,
                                                                                 Multimap<String, Long> clusterRackMeta2LogicClusterId,
                                                                                 IndexTemplateLogicWithPhyTemplates templateLogicWithPhysical) {
        List<IndexTemplatePhy> physicals = templateLogicWithPhysical.getPhysicals();

        Map<Long, ClusterLogic> relatedLogicClusters = Maps.newHashMap();
        if (CollectionUtils.isNotEmpty(physicals)) {
            for (IndexTemplatePhy physical : physicals) {

                List<ClusterLogic> logicClusters = getPhysicalTemplateLogicCluster(physical, clusterRackMeta2LogicClusterId,
                        logicClusterId2ClusterMeta);

                if (!CollectionUtils.isEmpty(logicClusters)) {
                    logicClusters.forEach(logicCluster -> relatedLogicClusters.put(logicCluster.getId(), logicCluster));
                }
            }
        }

        IndexTemplateLogicWithCluster templateLogicWithCluster = ConvertUtil.obj2Obj(templateLogicWithPhysical,
            IndexTemplateLogicWithCluster.class);

        templateLogicWithCluster.setLogicClusters(Lists.newArrayList(relatedLogicClusters.values()));

        return templateLogicWithCluster;
    }

    /**
     * ?????????????????????????????????????????????
     *
     * @param physicalTemplate                        ????????????
     * @param clusterRack2LogicClusterId      ??????Rack
     * @param logicClusterId2LogicClusterMeta ????????????ID??????????????????????????????
     * @return
     */
    private List<ClusterLogic> getPhysicalTemplateLogicCluster(IndexTemplatePhy physicalTemplate,
                                                               Multimap<String, Long> clusterRack2LogicClusterId,
                                                               Map<Long, ClusterLogic> logicClusterId2LogicClusterMeta) {

        if (physicalTemplate != null) {
            Collection<Long> logicClusterIds = clusterRack2LogicClusterId
                    .get(fetchRackKey(physicalTemplate.getCluster(), fetchFirstRack(physicalTemplate.getRack())));

            if (CollectionUtils.isEmpty(logicClusterIds)) {
                logicClusterIds = clusterRack2LogicClusterId
                        .get(fetchRackKey(physicalTemplate.getCluster(), AdminConstant.RACK_COMMA));
            }

            List<ClusterLogic> clusterLogics = Lists.newArrayList();
            logicClusterIds.forEach(logicClusterId -> clusterLogics.add(logicClusterId2LogicClusterMeta.get(logicClusterId)));

            return clusterLogics;
        }

        return null;
    }

    /**
     * ???????????????Rack
     * @param racks ????????????Rack??????
     * @return
     */
    private String fetchFirstRack(String racks) {
        if (StringUtils.isNotBlank(racks)) {
            return racks.split(",")[0];
        }

        return StringUtils.EMPTY;
    }

    /**
     * ??????????????????Rack???????????????ID??????
     *
     * @return
     */
    private Multimap<String, Long> fetchClusterRacks2LogicClusterIdMappings() {
        Multimap<String, Long> logicClusterIdMappings = ArrayListMultimap.create();
        for (ClusterLogicRackInfo param : regionRackService.listAllLogicClusterRacks()) {
            List<Long> logicClusterIds = ListUtils.string2LongList(param.getLogicClusterIds());
            logicClusterIds.forEach(logicClusterId -> logicClusterIdMappings.put(fetchRackKey(param.getPhyClusterName(), param.getRack()), logicClusterId));
        }
        return logicClusterIdMappings;
    }

    /**
     * ??????Map Key
     *
     * @param cluster ??????????????????
     * @param rack    Rack??????
     * @return
     */
    private String fetchRackKey(String cluster, String rack) {
        return cluster + "&" + rack;
    }

    /**
     * ????????????????????????
     *
     * @return
     */
    private Map<Long, ClusterLogic> getLogicClusters() {
        return ConvertUtil.list2Map(clusterLogicService.listAllClusterLogics(), ClusterLogic::getId);
    }

    /**
     * ???LogicTemplateCombinePhysicalTemplates?????????LogicTemplateCombineLogicCluster
     * @param logicTemplatesCombinePhysicals
     * @return
     */
    private List<IndexTemplateLogicWithCluster> convert2WithCluster(List<IndexTemplateLogicWithPhyTemplates> logicTemplatesCombinePhysicals) {
        if (CollectionUtils.isEmpty(logicTemplatesCombinePhysicals)) {
            return new ArrayList<>();
        }

        List<IndexTemplateLogicWithCluster> indexTemplateLogicWithClusters = new CopyOnWriteArrayList<>();
        // ?????????????????????key-????????????id???value-????????????
        final Map<Long, ClusterLogic> logicClusterMap = getLogicClusters();
        // ??????rack???????????????id?????????
        final Multimap<String, Long> clusterIdMappingsMap = fetchClusterRacks2LogicClusterIdMappings();

        logicTemplatesCombinePhysicals.forEach(templateLogicWithPhysical -> {
            try {
                indexTemplateLogicWithClusters.add(buildLogicTemplateWithLogicClusterMeta(logicClusterMap,
                    clusterIdMappingsMap, templateLogicWithPhysical));
            } catch (Exception e) {
                LOGGER.error("class=LogicTemplateCombineClusterServiceImpl||method=acquireLogicTemplateCombineClusters"
                             + "||physical={}",
                    templateLogicWithPhysical, e);
            }
        });
        return indexTemplateLogicWithClusters;
    }

    private Result<Void> checkConfigParam(IndexTemplateConfigDTO configDTO) {
        if (configDTO == null) {
            return Result.buildParamIllegal("??????????????????");
        }
        if (configDTO.getLogicId() == null) {
            return Result.buildParamIllegal("??????ID??????");
        }
        if (configDTO.getIsSourceSeparated() != null && !yesOrNo(configDTO.getIsSourceSeparated())) {
            return Result.buildParamIllegal("??????????????????????????????");
        }
        if (configDTO.getDynamicLimitEnable() != null && !yesOrNo(configDTO.getDynamicLimitEnable())) {
            return Result.buildParamIllegal("??????????????????????????????");
        }
        if (configDTO.getMappingImproveEnable() != null && !yesOrNo(configDTO.getMappingImproveEnable())) {
            return Result.buildParamIllegal("mapping??????????????????");
        }

        return Result.buildSucc();
    }

    private TemplateConfigPO getDefaultTemplateConfig(Integer logicId) {
        TemplateConfigPO configPO = new TemplateConfigPO();
        configPO.setLogicId(logicId);
        configPO.setAdjustRackTpsFactor(1.0);
        configPO.setAdjustRackShardFactor(1.0);
        configPO.setDynamicLimitEnable(AdminConstant.YES);
        configPO.setMappingImproveEnable(AdminConstant.NO);
        configPO.setIsSourceSeparated(AdminConstant.NO);
        configPO.setDisableSourceFlags(false);
        configPO.setPreCreateFlags(true);
        configPO.setShardNum(1);
        return configPO;
    }

    /**
     * ??????????????????   ???????????????
     *
     * @param param    ??????
     * @param operator ?????????
     * @return result
     */
    private Result<Void> editTemplateWithoutCheck(IndexTemplateLogicDTO param, String operator) throws AdminOperateException {

        if (param.getDateFormat() != null) {
            param.setDateFormat(param.getDateFormat().replace("Y", "y"));
        }

        TemplateLogicPO oldPO = indexTemplateLogicDAO.getById(param.getId());
        TemplateLogicPO editTemplate = responsibleConvertTool.obj2Obj(param, TemplateLogicPO.class);
        if ("".equals(editTemplate.getResponsible())) {
            editTemplate.setResponsible(null);
        }

        boolean succeed = (1 == indexTemplateLogicDAO.update(editTemplate));

        if (succeed) {
            param.setId(editTemplate.getId());
            // ??????????????????????????????
            Result<Void> editPhyResult = templatePhyService.editTemplateFromLogic(param, operator);
            if (editPhyResult.failed()) {
                throw new AdminOperateException("????????????????????????");
            }

            // ????????????????????????
            operateRecordService.save(ModuleEnum.TEMPLATE, OperationEnum.EDIT, param.getId(), JSON.toJSONString(
                    new TemplateOperateRecord(TemplateOperateRecordEnum.TRANSFER.getCode(), AriusObjUtils.findChangedWithClear(oldPO, editTemplate))), operator);

            SpringTool.publish(new LogicTemplateModifyEvent(this, responsibleConvertTool.obj2Obj(oldPO, IndexTemplateLogic.class)
                    , getLogicTemplateById(oldPO.getId())));
        }

        return Result.build(succeed);
    }

    /**
     * ??????????????????
     * @param combineLogicCluster ????????????
     * @return
     */
    private IndexTemplateLogicWithClusterAndMasterTemplate convert(IndexTemplateLogicWithCluster combineLogicCluster) {
        if (combineLogicCluster == null) {
            return null;
        }

        IndexTemplateLogicWithClusterAndMasterTemplate combineLogicClusterAndMasterTemplate = ConvertUtil
            .obj2Obj(combineLogicCluster, IndexTemplateLogicWithClusterAndMasterTemplate.class);

        combineLogicClusterAndMasterTemplate.setLogicCluster(fetchOne(combineLogicCluster.getLogicClusters()));

        combineLogicClusterAndMasterTemplate.setMasterTemplate(
            fetchMasterTemplate(templatePhyService.getValidTemplatesByLogicId(combineLogicCluster.getId())));

        return combineLogicClusterAndMasterTemplate;
    }

    /**
     * ??????Master????????????
     * @param physicalTemplates ??????????????????
     * @return
     */
    private IndexTemplatePhy fetchMasterTemplate(List<IndexTemplatePhy> physicalTemplates) {
        if (CollectionUtils.isEmpty(physicalTemplates)) {
            return null;
        }

        for (IndexTemplatePhy physicalTemplate : physicalTemplates) {
            if (TemplateDeployRoleEnum.MASTER.getCode().equals(physicalTemplate.getRole())) {
                return physicalTemplate;
            }
        }

        return null;
    }

    /**
     * ?????????????????????
     * @param logicClusters ??????????????????
     * @return
     */
    private ClusterLogic fetchOne(List<ClusterLogic> logicClusters) {
        if (CollectionUtils.isNotEmpty(logicClusters)) {
            return logicClusters.get(0);
        }

        return null;
    }

    private Result<Void> validateIndexTemplateLogicStep2(IndexTemplateLogicDTO param, String dateFormatFinal, String expressionFinal, String nameFinal, String dateFieldFinal) {
        List<String> responsibles = ListUtils.string2StrList(param.getResponsible());
        for (String responsible : responsibles) {
            if (AriusObjUtils.isNull(ariusUserInfoService.getByDomainAccount(responsible))) {
                return Result.buildParamIllegal(String.format("?????????%s??????", responsible));
            }
        }
        if (expressionFinal != null && expressionFinal.endsWith("*") && AriusObjUtils.isNull(dateFormatFinal)) {
            return Result.buildParamIllegal("?????????*??????,??????????????????");
        }
        if (dateFormatFinal != null && param.getExpireTime() != null && TemplateUtils.isSaveByDay(dateFormatFinal)
                && param.getExpireTime() > TEMPLATE_SAVE_BY_DAY_EXPIRE_MAX) {
            return Result.buildParamIllegal("???????????????????????????????????????????????????180???");
        }
        if (dateFormatFinal != null && param.getExpireTime() != null && TemplateUtils.isSaveByMonth(dateFormatFinal)
                && (param.getExpireTime() < TEMPLATE_SAVE_BY_MONTH_EXPIRE_MIN && param.getExpireTime() > 0)) {
            return Result.buildParamIllegal("???????????????????????????????????????????????????30???");
        }
        if (param.getExpireTime() != null && param.getExpireTime() > 0 &&
                param.getExpireTime() < AdminConstant.PLATFORM_EXPIRE_TIME_MIN) {
            return Result.buildParamIllegal(String.format("????????????????????????????????????????????????%d???", AdminConstant.PLATFORM_EXPIRE_TIME_MIN));
        }
        if (nameFinal != null) {
            boolean expressionMatch = nameFinal.equals(expressionFinal) || (nameFinal + "*").equals(expressionFinal);
            if (!expressionMatch) {
                return Result.buildParamIllegal("?????????????????????????????????");
            }
        }
        if (StringUtils.isNotBlank(dateFormatFinal) && StringUtils.isBlank(dateFieldFinal)) {
            return Result.buildParamIllegal("???????????????????????????????????????");
        }
        return Result.buildSucc();
    }

    private Result<Void> validateIndexTemplateLogicStep1(IndexTemplateLogicDTO param, List<IndexTemplateLogic> indexTemplateLogicList) {
        // ??????????????????
        if (param.getName() != null) {
            Result<Void> result = validateIndexName(param, indexTemplateLogicList);
            if (result.failed()){return result;}
        }
        if (param.getExpression() != null) {
            Result<Void> result = validateExpression(param, indexTemplateLogicList);
            if (result.failed()){return result;}
        }
        if (param.getDataCenter() != null
                && !DataCenterEnum.validate(param.getDataCenter())) {
            return Result.buildParamIllegal("??????????????????");
        }
        if (param.getAppId() != null
                && !appService.isAppExists(param.getAppId())) {
            return Result.buildParamIllegal("?????????????????????");
        }
        if (param.getDataType() != null
                && DataTypeEnum.UNKNOWN.equals(DataTypeEnum.valueOf(param.getDataType()))) {
            return Result.buildParamIllegal("??????????????????");
        }
        if (param.getShardNum() != null && param.getShardNum() <= 0) {
            return Result.buildNotExist("shard??????????????????0");
        }
        return Result.buildSucc();
    }

    private String getDateField(IndexTemplateLogicDTO param, TemplateLogicPO oldPO) {
        String dateFieldFinal;
        if (param.getDateField() != null) {
            dateFieldFinal = param.getDateField();
        } else {
            dateFieldFinal = oldPO.getDateField();
        }
        return dateFieldFinal;
    }

    private String getExpression(IndexTemplateLogicDTO param, TemplateLogicPO oldPO) {
        String expressionFinal;
        if (param.getExpression() != null) {
            expressionFinal = param.getExpression();
        } else {
            expressionFinal = oldPO.getExpression();
        }
        return expressionFinal;
    }

    private String getDateFormat(IndexTemplateLogicDTO param, TemplateLogicPO oldPO) {
        String dateFormatFinal;
        if (param.getDateFormat() != null) {
            dateFormatFinal = param.getDateFormat();
        } else {
            dateFormatFinal = oldPO.getDateFormat();
        }
        return dateFormatFinal;
    }

    private Result<Void> validateAdd(IndexTemplateLogicDTO param) {
        if (AriusObjUtils.isNull(param.getName())) {
            return Result.buildParamIllegal("????????????");
        }
        if (AriusObjUtils.isNull(param.getAppId())) {
            return Result.buildParamIllegal("??????????????????");
        }
        if (AriusObjUtils.isNull(param.getDataType())) {
            return Result.buildParamIllegal("??????????????????");
        }
        if (AriusObjUtils.isNull(param.getExpireTime())) {
            return Result.buildParamIllegal("??????????????????");
        }
        if (AriusObjUtils.isNull(param.getResponsible())) {
            return Result.buildParamIllegal("???????????????");
        }
        if (AriusObjUtils.isNull(param.getExpression())) {
            return Result.buildParamIllegal("???????????????");
        }
        if (AriusObjUtils.isNull(param.getDataCenter())) {
            return Result.buildParamIllegal("??????????????????");
        }
        if (AriusObjUtils.isNull(param.getQuota())) {
            return Result.buildParamIllegal("Quota??????");
        }
        if (AriusObjUtils.isNull(param.getWriteRateLimit())) {
            param.setWriteRateLimit(-1);
        }
        if(LevelEnum.valueOfCode(param.getLevel()).equals(LevelEnum.UNKNOWN)) {
            return Result.buildParamIllegal("??????????????????????????????????????????");
        }
        if(levelOfTemplateLower(param)) {
            return Result.buildParamIllegal("??????????????????????????????????????????????????????????????????");
        }

        return Result.buildSucc();
    }

    private boolean levelOfTemplateLower(IndexTemplateLogicDTO param) {
        ClusterLogic clusterLogic = clusterLogicService.getClusterLogicById(param.getResourceId());
        return !AriusObjUtils.isNull(clusterLogic) && clusterLogic.getLevel() < param.getLevel();
    }

    private Result<Void> validateExpression(IndexTemplateLogicDTO param, List<IndexTemplateLogic> indexTemplateLogicList) {
        String expression = param.getExpression();
        for (IndexTemplateLogic templateLogic : indexTemplateLogicList) {
            if (StringUtils.isBlank(templateLogic.getExpression()) || StringUtils.isBlank(expression)) {
                continue;
            }

            if (templateLogic.getId().equals(param.getId())) {
                continue;
            }

            if (templateLogic.getExpression().equals(expression)) {
                return Result.buildParamIllegal("???????????????????????????");
            }

            String otherExpressionPre = templateLogic.getExpression();
            if (otherExpressionPre.endsWith("*")) {
                otherExpressionPre = otherExpressionPre.substring(0, otherExpressionPre.length() - 1);
            }

            String expressionPre = expression;
            if (expressionPre.contains("*")) {
                expressionPre = expressionPre.substring(0, expressionPre.length() - 1);
            }

            if (expressionPre.startsWith(otherExpressionPre) || otherExpressionPre.startsWith(expressionPre)) {
                return Result.buildParamIllegal("???????????????" + templateLogic.getName() + "?????????,??????????????????,?????????????????????????????????");
            }
        }
        return Result.buildSucc();
    }

    private Result<Void> validateIndexName(IndexTemplateLogicDTO param, List<IndexTemplateLogic> indexTemplateLogicList) {
        String name = param.getName();
        if (name.length() < TEMPLATE_NAME_SIZE_MIN || name.length() > TEMPLATE_NAME_SIZE_MAX) {
            return Result.buildParamIllegal(String.format("??????????????????, %s-%s",TEMPLATE_NAME_SIZE_MIN,TEMPLATE_NAME_SIZE_MAX));
        }

        for (Character c : name.toCharArray()) {
            if (!TEMPLATE_NAME_CHAR_SET.contains(c)) {
                return Result.buildParamIllegal("????????????????????????, ????????????????????????????????????-???_???.");
            }
        }

        for (IndexTemplateLogic templateLogic : indexTemplateLogicList) {
            if (templateLogic.getName().equals(name) && !templateLogic.getId().equals(param.getId())) {
                return Result.buildDuplicate("????????????????????????");
            }
        }
        return Result.buildSucc();
    }
}
