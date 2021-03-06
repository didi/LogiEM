package com.didichuxing.datachannel.arius.admin.core.service.app.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ClusterLogic;
import com.didichuxing.datachannel.arius.admin.core.service.app.AppService;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.logic.ClusterLogicService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson.JSON;
import com.didichuxing.datachannel.arius.admin.client.bean.common.Result;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.app.AppLogicClusterAuthDTO;
import com.didichuxing.datachannel.arius.admin.client.constant.app.AppClusterLogicAuthEnum;
import com.didichuxing.datachannel.arius.admin.client.constant.operaterecord.ModuleEnum;
import com.didichuxing.datachannel.arius.admin.client.constant.operaterecord.OperationEnum;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.app.AppClusterLogicAuth;
import com.didichuxing.datachannel.arius.admin.common.bean.po.app.AppClusterLogicAuthPO;
import com.didichuxing.datachannel.arius.admin.common.component.SpringTool;
import com.didichuxing.datachannel.arius.admin.common.event.auth.AppLogicClusterAuthAddEvent;
import com.didichuxing.datachannel.arius.admin.common.event.auth.AppLogicClusterAuthDeleteEvent;
import com.didichuxing.datachannel.arius.admin.common.event.auth.AppLogicClusterAuthEditEvent;
import com.didichuxing.datachannel.arius.admin.common.util.AriusObjUtils;
import com.didichuxing.datachannel.arius.admin.common.util.ConvertUtil;
import com.didichuxing.datachannel.arius.admin.common.util.EnvUtil;
import com.didichuxing.datachannel.arius.admin.core.component.ResponsibleConvertTool;
import com.didichuxing.datachannel.arius.admin.core.service.app.AppClusterLogicAuthService;
import com.didichuxing.datachannel.arius.admin.core.service.common.AriusUserInfoService;
import com.didichuxing.datachannel.arius.admin.core.service.common.OperateRecordService;
import com.didichuxing.datachannel.arius.admin.persistence.mysql.app.AppLogicClusterAuthDAO;
import com.didiglobal.logi.log.ILog;
import com.didiglobal.logi.log.LogFactory;

/**
 * APP ????????????????????????
 * @author wangshu
 * @date 2020/09/19
 */
@Service
public class AppClusterLogicAuthServiceImpl implements AppClusterLogicAuthService {

    private static final ILog      LOGGER = LogFactory.getLog(AppLogicTemplateAuthServiceImpl.class);

    @Autowired
    private AppLogicClusterAuthDAO logicClusterAuthDAO;

    @Autowired
    private ClusterLogicService    clusterLogicService;

    @Autowired
    private AppService             appService;

    @Autowired
    private OperateRecordService   operateRecordService;

    @Autowired
    private AriusUserInfoService   ariusUserInfoService;

    @Autowired
    private ResponsibleConvertTool responsibleConvertTool;

    /**
     * ??????APP???????????????????????????.
     * ??????????????????????????????????????????????????????????????????????????????????????????
     * @param appId          APP???ID
     * @param logicClusterId ????????????ID
     * @param auth           ??????????????????
     * @param responsible    ??????????????????????????????????????????
     * @param operator       ?????????
     * @return ????????????
     */
    @Override
    public Result<Void> ensureSetLogicClusterAuth(Integer appId, Long logicClusterId, AppClusterLogicAuthEnum auth,
                                            String responsible, String operator) {
        // ????????????
        if (appId == null) {
            return Result.buildParamIllegal("?????????appId");
        }

        if (logicClusterId == null) {
            return Result.buildParamIllegal("?????????????????????ID");
        }

        if (StringUtils.isBlank(operator)) {
            return Result.buildParamIllegal("??????????????????");
        }

        // ?????????????????????????????????????????????????????????id??????null????????????????????????id???null???
        AppClusterLogicAuth oldAuth = getLogicClusterAuth(appId, logicClusterId);

        if (oldAuth == null || oldAuth.getType().equals(AppClusterLogicAuthEnum.NO_PERMISSIONS.getCode())) {
            // ???????????????
            return handleNoAuth(appId, logicClusterId, auth, responsible, operator);
        } else {
            // ???????????????
            if (oldAuth.getId() != null) {
                // ??????????????????
                return deleteAuth(auth, responsible, operator, oldAuth);
            } else {
                //????????????????????????????????????????????????OWN???,????????????owner???app?????????????????????????????????????????????OWN?????????
                return addAuth(appId, logicClusterId, auth, responsible, operator);
            }
        }
    }

