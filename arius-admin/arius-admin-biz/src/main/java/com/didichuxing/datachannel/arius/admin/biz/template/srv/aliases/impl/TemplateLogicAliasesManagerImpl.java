package com.didichuxing.datachannel.arius.admin.biz.template.srv.aliases.impl;

import java.util.*;
import java.util.stream.Collectors;

import com.didichuxing.datachannel.arius.admin.biz.template.srv.aliases.TemplateLogicAliasesManager;
import com.didichuxing.datachannel.arius.admin.biz.template.srv.aliases.TemplatePhyAliasesManager;
import com.didichuxing.datachannel.arius.admin.biz.template.srv.base.BaseTemplateSrv;
import com.didichuxing.datachannel.arius.admin.client.bean.common.Result;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.template.alias.ConsoleAliasDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.template.alias.ConsoleLogicTemplateAliasesDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.template.alias.ConsoleLogicTemplateDeleteAliasesDTO;
import com.didichuxing.datachannel.arius.admin.common.Tuple;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.app.AppTemplateAuth;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.template.IndexTemplateAlias;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.template.IndexTemplateLogicWithPhyTemplates;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.template.IndexTemplatePhy;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.template.IndexTemplatePhyAlias;
import com.didichuxing.datachannel.arius.admin.common.constant.template.TemplateServiceEnum;
import com.didichuxing.datachannel.arius.admin.common.exception.ESOperateException;
import com.didichuxing.datachannel.arius.admin.common.util.AriusObjUtils;
import com.didichuxing.datachannel.arius.admin.core.service.app.AppLogicTemplateAuthService;
import com.didichuxing.datachannel.arius.admin.core.service.es.ESIndexService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static com.didichuxing.datachannel.arius.admin.client.constant.operaterecord.ModuleEnum.TEMPLATE;
import static com.didichuxing.datachannel.arius.admin.client.constant.operaterecord.OperationEnum.EDIT_TEMPLATE_ALIASES;
import static com.didichuxing.datachannel.arius.admin.common.constant.template.TemplateServiceEnum.TEMPLATE_ALIASES;

/**
 * @author zqr
 * @date 2020-09-09
 */
@Service
public class TemplateLogicAliasesManagerImpl extends BaseTemplateSrv implements TemplateLogicAliasesManager {

    private static final String OPERATION_FAILED_TIPS = "???????????????????????????";

    private static final String OPERATOR_IS_NULL_TIPS = "???????????????";

    @Autowired
    private TemplatePhyAliasesManager templatePhyAliasesManager;

    @Autowired
    private ESIndexService              esIndexService;

    @Autowired
    private AppLogicTemplateAuthService appLogicTemplateAuthService;

    @Override
    public TemplateServiceEnum templateService() {
        return TEMPLATE_ALIASES;
    }

    @Override
    public Result<List<IndexTemplatePhyAlias>> getAliases(Integer logicId) {
        return fetchTemplateAliasesByLogicId(logicId);
    }

    /**
     * ????????????
     * ?????????????????????, ????????????, ?????????????????????????????????, ?????????????????????????????????
     * @return list
     */
    @Override
    public List<IndexTemplateAlias> listAlias() {
        return listAlias(templateLogicService.getAllLogicTemplateWithPhysicals());
    }

