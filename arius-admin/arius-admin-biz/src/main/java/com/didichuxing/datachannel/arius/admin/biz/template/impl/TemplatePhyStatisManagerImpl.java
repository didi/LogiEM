package com.didichuxing.datachannel.arius.admin.biz.template.impl;

import com.didichuxing.datachannel.arius.admin.biz.template.TemplatePhyStatisManager;
import com.didichuxing.datachannel.arius.admin.client.bean.common.*;
import com.didichuxing.datachannel.arius.admin.client.bean.vo.template.AppIdTemplateAccessCountVO;
import com.didichuxing.datachannel.arius.admin.client.bean.vo.template.TemplateHealthDegreeRecordVO;
import com.didichuxing.datachannel.arius.admin.client.bean.vo.template.TemplateStatsInfoVO;
import com.didichuxing.datachannel.arius.admin.client.bean.vo.template.TemplateValueRecordVO;
import com.didichuxing.datachannel.arius.admin.client.constant.quota.NodeSpecifyEnum;
import com.didichuxing.datachannel.arius.admin.client.constant.quota.Resource;
import com.didichuxing.datachannel.arius.admin.client.constant.result.ResultType;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ClusterLogic;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ClusterLogicRackInfo;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.stats.ESIndexStats;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.template.IndexTemplatePhy;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.template.IndexTemplatePhyWithLogic;
import com.didichuxing.datachannel.arius.admin.common.constant.AdminConstant;
import com.didichuxing.datachannel.arius.admin.common.util.AriusObjUtils;
import com.didichuxing.datachannel.arius.admin.common.util.ConvertUtil;
import com.didichuxing.datachannel.arius.admin.core.component.QuotaTool;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.logic.ClusterLogicService;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.region.RegionRackService;
import com.didichuxing.datachannel.arius.admin.core.service.template.logic.TemplateLogicService;
import com.didichuxing.datachannel.arius.admin.core.service.template.physic.TemplatePhyService;
import com.didichuxing.datachannel.arius.admin.metadata.service.TemplateHealthDegreeService;
import com.didichuxing.datachannel.arius.admin.metadata.service.TemplateSattisService;
import com.didichuxing.datachannel.arius.admin.metadata.service.TemplateValueService;
import com.didiglobal.logi.log.ILog;
import com.didiglobal.logi.log.LogFactory;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.Objects;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author d06679
 * @date 2019-06-24
 */
@Service
public class TemplatePhyStatisManagerImpl implements TemplatePhyStatisManager {

    private static final ILog         LOGGER = LogFactory.getLog( TemplatePhyStatisManagerImpl.class);

    @Autowired
    private TemplatePhyService          templatePhyService;

    @Autowired
    private TemplateLogicService        templateLogicService;

    @Autowired
    private TemplateSattisService       templateSattisService;

    @Autowired
    private ClusterLogicService clusterLogicService;

    @Autowired
    private RegionRackService regionRackService;

    @Autowired
    private TemplateHealthDegreeService templateHealthDegreeService;

    @Autowired
    private TemplateValueService templateValueService;

    @Autowired
    private QuotaTool                 quotaTool;

    /**
     * ????????????????????????
     *
     * @param physicalIds id??????
     * @return result
     */
    @Override
    public List<TemplateMetaMetric> metaByPhysical(List<Long> physicalIds) {
        List<IndexTemplatePhyWithLogic> templatePhysicalWithLogics = templatePhyService
            .getTemplateWithLogicByIds(physicalIds);

        Map<Integer, Integer> templateLogicId2DeployCountMap = templateLogicService.getAllLogicTemplatesPhysicalCount();

        Map<Long, LogicResourceConfig> physicalId2ResourceLogicMap = genPhysicalId2ResourceConfigMap(
            templatePhysicalWithLogics);

        List<TemplateMetaMetric> templateMetaMetrics = Lists.newArrayList();
        for (IndexTemplatePhyWithLogic physicalWithLogic : templatePhysicalWithLogics) {
            TemplateMetaMetric templateMetaMetric = new TemplateMetaMetric();
            templateMetaMetric.setPhysicalId(physicalWithLogic.getId());
            templateMetaMetric.setCluster(physicalWithLogic.getCluster());
            templateMetaMetric.setTemplateName(physicalWithLogic.getName());
            templateMetaMetric.setQuota(physicalWithLogic.getLogicTemplate().getQuota()
                                        / templateLogicId2DeployCountMap.get(physicalWithLogic.getLogicId()));
            templateMetaMetric.setExpireTime(physicalWithLogic.getLogicTemplate().getExpireTime());
            templateMetaMetric.setHotTime(physicalWithLogic.getLogicTemplate().getHotTime());
            templateMetaMetric.setShardNum(physicalWithLogic.getShard());
            templateMetaMetric.setCreateTime(physicalWithLogic.getLogicTemplate().getCreateTime());
            templateMetaMetric.setDateFormat(physicalWithLogic.getLogicTemplate().getDateFormat());
            templateMetaMetric.setExpression(physicalWithLogic.getLogicTemplate().getExpression());

            if (physicalId2ResourceLogicMap.containsKey(physicalWithLogic.getId())) {
                templateMetaMetric
                    .setReplicaNum(physicalId2ResourceLogicMap.get(physicalWithLogic.getId()).getReplicaNum());
            } else {
                templateMetaMetric.setReplicaNum(1);
            }

            templateMetaMetrics.add(templateMetaMetric);
        }

        return templateMetaMetrics;
    }

