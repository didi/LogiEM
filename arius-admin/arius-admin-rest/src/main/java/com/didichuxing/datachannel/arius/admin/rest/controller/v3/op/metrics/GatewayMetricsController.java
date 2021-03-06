package com.didichuxing.datachannel.arius.admin.rest.controller.v3.op.metrics;

import static com.didichuxing.datachannel.arius.admin.common.constant.ApiVersion.V3_OP;

import java.util.List;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.didichuxing.datachannel.arius.admin.biz.metrics.GatewayMetricsManager;
import com.didichuxing.datachannel.arius.admin.client.bean.common.Result;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.metrics.ClientNodeDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.metrics.GatewayAppDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.metrics.GatewayDslDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.metrics.GatewayIndexDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.metrics.GatewayMetricsDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.metrics.GatewayNodeDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.metrics.GatewayOverviewDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.metrics.MultiGatewayNodesDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.vo.metrics.other.gateway.GatewayOverviewMetricsVO;
import com.didichuxing.datachannel.arius.admin.client.bean.vo.metrics.top.VariousLineChartMetricsVO;
import com.didichuxing.datachannel.arius.admin.common.constant.metrics.GatewayMetricsTypeEnum;
import com.didichuxing.datachannel.arius.admin.common.util.HttpRequestUtils;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.annotations.ApiOperation;

@RestController
@RequestMapping(V3_OP + "/gateway/metrics")
@Api(tags = "Gateway??????????????????")
public class GatewayMetricsController {

    @Autowired
    private GatewayMetricsManager gatewayMetricsManager;

    @GetMapping("/config/{group}")
    @ApiOperation(value = "????????????????????????", notes = "")
    public Result<List<String>> getGatewayMetrics(@PathVariable String group) {
        return gatewayMetricsManager.getGatewayMetricsEnums(group);
    }

    @GetMapping("/dslMd5/list")
    @ApiOperation(value = "????????????????????????dslMd5??????", notes = "")
    public Result<List<String>> getDslMd5List(Long startTime, Long endTime, HttpServletRequest request) {
        return gatewayMetricsManager.getDslMd5List(HttpRequestUtils.getAppId(request), startTime, endTime);
    }

    @PostMapping("/overview")
    @ApiOperation(value = "??????gateway??????", notes = "")
    public Result<List<GatewayOverviewMetricsVO>> getGatewayOverviewMetrics(@RequestBody GatewayOverviewDTO dto) {
        validateParam(dto);
        return gatewayMetricsManager.getGatewayOverviewMetrics(dto);
    }

    @PostMapping("/node")
    @ApiOperation(value = "??????gateway??????????????????")
    public Result<List<VariousLineChartMetricsVO>> getGatewayNodeMetrics(@RequestBody GatewayNodeDTO dto,
                                                                         HttpServletRequest request) {
        validateParam(dto);
        return gatewayMetricsManager.getGatewayNodeMetrics(dto, HttpRequestUtils.getAppId(request));
    }

    @PostMapping("/nodes")
    @ApiOperation(value = "???????????????gateway??????????????????")
    public Result<List<VariousLineChartMetricsVO>> getMultiGatewayNodesMetrics(@RequestBody MultiGatewayNodesDTO dto,
                                                                               HttpServletRequest request) {
        validateParam(dto);
        return gatewayMetricsManager.getMultiGatewayNodesMetrics(dto, HttpRequestUtils.getAppId(request));
    }

    @PostMapping("/node/client")
    @ApiOperation(value = "??????gatewayNode?????????clientNode????????????")
    public Result<List<VariousLineChartMetricsVO>> getClientNodeMetrics(@RequestBody ClientNodeDTO dto,
                                                                         HttpServletRequest request) {
        validateParam(dto);
        return gatewayMetricsManager.getClientNodeMetrics(dto, HttpRequestUtils.getAppId(request));
    }

    @GetMapping("/node/client/list")
    @ApiOperation(value = "?????????gatewayNode?????????clientNode ip??????")
    public Result<List<String>> getClientNodeIpList(String gatewayNode, Long startTime,
                                                    Long endTime, HttpServletRequest request) {
        return gatewayMetricsManager.getClientNodeIdList(gatewayNode, startTime, endTime, HttpRequestUtils.getAppId(request));
    }

    @PostMapping("/index")
    @ApiOperation(value = "??????gateway??????????????????")
    public Result<List<VariousLineChartMetricsVO>> getGatewayIndexMetrics(@RequestBody GatewayIndexDTO dto,
                                                                          HttpServletRequest request) {
        validateParam(dto);
        return gatewayMetricsManager.getGatewayIndexMetrics(dto, HttpRequestUtils.getAppId(request));
    }

    @PostMapping("/app")
    @ApiModelProperty(value = "??????gateway??????????????????")
    public Result<List<VariousLineChartMetricsVO>> getGatewayAppMetrics(@RequestBody GatewayAppDTO dto) {
        validateParam(dto);
        return gatewayMetricsManager.getGatewayAppMetrics(dto);
    }


    @PostMapping("/dsl")
    @ApiModelProperty(value = "??????gateway????????????????????????")
    public Result<List<VariousLineChartMetricsVO>> getGatewayDslMetrics(@RequestBody GatewayDslDTO dto,
                                                                        HttpServletRequest request) {
        validateParam(dto);
        return gatewayMetricsManager.getGatewayDslMetrics(dto, HttpRequestUtils.getAppId(request));
    }

    private void validateParam(GatewayMetricsDTO dto) {
        dto.validParam();
        //??????????????????????????????????????????
        List<String> metricsByGroup = GatewayMetricsTypeEnum.getMetricsByGroup(dto.getGroup());
        String invalidMetrics = dto.getMetricsTypes().stream().filter(x -> !metricsByGroup.contains(x)).collect(Collectors.joining(","));
        if (StringUtils.isNotBlank(invalidMetrics)) {
            throw new RuntimeException("????????????:" + invalidMetrics);
        }
    }

}
