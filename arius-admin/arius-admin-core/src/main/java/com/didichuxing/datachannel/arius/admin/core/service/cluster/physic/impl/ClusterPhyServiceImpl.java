package com.didichuxing.datachannel.arius.admin.core.service.cluster.physic.impl;

import java.util.*;
import java.util.stream.Collectors;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.didichuxing.datachannel.arius.admin.client.bean.common.Plugin;
import com.didichuxing.datachannel.arius.admin.client.bean.common.Result;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.cluster.ClusterPhyConditionDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.cluster.ClusterSettingDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.cluster.ESClusterDTO;
import com.didichuxing.datachannel.arius.admin.client.constant.operaterecord.OperationEnum;
import com.didichuxing.datachannel.arius.admin.client.constant.resource.ESClusterNodeRoleEnum;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ClusterPhy;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ecm.RoleCluster;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ecm.RoleClusterHost;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.template.IndexTemplateLogic;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.template.IndexTemplatePhy;
import com.didichuxing.datachannel.arius.admin.common.bean.po.cluster.ClusterPO;
import com.didichuxing.datachannel.arius.admin.common.constant.AdminConstant;
import com.didichuxing.datachannel.arius.admin.common.constant.DataCenterEnum;
import com.didichuxing.datachannel.arius.admin.common.constant.cluster.ClusterDynamicConfigsEnum;
import com.didichuxing.datachannel.arius.admin.common.constant.SortConstant;
import com.didichuxing.datachannel.arius.admin.common.constant.template.TemplateServiceEnum;
import com.didichuxing.datachannel.arius.admin.common.exception.ESOperateException;
import com.didichuxing.datachannel.arius.admin.common.util.AriusObjUtils;
import com.didichuxing.datachannel.arius.admin.common.util.ConvertUtil;
import com.didichuxing.datachannel.arius.admin.common.util.ListUtils;
import com.didichuxing.datachannel.arius.admin.common.util.SizeUtil;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.ecm.ESPluginService;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.physic.ClusterPhyService;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.physic.RoleClusterHostService;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.physic.RoleClusterService;
import com.didichuxing.datachannel.arius.admin.core.service.es.ESClusterService;
import com.didichuxing.datachannel.arius.admin.core.service.template.logic.TemplateLogicService;
import com.didichuxing.datachannel.arius.admin.core.service.template.physic.TemplatePhyService;
import com.didichuxing.datachannel.arius.admin.persistence.constant.ESOperateContant;
import com.didichuxing.datachannel.arius.admin.persistence.mysql.resource.ClusterDAO;
import com.didiglobal.logi.elasticsearch.client.model.type.ESVersion;
import com.didiglobal.logi.log.ILog;
import com.didiglobal.logi.log.LogFactory;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.didichuxing.datachannel.arius.admin.common.constant.AdminConstant.COLD_RACK_PREFER;
import static com.didichuxing.datachannel.arius.admin.common.constant.ClusterConstant.DEFAULT_CLUSTER_HEALTH;

@Service
@NoArgsConstructor
public class ClusterPhyServiceImpl implements ClusterPhyService {

    private static final ILog        LOGGER = LogFactory.getLog(ClusterPhyServiceImpl.class);

    private static final String CLUSTER_NOT_EXIST = "???????????????";

    @Value("${es.client.cluster.port}")
    private String                   esClusterClientPort;

    @Autowired
    private ClusterDAO               clusterDAO;

    @Autowired
    private ESClusterService         esClusterService;

    @Autowired
    private ESPluginService          esPluginService;

    @Autowired
    private TemplatePhyService       templatePhyService;

    @Autowired
    private TemplateLogicService     templateLogicService;

    @Autowired
    private RoleClusterService       roleClusterService;

    @Autowired
    private RoleClusterHostService   roleClusterHostService;

    private static final String DEFAULT_WRITE_ACTION = "RestBulkAction,RestDeleteAction,RestIndexAction,RestUpdateAction";

    /**
     * ????????????
     * @param params ??????
     * @return ????????????
     */
    @Override
    public List<ClusterPhy> listClustersByCondt(ESClusterDTO params) {
        List<ClusterPO> clusterPOs = clusterDAO.listByCondition(ConvertUtil.obj2Obj(params, ClusterPO.class));

        if (CollectionUtils.isEmpty(clusterPOs)) {
            return Lists.newArrayList();
        }

        return ConvertUtil.list2List(clusterPOs, ClusterPhy.class);
    }