    private Result<Void> addAuth(Integer appId, Long logicClusterId, AppClusterLogicAuthEnum auth, String responsible, String operator) {
        if (auth != null
            && AppClusterLogicAuthEnum.valueOf(auth.getCode()).higher(AppClusterLogicAuthEnum.OWN)) {
            return addLogicClusterAuth(
                new AppLogicClusterAuthDTO(null, appId, logicClusterId, auth.getCode(), responsible), operator);
        } else {
            return Result.buildFail("??????????????????owner?????????????????????");
        }
    }

    private Result<Void> deleteAuth(AppClusterLogicAuthEnum auth, String responsible, String operator, AppClusterLogicAuth oldAuth) {
        if (auth == AppClusterLogicAuthEnum.NO_PERMISSIONS) {
            return deleteLogicClusterAuthById(oldAuth.getId(), operator);
        }

        // ????????????????????????
        AppLogicClusterAuthDTO newAuthDTO = new AppLogicClusterAuthDTO(oldAuth.getId(), null, null,
            auth == null ? null : auth.getCode(), StringUtils.isBlank(responsible) ? null : responsible);
        return updateLogicClusterAuth(newAuthDTO, operator);
    }

    private Result<Void> handleNoAuth(Integer appId, Long logicClusterId, AppClusterLogicAuthEnum auth, String responsible, String operator) {
        // NO_PERMISSIONS????????????
        if (auth == null || auth == AppClusterLogicAuthEnum.NO_PERMISSIONS) {
            return Result.buildSucc();
        }

        // ??????
        return addLogicClusterAuth(
            new AppLogicClusterAuthDTO(null, appId, logicClusterId, auth.getCode(), responsible), operator);
    }

    /**
     * ???????????????????????????
     * @param logicClusterAuth ?????????????????????
     * @return
     */
    @Override
    public Result<Void> addLogicClusterAuth(AppLogicClusterAuthDTO logicClusterAuth, String operator) {

        Result<Void> checkResult = validateLogicClusterAuth(logicClusterAuth, OperationEnum.ADD);
        if (checkResult.failed()) {
            LOGGER.warn("class=AppClusterLogicAuthServiceImpl||method=createLogicClusterAuth||msg={}||msg=check fail!",
                checkResult.getMessage());
            return checkResult;
        }

        return addLogicClusterAuthWithoutCheck(logicClusterAuth, operator);
    }

    /**
     * ???????????????????????????
     * @param logicClusterAuth ?????????????????????
     * @return
     */
    @Override
    public Result<Void> updateLogicClusterAuth(AppLogicClusterAuthDTO logicClusterAuth, String operator) {
        // ???????????????????????????????????????
        logicClusterAuth.setAppId(null);
        logicClusterAuth.setLogicClusterId(null);

        Result<Void> checkResult = validateLogicClusterAuth(logicClusterAuth, OperationEnum.EDIT);
        if (checkResult.failed()) {
            LOGGER.warn("class=AppClusterLogicAuthServiceImpl||method=createLogicClusterAuth||msg={}||msg=check fail!",
                checkResult.getMessage());
            return checkResult;
        }

        return updateLogicClusterAuthWithoutCheck(logicClusterAuth, operator);
    }