    /**
     * ????????????
     *
     * @return list
     */
    @Override
    public List<IndexTemplateAlias> listAlias(List<IndexTemplateLogicWithPhyTemplates> templateLogicList) {
        List<IndexTemplateAlias> aliases = new ArrayList<>();
        Set<String> clusters = new HashSet<>();
        for (IndexTemplateLogicWithPhyTemplates templateLogicWithPhyTemplates : templateLogicList) {
            if (null != templateLogicWithPhyTemplates && null != templateLogicWithPhyTemplates.getMasterPhyTemplate()
                    && StringUtils.isNotBlank(templateLogicWithPhyTemplates.getMasterPhyTemplate().getCluster())) {
                clusters.add(templateLogicWithPhyTemplates.getMasterPhyTemplate().getCluster());
            }
        }

        try {
            Map<String, List<IndexTemplatePhyAlias>> map =  templatePhyAliasesManager.fetchAllTemplateAliases(new ArrayList<>(clusters));
            for (IndexTemplateLogicWithPhyTemplates templateLogic : templateLogicList) {
                for (IndexTemplatePhyAlias physicalAlias : map.get(templateLogic.getName())) {
                    aliases.add(fetchAlias(templateLogic.getId(), physicalAlias));
                }
            }

        } catch (ESOperateException e) {
            LOGGER.info("class=TemplateLogicAliasesManagerImpl||method=listAlias||"
                            + "msg=esTemplateNotFound||clusters={}",
                    clusters);
        }

        return aliases;
    }

    @Override
    public List<IndexTemplateAlias> getAliasesById(Integer logicId) {
        List<IndexTemplateAlias> templateAliases = new ArrayList<>();

        Result<List<IndexTemplatePhyAlias>> result = fetchTemplateAliasesByLogicId(logicId);
        if (result.success()) {
            List<IndexTemplatePhyAlias> aliases = result.getData();
            for (IndexTemplatePhyAlias physicalAlias : aliases) {
                templateAliases.add(fetchAlias(logicId, physicalAlias));
            }
        }

        return templateAliases;
    }

    @Override
    public Result<Void> createAliases(ConsoleLogicTemplateAliasesDTO aliases, String operator) {
        if (AriusObjUtils.isNull(operator)) {
            return Result.buildParamIllegal(OPERATOR_IS_NULL_TIPS);
        }

        if (aliases == null || CollectionUtils.isEmpty(aliases.getAliases())) {
            return Result.buildParamIllegal("??????????????????");
        }

        Result<Void> operationResult = createTemplateAliases(aliases.getLogicId(), aliases.getAliases());
        if (operationResult.success()) {
            operateRecordService.save(TEMPLATE, EDIT_TEMPLATE_ALIASES, aliases.getLogicId(), "-", operator);
        }

        return operationResult;
    }

    /**
     * ??????????????????
     * @param aliases ??????????????????
     * @param operator ?????????
     * @return
     */
    @Override
    public Result<Void> modifyAliases(ConsoleLogicTemplateAliasesDTO aliases, String operator) {
        if (AriusObjUtils.isNull(operator)) {
            return Result.buildParamIllegal(OPERATOR_IS_NULL_TIPS);
        }

        if (aliases == null || CollectionUtils.isEmpty(aliases.getAliases())) {
            return Result.buildParamIllegal("??????????????????");
        }

        Result<Void> operationResult = modifyTemplateAliases(aliases.getLogicId(), aliases.getAliases());
        if (operationResult.success()) {
            operateRecordService.save(TEMPLATE, EDIT_TEMPLATE_ALIASES, aliases.getLogicId(), "-", operator);
        }

        return operationResult;
    }

    @Override
    public Result<Void> deleteTemplateAliases(ConsoleLogicTemplateDeleteAliasesDTO deleteAliasesDTO, String operator) {
        if (AriusObjUtils.isNull(operator)) {
            return Result.buildParamIllegal(OPERATOR_IS_NULL_TIPS);
        }

        if (deleteAliasesDTO == null || CollectionUtils.isEmpty(deleteAliasesDTO.getAliases())) {
            return Result.buildParamIllegal("???????????????????????????");
        }

        Result<Void> operationResult = deleteTemplateAliases(deleteAliasesDTO.getLogicId(), deleteAliasesDTO.getAliases());

        if (operationResult.success()) {
            operateRecordService.save(TEMPLATE, EDIT_TEMPLATE_ALIASES, deleteAliasesDTO.getLogicId(), "-", operator);
        }

        return operationResult;
    }

