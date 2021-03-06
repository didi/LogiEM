package com.didichuxing.datachannel.arius.admin.biz.page;

import static com.didichuxing.datachannel.arius.admin.client.constant.app.AppTemplateAuthEnum.isTemplateAuthExitByCode;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.didichuxing.datachannel.arius.admin.biz.app.AppLogicTemplateAuthManager;
import com.didichuxing.datachannel.arius.admin.biz.template.TemplateLogicManager;
import com.didichuxing.datachannel.arius.admin.client.bean.common.PaginationResult;
import com.didichuxing.datachannel.arius.admin.client.bean.common.Result;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.PageDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.template.TemplateConditionDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.vo.template.ConsoleTemplateVO;
import com.didichuxing.datachannel.arius.admin.client.constant.template.DataTypeEnum;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.app.AppTemplateAuth;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.template.IndexTemplateConfig;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.template.IndexTemplateLogic;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.template.IndexTemplatePhy;
import com.didichuxing.datachannel.arius.admin.common.constant.SortTermEnum;
import com.didichuxing.datachannel.arius.admin.common.util.AriusObjUtils;
import com.didichuxing.datachannel.arius.admin.common.util.ConvertUtil;
import com.didichuxing.datachannel.arius.admin.common.util.FutureUtil;
import com.didichuxing.datachannel.arius.admin.core.service.app.AppService;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.logic.ClusterLogicService;
import com.didichuxing.datachannel.arius.admin.core.service.template.logic.TemplateLogicService;
import com.didichuxing.datachannel.arius.admin.core.service.template.physic.TemplatePhyService;
import com.didiglobal.logi.log.ILog;
import com.didiglobal.logi.log.LogFactory;
import com.google.common.collect.Lists;

/**
 * Created by linyunan on 2021-10-14
 */
@Component
public class TemplateLogicPageSearchHandle extends BasePageSearchHandle<ConsoleTemplateVO> {

    private static final ILog LOGGER = LogFactory.getLog(TemplateLogicPageSearchHandle.class);

    @Autowired
    private AppService                  appService;

    @Autowired
    private AppLogicTemplateAuthManager appLogicTemplateAuthManager;

    @Autowired
    private TemplatePhyService          templatePhyService;

    @Autowired
    private TemplateLogicManager        templateLogicManager;

    @Autowired
    private TemplateLogicService        templateLogicService;

    @Autowired
    private ClusterLogicService          clusterLogicService;


    private static final FutureUtil<Void> BUILD_BELONG_CLUSTER_FUTURE_UTIL = FutureUtil.init("BUILD_BELONG_CLUSTER_FUTURE_UTIL",10,10,100);

    private static final FutureUtil<Void> RESOURCE_BUILD_FUTURE_UTIL = FutureUtil.init("RESOURCE_BUILD_FUTURE_UTIL",10,10,100);

    @Override
    protected Result<Boolean> validCheckForAppId(Integer appId) {
        if (!appService.isAppExists(appId)) {
            return Result.buildParamIllegal("???????????????");
        }
        return Result.buildSucc(true);
    }

    @Override
    protected Result<Boolean> validCheckForCondition(PageDTO pageDTO, Integer appId) {
        if (pageDTO instanceof TemplateConditionDTO) {
            TemplateConditionDTO templateConditionDTO = (TemplateConditionDTO) pageDTO;

            if (null != templateConditionDTO.getDataType() && !DataTypeEnum.isExit(templateConditionDTO.getDataType())) {
                return Result.buildParamIllegal("?????????????????????");
            }

            if (null != templateConditionDTO.getAuthType() && !isTemplateAuthExitByCode(templateConditionDTO.getAuthType())){
                return Result.buildParamIllegal("?????????????????????");
            }

            String templateName = templateConditionDTO.getName();
            if (!AriusObjUtils.isBlack(templateName) && (templateName.startsWith("*") || templateName.startsWith("?"))) {
                return Result.buildParamIllegal("??????????????????????????????*, ???????????????????");
            }

            if (null != templateConditionDTO.getSortTerm() && !SortTermEnum.isExit(templateConditionDTO.getSortTerm())) {
                return Result.buildParamIllegal(String.format("????????????????????????[%s]", templateConditionDTO.getSortTerm()));
            }

            return Result.buildSucc(true);
        }

        LOGGER.error("class=IndicesPageSearchHandle||method=validCheckForCondition||errMsg=failed to convert PageDTO to templateConditionDTO");

        return Result.buildFail();
    }

    @Override
    protected void init(PageDTO pageDTO) {
        // Do nothing
    }

