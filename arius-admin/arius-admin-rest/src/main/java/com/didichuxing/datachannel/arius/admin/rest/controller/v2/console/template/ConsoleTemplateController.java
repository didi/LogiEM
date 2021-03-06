package com.didichuxing.datachannel.arius.admin.rest.controller.v2.console.template;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.didichuxing.datachannel.arius.admin.biz.cluster.ClusterLogicManager;
import com.didichuxing.datachannel.arius.admin.biz.template.TemplateLogicManager;
import com.didichuxing.datachannel.arius.admin.biz.template.TemplatePhyStatisManager;
import com.didichuxing.datachannel.arius.admin.biz.template.srv.pipeline.TemplatePipelineManager;
import com.didichuxing.datachannel.arius.admin.biz.template.srv.quota.TemplateQuotaManager;
import com.didichuxing.datachannel.arius.admin.client.bean.common.IndexTemplateValue;
import com.didichuxing.datachannel.arius.admin.client.bean.common.QuotaUsage;
import com.didichuxing.datachannel.arius.admin.client.bean.common.Result;
import com.didichuxing.datachannel.arius.admin.client.bean.common.TemplateLabel;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.template.ConsoleTemplateClearDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.template.ConsoleTemplateRateLimitDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.template.ConsoleTemplateUpdateDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.template.IndexTemplateLogicDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.vo.app.ConsoleAppVO;
import com.didichuxing.datachannel.arius.admin.client.bean.vo.template.*;
import com.didichuxing.datachannel.arius.admin.client.constant.template.TemplateDeployRoleEnum;
import com.didichuxing.datachannel.arius.admin.common.Tuple;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.app.App;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.quota.ESTemplateQuotaUsage;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.quota.LogicTemplateQuotaUsage;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.template.*;
import com.didichuxing.datachannel.arius.admin.common.constant.AdminConstant;
import com.didichuxing.datachannel.arius.admin.common.exception.AdminOperateException;
import com.didichuxing.datachannel.arius.admin.common.exception.ESOperateException;
import com.didichuxing.datachannel.arius.admin.common.util.ConvertUtil;
import com.didichuxing.datachannel.arius.admin.common.util.HttpRequestUtils;
import com.didichuxing.datachannel.arius.admin.core.service.app.AppService;
import com.didichuxing.datachannel.arius.admin.core.service.es.ESIndexService;
import com.didichuxing.datachannel.arius.admin.core.service.template.physic.TemplatePhyService;
import com.didichuxing.datachannel.arius.admin.metadata.service.TemplateSattisService;
import com.didiglobal.logi.elasticsearch.client.response.indices.catindices.CatIndexResult;
import com.didiglobal.logi.log.ILog;
import com.didiglobal.logi.log.LogFactory;
import com.google.common.collect.Lists;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

import static com.didichuxing.datachannel.arius.admin.common.constant.ApiVersion.V2_CONSOLE;

/**
 * @author d06679
 * @date 2019/5/15
 */
@RestController
@RequestMapping(V2_CONSOLE + "/template")
@Api(tags = "Console-???????????????????????????(REST)")
public class ConsoleTemplateController extends BaseConsoleTemplateController {

    private static final ILog        LOGGER = LogFactory.getLog(ConsoleTemplateController.class);

    private static final String INDEX_NOT_EXISTS_TIPS = "???????????????";
    private static final String APP_IS_NOT_EXIST = "???????????????";

    @Autowired
    private AppService               appService;

    @Autowired
    private TemplateQuotaManager     templateQuotaManager;

    @Autowired
    private TemplatePhyService       templatePhyService;

    @Autowired
    private TemplateSattisService    templateSattisService;

    @Autowired
    private ESIndexService           esIndexService;

    @Autowired
    private TemplateLogicManager     templateLogicManager;

    @Autowired
    private ClusterLogicManager      clusterLogicManager;

    @Autowired
    private TemplatePhyStatisManager templatePhyStatisManager;

    @Autowired
    private TemplatePipelineManager templatePipelineManager;