    @Override
    public Result<List<IndexTemplatePhyAlias>> fetchTemplateAliasesByLogicId(Integer logicId) {
        Result<IndexTemplatePhy> result = fetchAnyOneLogicTemplateMasterPhysicalTemplate(logicId);
        if (result.failed()) {
            return Result.buildFrom(result);
        }

        IndexTemplatePhy indexTemplatePhy = result.getData();

        try {
            return Result.buildSucc( templatePhyAliasesManager.fetchTemplateAliases(indexTemplatePhy.getCluster(),
                indexTemplatePhy.getName()));
        } catch (ESOperateException e) {
            LOGGER.warn("class=TemplateLogicAliasesManagerImpl||method=fetchTemplateAliasesByLogicId||"
                        + "msg=failedFetchTemplateAliases||cluster={}||templateName={}",
                indexTemplatePhy.getCluster(), indexTemplatePhy.getName(), e);

            return Result.buildFail("?????????????????????????????????" + e.getMessage());
        }
    }

    @Override
    public Result<Void> createTemplateAliases(Integer logicId, List<ConsoleAliasDTO> aliases) {
        Result<IndexTemplatePhy> result = fetchAnyOneLogicTemplateMasterPhysicalTemplate(logicId);
        if (result.failed()) {
            return Result.buildFrom(result);
        }

        IndexTemplatePhy indexTemplatePhy = result.getData();
        if (!isTemplateSrvOpen(indexTemplatePhy.getCluster())) {
            return Result.buildFail(indexTemplatePhy.getCluster() + "????????????" + templateServiceName());
        }

        try {
            if (templatePhyAliasesManager.batchCreateTemplateAliases(indexTemplatePhy.getCluster(),
                indexTemplatePhy.getName(), convertAliases(aliases))) {
                return Result.buildSucc();
            }
        } catch (ESOperateException e) {
            return Result.buildFail(e.getMessage());
        }

        return Result.buildFail(OPERATION_FAILED_TIPS);
    }

    @Override
    public Result<Void> modifyTemplateAliases(Integer logicId, List<ConsoleAliasDTO> aliases) {
        Result<IndexTemplatePhy> result = fetchAnyOneLogicTemplateMasterPhysicalTemplate(logicId);
        if (result.failed()) {
            return Result.buildFrom(result);
        }

        IndexTemplatePhy indexTemplatePhy = result.getData();
        if (!isTemplateSrvOpen(indexTemplatePhy.getCluster())) {
            return Result.buildFail(indexTemplatePhy.getCluster() + "????????????" + templateServiceName());
        }

        try {
            if (templatePhyAliasesManager.modifyTemplateAliases(indexTemplatePhy.getCluster(),
                indexTemplatePhy.getName(), convertAliases(aliases))) {
                return Result.buildSucc();
            }
        } catch (ESOperateException e) {
            return Result.buildFail(e.getMessage());
        }

        return Result.buildFail(OPERATION_FAILED_TIPS);

    }

    /**
     * ????????????????????????
     * @param logicId ????????????ID
     * @param aliases ????????????
     * @return
     */
    @Override
    public Result<Void> deleteTemplateAliases(Integer logicId, List<String> aliases) {
        Result<IndexTemplatePhy> result = fetchAnyOneLogicTemplateMasterPhysicalTemplate(logicId);
        if (result.failed()) {
            return Result.buildFrom(result);
        }

        IndexTemplatePhy indexTemplatePhy = result.getData();

        if (!isTemplateSrvOpen(indexTemplatePhy.getCluster())) {
            return Result.buildFail(indexTemplatePhy.getCluster() + "????????????" + templateServiceName());
        }

        try {
            if (templatePhyAliasesManager.deleteTemplateAliases(indexTemplatePhy.getCluster(),
                indexTemplatePhy.getName(), aliases)) {
                return Result.buildSucc();
            }
        } catch (ESOperateException e) {
            return Result.buildFail(e.getMessage());
        }

        return Result.buildFail(OPERATION_FAILED_TIPS);
    }

