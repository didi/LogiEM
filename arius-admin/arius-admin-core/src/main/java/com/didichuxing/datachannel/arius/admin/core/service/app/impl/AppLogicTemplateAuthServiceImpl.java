package com.didichuxing.datachannel.arius.admin.core.service.app.impl;

import com.alibaba.fastjson.JSON;
import com.didichuxing.datachannel.arius.admin.client.bean.common.Result;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.app.AppTemplateAuthDTO;
import com.didichuxing.datachannel.arius.admin.client.constant.app.AppClusterLogicAuthEnum;
import com.didichuxing.datachannel.arius.admin.client.constant.app.AppTemplateAuthEnum;
import com.didichuxing.datachannel.arius.admin.client.constant.operaterecord.ModuleEnum;
import com.didichuxing.datachannel.arius.admin.client.constant.operaterecord.OperationEnum;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.app.App;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.app.AppTemplateAuth;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ClusterLogic;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.template.IndexTemplateLogic;
import com.didichuxing.datachannel.arius.admin.common.bean.po.app.AppTemplateAuthPO;
import com.didichuxing.datachannel.arius.admin.common.component.SpringTool;
import com.didichuxing.datachannel.arius.admin.common.event.auth.AppTemplateAuthAddEvent;
import com.didichuxing.datachannel.arius.admin.common.event.auth.AppTemplateAuthDeleteEvent;
import com.didichuxing.datachannel.arius.admin.common.event.auth.AppTemplateAuthEditEvent;
import com.didichuxing.datachannel.arius.admin.common.util.AriusObjUtils;
import com.didichuxing.datachannel.arius.admin.common.util.ConvertUtil;
import com.didichuxing.datachannel.arius.admin.common.util.EnvUtil;
import com.didichuxing.datachannel.arius.admin.core.component.ResponsibleConvertTool;
import com.didichuxing.datachannel.arius.admin.core.service.app.AppClusterLogicAuthService;
import com.didichuxing.datachannel.arius.admin.core.service.app.AppLogicTemplateAuthService;
import com.didichuxing.datachannel.arius.admin.core.service.app.AppService;
import com.didichuxing.datachannel.arius.admin.core.service.common.AriusUserInfoService;
import com.didichuxing.datachannel.arius.admin.core.service.common.OperateRecordService;
import com.didichuxing.datachannel.arius.admin.core.service.template.logic.TemplateLogicService;
import com.didichuxing.datachannel.arius.admin.persistence.mysql.app.AppTemplateAuthDAO;
import com.didiglobal.logi.log.ILog;
import com.didiglobal.logi.log.LogFactory;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author d06679
 * @date 2019/4/16
 */
@Service
public class AppLogicTemplateAuthServiceImpl implements AppLogicTemplateAuthService {

    private static final ILog          LOGGER = LogFactory.getLog(AppLogicTemplateAuthServiceImpl.class);

    @Autowired
    private AppTemplateAuthDAO         templateAuthDAO;

    @Autowired
    private AppService                 appService;

    @Autowired
    private TemplateLogicService       templateLogicService;

    @Autowired
    private AriusUserInfoService       ariusUserInfoService;

    @Autowired
    private ResponsibleConvertTool     responsibleConvertTool;

    @Autowired
    private AppClusterLogicAuthService logicClusterAuthService;

    @Autowired
    private OperateRecordService       operateRecordService;