    /**
     * ???????????????
     * @param authId ?????????ID
     * @return
     */
    @Override
    public Result<Void> deleteLogicClusterAuthById(Long authId, String operator) {

        AppClusterLogicAuthPO oldAuthPO = logicClusterAuthDAO.getById(authId);
        if (oldAuthPO == null) {
            return Result.buildNotExist("???????????????");
        }

        boolean succeed = 1 == logicClusterAuthDAO.delete(authId);
        if (succeed) {
            SpringTool.publish(new AppLogicClusterAuthDeleteEvent(this,
                responsibleConvertTool.obj2Obj(oldAuthPO, AppClusterLogicAuth.class)));

            operateRecordService.save(ModuleEnum.LOGIC_CLUSTER_PERMISSIONS, OperationEnum.DELETE, oldAuthPO.getId(),
                StringUtils.EMPTY, operator);
        }

        return Result.build(succeed);
    }

    @Override
    public Result<Boolean> deleteLogicClusterAuthByLogicClusterId(Long logicClusterId) {
        boolean succ = logicClusterAuthDAO.deleteByLogicClusterId(logicClusterId) >= 0;
        return Result.buildBoolen(succ);
    }

    /**
     * ??????APP???????????????
     * @param appId ??????ID
     * @return
     */
    @Override
    public List<AppClusterLogicAuth> getAllLogicClusterAuths(Integer appId) {

        if (appId == null) {
            return new ArrayList<>();
        }

        // ?????????
        List<AppClusterLogicAuthPO> authPOs = logicClusterAuthDAO.listByAppId(appId);
        List<AppClusterLogicAuth> authDTOs = ConvertUtil.list2List(authPOs, AppClusterLogicAuth.class);

        // ????????????????????????APP??????owner?????????
        List<ClusterLogic> clusterLogicList = clusterLogicService.getOwnedClusterLogicListByAppId(appId);
        authDTOs.addAll(clusterLogicList
                        .stream()
                        .map(clusterLogic -> buildLogicClusterAuth(clusterLogic, AppClusterLogicAuthEnum.OWN))
                        .collect(Collectors.toList()));

        return authDTOs;
    }

    @Override
    public List<AppClusterLogicAuth> getLogicClusterAccessAuths(Integer appId) {
        return ConvertUtil.list2List(logicClusterAuthDAO.listWithAccessByAppId(appId), AppClusterLogicAuth.class);
    }

    /**
     * ??????ID???????????????????????????
     * @param authId ?????????ID
     * @return
     */
    @Override
    public AppClusterLogicAuth getLogicClusterAuthById(Long authId) {
        return ConvertUtil.obj2Obj(logicClusterAuthDAO.getById(authId), AppClusterLogicAuth.class);
    }

    /**
     * ????????????app??????????????????????????????.
     * @param appId          APP ID
     * @param logicClusterId ????????????ID
     */
    @Override
    public AppClusterLogicAuthEnum getLogicClusterAuthEnum(Integer appId, Long logicClusterId) {
        if (appId == null || logicClusterId == null) {
            return AppClusterLogicAuthEnum.NO_PERMISSIONS;
        }

        AppClusterLogicAuth auth = getLogicClusterAuth(appId, logicClusterId);
        return auth == null ? AppClusterLogicAuthEnum.NO_PERMISSIONS
            : AppClusterLogicAuthEnum.valueOf(auth.getType());
    }

