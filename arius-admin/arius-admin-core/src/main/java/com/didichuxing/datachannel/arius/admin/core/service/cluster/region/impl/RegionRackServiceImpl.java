package com.didichuxing.datachannel.arius.admin.core.service.cluster.region.impl;

import java.util.*;
import java.util.stream.Collectors;

import com.didichuxing.datachannel.arius.admin.client.bean.common.Result;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.cluster.ESLogicClusterRackInfoDTO;
import com.didichuxing.datachannel.arius.admin.client.constant.operaterecord.OperationEnum;
import com.didichuxing.datachannel.arius.admin.client.constant.resource.ResourceLogicTypeEnum;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ClusterLogic;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ClusterLogicRackInfo;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ClusterPhy;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.region.ClusterRegion;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.template.IndexTemplatePhy;
import com.didichuxing.datachannel.arius.admin.common.bean.po.cluster.ClusterRegionPO;
import com.didichuxing.datachannel.arius.admin.common.component.SpringTool;
import com.didichuxing.datachannel.arius.admin.common.constant.AdminConstant;
import com.didichuxing.datachannel.arius.admin.common.event.region.RegionBindEvent;
import com.didichuxing.datachannel.arius.admin.common.event.region.RegionCreateEvent;
import com.didichuxing.datachannel.arius.admin.common.event.region.RegionDeleteEvent;
import com.didichuxing.datachannel.arius.admin.common.event.region.RegionUnbindEvent;
import com.didichuxing.datachannel.arius.admin.common.exception.AdminOperateException;
import com.didichuxing.datachannel.arius.admin.common.util.AriusObjUtils;
import com.didichuxing.datachannel.arius.admin.common.util.ConvertUtil;
import com.didichuxing.datachannel.arius.admin.common.util.ListUtils;
import com.didichuxing.datachannel.arius.admin.common.util.RackUtils;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.logic.ClusterLogicService;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.physic.ClusterPhyService;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.region.RegionRackService;
import com.didichuxing.datachannel.arius.admin.core.service.common.OperateRecordService;
import com.didichuxing.datachannel.arius.admin.core.service.template.physic.TemplatePhyService;
import com.didichuxing.datachannel.arius.admin.persistence.mysql.region.ClusterRegionDAO;
import com.didiglobal.logi.log.ILog;
import com.didiglobal.logi.log.LogFactory;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import static com.didichuxing.datachannel.arius.admin.client.constant.operaterecord.ModuleEnum.CLUSTER_REGION;
import static com.didichuxing.datachannel.arius.admin.client.constant.operaterecord.ModuleEnum.REGION;
import static com.didichuxing.datachannel.arius.admin.client.constant.operaterecord.OperationEnum.*;

/**
 * @Author: lanxinzheng
 * @Date: 2021/1/2
 * @Comment:
 */
@Service
public class RegionRackServiceImpl implements RegionRackService {

    private static final ILog    LOGGER = LogFactory.getLog(RegionRackServiceImpl.class);

    private static final String REGION_NOT_EXIST = "region %d ?????????";

    @Autowired
    private ClusterRegionDAO     clusterRegionDAO;

    @Autowired
    private ClusterLogicService  clusterLogicService;

    @Autowired
    private ClusterPhyService    esClusterPhyService;

    @Autowired
    private OperateRecordService operateRecordService;

    @Autowired
    private TemplatePhyService   templatePhyService;

    /**
     *
     * @param rackId Rack ID
     * @return
     * @deprecated
     */
    @Deprecated
    @Override
    public boolean deleteRackById(Long rackId) {
        return false;
    }

    @Override
    public ClusterRegion getRegionById(Long regionId) {
        if (regionId == null) {
            return null;
        }
        return ConvertUtil.obj2Obj(clusterRegionDAO.getById(regionId), ClusterRegion.class);
    }

    @Override
    public List<ClusterLogicRackInfo> listAllLogicClusterRacks() {

        // ????????????????????????region
        List<ClusterRegion> regions = listAllBoundRegions();

        // ??????????????????????????????????????????????????????
        regions.sort(Comparator.comparing(ClusterRegion::getLogicClusterIds));

        // ??????rack??????
        return buildRackInfos(regions);

    }

