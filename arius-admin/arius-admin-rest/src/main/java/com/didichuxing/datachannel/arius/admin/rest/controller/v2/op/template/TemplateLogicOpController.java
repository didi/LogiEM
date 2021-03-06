package com.didichuxing.datachannel.arius.admin.rest.controller.v2.op.template;

import java.util.List;

import com.didichuxing.datachannel.arius.admin.biz.template.TemplateAction;
import com.didichuxing.datachannel.arius.admin.biz.template.TemplateLogicManager;
import com.didichuxing.datachannel.arius.admin.biz.template.TemplatePhyManager;
import com.didichuxing.datachannel.arius.admin.biz.template.srv.limit.TemplateLimitManager;
import com.didichuxing.datachannel.arius.admin.biz.template.srv.pipeline.TemplatePipelineManager;
import com.didichuxing.datachannel.arius.admin.client.bean.common.Result;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.template.IndexTemplateConfigDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.template.IndexTemplateLogicDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.vo.app.AppTemplateAuthVO;
import com.didichuxing.datachannel.arius.admin.client.bean.vo.template.IndexTemplateConfigVO;
import com.didichuxing.datachannel.arius.admin.client.bean.vo.template.IndexTemplateLogicAllVO;
import com.didichuxing.datachannel.arius.admin.client.bean.vo.template.IndexTemplatePhysicalVO;
import com.didichuxing.datachannel.arius.admin.client.bean.vo.template.OpLogicTemplateVO;
import com.didichuxing.datachannel.arius.admin.client.constant.template.DataTypeEnum;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.template.IndexTemplateLogic;
import com.didichuxing.datachannel.arius.admin.common.exception.AdminOperateException;
import com.didichuxing.datachannel.arius.admin.common.exception.ESOperateException;
import com.didichuxing.datachannel.arius.admin.common.util.ConvertUtil;
import com.didichuxing.datachannel.arius.admin.common.util.HttpRequestUtils;
import com.didichuxing.datachannel.arius.admin.core.service.app.AppLogicTemplateAuthService;
import com.didichuxing.datachannel.arius.admin.core.service.app.AppService;
import com.didichuxing.datachannel.arius.admin.core.service.template.logic.TemplateLogicService;
import com.didichuxing.datachannel.arius.admin.core.service.template.physic.TemplatePhyService;
import com.didichuxing.datachannel.arius.admin.metadata.service.TemplateLabelService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

import static com.didichuxing.datachannel.arius.admin.common.constant.ApiVersion.V2_OP;

@RestController
@RequestMapping(V2_OP + "/template/logic")
@Api(tags = "es????????????????????????(REST)")
public class TemplateLogicOpController {

    @Autowired
    private TemplateLogicService        templateLogicService;

    @Autowired
    private TemplatePhyService          templatePhyService;

    @Autowired
    private TemplatePipelineManager     templatePipelineManager;

    @Autowired
    private TemplateLimitManager        templateLimitManager;

    @Autowired
    private AppLogicTemplateAuthService appLogicTemplateAuthService;

    @Autowired
    private TemplateAction              templateAction;

    @Autowired
    private TemplateLabelService        templateLabelService;

    @Autowired
    private AppService                  appService;

    @Autowired
    private TemplatePhyManager          templatePhyManager;

    @Autowired
    private TemplateLogicManager        templateLogicManager;

    @PostMapping("/list")
    @ResponseBody
    @ApiOperation(value = "??????????????????????????????", notes = "")
    public Result<List<OpLogicTemplateVO>> list(@RequestBody IndexTemplateLogicDTO param) {
        return getLogicTemplateList(param);
    }

    @GetMapping("")
    @ResponseBody
    @ApiOperation(value = "??????????????????????????????", notes = "")
    public Result<List<OpLogicTemplateVO>> getLogicTemplates(@RequestParam IndexTemplateLogicDTO param) {
        return getLogicTemplateList(param);
    }

    @GetMapping("/listByHasAuthCluster")
    @ResponseBody
    @ApiOperation(value = "??????APP???????????????????????????????????????????????????????????????", notes = "")
    @ApiImplicitParams({ @ApiImplicitParam(paramType = "query", dataType = "Integer", name = "appId", value = "??????ID", required = true) })
    public Result<List<OpLogicTemplateVO>> listByHasAuthCluster(@RequestParam("appId") Integer appId) {

        List<IndexTemplateLogic> hasClusterAuthTemplates = templateLogicService.getTemplatesByHasAuthCluster(appId);
        return Result.buildSucc(ConvertUtil.list2List(hasClusterAuthTemplates, OpLogicTemplateVO.class));
    }

