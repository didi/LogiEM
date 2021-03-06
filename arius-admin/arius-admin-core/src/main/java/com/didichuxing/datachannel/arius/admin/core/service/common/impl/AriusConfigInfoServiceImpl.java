package com.didichuxing.datachannel.arius.admin.core.service.common.impl;

import com.alibaba.fastjson.JSON;
import com.didichuxing.datachannel.arius.admin.client.bean.common.Result;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.config.AriusConfigInfoDTO;
import com.didichuxing.datachannel.arius.admin.client.constant.config.AriusConfigDimensionEnum;
import com.didichuxing.datachannel.arius.admin.client.constant.config.AriusConfigStatusEnum;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.config.AriusConfigInfo;
import com.didichuxing.datachannel.arius.admin.common.bean.po.config.AriusConfigInfoPO;
import com.didichuxing.datachannel.arius.admin.common.constant.arius.AriusUser;
import com.didichuxing.datachannel.arius.admin.common.util.AriusObjUtils;
import com.didichuxing.datachannel.arius.admin.common.util.ConvertUtil;
import com.didichuxing.datachannel.arius.admin.common.util.EnvUtil;
import com.didichuxing.datachannel.arius.admin.core.service.common.AriusConfigInfoService;
import com.didichuxing.datachannel.arius.admin.core.service.common.OperateRecordService;
import com.didichuxing.datachannel.arius.admin.persistence.mysql.config.AriusConfigInfoDAO;
import com.didiglobal.logi.log.ILog;
import com.didiglobal.logi.log.LogFactory;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.didichuxing.datachannel.arius.admin.client.constant.operaterecord.ModuleEnum.CONFIG;
import static com.didichuxing.datachannel.arius.admin.client.constant.operaterecord.OperationEnum.*;

/**
 *
 *
 * @author d06679
 * @date 2019/3/14
 */
@Service
public class AriusConfigInfoServiceImpl implements AriusConfigInfoService {

    private static final ILog                LOGGER      = LogFactory.getLog(AriusConfigInfoServiceImpl.class);

    private static final String NOT_EXIST = "???????????????";

    @Autowired
    private AriusConfigInfoDAO               configInfoDAO;

    @Autowired
    private OperateRecordService             operateRecordService;

    private Cache<String, AriusConfigInfoPO> configCache = CacheBuilder.newBuilder()
        .expireAfterWrite(1, TimeUnit.MINUTES).maximumSize(100).build();