    @GetMapping("/listSample")
    @ResponseBody
    @ApiOperation(value = "????????????????????????", notes = "")
    public Result<List<ConsoleTemplateSampleVO>> getTemplateSamples(@RequestParam(value = "dataCenter", required = false, defaultValue = "") String dataCenter) {
        List<ConsoleTemplateSampleVO> templateSamples = new ArrayList<>();

        List<ConsoleTemplateVO> consoleTemplates = templateLogicManager
            .fetchConsoleTemplates(templateLogicManager.getAllTemplatesAggregate(AdminConstant.DEFAULT_APP_ID));

        if (CollectionUtils.isNotEmpty(consoleTemplates)) {
            Map<Integer, App> apps = ConvertUtil.list2Map(appService.listApps(), App::getId);
            for (ConsoleTemplateVO consoleTemplate : consoleTemplates) {
                templateSamples.add(getConsoleSample(consoleTemplate, apps));
            }
        }

        return Result.buildSucc(templateSamples);
    }

    @GetMapping("/list")
    @ResponseBody
    @ApiOperation(value = "??????????????????", notes = "?????????????????????????????????????????????")
    @ApiImplicitParams({ @ApiImplicitParam(paramType = "query", dataType = "Integer", name = "appId", value = "??????ID?????????????????????????????????????????????", required = true) })
    public Result<List<ConsoleTemplateVO>> getConsoleTemplates(@RequestParam(value = "appId", required = false) Integer appId,
                                                               @RequestParam(value = "dataCenter", required = false, defaultValue = "") String dataCenter) {
        App app = appService.getAppById(appId);
        if (null == app) {
            return Result.buildNotExist(APP_IS_NOT_EXIST);
        }
        return Result.buildSucc(templateLogicManager.getConsoleTemplatesVOS(appId));
    }

    @GetMapping("/get")
    @ResponseBody
    @ApiOperation(value = "??????????????????????????????", notes = "")
    @ApiImplicitParams({ @ApiImplicitParam(paramType = "query", dataType = "Integer", name = "logicId", value = "??????ID", required = true) })
    public Result<ConsoleTemplateDetailVO> getConsoleTemplateDetail(HttpServletRequest request,
                                                                    @RequestParam("logicId") Integer logicId) {
        IndexTemplateLogicWithCluster indexTemplateLogicWithCluster = templateLogicService
            .getLogicTemplateWithCluster(logicId);

        if (null == indexTemplateLogicWithCluster) {
            return Result.buildFail("???????????????????????????!");
        }

        ConsoleTemplateDetailVO consoleTemplateDetail = ConvertUtil.obj2Obj(indexTemplateLogicWithCluster,
            ConsoleTemplateDetailVO.class);

        consoleTemplateDetail.setCyclicalRoll(indexTemplateLogicWithCluster.getExpression().endsWith("*"));
        consoleTemplateDetail
            .setCluster(templateLogicManager.jointCluster(indexTemplateLogicWithCluster.getLogicClusters()));

        // ???????????????????????????????????????????????????????????????
        if (indexTemplateLogicWithCluster.getLogicClusters().size() == 1) {
            consoleTemplateDetail.setClusterType(indexTemplateLogicWithCluster.getLogicClusters().get(0).getType());
            consoleTemplateDetail.setClusterLevel(indexTemplateLogicWithCluster.getLogicClusters().get(0).getLevel());
        }
        consoleTemplateDetail.setAppName(getAppName(indexTemplateLogicWithCluster.getAppId()));
        consoleTemplateDetail.setIndices(getLogicTemplateIndices(logicId));

        Result<Void> checkAuthResult = checkAppAuth(logicId, HttpRequestUtils.getAppId(request));
        consoleTemplateDetail.setEditable(checkAuthResult.success());
        // ??????indexRollover??????????????????
        consoleTemplateDetail.setDisableIndexRollover(templateLogicService.getTemplateConfig(logicId).getDisableIndexRollover());

        return Result.buildSucc(consoleTemplateDetail);
    }

    @PutMapping("/update")
    @ResponseBody
    @ApiOperation(value = "????????????????????????", notes = "?????????????????????????????????????????????")
    public Result<Void> modifyConsoleTemplate(HttpServletRequest request,
                                        @RequestBody ConsoleTemplateUpdateDTO templateLogicDTO) throws AdminOperateException {
        return templateLogicManager.editTemplate(ConvertUtil.obj2Obj(templateLogicDTO, IndexTemplateLogicDTO.class),
            HttpRequestUtils.getOperator(request));
    }