    /**
     * ????????????app?????????????????????????????????????????????????????????null.
     * ??????????????????????????????id??????null????????????????????????????????????????????????????????????????????????
     * @param appId          APP ID
     * @param logicClusterId ????????????ID
     */
    @Override
    public AppClusterLogicAuth getLogicClusterAuth(Integer appId, Long logicClusterId) {
        if (appId == null || logicClusterId == null) {
            return null;
        }

        // ????????????????????????????????????
        ClusterLogic clusterLogic = clusterLogicService.getClusterLogicById(logicClusterId);
        AppClusterLogicAuthEnum authFromCreateRecord = (clusterLogic != null && appId.equals(clusterLogic.getAppId()))
                                                            ? AppClusterLogicAuthEnum.OWN
                                                            : AppClusterLogicAuthEnum.NO_PERMISSIONS;

        // ??????????????????????????????
        AppClusterLogicAuthPO authPO = logicClusterAuthDAO.getByAppIdAndLogicCluseterId(appId, logicClusterId);
        AppClusterLogicAuthEnum authFromAuthRecord = authPO != null ? AppClusterLogicAuthEnum.valueOf(authPO.getType()) : AppClusterLogicAuthEnum.NO_PERMISSIONS;

        // ???????????????
        if (authFromCreateRecord == AppClusterLogicAuthEnum.NO_PERMISSIONS
            && authFromAuthRecord == AppClusterLogicAuthEnum.NO_PERMISSIONS) {
            return buildLogicClusterAuth(clusterLogic, AppClusterLogicAuthEnum.NO_PERMISSIONS);
        }

        // ????????????????????????AppLogicClusterAuthDTO?????????????????????????????????
        return authFromAuthRecord.higherOrEqual(authFromCreateRecord)
            ? ConvertUtil.obj2Obj(authPO, AppClusterLogicAuth.class)
            : buildLogicClusterAuth(clusterLogic, AppClusterLogicAuthEnum.OWN);

    }

    /**
     * ?????????????????????????????????
     * @param logicClusterId  ????????????ID
     * @param clusterAuthType ??????????????????
     * @return
     */
    @Override
    public List<AppClusterLogicAuth> getLogicClusterAuths(Long logicClusterId,
                                                             AppClusterLogicAuthEnum clusterAuthType) {

        AppClusterLogicAuthPO queryParams = new AppClusterLogicAuthPO();
        if (logicClusterId != null) {
            queryParams.setLogicClusterId(logicClusterId);
        }

        if (clusterAuthType != null) {
            queryParams.setType(clusterAuthType.getCode());
        }

        // ?????????
        List<AppClusterLogicAuthPO> authPOs = logicClusterAuthDAO.listByCondition(queryParams);
        List<AppClusterLogicAuth>  authDTOS = ConvertUtil.list2List(authPOs, AppClusterLogicAuth.class);

        // ????????????????????????APP??????owner?????????
        if (logicClusterId != null && clusterAuthType == AppClusterLogicAuthEnum.OWN) {
            ClusterLogic clusterLogic = clusterLogicService.getClusterLogicById(logicClusterId);
            if (clusterLogic != null) {
                authDTOS.add(buildLogicClusterAuth(clusterLogic, AppClusterLogicAuthEnum.OWN));
            }
        }

        return authDTOS;
    }

    @Override
    public boolean canCreateLogicTemplate(Integer appId, Long logicClusterId) {
        if (appId == null || logicClusterId == null) {
            return false;
        }

        AppClusterLogicAuthEnum authEnum = getLogicClusterAuthEnum(appId, logicClusterId);
        return authEnum.higherOrEqual(AppClusterLogicAuthEnum.ACCESS);
    }

    /**
     * ????????????  ??????????????????
     * @param authDTO  ????????????
     * @param operator ?????????
     * @return result
     */
    @Override
    public Result<Void> addLogicClusterAuthWithoutCheck(AppLogicClusterAuthDTO authDTO, String operator) {
        AppClusterLogicAuthPO authPO = responsibleConvertTool.obj2Obj(authDTO, AppClusterLogicAuthPO.class);

        boolean succeed = 1 == logicClusterAuthDAO.insert(authPO);
        if (succeed) {
            // ????????????
            SpringTool.publish(new AppLogicClusterAuthAddEvent(this,
                responsibleConvertTool.obj2Obj(authPO, AppClusterLogicAuth.class)));

            // ????????????
            operateRecordService.save(ModuleEnum.LOGIC_CLUSTER_PERMISSIONS, OperationEnum.ADD, authPO.getId(),
                JSON.toJSONString(authPO), operator);
        }

        return Result.build(succeed);
    }