    /**
     * ????????????
     * @param configInfoDTO ????????????
     * @param operator      ?????????
     * @return ?????? true
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Integer> addConfig(AriusConfigInfoDTO configInfoDTO, String operator) {
        Result<Void> checkResult = checkParam(configInfoDTO);
        if (checkResult.failed()) {
            LOGGER.warn("class=AriusConfigInfoServiceImpl||method=addConfig||msg={}||msg=check fail!",
                checkResult.getMessage());
            return Result.buildFrom(checkResult);
        }

        initConfig(configInfoDTO);

        AriusConfigInfoPO oldConfig = getByGroupAndNameFromDB(configInfoDTO.getValueGroup(),
            configInfoDTO.getValueName());
        if (oldConfig != null) {
            return Result.buildDuplicate("????????????");
        }

        AriusConfigInfoPO param = ConvertUtil.obj2Obj(configInfoDTO, AriusConfigInfoPO.class);
        boolean succ = (1 == configInfoDAO.insert(param));
        if (succ) {
            operateRecordService.save(CONFIG, ADD, param.getId(),
                String.format("??????????????????, ?????????:%s, ????????????%s", configInfoDTO.getValueGroup(), configInfoDTO.getValueName()),
                operator);
        }
        return Result.build(succ,param.getId());
    }

    /**
     * ????????????
     * @param configId ??????id
     * @param operator ?????????
     * @return ?????? true  ?????? false
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> delConfig(Integer configId, String operator) {
        AriusConfigInfoPO configInfoPO = configInfoDAO.getbyId(configId);
        if (configInfoPO == null) {
            return Result.buildNotExist(NOT_EXIST);
        }

        boolean succ = (1 == configInfoDAO.updateByIdAndStatus(configId, AriusConfigStatusEnum.DELETED.getCode()));
        if (succ) {
            operateRecordService.save(CONFIG, DELETE, configId,
                String.format("??????????????????, ?????????:%s, ????????????%s", configInfoPO.getValueGroup(), configInfoPO.getValueName()),
                operator);
        }

        return Result.build(succ);
    }

    /**
     * ???????????? ???????????????  ????????????????????????
     * @param configInfoDTO ????????????
     * @param operator      ?????????
     * @return ?????? true  ?????? false
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> editConfig(AriusConfigInfoDTO configInfoDTO, String operator) {
        if (AriusObjUtils.isNull(configInfoDTO.getId())) {
            return Result.buildParamIllegal("??????ID??????");
        }

        AriusConfigInfoPO configInfoPO = configInfoDAO.getbyId(configInfoDTO.getId());
        if (configInfoPO == null) {
            return Result.buildNotExist(NOT_EXIST);
        }

        boolean succ = (1 == configInfoDAO.update(ConvertUtil.obj2Obj(configInfoDTO, AriusConfigInfoPO.class)));

        if (succ) {
            operateRecordService.save(CONFIG, EDIT, configInfoDTO.getId(),
                    String.format("?????????????????????????????????%s???????????????%s???????????????", configInfoPO.getValueGroup(), configInfoPO.getValueName())
                            + AriusObjUtils.findChangedWithClear(configInfoPO, configInfoDTO),
                    operator);
        }

        return Result.build(succ);
    }

    /**
     * ????????????
     * @param configId ??????id
     * @param status   ??????
     * @param operator ?????????
     * @return ?????? true  ?????? false
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> switchConfig(Integer configId, Integer status, String operator) {
        AriusConfigInfoPO configInfoPO = configInfoDAO.getbyId(configId);
        if (configInfoPO == null) {
            return Result.buildNotExist(NOT_EXIST);
        }

        AriusConfigStatusEnum statusEnum = AriusConfigStatusEnum.valueOf(status);
        if (statusEnum == null) {
            return Result.buildParamIllegal("????????????");
        }

        boolean succ = (1 == configInfoDAO.updateByIdAndStatus(configId, status));
        if (succ) {
            operateRecordService.save(CONFIG, SWITCH, configId, String.format("????????????%s, ?????????:%s, ????????????%s",
                statusEnum.getDesc(), configInfoPO.getValueGroup(), configInfoPO.getValueName()), operator);
        }

        return Result.build(succ);
    }

    /**
     * ??????????????????????????????
     * @param group ?????????
     * @return ??????AriusConfigInfoPO??????  ??????????????????
     * <p>
     * ???????????????????????? ???????????????
     */
    @Override
    public List<AriusConfigInfo> getConfigByGroup(String group) {
        List<AriusConfigInfo> configInfos = Lists.newArrayList();

        List<AriusConfigInfoPO> configInfoPOs = configInfoDAO.listByGroup(group);
        if (CollectionUtils.isEmpty(configInfoPOs)) {
            return configInfos;
        }

        return ConvertUtil.list2List(configInfoPOs, AriusConfigInfo.class);
    }

    /**
     * ????????????????????????AriusConfigInfoVO??????
     * @param param ????????????
     * @return ????????????
     *
     * ???????????????,???????????????
     */
    @Override
    public List<AriusConfigInfo> queryByCondt(AriusConfigInfoDTO param) {
        List<AriusConfigInfoPO> configInfoPOs = configInfoDAO
            .listByCondition(ConvertUtil.obj2Obj(param, AriusConfigInfoPO.class));
        return ConvertUtil.list2List(configInfoPOs, AriusConfigInfo.class);
    }

    /**
     * ??????????????????
     * @param configId ??????id
     * @return ????????????  ???????????????null
     */
    @Override
    public AriusConfigInfo getConfigById(Integer configId) {
        return ConvertUtil.obj2Obj(configInfoDAO.getbyId(configId), AriusConfigInfo.class);
    }