    /**
     * ????????????????????????
     *
     * @param physicalIds ????????????id
     * @param startTime   ????????????
     * @param endTime     ????????????
     * @return list
     */
    @Override
    public Result<List<TemplateMetaMetric>> getTemplateMetricByPhysicals(List<Long> physicalIds, long startTime,
                                                                         long endTime,
                                                                         TemplateResourceConfig resourceConfig) {
        Result<List<TemplateMetaMetric>> result = mataAndMetricByPhysicals(physicalIds, startTime, endTime);

        if (result.success()) {
            // ??????????????????????????????????????????????????????
            // ?????????????????????Quota?????????????????????????????????
            for (TemplateMetaMetric templateMetaMetric : result.getData()) {
                computeActualCost(templateMetaMetric, resourceConfig);
                computeQuotaCost(templateMetaMetric);
            }
        }

        return result;
    }

    /**
     * ????????????????????????
     *
     * @param physicalId     ????????????id
     * @param startTime      ????????????
     * @param endTime        ????????????
     * @param resourceConfig ??????
     * @return list
     */
    @Override
    public Result<TemplateMetaMetric> getTemplateMetricByPhysical(Long physicalId, long startTime, long endTime,
                                                                  TemplateResourceConfig resourceConfig) {
        Result<List<TemplateMetaMetric>> result = mataAndMetricByPhysicals(Lists.newArrayList(physicalId), startTime,
            endTime);

        if (result.success()) {
            TemplateMetaMetric templateMetaMetric = result.getData().get(0);
            computeActualCost(templateMetaMetric, resourceConfig);
            computeQuotaCost(templateMetaMetric);
            return Result.buildSucc(templateMetaMetric);
        }

        return Result.buildFrom(result);
    }

    /**
     * ????????????tps??????
     *
     * @param cluster   ??????
     * @param template  ??????
     * @param currentStartTime ????????????
     * @param currentEndTime   ????????????
     * @return result
     */
    @Override
    public Result<PhysicalTemplateTpsMetric> getTemplateTpsMetric(String cluster, String template,
                                                                  long currentStartTime, long currentEndTime) {

        IndexTemplatePhy templatePhysical = templatePhyService.getTemplateByClusterAndName(cluster, template);
        if (templatePhysical == null) {
            return Result.buildNotExist("???????????????");
        }

        Result<LogicTemplateTpsMetric> result = templateSattisService
            .getTemplateTpsMetric(templatePhysical.getLogicId(), currentStartTime, currentEndTime);

        if (result.failed()) {
            return Result.buildFrom(result);
        }

        LogicTemplateTpsMetric logicTemplateTpsMetric = result.getData();
        if (logicTemplateTpsMetric.getMaxTps() == null) {
            return Result.build(ResultType.AMS_SERVER_ERROR.getCode(), "maxTps??????");
        }

        if (logicTemplateTpsMetric.getCurrentTpsMap() == null
            || !logicTemplateTpsMetric.getCurrentTpsMap().containsKey(templatePhysical.getId())) {
            return Result.build(ResultType.AMS_SERVER_ERROR.getCode(), "????????????currentTps??????");
        }

        if (logicTemplateTpsMetric.getCurrentFailCountMap() == null) {
            return Result.build(ResultType.AMS_SERVER_ERROR.getCode(), "????????????currentFailCountMap??????");
        }

        PhysicalTemplateTpsMetric tpsMetric = new PhysicalTemplateTpsMetric();
        tpsMetric.setMaxTps(result.getData().getMaxTps());
        tpsMetric.setCurrentTps(result.getData().getCurrentTpsMap().get(templatePhysical.getId()));
        tpsMetric.setCurrentFailCount(result.getData().getCurrentFailCountMap().get(templatePhysical.getId()));

        return Result.buildSucc(tpsMetric);
    }

    @Override
    public Result<Map<Integer, Long>> getAccessStatsInfoByTemplateIdAndDays(int logicTemplateId, int days) {
        return templateSattisService.getAccessStatsInfoByTemplateIdAndDays(logicTemplateId, days);
    }