    /**
     * ????????????
     *
     * @param clusterId ??????id
     * @param operator  ?????????
     * @return ?????? true ?????? false
     * <p>
     * NotExistException
     * ???????????????
     */
    @Override
    public Result<Boolean> deleteClusterById(Integer clusterId, String operator) {
        ClusterPO clusterPO = clusterDAO.getById(clusterId);
        if (clusterPO == null) {
            return Result.buildNotExist(CLUSTER_NOT_EXIST);
        }
        
        return Result.buildBoolen(clusterDAO.delete(clusterId) == 1);
    }

    /**
     * ????????????
     * @param param    ????????????
     * @param operator ?????????
     * @return ?????? true ?????? false
     * <p>
     * DuplicateException
     * ??????????????????(???????????????)
     * IllegalArgumentException
     * ???????????????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Boolean> createCluster(ESClusterDTO param, String operator) {
        Result<Boolean> checkResult = checkClusterParam(param, OperationEnum.ADD);
        if (checkResult.failed()) {
            LOGGER.warn("class=ESClusterPhyServiceImpl||method=addCluster||msg={}", checkResult.getMessage());
            return checkResult;
        }

        initClusterParam(param);

        ClusterPO clusterPO = ConvertUtil.obj2Obj(param, ClusterPO.class);
        boolean succ = (1 == clusterDAO.insert(clusterPO));
        if (succ) {
            param.setId(clusterPO.getId());
        }
        return Result.buildBoolen(succ);
    }

    /**
     * ????????????
     * @param param    ????????????
     * @param operator ?????????
     * @return ?????? true ?????? false
     * <p>
     * IllegalArgumentException
     * ???????????????
     * NotExistException
     * ???????????????
     */
    @Override
    public Result<Boolean> editCluster(ESClusterDTO param, String operator) {
        Result<Boolean> checkResult = checkClusterParam(param, OperationEnum.EDIT);
        if (checkResult.failed()) {
            LOGGER.warn("class=ESClusterPhyServiceImpl||method=editCluster||msg={}", checkResult.getMessage());
            return checkResult;
        }

        boolean succ = (1 == clusterDAO.update(ConvertUtil.obj2Obj(param, ClusterPO.class)));
        return Result.buildBoolen(succ);
    }

    /**
     * ??????????????????????????????
     * @param clusterName ????????????
     * @return ??????
     */
    @Override
    public ClusterPhy getClusterByName(String clusterName) {
        // ??????????????????
        ClusterPO clusterPO = clusterDAO.getByName(clusterName);
        if (null == clusterPO) {
            return null;
        }

        // ????????????????????????
        ClusterPhy clusterPhy = ConvertUtil.obj2Obj(clusterPO, ClusterPhy.class);

        // ???????????????????????????
        List<RoleCluster> roleClusters = roleClusterService.getAllRoleClusterByClusterId(
                clusterPhy.getId());
        if (CollectionUtils.isNotEmpty(roleClusters)) {
            // ????????????
            clusterPhy.setRoleClusters(roleClusters);

            // ????????????
            List<RoleClusterHost> roleClusterHosts = new ArrayList<>();
            Map<Long, List<RoleClusterHost>> map = roleClusterHostService.getByRoleClusterIds(roleClusters.stream().map(RoleCluster::getId).collect(Collectors.toList()));
            for (RoleCluster roleCluster : roleClusters) {
                List<RoleClusterHost> esRoleClusterHosts = map.getOrDefault(roleCluster.getId(), new ArrayList<>());
                roleClusterHosts.addAll(esRoleClusterHosts);
            }
            clusterPhy.setRoleClusterHosts(roleClusterHosts);
        }

        return clusterPhy;
    }

    @Override
    public Result<Void> updatePluginIdsById(String pluginIds, Integer phyClusterId) {
        boolean succ = (1 == clusterDAO.updatePluginIdsById(pluginIds, phyClusterId));
        return Result.build(succ);
    }

    @Override
    public List<ClusterPhy> listAllClusters() {
        return ConvertUtil.list2List(clusterDAO.listAll(), ClusterPhy.class);
    }

