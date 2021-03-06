package com.didichuxing.datachannel.arius.admin.biz.app.impl;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.didichuxing.datachannel.arius.admin.biz.app.AppClusterPhyAuthManager;
import com.didichuxing.datachannel.arius.admin.biz.cluster.ClusterContextManager;
import com.didichuxing.datachannel.arius.admin.client.constant.app.AppClusterLogicAuthEnum;
import com.didichuxing.datachannel.arius.admin.client.constant.app.AppClusterPhyAuthEnum;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.app.App;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.app.AppClusterLogicAuth;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.app.AppClusterPhyAuth;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ClusterLogic;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ClusterLogicContext;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ClusterPhy;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ClusterPhyContext;
import com.didichuxing.datachannel.arius.admin.common.threadpool.AriusScheduleThreadPool;
import com.didichuxing.datachannel.arius.admin.common.util.AriusObjUtils;
import com.didichuxing.datachannel.arius.admin.common.util.ConvertUtil;
import com.didichuxing.datachannel.arius.admin.core.service.app.AppClusterLogicAuthService;
import com.didichuxing.datachannel.arius.admin.core.service.app.AppClusterPhyAuthService;
import com.didichuxing.datachannel.arius.admin.core.service.app.AppService;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.logic.ClusterLogicService;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.physic.ClusterPhyService;
import com.didiglobal.logi.log.ILog;
import com.didiglobal.logi.log.LogFactory;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * Created by linyunan on 2021-10-15
 */
@Component
public class AppClusterPhyAuthManagerImpl implements AppClusterPhyAuthManager {
    private static final ILog LOGGER = LogFactory.getLog(AppClusterPhyAuthManagerImpl.class);
    @Autowired
    private AppService                 appService;

    @Autowired
    private AppClusterPhyAuthService   appClusterPhyAuthService;

    @Autowired
    private AppClusterLogicAuthService appClusterLogicAuthService;

    @Autowired
    private ClusterContextManager      clusterContextManager;

    @Autowired
    private ClusterPhyService          clusterPhyService;

    @Autowired
    private ClusterLogicService        clusterLogicService;

    @Autowired
    private AriusScheduleThreadPool    ariusScheduleThreadPool;

    private static final Map<Integer/*appId*/, List<AppClusterPhyAuth> /*AppClusterPhyAuthList*/> appId2AppClusterPhyAuthListMap = Maps.newConcurrentMap();

    @PostConstruct
    private void init(){
        ariusScheduleThreadPool.submitScheduleAtFixedDelayTask(this::refreshAppClusterPhyAuth,60, 120);
    }

    @Override
    public List<AppClusterPhyAuth> getByClusterPhyListAndAppIdFromCache(Integer appId,
                                                                        List<ClusterPhy> clusterPhyList) {
        if (null == appId || CollectionUtils.isEmpty(clusterPhyList)) { return Lists.newArrayList();}
        
        List<AppClusterPhyAuth> appClusterPhyAuthList = appId2AppClusterPhyAuthListMap.get(appId);
        if (CollectionUtils.isEmpty(appClusterPhyAuthList)) {
            return buildInitAppClusterPhyAuth(clusterPhyList);
        }

        return appClusterPhyAuthList;
    }

    private List<AppClusterPhyAuth> buildInitAppClusterPhyAuth(List<ClusterPhy> clusterPhyList) {
        List<AppClusterPhyAuth>  initAppClusterPhyAuthList = Lists.newArrayList();
        for (ClusterPhy clusterPhy : clusterPhyList) {
            AppClusterPhyAuth appClusterPhyAuth = new AppClusterPhyAuth();
            appClusterPhyAuth.setClusterPhyName(clusterPhy.getCluster());
            appClusterPhyAuth.setType(AppClusterLogicAuthEnum.NO_PERMISSIONS.getCode());
            initAppClusterPhyAuthList.add(appClusterPhyAuth);
        }

        return initAppClusterPhyAuthList;
    }

