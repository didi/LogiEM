package com.didichuxing.datachannel.arius.admin.extend.capacity.plan.service.impl;

import com.didichuxing.datachannel.arius.admin.biz.cluster.ClusterNodeManager;
import com.didichuxing.datachannel.arius.admin.biz.template.TemplatePhyManager;
import com.didichuxing.datachannel.arius.admin.client.bean.common.RackMetaMetric;
import com.didichuxing.datachannel.arius.admin.client.bean.common.RegionMetric;
import com.didichuxing.datachannel.arius.admin.client.bean.common.Result;
import com.didichuxing.datachannel.arius.admin.client.bean.common.TemplateMetaMetric;
import com.didichuxing.datachannel.arius.admin.common.constant.arius.AriusUser;
import com.didichuxing.datachannel.arius.admin.common.exception.AmsRemoteException;
import com.didichuxing.datachannel.arius.admin.common.util.AriusDateUtils;
import com.didichuxing.datachannel.arius.admin.common.util.BatchProcessor;
import com.didichuxing.datachannel.arius.admin.common.util.ConvertUtil;
import com.didichuxing.datachannel.arius.admin.common.util.RackUtils;
import com.didichuxing.datachannel.arius.admin.extend.capacity.plan.bean.common.CapacityPlanRegionContext;
import com.didichuxing.datachannel.arius.admin.extend.capacity.plan.bean.dto.CapacityPlanRegionTaskDTO;
import com.didichuxing.datachannel.arius.admin.extend.capacity.plan.bean.entity.CapacityPlanRegion;
import com.didichuxing.datachannel.arius.admin.extend.capacity.plan.bean.entity.CapacityPlanRegionTask;
import com.didichuxing.datachannel.arius.admin.extend.capacity.plan.bean.entity.CapacityPlanRegionTaskItem;
import com.didichuxing.datachannel.arius.admin.extend.capacity.plan.bean.po.CapacityPlanRegionTaskItemPO;
import com.didichuxing.datachannel.arius.admin.extend.capacity.plan.bean.po.CapacityPlanRegionTaskPO;
import com.didichuxing.datachannel.arius.admin.extend.capacity.plan.constant.CapacityPlanRegionTaskEnum;
import com.didichuxing.datachannel.arius.admin.extend.capacity.plan.constant.CapacityPlanRegionTaskStatusEnum;
import com.didichuxing.datachannel.arius.admin.extend.capacity.plan.constant.CapacityPlanRegionTaskTypeEnum;
import com.didichuxing.datachannel.arius.admin.extend.capacity.plan.dao.mysql.CapacityPlanRegionTaskDAO;
import com.didichuxing.datachannel.arius.admin.extend.capacity.plan.dao.mysql.CapacityPlanRegionTaskItemDAO;
import com.didichuxing.datachannel.arius.admin.extend.capacity.plan.service.CapacityPlanRegionService;
import com.didichuxing.datachannel.arius.admin.extend.capacity.plan.service.CapacityPlanRegionTaskService;
import com.didiglobal.logi.log.ILog;
import com.didiglobal.logi.log.LogFactory;
import com.google.common.collect.Lists;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author d06679
 * @date 2019-06-24
 */
@Service
public class CapacityPlanRegionTaskServiceImpl implements CapacityPlanRegionTaskService {

    private static final ILog             LOGGER = LogFactory.getLog(CapacityPlanRegionTaskServiceImpl.class);

    @Autowired
    private CapacityPlanRegionTaskDAO     capacityPlanRegionTaskDAO;

    @Autowired
    private CapacityPlanRegionTaskItemDAO capacityPlanRegionTaskItemDAO;

    @Autowired
    private CapacityPlanRegionService     capacityPlanRegionService;

    @Autowired
    private TemplatePhyManager            templatePhyManager;

    @Autowired
    private ClusterNodeManager      clusterNodeManager;

    /**
     * ???????????????region???????????????
     *
     * @param regionId regionId
     * @return ????????????
     */
    @Override
    public List<CapacityPlanRegionTask> getTaskByRegionId(Long regionId) {
        return ConvertUtil.list2List(capacityPlanRegionTaskDAO.getByRegionId(regionId), CapacityPlanRegionTask.class);
    }

    /**
     * ???????????????region???????????????????????????????????????
     *
     * @param regionId regionId
     * @return ?????????region???????????????????????????????????????
     */
    @Override
    public CapacityPlanRegionTask getDecreasingTaskByRegionId(Long regionId) {
        return ConvertUtil.obj2Obj(capacityPlanRegionTaskDAO.getDecreasingTaskByRegionId(regionId), CapacityPlanRegionTask.class);
    }

