package com.didichuxing.datachannel.arius.admin.core.service.template.logic.impl;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.didichuxing.datachannel.arius.admin.client.bean.common.Result;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.template.alias.ConsoleTemplateAliasSwitchDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.template.alias.IndexTemplateAliasDTO;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.template.IndexTemplateAlias;
import com.didichuxing.datachannel.arius.admin.common.bean.po.template.TemplateAliasPO;
import com.didichuxing.datachannel.arius.admin.common.bean.po.template.TemplateLogicPO;
import com.didichuxing.datachannel.arius.admin.common.bean.po.template.TemplatePhysicalPO;
import com.didichuxing.datachannel.arius.admin.common.exception.ESOperateException;
import com.didichuxing.datachannel.arius.admin.common.util.ConvertUtil;
import com.didichuxing.datachannel.arius.admin.core.component.CacheSwitch;
import com.didichuxing.datachannel.arius.admin.core.service.template.logic.TemplateLogicAliasService;
import com.didichuxing.datachannel.arius.admin.persistence.component.ESOpClient;
import com.didichuxing.datachannel.arius.admin.persistence.mysql.template.IndexTemplateAliasDAO;
import com.didichuxing.datachannel.arius.admin.persistence.mysql.template.IndexTemplateLogicDAO;
import com.didichuxing.datachannel.arius.admin.persistence.mysql.template.IndexTemplatePhysicalDAO;
import com.didiglobal.logi.elasticsearch.client.ESClient;
import com.didiglobal.logi.elasticsearch.client.request.index.putalias.PutAliasNode;
import com.didiglobal.logi.elasticsearch.client.request.index.putalias.PutAliasType;
import com.didiglobal.logi.elasticsearch.client.response.indices.getalias.ESIndicesGetAliasResponse;
import com.didiglobal.logi.elasticsearch.client.response.indices.putalias.ESIndicesPutAliasResponse;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Maps;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.didichuxing.datachannel.arius.admin.common.constant.TemplateConstant.*;

@Service
public class TemplateLogicAliasServiceImpl implements TemplateLogicAliasService {

    @Autowired
    private IndexTemplateAliasDAO indexTemplateAliasDAO;
    @Autowired
    private IndexTemplateLogicDAO indexTemplateLogicDAO;
    @Autowired
    private IndexTemplatePhysicalDAO indexTemplatePhysicalDAO;
    @Autowired
    private ESOpClient esOpClient;
    @Autowired
    private CacheSwitch cacheSwitch;

    private Cache<Integer, List<String>> templateLogicAliasCache = CacheBuilder
            .newBuilder().expireAfterWrite(60, TimeUnit.MINUTES).maximumSize(1000).build();
    private Cache<String, Map<Integer, List<String>>> templateAliasMapCache = CacheBuilder
            .newBuilder().expireAfterWrite(1, TimeUnit.MINUTES).maximumSize(100).build();

    /**
     * ????????????
     *
     * @param logicId logicId
     * @return list
     */
    @Override
    public List<String> getAliasesById(Integer logicId) {
        List<TemplateAliasPO> indexTemplateAliasPOS = indexTemplateAliasDAO.listByTemplateId(logicId);
        if(CollectionUtils.isEmpty(indexTemplateAliasPOS)){
            return new ArrayList<>();
        }

        return indexTemplateAliasPOS.stream().map(i -> i.getName()).collect(Collectors.toList());
    }

    /**
     * ???????????????????????????????????????
     * @param logicId
     * @return
     */
    @Override
    public List<String> getAliasesByIdFromCache(Integer logicId) {
        try {
            return templateLogicAliasCache.get(logicId, () -> getAliasesById(logicId));
        } catch (ExecutionException e) {
            return getAliasesById(logicId);
        }
    }

    /**
     * ????????????
     *
     * @return list
     */
    @Override
    public List<IndexTemplateAlias> listAlias() {
        return ConvertUtil.list2List(indexTemplateAliasDAO.listAll(), IndexTemplateAlias.class);
    }

