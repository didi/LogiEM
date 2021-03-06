package com.didichuxing.datachannel.arius.admin.biz.template.srv.setting.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.didichuxing.datachannel.arius.admin.biz.template.srv.base.BaseTemplateSrv;
import com.didichuxing.datachannel.arius.admin.biz.template.srv.mapping.TemplateLogicMappingManager;
import com.didichuxing.datachannel.arius.admin.biz.template.srv.precreate.TemplatePreCreateManager;
import com.didichuxing.datachannel.arius.admin.biz.template.srv.setting.TemplateLogicSettingsManager;
import com.didichuxing.datachannel.arius.admin.biz.template.srv.setting.TemplatePhySettingsManager;
import com.didichuxing.datachannel.arius.admin.client.bean.common.Result;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.template.ConsoleTemplateSettingDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.template.TemplateSettingDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.vo.template.TemplateSettingVO;
import com.didichuxing.datachannel.arius.admin.client.mapping.AriusIndexTemplateSetting;
import com.didichuxing.datachannel.arius.admin.client.mapping.AriusTypeProperty;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.template.IndexTemplateLogicWithMapping;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.template.IndexTemplateLogicWithPhyTemplates;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.template.IndexTemplatePhy;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.template.IndexTemplatePhySettings;
import com.didichuxing.datachannel.arius.admin.common.constant.template.TemplateServiceEnum;
import com.didichuxing.datachannel.arius.admin.common.exception.AdminOperateException;
import com.didichuxing.datachannel.arius.admin.common.exception.ESOperateException;
import com.didichuxing.datachannel.arius.admin.common.util.AriusObjUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static com.didichuxing.datachannel.arius.admin.common.constant.template.TemplateServiceEnum.TEMPLATE_SETTING;
import static com.didichuxing.datachannel.arius.admin.client.mapping.AriusIndexTemplateSetting.*;

/**
 * ??????setting????????????
 * @author zqr
 * @date 2020-09-09
 */
@Service
public class TemplateLogicSettingsManagerImpl extends BaseTemplateSrv implements TemplateLogicSettingsManager {

    @Autowired
    private TemplatePhySettingsManager templatePhySettingsManager;

    @Autowired
    private TemplateLogicMappingManager templateLogicMappingManager;

    @Autowired
    private TemplatePreCreateManager templatePreCreateManager;