    /**
     * Check??????????????????????????????????????????
     * @param shouldDeleteFlags ????????????
     * @return
     */
    @Override
    public boolean deleteExcessTemplateAuthsIfNeed(boolean shouldDeleteFlags) {
        Map<Integer, IndexTemplateLogic> logicTemplateId2LogicTemplateMappings = ConvertUtil
            .list2Map(templateLogicService.getAllLogicTemplates(), IndexTemplateLogic::getId);

        Multimap<Integer, AppTemplateAuthPO> appId2TemplateAuthsMappings = ConvertUtil
            .list2MulMap(templateAuthDAO.listWithRwAuths(), AppTemplateAuthPO::getAppId);

        Map<Long, AppTemplateAuthPO> needDeleteTemplateAuths = Maps.newHashMap();

        for (Integer appId : appId2TemplateAuthsMappings.keySet()) {
            List<AppTemplateAuthPO> appTemplateAuths = Lists.newArrayList(appId2TemplateAuthsMappings.get(appId));

            Multimap<Integer, AppTemplateAuthPO> currentAppLogicId2TemplateAuthsMappings = ConvertUtil
                .list2MulMap(appTemplateAuths, AppTemplateAuthPO::getTemplateId);

            for (Integer logicTemplateId : currentAppLogicId2TemplateAuthsMappings.keySet()) {
                List<AppTemplateAuthPO> currentLogicTemplateAuths = Lists
                    .newArrayList(currentAppLogicId2TemplateAuthsMappings.get(logicTemplateId));

                if (!logicTemplateId2LogicTemplateMappings.containsKey(logicTemplateId)) {
                    needDeleteTemplateAuths
                        .putAll(ConvertUtil.list2Map(currentLogicTemplateAuths, AppTemplateAuthPO::getId));

                    LOGGER.info("class=AppLogicTemplateAuthServiceImpl||method=checkMeta||msg=templateDeleted||appId=={}||logicId={}", appId, logicTemplateId);

                } else if (appId.equals(logicTemplateId2LogicTemplateMappings.get(logicTemplateId).getAppId())) {
                    needDeleteTemplateAuths
                        .putAll(ConvertUtil.list2Map(currentLogicTemplateAuths, AppTemplateAuthPO::getId));

                    LOGGER.info("class=AppLogicTemplateAuthServiceImpl||method=checkMeta||msg=appOwnTemplate||appId=={}||logicId={}", appId, logicTemplateId);
                } else {

                    if(currentLogicTemplateAuths.size() == 1) {
                        continue;
                    }

                    currentLogicTemplateAuths.sort(Comparator.comparing(AppTemplateAuthPO::getType));

                    needDeleteTemplateAuths.putAll(
                        ConvertUtil.list2Map(currentLogicTemplateAuths.subList(1, currentLogicTemplateAuths.size()),
                            AppTemplateAuthPO::getId));

                    LOGGER.info("class=AppLogicTemplateAuthServiceImpl||method=checkMeta||msg=appHasMultiTemplateAuth||appId=={}||logicId={}", appId,
                        logicTemplateId);
                }
            }
        }

        doDeleteOperationForNeed(needDeleteTemplateAuths.values(), shouldDeleteFlags);

        return true;
    }

    @Override
    public Result<Void> ensureSetLogicTemplateAuth(Integer appId, Integer logicTemplateId, AppTemplateAuthEnum auth,
                                             String responsible, String operator) {
        // ????????????
        if (appId == null) {
            return Result.buildParamIllegal("?????????appId");
        }

        if (logicTemplateId == null) {
            return Result.buildParamIllegal("?????????????????????ID");
        }

        if (StringUtils.isBlank(operator)) {
            return Result.buildParamIllegal("??????????????????");
        }

        // ???????????????????????????????????????????????
        AppTemplateAuthPO oldAuthPO = templateAuthDAO.getByAppIdAndTemplateId(appId, String.valueOf(logicTemplateId));

        if (oldAuthPO == null) {
            // ???????????????
            // NO_PERMISSIONS????????????
            if (auth == null || auth == AppTemplateAuthEnum.NO_PERMISSION) {
                return Result.buildSucc();
            }

            // ??????
            return addTemplateAuth(new AppTemplateAuthDTO(null, appId, logicTemplateId, auth.getCode(), responsible),
                operator);
        } else {
            // ???????????????
            // ??????????????????
            if (auth == AppTemplateAuthEnum.NO_PERMISSION) {
                return deleteTemplateAuth(oldAuthPO.getId(), operator);
            }

            // ????????????????????????
            AppTemplateAuthDTO newAuthDTO = new AppTemplateAuthDTO(oldAuthPO.getId(), null, null,
                auth == null ? null : auth.getCode(), StringUtils.isBlank(responsible) ? null : responsible);
            return updateTemplateAuth(newAuthDTO, operator);
        }
    }

    /**
     * ??????APP??????????????????????????????????????????????????????APP???OWN?????????????????????R/RW?????????
     * @param appId APP ID
     * @return ????????????
     */
    @Override
    public List<AppTemplateAuth> getTemplateAuthsByAppId(Integer appId) {
        if (!appService.isAppExists(appId)) {
            return Lists.newArrayList();
        }

        //??????????????????????????????own??????
        if (appService.isSuperApp(appId)) {
            List<IndexTemplateLogic> allLogicTemplates = templateLogicService.getAllLogicTemplates();
            return allLogicTemplates.stream().map(r -> buildTemplateAuth(r, AppTemplateAuthEnum.OWN))
                .collect(Collectors.toList());
        }

        // ???????????????????????????
        List<AppTemplateAuth> appTemplateRWAndRAuths = getAppActiveTemplateRWAndRAuths(appId);

        // ???????????????????????????????????????own??????
        List<AppTemplateAuth> appTemplateOwnerAuths = getAppTemplateOwnerAuths(appId);
        return mergeAppTemplateAuths(appTemplateRWAndRAuths, appTemplateOwnerAuths);
    }

