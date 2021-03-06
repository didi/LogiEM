package com.didichuxing.datachannel.arius.admin.core.service.app.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.didichuxing.datachannel.arius.admin.client.bean.common.Result;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.app.AppConfigDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.app.AppDTO;
import com.didichuxing.datachannel.arius.admin.client.constant.app.AppClusterLogicAuthEnum;
import com.didichuxing.datachannel.arius.admin.client.constant.app.AppSearchTypeEnum;
import com.didichuxing.datachannel.arius.admin.client.constant.operaterecord.OperationEnum;
import com.didichuxing.datachannel.arius.admin.client.constant.result.ResultType;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.app.App;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.app.AppClusterLogicAuth;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.app.AppConfig;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.app.AppUserInfo;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ClusterLogic;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.template.IndexTemplateLogic;
import com.didichuxing.datachannel.arius.admin.common.bean.po.app.AppConfigPO;
import com.didichuxing.datachannel.arius.admin.common.bean.po.app.AppPO;
import com.didichuxing.datachannel.arius.admin.common.component.SpringTool;
import com.didichuxing.datachannel.arius.admin.common.constant.AdminConstant;
import com.didichuxing.datachannel.arius.admin.common.event.app.AppAddEvent;
import com.didichuxing.datachannel.arius.admin.common.event.app.AppDeleteEvent;
import com.didichuxing.datachannel.arius.admin.common.event.app.AppEditEvent;
import com.didichuxing.datachannel.arius.admin.common.util.AriusObjUtils;
import com.didichuxing.datachannel.arius.admin.common.util.ConvertUtil;
import com.didichuxing.datachannel.arius.admin.common.util.EnvUtil;
import com.didichuxing.datachannel.arius.admin.common.util.VerifyCodeFactory;
import com.didichuxing.datachannel.arius.admin.core.component.ResponsibleConvertTool;
import com.didichuxing.datachannel.arius.admin.core.service.app.AppClusterLogicAuthService;
import com.didichuxing.datachannel.arius.admin.core.service.app.AppService;
import com.didichuxing.datachannel.arius.admin.core.service.app.AppUserInfoService;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.logic.ClusterLogicService;
import com.didichuxing.datachannel.arius.admin.core.service.common.OperateRecordService;
import com.didichuxing.datachannel.arius.admin.core.service.extend.employee.EmployeeService;
import com.didichuxing.datachannel.arius.admin.core.service.template.logic.TemplateLogicService;
import com.didichuxing.datachannel.arius.admin.persistence.mysql.app.AppConfigDAO;
import com.didichuxing.datachannel.arius.admin.persistence.mysql.app.AppDAO;
import com.didiglobal.logi.log.ILog;
import com.didiglobal.logi.log.LogFactory;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.didichuxing.datachannel.arius.admin.client.constant.operaterecord.ModuleEnum.APP;
import static com.didichuxing.datachannel.arius.admin.client.constant.operaterecord.ModuleEnum.APP_CONFIG;
import static com.didichuxing.datachannel.arius.admin.client.constant.operaterecord.OperationEnum.*;
import static com.didichuxing.datachannel.arius.admin.common.constant.AdminConstant.yesOrNo;

@Service
public class AppServiceImpl implements AppService {

    private static final ILog          LOGGER                      = LogFactory.getLog(AppServiceImpl.class);

    private static final Integer       VERIFY_CODE_LENGTH          = 15;

    private static final Integer       APP_QUERY_THRESHOLD_DEFAULT = 100;

    private static final String APP_NOT_EXIST = "???????????????";

    @Autowired
    private AppDAO                      appDAO;

    @Autowired
    private EmployeeService             employeeService;

    @Autowired
    private OperateRecordService        operateRecordService;

    @Autowired
    private ResponsibleConvertTool      responsibleConvertTool;

    @Autowired
    private AppConfigDAO                appConfigDAO;

    @Autowired
    private AppUserInfoService          appUserInfoService;

    @Autowired
    private ClusterLogicService         clusterLogicService;

    @Autowired
    private TemplateLogicService        templateLogicService;

    @Autowired
    private AppClusterLogicAuthService logicClusterAuthService;