    @Override
    public Result<TemplateStatsInfoVO> getTemplateBaseStatisticalInfoByLogicTemplateId(Long logicTemplateId) {
        return Result.buildSucc(ConvertUtil.obj2Obj(
                templateSattisService.getTemplateBaseStatisticalInfoByLogicTemplateId(logicTemplateId).getData(),
                TemplateStatsInfoVO.class));
    }

    @Override
    public Result<List<AppIdTemplateAccessCountVO>> getAccessAppInfos(int logicTemplateId, Long startDate, Long endDate) {
        return Result.buildSucc(ConvertUtil.list2List(
                templateSattisService.getAccessAppInfos(logicTemplateId, startDate, endDate).getData(),
                AppIdTemplateAccessCountVO.class));
    }

    @Override
    public Result<List<ESIndexStats>> getIndexStatis(Long logicTemplateId, Long startDate, Long endDate) {
        return templateSattisService.getIndexStatis(logicTemplateId, startDate, endDate);
    }

    @Override
    public Result<List<TemplateHealthDegreeRecordVO>> getHealthDegreeRecordByLogicTemplateId(Long logicTemplateId, Long startDate, Long endDate) {
        return Result.buildSucc(ConvertUtil.list2List(
                templateHealthDegreeService.getRecordByLogicTemplateId(logicTemplateId, startDate, endDate),
                TemplateHealthDegreeRecordVO.class));
    }

    @Override
    public Result<List<TemplateValueRecordVO>> getValueRecordByLogicTemplateId(Long logicTemplateId, Long startDate, Long endDate) {
        return Result.buildSucc(ConvertUtil.list2List(
                templateValueService.getRecordByLogicTemplateId(logicTemplateId, startDate, endDate),
                TemplateValueRecordVO.class));
    }

    /**************************************** private method ****************************************************/
    private Result<List<TemplateMetaMetric>> mataAndMetricByPhysicals(List<Long> physicalIds, long startTime,
                                                                      long endTime) {
        List<TemplateMetaMetric> templateMetaMetrics = metaByPhysical(physicalIds);

        if (CollectionUtils.isEmpty(templateMetaMetrics)) {
            return Result.buildSucc(Lists.newArrayList());
        }

        String cluster = templateMetaMetrics.get(0).getCluster();
        List<String> names = templateMetaMetrics.stream().map(TemplateMetaMetric::getTemplateName)
            .collect(Collectors.toList());
        Result<List<TemplateMetric>> result = templateSattisService.getTemplateMetrics(cluster, names, startTime,
            endTime);
        if (result.failed()) {
            return Result.buildParamIllegal("??????????????????????????????");
        }

        if (physicalIds.size() != result.getData().size()) {
            LOGGER.warn("class=TemplatePhyStatisManagerImpl||method=mataAndMetricByPhysical||physicalIdsSize={}||resultSize={}", physicalIds.size(),
                result.getData().size());
        }

        Result<Void> checkResult = checkTemplateMetrics(result.getData());
        if (checkResult.failed()) {
            return Result.buildParamIllegal("AMS???????????????????????????" + checkResult.getMessage());
        }

        Map<String, TemplateMetric> name2TemplateMetricMap = ConvertUtil.list2Map(result.getData(),
            TemplateMetric::getTemplate);

        for (TemplateMetaMetric templateMetaMetric : templateMetaMetrics) {
            TemplateMetric templateMetric = name2TemplateMetricMap.get(templateMetaMetric.getTemplateName());
            if (templateMetric == null) {
                return Result
                    .buildFrom(Result.buildParamIllegal("AMS????????????????????????: " + templateMetaMetric.getTemplateName()));
            }

            BeanUtils.copyProperties(templateMetric, templateMetaMetric);
        }

        return Result.buildSucc(templateMetaMetrics);
    }

    private void computeQuotaCost(TemplateMetaMetric templateMetaMetric) {
        Resource resource = quotaTool.getResourceOfQuota(NodeSpecifyEnum.DOCKER.getCode(),
            templateMetaMetric.getQuota());
        templateMetaMetric.setQuotaCpuCount(resource.getCpu());
        templateMetaMetric.setQuotaDiskG(resource.getDisk());
    }