    @GetMapping("/capacity")
    @ResponseBody
    @ApiOperation(value = "??????????????????????????????", notes = "")
    @ApiImplicitParams({ @ApiImplicitParam(paramType = "query", dataType = "Integer", name = "logicId", value = "??????ID", required = true) })
    public Result<ConsoleTemplateCapacityVO> getLogicTemplateCapacity(@RequestParam("logicId") Integer logicId) {
        IndexTemplateLogic templateLogic = templateLogicService.getLogicTemplateById(logicId);
        if (templateLogic == null) {
            return Result.buildParamIllegal(INDEX_NOT_EXISTS_TIPS);
        }

        ConsoleTemplateCapacityVO templateCapacityVO = ConvertUtil.obj2Obj(templateLogic,
            ConsoleTemplateCapacityVO.class);
        templateCapacityVO.setCyclicalRoll(templateLogic.getExpression().endsWith("*"));
        templateCapacityVO.setTopUsage(getQuotaUsage(templateLogic.getId()));
        templateCapacityVO.setCurrentUsage(fetchLogicTemplateCurrentUsage(logicId));
        return Result.buildSucc(templateCapacityVO);
    }

    @GetMapping("/clearInfo")
    @ResponseBody
    @ApiOperation(value = "??????????????????????????????", notes = "")
    @ApiImplicitParams({ @ApiImplicitParam(paramType = "query", dataType = "Integer", name = "logicId", value = "??????ID", required = true) })
    public Result<ConsoleTemplateClearVO> getLogicTemplateClearInfo(@RequestParam("logicId") Integer logicId) {
        IndexTemplateLogicWithPhyTemplates templateLogicWithPhysical = templateLogicService
            .getLogicTemplateWithPhysicalsById(logicId);

        if (templateLogicWithPhysical == null) {
            return Result.buildParamIllegal(INDEX_NOT_EXISTS_TIPS);
        }

        if (!templateLogicWithPhysical.hasPhysicals()) {
            return Result.buildParamIllegal("??????????????????");
        }

        ConsoleTemplateClearVO consoleTemplateClearVO = new ConsoleTemplateClearVO();
        consoleTemplateClearVO.setLogicId(templateLogicWithPhysical.getId());
        consoleTemplateClearVO.setName(templateLogicWithPhysical.getName());
        consoleTemplateClearVO
            .setIndices(templatePhyService.getMatchNoVersionIndexNames(templateLogicWithPhysical.getAnyOne().getId()));
        consoleTemplateClearVO.setAccessApps(
            ConvertUtil.list2List(templateLogicManager.getLogicTemplateAppAccess(logicId), ConsoleAppVO.class));

        return Result.buildSucc(consoleTemplateClearVO);
    }

    @PutMapping("/clearInfo")
    @ResponseBody
    @ApiOperation(value = "????????????????????????", notes = "")
    @ApiImplicitParams({ @ApiImplicitParam(paramType = "header", dataType = "String", name = "X-ARIUS-APP-ID", value = "??????ID", required = true) })
    public Result<Void> clearLogicTemplateIndices(HttpServletRequest request,
                                            @RequestBody ConsoleTemplateClearDTO clearDTO) throws ESOperateException {
        Result<Void> checkAuthResult = checkAppAuth(clearDTO.getLogicId(), HttpRequestUtils.getAppId(request));
        if (checkAuthResult.failed()) {
            return checkAuthResult;
        }

        return clusterLogicManager.clearIndices(clearDTO, HttpRequestUtils.getOperator(request));
    }

    @GetMapping("/deleteInfo")
    @ResponseBody
    @ApiOperation(value = "????????????????????????????????????", notes = "")
    @ApiImplicitParams({ @ApiImplicitParam(paramType = "query", dataType = "Integer", name = "logicId", value = "??????ID", required = true) })
    public Result<ConsoleTemplateDeleteVO> getLogicTemplateDeleteInfo(@RequestParam("logicId") Integer logicId) {
        //??????????????????????????????????????????
        IndexTemplateLogicWithPhyTemplates templateLogicWithPhysical = templateLogicService
            .getLogicTemplateWithPhysicalsById(logicId);
        if (templateLogicWithPhysical == null) {
            return Result.buildParamIllegal(INDEX_NOT_EXISTS_TIPS);
        }

        ConsoleTemplateDeleteVO consoleTemplateDeleteVO = new ConsoleTemplateDeleteVO();
        consoleTemplateDeleteVO.setLogicId(templateLogicWithPhysical.getId());
        consoleTemplateDeleteVO.setName(templateLogicWithPhysical.getName());

        if (!templateLogicWithPhysical.hasPhysicals()) {
            return Result.buildParamIllegal("??????????????????");
        }

        consoleTemplateDeleteVO.setAccessApps(
            ConvertUtil.list2List(templateLogicManager.getLogicTemplateAppAccess(logicId), ConsoleAppVO.class));

        return Result.buildSucc(consoleTemplateDeleteVO);
    }

