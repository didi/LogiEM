package com.didi.arius.gateway.remote;

import com.didi.arius.gateway.common.metadata.TemplateAlias;
import com.didi.arius.gateway.remote.response.*;

/**
 * @author fitz
 * @date 2021/5/8 11:43 上午
 * 调用Arius-admin第三方接口
 */
public interface AriusAdminRemoteService {
    /**
     *
     * @return
     */
    AppListResponse listApp();

    /**
     *
     * @return
     */
    DataCenterListResponse listCluster();

    /**
     *
     * @param cluster
     * @return
     */
    TemplateInfoListResponse getTemplateInfoMap(String cluster);

    /**
     *
     * @param clusterName
     * @return
     */
    ActiveCountResponse getAliveCount(String clusterName);

    /**
     *
     * @return
     */
    IndexTemplateListResponse listDeployInfo();

    /**
     *
     * @return
     */
    DynamicConfigListResponse listQueryConfig();

    /**
     *
     * @param clusterName
     * @param hostName
     * @param port
     */
    void heartbeat(String clusterName, String hostName, Integer port);

    /**
     *
     * @param lastModifyTime
     * @param scrollId
     * @return
     */
    DSLTemplateListResponse listDslTemplates(long lastModifyTime, String scrollId);

    TempaletAliasResponse addAdminTemplateAlias(TemplateAlias templateAlias);

    TempaletAliasResponse delAdminTemplateAlias(TemplateAlias templateAlias);

}