    @Override
    public List<String> listAllClusterNameList() {
        List<String> clusterNameList = Lists.newArrayList();
        try {
            clusterNameList.addAll(clusterDAO.listAllName());
        } catch (Exception e) {
            LOGGER.error("class=ESClusterPhyServiceImpl||method=listAllClusterNameList||errMsg={}",e.getMessage(), e);
        }
        return clusterNameList;
    }

    @Override
    public List<ClusterPhy> listClustersByNames(List<String> names) {
        if (CollectionUtils.isEmpty(names)) {
            return new ArrayList<>();
        }
        return ConvertUtil.list2List(clusterDAO.listByNames(names), ClusterPhy.class);
    }

    /**
     * ??????????????????
     * @param clusterName ????????????
     * @return true ??????
     */
    @Override
    public boolean isClusterExists(String clusterName) {
        return clusterDAO.getByName(clusterName) != null;
    }

    /**
     * ??????????????????
     * @param clusterName ????????????
     * @return true ??????
     */
    @Override
    public boolean isClusterExistsByList(List<ClusterPhy> list,String clusterName) {
        return list.stream().map(ClusterPhy::getCluster).anyMatch(cluster->cluster.equals(clusterName));
    }
    /**
     * rack????????????
     * @param cluster ????????????
     * @param racks   rack??????
     * @return true ??????
     */
    @Override
    public boolean isRacksExists(String cluster, String racks) {
        Set<String> rackSet = getClusterRacks(cluster);
        if (CollectionUtils.isEmpty(rackSet)) {
            LOGGER.warn("class=ESClusterPhyServiceImpl||method=rackExist||cluster={}||msg=can not get rack set!",
                cluster);
            return false;
        }

        for (String r : racks.split(AdminConstant.RACK_COMMA)) {
            if (!rackSet.contains(r)) {
                LOGGER.warn(
                    "class=ESClusterPhyServiceImpl||method=rackExist||cluster={}||rack={}||msg=can not get rack!",
                    cluster, r);
                return false;
            }
        }

        return true;
    }

    /**
     * ?????????????????????rack
     * @param cluster cluster
     * @return set
     */
    @Override
    public Set<String> getClusterRacks(String cluster) {
        List<RoleClusterHost> nodes = roleClusterHostService.getNodesByCluster(cluster);
        if (CollectionUtils.isEmpty(nodes)) {
            return Sets.newHashSet();
        }

        Set<String> rackSet = new HashSet<>();
        // ??????datanode??????rack
        for (RoleClusterHost roleClusterHost : nodes) {
            if (ESClusterNodeRoleEnum.DATA_NODE.getCode() == roleClusterHost.getRole()) {
                rackSet.add(roleClusterHost.getRack());
            }
        }

        return rackSet;
    }

    @Override
    public Set<String> listHotRacks(String cluster) {
        // ?????????rack???c?????????????????????????????????
        return getClusterRacks(cluster).stream().filter(rack -> !rack.toLowerCase().startsWith(COLD_RACK_PREFER))
            .collect(Collectors.toSet());
    }

    @Override
    public Set<String> listColdRacks(String cluster) {
        // ?????????rack???c??????
        return getClusterRacks(cluster).stream().filter(rack -> rack.toLowerCase().startsWith(COLD_RACK_PREFER))
            .collect(Collectors.toSet());
    }

    /**
     * ???????????????????????????????????????????????????
     * @param cluster ????????????
     * @return
     */
    @Override
    public List<Plugin> listClusterPlugins(String cluster) {
        ClusterPO clusterPhy = clusterDAO.getByName(cluster);
        if (AriusObjUtils.isNull(clusterPhy)) {
            return new ArrayList<>();
        }

        List<Plugin> pluginList = ConvertUtil.list2List(esPluginService.listClusterAndDefaultESPlugin(clusterPhy.getId().toString()), Plugin.class);

        // ??????????????????????????????????????????(???????????????????????????)?????????????????????FALSE
        Map<Long, Plugin> pluginMap = new HashMap<>(0);
        for (Plugin esPlugin : pluginList) {
            esPlugin.setInstalled(Boolean.FALSE);
            pluginMap.put(esPlugin.getId(), esPlugin);
        }

        // ??????????????????????????????????????????????????????????????????????????????????????????????????????TRUE
        List<Long> pluginIds = parsePluginIds(clusterPhy.getPlugIds());
        for (Long pluginId : pluginIds) {
            Plugin phyPlugin = pluginMap.get(pluginId);
            if (AriusObjUtils.isNull(phyPlugin)) {
                continue;
            }
            phyPlugin.setInstalled(true);
        }

        return new ArrayList<>(pluginMap.values());
    }