    @Override
    public List<AppClusterPhyAuth> getAppAccessClusterPhyAuths(Integer appId) {
        List<AppClusterPhyAuth> appAccessClusterPhyAuthList = Lists.newArrayList();
        List<AppClusterLogicAuth> logicClusterAccessAuths   = appClusterLogicAuthService.getLogicClusterAccessAuths(appId);
        if (CollectionUtils.isEmpty(logicClusterAccessAuths)) {
            return appAccessClusterPhyAuthList;
        }
        
        List<List<String>> clusterPhyLists = logicClusterAccessAuths
                                            .stream()
                                            .map(AppClusterLogicAuth::getLogicClusterId)
                                            .map(clusterContextManager::getClusterLogicContext)
                                            .filter(Objects::nonNull)
                                            .map(ClusterLogicContext::getAssociatedClusterPhyNames)
                                            .collect(Collectors.toList());
        
        for (List<String> clusterPhyList : clusterPhyLists) {
            clusterPhyList.forEach(clusterPhy -> {
                AppClusterPhyAuth appClusterPhyAuth = appClusterPhyAuthService.buildClusterPhyAuth(appId, clusterPhy,
                                                      AppClusterPhyAuthEnum.ACCESS);
                appAccessClusterPhyAuthList.add(appClusterPhyAuth);
            });
        }
        
        return appAccessClusterPhyAuthList;
    }

    /**
     * ????????????????????????
     */
    private void refreshAppClusterPhyAuth() {
        long currentTimeMillis = System.currentTimeMillis();
        LOGGER.info("class=AppClusterPhyAuthManagerImpl||method=refreshAppClusterPhyAuth||msg=start...");

        Map<String, ClusterPhyContext> name2clusterPhyContextMap = clusterContextManager.listClusterPhyContextMap();
        if (MapUtils.isEmpty(name2clusterPhyContextMap)) { return;}

        List<App> appList = appService.listApps();
        if (CollectionUtils.isEmpty(appList)) { return;}

        List<ClusterPhy> esClusterPhyList = clusterPhyService.listClustersByCondt(null);
        if (CollectionUtils.isEmpty(esClusterPhyList)) { return;}

        List<ClusterLogic> clusterLogicList = clusterLogicService.listAllClusterLogics();

        Map<Long, ClusterLogic> id2ClusterLogicMap = ConvertUtil.list2Map(clusterLogicList, ClusterLogic::getId);

        List<AppClusterLogicAuth> appClusterLogicAuthList= appClusterLogicAuthService.list();
        // ???????????????key appId@clusterLogiId
        Map<String, AppClusterLogicAuth> key2AppClusterLogicAuthMap = ConvertUtil.list2Map(appClusterLogicAuthList,
                                                a -> a.getAppId() + "@" + a.getLogicClusterId());

        for (App app : appList) {
            // ??????app??????????????????
            int appId = app.getId();
            List<AppClusterPhyAuth> appClusterPhyAuthList = Lists.newArrayList();
            if (app.getIsRoot() == 1) {
                appClusterPhyAuthList = esClusterPhyList.stream()
                        .map(r -> buildClusterPhyAuth(appId, r.getCluster(), AppClusterPhyAuthEnum.OWN))
                        .collect(Collectors.toList());
                appId2AppClusterPhyAuthListMap.put(app.getId(), appClusterPhyAuthList);
                continue;
            }
            
            // ???????????????app?????????????????????
            for (ClusterPhy clusterPhy : esClusterPhyList) {
                ClusterPhyContext clusterPhyContext = name2clusterPhyContextMap.get(clusterPhy.getCluster());
                if (null == clusterPhyContext) {
                    appClusterPhyAuthList.add(buildClusterPhyAuth(appId, clusterPhy.getCluster(), AppClusterPhyAuthEnum.NO_PERMISSIONS));
                    continue;
                }

                List<Long> associatedClusterLogicIds        =   clusterPhyContext.getAssociatedClusterLogicIds();
                if (CollectionUtils.isEmpty(associatedClusterLogicIds)) {
                    appClusterPhyAuthList.add(buildClusterPhyAuth(appId, clusterPhy.getCluster(), AppClusterPhyAuthEnum.NO_PERMISSIONS));
                    continue;
                }

                //  ???????????????????????????????????????????????????
                Set<Integer> appClusterLogicAuthTypeSet = Sets.newHashSet();
                for (Long associatedClusterLogicId : associatedClusterLogicIds) {
                    // ????????????????????????????????????
                    ClusterLogic clusterLogic = id2ClusterLogicMap.get(associatedClusterLogicId);
                    AppClusterLogicAuthEnum authFromCreateRecord = (clusterLogic != null && appId == clusterLogic.getAppId())
                            ? AppClusterLogicAuthEnum.OWN
                            : AppClusterLogicAuthEnum.NO_PERMISSIONS;

                    // ??????????????????????????????
                    AppClusterLogicAuth authPO = key2AppClusterLogicAuthMap.get(appId + "@" + clusterLogic);
                    AppClusterLogicAuthEnum authFromAuthRecord = authPO != null ? AppClusterLogicAuthEnum.valueOf(authPO.getType()) :
                            AppClusterLogicAuthEnum.NO_PERMISSIONS;

                    // ???????????????
                    if (authFromCreateRecord == AppClusterLogicAuthEnum.NO_PERMISSIONS
                            && authFromAuthRecord == AppClusterLogicAuthEnum.NO_PERMISSIONS) {
                        AppClusterLogicAuth appClusterLogicAuth = buildLogicClusterAuth(clusterLogic, AppClusterLogicAuthEnum.NO_PERMISSIONS);
                        appClusterLogicAuthTypeSet.add(null == appClusterLogicAuth ? AppClusterLogicAuthEnum.NO_PERMISSIONS.getCode() :
                                appClusterLogicAuth.getType());
                        continue;
                    }

                    AppClusterLogicAuth appClusterLogicAuth = authFromAuthRecord.higherOrEqual(authFromCreateRecord)
                            ? ConvertUtil.obj2Obj(authPO, AppClusterLogicAuth.class)
                            : buildLogicClusterAuth(clusterLogic, AppClusterLogicAuthEnum.OWN);

                    appClusterLogicAuthTypeSet.add(null == appClusterLogicAuth ? AppClusterLogicAuthEnum.NO_PERMISSIONS.getCode() :
                            appClusterLogicAuth.getType());
                }

                // ????????????
                AppClusterPhyAuthEnum appClusterPhyAuthEnum = computeAppClusterPhyAuthEnum(appClusterLogicAuthTypeSet);
                appClusterPhyAuthList.add(buildClusterPhyAuth(appId, clusterPhy.getCluster(), appClusterPhyAuthEnum));
            }
            appId2AppClusterPhyAuthListMap.put(app.getId(), appClusterPhyAuthList);
        }

        LOGGER.info("class=AppClusterPhyAuthManagerImpl||method=refreshAppClusterPhyAuth||msg=finish...||consumingTime={}",
                System.currentTimeMillis() - currentTimeMillis);
    }

