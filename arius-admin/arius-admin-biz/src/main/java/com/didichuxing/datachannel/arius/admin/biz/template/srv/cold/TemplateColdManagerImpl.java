package com.didichuxing.datachannel.arius.admin.biz.template.srv.cold;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.didichuxing.datachannel.arius.admin.biz.template.srv.base.BaseTemplateSrv;
import com.didichuxing.datachannel.arius.admin.client.bean.common.Result;
import com.didichuxing.datachannel.arius.admin.client.constant.operaterecord.ModuleEnum;
import com.didichuxing.datachannel.arius.admin.common.Tuple;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.template.IndexTemplatePhy;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.template.IndexTemplatePhyWithLogic;
import com.didichuxing.datachannel.arius.admin.common.constant.AdminConstant;
import com.didichuxing.datachannel.arius.admin.common.constant.template.TemplateServiceEnum;
import com.didichuxing.datachannel.arius.admin.common.exception.ESOperateException;
import com.didichuxing.datachannel.arius.admin.common.util.IndexNameFactory;
import com.didichuxing.datachannel.arius.admin.common.util.RackUtils;
import com.didichuxing.datachannel.arius.admin.common.util.TemplateUtils;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.physic.ClusterPhyService;
import com.didichuxing.datachannel.arius.admin.core.service.common.AriusConfigInfoService;
import com.didichuxing.datachannel.arius.admin.core.service.es.ESClusterService;
import com.didichuxing.datachannel.arius.admin.core.service.es.ESIndexService;
import com.didichuxing.datachannel.arius.admin.persistence.mysql.template.IndexTemplateLogicDAO;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Set;

import static com.didichuxing.datachannel.arius.admin.client.constant.operaterecord.OperationEnum.BATCH_CHANGE_TEMPLATE_HOT_DAYS;
import static com.didichuxing.datachannel.arius.admin.common.constant.AdminConstant.MILLIS_PER_DAY;
import static com.didichuxing.datachannel.arius.admin.common.constant.AriusConfigConstant.ARIUS_COMMON_GROUP;
import static com.didichuxing.datachannel.arius.admin.common.constant.AriusConfigConstant.ARIUS_TEMPLATE_COLD;
import static com.didichuxing.datachannel.arius.admin.common.constant.template.TemplateServiceEnum.TEMPLATE_COLD;
import static com.didichuxing.datachannel.arius.admin.common.util.IndexNameFactory.genIndexNameClear;
import static com.didichuxing.datachannel.arius.admin.persistence.constant.ESOperateContant.*;

/**
 * ??????????????????
 * @author zqr
 * @date 2020-09-09
 */
@Service
public class TemplateColdManagerImpl extends BaseTemplateSrv implements TemplateColdManager {

    @Autowired
    private AriusConfigInfoService ariusConfigInfoService;

    @Autowired
    private ESClusterService       esClusterService;

    @Autowired
    private ESIndexService         esIndexService;

    @Autowired
    private IndexTemplateLogicDAO  indexTemplateLogicDAO;

    @Autowired
    private ClusterPhyService      clusterPhyService;

    @Override
    public TemplateServiceEnum templateService() {
        return TEMPLATE_COLD;
    }

    /**
     * ??????cold??????
     *
     * @param physicalId ????????????ID
     * @return set?????? v1:????????????????????? v2????????????????????????
     */
    @Override
    public Tuple</*?????????????????????*/Set<String>, /*?????????????????????*/Set<String>> getColdAndHotIndex(Long physicalId) {
        IndexTemplatePhyWithLogic templatePhysicalWithLogic = templatePhyService
            .getTemplateWithLogicById(physicalId);
        if (templatePhysicalWithLogic == null) {
            return new Tuple<>();
        }

        int hotTime = templatePhysicalWithLogic.getLogicTemplate().getHotTime();

        if (hotTime <= 0) {
            LOGGER.info("class=TemplateColdManagerImpl||method=getColdIndex||template={}||msg=hotTime illegal", templatePhysicalWithLogic.getName());
            return new Tuple<>();
        }

        // ?????????????????????????????????????????????
        int adminHotTime = ariusConfigInfoService.intSetting(ARIUS_COMMON_GROUP, "platform.govern.admin.hot.days", -1);
        if (adminHotTime > 0) {
            hotTime = adminHotTime;
        }

        if (hotTime >= templatePhysicalWithLogic.getLogicTemplate().getExpireTime()) {
            LOGGER.info("class=TemplateColdManagerImpl||method=getColdIndex||||template={}||msg=all index is hot",
                templatePhysicalWithLogic.getName());
            return new Tuple<>();
        }

        return templatePhyManager.getHotAndColdIndexByBeforeDay(templatePhysicalWithLogic, hotTime);
    }

