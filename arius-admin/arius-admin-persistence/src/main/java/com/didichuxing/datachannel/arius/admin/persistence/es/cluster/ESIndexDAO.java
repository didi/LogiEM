package com.didichuxing.datachannel.arius.admin.persistence.es.cluster;

import com.alibaba.fastjson.JSON;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.index.setting.ESIndicesGetAllSettingRequest;
import com.didichuxing.datachannel.arius.admin.common.exception.ESOperateException;
import com.didichuxing.datachannel.arius.admin.common.util.EnvUtil;
import com.didichuxing.datachannel.arius.admin.common.util.ListUtils;
import com.didichuxing.datachannel.arius.admin.persistence.es.BaseESDAO;
import com.didiglobal.logi.elasticsearch.client.ESClient;
import com.didiglobal.logi.elasticsearch.client.model.Client;
import com.didiglobal.logi.elasticsearch.client.model.type.ESVersion;
import com.didiglobal.logi.elasticsearch.client.request.index.exists.ESIndicesExistsRequest;
import com.didiglobal.logi.elasticsearch.client.request.index.getindex.ESIndicesGetIndexRequest;
import com.didiglobal.logi.elasticsearch.client.request.index.stats.ESIndicesStatsRequestBuilder;
import com.didiglobal.logi.elasticsearch.client.request.index.stats.IndicesStatsLevel;
import com.didiglobal.logi.elasticsearch.client.request.index.updatemapping.ESIndicesUpdateMappingRequestBuilder;
import com.didiglobal.logi.elasticsearch.client.request.index.updatesettings.ESIndicesUpdateSettingsRequestBuilder;
import com.didiglobal.logi.elasticsearch.client.response.indices.catindices.CatIndexResult;
import com.didiglobal.logi.elasticsearch.client.response.indices.catindices.ESIndicesCatIndicesResponse;
import com.didiglobal.logi.elasticsearch.client.response.indices.closeindex.ESIndicesCloseIndexResponse;
import com.didiglobal.logi.elasticsearch.client.response.indices.deletebyquery.ESIndicesDeleteByQueryResponse;
import com.didiglobal.logi.elasticsearch.client.response.indices.deleteindex.ESIndicesDeleteIndexResponse;
import com.didiglobal.logi.elasticsearch.client.response.indices.getalias.AliasIndexNode;
import com.didiglobal.logi.elasticsearch.client.response.indices.getalias.ESIndicesGetAliasResponse;
import com.didiglobal.logi.elasticsearch.client.response.indices.getindex.ESIndicesGetIndexResponse;
import com.didiglobal.logi.elasticsearch.client.response.indices.openindex.ESIndicesOpenIndexResponse;
import com.didiglobal.logi.elasticsearch.client.response.indices.putindex.ESIndicesPutIndexResponse;
import com.didiglobal.logi.elasticsearch.client.response.indices.refreshindex.ESIndicesRefreshIndexResponse;
import com.didiglobal.logi.elasticsearch.client.response.indices.stats.ESIndicesStatsResponse;
import com.didiglobal.logi.elasticsearch.client.response.indices.stats.IndexNodes;
import com.didiglobal.logi.elasticsearch.client.response.indices.updatemapping.ESIndicesUpdateMappingResponse;
import com.didiglobal.logi.elasticsearch.client.response.indices.updatesettings.ESIndicesUpdateSettingsResponse;
import com.didiglobal.logi.elasticsearch.client.response.setting.common.MappingConfig;
import com.didiglobal.logi.elasticsearch.client.response.setting.common.TypeConfig;
import com.didiglobal.logi.elasticsearch.client.response.setting.index.IndexConfig;
import com.didiglobal.logi.elasticsearch.client.response.setting.index.MultiIndexsConfig;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.didichuxing.datachannel.arius.admin.persistence.constant.ESOperateContant.*;

/**
 * @author d06679
 * @date 2019/3/29
 */
@Repository
public class ESIndexDAO extends BaseESDAO {