    /**
     * ???????????????????????????
     *
     * @param appId appId
     */
    @Override
    public Result<List<Tuple<String/*index*/, String/*aliases*/>>> getAllTemplateAliasesByAppid(Integer appId) {
        List<Tuple<String, String>> aliases = new ArrayList<>();

        List<AppTemplateAuth> appTemplateAuths = appLogicTemplateAuthService.getTemplateAuthsByAppId(appId);
        if (CollectionUtils.isEmpty(appTemplateAuths)) {
            return Result.build(true);
        }

        appTemplateAuths.parallelStream().forEach(appTemplateAuth -> {
            IndexTemplateLogicWithPhyTemplates logicWithPhysical = this.templateLogicService
                .getLogicTemplateWithPhysicalsById(appTemplateAuth.getTemplateId());

            if (null != logicWithPhysical && logicWithPhysical.hasPhysicals()) {
                IndexTemplatePhy indexTemplatePhysical = logicWithPhysical.getPhysicals().get(0);

                if (!isTemplateSrvOpen(indexTemplatePhysical.getCluster())) {
                    return;
                }

                aliases.addAll(esIndexService.syncGetIndexAliasesByExpression(indexTemplatePhysical.getCluster(),
                    indexTemplatePhysical.getExpression()));
            }
        });

        return Result.buildSucc(aliases.stream().collect(Collectors.toList()));
    }

    /**************************************** private method ****************************************************/
    /**
     * ??????????????????Master??????????????????
     * @param logicId ????????????ID
     * @return
     */
    private Result<IndexTemplatePhy> fetchAnyOneLogicTemplateMasterPhysicalTemplate(Integer logicId) {
        if (logicId == null) {
            return Result.buildNotExist("???????????????ID??? " + logicId);
        }

        IndexTemplateLogicWithPhyTemplates templateLogicWithPhysical = templateLogicService
            .getLogicTemplateWithPhysicalsById(logicId);

        if (templateLogicWithPhysical == null) {
            return Result.buildNotExist("?????????????????????, ID:" + logicId);
        }

        if (!templateLogicWithPhysical.hasPhysicals()) {
            return Result.buildNotExist("????????????????????????ID:" + logicId);
        }

        IndexTemplatePhy indexTemplatePhy = templateLogicWithPhysical.getMasterPhyTemplate();
        if (indexTemplatePhy != null) {
            return Result.buildSucc(indexTemplatePhy);
        }

        return Result.buildNotExist("?????????????????????Master?????????????????????ID:" + logicId);
    }

    /**
     * ??????????????????
     * @param logicId ????????????ID
     * @param alias ????????????
     * @return
     */
    private IndexTemplateAlias fetchAlias(Integer logicId, IndexTemplatePhyAlias alias) {
        if (alias != null) {
            IndexTemplateAlias templateAlias = new IndexTemplateAlias();
            templateAlias.setName(alias.getAlias());
            templateAlias.setLogicId(logicId);
            return templateAlias;
        }

        return null;
    }

    /**
     * ??????????????????
     * @param aliasDTOS ??????DTO??????
     * @return
     */
    private List<IndexTemplatePhyAlias> convertAliases(List<ConsoleAliasDTO> aliasDTOS) {
        List<IndexTemplatePhyAlias> aliases = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(aliasDTOS)) {
            for (ConsoleAliasDTO aliasDTO: aliasDTOS) {
                aliases.add(convertAlias(aliasDTO));
            }
        }
        return aliases;
    }

    /**
     * ????????????
     * @param aliasDTO ??????DTO
     * @return
     */
    private IndexTemplatePhyAlias convertAlias(ConsoleAliasDTO aliasDTO) {
        if (aliasDTO != null) {
            IndexTemplatePhyAlias alias = new IndexTemplatePhyAlias();
            alias.setAlias(aliasDTO.getAlias());
            alias.setFilter(aliasDTO.getFilter());
            return alias;
        }
        return null;
    }
}