    @Override
    public AppTemplateAuth getTemplateRWAuthByLogicTemplateIdAndAppId(Integer logicTemplateId, Integer appId) {
        return responsibleConvertTool.obj2Obj(
                templateAuthDAO.getByAppIdAndTemplateId(appId, String.valueOf(logicTemplateId)), AppTemplateAuth.class);
    }

    /**
     * ??????????????????????????????
     * @param logicTemplateId ????????????ID
     * @return ????????????
     */
    @Override
    public List<AppTemplateAuth> getTemplateAuthsByLogicTemplateId(Integer logicTemplateId) {
        return responsibleConvertTool.list2List(templateAuthDAO.listByLogicTemplateId(String.valueOf(logicTemplateId)),
            AppTemplateAuth.class);
    }

    /**
     * ????????????
     * @param authDTO  ????????????
     * @param operator ?????????
     * @return result
     */
    @Override
    public Result<Void> addTemplateAuth(AppTemplateAuthDTO authDTO, String operator) {

        Result<Void> checkResult = validateTemplateAuth(authDTO, OperationEnum.ADD);
        if (checkResult.failed()) {
            LOGGER.warn("class=AppAuthServiceImpl||method=addTemplateAuth||msg={}||msg=check fail!",
                checkResult.getMessage());
            return checkResult;
        }

        return addTemplateAuthWithoutCheck(authDTO, operator);
    }

    /**
     * ???????????? ????????????????????????????????????
     * @param authDTO  ??????
     * @param operator ?????????
     * @return result
     */
    @Override
    public Result<Void> updateTemplateAuth(AppTemplateAuthDTO authDTO, String operator) {
        Result<Void> checkResult = validateTemplateAuth(authDTO, OperationEnum.EDIT);
        if (checkResult.failed()) {
            LOGGER.warn("class=AppAuthServiceImpl||method=updateTemplateAuth||msg={}||msg=check fail!",
                checkResult.getMessage());
            return checkResult;
        }
        return updateTemplateAuthWithoutCheck(authDTO, operator);
    }

    /**
     * ??????????????????
     * @param authId   ??????
     * @param operator ?????????
     * @return result
     */
    @Override
    public Result<Void> deleteTemplateAuth(Long authId, String operator) {

        AppTemplateAuthPO oldAuthPO = templateAuthDAO.getById(authId);
        if (oldAuthPO == null) {
            return Result.buildNotExist("???????????????");
        }

        boolean succeed = 1 == templateAuthDAO.delete(authId);

        if (succeed) {
            SpringTool.publish(
                new AppTemplateAuthDeleteEvent(this, responsibleConvertTool.obj2Obj(oldAuthPO, AppTemplateAuth.class)));

            operateRecordService.save(ModuleEnum.LOGIC_TEMPLATE_PERMISSIONS, OperationEnum.DELETE, oldAuthPO.getId(),
                StringUtils.EMPTY, operator);
        }

        return Result.build(succeed);
    }

    @Override
    public Result<Void> deleteTemplateAuthByTemplateId(Integer templateId, String operator) {
        boolean succeed = false;
        try {
            List<AppTemplateAuthPO> oldAppTemplateAuthPO = templateAuthDAO.getByTemplateId(templateId);
            if (CollectionUtils.isEmpty(oldAppTemplateAuthPO)) {
                return Result.buildSucc();
            }

            List<Integer> oldTemplateIds = oldAppTemplateAuthPO.stream().map(AppTemplateAuthPO::getTemplateId).collect(Collectors.toList());
            succeed = oldTemplateIds.size() == templateAuthDAO.batchDeleteByTemplateIds(oldTemplateIds);
            if (succeed) {
                operateRecordService.save(ModuleEnum.LOGIC_TEMPLATE_PERMISSIONS, OperationEnum.DELETE, templateId,
                        StringUtils.EMPTY, operator);
            } else {
                LOGGER.error("class=AppLogicTemplateAuthServiceImpl||method=deleteTemplateAuthByTemplateId||delete infos failed");
            }
        } catch (Exception e) {
            LOGGER.error("class=AppLogicTemplateAuthServiceImpl||method=deleteTemplateAuthByTemplateId||errMsg={}",
                    e.getMessage(), e);
        }

        return Result.build(succeed);
    }