    /**
     * ???????????????????????????
     * @param group ?????????
     * @param name  ????????????
     * @param value ????????????
     * @return ?????? true  ?????? false
     *
     * NotExistExceptio ???????????????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> updateValueByGroupAndName(String group, String name, String value) {
        if (value == null) {
            return Result.buildParamIllegal("?????????");
        }

        AriusConfigInfoPO configInfoPO = getByGroupAndNameFromDB(group, name);
        if (configInfoPO == null) {
            return Result.buildNotExist(NOT_EXIST);
        }

        AriusConfigInfoDTO param = new AriusConfigInfoDTO();
        param.setId(configInfoPO.getId());
        param.setValue(value);

        return editConfig(param, AriusUser.SYSTEM.getDesc());
    }

    /**
     * ???????????????????????????, ??????????????????????????????
     * @param group ?????????
     * @param name  ????????????
     * @param value ????????????
     * @return ?????? true  ?????? false
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result upsertValueByGroupAndName(String group, String name, String value) {
        AriusConfigInfoPO configInfoPO = getByGroupAndNameFromDB(group, name);

        if (configInfoPO != null) {
            return updateValueByGroupAndName(group, name, value);
        }

        AriusConfigInfoDTO configInfoDTO = new AriusConfigInfoDTO();
        configInfoDTO.setValueGroup(group);
        configInfoDTO.setValueName(name);
        configInfoDTO.setValue(value);

        return addConfig(configInfoDTO, AriusUser.SYSTEM.getDesc());
    }

    /**
     * ??????int????????????
     * @param group        ?????????
     * @param name         ?????????
     * @param defaultValue ?????????
     * @return ???????????????????????????, ????????????????????????????????????????????????
     */
    @Override
    public Integer intSetting(String group, String name, Integer defaultValue) {
        try {
            AriusConfigInfoPO configInfoPO = getByGroupAndName(group, name);
            if (configInfoPO == null || StringUtils.isBlank(configInfoPO.getValue())) {
                return defaultValue;
            }
            return Integer.valueOf(configInfoPO.getValue());
        } catch (NumberFormatException e) {
            if (!EnvUtil.isOnline()) {
                LOGGER.warn(
                    "class=AriusConfigInfoServiceImpl||method=intSetting||group={}||name={}||msg=get config error!",
                    group, name);
            }
        }
        return defaultValue;
    }

    /**
     * ??????long????????????
     * @param group        ?????????
     * @param name         ?????????
     * @param defaultValue ?????????
     * @return ???????????????????????????, ????????????????????????????????????????????????
     */
    @Override
    public Long longSetting(String group, String name, Long defaultValue) {
        try {
            AriusConfigInfoPO configInfoPO = getByGroupAndName(group, name);
            if (configInfoPO == null || StringUtils.isBlank(configInfoPO.getValue())) {
                return defaultValue;
            }
            return Long.valueOf(configInfoPO.getValue());
        } catch (Exception e) {
            if (!EnvUtil.isOnline()) {
                LOGGER.warn(
                    "class=AriusConfigInfoServiceImpl||method=longSetting||group={}||name={}||msg=get config error!",
                    group, name);
            }
        }
        return defaultValue;
    }

    /**
     * ??????double????????????
     * @param group        ?????????
     * @param name         ?????????
     * @param defaultValue ?????????
     * @return ???????????????????????????, ????????????????????????????????????????????????
     */
    @Override
    public Double doubleSetting(String group, String name, Double defaultValue) {
        try {
            AriusConfigInfoPO configInfoPO = getByGroupAndName(group, name);
            if (configInfoPO == null || StringUtils.isBlank(configInfoPO.getValue())) {
                return defaultValue;
            }
            return Double.valueOf(configInfoPO.getValue());
        } catch (Exception e) {
            if (!EnvUtil.isOnline()) {
                LOGGER.warn(
                    "class=AriusConfigInfoServiceImpl||method=doubleSetting||group={}||name={}||msg=get config error!",
                    group, name, e);
            }
        }
        return defaultValue;
    }