    /**
     * ????????????
     * @param cluster ??????
     * @param indexName ????????????
     * @return result
     */
    public boolean createIndex(String cluster, String indexName) {
        if (exist(cluster, indexName)) {
            LOGGER.warn("class=ESIndexDAO||method=createIndex||index already exist||cluster={}||indexName={}", cluster, indexName);
            return true;
        }

        ESClient client = fetchESClientByCluster(cluster);
        if (client != null) {
            ESIndicesPutIndexResponse response = client.admin().indices().preparePutIndex(indexName).execute()
                    .actionGet(ES_OPERATE_TIMEOUT, TimeUnit.SECONDS);
            return response.getAcknowledged();
        } else {
            return false;
        }
    }

    /**
     * ?????????????????????????????????ES Client
     * @param clusterName ????????????
     * @return
     */
    private ESClient fetchESClientByCluster(String clusterName) {
        ESClient client = esOpClient.getESClient(clusterName);
        if (client == null) {
            LOGGER.warn("class=ESIndexDAO||method=fetchESClientByCluster||cannot get es client,cluster={}", clusterName);
        }
        return client;
    }

    /**
     * ?????????????????????????????????
     * @param cluster ??????
     * @param indexName ??????
     * @param indexConfig ????????????
     * @return
     */
    public boolean createIndexWithConfig(String cluster, String indexName, IndexConfig indexConfig) {
        if (exist(cluster, indexName)) {
            LOGGER.warn("class=ESIndexDAO||method=createIndexWithConfig||index already exist||cluster={}||indexName={}", cluster, indexName);
            return true;
        }
        ESClient client = fetchESClientByCluster(cluster);
        if (client != null) {
            indexConfig.setVersion(ESVersion.valueBy(client.getEsVersion()));
            ESIndicesPutIndexResponse response = client.admin().indices().preparePutIndex(indexName)
                    .setIndexConfig(indexConfig).execute().actionGet(ES_OPERATE_TIMEOUT, TimeUnit.MINUTES);
            return response.getAcknowledged();
        } else {
            return false;
        }
    }

    /**
     * ??????????????????????????????????????????
     * @param cluster
     * @return
     */
    public boolean exist(String cluster, String expression) {
        Client client = fetchESClientByCluster(cluster);
        if (client != null) {
            return client.admin().indices().prepareExists(expression).execute()
                    .actionGet(ES_OPERATE_TIMEOUT, TimeUnit.SECONDS).isExists();
        }
        return false;
    }

    /**
     * ?????????????????????????????????????????????
     * @param cluster    ????????????
     * @param indexName  ????????????
     * @return
     */
    public boolean existByClusterAndIndexName(String cluster, String indexName) {
        Client client = fetchESClientByCluster(cluster);
        ESIndicesExistsRequest esIndicesExistsRequest = new ESIndicesExistsRequest();
        esIndicesExistsRequest.setIndex(indexName);
        if (client != null) {
            return client.admin().indices().exists(esIndicesExistsRequest)
                .actionGet(ES_OPERATE_TIMEOUT, TimeUnit.SECONDS).isExists();
        }
        return false;
    }

    /**
     * ????????????????????????
     * @param cluster ??????
     * @param indexNames ????????????
     * @return ??????
     */
    public MultiIndexsConfig batchGetIndexConfig(String cluster, List<String> indexNames) {
        ESClient client = esOpClient.getESClient(cluster);

        if (client != null) {
            String indexExpression = String.join(",", indexNames);
            ESIndicesGetIndexResponse getIndexResponse = client.admin().indices().prepareGetIndex(indexExpression).execute()
                    .actionGet(ES_OPERATE_TIMEOUT, TimeUnit.SECONDS);
            return getIndexResponse.getIndexsMapping();
        } else {
            return null;
        }
    }

    /**
     * ???????????????mapping??????
     * @param cluster ??????
     * @param indexName ????????????
     * @return
     */
    public MappingConfig getIndexMapping(String cluster, String indexName) {
        if (!exist(cluster, indexName)) {
            LOGGER.warn("class=ESIndexDAO||method=getIndexMapping||cluster={}||indexName={}||msg=index not exist",
                    cluster, indexName);
            return null;
        }

        ESClient client = esOpClient.getESClient(cluster);
        if (null == client) { return null;}
        ESIndicesGetIndexResponse response = client.admin().indices().prepareGetIndex(indexName).execute()
            .actionGet(ES_OPERATE_TIMEOUT, TimeUnit.SECONDS);

        return response.getIndexsMapping().getIndexConfig(indexName).getMappings();
    }