    /**
     * ????????????APP?????????
     * @return map, key???appId???value???app????????????????????????
     */
    @Override
    public Map<Integer, Collection<AppTemplateAuth>> getAllAppTemplateAuths() {

        List<AppTemplateAuth> authTemplates = getAllAppsActiveTemplateRWAuths();
        authTemplates.addAll(getAllAppsActiveTemplateOwnerAuths());

        return ConvertUtil.list2MulMap(authTemplates, AppTemplateAuth::getAppId).asMap();
    }

    @Override
    public AppTemplateAuthEnum getAuthEnumByAppIdAndLogicId(Integer appId, Integer logicId) {
        if (appService.isSuperApp(appId)) {
            return AppTemplateAuthEnum.OWN;
        }

        for (AppTemplateAuth appTemplateAuth : getTemplateAuthsByLogicTemplateId(logicId)) {
            if (appId.equals(appTemplateAuth.getAppId())) {
                return AppTemplateAuthEnum.valueOf(appTemplateAuth.getType());
            }
        }

        return AppTemplateAuthEnum.NO_PERMISSION;
    }

    @Override
    public AppTemplateAuth buildTemplateAuth(IndexTemplateLogic logicTemplate, AppTemplateAuthEnum appTemplateAuthEnum) {
        AppTemplateAuth auth = new AppTemplateAuth();
        auth.setAppId(logicTemplate.getAppId());
        auth.setTemplateId(logicTemplate.getId());
        auth.setType(appTemplateAuthEnum.getCode());
        auth.setResponsible(logicTemplate.getResponsible());
        return auth;
    }

    /**************************************** private method ****************************************************/
    /**
     * ???????????? ???????????????????????????????????? ???????????????
     * @param authDTO  ??????
     * @param operator ?????????
     * @return result
     */
    private Result<Void> updateTemplateAuthWithoutCheck(AppTemplateAuthDTO authDTO, String operator) {

        AppTemplateAuthPO oldAuthPO = templateAuthDAO.getById(authDTO.getId());
        AppTemplateAuthPO newAuthPO = responsibleConvertTool.obj2Obj(authDTO, AppTemplateAuthPO.class);

        boolean succeed = 1 == templateAuthDAO.update(newAuthPO);

        if (succeed) {
            SpringTool.publish(
                new AppTemplateAuthEditEvent(this, responsibleConvertTool.obj2Obj(oldAuthPO, AppTemplateAuth.class),
                    responsibleConvertTool.obj2Obj(templateAuthDAO.getById(authDTO.getId()), AppTemplateAuth.class)));

            operateRecordService.save(ModuleEnum.LOGIC_TEMPLATE_PERMISSIONS, OperationEnum.EDIT, oldAuthPO.getId(),
                JSON.toJSONString(newAuthPO), operator);
        }

        return Result.build(succeed);
    }

    /**
     * ????????????  ??????????????????
     * @param authDTO  ????????????
     * @param operator ?????????
     * @return result
     */
    private Result<Void> addTemplateAuthWithoutCheck(AppTemplateAuthDTO authDTO, String operator) {
        AppTemplateAuthPO authPO = responsibleConvertTool.obj2Obj(authDTO, AppTemplateAuthPO.class);

        boolean succeed = 1 == templateAuthDAO.insert(authPO);
        if (succeed) {
            // ????????????
            SpringTool.publish(
                new AppTemplateAuthAddEvent(this, responsibleConvertTool.obj2Obj(authPO, AppTemplateAuth.class)));

            // ????????????
            operateRecordService.save(ModuleEnum.LOGIC_TEMPLATE_PERMISSIONS, OperationEnum.ADD, authPO.getId(),
                JSON.toJSONString(authPO), operator);
        }

        return Result.build(succeed);
    }