    /**
     * ??????????????????????????????
     * @return
     */
    @Override
    public Map<Integer, List<String>> listAliasMap() {
        Map<Integer, List<String>> templateAliasMap = Maps.newHashMap();

        List<IndexTemplateAlias> aliasList = ConvertUtil.list2List(indexTemplateAliasDAO.listAll(), IndexTemplateAlias.class);

        if (CollectionUtils.isNotEmpty(aliasList)) {
            for (IndexTemplateAlias alias : aliasList) {
                Integer key = alias.getLogicId();
                List<String> aliasNames = templateAliasMap.getOrDefault(key, new ArrayList<>());
                aliasNames.add(alias.getName());
                templateAliasMap.putIfAbsent(key, aliasNames);
            }
        }
        return templateAliasMap;
    }

    @Override
    public Map<Integer, List<String>> listAliasMapWithCache() {
        if (cacheSwitch.logicTemplateCacheEnable()) {
            try {
                return templateAliasMapCache.get("listAliasMap", this::listAliasMap);
            } catch (ExecutionException e) {
                return listAliasMap();
            }
        }
        return listAliasMap();
    }



    /**
     * ???????????????????????????
     *
     * @param indexTemplateAlias
     * @return
     */
    @Override
    public Result<Boolean> addAlias(IndexTemplateAliasDTO indexTemplateAlias) {
        //????????????????????????
        Result result = aliasChecked(indexTemplateAlias.getName(), null, indexTemplateAlias.getLogicId());
        if (null != result) {
            return Result.buildSucc(result.success(),result.getMessage());
        }
        int ret = indexTemplateAliasDAO.insert( ConvertUtil.obj2Obj(indexTemplateAlias, TemplateAliasPO.class));

        return Result.buildSucc(ret > 0);
    }

    /**
     * ???????????????????????????
     *
     * @param indexTemplateAlias
     * @return
     */
    @Override
    public Result<Boolean> delAlias(IndexTemplateAliasDTO indexTemplateAlias) {
        int ret =  indexTemplateAliasDAO.delete(indexTemplateAlias.getLogicId(), indexTemplateAlias.getName());

        return Result.buildSucc(ret > 0);
    }

    @Override
    @Transactional
    public Result aliasSwitch(ConsoleTemplateAliasSwitchDTO aliasSwitchDTO) throws ESOperateException {
        //????????????
        Result result = aliasChecked(aliasSwitchDTO.getAliasName(), aliasSwitchDTO.getAppId(), null);
        if (result != null && result.failed()) {
            return result;
        }
        List<TemplateAliasPO> insertAliasList = new ArrayList<>();
        List<Integer> deleteAliasList = new ArrayList<>();
        Set<Integer> logicIdList = new HashSet<>();
        List<PutAliasNode> nodes;
        List<TemplateLogicPO> indexTemplateLogicPOS = indexTemplateLogicDAO.listByAppId(aliasSwitchDTO.getAppId());
        //????????????
        if (CollectionUtils.isEmpty(aliasSwitchDTO.getAddAliasIndices()) && CollectionUtils.isEmpty(aliasSwitchDTO.getDelAliasIndices())) {
            return Result.buildFail("????????????????????????????????????");
        }
        Result<List<PutAliasNode>> nodesResult = buildAliasNodes(aliasSwitchDTO, indexTemplateLogicPOS, insertAliasList, deleteAliasList, logicIdList);
        if (nodesResult.success()) {
            nodes = new ArrayList<>(nodesResult.getData());
        } else {
            return nodesResult;
        }
        //??????????????????????????????????????????????????????????????????
        List<TemplatePhysicalPO> physicalPOList = indexTemplatePhysicalDAO.listByLogicIds(new ArrayList<>(logicIdList));
        String cluster = "";
        if (CollectionUtils.isNotEmpty(physicalPOList)) {
            Set<String> set = physicalPOList.stream().map(TemplatePhysicalPO::getCluster).collect(Collectors.toSet());
            if (set.size() != 1) {
                return Result.buildFail("??????????????????????????????");
            }
            cluster = set.toArray(new String[1])[0];
        } else {
            return Result.buildFail("????????????????????????");
        }
        //????????????
        if (CollectionUtils.isNotEmpty(deleteAliasList)) {
            indexTemplateAliasDAO.deleteBatch(deleteAliasList, aliasSwitchDTO.getAliasName());
        }
        if (CollectionUtils.isNotEmpty(insertAliasList)) {
            indexTemplateAliasDAO.insertBatch(insertAliasList);
        }
        //??????ES
        ESClient client = esOpClient.getESClient(cluster);
        if (CollectionUtils.isNotEmpty(aliasSwitchDTO.getDelAliasIndices()) && CollectionUtils.isEmpty(aliasSwitchDTO.getAddAliasIndices())) {
            //?????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
            ESIndicesGetAliasResponse response = client.admin().indices().prepareAlias(StringUtils.join(aliasSwitchDTO.getDelAliasIndices(), ",").split(",")).execute().actionGet(30, TimeUnit.SECONDS);
            if (null == response || null == response.getM() || response.getM().values().stream().noneMatch(indexNode -> indexNode.getAliases().containsKey(aliasSwitchDTO.getAliasName()))) {
                throw new ESOperateException("???????????????????????????");
            }
        }
        ESIndicesPutAliasResponse response = client.admin().indices().preparePutAlias().addPutAliasNodes(nodes).execute().actionGet(30, TimeUnit.SECONDS);
        if (null == response || !response.getAcknowledged()) {
            throw new ESOperateException("?????????????????????");
        }
        return Result.buildSucc();
    }