    /**
     * ???????????????region?????????item??????
     *
     * @param taskId taskId
     * @return region?????????item??????
     */
    @Override
    public List<CapacityPlanRegionTaskItem> getTaskItemByTaskId(Long taskId) {
        return ConvertUtil.list2List(capacityPlanRegionTaskItemDAO.getByTaskId(taskId), CapacityPlanRegionTaskItem.class);
    }

    /**
     * ????????????????????????????????????
     *
     * @return result
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> checkTasks() {

        // ??????????????????????????????????????????
        List<CapacityPlanRegionTaskPO> movingTaskPOs = capacityPlanRegionTaskDAO
            .getByStatus(CapacityPlanRegionTaskStatusEnum.DATA_MOVING.getCode());
        if (CollectionUtils.isEmpty(movingTaskPOs)) {
            return Result.buildSucc();
        }

        boolean result = true;
        for (CapacityPlanRegionTaskPO taskPO : movingTaskPOs) {
            try {
                CapacityPlanRegion region = capacityPlanRegionService.getRegionById(taskPO.getRegionId());
                if (shardMoveFinish(taskPO, region)) {
                    if (finishTask(taskPO.getId()) && changeRegionRacks(taskPO)) {
                        LOGGER.info("class=CapacityPlanRegionTaskServiceImpl||method=checkTasks||regionId={}||msg=succ", taskPO.getRegionId());
                    } else {
                        result = false;
                        LOGGER.warn("class=CapacityPlanRegionTaskServiceImpl||method=checkTasks||regionId={}||msg=fail", taskPO.getRegionId());
                    }
                } else {
                    LOGGER.info("class=CapacityPlanRegionTaskServiceImpl||method=checkTasks||regionId={}||deltaRack={}||msg=has shard", taskPO.getRegionId(),
                        taskPO.getDeltaRacks());
                }
            } catch (Exception e) {
                LOGGER.warn("class=CapacityPlanRegionTaskServiceImpl||method=checkTasks||regionId={}||errorMsg={}", taskPO.getRegionId(), e.getMessage(), e);
                result = false;
            }
        }

        return Result.build(result);
    }

    /**
     * ????????????
     *
     * @param taskId ??????id
     * @return result
     */
    @Override
    public boolean finishTask(Long taskId) {
        return 1 == capacityPlanRegionTaskDAO.updateStatus(taskId, CapacityPlanRegionTaskStatusEnum.FINISHED.getCode());
    }

    /**
     * ??????region???????????????
     *
     * @param regionId        regionId
     * @param racks           racks
     * @param templateMetaMetrics ???????????????
     * @return result
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveInitTask(Long regionId, String racks, List<TemplateMetaMetric> templateMetaMetrics) {
        Date now = new Date();
        CapacityPlanRegionTaskDTO dto = new CapacityPlanRegionTaskDTO();
        dto.setRegionId(regionId);
        dto.setTask(CapacityPlanRegionTaskEnum.INIT.getCode());
        dto.setType(CapacityPlanRegionTaskTypeEnum.INIT.getCode());
        dto.setStatus(CapacityPlanRegionTaskStatusEnum.EXE_PENDING.getCode());
        dto.setSrcRacks(racks);
        dto.setDeltaRacks("");
        dto.setRegionCostDiskG(0.0);
        dto.setRegionCostCpuCount(0.0);
        dto.setStartTime(now);
        dto.setFinishTime(now);
        dto.setTaskItems(buildTaskItems(templateMetaMetrics));
        return saveTask(dto);
    }

    /**
     * ???????????????region??????
     *
     * @param taskId taskId
     * @return ????????????
     */
    @Override
    public Result<Void> exeInitTask(Long taskId) {
        CapacityPlanRegionTaskPO taskPO = capacityPlanRegionTaskDAO.getById(taskId);

        if (!taskPO.getStatus().equals(CapacityPlanRegionTaskStatusEnum.EXE_PENDING.getCode())) {
            LOGGER.warn("class=CapacityPlanRegionTaskServiceImpl||method=exeInitTask||taskId={}||msg=status illegal", taskPO.getRegionId(), taskId);
            return Result.buildParamIllegal("??????????????????");
        }

        List<CapacityPlanRegionTaskItemPO> itemPOS = capacityPlanRegionTaskItemDAO.getByTaskId(taskId);

        boolean succ = true;
        for (CapacityPlanRegionTaskItemPO itemPO : itemPOS) {
            try {
                Result<Void> result = templatePhyManager.editTemplateRackWithoutCheck(itemPO.getPhysicalId(),
                    taskPO.getSrcRacks(), AriusUser.CAPACITY_PLAN.getDesc(), 5);
                if (result.failed()) {
                    succ = false;
                    LOGGER.warn("class=CapacityPlanRegionTaskServiceImpl||method=exeInitTask||taskId={}||template={}||tgtRack={}||msg=fail", taskId,
                        itemPO.getTemplateName(), taskPO.getSrcRacks());
                } else {
                    LOGGER.info("class=CapacityPlanRegionTaskServiceImpl||method=exeInitTask||taskId={}||template={}||tgtRack={}||msg=succ", taskId,
                        itemPO.getTemplateName(), taskPO.getSrcRacks());
                }
            } catch (Exception e) {
                succ = false;
                LOGGER.warn("class=CapacityPlanRegionTaskServiceImpl||method=exeInitTask||taskId={}||template={}||tgtRack={}||errorMsg={}", taskId,
                    itemPO.getTemplateName(), taskPO.getSrcRacks(), e.getMessage(), e);
            }
        }

        if (succ) {
            capacityPlanRegionTaskDAO.updateStatus(taskId, CapacityPlanRegionTaskStatusEnum.FINISHED.getCode());
        }

        return Result.build(succ);
    }

