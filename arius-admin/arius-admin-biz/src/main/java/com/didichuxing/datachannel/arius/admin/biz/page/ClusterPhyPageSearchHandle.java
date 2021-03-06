package com.didichuxing.datachannel.arius.admin.biz.page;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.didichuxing.datachannel.arius.admin.biz.cluster.ClusterPhyManager;
import com.didichuxing.datachannel.arius.admin.client.bean.common.PaginationResult;
import com.didichuxing.datachannel.arius.admin.client.bean.common.Result;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.PageDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.cluster.ClusterPhyConditionDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.vo.cluster.ConsoleClusterPhyVO;
import com.didichuxing.datachannel.arius.admin.client.constant.app.AppClusterPhyAuthEnum;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ClusterPhy;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ecm.RoleCluster;
import com.didichuxing.datachannel.arius.admin.common.constant.SortTermEnum;
import com.didichuxing.datachannel.arius.admin.common.constant.cluster.ClusterHealthEnum;
import com.didichuxing.datachannel.arius.admin.common.util.AriusObjUtils;
import com.didichuxing.datachannel.arius.admin.common.util.ConvertUtil;
import com.didichuxing.datachannel.arius.admin.common.util.FutureUtil;
import com.didichuxing.datachannel.arius.admin.core.service.app.AppService;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.physic.ClusterPhyService;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.physic.RoleClusterService;
import com.didiglobal.logi.log.ILog;
import com.didiglobal.logi.log.LogFactory;
import com.google.common.collect.Lists;

/**
 * Created by linyunan on 2021-10-14
 */
@Component
public class ClusterPhyPageSearchHandle extends BasePageSearchHandle<ConsoleClusterPhyVO> {

    private static final ILog        LOGGER = LogFactory.getLog(ClusterPhyPageSearchHandle.class);

    @Autowired
    private AppService               appService;

    @Autowired
    private ClusterPhyService        clusterPhyService;

    @Autowired
    private ClusterPhyManager        clusterPhyManager;

    @Autowired
    private RoleClusterService       roleClusterService;

    private static final FutureUtil<Void> futureUtil = FutureUtil.init("ClusterPhyPageSearchHandle",20, 40, 100);

    @Override
    protected Result<Boolean> validCheckForAppId(Integer appId) {
        if (!appService.isAppExists(appId)) {
            return Result.buildParamIllegal("???????????????");
        }
        return Result.buildSucc(true);
    }

    @Override
    protected Result<Boolean> validCheckForCondition(PageDTO pageDTO, Integer appId) {
        if (pageDTO instanceof ClusterPhyConditionDTO) {
            ClusterPhyConditionDTO clusterPhyConditionDTO = (ClusterPhyConditionDTO) pageDTO;
            Integer authType = clusterPhyConditionDTO.getAuthType();
            if (null != authType && !AppClusterPhyAuthEnum.isExitByCode(authType)) {
                return Result.buildParamIllegal("?????????????????????");
            }

            Integer status = clusterPhyConditionDTO.getHealth();
            if (null != status && !ClusterHealthEnum.isExitByCode(status)) {
                return Result.buildParamIllegal("???????????????????????????");
            }

            String clusterPhyName = clusterPhyConditionDTO.getCluster();
            if (!AriusObjUtils.isBlack(clusterPhyName) && (clusterPhyName.startsWith("*") || clusterPhyName.startsWith("?"))) {
                return Result.buildParamIllegal("????????????????????????????????????*, ???????????????????");
            }

            if (null != clusterPhyConditionDTO.getSortTerm() && !SortTermEnum.isExit(clusterPhyConditionDTO.getSortTerm())) {
                return Result.buildParamIllegal(String.format("???????????????????????????[%s]", clusterPhyConditionDTO.getSortTerm()));
            }

            return Result.buildSucc(true);
        }

        LOGGER.error("class=ClusterPhyPageSearchHandle||method=validCheckForCondition||errMsg=failed to convert PageDTO to ClusterPhyConditionDTO");

        return Result.buildFail();
    }

    @Override
    protected void init(PageDTO pageDTO) {
        // Do nothing
    }

