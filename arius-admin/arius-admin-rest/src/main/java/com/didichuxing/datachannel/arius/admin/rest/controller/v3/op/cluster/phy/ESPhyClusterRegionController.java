package com.didichuxing.datachannel.arius.admin.rest.controller.v3.op.cluster.phy;

import com.didichuxing.datachannel.arius.admin.biz.cluster.ClusterRegionManager;
import com.didichuxing.datachannel.arius.admin.client.bean.common.Result;
import com.didichuxing.datachannel.arius.admin.client.bean.vo.cluster.ClusterRegionVO;
import com.didichuxing.datachannel.arius.admin.client.bean.vo.cluster.ESRoleClusterHostVO;
import com.didichuxing.datachannel.arius.admin.client.bean.vo.cluster.PhyClusterRackVO;
import com.didichuxing.datachannel.arius.admin.client.bean.vo.template.IndexTemplatePhysicalVO;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.cluster.ecm.RoleClusterHost;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.region.ClusterRegion;
import com.didichuxing.datachannel.arius.admin.common.util.AriusObjUtils;
import com.didichuxing.datachannel.arius.admin.common.util.ConvertUtil;
import com.didichuxing.datachannel.arius.admin.common.util.HttpRequestUtils;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.physic.ClusterPhyService;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.physic.RoleClusterHostService;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.region.RegionRackService;
import com.didichuxing.datachannel.arius.admin.core.service.template.physic.TemplatePhyService;
import com.didichuxing.datachannel.arius.admin.extend.capacity.plan.bean.dto.CapacityPlanRegionDTO;
import com.didichuxing.datachannel.arius.admin.extend.capacity.plan.service.CapacityPlanRegionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.didichuxing.datachannel.arius.admin.common.constant.ApiVersion.V3_OP;

/**
 * ??????????????????Controller
 * ????????????????????????Region???????????????????????????Rack
 *
 * @author wangshu
 * @date 2020/09/20
 */
@RestController
@RequestMapping(V3_OP + "/phy/cluster/region")
@Api(tags = "ES????????????region??????(REST)")
public class ESPhyClusterRegionController {

    @Autowired
    private ClusterPhyService         clusterPhyService;

    @Autowired
    private RegionRackService         regionRackService;

    @Autowired
    private RoleClusterHostService    roleClusterHostService;

    @Autowired
    private TemplatePhyService        physicalService;

    @Autowired
    private ClusterRegionManager      clusterRegionManager;

    @Autowired
    private CapacityPlanRegionService capacityPlanRegionService;

    @GetMapping("")
    @ResponseBody
    @ApiOperation(value = "??????????????????region????????????", notes = "??????????????????????????????Region??????")
    public Result<List<ClusterRegionVO>> listPhyClusterRegions(@RequestParam("cluster") String cluster,
                                                               @RequestParam("clusterLogicType") Integer clusterLogicType) {
        return listPhyClusterRegionsAfterFilter(cluster, clusterLogicType, null);
    }

    @GetMapping("/bind")
    @ResponseBody
    @ApiOperation(value = "??????????????????region????????????", notes = "??????????????????????????????Region??????")
    public Result<List<ClusterRegionVO>> listPhyClusterRegions(@RequestParam("cluster") String cluster,
                                                               @RequestParam("clusterLogicType") Integer clusterLogicType,
                                                               @RequestParam("clusterLogicId") Long clusterLogicId) {

        return listPhyClusterRegionsAfterFilter(cluster, clusterLogicType, clusterLogicId);
    }

    @PostMapping("/add")
    @ResponseBody
    @ApiOperation(value = "??????????????????region??????", notes = "")
    public Result<Long> createRegion(HttpServletRequest request, @RequestBody CapacityPlanRegionDTO param) {

        return regionRackService.createPhyClusterRegion(param.getClusterName(), param.getRacks(), param.getShare(),
            HttpRequestUtils.getOperator(request));
    }

    @GetMapping("/phyClusterRacks")
    @ResponseBody
    @ApiOperation(value = "??????????????????????????????region???Racks??????", notes = "")
    public Result<List<PhyClusterRackVO>> listPhyClusterRacks(@RequestParam("cluster") String cluster) {
        return Result.buildSucc(clusterRegionManager.buildCanDividePhyClusterRackVOs(cluster));
    }

    @PutMapping("/edit")
    @ResponseBody
    @ApiOperation(value = "??????????????????region??????", notes = "???????????????????????????region???racks")
    public Result<Void> editClusterRegion(HttpServletRequest request, @RequestBody CapacityPlanRegionDTO param) {

        // ???????????????????????????????????????
        // 1. racks?????????????????????region?????????
        // 2. share???configJson??????????????????????????????
        // capacityPlanRegionService.editRegion()????????????????????????????????????????????????
        return capacityPlanRegionService.editRegion(param, HttpRequestUtils.getOperator(request));
    }

    @DeleteMapping("/delete")
    @ResponseBody
    @ApiOperation(value = "??????????????????region??????", notes = "")
    @ApiImplicitParams({ @ApiImplicitParam(paramType = "query", dataType = "Long", name = "regionId", value = "regionId", required = true) })
    public Result<Void> removeRegion(HttpServletRequest request, @RequestParam("regionId") Long regionId) {
        return regionRackService.deletePhyClusterRegion(regionId, HttpRequestUtils.getOperator(request));
    }

    @GetMapping("/{regionId}/nodes")
    @ResponseBody
    @ApiOperation(value = "??????region??????????????????", notes = "")
    public Result<List<ESRoleClusterHostVO>> getRegionNodes(@PathVariable Long regionId) {

        ClusterRegion region = regionRackService.getRegionById(regionId);
        if (region == null) {
            return Result.buildFail("region?????????");
        }

        List<RoleClusterHost> hosts = roleClusterHostService.listRacksNodes(region.getPhyClusterName(),
            region.getRacks());

        return Result.buildSucc(ConvertUtil.list2List(hosts, ESRoleClusterHostVO.class));

    }

    @GetMapping("/{regionId}/templates")
    @ResponseBody
    @ApiOperation(value = "??????Region????????????????????????")
    public Result<List<IndexTemplatePhysicalVO>> getRegionPhysicalTemplates(@PathVariable Long regionId) {
        return Result.buildSucc(
            ConvertUtil.list2List(physicalService.getTemplateByRegionId(regionId), IndexTemplatePhysicalVO.class));
    }

    @GetMapping("/{clusterPhyName}/rack")
    @ResponseBody
    @ApiOperation(value = "????????????????????????rack??????")
    public Result<Set<String>> getClusterPhyRacks(@PathVariable String clusterPhyName) {
        return Result.buildSucc(clusterPhyService.getClusterRacks(clusterPhyName));
    }

    /**
     * ???????????????????????????????????????
     * @param phyCluster          ??????????????????
     * @param clusterLogicType ??????????????????
     * @param clusterLogicId   ????????????id???????????????????????????null
     * @return ??????????????????region????????????
     */
    private Result<List<ClusterRegionVO>> listPhyClusterRegionsAfterFilter(String phyCluster, Integer clusterLogicType, Long clusterLogicId) {
        if (StringUtils.isBlank(phyCluster) || AriusObjUtils.isNull(clusterLogicType)) {
            return Result.buildSucc(new ArrayList<>());
        }

        //????????????????????????????????????????????????????????????region
        List<ClusterRegion> regions = clusterRegionManager.filterClusterRegionByLogicClusterType(clusterLogicId, phyCluster, clusterLogicType);
        return Result.buildSucc(clusterRegionManager.buildLogicClusterRegionVO(regions));
    }
}
