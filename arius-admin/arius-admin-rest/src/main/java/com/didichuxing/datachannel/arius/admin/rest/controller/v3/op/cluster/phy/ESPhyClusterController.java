package com.didichuxing.datachannel.arius.admin.rest.controller.v3.op.cluster.phy;

import static com.didichuxing.datachannel.arius.admin.common.constant.ApiVersion.V3_OP;

import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.didichuxing.datachannel.arius.admin.biz.cluster.ClusterPhyManager;
import com.didichuxing.datachannel.arius.admin.client.bean.common.PaginationResult;
import com.didichuxing.datachannel.arius.admin.client.bean.common.Result;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.cluster.ClusterJoinDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.cluster.ClusterPhyConditionDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.vo.cluster.ConsoleClusterPhyVO;
import com.didichuxing.datachannel.arius.admin.client.bean.vo.cluster.ESRoleClusterHostVO;
import com.didichuxing.datachannel.arius.admin.client.bean.vo.cluster.ESRoleClusterVO;
import com.didichuxing.datachannel.arius.admin.client.constant.result.ResultType;
import com.didichuxing.datachannel.arius.admin.common.Tuple;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ecm.RoleCluster;
import com.didichuxing.datachannel.arius.admin.common.util.AriusObjUtils;
import com.didichuxing.datachannel.arius.admin.common.util.ConvertUtil;
import com.didichuxing.datachannel.arius.admin.common.util.HttpRequestUtils;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.ecm.ESPackageService;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.ecm.ESPluginService;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.physic.ClusterPhyService;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiOperation;

@RestController("esPhyClusterControllerV3")
@RequestMapping(V3_OP + "/phy/cluster")
@Api(tags = "ES????????????????????????(REST)")
public class ESPhyClusterController {

    @Autowired
    private ClusterPhyService esClusterPhyService;

    @Autowired
    private ClusterPhyManager clusterPhyManager;

    @Autowired
    private ESPluginService   esPluginService;

    @Autowired
    private ESPackageService  packageService;

    /**
     * ??????????????????ID??????????????????
     */
    @GetMapping("/{clusterId}/roles")
    @ResponseBody
    @ApiOperation(value = "??????????????????ID????????????????????????", notes = "")
    public Result<List<ESRoleClusterVO>> roleList(@PathVariable Integer clusterId) {
        List<RoleCluster> roleClusters = esClusterPhyService.listPhysicClusterRoles(clusterId);

        if (AriusObjUtils.isNull(roleClusters)) {
            return Result.buildFail(ResultType.NOT_EXIST.getMessage());
        }
        return Result.buildSucc(ConvertUtil.list2List(roleClusters, ESRoleClusterVO.class));
    }

    @DeleteMapping("/plugin/{id}")
    @ResponseBody
    @ApiOperation(value = "??????????????????", notes = "")
    @Deprecated
    public Result<Long> pluginDelete(HttpServletRequest request, @PathVariable Long id) {
        return esPluginService.deletePluginById(id, HttpRequestUtils.getOperator(request));
    }

    @DeleteMapping("/package/{id}")
    @ResponseBody
    @ApiOperation(value = "?????????????????????", notes = "")
    public Result<Long> packageDelete(HttpServletRequest request, @PathVariable Long id) {
        return packageService.deleteESPackage(id, HttpRequestUtils.getOperator(request));
    }

    @PostMapping("/join")
    @ResponseBody
    @ApiOperation(value = "????????????", notes = "???????????????????????????")
    public Result<Tuple<Long, String>> clusterJoin(HttpServletRequest request, @RequestBody ClusterJoinDTO param) {
        return clusterPhyManager.clusterJoin(param, HttpRequestUtils.getOperator(request));
    }

    @PostMapping("/join/{templateSrvId}/checkTemplateService")
    @ResponseBody
    @ApiOperation(value = "???????????????????????????????????????????????????????????????")
    public Result<Boolean> addTemplateSrvId(HttpServletRequest request,
                                            @RequestBody ClusterJoinDTO clusterJoinDTO,
                                            @PathVariable("templateSrvId") String templateSrvId) {
        return clusterPhyManager.checkTemplateServiceWhenJoin(clusterJoinDTO, templateSrvId, HttpRequestUtils.getOperator(request));
    }

    @GetMapping("/{clusterId}/regioninfo")
    @ResponseBody
    @ApiOperation(value = "????????????????????????")
    public Result<List<ESRoleClusterHostVO>> getClusterPhyRegionInfos(@PathVariable Integer clusterId) {
        return clusterPhyManager.getClusterPhyRegionInfos(clusterId);
    }