    /**
     * ???????????????mapping??????
     * @param cluster ??????
     * @param indexName ????????????
     * @return
     */
    public boolean updateIndexMapping(String cluster, String indexName, MappingConfig mappingConfig) {
        if (!exist(cluster, indexName)) {
            LOGGER.warn("class=ESIndexDAO||method=updateIndexMapping||cluster={}||indexName={}||msg=index not exist",
                    cluster, indexName);
            return true;
        }

        ESClient client = esOpClient.getESClient(cluster);
        Map<String, TypeConfig> typeConfigMap = mappingConfig.getMapping();
        if (typeConfigMap == null) {
            return false;
        }

        for(Map.Entry<String, TypeConfig> entry : typeConfigMap.entrySet()){
            String typeName = entry.getKey();
            ESIndicesUpdateMappingRequestBuilder builder = client.admin().indices().prepareUpdateMapping();
            builder.setIndex(indexName).setType(typeName).setTypeConfig(typeConfigMap.get(typeName));
            // es ????????????7 ???????????????includeTypeName ????????????????????????index mapping
            if (Integer.parseInt(client.getEsVersion().split("\\.")[0]) >= 7) {
                builder.setIncludeTypeName(true);
            }
            ESIndicesUpdateMappingResponse response = builder.execute()
                    .actionGet(ES_OPERATE_TIMEOUT, TimeUnit.SECONDS);;

            if (!response.getAcknowledged().booleanValue()) {
                return false;
            }
        }

        return true;
    }

    /**
     * ????????????
     * @param cluster ??????
     * @param indexName ????????????
     * @return
     */
    public boolean deleteIndex(String cluster, String indexName) {
        if (!exist(cluster, indexName)) {
            LOGGER.warn("class=ESIndexDAO||method=deleteIndex||cluster={}||indexName={}||msg=index not exist",
                    cluster, indexName);
            return true;
        }


        ESClient client = esOpClient.getESClient(cluster);
        ESIndicesDeleteIndexResponse response = client.admin().indices().prepareDeleteIndex(indexName).execute()
            .actionGet(ES_OPERATE_TIMEOUT, TimeUnit.SECONDS);
        return response.getAcknowledged();
    }

    /**
     * ????????????????????????????????????????????????????????????
     * @param cluster ????????????
     * @param expression ?????????????????????
     * @return
     */
    public List<CatIndexResult> catIndexByExpression(String cluster, String expression) {
        List<CatIndexResult> indices = new ArrayList<>();

        try {
            ESClient client = fetchESClientByCluster(cluster);
            if (client != null) {
                ESIndicesCatIndicesResponse response = client.admin().indices().prepareCatIndices(expression).execute()
                        .actionGet(ES_OPERATE_TIMEOUT, TimeUnit.SECONDS);
                indices.addAll(response.getCatIndexResults());
            }
        } catch (Exception e) {
            LOGGER.warn("class=ESIndexDAO||method=catIndexByExpression||errMsg={}||cluster={}||expression={}",
                    e.getMessage(), cluster, expression, e);
        }

        return indices;
    }

    /**
     * ???????????????????????????
     * @param cluster
     * @return
     */
    public List<CatIndexResult> catIndices(String cluster) {
        List<CatIndexResult> indices = Lists.newArrayList();

        try {
            ESClient client = fetchESClientByCluster(cluster);
            if (client != null) {
                ESIndicesCatIndicesResponse response = client.admin().indices().prepareCatIndices().execute()
                        .actionGet(ES_OPERATE_TIMEOUT, TimeUnit.SECONDS);
                indices.addAll(response.getCatIndexResults());
            }
        } catch (Exception e) {
            LOGGER.warn("class=ESIndexDAO||method=catIndexByExpression||errMsg={}||cluster={}",
                    e.getMessage(), cluster, e);
        }

        return indices;
    }