    @Override
    public AppClusterLogicAuth buildClusterLogicAuth(Integer appId, Long clusterLogicId,
                                                     AppClusterLogicAuthEnum appClusterLogicAuthEnum) {
        if (null == appClusterLogicAuthEnum || null == appId || null == clusterLogicId) {
            return null;
        }

        if (!AppClusterLogicAuthEnum.isExitByCode(appClusterLogicAuthEnum.getCode())) {
            return null;
        }

        AppClusterLogicAuth appClusterLogicAuth = new AppClusterLogicAuth();
        appClusterLogicAuth.setAppId(appId);
        appClusterLogicAuth.setLogicClusterId(clusterLogicId);
        appClusterLogicAuth.setType(appClusterLogicAuthEnum.getCode());
        return appClusterLogicAuth;
    }

    @Override
    public List<AppClusterLogicAuth> list() {
        return  ConvertUtil.list2List(logicClusterAuthDAO.listByCondition(null), AppClusterLogicAuth.class);
    }

    /**************************************** private method ****************************************************/
    /**
     * ??????????????????
     * @param authDTO   ????????????
     * @param operation ??????
     * @return result
     */
    private Result<Void> validateLogicClusterAuth(AppLogicClusterAuthDTO authDTO, OperationEnum operation) {
        if (!EnvUtil.isOnline()) {
            LOGGER.info("class=AppAuthServiceImpl||method=validateTemplateAuth||authDTO={}||operator={}",
                JSON.toJSONString(authDTO), operation);
        }

        if (authDTO == null) {
            return Result.buildParamIllegal("??????????????????");
        }

        Integer appId = authDTO.getAppId();
        Long logicClusterId = authDTO.getLogicClusterId();
        AppClusterLogicAuthEnum authEnum = AppClusterLogicAuthEnum.valueOf(authDTO.getType());

        if (OperationEnum.ADD.equals(operation)) {
            Result<Void> result = handleAdd(authDTO, appId, logicClusterId, authEnum);
            if (result.failed()) return result;

        } else if (OperationEnum.EDIT.equals(operation)) {
            Result<Void> result = handleEdit(authDTO);
            if (result.failed()) return result;
        }

        Result<Void> isIllegalResult = isIllegal(authDTO, authEnum);
        if (isIllegalResult.failed()) return isIllegalResult;

        return Result.buildSucc();
    }

    private Result<Void> handleEdit(AppLogicClusterAuthDTO authDTO) {
        // ??????????????????
        if (AriusObjUtils.isNull(authDTO.getId())) {
            return Result.buildParamIllegal("??????ID??????");
        }

        if (null == logicClusterAuthDAO.getById(authDTO.getId())) {
            return Result.buildNotExist("???????????????");
        }
        return Result.buildSucc();
    }

    private Result<Void> handleAdd(AppLogicClusterAuthDTO authDTO, Integer appId, Long logicClusterId, AppClusterLogicAuthEnum authEnum) {
        // ??????????????????
        Result<Void> judgeResult = validateAppIdIsNull(appId, logicClusterId);
        if (judgeResult.failed()) {
            return judgeResult;
        }
        
        ClusterLogic clusterLogic = clusterLogicService.getClusterLogicById(logicClusterId);
        if (AriusObjUtils.isNull(clusterLogic)) {
            return Result.buildParamIllegal(String.format("????????????[%d]?????????", logicClusterId));
        }

        if (AriusObjUtils.isNull(authDTO.getType())) {
            return Result.buildParamIllegal("??????????????????");
        }

        if (AriusObjUtils.isNull(authDTO.getResponsible())) {
            return Result.buildParamIllegal("???????????????");
        }

        // ???????????????????????????????????????
        if (null != logicClusterAuthDAO.getByAppIdAndLogicCluseterId(appId, logicClusterId)) {
            return Result.buildDuplicate("???????????????");
        }

        // APP??????????????????owner???????????????
        if (clusterLogic.getAppId().equals(appId) && authEnum == AppClusterLogicAuthEnum.OWN) {
            return Result.buildDuplicate(String.format("APP[%d]??????????????????", appId));
        }
        return Result.buildSucc();
    }