    @DeleteMapping("/deleteInfo")
    @ResponseBody
    @ApiOperation(value = "????????????????????????", notes = "")
    @ApiImplicitParams({ @ApiImplicitParam(paramType = "header", dataType = "String", name = "X-ARIUS-APP-ID", value = "??????ID", required = true),
                         @ApiImplicitParam(paramType = "query", dataType = "Integer", name = "logicId", value = "??????ID", required = true) })
    public Result<Void> deleteTemplate(HttpServletRequest request,
                                 @RequestParam("logicId") Integer logicId) throws AdminOperateException {
        Result<Void> checkAuthResult = checkAppAuth(logicId, HttpRequestUtils.getAppId(request));
        if (checkAuthResult.failed()) {
            return checkAuthResult;
        }
        return templateLogicManager.delTemplate(logicId, HttpRequestUtils.getOperator(request));
    }

    @GetMapping("/indices/list")
    @ResponseBody
    @ApiOperation(value = "??????App Id?????????????????????", notes = "")
    @ApiImplicitParams({ @ApiImplicitParam(paramType = "query", dataType = "Integer", name = "appId", value = "??????ID", required = true) })
    public Result<List<Tuple<String, String>>> getLogicTemplatesByAppId(HttpServletRequest request,
                                                                        @RequestParam("appId") Integer appId) {

        App app = appService.getAppById(appId);
        if (null == app) {
            return Result.buildNotExist("???????????????");
        }

        return templateLogicService.getLogicTemplatesByAppId(appId);
    }

    @GetMapping("/label")
    @ResponseBody
    @ApiOperation(value = "??????????????????", notes = "")
    @ApiImplicitParams({ @ApiImplicitParam(paramType = "query", dataType = "Integer", name = "logicId", value = "????????????ID", required = true) })
    public Result<TemplateLabel> getTemplateLabel(HttpServletRequest request,
                                                  @RequestParam("logicId") Integer logicId) {
        IndexTemplateLogicWithLabels logicWithLabel = templateLogicManager.getLabelByLogicId(logicId);
        return Result.buildSucc(ConvertUtil.obj2Obj(logicWithLabel, TemplateLabel.class));
    }

    @PutMapping("/transfer")
    @ResponseBody
    @ApiOperation(value = "??????????????????????????????appid", notes = "")
    @ApiImplicitParams({ @ApiImplicitParam(paramType = "query", dataType = "Integer", name = "logicId", value = "??????ID", required = true),
                         @ApiImplicitParam(paramType = "query", dataType = "Integer", name = "tgtAppId", value = "????????????", required = true) })
    public Result<Void> turnOverLogicTemplate(HttpServletRequest request, @RequestParam("logicId") Integer logicId,
                                        @RequestParam("tgtAppId") Integer tgtAppId) {
        IndexTemplateLogic templateLogic = templateLogicService.getLogicTemplateById(logicId);
        if (null == templateLogic) {
            return Result.buildNotExist("???????????????");
        }

        try {
            return templateLogicService.turnOverLogicTemplate(logicId, tgtAppId, templateLogic.getResponsible(),
                HttpRequestUtils.getOperator(request));
        } catch (AdminOperateException e) {
            return Result.buildNotExist("transfer exception!");
        }
    }

    @GetMapping("/cyclicalRoll")
    @ResponseBody
    @ApiOperation(value = "????????????????????????", notes = "")
    @ApiImplicitParams({ @ApiImplicitParam(paramType = "query", dataType = "Integer", name = "logicId", value = "??????ID", required = true) })
    public Result<List<TemplateCyclicalRollInfoVO>> getCyclicalRollInfo(HttpServletRequest request,
                                                                        @RequestParam("logicId") Integer logicId) {
        IndexTemplateLogicWithPhyTemplates templateLogicWithPhysical = templateLogicService
            .getLogicTemplateWithPhysicalsById(logicId);
        if (templateLogicWithPhysical == null) {
            return Result.buildParamIllegal(INDEX_NOT_EXISTS_TIPS);
        }

        List<CatIndexResult> catIndexResults = Lists.newArrayList();

        List<IndexTemplatePhy> physicalMasters = templateLogicWithPhysical.fetchMasterPhysicalTemplates();
        for (IndexTemplatePhy physicalMaster : physicalMasters) {
            try {
                catIndexResults.addAll(esIndexService.syncCatIndexByExpression(physicalMaster.getCluster(),
                    physicalMaster.getExpression()));
            } catch (Exception e) {
                LOGGER.warn("class=ConsoleTemplateController||method=TemplateCyclicalRollInfoVO||logicId={}||errMsg={}", logicId, e.getMessage(), e);
            }
        }

        return Result.buildSucc(ConvertUtil.list2List(catIndexResults, TemplateCyclicalRollInfoVO.class));
    }