    /**
     * ??????????????????,????????????????????????
     * @param cluster
     * @param expression
     * @return
     */
    public Map<String, IndexNodes> getIndexByExpression(String cluster, String expression) {
        try {
            ESClient client = fetchESClientByCluster(cluster);
            if (client == null) {
                return null;
            }

            ESIndicesStatsResponse response = client.admin().indices().prepareStats(expression).execute()
                .actionGet(ES_OPERATE_TIMEOUT, TimeUnit.SECONDS);
            return response.getIndicesMap();
        } catch (Exception e) {
            LOGGER.warn("class=ESIndexDAO||method=getIndexByExpression||errMsg={}||cluster={}||expression={}",
                e.getMessage(), cluster, expression, e);
            return null;
        }
    }

    /**
     * ??????????????????,?????????????????????????????????????????????shard?????????
     * @param cluster
     * @param expression
     * @return
     */
    public Map<String, IndexNodes> getIndexStatsWithShards(String cluster, String expression) {
        try {
            ESClient client = fetchESClientByCluster(cluster);
            if (client == null) {
                return null;
            }

            ESIndicesStatsResponse response = client.admin().indices().prepareStats(expression).setLevel(IndicesStatsLevel.SHARDS).execute()
                    .actionGet(ES_OPERATE_TIMEOUT, TimeUnit.SECONDS);
            return response.getIndicesMap();
        } catch (Exception e) {
            LOGGER.warn("class=ESIndexDAO||method=getIndexByExpression||errMsg={}||cluster={}||expression={}",
                    e.getMessage(), cluster, expression, e);
            return Maps.newHashMap();
        }
    }

    /**
     * ??????????????????,????????????????????????
     * @param cluster
     * @param expression
     * @return
     */
    public Map<String/*index*/, AliasIndexNode> getAliasesByExpression(String cluster, String expression) {
        try {
            ESClient client = esOpClient.getESClient(cluster);
            ESIndicesGetAliasResponse response = client.admin().indices().prepareAlias(expression).execute()
                .actionGet(ES_OPERATE_TIMEOUT, TimeUnit.SECONDS);
            return response.getM();
        } catch (Exception e) {
            LOGGER.warn("class=ESIndexDAO||method=getAliasesByExpression||errMsg={}||cluster={}||expression={}",
                cluster, e.getMessage(), expression, e);
            return null;
        }
    }

    /**
     * ????????????
     * @param cluster ??????
     * @param delIndices ??????
     * @param delQueryDsl ??????
     * @return true/false
     */
    public boolean deleteByQuery(String cluster, String delIndices, String delQueryDsl) throws ESOperateException {
        ESClient client = fetchESClientByCluster(cluster);
        if (client == null) {
            throw new ESOperateException("cannot fetch es client for cluster: " + cluster);
        }

        ESIndicesDeleteByQueryResponse response = client.admin().indices().prepareDeleteByQuery().setIndex(delIndices)
            .setQuery(delQueryDsl).setHighES(true).execute().actionGet(ES_OPERATE_TIMEOUT, TimeUnit.SECONDS);

        if (response.getRoot().containsKey("error")) {
            throw new ESOperateException(response.getRoot().get("error").toString());
        }

        return true;
    }

    /**
     * ???????????????setting ??????
     * @param cluster ??????
     * @param indices indices
     * @param tgtRack tgtRack
     * @return true/false
     */
    public boolean batchUpdateIndexRack(String cluster, List<String> indices, String tgtRack) {
        return putIndexSetting(cluster, indices, INDEX_INCLUDE_RACK, tgtRack, "");
    }

    /**
     * ????????????????????????
     * @param cluster ??????
     * @param indexNames ????????????
     * @param block ??????
     * @return result
     */
    public boolean blockIndexWrite(String cluster, List<String> indexNames, boolean block) {
        return putIndexSetting(cluster, indexNames, INDEX_BLOCKS_WRITE, String.valueOf(block), "false");
    }

