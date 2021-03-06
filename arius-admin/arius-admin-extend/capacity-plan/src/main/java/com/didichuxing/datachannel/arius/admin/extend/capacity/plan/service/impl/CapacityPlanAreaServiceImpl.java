package com.didichuxing.datachannel.arius.admin.extend.capacity.plan.service.impl;

import com.alibaba.fastjson.JSON;
import com.didichuxing.datachannel.arius.admin.biz.cluster.ClusterNodeManager;
import com.didichuxing.datachannel.arius.admin.biz.template.srv.base.BaseTemplateSrv;
import com.didichuxing.datachannel.arius.admin.client.bean.common.RackMetaMetric;
import com.didichuxing.datachannel.arius.admin.client.bean.common.RegionMetric;
import com.didichuxing.datachannel.arius.admin.client.bean.common.Result;
import com.didichuxing.datachannel.arius.admin.client.bean.common.TemplateMetaMetric;
import com.didichuxing.datachannel.arius.admin.client.constant.quota.NodeSpecifyEnum;
import com.didichuxing.datachannel.arius.admin.client.constant.quota.Resource;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.region.ClusterRegion;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.template.IndexTemplatePhy;
import com.didichuxing.datachannel.arius.admin.common.constant.AdminConstant;
import com.didichuxing.datachannel.arius.admin.common.constant.arius.AriusUser;
import com.didichuxing.datachannel.arius.admin.common.constant.template.TemplateServiceEnum;
import com.didichuxing.datachannel.arius.admin.common.exception.ESOperateException;
import com.didichuxing.datachannel.arius.admin.common.util.AriusObjUtils;
import com.didichuxing.datachannel.arius.admin.common.util.ConvertUtil;
import com.didichuxing.datachannel.arius.admin.common.util.RackUtils;
import com.didichuxing.datachannel.arius.admin.core.component.QuotaTool;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.region.RegionRackService;
import com.didichuxing.datachannel.arius.admin.core.service.common.AriusConfigInfoService;
import com.didichuxing.datachannel.arius.admin.extend.capacity.plan.bean.common.CapacityPlanConfig;
import com.didichuxing.datachannel.arius.admin.extend.capacity.plan.bean.dto.CapacityPlanAreaDTO;
import com.didichuxing.datachannel.arius.admin.extend.capacity.plan.bean.entity.CapacityPlanArea;
import com.didichuxing.datachannel.arius.admin.extend.capacity.plan.bean.entity.CapacityPlanRegion;
import com.didichuxing.datachannel.arius.admin.extend.capacity.plan.bean.entity.CapacityPlanRegionTask;
import com.didichuxing.datachannel.arius.admin.extend.capacity.plan.bean.po.CapacityPlanAreaPO;
import com.didichuxing.datachannel.arius.admin.extend.capacity.plan.component.RegionResourceManager;
import com.didichuxing.datachannel.arius.admin.extend.capacity.plan.constant.CapacityPlanAreaStatusEnum;
import com.didichuxing.datachannel.arius.admin.extend.capacity.plan.dao.mysql.CapacityPlanAreaDAO;
import com.didichuxing.datachannel.arius.admin.extend.capacity.plan.exception.ClusterMetadataException;
import com.didichuxing.datachannel.arius.admin.extend.capacity.plan.exception.ResourceNotEnoughException;
import com.didichuxing.datachannel.arius.admin.extend.capacity.plan.service.CapacityPlanAreaService;
import com.didichuxing.datachannel.arius.admin.extend.capacity.plan.service.CapacityPlanRegionService;
import com.didichuxing.datachannel.arius.admin.extend.capacity.plan.service.CapacityPlanRegionTaskService;
import com.didiglobal.logi.log.ILog;
import com.didiglobal.logi.log.LogFactory;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.didichuxing.datachannel.arius.admin.client.constant.operaterecord.ModuleEnum.CAPACITY_PLAN_AREA;
import static com.didichuxing.datachannel.arius.admin.client.constant.operaterecord.OperationEnum.*;
import static com.didichuxing.datachannel.arius.admin.common.constant.AdminConstant.MILLIS_PER_DAY;
import static com.didichuxing.datachannel.arius.admin.common.constant.template.TemplateServiceEnum.TEMPLATE_CAPA_PLAN;
import static com.didichuxing.datachannel.arius.admin.common.util.RackUtils.belong;
import static com.didichuxing.datachannel.arius.admin.common.util.RackUtils.removeRacks;
import static com.didichuxing.datachannel.arius.admin.core.component.QuotaTool.TEMPLATE_QUOTA_MIN;
import static com.didichuxing.datachannel.arius.admin.extend.capacity.plan.constant.CapacityPlanRegionTaskStatusEnum.DATA_MOVING;
import static com.didichuxing.datachannel.arius.admin.extend.capacity.plan.constant.CapacityPlanRegionTaskStatusEnum.OP_ES_ERROR;

/**
 * @author d06679
 * @date 2019-06-24
 */
@Service
public class CapacityPlanAreaServiceImpl extends BaseTemplateSrv implements CapacityPlanAreaService {

    private static final ILog LOGGER = LogFactory.getLog(CapacityPlanAreaServiceImpl.class);

    private static final Integer REGION_IS_TOO_SMALL = 0;
    private static final Integer NO_TEMPLATE = -1;
    private static final Integer REGION_PLAN_SUCCEED = 1;