    /**
     * ?????????????????????????????????????????????????????????
     *
     * @param logicTemplateId ??????id
     * @param startDate       ????????????????????????????????????
     * @param endDate         ??????
     * @return list
     */
    @GetMapping(path = "/health/v2/degree/record/list.do")
    @ApiOperation(value = "?????????????????????????????????????????????????????????", notes = "")
    @ApiImplicitParams({ @ApiImplicitParam(paramType = "query", dataType = "Long", name = "logicTemplateId", value = "??????id", required = true),
                         @ApiImplicitParam(paramType = "query", dataType = "Long", name = "startDate", value = "????????????????????????????????????", required = true),
                         @ApiImplicitParam(paramType = "query", dataType = "Long", name = "endDate", value = "????????????????????????????????????", required = true) })
    public Result<List<TemplateHealthDegreeRecordVO>> getHealthDegreeRecordByLogicTemplateId(@RequestParam(value = "logicTemplateId") Long logicTemplateId,
                                                                                             @RequestParam(value = "startDate") Long startDate,
                                                                                             @RequestParam(value = "endDate") Long endDate) {
        return templatePhyStatisManager.getHealthDegreeRecordByLogicTemplateId(logicTemplateId, startDate, endDate);
    }

    /**
     * ????????????????????????????????????????????????
     *
     * @param logicTemplateId ??????id
     * @param startDate       ????????????????????????????????????
     * @param endDate         ????????????????????????????????????
     * @return list
     */
    @GetMapping(path = "/value/record/list.do")
    @ApiOperation(value = "??????????????????????????????????????????????????????", notes = "")
    @ApiImplicitParams({ @ApiImplicitParam(paramType = "query", dataType = "Long", name = "logicTemplateId", value = "????????????id", required = true),
                         @ApiImplicitParam(paramType = "query", dataType = "Long", name = "startDate", value = "????????????????????????????????????", required = true),
                         @ApiImplicitParam(paramType = "query", dataType = "Long", name = "endDate", value = "????????????????????????????????????", required = true) })
    public Result<List<TemplateValueRecordVO>> getValueRecordByLogicTemplateId(@RequestParam(value = "logicTemplateId") Long logicTemplateId,
                                                                               @RequestParam(value = "startDate") Long startDate,
                                                                               @RequestParam(value = "endDate") Long endDate) {
        return templatePhyStatisManager.getValueRecordByLogicTemplateId(logicTemplateId, startDate, endDate);
    }

    /**
     * ???????????????????????????
     */
    @GetMapping(path = "/rateLimit")
    @ResponseBody
    @ApiOperation(value = "?????????????????????????????????", notes = "")
    @ApiImplicitParams({@ApiImplicitParam(paramType = "query", dataType = "Integer", name = "logicId", value = "??????ID", required = true)})
    public Result<ConsoleTemplateRateLimitVO> getTemplateRateLimit(@RequestParam("logicId") Integer logicId) throws Exception {    // ???????????????????????????master slave???????????????????????????????????????master??????
        List<IndexTemplatePhy> indexTemplatePhysical = templatePhyService.getTemplateByLogicId(logicId);
        ConsoleTemplateRateLimitVO consoleTemplateRateLimitVO = new ConsoleTemplateRateLimitVO();
        IndexTemplatePhy indexTemplatePhysicalMaster = new IndexTemplatePhy();
        for (IndexTemplatePhy item : indexTemplatePhysical) {
            if (item.getRole() == TemplateDeployRoleEnum.MASTER.getCode()) {
                indexTemplatePhysicalMaster = item;
            }
        }
        consoleTemplateRateLimitVO.setRateLimit(templatePipelineManager.getRateLimit(indexTemplatePhysicalMaster));
        return Result.buildSucc(consoleTemplateRateLimitVO);
    }