    /**
     * ????????????????????????
     * @param cluster ??????
     * @param indexNames ????????????
     * @param block ??????
     * @return result
     */
    public boolean blockIndexRead(String cluster, List<String> indexNames, boolean block) {
        return putIndexSetting(cluster, indexNames, INDEX_BLOCKS_READ, String.valueOf(block), "false");
    }

    /**
     * refresh??????
     * @param cluster ??????
     * @param indexNames ????????????
     * @return result
     */
    public boolean refreshIndex(String cluster, List<String> indexNames) {
        ESClient client = fetchESClientByCluster(cluster);
        if (client == null) {
            return false;
        }

        ESIndicesRefreshIndexResponse response = client.admin().indices()
            .prepareRefreshIndex(String.join(",", indexNames)).execute()
            .actionGet(ES_OPERATE_TIMEOUT, TimeUnit.SECONDS);

        return response.getFaild() == 0;
    }

    /**
     * ???????????????????????????setting???????????????
     * @param cluster ??????????????????
     * @param settings key???setting?????? value???setting??????
     */
    public boolean putIndexSettings(String cluster, List<String> indices,
                                    Map</*setting??????*/String, /*setting??????*/String> settings) {
        ESClient client = fetchESClientByCluster(cluster);
        if (client == null || MapUtils.isEmpty(settings)) {
            LOGGER.warn("class=ESTemplateDAO||method=putIndexSettings||get settings fail||clusterName={}||indexNames={}",
                    cluster, indices);
            return false;
        }

        ESIndicesUpdateSettingsRequestBuilder updateSettingsRequestBuilder = client.admin().indices().prepareUpdateSettings(String.join(",", indices));
        //???????????????????????????setting???????????????????????????
        settings.forEach(updateSettingsRequestBuilder::addSettings);

        return updateSettingsRequestBuilder.execute().actionGet(ES_OPERATE_TIMEOUT, TimeUnit.SECONDS).getAcknowledged();
    }

    /**
     * ??????????????????
     * @param cluster ??????
     * @param indices ??????
     * @param settingName ????????????
     * @param setting ??????
     * @param defaultValue ???????????????
     * @return true/false
     */
    public boolean putIndexSetting(String cluster, List<String> indices, String settingName, String setting,
                                   String defaultValue) {
        ESClient client = fetchESClientByCluster(cluster);
        if (client == null) {
            return false;
        }

        MultiIndexsConfig multiIndexsConfig = batchGetIndexConfig(cluster, indices);

        List<String> needOps = Lists.newArrayList();
        for (String indexName : indices) {
            IndexConfig indexConfig = multiIndexsConfig.getIndexConfig(indexName);
            if (indexConfig == null) {
                continue;
            }

            //??????????????????put-setting????????????????????????"index."????????? ???????????????????????????
            Map<String, String> config = indexConfig.getSettings();
            String src = config.get("index." + settingName);
            if (src == null) {
                src = defaultValue;
            }
            if (src.equals(String.valueOf(setting))) {
                continue;
            }
            needOps.add(indexName);
        }

        if (CollectionUtils.isEmpty(needOps)) {
            return true;
        }

        ESIndicesUpdateSettingsResponse updateSettingsResponse = client.admin().indices()
            .prepareUpdateSettings(String.join(",", needOps)).addSettings(settingName, String.valueOf(setting))
            .execute().actionGet(ES_OPERATE_TIMEOUT, TimeUnit.SECONDS);
        return updateSettingsResponse.getAcknowledged();
    }

    /**
     * ????????????
     * @param cluster ??????
     * @param indices ??????
     * @return result
     */
    public boolean closeIndex(String cluster, List<String> indices) {
        ESClient client = fetchESClientByCluster(cluster);
        if (client == null) {
            return false;
        }
        ESIndicesCloseIndexResponse response = client.admin().indices().prepareCloseIndex(String.join(",", indices))
            .execute().actionGet(ES_OPERATE_TIMEOUT, TimeUnit.SECONDS);
        return response.getAcknowledged();
    }