    @Override
    protected PaginationResult<ConsoleTemplateVO> buildWithAuthType(PageDTO pageDTO, Integer authType, Integer appId) {
        TemplateConditionDTO condition = buildInitTemplateConditionDTO(pageDTO);

        //1. ????????????/??????/???/????????????????????????
        List<IndexTemplateLogic> appAuthTemplatesList = templateLogicManager.getTemplatesByAppIdAndAuthType(appId, condition.getAuthType());
        if (CollectionUtils.isEmpty(appAuthTemplatesList)) {
            return PaginationResult.buildSucc(null, 0, condition.getPage(), condition.getSize());
        }

        //2. ???????????????????????????????????????????????????????????????????????????????????????????????????????????????, ???????????????
        List<IndexTemplateLogic> meetConditionTemplateList = getMeetConditionTemplateList(condition, appAuthTemplatesList);

        //3. ???????????????
        int hitTotal = meetConditionTemplateList.size();

        //4. ?????????????????????????????????id????????????, ????????????????????????????????????????????????id
        sort(meetConditionTemplateList, condition.getSortTerm(), condition.getOrderByDesc());

        // 5.????????????
        List<IndexTemplateLogic> fuzzyAndLimitTemplateList = filterFullDataByPage(meetConditionTemplateList, condition);
        List<ConsoleTemplateVO>  consoleTemplateVOList     = ConvertUtil.list2List(fuzzyAndLimitTemplateList, ConsoleTemplateVO.class);

        //6. ????????????
        //7. ????????????????????????????????????
        //8. ?????????????????????indexRollover??????
        RESOURCE_BUILD_FUTURE_UTIL
                .runnableTask(() -> consoleTemplateVOList.forEach(consoleTemplateVO -> consoleTemplateVO.setAuthType(condition.getAuthType())))
                .runnableTask(() -> setTemplateBelongClusterPhyNames(consoleTemplateVOList))
                .runnableTask(() -> setTemplateIndexRolloverStatus(consoleTemplateVOList))
                .waitExecute();

        return PaginationResult.buildSucc(consoleTemplateVOList, hitTotal, condition.getPage(), condition.getSize());
    }

    @Override
    protected PaginationResult<ConsoleTemplateVO> buildWithoutAuthType(PageDTO pageDTO, Integer appId) {
        TemplateConditionDTO condition = buildInitTemplateConditionDTO(pageDTO);

        int totalHit;
        List<IndexTemplateLogic> matchIndexTemplateLogic;
        if (!AriusObjUtils.isEmptyList(condition.getClusterPhies())) {
            List<IndexTemplateLogic> allTemplateLogics = templateLogicService.getAllLogicTemplates();
            if (CollectionUtils.isEmpty(allTemplateLogics)) {
                return PaginationResult.buildSucc();
            }
            //???????????????????????????????????????????????????????????????????????????????????????????????????????????????, ???????????????
            List<IndexTemplateLogic> meetConditionTemplateList = getMeetConditionTemplateList(condition, allTemplateLogics);
            //???????????????
            totalHit = meetConditionTemplateList.size();
            //?????????????????????????????????id????????????, ????????????????????????????????????????????????id
            sort(meetConditionTemplateList, condition.getSortTerm(), condition.getOrderByDesc());
            //????????????????????????
            matchIndexTemplateLogic = filterFullDataByPage(meetConditionTemplateList, condition);
        } else {
            matchIndexTemplateLogic = templateLogicService.pagingGetLogicTemplatesByCondition(condition);
            totalHit                = templateLogicService.fuzzyLogicTemplatesHitByCondition(condition).intValue();
        }

        List<ConsoleTemplateVO> consoleTemplateVOList = doBuildWithoutAuthType(matchIndexTemplateLogic, appId);
        return PaginationResult.buildSucc(consoleTemplateVOList, totalHit, condition.getPage(), condition.getSize());
    }

    /******************************************private***********************************************/
    /**
     * ???????????????????????????????????????????????????????????????????????????????????????????????????????????????, ???????????????
     *
     * @param condition
     * @param appAuthTemplatesList
     * @return
     */
    private List<IndexTemplateLogic> getMeetConditionTemplateList(TemplateConditionDTO condition,
                                                                  List<IndexTemplateLogic> appAuthTemplatesList) {
        List<IndexTemplateLogic> meetConditionTemplateList = Lists.newArrayList();
        if (null != condition.getHasDCDR()) {
            appAuthTemplatesList = appAuthTemplatesList.stream().filter(r -> condition.getHasDCDR().equals(r.getHasDCDR()))
                    .collect(Collectors.toList());
        }

        if (!AriusObjUtils.isEmptyList(condition.getClusterPhies())) {
            Set<String> logicIdSet = templatePhyService.getMatchNormalLogicIdByCluster(condition.getClusterPhies().get(0));
            appAuthTemplatesList = appAuthTemplatesList.stream().filter(r -> logicIdSet.contains(r.getId().toString()))
                    .collect(Collectors.toList());
        }

        if (!AriusObjUtils.isBlack(condition.getName())) {
            appAuthTemplatesList = appAuthTemplatesList.stream().filter(r -> r.getName().contains(condition.getName()))
                .collect(Collectors.toList());
        }
        if (null != condition.getDataType()) {
            appAuthTemplatesList = appAuthTemplatesList.stream()
                .filter(r -> r.getDataType().equals(condition.getDataType())).collect(Collectors.toList());
        }
        meetConditionTemplateList.addAll(appAuthTemplatesList);
        return meetConditionTemplateList;
    }