    @GetMapping("/listHasAuthInLogicCluster")
    @ResponseBody
    @ApiOperation(value = "??????APP???????????????????????????????????????????????????????????????", notes = "")
    @ApiImplicitParams({ @ApiImplicitParam(paramType = "query", dataType = "Integer", name = "appId", value = "??????ID", required = true),
                         @ApiImplicitParam(paramType = "query", dataType = "Long", name = "logicClusterId", value = "????????????ID", required = true) })
    public Result<List<OpLogicTemplateVO>> listHasAuthInLogicCluster(@RequestParam("appId") Integer appId,
                                                                     @RequestParam("logicClusterId") Long logicClusterId) {
        List<IndexTemplateLogic> hasAuthTemplatesInCluster = templateLogicService
            .getHasAuthTemplatesInLogicCluster(appId, logicClusterId);
        return Result.buildSucc(ConvertUtil.list2List(hasAuthTemplatesInCluster, OpLogicTemplateVO.class));
    }

    @PostMapping("/query")
    @ResponseBody
    @ApiOperation(value = "??????????????????????????????", notes = "??????????????????")
    public Result<List<OpLogicTemplateVO>> query(@RequestBody IndexTemplateLogicDTO param) {
        return Result.buildSucc(ConvertUtil.list2List(templateLogicService.fuzzyLogicTemplatesByCondition(param),
            OpLogicTemplateVO.class));
    }

    @GetMapping("/listByLabelIds")
    @ResponseBody
    @ApiOperation(value = "????????????????????????????????????")
    @ApiImplicitParams({ @ApiImplicitParam(paramType = "query", dataType = "String", name = "includeLabelIds", value = "???????????????id"),
                         @ApiImplicitParam(paramType = "query", dataType = "String", name = "excludeLabelIds", value = "???????????????id") })
    public Result<List<OpLogicTemplateVO>> listByLabelExpression(@RequestParam(value = "includeLabelIds", required = false) String includeLabelIds,
                                                                 @RequestParam(value = "excludeLabelIds", required = false) String excludeLabelIds) {
        return Result.buildSucc(ConvertUtil
            .list2List(templateLogicManager.getByLabelIds(includeLabelIds, excludeLabelIds), OpLogicTemplateVO.class));
    }

    @GetMapping("/get")
    @ResponseBody
    @ApiOperation(value = "??????????????????????????????", notes = "")
    @ApiImplicitParams({ @ApiImplicitParam(paramType = "query", dataType = "Integer", name = "logicId", value = "????????????ID", required = true) })
    public Result<OpLogicTemplateVO> getById(@RequestParam("logicId") Integer logicId) {
        return Result.buildSucc(
            ConvertUtil.obj2Obj(templateLogicService.getLogicTemplateById(logicId), OpLogicTemplateVO.class));
    }

    @GetMapping("/getTemplateByName")
    @ResponseBody
    @ApiOperation(value = "??????????????????????????????????????????", notes = "")
    @ApiImplicitParams({ @ApiImplicitParam(paramType = "query", dataType = "String", name = "templateName", value = "????????????", required = true) })
    public Result<List<OpLogicTemplateVO>> getByName(@RequestParam("templateName") String templateName) {
        return Result.buildSucc(
            ConvertUtil.list2List(templateLogicService.getLogicTemplateByName(templateName), OpLogicTemplateVO.class));
    }

    @GetMapping("/getAll")
    @ResponseBody
    @ApiOperation(value = "??????????????????????????????????????????", notes = "")
    @ApiImplicitParams({ @ApiImplicitParam(paramType = "query", dataType = "Integer", name = "logicId", value = "????????????ID", required = true) })
    public Result<IndexTemplateLogicAllVO> getAll(@RequestParam("logicId") Integer logicId) {
        IndexTemplateLogic templateLogic = templateLogicService.getLogicTemplateById(logicId);
        if (templateLogic == null) {
            return Result.buildNotExist("???????????????");
        }

        IndexTemplateLogicAllVO logicAllVO = ConvertUtil.obj2Obj(templateLogic, IndexTemplateLogicAllVO.class);

        logicAllVO.setDataTypeStr(DataTypeEnum.valueOf(templateLogic.getDataType()).getDesc());

        // ????????????????????????
        logicAllVO.setPhysicalVOS(
            ConvertUtil.list2List(templatePhyService.getTemplateByLogicId(logicId), IndexTemplatePhysicalVO.class));

        // ????????????APP??????
        logicAllVO.setTemplateAuthVOS(ConvertUtil.list2List(
            appLogicTemplateAuthService.getTemplateAuthsByLogicTemplateId(logicId), AppTemplateAuthVO.class));

        // ????????????????????????
        logicAllVO.setLabels(templateLabelService.listTemplateLabel(logicId));

        // ??????????????????
        logicAllVO.setAppName(appService.getAppName(logicAllVO.getAppId()));

        return Result.buildSucc(logicAllVO);
    }

    @DeleteMapping("/del")
    @ResponseBody
    @ApiOperation(value = "????????????????????????", notes = "")
    @ApiImplicitParams({ @ApiImplicitParam(paramType = "query", dataType = "Integer", name = "logicId", value = "????????????ID", required = true) })
    public Result<Void> delete(HttpServletRequest request,
                         @RequestParam(value = "logicId") Integer logicId) throws AdminOperateException {
        return templateLogicManager.delTemplate(logicId, HttpRequestUtils.getOperator(request));
    }