    /**
     * ????????????
     *
     * @param typeEnum      ????????????
     * @param regionContext ?????????
     * @param deltaRacks    ?????????rack
     * @param statusEnum    ????????????
     * @return true/false
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveTask(CapacityPlanRegionTaskTypeEnum typeEnum,
                            CapacityPlanRegionContext regionContext,
                            List<String> deltaRacks,
                            CapacityPlanRegionTaskStatusEnum statusEnum) {
        CapacityPlanRegionTaskDTO dto = buildTaskDto(regionContext, RackUtils.list2Racks(deltaRacks), statusEnum, typeEnum);
        return saveTask(dto);
    }

    /**
     * ??????region????????????checktask
     *
     * @param regionId regionId
     * @return task
     */
    @Override
    public CapacityPlanRegionTask getLastCheckTask(Long regionId) {
        return ConvertUtil.obj2Obj(capacityPlanRegionTaskDAO.getLastCheckTask(regionId), CapacityPlanRegionTask.class);
    }

    /**
     * ??????region?????????task
     *
     * @param regionId region
     * @return true/false
     */
    @Override
    public int deleteTasksByRegionId(Long regionId) {
        return capacityPlanRegionTaskDAO.deleteByRegionId(regionId);
    }

    /**
     * ????????????days?????????????????????
     *
     * @param regionId regionID
     * @param days     ??????
     * @return task
     */
    @Override
    public CapacityPlanRegionTask getLastDecreaseTask(Long regionId, int days) {
        Date end = new Date();
        Date start = AriusDateUtils.getBeforeDays(end, days);
        return ConvertUtil.obj2Obj(capacityPlanRegionTaskDAO.getLastDecreaseTask(regionId, start, end),
            CapacityPlanRegionTask.class);
    }

    /***************************************** private method ****************************************************/

    private CapacityPlanRegionTaskDTO buildTaskDto(CapacityPlanRegionContext regionContext, String deltaRacks,
                                                   CapacityPlanRegionTaskStatusEnum statusEnum,
                                                   CapacityPlanRegionTaskTypeEnum typeEnum) {
        Date now = new Date();
        CapacityPlanRegionTaskDTO dto = new CapacityPlanRegionTaskDTO();
        dto.setRegionId(regionContext.getRegion().getRegionId());
        dto.setTask(regionContext.getTaskType());
        dto.setType(typeEnum.getCode());
        dto.setStatus(statusEnum.getCode());
        dto.setSrcRacks(regionContext.getRegion().getRacks());
        dto.setDeltaRacks(deltaRacks);
        dto.setRegionCostDiskG(regionContext.getRegionCostDiskG());
        dto.setRegionCostCpuCount(regionContext.getRegionCostCpuCount());
        dto.setStartTime(now);
        dto.setFinishTime(now);

        List<CapacityPlanRegionTaskItem> items = regionContext.getTemplateMetaMetrics().stream().map(metric -> {
            CapacityPlanRegionTaskItem item = ConvertUtil.obj2Obj(metric, CapacityPlanRegionTaskItem.class);
            item.setHotDay(metric.getHotTime());
            return item;
        }).collect(Collectors.toList());
        dto.setTaskItems(items);

        return dto;
    }