    /**
     * ???????????????????????????????????????????????????????????????????????????????????????[-99,1000]
     *  @param consoleTemplateRateLimitDTO ????????????????????????????????????
     *  @return
     *  @throws AdminOperateException
     */
    @PutMapping(path = "/rateLimit")
    @ResponseBody
    @ApiOperation(value = "?????????????????????????????????", notes = "")
    public Result updateTemplateRateLimit(@RequestBody ConsoleTemplateRateLimitDTO consoleTemplateRateLimitDTO) {
        // ????????????????????????????????????
        int percent = (int) Math.ceil(100.0 * (consoleTemplateRateLimitDTO.getAdjustRateLimit() - consoleTemplateRateLimitDTO.getCurRateLimit()) / consoleTemplateRateLimitDTO.getCurRateLimit());
        if (percent < -99 || percent > 10000) {
            return Result.buildFail("???????????????????????????????????????????????????100?????????");
        }
        try {
            return templateLogicService.updateTemplateWriteRateLimit(consoleTemplateRateLimitDTO);
        } catch (ESOperateException e) {
            LOGGER.info("??????????????????", e);
            return Result.buildFail("?????????????????????");
        }
    }

    /********************************************private************************************************/
    /**
     * ????????????
     *
     * @param consoleTemplate ????????????
     * @param apps            Apps
     * @return
     */
    private ConsoleTemplateSampleVO getConsoleSample(ConsoleTemplateVO consoleTemplate, Map<Integer, App> apps) {
        ConsoleTemplateSampleVO templateSample = ConvertUtil.obj2Obj(consoleTemplate, ConsoleTemplateSampleVO.class);

        App currentTemplateApp = apps.get(consoleTemplate.getAppId());
        if (currentTemplateApp != null) {
            templateSample.setAppName(currentTemplateApp.getName());
        }

        return templateSample;
    }

    /**
     * ???????????????????????????
     *
     * @param logicId ????????????ID
     * @return
     */
    private QuotaUsage getQuotaUsage(Integer logicId) {
        LogicTemplateQuotaUsage templateQuotaUsage = templateQuotaManager.getLogicTemplateQuotaUsage(logicId,
            7 * AdminConstant.MILLIS_PER_DAY);
        QuotaUsage topUsage = ConvertUtil.obj2Obj(templateQuotaUsage, QuotaUsage.class);
        topUsage.setQuotaDiskUsage(templateQuotaUsage.getActualDiskG() / templateQuotaUsage.getQuotaDiskG());
        topUsage.setQuotaCpuUsage(templateQuotaUsage.getActualCpuCount() / templateQuotaUsage.getQuotaCpuCount());

        return topUsage;
    }

    /**
     * ???????????????????????????
     *
     * @param logicId ????????????ID
     * @return
     */
    private Integer fetchLogicTemplateValue(Integer logicId) {
        Result<IndexTemplateValue> getTemplateValueResult = templateSattisService.getTemplateValue(logicId);
        if (getTemplateValueResult.success() && getTemplateValueResult.getData() != null) {
            return getTemplateValueResult.getData().getValue();
        }

        return TemplateLogicManager.DEFAULT_TEMPLATE_VALUE;
    }

    /**
     * ??????????????????????????????
     *
     * @param logicId ??????ID
     * @return
     */
    private List<String> getLogicTemplateIndices(Integer logicId) {
        IndexTemplateLogicWithPhyTemplates templateLogicWithPhysical = templateLogicService
            .getLogicTemplateWithPhysicalsById(logicId);

        if (null != templateLogicWithPhysical && null != templateLogicWithPhysical.getAnyOne()) {
            return templatePhyService.getMatchNoVersionIndexNames(templateLogicWithPhysical.getAnyOne().getId());
        }

        return new ArrayList<>();
    }

    /**
     * ??????App??????
     *
     * @param appId App Id
     * @return
     */
    private String getAppName(Integer appId) {
        App app = appService.getAppById(appId);
        if (app != null) {
            return app.getName();
        }

        return StringUtils.EMPTY;
    }

    /**
     * ?????????????????????????????????
     *
     * @param logicId ????????????ID
     * @return
     */
    private QuotaUsage fetchLogicTemplateCurrentUsage(Integer logicId) {
        ESTemplateQuotaUsage esTemplateQuotaUsage = templateQuotaManager.getByLogicId(logicId);

        QuotaUsage currentUsage = ConvertUtil.obj2Obj(esTemplateQuotaUsage, QuotaUsage.class);
        if (esTemplateQuotaUsage != null) {
            currentUsage
                .setQuotaDiskUsage(esTemplateQuotaUsage.getActualDiskG() / esTemplateQuotaUsage.getQuotaDiskG());
            currentUsage
                .setQuotaCpuUsage(esTemplateQuotaUsage.getActualCpuCount() / esTemplateQuotaUsage.getQuotaCpuCount());
        }

        return currentUsage;
    }
}