    @Autowired
    private CapacityPlanAreaDAO capacityPlanAreaDAO;

    @Autowired
    private CapacityPlanRegionService capacityPlanRegionService;

    @Autowired
    private CapacityPlanRegionTaskService capacityPlanRegionTaskService;

    @Autowired
    private ClusterNodeManager clusterNodeManager;

    @Autowired
    private QuotaTool quotaTool;

    @Autowired
    private RegionRackService regionRackService;

    @Autowired
    private RegionResourceManager regionResourceManager;

    @Autowired
    private AriusConfigInfoService ariusConfigInfoService;

    private final Cache<String, CapacityPlanAreaPO> cpcCache = CacheBuilder.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES).maximumSize(1000).build();

    @Override
    public TemplateServiceEnum templateService() {
        return TEMPLATE_CAPA_PLAN;
    }

    /**
     * ?????????????????????????????????
     *
     * @return list
     */
    @Override
    public List<CapacityPlanArea> listAllPlanAreas() {

        // ????????????area
        List<CapacityPlanAreaPO> areaPOs = capacityPlanAreaDAO.listAll();

        List<CapacityPlanArea> capacityPlanAreas = Lists.newArrayList();
        for (CapacityPlanAreaPO areaPO : areaPOs) {
            try {
                CapacityPlanArea capacityPlanArea = ConvertUtil.obj2Obj(areaPO, CapacityPlanArea.class);
                capacityPlanArea.setConfig(JSON.parseObject(capacityPlanArea.getConfigJson(), CapacityPlanConfig.class));
                capacityPlanAreas.add(capacityPlanArea);
            } catch (Exception e) {
                LOGGER.warn("class=CapacityPlanAreaServiceImpl||method=listPlanArea||msg=parseCapacityPlanAreaFailed||config={}",
                    areaPO.getConfigJson(), e);
            }
        }
        return capacityPlanAreas;
    }

    @Override
    public List<CapacityPlanArea> listAreasByLogicCluster(Long logicClusterId) {
        return listAllPlanAreas().stream().filter(area -> area.getResourceId().equals(logicClusterId)).collect(Collectors.toList());
    }

    @Override
    public List<CapacityPlanArea> listAreasByPhyCluster(String phyClusterName) {
        return listAllPlanAreas().stream().filter(area -> area.getClusterName().equals(phyClusterName)).collect(Collectors.toList());
    }

    /**
     * ?????????????????????????????????
     *
     * @return list
     */
    @Override
    public List<CapacityPlanArea> listPlaningAreas() {
        List<CapacityPlanArea> areas = listAllPlanAreas();
        areas = areas.stream()
                .filter(capacityPlanArea -> checkTemplateSrvOpen(capacityPlanArea).success())
                .collect(Collectors.toList());
        return areas;
    }

    /**
     * ?????????????????????????????????
     *
     * @param capacityPlanAreaDTO ????????????
     * @return result
     */
    @Override
    public Result<Long> createPlanAreaInNotExist(CapacityPlanAreaDTO capacityPlanAreaDTO, String operator) {
        // ????????????
        Result<Void> checkResult = checkPlanAreaParams(capacityPlanAreaDTO);
        if (checkResult.failed()) {
            return Result.buildFrom(checkResult);
        }

        // ???????????????????????????
        CapacityPlanArea area = getAreaByResourceIdAndCluster(capacityPlanAreaDTO.getResourceId(), capacityPlanAreaDTO.getClusterName());
        if (area != null) {
            return Result.build(true, area.getId());
        }

        // ????????????????????????????????????area??????status??????
        capacityPlanAreaDTO.setStatus(isTemplateSrvOpen(capacityPlanAreaDTO.getClusterName())
            ? CapacityPlanAreaStatusEnum.PLANING.getCode() : CapacityPlanAreaStatusEnum.SUSPEND.getCode());

        if (capacityPlanAreaDTO.getConfigJson() == null) {
            capacityPlanAreaDTO.setConfigJson("");
        }

        CapacityPlanAreaPO capacityPlanAreaPO = ConvertUtil.obj2Obj(capacityPlanAreaDTO, CapacityPlanAreaPO.class);
        boolean succeed = capacityPlanAreaDAO.insert(capacityPlanAreaPO) == 1;
        if (succeed) {
            operateRecordService.save(CAPACITY_PLAN_AREA, ADD, capacityPlanAreaPO.getId(), "", operator);
        }

        return Result.build(succeed, capacityPlanAreaPO.getId());
    }

    /**
     * ????????????????????? ?????????????????????
     *
     * @param areaDTO ????????????
     * @return result
     */
    @Override
    public Result<Void> modifyPlanArea(CapacityPlanAreaDTO areaDTO, String operator) {
        if (AriusObjUtils.isNull(areaDTO)) {
            return Result.buildParamIllegal("??????????????????");
        }

        if (AriusObjUtils.isNull(areaDTO.getId())) {
            return Result.buildParamIllegal("??????areaId??????");
        }

        if (areaDTO.getStatus() != null
            && (CapacityPlanAreaStatusEnum.valueOf(areaDTO.getStatus()) == null) ) {
            return Result.buildParamIllegal("????????????");
        }

        CapacityPlanAreaPO oldPO = capacityPlanAreaDAO.getById(areaDTO.getId());
        if (oldPO == null) {
            return Result.buildNotExist("??????area?????????");
        }

        CapacityPlanAreaPO param = ConvertUtil.obj2Obj(areaDTO, CapacityPlanAreaPO.class);
        boolean succeed = (1 == capacityPlanAreaDAO.update(param));
        if (succeed) {
            operateRecordService.save(CAPACITY_PLAN_AREA, EDIT, param.getId(), AriusObjUtils.findChangedWithClear(oldPO, param), operator);
        }

        return Result.build(succeed);
    }

    /**
     * ??????area???????????????
     *
     * @param areaId      areaId
     * @param usageAvg    usageAvg
     * @param overSoldAvg overSoldAvg
     * @return true/false
     */
    @Override
    public boolean recordAreaStatis(Long areaId, double usageAvg, double overSoldAvg) {
        CapacityPlanAreaPO param = new CapacityPlanAreaPO();
        param.setId(areaId);
        param.setUsage(usageAvg);
        param.setOverSold(overSoldAvg);
        return 1 == capacityPlanAreaDAO.update(param);
    }

    /**
     * ?????????????????????
     *
     * @param areaId ????????????ID
     * @return result
     */
    @Override
    public Result<Void> deletePlanArea(Long areaId, String operator) {
        CapacityPlanAreaPO areaPO = capacityPlanAreaDAO.getById(areaId);
        if (areaPO == null){
            return Result.buildFail(String.format("area %d ?????????", areaId));
        }

        // area????????????region??????
        List<ClusterRegion> regions =  regionRackService
				.listRegionsByLogicAndPhyCluster(areaPO.getResourceId(), areaPO.getClusterName());
        if (CollectionUtils.isNotEmpty(regions)) {
            return Result.buildParamIllegal("??????????????????region???????????????region");
        }

        boolean succeed = 1 == capacityPlanAreaDAO.delete(areaId);
        if (succeed) {
            operateRecordService.save(CAPACITY_PLAN_AREA, DELETE, areaId, "", operator);
        }
        return Result.build(succeed);
    }

    /**
     * ????????????????????????region?????????????????????region???racks??????racks???????????????????????????region
     *
     * @param areaId areaId
     * @return ????????????????????????region????????????
     */
    @Override
    public Result<List<CapacityPlanRegion>> initRegionsInPlanArea(Long areaId, String operator) {
        CapacityPlanArea capacityPlanArea = getAreaById(areaId);

        // ????????????????????????
        Result<Void> checkResult = checkTemplateSrvOpen(capacityPlanArea);
        if (checkResult.failed()) {
            return Result.buildFail("????????????????????????????????????");
        }

        CapacityPlanConfig capacityPlanConfig = capacityPlanArea.getConfig();
        if (capacityPlanConfig == null) {
            return Result.buildNotExist("??????????????????????????????");
        }

        // ?????????????????????region - ????????????region
        List<CapacityPlanRegion> hasExistRegions = capacityPlanRegionService.listRegionsInArea(areaId);
        Set<String> hasRegionRackSet = getHasRegionRacks(hasExistRegions);

        // ???????????????????????????rack
        Set<String> noRegionRackSet = getNoRegionRacks(capacityPlanArea.getId(), hasRegionRackSet);
        if (CollectionUtils.isEmpty(noRegionRackSet)) {
            return Result.buildSucc(hasExistRegions);
        }

        // ???????????????rack??????????????????
        List<IndexTemplatePhy> templatePhysicals = templatePhyService
                .getNormalTemplateByClusterAndRack(capacityPlanArea.getClusterName(), noRegionRackSet);
        if (CollectionUtils.isEmpty(templatePhysicals)) {
            return Result.buildNotExist("?????????????????????");
        }

        // ???????????????????????????region??????????????????????????????rack???????????????hasRegionRackList?????????????????????noRegionRackSet???
        if (!checkTemplateAllocate(templatePhysicals, hasRegionRackSet, noRegionRackSet)) {
            return Result.buildParamIllegal("????????????????????????region???????????????");
        }

        // ??????rack???????????????????????????
        Result<List<RackMetaMetric>> rackMetaMetricsResult = clusterNodeManager.meta(capacityPlanArea.getClusterName(),
                noRegionRackSet);
        if (rackMetaMetricsResult.failed()) {
            return Result.buildFrom(rackMetaMetricsResult);
        }
        List<RackMetaMetric> rackMetas = rackMetaMetricsResult.getData();

        // ???????????????????????????????????????
        List<TemplateMetaMetric> templateMetas = regionResourceManager.getTemplateMetrics(
                getNoRegionTemplateIds(templatePhysicals, noRegionRackSet),
                capacityPlanConfig.getPlanRegionResourceDays() * MILLIS_PER_DAY, capacityPlanConfig);

        // ?????????quota????????????????????????????????????????????????
        refreshTemplateQuota(templateMetas);

        // ??????region???key-???????????????rack???value-???????????????rack???????????????region???????????????
        Map<String, List<TemplateMetaMetric>> rack2templateMetasMap = initRegionInner(rackMetas, templateMetas,
                capacityPlanArea);

        LOGGER.info("class=CapacityPlanAreaServiceImpl||method=initRegionsInPlanArea||cluster={}||msg=get pan region result||result={}", capacityPlanArea.getClusterName(),
                JSON.toJSON(rack2templateMetasMap));

        // ???????????????
        if (!saveRegion(capacityPlanArea, rack2templateMetasMap)) {
            throw new IllegalArgumentException("region????????????");
        }

        operateRecordService.save(CAPACITY_PLAN_AREA, CAPACITY_PAN_INIT_REGION, areaId, "", operator);

        return Result.buildSucc(capacityPlanRegionService.listRegionsInArea(areaId));
    }

    /**
     * ??????area??????
     *
     * @param areaId areaId
     * @return result
     */
    @Override
    public CapacityPlanArea getAreaById(Long areaId) {

        CapacityPlanAreaPO areaPO = getCapacityPlanRegionPOFromCache(areaId);
        if (areaPO == null) {
            return null;
        }

        // ?????????????????????
        CapacityPlanArea capacityPlanArea = ConvertUtil.obj2Obj(areaPO, CapacityPlanArea.class);

        // ????????????
        CapacityPlanConfig capacityPlanConfig = new CapacityPlanConfig();

        // area???????????????
        if (StringUtils.isNotBlank(areaPO.getConfigJson())) {
            BeanUtils.copyProperties(JSON.parseObject(areaPO.getConfigJson(), CapacityPlanConfig.class), capacityPlanConfig);
        }
        capacityPlanArea.setConfig(capacityPlanConfig);

        return capacityPlanArea;
    }

    /**
     * ???????????????rack??????
     *
     * @param areaId ??????id
     * @return result
     */
    @Override
    public Set<String> listAreaRacks(Long areaId) {

        // 2.0??????????????????arius_resource_logic_item????????????area???rack
        // 3.0?????????area????????????????????????rack
        CapacityPlanAreaPO capacityPlanAreaPO = capacityPlanAreaDAO.getById(areaId);
        if (capacityPlanAreaPO == null) {
            return Sets.newHashSet();
        }

        return clusterPhyService.listHotRacks(capacityPlanAreaPO.getClusterName());
    }

    /**
     * ???????????????rack
     *
     * @param areaId areaId
     * @return list
     */
    @Override
    public List<String> listAreaFreeRacks(Long areaId) {
        CapacityPlanArea capacityPlanCluster = getAreaById(areaId);
        if (capacityPlanCluster == null) {
            return Lists.newArrayList();
        }

        Set<String> usedRacks = listAreaUsedRacks(areaId);
        Set<String> areaRacks = listAreaRacks(areaId);
        areaRacks.removeAll(usedRacks);

        List<String> freeRackList = Lists.newArrayList(areaRacks);
        freeRackList.sort(RackUtils::compareByName);

        return freeRackList;
    }

    /**
     * ?????????????????????????????????region?????????rack
     *
     * @param areaId areaId
     * @return set
     */
    @Override
    public Set<String> listAreaUsedRacks(Long areaId) {
        List<CapacityPlanRegion> regions = capacityPlanRegionService.listRegionsInArea(areaId);
        Set<String> racksInRegion = new HashSet<>();
        for (CapacityPlanRegion region : regions) {
            racksInRegion.addAll(RackUtils.racks2Set(region.getRacks()));
        }
        return racksInRegion;
    }

    /**
     * ????????????
     *
     * @param areaId areaId
     * @return true/false
     */
    @Override
    public Result<Void> planRegionsInArea(Long areaId) throws ESOperateException {

        // ????????????????????????
        Result<Void> checkResult = checkTemplateSrvOpen(areaId);
        if (checkResult.failed()) {
            return checkResult;
        }

        List<CapacityPlanRegion> regions = capacityPlanRegionService.listRegionsInArea(areaId);

        if (CollectionUtils.isEmpty(regions)) {
            return Result.buildSucc();
        }

        // ??????????????????
        List<String> failMsg = new LinkedList<>();
        for (CapacityPlanRegion region : regions) {
            try {
                Result<Void> result = capacityPlanRegionService.planRegion(region.getRegionId());
                if (result.failed()) {
                    failMsg.add(String.format("regionId:%d failMsg:%s", region.getRegionId(), result.getMessage()));
                    LOGGER.warn("class=CapacityPlanAreaServiceImpl||method=planRegion||region={}||failMag={}", region, result.getMessage());
                } else {
                    LOGGER.info("class=CapacityPlanAreaServiceImpl||method=planRegion||region={}||msg=succ", region, result.getMessage());
                }
            } catch (Exception e) {
                failMsg.add(String.format("regionId:%d errMsg:%s", region.getRegionId(), e.getMessage()));
                LOGGER.warn("class=CapacityPlanAreaServiceImpl||method=planRegion||region={}||errMag={}", region, e.getMessage(), e);
            }
        }

        if (CollectionUtils.isNotEmpty(failMsg)){
            return Result.buildFail(String.join(",", failMsg));
        }

        return Result.buildSucc();
    }

    /**
     * ????????????
     *
     * @param areaId areaId
     * @return true/false
     */
    @Override
    public Result<Void> checkRegionsInArea(Long areaId) {

        // ????????????????????????
        Result<Void> checkResult = checkTemplateSrvOpen(areaId);
        if (checkResult.failed()) {
            return checkResult;
        }

        List<CapacityPlanRegion> regions = capacityPlanRegionService.listRegionsInArea(areaId);
        if (CollectionUtils.isEmpty(regions)) {
            return Result.buildSucc();
        }

        // ??????????????????
        List<String> failMsg = new LinkedList<>();

        // ??????area????????????region????????????
        for (CapacityPlanRegion region : regions) {
            try {
                Result<Void> result = capacityPlanRegionService.checkRegion(region.getRegionId());
                if (result.failed()) {
                    failMsg.add(String.format("regionId:%d failMsg:%s", region.getRegionId(), result.getMessage()));
                    LOGGER.warn("class=CapacityPlanAreaServiceImpl||method=checkRegion||region={}||failMag={}", region, result.getMessage());
                } else {
                    LOGGER.info("class=CapacityPlanAreaServiceImpl||method=checkRegion||region={}||msg=succ", region, result.getMessage());
                }
            } catch (Exception e) {
                failMsg.add(String.format("regionId:%d errMsg:%s", region.getRegionId(), e.getMessage()));
                LOGGER.warn("class=CapacityPlanAreaServiceImpl||method=checkRegion||region={}||errMag={}", region, e.getMessage(), e);
            }
        }

        if (StringUtils.isNotBlank(failMsg.toString())) {
            return Result.buildFail(failMsg.toString());
        }

        return Result.buildSucc();
    }

    /**
     * ????????????
     *
     * @return true/false
     */
    @Override
    public boolean balanceRegions() {

        List<CapacityPlanArea> areas = listAllPlanAreas();

        // ??????????????????????????????????????????balance
        Set<String> enableResourceIdSet = ariusConfigInfoService.stringSettingSplit2Set(
                "capacity.plan.config.group",
                "capacity.plan.balance.region.resourceId", "", ",");

        areas = areas.stream()
                .filter(capacityPlanArea -> enableResourceIdSet.contains(String.valueOf(capacityPlanArea.getResourceId())))
                .collect(Collectors.toList());

        if (CollectionUtils.isEmpty(areas)) {
            return true;
        }

        for (CapacityPlanArea area : areas) {
            try {
                capacityPlanRegionService.balanceRegion(area.getId(), true);
            } catch (Exception e) {
                LOGGER.warn("class=CapacityPlanAreaServiceImpl||method=balanceRegion||areaId={}||errMsg={}", area.getResourceId(), e.getMessage(), e);
            }
        }

        return true;

    }

    /**
     * ????????????id???cluster??????
     *
     * @param logicClusterId ????????????ID
     * @param cluster        ??????????????????
     * @return area
     */
    @Override
    public CapacityPlanArea getAreaByResourceIdAndCluster(Long logicClusterId, String cluster) {
        return ConvertUtil.obj2Obj(capacityPlanAreaDAO.getByClusterAndResourceId(cluster, logicClusterId), CapacityPlanArea.class);
    }

    /**
     * ??????region???rack???region????????????rack????????????
     *
     */
    @Override
    public void correctAllAreaRegionAndTemplateRacks() {
        List<CapacityPlanArea> areas = listAllPlanAreas();
        for (CapacityPlanArea area : areas) {
            try {
                correctAreaRegionAndTemplateRacks(area.getId());
            } catch (Exception e) {
                LOGGER.warn("class=CapacityPlanAreaServiceImpl||method=checkMeta||areaId={}||errMsg={}", area.getResourceId(), e.getMessage(), e);
            }
        }
    }

    @Override
    public boolean correctAreaRegionAndTemplateRacks(Long areaId) {
        // ????????????rack????????????region???
        ensureAreaRacksUnique(areaId);
        // ????????????area????????????region??????????????????Rack?????????region??????????????????
        ensureAreaPhysicalTemplatesRacksSameWithRegion(areaId);
        return true;
    }

    /***************************************** private method ****************************************************/
    /**
     * ??????????????????????????????????????????Region Rack??????????????????
     *
     * @param areaId ????????????
     */
    private void ensureAreaRacksUnique(Long areaId) {
        List<CapacityPlanRegion> regions = capacityPlanRegionService.listRegionsInArea(areaId);

        // ??????????????????????????????rack??????????????????region???
        for (CapacityPlanRegion region1 : regions) {
            String targetRack = region1.getRacks();

            for (CapacityPlanRegion region2 : regions) {
                if (region2.getRegionId().equals(region1.getRegionId())) {
                    continue;
                }

                targetRack = removeRacks(targetRack, region2.getRacks());
            }

            if (!RackUtils.same(targetRack, region1.getRacks()) && StringUtils.isNotEmpty(targetRack)) {
                LOGGER.info("class=CapacityPlanAreaServiceImpl||method=checkRegionIsolation||regionId={}||srcRack={}||targetRack={}",
                        region1.getRegionId(), region1.getRacks(), targetRack);

                region1.setRacks(targetRack);
                // ???rack?????????????????????
                capacityPlanRegionService.modifyRegionRacks(region1.getRegionId(), targetRack);
            } else {
                LOGGER.info("class=CapacityPlanAreaServiceImpl||method=checkRegionIsolation||msg=rack no conflict", region1.getRegionId());
            }
        }
    }

    /**
     * ????????????area????????????region??????????????????Rack?????????region??????????????????
     *
     * @param areaId ????????????
     */
    private void ensureAreaPhysicalTemplatesRacksSameWithRegion(Long areaId) {
        Collection<CapacityPlanRegion> clusterRegions = capacityPlanRegionService.listRegionsInArea(areaId);
        CapacityPlanArea area = getAreaById(areaId);

        if(null == area){return;}

        // ??????????????????????????????
        List<IndexTemplatePhy> clusterTemplates = templatePhyService.getNormalTemplateByCluster(area.getClusterName());

        // ?????????????????????rack?????????
        for (CapacityPlanRegion region : clusterRegions) {

            // region???rack???????????????????????????rack???
            String tgtRack = getRegionFinalRack(region);

            List<IndexTemplatePhy> regionTemplates = clusterTemplates.stream()
                    .filter(indexTemplatePhysical -> belong(indexTemplatePhysical.getRack(), tgtRack))
                    .collect(Collectors.toList());

            // ???????????????????????????rack????????????
            for (IndexTemplatePhy templatePhysical : regionTemplates) {
                if (RackUtils.same(templatePhysical.getRack(), tgtRack)) {
                    continue;
                }

                try {
                    // ???????????????rack???region???rack??????
                    templatePhyManager.editTemplateRackWithoutCheck(templatePhysical.getId(), tgtRack,
                            AriusUser.CAPACITY_PLAN.getDesc(), 0);
                    LOGGER.info("class=CapacityPlanAreaServiceImpl||method=checkRegionUniformity||template={}||srcRack={}||targetRack={}",
                            templatePhysical.getName(), templatePhysical.getRack(), tgtRack);
                } catch (Exception e) {
                    LOGGER.error(
                            "class=CapacityPlanRegionServiceImpl||method=checkMeta||errMsg={}||physicalId={}||tgtRack={}",
                            e.getMessage(), templatePhysical.getId(), tgtRack, e);
                }
            }
        }
    }

    /**
     * ??????region????????????rack
     * ??????????????????????????????
     *
     * @param region region
     * @return
     */
    private String getRegionFinalRack(CapacityPlanRegion region) {

        String racks = region.getRacks();

        CapacityPlanRegionTask lastDecreaseTask = capacityPlanRegionTaskService.getLastDecreaseTask(region.getRegionId(), 7);
        // ??????????????????
        if (lastDecreaseTask == null) {
            return racks;
        }

        // ?????????????????????????????????
        if (!lastDecreaseTask.getStatus().equals(DATA_MOVING.getCode())
                && !lastDecreaseTask.getStatus().equals(OP_ES_ERROR.getCode())) {
            return racks;
        }

        return RackUtils.removeRacks(racks, lastDecreaseTask.getDeltaRacks());
    }

    /**
     * ??????region??????
     * @param capacityPlanArea      ????????????area
     * @param rack2templateMetasMap region?????????????????????key-racks???value-racks????????????
     * @return result
     */
    private boolean saveRegion(CapacityPlanArea capacityPlanArea,
                               Map<String, List<TemplateMetaMetric>> rack2templateMetasMap) {

        for (Map.Entry<String, List<TemplateMetaMetric>> entry : rack2templateMetasMap.entrySet()) {
            String racks = entry.getKey();
            List<TemplateMetaMetric> templateMetaMetrics = entry.getValue();

            // ???????????????region
            Result<Long> createAndBindResult = regionRackService.createAndBindRegion(
                capacityPlanArea.getClusterName(),
                racks,
                capacityPlanArea.getResourceId(),
                AdminConstant.YES,
                AriusUser.CAPACITY_PLAN.getDesc());


            if (createAndBindResult.failed()) {
                return false;
            }
            // ????????????regionId
            Long regionId = createAndBindResult.getData();

            // ?????????????????????
            if (!capacityPlanRegionTaskService.saveInitTask(regionId, racks, templateMetaMetrics)) {
                return false;
            }
        }

        return true;
    }

    /**
     * ??????region
     *
     * @param rackMetas           rack??????
     * @param templateMetas       ????????????
     * @param capacityPlanArea ????????????
     * @return result
     */
    private Map<String, List<TemplateMetaMetric>> initRegionInner(List<RackMetaMetric> rackMetas,
                                                                  List<TemplateMetaMetric> templateMetas,
                                                                  CapacityPlanArea capacityPlanArea) {
        // rack????????????????????????
        rackMetas.sort((o1, o2) -> RackUtils.compareByName(o1.getName(), o2.getName()));

        // ??????????????????????????????
        Set<Long> planedTemplateSet = Sets.newHashSet();

        Map<String, List<TemplateMetaMetric>> rack2TemplateMetasMap = Maps.newHashMap();

        List<RackMetaMetric> newRegionRacks = Lists.newArrayList();
        for (int i = 0; i < rackMetas.size(); i++) {
            RackMetaMetric rackMeta = rackMetas.get(i);
            newRegionRacks.add(rackMeta);

            LOGGER.info("class=CapacityPlanAreaServiceImpl||method=planRegion||rack={}||msg=add new rack", rackMeta);

            if (newRegionRacks.size() >= capacityPlanArea.getConfig().getCountRackPerRegion()
                    || i == rackMetas.size() - 1) {
                RegionMetric regionMetric = capacityPlanRegionService.calcRegionMetric(newRegionRacks);

                LOGGER.info("class=CapacityPlanAreaServiceImpl||method=initRegionInner||regionMetric={}||count={}||msg=region count reach config count",
                        regionMetric, capacityPlanArea.getConfig().getCountRackPerRegion());

                List<TemplateMetaMetric> matchedTemplates = Lists.newArrayList();
                Integer result = findMatchedTemplates(getRegionCapacity(regionMetric.getResource()),
                        templateMetas.stream()
                                .filter(templateMeta -> !planedTemplateSet.contains(templateMeta.getPhysicalId()))
                                .collect(Collectors.toList()),
                        capacityPlanArea.getConfig(), matchedTemplates);

                if (NO_TEMPLATE.equals(result)) {
                    LOGGER.info("class=CapacityPlanAreaServiceImpl||method=planRegion||regionRacks={}||msg=no template", regionMetric.getRacks());
                    break;
                }

                if (REGION_IS_TOO_SMALL.equals(result)) {
                    LOGGER.info("class=CapacityPlanAreaServiceImpl||method=planRegion||regionRacks={}||msg=region is small", regionMetric.getRacks());
                    continue;
                }

                if (REGION_PLAN_SUCCEED.equals(result)) {
                    LOGGER.info("class=CapacityPlanAreaServiceImpl||method=planRegion||regionRacks={}||matchedTemplates={}||msg=plan succ",
                            regionMetric.getRacks(), matchedTemplates);

                    rack2TemplateMetasMap.put(regionMetric.getRacks(), matchedTemplates);

                    planedTemplateSet.addAll(
                            matchedTemplates.stream().map(TemplateMetaMetric::getPhysicalId).collect(Collectors.toSet()));

                    newRegionRacks = Lists.newArrayList();
                }
            }
        }

        // ????????????
        if (planedTemplateSet.size() < templateMetas.size()) {
            List<TemplateMetaMetric> notPlanedTemplates = templateMetas.stream()
                    .filter(templateMeta -> !planedTemplateSet.contains(templateMeta.getPhysicalId()))
                    .collect(Collectors.toList());

            Double missQuotaSum = 0.0;
            for (TemplateMetaMetric templateMeta : notPlanedTemplates) {
                missQuotaSum += templateMeta.getQuota();
            }

            LOGGER.info(
                    "class=CapacityPlanAreaServiceImpl||method=planRegion||planedTemplateSet={}||templateMetas={}||missQuotaSum={}||notPlanedTemplates={}||msg=cluster not enough",
                    planedTemplateSet.size(), templateMetas.size(), missQuotaSum, notPlanedTemplates);

            throw new ResourceNotEnoughException("??????????????????, ??????Quota???" + missQuotaSum);
        }

        return rack2TemplateMetasMap;
    }

    /**
     * ??????region?????????
     * ?????????quota?????????DOCKER(16C32G3T)??????????????????;?????????????????????????????????????????????????????????region???????????????DOCKER(16C32G3T)?????????
     *
     * @param resource region
     * @return ??????
     */
    private Double getRegionCapacity(Resource resource) {
        return quotaTool.getResourceQuotaCountByCpuAndDisk(NodeSpecifyEnum.DOCKER.getCode(), resource.getCpu(),
                resource.getDisk(), 0.0);
    }

    /**
     * ???region????????????
     *
     * @param regionCapacity   region?????????
     * @param templateMetas    ??????????????????
     * @param config           ????????????
     * @param matchedTemplates ??????
     * @return ??????????????????????????????????????????
     */
    private Integer findMatchedTemplates(Double regionCapacity, List<TemplateMetaMetric> templateMetas,
                                         CapacityPlanConfig config, List<TemplateMetaMetric> matchedTemplates) {
        if (CollectionUtils.isEmpty(templateMetas)) {
            return NO_TEMPLATE;
        }

        LOGGER.info(
                "class=CapacityPlanAreaServiceImpl||method=findMatchedTemplates||regionCapacity={}||templateMetas={}||config={}||msg=begin init a region",
                regionCapacity, templateMetas, config);

        // ???????????????????????????????????????????????????????????????region?????????
        List<TemplateMetaMetric> bigTemplates = templateMetas.stream()
                .filter(templateMeta -> templateMeta.getQuota() >= regionCapacity * config.getRegionWatermarkHigh())
                .sorted((t1, t2) -> t2.getQuota().compareTo(t1.getQuota())).collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(bigTemplates)) {
            TemplateMetaMetric templateMax = bigTemplates.get(0);

            LOGGER.info("class=CapacityPlanAreaServiceImpl||method=findMatchedTemplates||templateMax={}||msg=get big template", templateMax);

            if (templateMax.getQuota() > regionCapacity * config.getRegionWatermarkHigh()) {
                return REGION_IS_TOO_SMALL;
            }

            matchedTemplates.add(templateMax);
            return REGION_PLAN_SUCCEED;
        }

        // ????????????????????????  ??????quota???????????????
        double resourceRate = (config.getRegionWatermarkHigh() + config.getRegionWatermarkLow()) / 2;
        Double quotaSum = 0.0;
        for (TemplateMetaMetric templateMeta : templateMetas) {
            if (quotaSum / regionCapacity >= resourceRate) {
                return REGION_PLAN_SUCCEED;
            }

            double templateQuota = templateMeta.getQuota();

            if (quotaSum + templateQuota <= regionCapacity * config.getRegionWatermarkHigh()) {
                quotaSum += templateQuota;
                matchedTemplates.add(templateMeta);
                LOGGER.info("class=CapacityPlanAreaServiceImpl||method=findMatchedTemplates||templateNormal={}||quotaSum={}||msg=get normal template",
                        templateMeta, quotaSum);
            }
        }

        LOGGER.info("class=CapacityPlanAreaServiceImpl||method=findMatchedTemplates||msg=no matched_template for not_full_region");

        return REGION_PLAN_SUCCEED;
    }

    private List<Long> getNoRegionTemplateIds(List<IndexTemplatePhy> templatePhysicals,
                                              Set<String> noRegionRackSet) {
        return templatePhysicals.stream()
                .filter(templatePhysical -> RackUtils.hasIntersect(templatePhysical.getRack(), noRegionRackSet))
                .map(IndexTemplatePhy::getId).collect(Collectors.toList());
    }

    private boolean checkTemplateAllocate(List<IndexTemplatePhy> templatePhysicals, Set<String> hasRegionRackSet,
                                          Set<String> noRegionRackSet) {
        for (IndexTemplatePhy templatePhysical : templatePhysicals) {
            if (RackUtils.hasIntersect(templatePhysical.getRack(), hasRegionRackSet)
                    && RackUtils.hasIntersect(templatePhysical.getRack(), noRegionRackSet)) {
                LOGGER.warn("class=CapacityPlanAreaServiceImpl||method=checkTemplateAllocate||msg=template cross region||template={}||rack={}||hasRegionRackSet={}||noRegionRackSet={}",
                        templatePhysical.getName(), templatePhysical.getRack(), hasRegionRackSet, noRegionRackSet);
                return false;
            }
        }
        return true;
    }

    private Set<String> getHasRegionRacks(List<CapacityPlanRegion> hasExistRegions) {
        Set<String> rackPlanedSet = Sets.newHashSet();
        for (CapacityPlanRegion region : hasExistRegions) {
            rackPlanedSet.addAll(RackUtils.racks2List(region.getRacks()));
        }
        return rackPlanedSet;
    }

    /**
     * ????????????????????????region??????rack
     * @param areaId areaId
     * @param hasRegionRackSet ??????????????????region??????rack
     * @return
     */
    private Set<String> getNoRegionRacks(Long areaId, Set<String> hasRegionRackSet) {

        // ??????racks
        Set<String> areaRacksTotal = listAreaRacks(areaId);
        if (CollectionUtils.isEmpty(areaRacksTotal)) {
            throw new ClusterMetadataException("????????????rack??????");
        }

        // ?????????hasRegionRackSet??????????????????areaRacksTotal
        Set<String> hasRegionRackSetCopy = Sets.newHashSet(hasRegionRackSet);
        hasRegionRackSetCopy.removeAll(areaRacksTotal);
        if (CollectionUtils.isNotEmpty(hasRegionRackSetCopy)) {
            throw new ClusterMetadataException("??????????????????????????????");
        }

        return areaRacksTotal.stream().filter(rack -> !hasRegionRackSet.contains(rack)).collect(Collectors.toSet());
    }

    /**
     * ????????????
     *
     * @param areaDTO ??????area??????
     * @return
     */
    private Result<Void> checkPlanAreaParams(CapacityPlanAreaDTO areaDTO) {
        if (AriusObjUtils.isNull(areaDTO)) {
            return Result.buildParamIllegal("??????????????????");
        }

        if (AriusObjUtils.isNull(areaDTO.getClusterName())) {
            return Result.buildParamIllegal("????????????????????????");
        }

        if (AriusObjUtils.isNull(areaDTO.getResourceId())) {
            return Result.buildParamIllegal("????????????ID??????");
        }

        return Result.buildSucc();
    }

    private void refreshTemplateQuota(List<TemplateMetaMetric> templateMetas) {
        for (TemplateMetaMetric templateMetaMetric : templateMetas) {
            double quota = quotaTool.getTemplateQuotaCountByCpuAndDisk(NodeSpecifyEnum.DOCKER.getCode(),
                    templateMetaMetric.getCombinedCpuCount(), templateMetaMetric.getCombinedDiskG(), TEMPLATE_QUOTA_MIN);
            LOGGER.info("class=CapacityPlanAreaServiceImpl||method=refreshTemplateQuota||templateName={}||srcQuota={}||tgtQuota={}",
                    templateMetaMetric.getTemplateName(), templateMetaMetric.getQuota(), quota);
            templateMetaMetric.setQuota(quota);
        }
    }

    private CapacityPlanAreaPO getCapacityPlanRegionPOFromCache(Long logicClusterId){
        try {
            return cpcCache.get( "CPC@" + logicClusterId, () -> capacityPlanAreaDAO.getPlanClusterByLogicClusterId(logicClusterId));
        } catch (Exception e) {
            return capacityPlanAreaDAO.getPlanClusterByLogicClusterId(logicClusterId);
        }
    }

    /**
     * ??????????????????????????????
     * @param areaId ????????????areaId
     * @return
     */
    private Result<Void> checkTemplateSrvOpen(Long areaId) {
        CapacityPlanArea capacityPlanArea = getAreaById(areaId);

        if (capacityPlanArea == null) {
            return Result.buildFail(String.format("????????????area %s ?????????", areaId));
        }

        return checkTemplateSrvOpen(capacityPlanArea);
    }

    /**
     * ??????????????????????????????
     * @param capacityPlanArea ????????????area
     * @return
     */
    private Result<Void> checkTemplateSrvOpen(CapacityPlanArea capacityPlanArea) {

        if (capacityPlanArea == null) {
            return Result.buildFail("capacityPlanArea?????????");
        }

        if (!isTemplateSrvOpen(capacityPlanArea.getClusterName())) {
            return Result.buildFail(String.format("%s ???????????? %s", capacityPlanArea.getClusterName(), templateServiceName()));
        }

        return Result.buildSucc();
    }
}