    private List<ConsoleTemplateVO> doBuildWithoutAuthType(List<IndexTemplateLogic> indexTemplateLogicList,
                                                           Integer appId) {
        if (CollectionUtils.isEmpty(indexTemplateLogicList)) {
            return Lists.newArrayList();
        }

        List<AppTemplateAuth> appTemplateAuthList = appLogicTemplateAuthManager
            .getTemplateAuthListByTemplateListAndAppId(appId, indexTemplateLogicList);

        Map<Integer, Integer> templateId2AuthTypeMap = ConvertUtil.list2Map(appTemplateAuthList,
            AppTemplateAuth::getTemplateId, AppTemplateAuth::getType);

        List<ConsoleTemplateVO> consoleTemplateVOList = ConvertUtil.list2List(indexTemplateLogicList,
            ConsoleTemplateVO.class);

        //1. ????????????
        //2. ????????????????????????????????????
        //4. ?????????????????????indexRollover??????
        RESOURCE_BUILD_FUTURE_UTIL
                .runnableTask(() -> consoleTemplateVOList.forEach(
                        consoleTemplateVO -> consoleTemplateVO.setAuthType(templateId2AuthTypeMap.get(consoleTemplateVO.getId()))))
                .runnableTask(() -> setTemplateBelongClusterPhyNames(consoleTemplateVOList))
                .runnableTask(() -> setTemplateIndexRolloverStatus(consoleTemplateVOList))
                .waitExecute();

        return consoleTemplateVOList;
    }

    private TemplateConditionDTO buildInitTemplateConditionDTO(PageDTO pageDTO) {
        if (pageDTO instanceof TemplateConditionDTO) {
            return (TemplateConditionDTO) pageDTO;
        }
        return null;
    }

    private void setTemplateBelongClusterPhyNames(List<ConsoleTemplateVO> consoleTemplateVOList) {
        if (CollectionUtils.isEmpty(consoleTemplateVOList)) {
            return;
        }

        for (ConsoleTemplateVO consoleTemplateVO : consoleTemplateVOList) {
            BUILD_BELONG_CLUSTER_FUTURE_UTIL.runnableTask(() -> {
                Set<String> clusterNameList = templatePhyService.getTemplateByLogicId(consoleTemplateVO.getId())
                        .stream()
                        .map(IndexTemplatePhy::getCluster)
                        .collect(Collectors.toSet());

                consoleTemplateVO.setClusterPhies(Lists.newArrayList(clusterNameList));
            });
        }

        BUILD_BELONG_CLUSTER_FUTURE_UTIL.waitExecute();
    }

    private void setTemplateIndexRolloverStatus(List<ConsoleTemplateVO> consoleTemplateVOList) {
        if (CollectionUtils.isEmpty(consoleTemplateVOList)) { return;}

        for (ConsoleTemplateVO consoleTemplateVO : consoleTemplateVOList) {
            IndexTemplateConfig templateConfig = templateLogicService.getTemplateConfig(consoleTemplateVO.getId());
            consoleTemplateVO.setDisableIndexRollover(templateConfig.getDisableIndexRollover());
        }
    }

    /**
     * ??????????????????????????????????????????
     * @param meetConditionTemplateList              ?????????????????????
     * @param sortTerm                               ????????????
     * @see   SortTermEnum                           ???????????????????????????
     * @param orderByDesc                            ?????????????????? true ??? false ???
     */
    private void sort(List<IndexTemplateLogic> meetConditionTemplateList, String sortTerm, Boolean orderByDesc) {
        // ??????????????????
        if (null == sortTerm) {
            Collections.sort(meetConditionTemplateList);
            return;
        }

        meetConditionTemplateList.sort((o1, o2) -> {
            // ?????????????????????????????????
            if (SortTermEnum.CHECK_POINT_DIFF.getType().equals(sortTerm)) {
                return orderByDesc ? o2.getCheckPointDiff().compareTo(o1.getCheckPointDiff()) :
                        o1.getCheckPointDiff().compareTo(o2.getCheckPointDiff());
            }

            if (SortTermEnum.LEVEL.getType().equals(sortTerm)) {
                return orderByDesc ? o2.getLevel().compareTo(o1.getLevel()) :
                        o1.getLevel().compareTo(o2.getLevel());
            }

            // ?????????
            return 0;
        });
    }
}