    @GetMapping("/{clusterLogicType}/{clusterLogicId}/list")
    @ResponseBody
    @ApiOperation(value = "???????????????????????????region???????????????????????????")
    public Result<List<String>> listCanBeAssociatedRegionOfClustersPhys(@PathVariable Integer clusterLogicType,
                                                                        @PathVariable Long clusterLogicId) {
        return clusterPhyManager.listCanBeAssociatedRegionOfClustersPhys(clusterLogicType, clusterLogicId);
    }

    @GetMapping("/{clusterLogicType}/list")
    @ResponseBody
    @ApiOperation(value = "??????????????????????????????????????????????????????")
    public Result<List<String>> listCanBeAssociatedClustersPhys(@PathVariable Integer clusterLogicType) {
        return clusterPhyManager.listCanBeAssociatedClustersPhys(clusterLogicType);
    }

    @GetMapping("/names")
    @ResponseBody
    @ApiOperation(value = "??????AppId??????????????????????????????????????????")
    public Result<List<String>> getClusterPhyNames(HttpServletRequest request) {
        return Result.buildSucc(clusterPhyManager.getAppClusterPhyNames(HttpRequestUtils.getAppId(request)));
    }

    @GetMapping("/{clusterPhyName}/nodes")
    @ResponseBody
    @ApiOperation(value = "??????AppId????????????????????????????????????")
    public Result<List<String>> getAppClusterPhyNodeNames(@PathVariable String clusterPhyName) {
        return Result.buildSucc(clusterPhyManager.getAppClusterPhyNodeNames(clusterPhyName));
    }

    @GetMapping("/node/names")
    @ResponseBody
    @ApiOperation(value = "??????AppId????????????????????????????????????")
    public Result<List<String>> getAppNodeNames(HttpServletRequest request) {
        return Result.buildSucc(clusterPhyManager.getAppNodeNames(HttpRequestUtils.getAppId(request)));
    }

    @PostMapping("/page")
    @ResponseBody
    @ApiOperation(value = "??????????????????????????????")
    public PaginationResult<ConsoleClusterPhyVO> pageGetConsoleClusterPhyVOS(HttpServletRequest request,
                                                                         @RequestBody ClusterPhyConditionDTO condition) {
        return clusterPhyManager.pageGetConsoleClusterPhyVOS(condition, HttpRequestUtils.getAppId(request));
    }

    @GetMapping("/{clusterPhyId}/overView")
    @ResponseBody
    @ApiOperation(value = "????????????????????????????????????")
    @ApiImplicitParam(type = "Integer", name = "clusterPhyId", value = "????????????ID", required = true)
    public Result<ConsoleClusterPhyVO> get(@PathVariable("clusterPhyId") Integer clusterId, HttpServletRequest request) {
        return Result.buildSucc(clusterPhyManager.getConsoleClusterPhy(clusterId, HttpRequestUtils.getAppId(request)));
    }

    @GetMapping("/{clusterLogicType}/{clusterName}/version/list")
    @ResponseBody
    @ApiOperation(value = "????????????????????????????????????????????????????????????????????????????????????????????????")
    public Result<List<String>> getPhyClusterNameWithSameEsVersion(@PathVariable("clusterLogicType") Integer clusterLogicType, @PathVariable(name = "clusterName", required = false) String clusterName) {
        return clusterPhyManager.getPhyClusterNameWithSameEsVersion(clusterLogicType, clusterName);
    }

    @GetMapping("/{clusterLogicId}/bind/version/list")
    @ResponseBody
    @ApiOperation(value = "???????????????????????????region??????????????????????????????????????????")
    public Result<List<String>> getPhyClusterNameWithSameEsVersionAfterBuildLogic(@PathVariable("clusterLogicId") Long clusterLogicId) {
        return clusterPhyManager.getPhyClusterNameWithSameEsVersionAfterBuildLogic(clusterLogicId);
    }

    @GetMapping("/{clusterPhy}/{clusterLogic}/{templateSize}/bindRack")
    @ResponseBody
    @ApiOperation(value = "???????????????????????????????????????????????????????????????????????????rack??????")
    public Result<Set<String>> getValidRacksListByDiskSize(@PathVariable("clusterPhy") String clusterPhy,
                                                @PathVariable("clusterLogic") String clusterLogic,
                                                @PathVariable("templateSize") String templateSize) {
        return clusterPhyManager.getValidRacksListByTemplateSize(clusterPhy, clusterLogic, templateSize);
    }

}