    private AppClusterPhyAuth buildClusterPhyAuth(Integer appId, String clusterPhyName, AppClusterPhyAuthEnum appClusterPhyAuthEnum) {
        if (null == appClusterPhyAuthEnum || null == appId || AriusObjUtils.isBlack(clusterPhyName)) {
            return null;
        }

        if (!AppClusterPhyAuthEnum.isExitByCode(appClusterPhyAuthEnum.getCode())) {
            return null;
        }

        AppClusterPhyAuth appClusterPhyAuth = new AppClusterPhyAuth();
        appClusterPhyAuth.setAppId(appId);
        appClusterPhyAuth.setClusterPhyName(clusterPhyName);
        appClusterPhyAuth.setType(appClusterPhyAuthEnum.getCode());
        return appClusterPhyAuth;
    }

    /**
     * ???????????????????????????owner APP???????????????
     * @param clusterLogic ??????????????????
     */
    private AppClusterLogicAuth buildLogicClusterAuth(ClusterLogic clusterLogic, AppClusterLogicAuthEnum appClusterLogicAuthEnum) {
        if (clusterLogic == null) { return null;}

        AppClusterLogicAuth appLogicClusterAuth = new AppClusterLogicAuth();
        appLogicClusterAuth.setId(null);
        appLogicClusterAuth.setAppId(clusterLogic.getAppId());
        appLogicClusterAuth.setLogicClusterId(clusterLogic.getId());
        appLogicClusterAuth.setType(appClusterLogicAuthEnum.getCode());
        appLogicClusterAuth.setResponsible(clusterLogic.getResponsible());
        return appLogicClusterAuth;
    }

    /**
     * ???????????????????????????????????????????????????
     * @param appClusterLogicAuthTypeSet  ????????????????????????
     * @return AppClusterPhyAuthEnum
     */
    private AppClusterPhyAuthEnum computeAppClusterPhyAuthEnum(Set<Integer> appClusterLogicAuthTypeSet) {
        if(appClusterLogicAuthTypeSet.contains(AppClusterPhyAuthEnum.NO_PERMISSIONS.getCode())) {
            return AppClusterPhyAuthEnum.NO_PERMISSIONS;
        }

        // 0 ??????  1 ?????? 2 ??????
        int max = 0;
        for (Integer authCode : appClusterLogicAuthTypeSet) {
            max = authCode > max ? authCode : max;
        }

        return AppClusterPhyAuthEnum.valueOf(max);
    }
}