    @Override
    public List<ClusterLogicRackInfo> listLogicClusterRacks(ESLogicClusterRackInfoDTO param) {
        return listLogicClusterRacks(param.getLogicClusterId(), param.getPhyClusterName());
    }

    @Override
    public List<ClusterLogicRackInfo> listLogicClusterRacks(Long logicClusterId) {
        List<ClusterRegion> regions = listLogicClusterRegions(logicClusterId);
        return buildRackInfos(regions);
    }

    @Override
    public List<ClusterLogicRackInfo> listLogicClusterRacks(Long logicClusterId, String phyClusterName) {
        ClusterRegionPO condt = new ClusterRegionPO();
        if (null != logicClusterId) {
            condt.setLogicClusterIds(logicClusterId.toString());
        }
        if (StringUtils.isNotBlank(phyClusterName)) {
            condt.setPhyClusterName(phyClusterName);
        }

        List<ClusterRegion> regions = ConvertUtil.list2List(clusterRegionDAO.listBoundRegionsByCondition(condt), ClusterRegion.class);
        return buildRackInfos(regions);
    }

    @Override
    public Result addRackToLogicCluster(ESLogicClusterRackInfoDTO param, String operator) {
        return Result.buildFail("???????????????????????????????????????rack??????");
    }

    @Override
    public List<ClusterLogicRackInfo> listAssignedRacksByClusterName(String phyClusterName) {

        List<ClusterRegion> regionsInPhyCluster = listRegionsByClusterName(phyClusterName);
        List<ClusterRegion> boundRegions = regionsInPhyCluster.stream().filter(this::isRegionBound)
            .collect(Collectors.toList());

        return buildRackInfos(boundRegions);
    }

    @Override
    public List<String> listPhysicClusterNames(Long logicClusterId) {
        // ????????????????????????region
        List<ClusterRegion> regions = listLogicClusterRegions(logicClusterId);
        // ???region?????????????????????
        return regions.stream().map(ClusterRegion::getPhyClusterName).distinct().collect(Collectors.toList());
    }

    @Override
    public List<Integer> listPhysicClusterId(Long logicClusterId) {
        List<String> clusterNames = listPhysicClusterNames(logicClusterId);
        // ????????????????????????????????????ID
        return clusterNames.stream().map(clusterName -> esClusterPhyService.getClusterByName(clusterName).getId())
            .collect(Collectors.toList());
    }

    @Override
    public int countRackMatchedRegion(String cluster, String racks) {

        if (StringUtils.isAnyBlank(cluster, racks)) {
            return 0;
        }

        // ????????????????????????region
        List<ClusterRegion> regions = listRegionsByClusterName(cluster);
        // ????????????region??????
        int count = 0;
        for (ClusterRegion region : regions) {
            if (RackUtils.hasIntersect(racks, RackUtils.racks2List(region.getRacks()))) {
                count++;
            }
        }
        return count;
    }

    @Override
    public List<ClusterRegion> listRegionsByLogicAndPhyCluster(Long logicClusterId, String phyClusterName) {
        if (logicClusterId == null || StringUtils.isBlank(phyClusterName)) {
            return new ArrayList<>();
        }

        ClusterRegionPO condt = new ClusterRegionPO();
        condt.setLogicClusterIds(logicClusterId.toString());
        condt.setPhyClusterName(phyClusterName);

        return ConvertUtil.list2List(clusterRegionDAO.listBoundRegionsByCondition(condt), ClusterRegion.class);
    }

    @Override
    public List<ClusterRegion> listPhyClusterRegions(String phyClusterName) {
        return ConvertUtil.list2List(clusterRegionDAO.getByPhyClusterName(phyClusterName), ClusterRegion.class);
    }

    @Override
    public List<ClusterRegion> listAllBoundRegions() {
        return ConvertUtil.list2List(clusterRegionDAO.listBoundRegions(), ClusterRegion.class);
    }