    /**
     * ??????String????????????
     * @param group        ?????????
     * @param name         ?????????
     * @param defaultValue ?????????
     * @return ???????????????????????????, ????????????????????????????????????????????????
     */
    @Override
    public String stringSetting(String group, String name, String defaultValue) {
        try {
            AriusConfigInfoPO configInfoPO = getByGroupAndName(group, name);
            if (configInfoPO == null || StringUtils.isBlank(configInfoPO.getValue())) {
                return defaultValue;
            }
            return configInfoPO.getValue();
        } catch (Exception e) {
            if (!EnvUtil.isOnline()) {
                LOGGER.warn(
                    "class=AriusConfigInfoServiceImpl||method=stringSetting||group={}||name={}||msg=get config error!",
                    group, name, e);
            }
        }
        return defaultValue;
    }

    /**
     * ??????String???????????? ???????????????
     *
     * @param group        ?????????
     * @param name         ?????????
     * @param defaultValue ?????????
     * @param split        ?????????
     * @return ?????????
     */
    @Override
    public Set<String> stringSettingSplit2Set(String group, String name, String defaultValue, String split) {
        String string = stringSetting(group, name, defaultValue);
        return Sets.newHashSet(string.split(split));
    }

    /**
     * ??????bool????????????
     * @param group        ?????????
     * @param name         ?????????
     * @param defaultValue ?????????
     * @return ???????????????????????????, ????????????????????????????????????????????????
     */
    @Override
    public Boolean booleanSetting(String group, String name, Boolean defaultValue) {
        AriusConfigInfoPO configInfoPO = getByGroupAndName(group, name);
        if (configInfoPO == null || StringUtils.isBlank(configInfoPO.getValue())) {
            return defaultValue;
        }
        return Boolean.valueOf(configInfoPO.getValue());
    }

    /**
     * ??????Object????????????
     * @param group        ?????????
     * @param name         ?????????
     * @param defaultValue ?????????
     * @param clazz        ????????????
     * @return ???????????????????????????, ????????????????????????????????????????????????
     */
    @Override
    public <T> T objectSetting(String group, String name, T defaultValue, Class<T> clazz) {
        try {
            AriusConfigInfoPO configInfoPO = getByGroupAndName(group, name);
            if (configInfoPO == null || StringUtils.isBlank(configInfoPO.getValue())) {
                return defaultValue;
            }
            return JSON.parseObject(configInfoPO.getValue(), clazz);
        } catch (Exception e) {
            if (!EnvUtil.isOnline()) {
                LOGGER.warn(
                    "class=AriusConfigInfoServiceImpl||method=objectSetting||group={}||name={}||msg=get config error!",
                    group, name, e);
            }
        }
        return defaultValue;
    }

    /******************************************* private method **************************************************/
    private Result<Void> checkParam(AriusConfigInfoDTO configInfoDTO) {
        if (AriusObjUtils.isNull(configInfoDTO)) {
            return Result.buildParamIllegal("??????????????????");
        }
        if (AriusObjUtils.isNull(configInfoDTO.getValueGroup())) {
            return Result.buildParamIllegal("?????????");
        }
        if (AriusObjUtils.isNull(configInfoDTO.getValueName())) {
            return Result.buildParamIllegal("????????????");
        }
        return Result.buildSucc();
    }

    private void initConfig(AriusConfigInfoDTO configInfoDTO) {

        if (configInfoDTO.getDimension() == null) {
            configInfoDTO.setDimension(AriusConfigDimensionEnum.UNKNOWN.getCode());
        }

        if (configInfoDTO.getStatus() == null) {
            configInfoDTO.setStatus(AriusConfigStatusEnum.NORMAL.getCode());
        }

        if (configInfoDTO.getValue() == null) {
            configInfoDTO.setValue("");
        }

        if (configInfoDTO.getMemo() == null) {
            configInfoDTO.setMemo("");
        }
    }

    private AriusConfigInfoPO getByGroupAndName(String group, String valueName) {
        try {
            return configCache.get(group + "@" + valueName, () -> getByGroupAndNameFromDB(group, valueName));
        } catch (Exception e) {
            return getByGroupAndNameFromDB(group, valueName);
        }
    }

    private AriusConfigInfoPO getByGroupAndNameFromDB(String group, String valueName) {
        return configInfoDAO.getByGroupAndName(group, valueName);
    }
}