    private boolean saveTask(CapacityPlanRegionTaskDTO taskDTO) {
        LOGGER.info("class=CapacityPlanRegionTaskServiceImpl||method=saveTask||regionId={}||taskItems={}", taskDTO.getRegionId(),
            CollectionUtils.isEmpty(taskDTO.getTaskItems()) ? 0 : taskDTO.getTaskItems().size());

        CapacityPlanRegionTaskPO taskPO = ConvertUtil.obj2Obj(taskDTO, CapacityPlanRegionTaskPO.class);
        boolean succ = 1 == capacityPlanRegionTaskDAO.insert(taskPO);
        if (succ) {
            List<CapacityPlanRegionTaskItemPO> itemPOS = ConvertUtil.list2List(taskDTO.getTaskItems(),
                CapacityPlanRegionTaskItemPO.class);

            for (CapacityPlanRegionTaskItemPO itemPO : itemPOS) {
                itemPO.setTaskId(taskPO.getId());
            }

            BatchProcessor.BatchProcessResult<CapacityPlanRegionTaskItemPO, Integer> batchResult = new BatchProcessor<CapacityPlanRegionTaskItemPO, Integer>()
                .batchList(itemPOS).batchSize(20).processor(items -> capacityPlanRegionTaskItemDAO.insertBatch(items))
                .process();

            if (!batchResult.isSucc() && (batchResult.getErrorMap().size() > 0)) {
                LOGGER.warn("class=CapacityPlanRegionTaskServiceImpl||method=saveTask||taskId={}||errMsg={}", taskPO.getId(), batchResult.getErrorMap());
            }
        }

        return succ;
    }

    private List<CapacityPlanRegionTaskItem> buildTaskItems(List<TemplateMetaMetric> templateMetaMetrics) {
        List<CapacityPlanRegionTaskItem> taskItems = Lists.newArrayList();

        for (TemplateMetaMetric templateMetaMetric : templateMetaMetrics) {
            CapacityPlanRegionTaskItem taskItem = ConvertUtil.obj2Obj(templateMetaMetric,
                CapacityPlanRegionTaskItem.class);

            taskItem.setSumIndexSizeG(0.0);
            taskItem.setSumDocCount(0L);
            taskItem.setHotDay(0);
            taskItem.setMaxTps(0.0);
            taskItem.setMaxQueryTime(0.0);
            taskItem.setMaxScrollTime(0.0);
            taskItem.setReplicaNum(0);
            taskItem.setActualCpuCount(0.0);
            taskItem.setActualDiskG(0.0);
            taskItem.setQuotaCpuCount(0.0);
            taskItem.setQuotaDiskG(0.0);
            taskItem.setCombinedCpuCount(0.0);
            taskItem.setCombinedDiskG(0.0);

            taskItems.add(taskItem);
        }

        return taskItems;
    }

    private boolean changeRegionRacks(CapacityPlanRegionTaskPO taskPO) {
        // ?????????racks
        String tgtRack = RackUtils.removeRacks(taskPO.getSrcRacks(), taskPO.getDeltaRacks());

        LOGGER.info("class=CapacityPlanRegionTaskServiceImpl||method=changeRegionRack||regionId={}||tgtRack={}", taskPO.getRegionId(), tgtRack);
        return capacityPlanRegionService.modifyRegionRacks(taskPO.getRegionId(), tgtRack);
    }

    /**
     * ????????????????????????????????????????????????
     * @param taskPO ??????
     * @param region region
     * @return
     */
    private boolean shardMoveFinish(CapacityPlanRegionTaskPO taskPO, CapacityPlanRegion region) {

        // ?????????????????????rack????????????????????????
        Result<List<RackMetaMetric>> rackMetaMetricsResult = clusterNodeManager.metaAndMetric(
            region.getClusterName(), RackUtils.racks2List(taskPO.getDeltaRacks()));

        if (rackMetaMetricsResult.failed()) {
            throw new AmsRemoteException("?????????????????????????????????" + rackMetaMetricsResult.getMessage());
        }
        List<RackMetaMetric> rackMetaMetrics = rackMetaMetricsResult.getData();

        // ??????????????????
        RegionMetric freeRacks = capacityPlanRegionService.calcRegionMetric(rackMetaMetrics);

        // ??????free?????????????????????????????????????????????
        return freeRacks.getDiskFreeG() / freeRacks.getResource().getDisk() > region.getConfig()
            .getNodeDiskFreeThreshold();
    }
}