    /**
     * ??????????????????
     * @param authDTO   ????????????
     * @param operation ??????
     * @return result
     */
    private Result<Void> validateTemplateAuth(AppTemplateAuthDTO authDTO, OperationEnum operation) {
        if (!EnvUtil.isOnline()) {
            LOGGER.info("class=AppAuthServiceImpl||method=validateTemplateAuth||authDTO={}",
                JSON.toJSONString(authDTO));
        }

        if (authDTO == null) {
            return Result.buildParamIllegal("??????????????????");
        }

        Integer appId = authDTO.getAppId();
        Integer logicTemplateId = authDTO.getTemplateId();
        AppTemplateAuthEnum authEnum = AppTemplateAuthEnum.valueOf(authDTO.getType());

        if (OperationEnum.ADD.equals(operation)) {
            Result<Void> result = handleAdd(authDTO, appId, logicTemplateId, authEnum);
            if (result.failed()) {
                return result;
            }
        } else if (OperationEnum.EDIT.equals(operation)) {
            Result<Void> result = handleEdit(authDTO);
            if (result.failed()){
                return result;
            }
        }

        // ????????????????????????
        if (AppTemplateAuthEnum.OWN == authEnum) {
            return Result.buildParamIllegal("???????????????????????????");
        }

        // ???????????????????????????
        if (!AriusObjUtils.isNull(authDTO.getResponsible())
                && AriusObjUtils.isNull(ariusUserInfoService.getByDomainAccount(authDTO.getResponsible()))) {
            return Result.buildParamIllegal("???????????????");
        }

        return Result.buildSucc();
    }

    private Result<Void> handleEdit(AppTemplateAuthDTO authDTO) {
        // ??????????????????
        if (AriusObjUtils.isNull(authDTO.getId())) {
            return Result.buildParamIllegal("??????ID??????");
        }

        if (null == templateAuthDAO.getById(authDTO.getId())) {
            return Result.buildNotExist("???????????????");
        }
        return Result.buildSucc();
    }

    private Result<Void> handleAdd(AppTemplateAuthDTO authDTO, Integer appId, Integer logicTemplateId, AppTemplateAuthEnum authEnum) {
        // ??????????????????
        if (AriusObjUtils.isNull(appId)) {
            return Result.buildParamIllegal("appId??????");
        }

        if (AriusObjUtils.isNull(appService.getAppById(appId))) {
            return Result.buildParamIllegal(String.format("app[%d]?????????", appId));
        }

        if (AriusObjUtils.isNull(logicTemplateId)) {
            return Result.buildParamIllegal("??????ID??????");
        }

        IndexTemplateLogic logicTemplate = templateLogicService.getLogicTemplateById(logicTemplateId);
        if (AriusObjUtils.isNull(logicTemplate)) {
            return Result.buildParamIllegal(String.format("????????????[%d]?????????", logicTemplateId));
        }

        if (AriusObjUtils.isNull(authDTO.getType())) {
            return Result.buildParamIllegal("??????????????????");
        }

        if (AriusObjUtils.isNull(authDTO.getResponsible())) {
            return Result.buildParamIllegal("???????????????");
        }

        // ???????????????????????????????????????
        if (null != templateAuthDAO.getByAppIdAndTemplateId(appId, String.valueOf(logicTemplateId))) {
            return Result.buildNotExist("???????????????");
        }

        // APP??????????????????owner???????????????
        if (logicTemplate.getAppId().equals(appId) && authEnum == AppTemplateAuthEnum.OWN) {
            return Result.buildDuplicate(String.format("APP[%d]??????????????????", appId));
        }

        // ???????????????????????????????????????
        ClusterLogic clusterLogic = templateLogicService
            .getLogicTemplateWithClusterAndMasterTemplate(logicTemplateId).getLogicCluster();
        if (AriusObjUtils.isNull(clusterLogic) || logicClusterAuthService.getLogicClusterAuthEnum(appId,
                clusterLogic.getId()) == AppClusterLogicAuthEnum.NO_PERMISSIONS) {
            return Result.buildOpForBidden("?????????????????????????????????");
        }
        return Result.buildSucc();
    }

    /**
     * ????????????APP??????OWNER???????????????????????????
     * @return
     */
    private List<AppTemplateAuth> getAllAppsActiveTemplateOwnerAuths() {
        List<IndexTemplateLogic> logicTemplates = templateLogicService.getAllLogicTemplates();
        Map<Integer, App> appsMap = appService.getAppsMap();

        return logicTemplates
                .stream()
                .filter(indexTemplateLogic -> appsMap.containsKey(indexTemplateLogic.getAppId()))
                .map(r -> buildTemplateAuth(r, AppTemplateAuthEnum.OWN))
                .collect(Collectors.toList());
    }