    @Override
    public TemplateServiceEnum templateService() {
        return TEMPLATE_SETTING;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> modifySetting(ConsoleTemplateSettingDTO settingDTO, String operator) throws AdminOperateException {

        LOGGER.info("class=TemplateLogicServiceImpl||method=modifySetting||operator={}||setting={}", operator,
            JSON.toJSONString(settingDTO));

        if (AriusObjUtils.isNull(operator)) {
            return Result.buildParamIllegal("???????????????");
        }

        if (settingDTO.getSetting() == null || settingDTO.getSetting().getAnalysis() == null) {
            return Result.buildParamIllegal("setting??????????????????");
        }

        Result<Void> result = updateSettings(settingDTO.getLogicId(), operator, settingDTO.getSetting());
        if (result.success()) {
            templatePreCreateManager.reBuildTomorrowIndex(settingDTO.getLogicId(), 3);
        }

        return result;
    }

    @Override
    public Result<Void> customizeSetting(TemplateSettingDTO settingDTO, String operator) throws AdminOperateException {

        LOGGER.info("class=TemplateLogicServiceImpl||method=modifySetting||operator={}||setting={}", operator,
                JSON.toJSONString(settingDTO));

        if (AriusObjUtils.isNull(operator)) {
            return Result.buildParamIllegal("???????????????");
        }

        // ????????????setting??????????????????????????????setting
        AriusIndexTemplateSetting settings = new AriusIndexTemplateSetting();
        settings.setReplicasNum(settingDTO.isCancelCopy() ? 0 : 1);
        settings.setTranslogDurability(settingDTO.isAsyncTranslog() ? ASYNC : REQUEST);

        Result<Void> result = updateSettings(settingDTO.getLogicId(), operator, settings);
        if (result.success()) {
            templatePreCreateManager.reBuildTomorrowIndex(settingDTO.getLogicId(), 3);
        }

        return result;
    }

    /**
     * ??????????????????settings
     *
     * @param logicId ????????????ID
     * @return
     * @throws AdminOperateException
     */
    @Override
    public Result<IndexTemplatePhySettings> getSettings(Integer logicId) {
        return getTemplateSettings(logicId);
    }

    @Override
    public Result<TemplateSettingVO> buildTemplateSettingVO(Integer logicId) {
        // ???es????????????????????????????????????settings??????
        Result<IndexTemplatePhySettings> indexTemplateSettingsResult = getSettings(logicId);
        if (indexTemplateSettingsResult.failed()) {
            return Result.buildFrom(indexTemplateSettingsResult);
        }

        IndexTemplatePhySettings indexTemplatePhySettings = indexTemplateSettingsResult.getData();

        //  ????????????setting??????????????????
        Map<String, String> flatIndexTemplateMap = indexTemplatePhySettings.flatSettings();

        //????????????setting????????????,??????????????????,cancelCopy???true,???translog???????????????asyncTranslog???true
        TemplateSettingVO templateSettingVO = new TemplateSettingVO();

        // translog?????????????????????request?????????
        templateSettingVO.setAsyncTranslog(flatIndexTemplateMap.containsKey(TRANSLOG_DURABILITY_KEY)
                && flatIndexTemplateMap.get(TRANSLOG_DURABILITY_KEY).equals(ASYNC));

        // ???????????????????????????????????????
        templateSettingVO.setCancelCopy(flatIndexTemplateMap.containsKey(NUMBER_OF_REPLICAS_KEY)
                && Integer.parseInt(flatIndexTemplateMap.get(NUMBER_OF_REPLICAS_KEY)) == 0);

        // ?????????????????????????????????????????????index.analysis??????????????????????????????
        templateSettingVO.setAnalysis(getAnalysisFromTemplateSettings(indexTemplatePhySettings));

        // ?????????????????????dynamic_templates
        templateSettingVO.setDynamicTemplates(getDynamicTemplatesByLogicTemplate(logicId));

        return Result.buildSucc(templateSettingVO);
    }

    /**
     * ??????settings??????
     * @param logicId ??????ID
     * @param settings settings
     * @return
     */
    @Override
    public Result<Void> updateSettings(Integer logicId, String operator, AriusIndexTemplateSetting settings) {
        IndexTemplateLogicWithPhyTemplates templateLogicWithPhysical = templateLogicService
            .getLogicTemplateWithPhysicalsById(logicId);

        if (templateLogicWithPhysical == null) {
            return Result.buildNotExist("?????????????????????, ID:" + logicId);
        }

        if (!templateLogicWithPhysical.hasPhysicals()) {
            return Result.buildNotExist("????????????????????????ID:" + logicId);
        }

        List<IndexTemplatePhy> templatePhysicals = templateLogicWithPhysical.fetchMasterPhysicalTemplates();

        if (!isTemplateSrvOpen(templatePhysicals)) {
            return Result.buildFail("??????????????????" + templateServiceName());
        }

        for (IndexTemplatePhy templatePhysical : templatePhysicals) {
            try {
                templatePhySettingsManager.mergeTemplateSettings(logicId, templatePhysical.getCluster(),
                    templatePhysical.getName(), operator, settings.toJSON());
            } catch (AdminOperateException adminOperateException) {
                return Result.buildFail(adminOperateException.getMessage());
            }
        }

        return Result.buildSucc();
    }

    /**
     * ????????????ID??????Settings
     * @param logicId ??????ID
     * @return
     */
    @Override
    public Result<IndexTemplatePhySettings> getTemplateSettings(Integer logicId) {
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
            try {
                return Result.buildSucc( templatePhySettingsManager
                    .fetchTemplateSettings(indexTemplatePhy.getCluster(), indexTemplatePhy.getName()));
            } catch (ESOperateException e) {
                return Result.buildFail(e.getMessage());
            }
        }

        return Result.buildFail("?????????Master?????????????????????ID???" + logicId);
    }

    /**************************************** private method ****************************************************/
    /**
     * ??????????????????id???????????????dynamic_templates??????
     * @param logicId ????????????id
     * @return
     */
    private JSONArray getDynamicTemplatesByLogicTemplate(Integer logicId) {
        Result<IndexTemplateLogicWithMapping> templateWithMapping = templateLogicMappingManager.getTemplateWithMapping(logicId);
        if (templateWithMapping.failed()) {
            LOGGER.warn("class=TemplateLogicServiceImpl||method=getDynamicTemplatesByLogicTemplate||logicTemplateId={}||msg={}",
                    logicId, templateWithMapping.getMessage());
            return null;
        }

        // ??????????????????????????????????????????mapping??????
        List<AriusTypeProperty> typeProperties = templateWithMapping.getData().getTypeProperties();
        if (CollectionUtils.isEmpty(typeProperties)) {
            return null;
        }

        // ?????????????????????????????????mapping?????????dynamic_templates??????
        return typeProperties.get(0).getDynamicTemplates();
    }

    private JSONObject getAnalysisFromTemplateSettings(IndexTemplatePhySettings indexTemplatePhySettings) {
        JSONObject indexSettings = indexTemplatePhySettings.getSettings().getJSONObject("index");
        if (AriusObjUtils.isNull(indexSettings)) {
            LOGGER.info("class=TemplateLogicServiceImpl||method=getAnalysisFromTemplateSettings||settings={}||msg= no index settings",
                    indexTemplatePhySettings);
            return null;
        }

        return indexSettings.getJSONObject("analysis");
    }
}