    private Cache<String, List<?>>      appListCache = CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.MINUTES).maximumSize(100).build();


    /**
     * ??????app????????????
     * @return ??????app??????
     */
    @Override
    public List<App> listApps() {
        return responsibleConvertTool.list2List(appDAO.listByCondition(new AppPO()), App.class);
    }

    @Override
    public List<App> listAppWithCache() {
        try {
            return (List<App>) appListCache.get("listApp", this::listApps);
        } catch (ExecutionException e) {
            return listApps();
        }
    }

    /**
     * ??????app????????????
     * @return ??????app?????????map???key???appId, value???app
     */
    @Override
    public Map<Integer, App> getAppsMap() {
        return ConvertUtil.list2Map( listApps(), App::getId);
    }

    /**
     * ??????APP
     * @param appDTO   dto
     * @param operator ????????? ????????????
     * @return ?????? true  ?????? false
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Integer> registerApp(AppDTO appDTO, String operator) {
        Result<Void> checkResult = validateApp(appDTO, ADD);
        if (checkResult.failed()) {
            LOGGER.warn("class=AppServiceImpl||method=addApp||fail msg={}", checkResult.getMessage());
            return Result.buildFrom(checkResult);
        }
        return addAppWithoutCheck(appDTO, operator);
    }

    /**
     * ??????APP??????????????????
     *
     * @param appDTO         dto
     * @param operation ????????????null??????;  ???????????????????????????,??????????????????????????????
     * @return ??????????????????
     */
    @Override
    public Result<Void> validateApp(AppDTO appDTO, OperationEnum operation) {
        if (AriusObjUtils.isNull(appDTO)) {
            return Result.buildParamIllegal("??????????????????");
        }
        Result<Void> validateAppFieldIsNullResult = validateAppFieldIsNull(appDTO);
        if (validateAppFieldIsNullResult.failed()) {return validateAppFieldIsNullResult;}

        if (AriusObjUtils.isNull(appDTO.getResponsible()) || employeeService.checkUsers(appDTO.getResponsible()).failed()) {
            return Result.buildParamIllegal("???????????????");
        }

        if (ADD.equals(operation)) {
            if (AriusObjUtils.isNull(appDTO.getName())) {
                return Result.buildParamIllegal("??????????????????");
            }
        } else if (EDIT.equals(operation)) {
            if (AriusObjUtils.isNull(appDTO.getId())) {
                return Result.buildParamIllegal("??????ID??????");
            }

            AppPO oldApp = appDAO.getById(appDTO.getId());
            if (AriusObjUtils.isNull(oldApp)) {
                return Result.buildNotExist(APP_NOT_EXIST);
            }
        }

        if (appDTO.getIsRoot() == null || !AdminConstant.yesOrNo(appDTO.getIsRoot())) {
            return Result.buildParamIllegal("??????????????????");
        }

        AppSearchTypeEnum searchTypeEnum = AppSearchTypeEnum.valueOf(appDTO.getSearchType());
        if (searchTypeEnum.equals(AppSearchTypeEnum.UNKNOWN)) {
            return Result.buildParamIllegal("??????????????????");
        }
        if (StringUtils.isBlank(appDTO.getVerifyCode())) {
            return Result.buildParamIllegal("?????????????????????");
        }
        // ??????????????????
        AppPO oldAppPO = getByName(appDTO.getName());
        if (oldAppPO != null && !oldAppPO.getId().equals(appDTO.getId())) {
            return Result.buildDuplicate("??????????????????");
        }

        return Result.buildSucc();
    }

    /**
     * ??????APP
     * @param appDTO   dto
     * @param operator ????????? ????????????
     * @return ?????? true  ?????? false
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> editApp(AppDTO appDTO, String operator) {
        Result<Void> checkResult = validateApp(appDTO, EDIT);
        if (checkResult.failed()) {
            LOGGER.warn("class=AppServiceImpl||method=updateApp||msg={}||msg=check fail", checkResult.getMessage());
            return checkResult;
        }
        return editAppWithoutCheck(appDTO, operator);
    }

    /**
     * ??????APP
     * @param appId    APPID
     * @param operator ????????? ????????????
     * @return ?????? true  ?????? false
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> deleteAppById(int appId, String operator) {

        if (hasOwnLogicCluster(appId)) {
            return Result.build(ResultType.IN_USE_ERROR.getCode(), "APP??????????????????????????????");
        }

        if (hasOwnTemplate(appId)) {
            return Result.build(ResultType.IN_USE_ERROR.getCode(), "APP??????????????????????????????");
        }

        AppPO oldPO = appDAO.getById(appId);
        boolean succ = appDAO.delete(appId) == 1;
        if (succ) {
            operateRecordService.save(APP, DELETE, appId, "", operator);
            SpringTool.publish(new AppDeleteEvent(this, responsibleConvertTool.obj2Obj(oldPO, App.class)));
        }

        return Result.build(succ);
    }

    /**
     * ?????????APP??????
     *
     * @param appId APPID
     * @return ?????? true  ??????false
     */
    @Override
    public Result<Void> initConfig(Integer appId) {
        AppConfigPO param = new AppConfigPO();
        param.setAppId(appId);
        param.setDslAnalyzeEnable(AdminConstant.YES);
        param.setIsSourceSeparated(AdminConstant.NO);
        param.setAggrAnalyzeEnable(AdminConstant.YES);
        param.setAnalyzeResponseEnable(AdminConstant.YES);

        return Result.build(appConfigDAO.update(param) == 1);
    }

    /**
     * ??????appid????????????
     *
     * @param appId APPID
     * @return ????????????
     */
    @Override
    public AppConfig getAppConfig(int appId) {
        AppPO oldApp = appDAO.getById(appId);
        if (oldApp == null) {
            LOGGER.warn("class=AppServiceImpl||method=getConfig||appId={}||msg=appid not exist!", appId);
            return null;
        }

        AppConfigPO oldConfigPO = appConfigDAO.getByAppId(appId);
        if (oldConfigPO == null) {
            initConfig(appId);
            oldConfigPO = appConfigDAO.getByAppId(appId);
        }

        return responsibleConvertTool.obj2Obj(oldConfigPO, AppConfig.class);
    }

    /**
     * ???????????????????????????
     *
     * @return list
     */
    @Override
    public List<AppConfig> listConfig() {
        return ConvertUtil.list2List(appConfigDAO.listAll(), AppConfig.class);
    }

    @Override
    public List<AppConfig> listConfigWithCache() {
        try {
            return (List<AppConfig>) appListCache.get("listConfig", this::listConfig);
        } catch (ExecutionException e) {
            return listConfig();
        }
    }

    /**
     * ??????APP??????
     * @param configDTO ????????????
     * @param operator  ?????????
     * @return ?????? true  ??????  false
     * <p>
     * NotExistException
     * APP?????????
     * IllegalArgumentException
     * ???????????????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> updateAppConfig(AppConfigDTO configDTO, String operator) {
        Result<Void> checkResult = checkConfigParam(configDTO);
        if (checkResult.failed()) {
            LOGGER.warn("class=AppServiceImpl||method=updateConfig||msg={}||msg=check fail!", checkResult.getMessage());
            return checkResult;
        }

        AppPO oldApp = appDAO.getById(configDTO.getAppId());
        if (oldApp == null) {
            return Result.buildNotExist(APP_NOT_EXIST);
        }

        AppConfigPO oldConfigPO = appConfigDAO.getByAppId(configDTO.getAppId());

        boolean succ = (1 == appConfigDAO.update(ConvertUtil.obj2Obj(configDTO, AppConfigPO.class)));
        if (succ) {
            operateRecordService.save(APP_CONFIG, EDIT, configDTO.getAppId(),
                AriusObjUtils.findChangedWithClear(oldConfigPO, configDTO), operator);
        }

        return Result.build(succ);
    }

    /**
     * ??????appid????????????
     * @param appId ??????id
     * @return true/false
     */
    @Override
    public boolean isAppExists(Integer appId) {
        return appDAO.getById(appId) != null;
    }

    @Override
    public boolean isAppExists(App app) {
        return app != null;
    }

    @Override
    public boolean isSuperApp(Integer appId) {
        App appById = getAppById(appId);
        if (AriusObjUtils.isNull(appById)) {
            return false;
        }

        return appById.getIsRoot() == 1;
    }

    @Override
    public boolean isSuperApp(App app) {
        if (AriusObjUtils.isNull(app)) {
            return false;
        }

        return app.getIsRoot() == 1;
    }

    /**
     * ??????id??????
     * @param appId appID
     * @return app  ?????????????????????null
     */
    @Override
    public App getAppById(Integer appId) {
        return responsibleConvertTool.obj2Obj(appDAO.getById(appId), App.class);
    }

    @Override
    public String getAppName(Integer appId) {
        if (null == appId) { return null;}
        App app = getAppById(appId);
        return app == null ? null : app.getName();
    }

    /**
     * ??????????????????
     *
     * @param appId      appId
     * @param verifyCode ?????????
     * @param operator   ?????????
     * @return result
     */
    @Override
    public Result<Void> login(Integer appId, String verifyCode, String operator) {
        AppPO appPO = appDAO.getById(appId);

        if (appPO == null) {
            return Result.buildNotExist(APP_NOT_EXIST);
        }

        if (StringUtils.isBlank(verifyCode) || !appPO.getVerifyCode().equals(verifyCode)) {
            return Result.buildParamIllegal("???????????????");
        }

        if (StringUtils.isBlank(operator)) {
            return Result.buildParamIllegal("???????????????");
        }

        // ??????appid?????????????????????
        appUserInfoService.recordAppidAndUser(appId, operator);

        return Result.buildSucc();
    }

    /**
     * ???????????????
     *
     * @param appId     app
     * @param verifyCode ?????????
     * @return result
     */
    @Override
    public Result<Void> verifyAppCode(Integer appId, String verifyCode) {
        AppPO appPO = appDAO.getById(appId);

        if (appPO == null) {
            return Result.buildNotExist(APP_NOT_EXIST);
        }

        if (StringUtils.isBlank(verifyCode) || !appPO.getVerifyCode().equals(verifyCode)) {
            return Result.buildParamIllegal("???????????????");
        }

        return Result.buildSucc();
    }

    /**
     * ?????????????????????????????????APP??????,???????????????????????????
     *
     * @param user ?????????
     * @return appList
     */
    @Override
    public List<App> getUserLoginWithoutCodeApps(String user) {
        List<AppUserInfo> userInfos = appUserInfoService.getByUser(user);

        if (CollectionUtils.isEmpty(userInfos)) {
            return Lists.newArrayList();
        }

        List<Integer> appIds = userInfos.stream().map(AppUserInfo::getAppId).collect(Collectors.toList());
        List<App> apps = responsibleConvertTool.list2List(appDAO.listByIds(appIds), App.class);

        // ???????????????????????????????????????
        Map<Integer, AppUserInfo> appId2appUserInfoMap = ConvertUtil.list2Map(userInfos, AppUserInfo::getAppId);
        apps.sort((o1, o2) -> {
            AppUserInfo o1UserInfo = appId2appUserInfoMap.get(o1.getId());
            AppUserInfo o2UserInfo = appId2appUserInfoMap.get(o2.getId());
            return o2UserInfo.getLastLoginTime().compareTo(o1UserInfo.getLastLoginTime());
        });

        return apps;
    }

    /**
     * ?????????????????????
     *
     * @param responsible
     * @return
     */
    @Override
    public List<App> getAppsByResponsibleId(Long responsible) {
        return responsibleConvertTool.list2List(appDAO.listByResponsible(String.valueOf(responsible)), App.class);
    }

    @Override
    public App getAppByName(String name) {
        AppPO appPO = getByName(name);
        if (appPO == null) {
            return null;
        }
        return responsibleConvertTool.obj2Obj(appPO, App.class);
    }

    @Override
    public List<App> getAppsByLowestLogicClusterAuth(Long logicClusterId, AppClusterLogicAuthEnum logicClusterAuth) {
        if (logicClusterId == null || logicClusterAuth == null) {
            return new ArrayList<>();
        }

        // ??????????????????????????????????????????APP?????????
        if (logicClusterAuth == AppClusterLogicAuthEnum.NO_PERMISSIONS) {
            return listApps();
        }

        // ?????????????????????????????????????????????
        List<AppClusterLogicAuth> auths = logicClusterAuthService.getLogicClusterAuths(logicClusterId, null);
        // ?????????????????????????????????app
        List<Integer> appIds = auths.stream().filter(appLogicClusterAuth -> AppClusterLogicAuthEnum
            .valueOf(appLogicClusterAuth.getType()).higherOrEqual(logicClusterAuth))
            .map(AppClusterLogicAuth::getAppId).collect(Collectors.toList());

        if (CollectionUtils.isEmpty(appIds)) {
            return new ArrayList<>();
        }

        // ?????????????????????APP
        return responsibleConvertTool.list2List(appDAO.listByIds(appIds), App.class);
    }

    /**************************************** private method ****************************************************/
    private AppPO getByName(String name) {
        if (name == null) {
            return null;
        }

        List<AppPO> appPOs = appDAO.listByName(name);
        if (CollectionUtils.isEmpty(appPOs)) {
            return null;
        }
        return appPOs.get(0);
    }

    private void initParam(AppDTO appDTO) {
        // ????????????root??????
        if (appDTO.getIsRoot() == null) {
            appDTO.setIsRoot(AdminConstant.NO);
        }

        if (StringUtils.isBlank(appDTO.getDataCenter())) {
            appDTO.setDataCenter(EnvUtil.getDC().getCode());
        }

        // ??????cluster=""
        if (appDTO.getCluster() == null) {
            appDTO.setCluster("");
        }

        if (appDTO.getDepartmentId() == null) {
            appDTO.setDepartmentId("");
        }

        if (appDTO.getDepartment() == null) {
            appDTO.setDepartment("");
        }

        // ??????????????????
        if (appDTO.getSearchType() == null) {
            appDTO.setSearchType(AppSearchTypeEnum.TEMPLATE.getCode());
        }

        // ????????????????????????
        if (StringUtils.isBlank(appDTO.getVerifyCode())) {
            appDTO.setVerifyCode(VerifyCodeFactory.get(VERIFY_CODE_LENGTH));
        }

        // ???????????????????????????
        if (appDTO.getQueryThreshold() == null) {
            appDTO.setQueryThreshold(APP_QUERY_THRESHOLD_DEFAULT);
        }
    }

    /**
     * ??????APP  ?????????????????????????????????  ???validateApp????????????
     * @param appDTO   dto
     * @param operator ????????? ????????????
     * @return ?????? true  ?????? false
     */
    private Result<Integer> addAppWithoutCheck(AppDTO appDTO, String operator) {
        initParam(appDTO);

        AppPO param = responsibleConvertTool.obj2Obj(appDTO, AppPO.class);
        boolean succ = (appDAO.insert(param) == 1);
        if (succ) {
            // ????????????
            if (initConfig(param.getId()).failed()) {
                LOGGER.warn("class=AppServiceImpl||method=addAppWithoutCheck||appid={}||msg=initConfig fail",
                        param.getId());
            }
            // ????????????
            operateRecordService.save(APP, ADD, param.getId(), "", operator);
            appUserInfoService.recordAppidAndUser(param.getId(), appDTO.getResponsible());
            SpringTool.publish(
                    new AppAddEvent(this, responsibleConvertTool.obj2Obj(appDAO.getById(param.getId()), App.class)));
        }

        return Result.build(succ, param.getId());
    }

    /**
     * ??????APP ?????????????????????????????????  ???validateApp????????????
     * @param appDTO   dto
     * @param operator ????????? ????????????
     * @return ?????? true  ?????? false
     */
    private Result<Void> editAppWithoutCheck(AppDTO appDTO, String operator) {
        AppPO oldPO = appDAO.getById(appDTO.getId());
        AppPO param = responsibleConvertTool.obj2Obj(appDTO, AppPO.class);

        boolean succeed = (appDAO.update(param) == 1);
        if (succeed) {
            operateRecordService.save(APP, EDIT, appDTO.getId(), AriusObjUtils.findChangedWithClear(oldPO, param), operator);
            appUserInfoService.recordAppidAndUser(appDTO.getId(), appDTO.getResponsible());
            SpringTool.publish(new AppEditEvent(this, responsibleConvertTool.obj2Obj(oldPO, App.class),
                    responsibleConvertTool.obj2Obj(appDAO.getById(param.getId()), App.class)));
        }
        return Result.build(succeed);
    }


    private Result<Void> checkConfigParam(AppConfigDTO configDTO) {
        if (configDTO == null) {
            return Result.buildParamIllegal("??????????????????");
        }
        if (configDTO.getAppId() == null) {
            return Result.buildParamIllegal("??????ID??????");
        }
        if (configDTO.getAnalyzeResponseEnable() != null && !yesOrNo(configDTO.getAnalyzeResponseEnable())) {
            return Result.buildParamIllegal("??????????????????????????????");
        }
        if (configDTO.getDslAnalyzeEnable() != null && !yesOrNo(configDTO.getDslAnalyzeEnable())) {
            return Result.buildParamIllegal("DSL??????????????????");
        }
        if (configDTO.getAggrAnalyzeEnable() != null && !yesOrNo(configDTO.getAggrAnalyzeEnable())) {
            return Result.buildParamIllegal("????????????????????????");
        }
        if (configDTO.getIsSourceSeparated() != null && !yesOrNo(configDTO.getIsSourceSeparated())) {
            return Result.buildParamIllegal("??????????????????????????????");
        }

        return Result.buildSucc();
    }

    private boolean hasOwnTemplate(int appId) {
        List<IndexTemplateLogic> templateLogics = templateLogicService.getAppLogicTemplatesByAppId(appId);
        return CollectionUtils.isNotEmpty(templateLogics);
    }

    private boolean hasOwnLogicCluster(int appId) {
        List<ClusterLogic> clusterLogics = clusterLogicService.getOwnedClusterLogicListByAppId(appId);
        return CollectionUtils.isNotEmpty(clusterLogics);
    }

    private Result<Void> validateAppFieldIsNull(AppDTO appDTO) {
        if (appDTO.getMemo() == null) {
            return Result.buildParamIllegal("????????????");
        }
        return Result.buildSucc();
    }
}