    @PutMapping("/add")
    @ResponseBody
    @ApiOperation(value = "????????????????????????", notes = "")
    public Result<Integer> add(HttpServletRequest request,
                               @RequestBody IndexTemplateLogicDTO param) throws AdminOperateException {
        return templateAction.createWithAutoDistributeResource(param, HttpRequestUtils.getOperator(request));
    }

    @PostMapping("/edit")
    @ResponseBody
    @ApiOperation(value = "????????????????????????", notes = "")
    public Result<Void> edit(HttpServletRequest request,
                       @RequestBody IndexTemplateLogicDTO param) throws AdminOperateException {
        // ????????????????????????
        param.setExpression(null);
        return templateLogicManager.editTemplate(param, HttpRequestUtils.getOperator(request));
    }

    @PostMapping("/switchMasterSlave")
    @ResponseBody
    @ApiOperation(value = "??????????????????", notes = "")
    @ApiImplicitParams({ @ApiImplicitParam(paramType = "query", dataType = "Integer", name = "logicId", value = "????????????ID", required = true) })
    public Result<Void> switchMasterSlave(HttpServletRequest request, @RequestParam(value = "logicId") Integer logicId,
                                    @RequestParam(value = "expectMasterPhysicalId") Long expectMasterPhysicalId) {
        return templatePhyManager.switchMasterSlave(logicId, expectMasterPhysicalId,
            HttpRequestUtils.getOperator(request));
    }

    @GetMapping("/config/get")
    @ResponseBody
    @ApiOperation(value = "??????????????????????????????", notes = "")
    @ApiImplicitParams({ @ApiImplicitParam(paramType = "query", dataType = "Integer", name = "logicId", value = "????????????ID", required = true) })
    public Result<IndexTemplateConfigVO> getConfig(@RequestParam("logicId") Integer logicId) {
        return Result.buildSucc(
            ConvertUtil.obj2Obj(templateLogicService.getTemplateConfig(logicId), IndexTemplateConfigVO.class));
    }

    @PutMapping("/config/update")
    @ResponseBody
    @ApiOperation(value = "??????????????????????????????", notes = "")
    public Result<Void> updateConfig(HttpServletRequest request, @RequestBody IndexTemplateConfigDTO configDTO) {
        return templateLogicService.updateTemplateConfig(configDTO, HttpRequestUtils.getOperator(request));
    }

    @PutMapping("/checkMeta")
    @ResponseBody
    @ApiOperation(value = "?????????????????????", notes = "")
    public Result<Void> checkMeta() {
        return Result.build(templateLogicManager.checkAllLogicTemplatesMeta());
    }

    @PutMapping("/repair/pipeline")
    @ResponseBody
    @ApiOperation(value = "????????????pipeline", notes = "")
    @ApiImplicitParams({ @ApiImplicitParam(paramType = "query", dataType = "Integer", name = "logicId", value = "????????????ID", required = true) })
    public Result<Void> repairPipeline(HttpServletRequest request,
                                 @RequestParam(value = "logicId") Integer logicId) throws ESOperateException {
        return templatePipelineManager.repairPipeline(logicId);
    }

    @PostMapping("/editName")
    @ResponseBody
    @ApiOperation(value = "??????????????????????????????", notes = "")
    public Result<Void> editName(HttpServletRequest request,
                           @RequestBody IndexTemplateLogicDTO param) throws AdminOperateException {
        return templateLogicService.editTemplateName(param, HttpRequestUtils.getOperator(request));
    }

    @PostMapping("/adjustPipelineRateLimit")
    @ResponseBody
    @ApiOperation(value = "??????Pipeline?????????", notes = "")
    @ApiImplicitParams({ @ApiImplicitParam(paramType = "query", dataType = "Integer", name = "logicId", value = "????????????ID", required = true) })
    public Result<Void> adjustPipelineRateLimit(@RequestParam(value = "logicId") Integer logicId) {
        return Result.build(templateLimitManager.adjustPipelineRateLimit(logicId));
    }

    @RequestMapping(path = "/blockRead", method = RequestMethod.PUT)
    @ResponseBody
    @ApiOperation(value = "????????????", notes = "")
    public Result updateBlockReadState(HttpServletRequest request, @RequestBody IndexTemplateLogicDTO param) {
        return templateLogicService.updateBlockReadState(param.getId(), param.getBlockRead(), HttpRequestUtils.getOperator(request));
    }

    @RequestMapping(path = "/blockWrite", method = RequestMethod.PUT)
    @ResponseBody
    @ApiOperation(value = "????????????", notes = "")
    public Result updateBlockWriteState(HttpServletRequest request, @RequestBody IndexTemplateLogicDTO param) {
        return templateLogicService.updateBlockWriteState(param.getId(), param.getBlockWrite(), HttpRequestUtils.getOperator(request));
    }

    private Result<List<OpLogicTemplateVO>> getLogicTemplateList(IndexTemplateLogicDTO param){
        return Result
                .buildSucc(ConvertUtil.list2List(templateLogicService.getLogicTemplates(param), OpLogicTemplateVO.class));
    }
}
