package com.didichuxing.datachannel.arius.admin.biz.page;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.didichuxing.datachannel.arius.admin.biz.app.AppClusterLogicAuthManager;
import com.didichuxing.datachannel.arius.admin.biz.cluster.ClusterContextManager;
import com.didichuxing.datachannel.arius.admin.biz.cluster.ClusterLogicManager;
import com.didichuxing.datachannel.arius.admin.client.bean.common.PaginationResult;
import com.didichuxing.datachannel.arius.admin.client.bean.common.Result;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.PageDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.cluster.ClusterLogicConditionDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.vo.cluster.ConsoleClusterVO;
import com.didichuxing.datachannel.arius.admin.client.constant.app.AppClusterLogicAuthEnum;
import com.didichuxing.datachannel.arius.admin.client.constant.resource.ResourceLogicTypeEnum;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.app.App;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.app.AppClusterLogicAuth;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ClusterLogic;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ClusterLogicContext;
import com.didichuxing.datachannel.arius.admin.common.constant.SortTermEnum;
import com.didichuxing.datachannel.arius.admin.common.constant.cluster.ClusterHealthEnum;
import com.didichuxing.datachannel.arius.admin.common.util.AriusObjUtils;
import com.didichuxing.datachannel.arius.admin.common.util.ConvertUtil;
import com.didichuxing.datachannel.arius.admin.common.util.FutureUtil;
import com.didichuxing.datachannel.arius.admin.core.service.app.AppService;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.logic.ClusterLogicService;
import com.didiglobal.logi.log.ILog;
import com.didiglobal.logi.log.LogFactory;
import com.google.common.collect.Lists;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by linyunan on 2021-10-14
 */
@Component
public class ClusterLogicPageSearchHandle extends BasePageSearchHandle<ConsoleClusterVO> {

    private static final ILog          LOGGER = LogFactory.getLog(ClusterLogicPageSearchHandle.class);

    @Autowired
    private AppService                 appService;

    @Autowired
    private ClusterLogicService        clusterLogicService;

    @Autowired
    private ClusterLogicManager        clusterLogicManager;

    @Autowired
    private AppClusterLogicAuthManager appClusterLogicAuthManager;

    @Autowired
    private ClusterContextManager      clusterContextManager;

    private static final FutureUtil<Void> futureUtilForClusterNum      = FutureUtil.init("futureUtilForClusterNum",10,10,100);

    @Override
    protected Result<Boolean> validCheckForAppId(Integer appId) {
        if (!appService.isAppExists(appId)) {
            return Result.buildParamIllegal("???????????????");
        }
        return Result.buildSucc(true);
    }

    @Override
    protected Result<Boolean> validCheckForCondition(PageDTO pageDTO, Integer appId) {
        if (pageDTO instanceof ClusterLogicConditionDTO) {
            ClusterLogicConditionDTO clusterLogicConditionDTO = (ClusterLogicConditionDTO) pageDTO;
            Integer authType = clusterLogicConditionDTO.getAuthType();
            if (null != authType && !AppClusterLogicAuthEnum.isExitByCode(authType)) {
                return Result.buildParamIllegal("?????????????????????");
            }

            Integer status = clusterLogicConditionDTO.getHealth();
            if (null != status && !ClusterHealthEnum.isExitByCode(status)) {
                return Result.buildParamIllegal("?????????????????????????????????");
            }

            if (null != clusterLogicConditionDTO.getType()
                    && !ResourceLogicTypeEnum.isExist(clusterLogicConditionDTO.getType())) {
                return Result.buildParamIllegal("???????????????????????????");
            }

            if (null != clusterLogicConditionDTO.getAppId()
                    && !appService.isAppExists(clusterLogicConditionDTO.getAppId())) {
                return Result.buildParamIllegal("?????????????????????????????????");
            }

            String clusterLogicName = clusterLogicConditionDTO.getName();
            if (!AriusObjUtils.isBlack(clusterLogicName) && (clusterLogicName.startsWith("*") || clusterLogicName.startsWith("?"))) {
                return Result.buildParamIllegal("????????????????????????????????????*, ???????????????????");
            }

            return Result.buildSucc(true);
        }

        LOGGER.error("class=ClusterLogicPageSearchHandle||method=validCheckForCondition||errMsg=failed to convert PageDTO to ClusterLogicConditionDTO");

        return Result.buildFail();
    }

    @Override
    protected void init(PageDTO pageDTO) {
        // Do nothing
    }