    /**
     * ??????????????????
     * @param phyClusterId ??????id
     * @return ??????  ???????????????null
     */
    @Override
    public ClusterPhy getClusterById(Integer phyClusterId) {
        ClusterPO clusterPO = clusterDAO.getById(phyClusterId);
        ClusterPhy clusterPhy = ConvertUtil.obj2Obj(clusterPO, ClusterPhy.class);
        return clusterPhy;
    }

    /**
     * ????????????????????????
     * @param cluster ??????
     * @return count
     */
    @Override
    public int getWriteClientCount(String cluster) {
        ClusterPO clusterPO = clusterDAO.getByName(cluster);

        if (StringUtils.isBlank(clusterPO.getHttpWriteAddress())) {
            return 1;
        }

        return clusterPO.getHttpWriteAddress().split(",").length;
    }

    /**
     * ?????????????????????DCDR??????????????????????????????????????????????????????
     * @param cluster       ??????
     * @param remoteCluster ????????????
     * @return
     */
    @Override
    public boolean ensureDcdrRemoteCluster(String cluster, String remoteCluster) throws ESOperateException {

        ClusterPhy clusterPhy = getClusterByName(cluster);
        if (clusterPhy == null) {
            return false;
        }

        ClusterPhy remoteClusterPhy = getClusterByName(remoteCluster);
        if (remoteClusterPhy == null) {
            return false;
        }

        if (esClusterService.hasSettingExist(cluster,
            String.format(ESOperateContant.REMOTE_CLUSTER_FORMAT, remoteCluster))) {
            return true;
        }

        return esClusterService.syncPutRemoteCluster(cluster, remoteCluster,
            genTcpAddr(remoteClusterPhy.getHttpWriteAddress(), 9300), 3);
    }

    @Override
    public List<RoleCluster> listPhysicClusterRoles(Integer clusterId) {
        return roleClusterService.getAllRoleClusterByClusterId(clusterId);
    }

    @Override
    public Result<Boolean> updatePhyClusterDynamicConfig(ClusterSettingDTO param) {
        Result<ClusterDynamicConfigsEnum> result = checkClusterDynamicType(param);
        if (result.failed()) {
            return Result.buildFrom(result);
        }

        Map<String, Object> persistentConfig = Maps.newHashMap();
        persistentConfig.put(param.getKey(), param.getValue());
        return Result.buildBoolen(esClusterService.syncPutPersistentConfig(param.getClusterName(), persistentConfig));
    }

    @Override
    public Set<String> getRoutingAllocationAwarenessAttributes(String cluster) {
        if(!isClusterExists(cluster)) {
            return Sets.newHashSet();
        }

        return esClusterService.syncGetAllNodesAttributes(cluster);
    }

    @Override
    public List<ClusterPhy> pagingGetClusterPhyByCondition(ClusterPhyConditionDTO param) {
        String sortTerm = null == param.getSortTerm() ? SortConstant.ID : param.getSortTerm();
        String sortType = param.getOrderByDesc() ? SortConstant.DESC : SortConstant.ASC;
        List<ClusterPO> clusterPOS = Lists.newArrayList();
        try {
            clusterPOS = clusterDAO.pagingByCondition(param.getCluster(), param.getHealth(),
                    param.getEsVersion(), (param.getPage() - 1) * param.getSize(), param.getSize(), sortTerm, sortType);
        } catch (Exception e) {
            LOGGER.error("class=ClusterPhyServiceImpl||method=pagingGetClusterPhyByCondition||msg={}", e.getMessage(), e);
        }
        return ConvertUtil.list2List(clusterPOS, ClusterPhy.class);
    }

    @Override
    public Long fuzzyClusterPhyHitByCondition(ClusterPhyConditionDTO param) {
        return clusterDAO.getTotalHitByCondition(ConvertUtil.obj2Obj(param, ClusterPO.class));
    }

    @Override
    public Result<Set<String>> getClusterRackByHttpAddress(String addresses, String password) {
        if (StringUtils.isBlank(addresses)) {
            return Result.buildFail("?????????es???????????????????????????");
        }

        return esClusterService.getClusterRackByHttpAddress(addresses, password);
    }