    private Result<List<PutAliasNode>> buildAliasNodes(ConsoleTemplateAliasSwitchDTO aliasSwitchDTO, List<TemplateLogicPO> indexTemplateLogicPOS, List<TemplateAliasPO> insertAliasList, List<Integer> deleteAliasList, Set<Integer> logicIdList) {
        List<PutAliasNode> nodes = new ArrayList<>();
        List<PutAliasNode> addNodes = buildAliasNodes(aliasSwitchDTO, PutAliasType.ADD, indexTemplateLogicPOS, insertAliasList, deleteAliasList, logicIdList);
        if (null == addNodes) {
            Result.buildFail("????????????????????????");
        } else if (CollectionUtils.isNotEmpty(addNodes)) {
            nodes.addAll(addNodes);
        }
        List<PutAliasNode> removeNodes = buildAliasNodes(aliasSwitchDTO, PutAliasType.REMOVE, indexTemplateLogicPOS, insertAliasList, deleteAliasList, logicIdList);
        if (removeNodes == null) {
            Result.buildFail("????????????????????????");
        } else if (CollectionUtils.isNotEmpty(removeNodes)) {
            nodes.addAll(removeNodes);
        }
        return Result.buildSucc(nodes);
    }

    private List<PutAliasNode> buildAliasNodes(ConsoleTemplateAliasSwitchDTO aliasSwitchDTO, PutAliasType aliasType, List<TemplateLogicPO> indexTemplateLogicPOS, List<TemplateAliasPO> insertAliasList, List<Integer> deleteAliasList, Set<Integer> logicIdList) {
        List<PutAliasNode> nodes = new ArrayList<>();
        List<String> indexList = null;
        String aliasName = aliasSwitchDTO.getAliasName();
        if (PutAliasType.ADD == aliasType) {
            indexList = aliasSwitchDTO.getAddAliasIndices();
        } else if (PutAliasType.REMOVE == aliasType) {
            indexList = aliasSwitchDTO.getDelAliasIndices();
        }

        if (CollectionUtils.isEmpty(indexList)) {
            return nodes;
        }
        for (String indexName : indexList) {
            Integer logicId = getLogicIdByIndexName(indexTemplateLogicPOS, indexName);
            if (null == logicId) {
                return null;
            }
            PutAliasNode node = new PutAliasNode();
            node.setType(aliasType);
            node.setAlias(aliasName);
            node.setIndex(indexName);
            nodes.add(node);
            TemplateAliasPO aliasPO = new TemplateAliasPO();
            if (!logicIdList.contains(logicId)) {
                //????????????ID??????????????????????????????????????????????????????????????????
                if (PutAliasType.ADD == aliasType) {
                    aliasPO.setName(aliasName);
                    aliasPO.setLogicId(logicId);
                    insertAliasList.add(aliasPO);
                }
                deleteAliasList.add(logicId);
                logicIdList.add(aliasPO.getLogicId());
            }
        }
        return nodes;
    }