    @Override
    protected PaginationResult<ConsoleClusterPhyVO> buildWithAuthType(PageDTO pageDTO, Integer authType, Integer appId) {
        ClusterPhyConditionDTO condition = buildClusterPhyConditionDTO(pageDTO);
        
        // 1. ????????????/??????/???/??????????????????????????????
        List<ClusterPhy> appAuthClusterPhyList = clusterPhyManager.getClusterPhyByAppIdAndAuthType(appId, condition.getAuthType());
        if (CollectionUtils.isEmpty(appAuthClusterPhyList)) {
            return PaginationResult.buildSucc(null, 0, condition.getPage(), condition.getSize());
        }

        // 2. ??????????????????????????????
        List<ClusterPhy> meetConditionClusterPhyList = getMeetConditionClusterPhyList(condition, appAuthClusterPhyList);

        // 3. ???????????????
        long hitTotal = meetConditionClusterPhyList.size();

        List<ConsoleClusterPhyVO> meetConditionClusterPhyListVOList = ConvertUtil.list2List(meetConditionClusterPhyList, ConsoleClusterPhyVO.class);
        
        // 4. ?????????????????????????????????id????????????, ????????????????????????????????????????????????id
        sort(meetConditionClusterPhyListVOList, condition.getSortTerm(), condition.getOrderByDesc());

        // 5. ????????????
        List<ConsoleClusterPhyVO> fuzzyAndLimitConsoleClusterPhyVOList  = filterFullDataByPage(meetConditionClusterPhyListVOList, pageDTO);

        // 6. ????????????
        fuzzyAndLimitConsoleClusterPhyVOList.forEach(consoleClusterPhyVO -> consoleClusterPhyVO.setCurrentAppAuth(condition.getAuthType()));

        // 7.??????????????????????????????????????????AppId
        fuzzyAndLimitConsoleClusterPhyVOList.forEach(consoleClusterPhyVO -> clusterPhyManager.buildBelongAppIdsAndNames(consoleClusterPhyVO));

        // 8. ????????????????????????
        List<Integer> clusterIds = fuzzyAndLimitConsoleClusterPhyVOList.stream().map(ConsoleClusterPhyVO::getId).collect(Collectors.toList());
        Map<Long, List<RoleCluster>> roleListMap = roleClusterService.getAllRoleClusterByClusterIds(clusterIds);

        for (ConsoleClusterPhyVO consoleClusterPhyVO : fuzzyAndLimitConsoleClusterPhyVOList) {
            futureUtil.runnableTask(() -> clusterPhyManager.buildClusterRole(consoleClusterPhyVO,
                    roleListMap.get(consoleClusterPhyVO.getId().longValue())));
        }
        futureUtil.waitExecute();
        
        return PaginationResult.buildSucc(fuzzyAndLimitConsoleClusterPhyVOList, hitTotal, condition.getPage(), condition.getSize());
    }

    @Override
    protected PaginationResult<ConsoleClusterPhyVO> buildWithoutAuthType(PageDTO pageDTO, Integer appId) {
        ClusterPhyConditionDTO condition = buildClusterPhyConditionDTO(pageDTO);

        List<ClusterPhy> pagingGetClusterPhyList      =  clusterPhyService.pagingGetClusterPhyByCondition(condition);

        List<ConsoleClusterPhyVO> consoleClusterPhyVOList = clusterPhyManager.buildClusterInfo(pagingGetClusterPhyList, appId);

        long totalHit = clusterPhyService.fuzzyClusterPhyHitByCondition(condition);
        return PaginationResult.buildSucc(consoleClusterPhyVOList, totalHit, condition.getPage(), condition.getSize());
    }
    
    /****************************************private***********************************************/
    private ClusterPhyConditionDTO buildClusterPhyConditionDTO(PageDTO pageDTO) {
        if (pageDTO instanceof ClusterPhyConditionDTO) {
            return (ClusterPhyConditionDTO) pageDTO;
        }
        return null;
    }

    /**
     * 3?????????????????????????????????????????????????????????????????? ???7?????????
     *
     * @param condition               ????????????
     * @param appAuthClusterPhyList   ????????????????????????????????????
     * @return
     */
    private List<ClusterPhy> getMeetConditionClusterPhyList(ClusterPhyConditionDTO condition, List<ClusterPhy> appAuthClusterPhyList) {
        List<ClusterPhy> meetConditionClusterPhyList = Lists.newArrayList();

        //??????????????????????????????????????????
        if (!AriusObjUtils.isBlack(condition.getCluster())) {
            appAuthClusterPhyList = appAuthClusterPhyList
                                  .stream()
                                  .filter(r -> r.getCluster().contains(condition.getCluster()))
                                  .collect(Collectors.toList());
        }

        //??????????????????????????????????????????
        if (null != condition.getHealth()) {
            appAuthClusterPhyList = appAuthClusterPhyList
                                .stream()
                                .filter(r -> r.getHealth().equals(condition.getHealth()))
                                .collect(Collectors.toList());
        }

        //????????????????????????????????????
        if (!AriusObjUtils.isBlack(condition.getEsVersion())) {
            appAuthClusterPhyList = appAuthClusterPhyList
                                .stream()
                                .filter(r -> r.getEsVersion().equals(condition.getEsVersion()))
                                .collect(Collectors.toList());
        }
        meetConditionClusterPhyList.addAll(appAuthClusterPhyList);
        return meetConditionClusterPhyList;
    }

    /**
     * ??????????????????????????????????????????
     * @param meetConditionClusterPhyListVOList      ?????????????????????
     * @param sortTerm                               ????????????
     * @see   SortTermEnum                           ???????????????????????????
     * @param orderByDesc                            ?????????????????? true ??? false ???
     */
    private void sort(List<ConsoleClusterPhyVO> meetConditionClusterPhyListVOList, String sortTerm, Boolean orderByDesc) {
        // ??????????????????
        if (null == sortTerm) {
            Collections.sort(meetConditionClusterPhyListVOList);
            return;
        }

        meetConditionClusterPhyListVOList.sort((o1, o2) -> {
            // ?????????????????????????????????
            if (SortTermEnum.DISK_FREE_PERCENT.getType().equals(sortTerm)) {
                return orderByDesc ? o2.getDiskUsagePercent().compareTo(o1.getDiskUsagePercent()) :
                        o1.getDiskUsagePercent().compareTo(o2.getDiskUsagePercent());
            }


            if (SortTermEnum.ACTIVE_SHARD_NUM.getType().equals(sortTerm)) {
                return orderByDesc ? o2.getActiveShardNum().compareTo(o1.getActiveShardNum()) :
                        o1.getActiveShardNum().compareTo(o2.getActiveShardNum());
            }

            // 0 ???????????? 
            return 0;
        });
    }
}