    @Override
    public Float getSurplusDiskSizeOfRacks(String clusterPhyName, String racks, Map<String, Float> allocationInfoOfRack) {
        List<String> rackList = ListUtils.string2StrList(racks);

        //??????region??????rack?????????????????????????????????
        Float regionDiskSize = 0F;
        for (String rack : rackList) {
            if (allocationInfoOfRack.containsKey(rack)) regionDiskSize += allocationInfoOfRack.get(rack);
        }

        //???????????????region????????????????????????
        Float templateOnRegionDiskSize = 0F;
        List<IndexTemplatePhy> normalTemplateOnPhyCluster = templatePhyService.getNormalTemplateByCluster(clusterPhyName);
        if (CollectionUtils.isEmpty(normalTemplateOnPhyCluster)) {
            return regionDiskSize;
        }

        //?????????region???????????????????????????????????????
        for (IndexTemplatePhy indexTemplatePhy : normalTemplateOnPhyCluster) {
            if (!rackList.containsAll(ListUtils.string2StrList(indexTemplatePhy.getRack()))) {
                continue;
            }

            //???????????????????????????????????????????????????quota??????
            IndexTemplateLogic logicTemplate = templateLogicService.getLogicTemplateById(indexTemplatePhy.getLogicId());
            if (AriusObjUtils.isNull(logicTemplate)) {
                continue;
            }

            //????????????quota??????????????????gb??????????????????????????????????????????
            templateOnRegionDiskSize += Float.valueOf(SizeUtil.getUnitSize(logicTemplate.getQuota() + "gb"));
        }

        //???????????????region????????????????????????????????????
        return regionDiskSize - templateOnRegionDiskSize;
    }

    /**************************************** private method ***************************************************/
    private List<String> genTcpAddr(String httpAddress, int tcpPort) {
        try {
            String[] httpAddrArr = httpAddress.split(",");
            List<String> result = Lists.newArrayList();
            for (String httpAddr : httpAddrArr) {
                result.add(httpAddr.split(":")[0] + ":" + tcpPort);
            }
            return result;
        } catch (Exception e) {
            LOGGER.warn("method=genTcpAddr||httpAddress={}||errMsg={}", httpAddress, e.getMessage(), e);
        }

        return Lists.newArrayList();
    }

    private Result<ClusterDynamicConfigsEnum> checkClusterDynamicType(ClusterSettingDTO param) {
        if(!isClusterExists(param.getClusterName())) {
            return Result.buildFail(CLUSTER_NOT_EXIST);
        }

        ClusterDynamicConfigsEnum clusterSettingEnum = ClusterDynamicConfigsEnum.valueCodeOfName(param.getKey());
        if(clusterSettingEnum.equals(ClusterDynamicConfigsEnum.UNKNOWN)) {
            return Result.buildFail("???????????????????????????");
        }

        if (!clusterSettingEnum.getCheckFun().apply(String.valueOf(param.getValue())).booleanValue()) {
            return Result.buildFail("?????????????????????????????????");
        }

        if (clusterSettingEnum == ClusterDynamicConfigsEnum.CLUSTER_ROUTING_ALLOCATION_AWARENESS_ATTRIBUTES
                && !getRoutingAllocationAwarenessAttributes(param.getClusterName())
                .containsAll((JSONArray) JSON.toJSON(param.getValue()))) {
            return Result.buildFail("?????????attributes??????????????????");
        }
        return Result.buildSucc();
    }


    private Result<Boolean> checkClusterParam(ESClusterDTO param, OperationEnum operation) {
        if (AriusObjUtils.isNull(param)) {
            return Result.buildParamIllegal("??????????????????");
        }

        if (OperationEnum.ADD.equals(operation)) {
            Result<Boolean> result = handleAdd(param);
            if (result.failed()) {
                return result;
            }
        } else if (OperationEnum.EDIT.equals(operation)) {
            Result<Boolean> result = handleEdit(param);
            if (result.failed()) {
                return result;
            }
        }

        Result<Boolean> isIllegalResult = isIllegal(param);
        if (isIllegalResult.failed()) {
            return isIllegalResult;
        }

        return Result.buildSucc();
    }