    /**
     * ????????????????????????appId????????????????????????????????????????????????????????????????????????????????????????????????
     *
     * @param name
     * @param appId
     * @param logicId
     * @return
     */
    private Result aliasChecked(String name, Integer appId, Integer logicId) {
        if (StringUtils.isBlank(name)) {
            return Result.buildFail("????????????????????????");
        }
        if (name.length() < TEMPLATE_NAME_SIZE_MIN || name.length() > TEMPLATE_NAME_SIZE_MAX) {
            return Result.buildParamIllegal(String.format("??????????????????, %s-%s",TEMPLATE_NAME_SIZE_MIN,TEMPLATE_NAME_SIZE_MAX));
        }
        for (Character c : name.toCharArray()) {
            if (!TEMPLATE_NAME_CHAR_SET.contains(c)) {
                return Result.buildParamIllegal("????????????????????????, ????????????????????????????????????-???_???.");
            }
        }

        String prefix = name.substring(0, 1);
        if (StringUtils.containsAny(prefix, "_", "-", "+")) {
            return Result.buildParamIllegal("Invalid alias name must not start with '_', '-', or '+'");
        }

        // ????????????????????????
        List<IndexTemplateAlias> aliasList = ConvertUtil.list2List(indexTemplateAliasDAO.listAll(), IndexTemplateAlias.class);
        List<TemplateLogicPO> poList = indexTemplateLogicDAO.listAll();
        Set<Integer> logicIds = new HashSet<>();
        if (null != logicId) {
            logicIds.add(logicId);
        }
        if (CollectionUtils.isNotEmpty(aliasList)) {
            for (IndexTemplateAlias alias : aliasList) {
                if (alias.getName().equals(name)) {
                    logicIds.add(alias.getLogicId());
                }
                if (alias.getName().equals(name) && alias.getLogicId().equals(logicId)) {
                    // ????????????????????????????????????true????????????????????????
                    return Result.buildSucc(true);
                }
            }
        }
        //???????????????????????????????????????
        if (CollectionUtils.isNotEmpty(poList)) {
            if (poList.stream().anyMatch(IndexTemplateLogic -> IndexTemplateLogic.getName().startsWith(name) || name.startsWith(IndexTemplateLogic.getName()))) {
                return Result.buildFail("??????????????????????????????????????????");
            }
            List<TemplateLogicPO> templateLogicPOS = poList.stream().filter(po -> logicIds.contains(po.getId())).collect(Collectors.toList());
            Set<Integer> appIds = new HashSet<>();
            if (null != appId) {
                appIds.add(appId);
            }
            if (CollectionUtils.isNotEmpty(templateLogicPOS)) {
                appIds.addAll(templateLogicPOS.stream().map(TemplateLogicPO::getAppId).collect(Collectors.toSet()));
            } else if (CollectionUtils.isEmpty(templateLogicPOS) && CollectionUtils.isNotEmpty(logicIds)) {
                return Result.buildFail("????????????????????????");
            }
            if (appIds.size() > 1) {
                //?????????????????????appId?????????
                return Result.buildFail("?????????????????????");
            }
        }
        return null;
    }


    private Integer getLogicIdByIndexName(List<TemplateLogicPO> indexTemplateLogicPOS, String indexName) {
        for (TemplateLogicPO template : indexTemplateLogicPOS) {
            String expression = template.getExpression();
            //??????????????????????????????????????????
            if ((!expression.endsWith("*") && template.getName().equals(indexName)) || (expression.endsWith("*") && indexName.startsWith(expression.substring(0, expression.length() - 1)))) {
                return template.getId();
            }
        }
        return null;
    }
}