    private Result<Void> isIllegal(AppLogicClusterAuthDTO authDTO, AppClusterLogicAuthEnum authEnum) {
        if (AppClusterLogicAuthEnum.NO_PERMISSIONS == authEnum) {
            // ???????????????????????????????????????
            return Result.buildParamIllegal("?????????????????????");
        }

        // ????????????????????????
        if (AppClusterLogicAuthEnum.ALL == authEnum) {
            return Result.buildParamIllegal("???????????????????????????");
        }

        // ???????????????????????????
        if (!AriusObjUtils.isNull(authDTO.getResponsible())
            && AriusObjUtils.isNull(ariusUserInfoService.getByDomainAccount(authDTO.getResponsible()))) {
            return Result.buildParamIllegal("???????????????");
        }
        return Result.buildSucc();
    }

    private Result<Void> validateAppIdIsNull(Integer appId, Long logicClusterId) {
        if (AriusObjUtils.isNull(appId)) {
            return Result.buildParamIllegal("appId??????");
        }

        
        if (!appService.isAppExists(appId)) {
            return Result.buildParamIllegal(String.format("app[%d]?????????", appId));
        }

        if (AriusObjUtils.isNull(logicClusterId)) {
            return Result.buildParamIllegal("????????????ID??????");
        }
        return Result.buildSucc();
    }

    /**
     * ???????????????????????????owner APP???????????????
     * @param clusterLogic ??????????????????
     */
    private AppClusterLogicAuth buildLogicClusterAuth(ClusterLogic clusterLogic, AppClusterLogicAuthEnum appClusterLogicAuthEnum) {
        if (clusterLogic == null) {
            return null;
        }
        AppClusterLogicAuth appLogicClusterAuth = new AppClusterLogicAuth();
        appLogicClusterAuth.setId(null);
        appLogicClusterAuth.setAppId(clusterLogic.getAppId());
        appLogicClusterAuth.setLogicClusterId(clusterLogic.getId());
        appLogicClusterAuth.setType(appClusterLogicAuthEnum.getCode());
        appLogicClusterAuth.setResponsible(clusterLogic.getResponsible());
        return appLogicClusterAuth;
    }

    /**
     * ???????????? ???????????????????????????????????? ???????????????
     * @param authDTO  ??????
     * @param operator ?????????
     * @return result
     */
    private Result<Void> updateLogicClusterAuthWithoutCheck(AppLogicClusterAuthDTO authDTO, String operator) {

        AppClusterLogicAuthPO oldAuthPO = logicClusterAuthDAO.getById(authDTO.getId());
        AppClusterLogicAuthPO newAuthPO = responsibleConvertTool.obj2Obj(authDTO, AppClusterLogicAuthPO.class);
        boolean succeed = 1 == logicClusterAuthDAO.update(newAuthPO);
        if (succeed) {
            SpringTool.publish(new AppLogicClusterAuthEditEvent(this,
                responsibleConvertTool.obj2Obj(oldAuthPO, AppClusterLogicAuth.class), responsibleConvertTool
                    .obj2Obj(logicClusterAuthDAO.getById(authDTO.getId()), AppClusterLogicAuth.class)));

            operateRecordService.save(ModuleEnum.LOGIC_CLUSTER_PERMISSIONS, OperationEnum.EDIT, oldAuthPO.getId(),
                JSON.toJSONString(newAuthPO), operator);
        }

        return Result.build(succeed);
    }
}