    private Result<Boolean> handleEdit(ESClusterDTO param) {
        if (AriusObjUtils.isNull(param.getId())) {
            return Result.buildParamIllegal("??????ID??????");
        }

        ClusterPO oldClusterPO = clusterDAO.getById(param.getId());
        if (oldClusterPO == null) {
            return Result.buildNotExist(CLUSTER_NOT_EXIST);
        }
        return Result.buildSucc();
    }

    private Result<Boolean> handleAdd(ESClusterDTO param) {
        Result<Boolean> isFieldNullResult = isFieldNull(param);
        if (isFieldNullResult.failed()) {
            return isFieldNullResult;
        }

        if (param.getCluster() != null) {
            ClusterPO clusterPO = clusterDAO.getByName(param.getCluster());
            if (clusterPO != null && clusterPO.getId().equals(param.getId())) {
                return Result.buildDuplicate("????????????");
            }
        }
        return Result.buildSucc();
    }

    private Result<Boolean> isIllegal(ESClusterDTO param) {
        if (param.getDataCenter() != null && !DataCenterEnum.validate(param.getDataCenter())) {
            return Result.buildParamIllegal("??????????????????");
        }

        if (param.getEsVersion() != null && ESVersion.valueBy(param.getEsVersion()) == null) {
            return Result.buildParamIllegal("es???????????????");
        }
        return Result.buildSucc();
    }

    private Result<Boolean> isFieldNull(ESClusterDTO param) {
        if (AriusObjUtils.isNull(param.getCluster())) {
            return Result.buildParamIllegal("??????????????????");
        }
        if (AriusObjUtils.isNull(param.getHttpAddress())) {
            return Result.buildParamIllegal("??????HTTP????????????");
        }
        if (AriusObjUtils.isNull(param.getType())) {
            return Result.buildParamIllegal("??????????????????");
        }
        if (AriusObjUtils.isNull(param.getDataCenter())) {
            return Result.buildParamIllegal("??????????????????");
        }
        if (AriusObjUtils.isNull(param.getIdc())) {
            return Result.buildParamIllegal("??????????????????");
        }
        if (AriusObjUtils.isNull(param.getEsVersion())) {
            return Result.buildParamIllegal("es????????????");
        }
        return Result.buildSucc();
    }

    private void initClusterParam(ESClusterDTO param) {
        if (param.getWriteAddress() == null) {
            param.setWriteAddress("");
        }

        if (param.getReadAddress() == null) {
            param.setReadAddress("");
        }

        if (param.getHttpWriteAddress() == null) {
            param.setHttpWriteAddress("");
        }

        if (param.getPassword() == null) {
            param.setPassword("");
        }

        if(param.getImageName() == null) {
            param.setImageName("");
        }

        if(param.getLevel() == null) {
            param.setLevel(1);
        }

        if(param.getCreator() == null) {
            param.setCreator("");
        }

        if(param.getNsTree() == null) {
            param.setNsTree("");
        }

        if(param.getDesc() == null) {
            param.setDesc("");
        }

        if (param.getWriteAction() == null) {
            param.setWriteAction(DEFAULT_WRITE_ACTION);
        }

        if (param.getTemplateSrvs() == null){
            param.setTemplateSrvs(TemplateServiceEnum.getDefaultSrvs());
        }

        if (null == param.getHealth()) {
            param.setHealth(DEFAULT_CLUSTER_HEALTH);
        }

        if(null == param.getActiveShardNum()) {
            param.setActiveShardNum(0L);
        }

        if (null == param.getDiskTotal()) {
            param.setDiskTotal(0L);
        }

        if (null == param.getDiskUsage()) {
            param.setDiskUsage(0L);
        }

        if (null == param.getDiskUsagePercent()) {
            param.setDiskUsagePercent(0D);
        }
    }

    /**
     * ????????????ID??????
     *
     * @param pluginIdsStr ??????ID??????????????????
     * @return
     */
    private List<Long> parsePluginIds(String pluginIdsStr) {
        List<Long> pluginIds = new ArrayList<>();
        if (StringUtils.isNotBlank(pluginIdsStr)) {
            String[] arr = StringUtils.split(pluginIdsStr, ",");
            for (int i = 0; i < arr.length; ++i) {
                pluginIds.add(Long.parseLong(arr[i]));
            }
        }
        return pluginIds;
    }
}