    @Override
    protected PaginationResult<ConsoleClusterVO> buildWithAuthType(PageDTO pageDTO, Integer authType, Integer appId) {
        ClusterLogicConditionDTO condition = buildClusterLogicConditionDTO(pageDTO);

        //1. ????????????/??????/??????????????????????????????
        List<ClusterLogic> appAuthClusterLogicList = clusterLogicManager.getClusterLogicByAppIdAndAuthType(appId, condition.getAuthType());
        if (CollectionUtils.isEmpty(appAuthClusterLogicList)) {
            return PaginationResult.buildSucc(null, 0, condition.getPage(), condition.getSize());
        }

        //2. ??????????????????????????????
        List<ClusterLogic> meetConditionClusterLogicList = getMeetConditionClusterLogicList(condition, appAuthClusterLogicList);

        //3. ???????????????
        long hitTotal = meetConditionClusterLogicList.size();

        //4. ??????????????????????????????????????????
        sort(meetConditionClusterLogicList, condition.getSortTerm(), condition.getOrderByDesc());

        //5.????????????
        List<ClusterLogic> fuzzyAndLimitClusterPhyList  = filterFullDataByPage(meetConditionClusterLogicList, condition) ;
        List<ConsoleClusterVO> consoleClusterVOList     = ConvertUtil.list2List(fuzzyAndLimitClusterPhyList, ConsoleClusterVO.class);

        //6. ????????????????????????
        consoleClusterVOList.forEach(consoleClusterVO -> consoleClusterVO.setAuthType(condition.getAuthType()));

        //7. ??????????????????????????????
        for (ConsoleClusterVO consoleClusterVO : consoleClusterVOList) {
            futureUtilForClusterNum.runnableTask(() -> setConsoleClusterBasicInfo(consoleClusterVO));
        }
        futureUtilForClusterNum.waitExecute();

        return PaginationResult.buildSucc(consoleClusterVOList, hitTotal, condition.getPage(), condition.getSize());
    }

    @Override
    protected PaginationResult<ConsoleClusterVO> buildWithoutAuthType(PageDTO pageDTO, Integer appId) {
        ClusterLogicConditionDTO condition = buildClusterLogicConditionDTO(pageDTO);
        
        List<ClusterLogic> pagingGetClusterLogicList   =  clusterLogicService.pagingGetClusterLogicByCondition(condition);
        List<ConsoleClusterVO> consoleClusterPhyVOList =  doBuildWithoutAuthType(pagingGetClusterLogicList, appId);

        long totalHit = clusterLogicService.fuzzyClusterLogicHitByCondition(condition);

        return PaginationResult.buildSucc(consoleClusterPhyVOList, totalHit, pageDTO.getPage(), pageDTO.getSize());
    }

    private List<ConsoleClusterVO> doBuildWithoutAuthType(List<ClusterLogic> clusterLogicList, Integer appId) {
        if (CollectionUtils.isEmpty(clusterLogicList)) {
            return Lists.newArrayList();
        }

        //??????????????????????????????????????????
        List<AppClusterLogicAuth> appClusterLogicAuthList = appClusterLogicAuthManager.getByClusterLogicListAndAppId(appId, clusterLogicList);
        Map<Long, AppClusterLogicAuth> clusterLogicId2AppClusterLogicAuthMap = ConvertUtil.list2Map(appClusterLogicAuthList,
                AppClusterLogicAuth::getLogicClusterId);

        List<ConsoleClusterVO> consoleClusterVOList = ConvertUtil.list2List(clusterLogicList, ConsoleClusterVO.class);
        //1. ????????????
        for (ConsoleClusterVO consoleClusterVO : consoleClusterVOList) {
            AppClusterLogicAuth appClusterLogicAuth = clusterLogicId2AppClusterLogicAuthMap.get(consoleClusterVO.getId());
            if (appClusterLogicAuth == null) {
                continue;
            }
            consoleClusterVO.setAuthType(appClusterLogicAuth.getType());
            consoleClusterVO.setAuthId(appClusterLogicAuth.getId());
        }

        //2. ??????????????????
        for (ConsoleClusterVO consoleClusterVO : consoleClusterVOList) {
            futureUtilForClusterNum.runnableTask(() -> setConsoleClusterBasicInfo(consoleClusterVO));
        }
        futureUtilForClusterNum.waitExecute();

        return consoleClusterVOList;
    }