    /**
     * ?????????????????????????????????????????????????????????????????????????????????
     * @param httpAddresses client??????
     * @return ????????????????????????????????????id
     */
    @Override
    public Result<Boolean> checkOpenTemplateSrvWhenClusterJoin(String httpAddresses, String password) {
        //??????????????????????????????????????????
        Result<Set<String>> clusterRackByHttpAddress = clusterPhyService.getClusterRackByHttpAddress(httpAddresses, password);
        if (clusterRackByHttpAddress.failed()) {
            return Result.buildFrom(clusterRackByHttpAddress);
        }

        //???????????????????????????????????????????????????????????????
        for (String rack : clusterRackByHttpAddress.getData()) {
            if (AdminConstant.DEFAULT_COLD_RACK.equals(rack)) {
                return Result.buildSucc(Boolean.TRUE);
            }
        }

        return Result.buildFail("???????????????????????????????????????????????????????????????????????????????????????????????????6.6.1???????????????cold???????????????");
    }

    /**
     * ????????????????????????????????????????????????????????????????????????
     * @param phyCluster ??????????????????
     * @return ????????????
     */
    @Override
    public Result<Boolean> checkOpenTemplateSrvByCluster(String phyCluster) {
        if (StringUtils.isBlank(phyCluster)) {
            return Result.buildFail("????????????????????????");
        }

        //????????????????????????????????????
        List<String> coldRackList = Lists.newArrayList(clusterPhyService.listColdRacks(phyCluster));
        if (CollectionUtils.isEmpty(coldRackList)) {
            LOGGER.warn("class=TemplateColdManagerImpl||method=move2ColdNode||cluster={}||no cold rack", phyCluster);
            return Result.buildFail("???????????????????????????????????????????????????????????????????????????????????????????????????6.6.1???????????????cold???????????????");
        }

        return Result.buildSucc();
    }

    /**
     * ??????????????????????????????
     *
     * ???????????????rack
     *
     * ??????tts?????????????????????????????????????????????????????????????????????
     *
     * @return result
     */
    @Override
    public Result<Boolean> move2ColdNode(String phyCluster) {
        if (!isTemplateSrvOpen(phyCluster)) {
            return Result.buildFail(phyCluster + " ??????????????????????????????");

        }

        List<String> coldRackList = Lists.newArrayList(clusterPhyService.listColdRacks(phyCluster));

        if (CollectionUtils.isEmpty(coldRackList)) {
            LOGGER.warn("class=TemplateColdManagerImpl||method=move2ColdNode||cluster={}||no cold rack", phyCluster);
            return Result.buildFail(phyCluster + "???????????????");
        }

        coldRackList.sort(RackUtils::compareByName);
        String coldRack = String.join(",", coldRackList);

        //????????????????????????????????????????????????????????????????????????????????????????????????
        tryConfigCluster(phyCluster);

        List<IndexTemplatePhy> templatePhysicals = templatePhyService.getNormalTemplateByCluster(phyCluster);

        if (CollectionUtils.isEmpty(templatePhysicals)) {
            return Result.buildSucc(true);
        }

        LOGGER.info("class=TemplateColdManagerImpl||method=move2ColdNode||cluster={}||coldRacks={}", phyCluster, coldRack);

        int succ = 0;
        for (IndexTemplatePhy templatePhysical : templatePhysicals) {
            try {
                Result<Boolean> moveResult = movePerTemplate(templatePhysical, coldRack);
                if (moveResult.success()) {
                    succ++;
                } else {
                    LOGGER.warn("class=TemplateColdManagerImpl||method=move2ColdNode||template={}||msg=move2ColdNode fail",
                        templatePhysical.getName());
                }
            } catch (Exception e) {
                LOGGER.warn("class=TemplateColdManagerImpl||method=move2ColdNode||template={}||errMsg={}", templatePhysical.getName(), e.getMessage(),
                    e);
            }
        }

        return Result.buildSucc(succ * 1.0 / templatePhysicals.size() > 0.8);
    }