    private void computeActualCost(TemplateMetaMetric templateMetaMetric, TemplateResourceConfig resourceConfig) {
        // ????????????
        templateMetaMetric.setActualDiskG(templateMetaMetric.getSumIndexSizeG());

        // cpu??????
        // cpu = ???????????? * (??????????????????) + ???search-query_time+scroll_time??? * query??????
        Double cpuByIndex = templateMetaMetric.getMaxTps() * templateMetaMetric.getReplicaNum()
                            / resourceConfig.getTpsPerCpu();
        if (templateMetaMetric.getSumDocCount() > 0 && templateMetaMetric.getSumIndexSizeG() > 0) {
            Double sizePerDoc = templateMetaMetric.getSumIndexSizeG() * 1024 * 1024
                                / templateMetaMetric.getSumDocCount();
            cpuByIndex = cpuByIndex * (sizePerDoc / resourceConfig.getDocSizeBaseline());
        }

        templateMetaMetric.setActualCpuCount(cpuByIndex);
    }

    private Result<Void> checkTemplateMetrics(List<TemplateMetric> data) {
        for (TemplateMetric templateMetric : data) {
            Result<Void> result = checkTemplateMetric(templateMetric);
            if (result.failed()) {return result;}
        }
        return Result.buildSucc();
    }

    private Result<Void> checkTemplateMetric(TemplateMetric templateMetric) {
        if (AriusObjUtils.isNull(templateMetric.getTemplate())) {
            return Result.buildParamIllegal("??????????????????");
        }

        if (AriusObjUtils.isNull(templateMetric.getSumIndexSizeG())) {
            return Result.buildParamIllegal(templateMetric.getTemplate() + "???sumIndexSizeG??????");
        }

        if (AriusObjUtils.isNull(templateMetric.getMaxIndexSizeG())) {
            return Result.buildParamIllegal(templateMetric.getTemplate() + "???maxIndexSizeG??????");
        }

        if (AriusObjUtils.isNull(templateMetric.getSumDocCount())) {
            return Result.buildParamIllegal(templateMetric.getTemplate() + "???sumDocCount??????");
        }

        if (AriusObjUtils.isNull(templateMetric.getMaxIndexDocCount())) {
            return Result.buildParamIllegal(templateMetric.getTemplate() + "???maxIndexDocCount??????");
        }

        if (AriusObjUtils.isNull(templateMetric.getMaxTps())) {
            return Result.buildParamIllegal(templateMetric.getTemplate() + "???maxTps??????");
        }

        if (AriusObjUtils.isNull(templateMetric.getMaxQueryTime())) {
            return Result.buildParamIllegal(templateMetric.getTemplate() + "???maxQueryTime??????");
        }

        if (AriusObjUtils.isNull(templateMetric.getMaxScrollTime())) {
            return Result.buildParamIllegal(templateMetric.getTemplate() + "???maxScrollTime??????");
        }

        if (templateMetric.getSumIndexSizeG() > 0.001 && templateMetric.getSumDocCount() <= 0) {
            return Result.buildParamIllegal(templateMetric.getTemplate() + "???sumDocCount??????");
        }

        if (templateMetric.getSumDocCount() > 0 && templateMetric.getSumIndexSizeG() <= 0) {
            return Result.buildParamIllegal(templateMetric.getTemplate() + "???sumIndexSizeG??????");
        }

        if (templateMetric.getMaxTps() > 0 && templateMetric.getSumIndexSizeG() <= 0) {
            return Result.buildParamIllegal(templateMetric.getTemplate() + "???sumIndexSizeG??????");
        }
        return Result.buildSucc();
    }

    private Map<Long, LogicResourceConfig> genPhysicalId2ResourceConfigMap(List<IndexTemplatePhyWithLogic> templatePhysicalWithLogics) {
        List<ClusterLogicRackInfo> logicClusterRacks = regionRackService.listAllLogicClusterRacks();
        Map<String, ClusterLogicRackInfo> clusterRack2ResourceIdMap = ConvertUtil.list2Map(logicClusterRacks,
            item -> item.getPhyClusterName() + "@" + item.getRack());

        List<ClusterLogic> logicClusters = clusterLogicService.listAllClusterLogics();
        Map<Long, ClusterLogic> resourceId2ResourceLogicMap = ConvertUtil.list2Map(logicClusters,
            ClusterLogic::getId);

        Map<Long, LogicResourceConfig> result = Maps.newHashMap();

        for (IndexTemplatePhyWithLogic physical : templatePhysicalWithLogics) {
            for (String rack : physical.getRack().split(AdminConstant.RACK_COMMA)) {
                String key = physical.getCluster() + "@" + rack;
                if (clusterRack2ResourceIdMap.containsKey(key)) {
                    ClusterLogic clusterLogic = resourceId2ResourceLogicMap
                        .get(clusterRack2ResourceIdMap.get(key).getLogicClusterIds());
                    if (Objects.nonNull(clusterLogic)){
                        result.put(physical.getId(),
                            clusterLogicService.genClusterLogicConfig(clusterLogic.getConfigJson()));
                    }
                 
                    break;
                }
            }
        }

        return result;
    }
}