    @Override
    public Result<Long> createPhyClusterRegion(String clusterName, String racks, Integer share, String operator) {
        Result<Void> validResult = validCreateRegionInfo(clusterName, racks, share);
        if (validResult.failed()) {
            return Result.buildFrom(validResult);
        }

        ClusterRegionPO clusterRegionPO = new ClusterRegionPO();
        clusterRegionPO.setLogicClusterIds(AdminConstant.REGION_NOT_BOUND_LOGIC_CLUSTER_ID.toString());
        clusterRegionPO.setPhyClusterName(clusterName);
        clusterRegionPO.setRacks(racks);

        // ??????
        boolean succeed = clusterRegionDAO.insert(clusterRegionPO) == 1;
        LOGGER.info(
            "class=RegionRackServiceImpl||method=createPhyClusterRegion||region={}||result={}||msg=create phy cluster region",
            clusterRegionPO, succeed);

        if (succeed) {
            // ????????????
            SpringTool.publish(new RegionCreateEvent(this, ConvertUtil.obj2Obj(clusterRegionPO, ClusterRegion.class),
                share, operator));
            // ????????????
            operateRecordService.save(CLUSTER_REGION, ADD, clusterRegionPO.getId(), "", operator);
        }

        return Result.build(succeed, clusterRegionPO.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Result<Long> createAndBindRegion(String clusterName, String racks, Long logicClusterId, Integer share,
                                            String operator) {
        try {
            // ??????region
            Result<Long> createRegionResult = createPhyClusterRegion(clusterName, racks, share, operator);
            if (createRegionResult.failed()) {
                throw new AdminOperateException(createRegionResult.getMessage());
            }
            Long regionId = createRegionResult.getData();

            // ??????region
            Result<Void> bindResult = bindRegion(regionId, logicClusterId, share, operator);
            if (bindResult.failed()) {
                throw new AdminOperateException(bindResult.getMessage());
            }

            return Result.buildSucc(regionId);
        } catch (Exception e) {
            LOGGER.error(
                "class=RegionRackServiceImpl||method=createAndBindRegion||clusterName={}||racks={}||logicClusterId={}||share={}"
                         + "||operator={}||msg=create and bind region failed||e->",
                clusterName, racks, logicClusterId, share, operator, e);
            // ????????????
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return Result.buildFail(e.getMessage());
        }
    }

    @Override
    public Result<Void> deletePhyClusterRegion(Long regionId, String operator) {
        if (regionId == null) {
            return Result.buildFail("regionId???null");
        }

        ClusterRegion region = getRegionById(regionId);
        if (region == null) {
            return Result.buildFail(String.format(REGION_NOT_EXIST, regionId));
        }

        // ??????????????????region????????????
        if (isRegionBound(region)) {
            // ???????????????????????????,??????region?????????????????????????????????
            List<Long> logicClusterIds = ListUtils.string2LongList(region.getLogicClusterIds());
            List<String> logicClusterNames = Lists.newArrayList();
            for (Long logicClusterId : logicClusterIds) {
                ClusterLogic clusterLogic = clusterLogicService.getClusterLogicById(logicClusterId);
                if (AriusObjUtils.isNull(clusterLogic)) {
                    continue;
                }
                // ?????????????????????????????????????????????
                logicClusterNames.add(clusterLogic.getName());
            }

            return Result.buildFail(String.format("region %d ?????????????????????????????? %s", regionId, ListUtils.strList2String(logicClusterNames)));
        }

        boolean succeed = clusterRegionDAO.delete(regionId) == 1;

        if (succeed) {
            // ????????????
            SpringTool.publish(new RegionDeleteEvent(this, region, operator));
            // ????????????
            operateRecordService.save(CLUSTER_REGION, DELETE, regionId, "", operator);
        }

        return Result.build(succeed);
    }

    @Override
    public Result<Void> deleteByClusterPhy(String clusterPhyName, String operator) {
        return Result.build(0 < clusterRegionDAO.deleteByClusterPhyName(clusterPhyName));
    }

    @Override
    public Result<Void> deletePhyClusterRegionWithoutCheck(Long regionId, String operator) {
        if (regionId == null) {
            return Result.buildFail("regionId???null");
        }

        ClusterRegion regionPO = getRegionById(regionId);
        if (regionPO == null) {
            return Result.buildFail(String.format(REGION_NOT_EXIST, regionId));
        }

        boolean succeed = clusterRegionDAO.delete(regionId) == 1;
        if (succeed) {
            // ????????????
            SpringTool.publish(new RegionDeleteEvent(this, regionPO, operator));
            // ????????????
            operateRecordService.save(CLUSTER_REGION, DELETE, regionId, "", operator);
        }
        return Result.build(succeed);
    }

    @Override
    public Result<Void> bindRegion(Long regionId, Long logicClusterId, Integer share, String operator) {

        try {
            // ??????region??????
            ClusterRegion region = getRegionById(regionId);
            if (region == null) {
                return Result.buildFail(String.format(REGION_NOT_EXIST, regionId));
            }

            // ????????????????????????
            ClusterLogic clusterLogic = clusterLogicService.getClusterLogicById(logicClusterId);
            if (AriusObjUtils.isNull(clusterLogic)) {
                return Result.buildFail(String.format("???????????? %S ?????????", logicClusterId));
            }

            // ????????????????????????,??????region????????????????????????????????????????????????????????????????????????region????????????????????????????????????????????????
            if (isRegionBound(region)) {
                if (!isRegionBindByPublicLogicCluster(region)) {
                    return Result.buildFail(String.format("region %d ????????????????????????????????????",regionId));
                }

                if (!clusterLogic.getType().equals(ResourceLogicTypeEnum.PUBLIC.getCode())) {
                    return Result.buildFail(String.format("region %d ???????????????,?????????????????? %s ??????????????????",
                            regionId, clusterLogic.getName()));
                }
            }

            if (share == null) {
                share = AdminConstant.YES;
            }

            if (!share.equals(AdminConstant.YES) && !share.equals(AdminConstant.NO)) {
                return Result.buildParamIllegal("?????????share??????");
            }

            // ??????
            updateRegion(regionId, constructNewLogicIds(logicClusterId,region.getLogicClusterIds()), null);

            // ?????????????????????????????????area????????????????????????????????????????????????
            SpringTool.publish(new RegionBindEvent(this, region, share, operator));
            operateRecordService.save(REGION, OperationEnum.REGION_BIND, regionId, "", operator);

            return Result.buildSucc();
        } catch (Exception e) {
            LOGGER.error(
                "class=RegionRackServiceImpl||method=bindRegion||regionId={}||logicClusterId={}||share={}||operator={}"
                         + "msg=bind region failed||e->",
                regionId, logicClusterId, share, operator, e);
            return Result.buildFail(e.getMessage());
        }
    }

    private String constructNewLogicIds(Long newLogicClusterId, String oldLogicClusterIds) {
        // region??????????????????????????????
        if (oldLogicClusterIds.equals(AdminConstant.REGION_NOT_BOUND_LOGIC_CLUSTER_ID)) {
            return newLogicClusterId.toString();
        }

        // region?????????,??????????????????
        return oldLogicClusterIds + "," + newLogicClusterId.toString();
    }

    @Override
    public Result<Void> editRegionRacks(Long regionId, String racks, String operator) {
        if (regionId == null) {
            return Result.buildFail("?????????regionId");
        }

        ClusterRegion region = getRegionById(regionId);
        if (region == null) {
            return Result.buildFail(String.format(REGION_NOT_EXIST, regionId));
        }

        Result<Void> checkRacksResult = checkRacks(region.getPhyClusterName(), racks);
        if (checkRacksResult.failed()) {
            return checkRacksResult;
        }

        // ??????rack
        String oldRacks = region.getRacks();
        updateRegion(regionId, null, racks.trim());

        LOGGER.info(
            "class=RegionRackServiceImpl||method=editRegionRacks||regionId={}||oldRacks={}||newRacks={}||operator={}"
                    + "msg=edit region",
            regionId, oldRacks, racks, operator);

        // ????????????
        operateRecordService.save(REGION, EDIT, regionId, String.format("%s -> %s", oldRacks, racks), operator);

        return Result.buildSucc();
    }

    @Override
    public Result<Void> unbindRegion(Long regionId, Long logicClusterId, String operator) {
        try {
            if (regionId == null) {
                return Result.buildFail("?????????regionId");
            }
            // ??????region??????
            ClusterRegion region = getRegionById(regionId);
            if (region == null) {
                return Result.buildFail(String.format(REGION_NOT_EXIST, regionId));
            }

            // ?????????????????????
            if (!isRegionBound(region)) {
                return Result.buildFail(String.format("region %d ????????????", regionId));
            }

            // ??????region???????????????
            List<IndexTemplatePhy> clusterTemplates = templatePhyService.getTemplateByRegionId(regionId);
            if (CollectionUtils.isNotEmpty(clusterTemplates)) {
                return Result.buildFail(String.format("region %d ?????????????????????", regionId));
            }

            // ????????????
            updateRegion(regionId, getNewBoundLogicIds(region,logicClusterId), null);

            // ?????????????????????????????????????????????
            SpringTool.publish(new RegionUnbindEvent(this, region, operator));


            // ????????????
            operateRecordService.save(REGION, OperationEnum.REGION_UNBIND, regionId, "", operator);

            return Result.buildSucc();
        } catch (Exception e) {
            LOGGER.error("class=RegionRackServiceImpl||method=unbindRegion||regionId={}||operator={}"
                         + "msg=unbind region failed||e->",
                regionId, operator, e);
            return Result.buildFail(e.getMessage());
        }
    }

    /**
     * ??????region???????????????????????????????????????????????????id??????
     * @param region region
     * @param logicClusterId ????????????id
     * @return region??????????????????id??????
     */
    private String getNewBoundLogicIds(ClusterRegion region, Long logicClusterId) {
        // ??????region??????????????????????????????id??????
        List<Long> boundLogicClusterIds = ListUtils.string2LongList(region.getLogicClusterIds());

        // ????????????????????????????????????id??????region?????????????????????????????????region???????????????????????????????????????????????????????????????-1
        if (AriusObjUtils.isNull(logicClusterId)
                || CollectionUtils.isEmpty(boundLogicClusterIds)
                || (boundLogicClusterIds.size() == 1 && boundLogicClusterIds.contains(logicClusterId))) {
            return AdminConstant.REGION_NOT_BOUND_LOGIC_CLUSTER_ID;
        }

        // ????????????????????????
        boundLogicClusterIds.remove(logicClusterId);
        return ListUtils.longList2String(boundLogicClusterIds);
    }

    /**
     * ???????????????????????????region
     * @param logicClusterId ????????????ID
     * @return ?????????????????????region
     */
    @Override
    public List<ClusterRegion> listLogicClusterRegions(Long logicClusterId) {

        if (logicClusterId == null) {
            return new ArrayList<>();
        }

        List<ClusterRegionPO> clusterRegionPOS = clusterRegionDAO.listAll()
                .stream()
                .filter(clusterRegionPO -> ListUtils.string2LongList(clusterRegionPO.getLogicClusterIds()).contains(logicClusterId))
                .collect(Collectors.toList());

        return ConvertUtil.list2List(clusterRegionPOS, ClusterRegion.class);
    }

    /**
     * ??????????????????region
     * @param phyClusterName ???????????????
     * @return ??????????????????region
     */
    @Override
    public List<ClusterRegion> listRegionsByClusterName(String phyClusterName) {
        if (StringUtils.isBlank(phyClusterName)) {
            return new ArrayList<>();
        }
        return ConvertUtil.list2List(clusterRegionDAO.getByPhyClusterName(phyClusterName), ClusterRegion.class);
    }

    /**
     * ??????region????????????????????????????????????
     * @param region region
     * @return true-??????????????????false-???????????????
     */
    @Override
    public boolean isRegionBound(ClusterRegion region) {
        if (region == null) {
            return false;
        }

        return !region.getLogicClusterIds().equals(AdminConstant.REGION_NOT_BOUND_LOGIC_CLUSTER_ID);
    }

    /**
     * ??????region??????????????????????????????????????????
     * @param region region??????
     * @return
     */
    private boolean isRegionBindByPublicLogicCluster(ClusterRegion region) {
        if (!isRegionBound(region)) {
            return false;
        }

        // ??????????????????????????????region?????????????????????
        Long logicClusterId = ListUtils.string2LongList(region.getLogicClusterIds()).get(0);
        ClusterLogic clusterLogic = clusterLogicService.getClusterLogicById(logicClusterId);

        return !AriusObjUtils.isNull(clusterLogic) && clusterLogic.getType().equals(ResourceLogicTypeEnum.PUBLIC.getCode());
    }

    @Override
    public Set<Long> getLogicClusterIdByPhyClusterId(Integer phyClusterId) {
        ClusterPhy clusterPhy = esClusterPhyService.getClusterById(phyClusterId);
        if (clusterPhy == null) {
            return null;
        }
        List<ClusterRegion> clusterRegions = listRegionsByClusterName(clusterPhy.getCluster());
        if (CollectionUtils.isEmpty(clusterRegions)) {
            return null;
        }

        // ???????????????????????????????????????????????????????????????
        Set<Long> logicClusterIds = Sets.newHashSet();
        clusterRegions.forEach(clusterRegion -> logicClusterIds.addAll(new HashSet<>(ListUtils.string2LongList(clusterRegion.getLogicClusterIds()))));
        return logicClusterIds;
    }

    /***************************************** private method ****************************************************/
    /**
     * ??????region??????rack??????
     * @param region region
     * @return
     */
    private List<ClusterLogicRackInfo> buildRackInfos(ClusterRegion region) {
        List<ClusterLogicRackInfo> rackInfos = new LinkedList<>();
        if (region == null) {
            return rackInfos;
        }

        for (String rack : RackUtils.racks2List(region.getRacks())) {
            ClusterLogicRackInfo rackInfo = new ClusterLogicRackInfo();
            rackInfo.setLogicClusterIds(region.getLogicClusterIds());
            rackInfo.setPhyClusterName(region.getPhyClusterName());
            rackInfo.setRegionId(region.getId());
            rackInfo.setRack(rack);
            rackInfos.add(rackInfo);
        }

        return rackInfos;
    }

    private List<ClusterLogicRackInfo> buildRackInfos(List<ClusterRegion> regions) {
        List<ClusterLogicRackInfo> rackInfos = new LinkedList<>();
        if (CollectionUtils.isEmpty(regions)) {
            return rackInfos;
        }

        for (ClusterRegion region : regions) {
            rackInfos.addAll(buildRackInfos(region));
        }
        return rackInfos;
    }

    /**
     * ??????regionId??????region???logicClusterId???racks
     * @param regionId       ????????????region???ID
     * @param logicClusterIds ????????????ID????????????null????????????
     * @param racks          racks??????null????????????
     */
    private void updateRegion(Long regionId, String logicClusterIds, String racks) {
        if (regionId == null) {
            return;
        }

        ClusterRegionPO updateParam = new ClusterRegionPO();
        updateParam.setId(regionId);
        updateParam.setLogicClusterIds(logicClusterIds);
        updateParam.setRacks(racks);

        clusterRegionDAO.update(updateParam);
    }

    private Result<Void> checkRacks(String phyClusterName, String racks) {

        Set<String> rackSet = RackUtils.racks2Set(racks);
        if (CollectionUtils.isEmpty(rackSet)) {
            return Result.buildParamIllegal("racks is blank");
        }

        Set<String> racksInCluster = esClusterPhyService.getClusterRacks(phyClusterName);

        if (!racksInCluster.containsAll(rackSet)) {
            return Result.buildParamIllegal(String.format("racks %s not found in cluster %s",
                RackUtils.removeRacks(racks, racksInCluster), phyClusterName));
        }

        return Result.buildSucc();
    }

    private Result<Void> validCreateRegionInfo(String clusterName, String racks, Integer share) {
        // ????????????
        if (StringUtils.isBlank(clusterName)) {
            return Result.buildParamIllegal("???????????????????????????");
        }

        if (esClusterPhyService.getClusterByName(clusterName) == null) {
            return Result.buildParamIllegal(String.format("???????????? %s ?????????", clusterName));
        }

        if (AriusObjUtils.isNull(racks)) {
            return Result.buildParamIllegal("racks??????");
        }

        //?????????rack????????????cold??????
        List<String> rackList = ListUtils.string2StrList(racks);
        if (rackList.contains("cold")) {
            return Result.buildParamIllegal("???cold????????????????????????????????????region");
        }

        if (share == null) {
            share = AdminConstant.YES;
        }

        if (!share.equals(AdminConstant.YES) && !share.equals(AdminConstant.NO)) {
            return Result.buildParamIllegal("?????????share??????");
        }

        return Result.buildSucc();
    }

}