    /**
     * ????????????????????????RW??????????????????????????????
     * @return
     */
    private List<AppTemplateAuth> getAllAppsActiveTemplateRWAuths() {
        List<AppTemplateAuth> rwTemplateAuths = responsibleConvertTool.list2List(templateAuthDAO.listWithRwAuths(),
            AppTemplateAuth.class);

        // ?????????active???????????????????????????
        Set<Integer> logicTemplateIds = templateLogicService.getAllLogicTemplates().stream().map(IndexTemplateLogic::getId)
                .collect(Collectors.toSet());
        return rwTemplateAuths.stream().filter(authTemplate -> logicTemplateIds.contains(authTemplate.getTemplateId()))
            .collect(Collectors.toList());
    }

    private List<AppTemplateAuth> getAppTemplateOwnerAuths(Integer appId) {
        List<IndexTemplateLogic> ownAuthTemplates = templateLogicService.getAppLogicTemplatesByAppId(appId);
        return ownAuthTemplates.stream().map(r -> buildTemplateAuth(r, AppTemplateAuthEnum.OWN))
            .collect(Collectors.toList());
    }

    @Override
    public List<AppTemplateAuth> getAppActiveTemplateRWAndRAuths(Integer appId) {
        return responsibleConvertTool
            .list2List(templateAuthDAO.listWithRwAuthsByAppId(appId), AppTemplateAuth.class);
    }

    @Override
    public List<AppTemplateAuth> getAppTemplateRWAndRAuthsWithoutCodecResponsible(Integer appId) {
        return ConvertUtil
                .list2List(templateAuthDAO.listWithRwAuthsByAppId(appId), AppTemplateAuth.class);
    }

    @Override
    public List<AppTemplateAuth> getAppActiveTemplateRWAuths(Integer appId) {
        AppTemplateAuthPO appTemplateAuthPO = new AppTemplateAuthPO();
        appTemplateAuthPO.setAppId(appId);
        appTemplateAuthPO.setType(AppTemplateAuthEnum.RW.getCode());
        return responsibleConvertTool
                .list2List(templateAuthDAO.listByCondition(appTemplateAuthPO), AppTemplateAuth.class);
    }

    @Override
    public List<AppTemplateAuth> getAppActiveTemplateRAuths(Integer appId) {
        AppTemplateAuthPO appTemplateAuthPO = new AppTemplateAuthPO();
        appTemplateAuthPO.setAppId(appId);
        appTemplateAuthPO.setType(AppTemplateAuthEnum.R.getCode());
        return responsibleConvertTool
                .list2List(templateAuthDAO.listByCondition(appTemplateAuthPO), AppTemplateAuth.class);
    }

    /**
     * ??????????????????
     * @param templateAuths ??????????????????
     * @param deleteFlags   ????????????
     */
    private void doDeleteOperationForNeed(Collection<AppTemplateAuthPO> templateAuths, boolean deleteFlags) {
        if (CollectionUtils.isNotEmpty(templateAuths)) {
            for (AppTemplateAuthPO templateAuth : templateAuths) {
                if (deleteFlags) {
                    if (1 == templateAuthDAO.delete(templateAuth.getId())) {
                        LOGGER.info("class=AppLogicTemplateAuthServiceImpl||method=checkMeta||msg=deleteTemplateAuthSucceed||authId={}", templateAuth.getId());
                    }
                } else {
                    LOGGER.info("class=AppLogicTemplateAuthServiceImpl||method=checkMeta||msg=deleteCheck||authId={}", templateAuth.getId());
                }
            }
        }
    }

    /**
     * ?????????????????????????????????
     * @param appTemplateRWAuths       ???????????????????????????????????????????????????
     * @param appTemplateOwnerAuths    ?????????????????????????????????????????????
     * @return
     */
    private List<AppTemplateAuth> mergeAppTemplateAuths(List<AppTemplateAuth> appTemplateRWAuths,
                                                        List<AppTemplateAuth> appTemplateOwnerAuths) {
        List<AppTemplateAuth> mergeAppTemplateAuthList = Lists.newArrayList();
        List<Integer> appOwnTemplateId = appTemplateOwnerAuths.stream().map(AppTemplateAuth::getTemplateId)
            .collect(Collectors.toList());

        //?????????????????????????????????
        for (AppTemplateAuth appTemplateRWAuth : appTemplateRWAuths) {
            if (appOwnTemplateId.contains(appTemplateRWAuth.getTemplateId())) {
                continue;
            }
            mergeAppTemplateAuthList.add(appTemplateRWAuth);
        }

        mergeAppTemplateAuthList.addAll(appTemplateOwnerAuths);
        return mergeAppTemplateAuthList;
    }
}
