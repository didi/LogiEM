package com.didichuxing.datachannel.arius.admin.rest.controller.v2.op.app;

import static com.didichuxing.datachannel.arius.admin.common.constant.ApiVersion.V2_OP;
import static com.didichuxing.datachannel.arius.admin.common.constant.ApiVersion.V3_OP;

import com.didichuxing.datachannel.arius.admin.biz.app.AppLogicTemplateAuthManager;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import com.didichuxing.datachannel.arius.admin.core.service.template.logic.TemplateLogicService;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.didichuxing.datachannel.arius.admin.client.bean.common.Result;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.app.AppTemplateAuthDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.vo.app.AppTemplateAuthVO;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ClusterLogic;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.template.IndexTemplateLogicWithClusterAndMasterTemplate;
import com.didichuxing.datachannel.arius.admin.common.util.ConvertUtil;
import com.didichuxing.datachannel.arius.admin.common.util.HttpRequestUtils;
import com.didichuxing.datachannel.arius.admin.core.service.app.AppLogicTemplateAuthService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;

@RestController
@RequestMapping({ V2_OP + "/app/auth", V3_OP + "/app/auth/template" })
@Api(tags = "OP-?????????App??????????????????(REST)")
public class AppTemplateAuthController {

    @Autowired
    private AppLogicTemplateAuthService appLogicTemplateAuthService;

    @Autowired
    private TemplateLogicService        templateLogicService;

    @Autowired
    private AppLogicTemplateAuthManager appLogicTemplateAuthManager;

    @GetMapping("/get")
    @ResponseBody
    @ApiOperation(value = "??????APP????????????", notes = "")
    @ApiImplicitParams({ @ApiImplicitParam(paramType = "query", dataType = "Integer", name = "appId", value = "??????ID", required = true) })
    public Result<List<AppTemplateAuthVO>> getAppTemplateAuths(@RequestParam("appId") Integer appId) {
        List<AppTemplateAuthVO> templateAuths = ConvertUtil
            .list2List(appLogicTemplateAuthService.getAppActiveTemplateRWAndRAuths(appId), AppTemplateAuthVO.class);

        fillTemplateAuthVO(templateAuths);

        return Result.buildSucc(templateAuths);
    }

    @PostMapping("/add")
    @ResponseBody
    @ApiOperation(value = "??????APP????????????", notes = "")
    public Result<Void> addTemplateAuth(HttpServletRequest request, @RequestBody AppTemplateAuthDTO authDTO) {
        return appLogicTemplateAuthService.addTemplateAuth(authDTO, HttpRequestUtils.getOperator(request));
    }

    @PutMapping("/update")
    @ResponseBody
    @ApiOperation(value = "??????APP????????????", notes = "")
    public Result<Void> updateTemplateAuth(HttpServletRequest request, @RequestBody AppTemplateAuthDTO authDTO) {
        return appLogicTemplateAuthManager.updateTemplateAuth(authDTO, HttpRequestUtils.getOperator(request));
    }

    @DeleteMapping("/delete")
    @ResponseBody
    @ApiOperation(value = "??????APP????????????", notes = "")
    @ApiImplicitParams({ @ApiImplicitParam(paramType = "query", dataType = "Long", name = "authId", value = "??????ID", required = true) })
    public Result<Void> deleteTemplateAuth(HttpServletRequest request, @RequestParam("authId") Long authId) {
        return appLogicTemplateAuthService.deleteTemplateAuth(authId, HttpRequestUtils.getOperator(request));
    }

    @PutMapping("/checkMeta")
    @ResponseBody
    @ApiOperation(value = "???????????????????????????", notes = "")
    @ApiImplicitParams({ @ApiImplicitParam(paramType = "query", dataType = "boolean", name = "delete", value = "?????????????????????", required = true) })
    public Result<Void> deleteExcessTemplateAuthsIfNeed(@RequestParam("delete") boolean delete) {
        return Result.build(appLogicTemplateAuthService.deleteExcessTemplateAuthsIfNeed(delete));
    }

    /********************************************private********************************************/
    /**
     * ???AppTemplateAuthVO????????????????????????ID???name???????????????name
     * @param templateAuths ??????????????????
     */
    private void fillTemplateAuthVO(List<AppTemplateAuthVO> templateAuths) {
        if (CollectionUtils.isEmpty(templateAuths)) {
            return;
        }

        // ?????????????????????id
        List<Integer> templateIds = templateAuths.stream().map(AppTemplateAuthVO::getTemplateId)
            .collect(Collectors.toList());

        Map<Integer, IndexTemplateLogicWithClusterAndMasterTemplate> logicTemplateMap = templateLogicService
            .getLogicTemplatesWithClusterAndMasterTemplateMap(new HashSet<>(templateIds));

        for (AppTemplateAuthVO authVO : templateAuths) {
            Integer templateId = authVO.getTemplateId();
            IndexTemplateLogicWithClusterAndMasterTemplate logicTemplate = logicTemplateMap.get(templateId);
            if (logicTemplate != null) {
                // ??????????????????
                authVO.setTemplateName(logicTemplate.getName());
                // ??????????????????
                ClusterLogic logicCluster = logicTemplate.getLogicCluster();
                // ???????????????????????????????????????????????????
                if (logicCluster != null) {
                    authVO.setLogicClusterId(logicCluster.getId());
                    authVO.setLogicClusterName(logicCluster.getName());
                } else {
                    authVO.setLogicClusterName("");
                }
            } else {
                authVO.setTemplateName("");
            }
        }
    }
}