    /**
     * ????????????????????????hotDay
     *
     * @param phyCluster ??????????????????
     * @return
     */
    @Override
    public int fetchClusterDefaultHotDay(String phyCluster) {
        int hotDay = -1;
        Set<String> enableClusterSet = ariusConfigInfoService.stringSettingSplit2Set(ARIUS_COMMON_GROUP,
            "platform.govern.cold.data.move2ColdNode.enable.clusters", "", ",");
        if (enableClusterSet.contains(phyCluster)) {
            int defaultHotDay = getDefaultHotDay();
            if (defaultHotDay > 0) {
                hotDay = defaultHotDay;
            }
        }

        LOGGER.info("class=TemplateColdManagerImpl||method=fetchClusterDefaultHotDay||msg=no changed||cluster={}||enableClusters={}||version={}",
            phyCluster, JSON.toJSONString(enableClusterSet), hotDay);

        return hotDay;
    }

    /**
     * ????????????hotDays
     *
     * @param days     ??????
     * @param operator ?????????
     * @return result
     */
    @Override
    public Result<Integer> batchChangeHotDay(Integer days, String operator) {
        if (days > 2 || days < -2) {
            return Result.buildParamIllegal("days????????????, [-2, 2]");
        }

        int count = indexTemplateLogicDAO.batchChangeHotDay(days);

        LOGGER.info("class=TemplateColdManagerImpl||method=batchChangeHotDay||days={}||count={}||operator={}", days, count, operator);

        operateRecordService.save(ModuleEnum.PLATFORM_OP, BATCH_CHANGE_TEMPLATE_HOT_DAYS, -1,
            "deltaHotDays:" + days + ";editCount:" + count, operator);

        return Result.buildSucc(count);
    }

    /**
     * ??????????????????rack
     *
     * @param physicalId ????????????id
     * @param tgtRack    ??????rack
     * @param retryCount ????????????
     * @return true/false
     * @throws ESOperateException e
     */
    @Override
    public boolean updateHotIndexRack(Long physicalId, String tgtRack, int retryCount) throws ESOperateException {

        IndexTemplatePhyWithLogic physicalWithLogic = templatePhyService.getTemplateWithLogicById(physicalId);
        if (physicalWithLogic == null) {
            return false;
        }

        List<String> indices = templatePhyService.getMatchIndexNames(physicalWithLogic.getId());
        if (CollectionUtils.isEmpty(indices)) {
            return true;
        }

        int hotDay = physicalWithLogic.getLogicTemplate().getHotTime();

        List<String> expList = getExpList(physicalWithLogic, indices, hotDay);

        LOGGER.info("class=TemplateColdManagerImpl||method=updateHotIndexRack||template={}||expList={}", physicalWithLogic.getName(), expList);

        if (CollectionUtils.isNotEmpty(expList)) {
            return esIndexService.syncBatchUpdateRack(physicalWithLogic.getCluster(), expList, tgtRack, retryCount);
        } else {
            return true;
        }
    }