    /**
     * ????????????
     * @param cluster ??????
     * @param indices ??????
     * @return result
     */
    public boolean openIndex(String cluster, List<String> indices) {
        ESClient client = fetchESClientByCluster(cluster);
        if (client == null) {
            return false;
        }
        ESIndicesOpenIndexResponse response = client.admin().indices().prepareOpenIndex(String.join(",", indices))
            .execute().actionGet(ES_OPERATE_TIMEOUT, TimeUnit.SECONDS);
        return response.getAcknowledged();
    }

    /**
     * ??????????????????
     * @param clusterName       ?????????
     * @param indexName         ?????????
     * @return result
     */
    public MultiIndexsConfig getIndexConfigs(String clusterName, String indexName) {
        ESClient esClient = esOpClient.getESClient(clusterName);

        if (null == esClient) {
            return null;
        }

        ESIndicesGetIndexRequest request = new ESIndicesGetIndexRequest();
        request.setIndices(indexName);

        ESIndicesGetIndexResponse response = null;
        try {
            response = esClient.admin().indices().getIndex(request).actionGet(ES_OPERATE_TIMEOUT, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOGGER.warn("class=ESTemplateDAO||method=getIndexConfigs||get index fail||clusterName={}||indexName={}||msg={}",
                    clusterName, indexName, e.getMessage(), e);
        }

        if (response == null) {
            return null;
        }

        if (!EnvUtil.isOnline()) {
            LOGGER.warn("class=ESTemplateDAO||method=getIndexConfigs||response={}", JSON.toJSONString(response));
        }

        return response.getIndexsMapping();
    }

    /**
     * ?????????????????????????????????????????????setting??????
     * @param clusterName       ????????????
     * @param indexNames        ????????????
     * @param tryTimes
     * @return
     */
    public Map<String, IndexConfig> getIndicesSetting(String clusterName, List<String> indexNames, int tryTimes) {
        ESClient esClient = esOpClient.getESClient(clusterName);
        if (null == esClient) {
            return Maps.newHashMap();
        }

        ESIndicesGetAllSettingRequest request = new ESIndicesGetAllSettingRequest();
        request.setDefaultSettingFlag(true);
        request.mapping(false);
        request.alias(false);
        request.setIndices(ListUtils.strList2StringArray(indexNames));

        ESIndicesGetIndexResponse response = null;
        try {
            for (int i = 0; i < tryTimes; i++) {
                response = esClient.admin().indices().getIndex(request).actionGet(ES_OPERATE_TIMEOUT, TimeUnit.SECONDS);
                if (null != response) {
                    break;
                }
            }
        } catch (Exception e) {
            LOGGER.warn("class=ESTemplateDAO||method=getIndexConfigs||get index fail||clusterName={}||indexName={}||msg={}",
                    clusterName, e.getMessage(), e);
        }

        if (response == null) {
            return Maps.newHashMap();
        }

        if (!EnvUtil.isOnline()) {
            LOGGER.warn("class=ESTemplateDAO||method=getIndexConfigs||response={}", JSON.toJSONString(response));
        }

        return response.getIndexsMapping().getIndexConfigMap();
    }

    /**
     * ????????????IndexNodes??????
     *
     * @param clusterName
     * @return
     */
    @Nullable
    public Map<String, IndexNodes> getIndexNodes(String clusterName, String templateExp) {
        ESClient esClient = esOpClient.getESClient(clusterName);

        if (esClient == null) {
            LOGGER.error("class=ClusterClientPool||method=getIndexNodes||clusterName={}||errMsg=esClient is null",
                    clusterName);
            return null;
        }

        ESIndicesStatsResponse response = null;
        ESIndicesStatsRequestBuilder builder = null;
        try {
            if (StringUtils.isBlank(templateExp)) {
                builder = esClient.admin().indices().prepareStats();
            } else {
                builder = esClient.admin().indices().prepareStats(templateExp);
            }
            response = builder.setStore(true).setDocs(true).execute().actionGet(ES_OPERATE_TIMEOUT, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOGGER.error("class=ClusterClientPool||method=getIndexNodes||clusterName={}||errMsg=get {} index stats error. ",
                    clusterName, templateExp, e);
        }

        if (response == null) {
            return null;
        }

        return response.getIndicesMap();
    }
}