    private ClusterLogicConditionDTO buildClusterLogicConditionDTO(PageDTO pageDTO) {
        if (pageDTO instanceof ClusterLogicConditionDTO) {
            return (ClusterLogicConditionDTO) pageDTO;
        }
        return null;
    }

    private List<ClusterLogic> getMeetConditionClusterLogicList(ClusterLogicConditionDTO condition,
                                                              List<ClusterLogic> appAuthClusterLogicList) {
        List<ClusterLogic> meetConditionClusterLogicList = Lists.newArrayList();

        //??????????????????????????????????????????
        if (!AriusObjUtils.isBlack(condition.getName())) {
            appAuthClusterLogicList = appAuthClusterLogicList
                    .stream()
                    .filter(r -> r.getName().contains(condition.getName()))
                    .collect(Collectors.toList());
        }

        //??????????????????????????????????????????
        if (null != condition.getType()) {
            appAuthClusterLogicList = appAuthClusterLogicList
                    .stream()
                    .filter(r -> r.getType().equals(condition.getType()))
                    .collect(Collectors.toList());
        }

        //??????????????????????????????????????????
        if (null != condition.getHealth()) {
            appAuthClusterLogicList = appAuthClusterLogicList
                    .stream()
                    .filter(r -> r.getHealth().equals(condition.getHealth()))
                    .collect(Collectors.toList());
        }

        //????????????????????????????????????Id
        if (null != condition.getAppId()) {
            appAuthClusterLogicList = appAuthClusterLogicList
                    .stream()
                    .filter(r -> r.getAppId().equals(condition.getAppId()))
                    .collect(Collectors.toList());
        }
        meetConditionClusterLogicList.addAll(appAuthClusterLogicList);
        return meetConditionClusterLogicList;
    }

    /**
     * 1. ??????????????????
     * 2. ????????????????????????
     * 3. ????????????
     * @param consoleClusterVO   ?????????????????????
     */
    private void setConsoleClusterBasicInfo(ConsoleClusterVO consoleClusterVO) {
        if (null == consoleClusterVO) {
            return;
        }
        setResponsible(consoleClusterVO);
        setAppName(consoleClusterVO);
        setClusterPhyFlagAndDataNodeNum(consoleClusterVO);
    }

    private void setResponsible(ConsoleClusterVO consoleClusterVO) {
        ClusterLogic clusterLogic = clusterLogicService.getClusterLogicById(consoleClusterVO.getId());
        if(clusterLogic == null) {
            return;
        }
        consoleClusterVO.setResponsible(clusterLogic.getResponsible());
    }

    private void setClusterPhyFlagAndDataNodeNum(ConsoleClusterVO consoleClusterVO) {
        ClusterLogicContext clusterLogicContext = clusterContextManager.getClusterLogicContext(consoleClusterVO.getId());
        if (null == clusterLogicContext || CollectionUtils.isEmpty(clusterLogicContext.getAssociatedClusterPhyNames())) {
            consoleClusterVO.setPhyClusterAssociated(false);
            consoleClusterVO.setDataNodesNumber(0);
        } else {
            consoleClusterVO.setPhyClusterAssociated(true);
            consoleClusterVO.setDataNodesNumber(clusterLogicContext.getAssociatedDataNodeNum());
        }
    }

    private void setAppName(ConsoleClusterVO consoleClusterVO) {
        App app = appService.getAppById(consoleClusterVO.getAppId());
        if (null != app && !AriusObjUtils.isBlack(app.getName())) {
            consoleClusterVO.setAppName(app.getName());
        }
    }


    /**
     * ??????????????????????????????????????????
     * @param meetConditionClusterLogicList  ?????????????????????
     * @param sortTerm                       ????????????
     * @see   SortTermEnum                   ???????????????????????????
     * @param orderByDesc                    ?????????????????? true ??? false ???
     */
    private void sort(List<ClusterLogic> meetConditionClusterLogicList, String sortTerm, Boolean orderByDesc) {
        // TODO: ??????????????????
        // ??????????????????
        if (null == sortTerm) {
            Collections.sort(meetConditionClusterLogicList);
            return;
        }

        meetConditionClusterLogicList.sort((o1, o2) -> {
            // ?????????????????????????????????
            if (SortTermEnum.LEVEL.getType().equals(sortTerm)) {
                return orderByDesc ? o2.getLevel().compareTo(o1.getLevel()) : o1.getLevel().compareTo(o2.getLevel());
            }

            // ??????0 ?????????
            return 0;
        });
    }
}