    /**************************************************** private method ****************************************************/
    private List<String> getExpList(IndexTemplatePhyWithLogic physicalWithLogic, List<String> indices, int hotDay) {
        List<String> expList = Lists.newArrayList();

        if (hotDay < 0) {
            expList.addAll(indices);
        } else if (TemplateUtils.isOnly1Index(physicalWithLogic.getExpression())) {
            expList.add(physicalWithLogic.getExpression());
        } else {
            if (CollectionUtils.isEmpty(indices)) {
                LOGGER.info("class=TemplateColdManagerImpl||method=updateHotIndexRack||template={}||msg=no matched indices",
                        physicalWithLogic.getName());
                return Lists.newArrayList();
            }
            Set<String> hotIndexNames = Sets.newHashSet();
            for (String indexName : indices) {
                Date indexTime = IndexNameFactory.genIndexTimeByIndexName(
                        genIndexNameClear(indexName, physicalWithLogic.getExpression()), physicalWithLogic.getExpression(),
                        physicalWithLogic.getLogicTemplate().getDateFormat());
                if (indexTime == null) {
                    LOGGER.warn("class=TemplateColdManagerImpl||method=updateHotIndexRack||template={}||msg=parse index time fail",
                            physicalWithLogic.getName());
                    continue;
                }
                long timeIntervalDay = (System.currentTimeMillis() - indexTime.getTime()) / MILLIS_PER_DAY;
                if (timeIntervalDay >= hotDay) {
                    LOGGER.info("class=TemplateColdManagerImpl||method=updateHotIndexRack||template={}||indexName={}||msg=index is cold",
                            physicalWithLogic.getName(), indexName);
                    continue;
                }
                hotIndexNames.add(indexName);
            }
            expList.addAll(hotIndexNames);
        }

        return expList;
    }

    private Result<Boolean> movePerTemplate(IndexTemplatePhy templatePhysical, String coldRacks) throws ESOperateException {
        //??????????????????????????????
        Set<String> coldIndexNames = getColdAndHotIndex(templatePhysical.getId()).getV1();
        //??????????????????????????????
        Set<String> hotIndexNames = getColdAndHotIndex(templatePhysical.getId()).getV2();

        //??????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
        return Result.buildBoolen(movePerIndexTemplate(templatePhysical, coldRacks, coldIndexNames)
                && movePerIndexTemplate(templatePhysical, templatePhysical.getRack(), hotIndexNames));
    }

    private boolean movePerIndexTemplate(IndexTemplatePhy templatePhysical,
                                         String racks, Set<String> indexNames) throws ESOperateException {
        if (CollectionUtils.isEmpty(indexNames)) {
            LOGGER.info("class=TemplateColdManagerImpl||method=movePerIndexTemplate||template={}||msg=no need index", templatePhysical.getName());
            return false;
        } else {
            //??????????????????????????????
            return esIndexService.syncBatchUpdateRack(templatePhysical.getCluster(), Lists.newArrayList(indexNames), racks, 3);
        }
    }

    private void tryConfigCluster(String cluster) {
        try {
            if (esClusterService.syncConfigColdDateMove(cluster,
                    ariusConfigInfoService.intSetting(ARIUS_COMMON_GROUP, CLUSTER_ROUTING_ALLOCATION_INGOING, 2),
                    ariusConfigInfoService.intSetting(ARIUS_COMMON_GROUP, CLUSTER_ROUTING_ALLOCATION_OUTGOING, 2),
                    ariusConfigInfoService.stringSetting(ARIUS_COMMON_GROUP, COLD_MAX_BYTES_PER_SEC_KEY, "10MB"),
                    3)) {
                LOGGER.info("class=TemplateColdManagerImpl||method=tryConfigCluster||cluster={}||msg=config cluster succ", cluster);
            } else {
                LOGGER.warn("class=TemplateColdManagerImpl||method=tryConfigCluster||cluster={}||msg=config cluster fail", cluster);
            }
        } catch (Exception e) {
            LOGGER.info("class=TemplateColdManagerImpl||method=tryConfigCluster||cluster={}||errMsg={}", cluster, e.getMessage(), e);
        }
    }

    /**
     * ??????????????????hotDay???
     *
     * @return
     */
    private int getDefaultHotDay() {
        String defaultDay = ariusConfigInfoService.stringSetting(ARIUS_TEMPLATE_COLD, "defaultDay", "");
        LOGGER.info("class=TemplateColdManagerImpl||method=getDefaultHotDay||msg=defaultDay: {}", defaultDay);
        if (StringUtils.isNotBlank(defaultDay)) {
            try {
                JSONObject object = JSON.parseObject(defaultDay);
                return object.getInteger("defaultHotDay");
            } catch (JSONException e) {
                LOGGER.warn("class=TemplateColdManagerImpl||method=getDefaultHotDay||errMsg={}", e.getMessage());
            }
        }
        return -1;
    }
}
