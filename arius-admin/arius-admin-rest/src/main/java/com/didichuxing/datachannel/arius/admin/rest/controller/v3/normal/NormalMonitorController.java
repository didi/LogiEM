package com.didichuxing.datachannel.arius.admin.rest.controller.v3.normal;

import com.didichuxing.datachannel.arius.admin.client.bean.common.BaseResult;
import com.didichuxing.datachannel.arius.admin.client.bean.common.NameValue;
import com.didichuxing.datachannel.arius.admin.client.bean.common.PaginationResult;
import com.didichuxing.datachannel.arius.admin.client.bean.common.Result;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.monitor.AppMonitorRuleDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.monitor.NotifyGroupDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.monitor.QueryMonitorRuleDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.monitor.QueryNotifyGroupDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.vo.monitor.MonitorAlertDetailVO;
import com.didichuxing.datachannel.arius.admin.client.bean.vo.monitor.MonitorAlertVO;
import com.didichuxing.datachannel.arius.admin.client.bean.vo.monitor.MonitorRuleDetailVO;
import com.didichuxing.datachannel.arius.admin.client.bean.vo.monitor.NotifyGroupVO;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.GlobalParams;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.monitor.Alert;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.monitor.MonitorAlertDetail;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.monitor.n9e.UserInfo;
import com.didichuxing.datachannel.arius.admin.common.constant.NotifyGroupStatusEnum;
import com.didichuxing.datachannel.arius.admin.common.converter.MonitorRuleConverter;
import com.didichuxing.datachannel.arius.admin.common.exception.NotExistException;
import com.didichuxing.datachannel.arius.admin.core.service.monitor.MonitorService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.didichuxing.datachannel.arius.admin.common.constant.ApiVersion.V3_NORMAL;

@RestController
@RequestMapping(V3_NORMAL + "/monitor")
@Api(tags = "Normal-????????????????????????")
public class NormalMonitorController {

    @Autowired
    private MonitorService monitorService;

    @GetMapping("/monitorRule/category")
    @ApiOperation(value = "????????????????????????")
    public Result<List<NameValue>> getCategory() {
        return Result.buildSucc(monitorService.findMonitorCategory());
    }

    @GetMapping("/monitorRule/statsType")
    @ApiOperation(value = "?????????????????????????????????")
    public Result<List<NameValue>> getStatsType() {
        return Result.buildSucc(monitorService.findMonitorStatsTypes());
    }

    @GetMapping("/monitorRule/operator")
    @ApiOperation(value = "?????????????????????????????????")
    public Result<List<NameValue>> getOperator() {
        return Result.buildSucc(monitorService.findMonitorOperators());
    }

    @GetMapping("/monitorRule/{category}/metrics")
    @ApiOperation(value = "?????????????????????????????????")
    public Result<List<String>> getMetrics(@PathVariable String category) {
        return Result.buildSucc(monitorService.findMonitorMetrics(category));
    }

    @PostMapping("/monitorRule")
    @ApiOperation(value = "??????????????????", notes = "")
    public Result<Long> createMonitorRule(@RequestBody AppMonitorRuleDTO dto) {
        if (!dto.paramLegal()) {
            return Result.buildParamIllegal("???????????????!");
        }
        return monitorService.createMonitorRule(dto);
    }

    @DeleteMapping("/monitorRule/{id}")
    @ApiOperation(value = "??????????????????", notes = "")
    public Result<Boolean> deleteMonitor(@PathVariable Long id) {
        return monitorService.deleteMonitorRule(id);
    }

    @PutMapping("/monitorRule")
    @ApiOperation(value = "??????????????????", notes = "")
    public Result<Void> modifyMonitors(@RequestBody AppMonitorRuleDTO dto) {
        if (!dto.paramLegal() || null == dto.getId()) {
            return Result.buildParamIllegal("???????????????!");
        }
        return monitorService.modifyMonitorRule(dto);
    }

    @PostMapping("/monitorRules")
    @ApiOperation(value = "??????????????????", notes = "")
    public BaseResult getMonitorRules(@RequestBody QueryMonitorRuleDTO dto) {
        Integer appId = dto.getAppId() != null ? dto.getAppId() : GlobalParams.CURRENT_APPID.get();
        dto.setAppId(appId);
        return monitorService.findMonitorRules(dto);
    }

    @GetMapping("/monitorRule/{id}")
    @ApiOperation(value = "??????????????????", notes = "")
    public Result<MonitorRuleDetailVO> getMonitorDetail(@PathVariable("id") Long id) {
        return monitorService.getMonitorRuleDetail(id);
    }

    @PostMapping("/monitorRule/switch/{id}")
    @ApiOperation(value = "??????/??????????????????", notes = "0 ?????????1??????")
    public Result<Boolean> switchMonitorRule(@PathVariable("id") Long id, @RequestParam Integer status) {
        return monitorService.switchMonitorRule(id, status);
    }

    @GetMapping("/alerts")
    @ApiOperation(value = "????????????", notes = "")
    public Result<List<MonitorAlertVO>> getMonitorAlertHistory(@RequestParam("monitorId") Long monitorId,
                                                               @RequestParam("startTime") Long startTime,
                                                               @RequestParam("endTime") Long endTime) {
        Result<List<Alert>> result = monitorService.getMonitorAlertHistory(monitorId, startTime, endTime);
        if (null == result || result.failed()) {
            return Result.buildFail("??????????????????");
        }
        return Result.buildSucc(MonitorRuleConverter.convert2MonitorAlertVOList(result.getData()));
    }

    @GetMapping("/alerts/{alertId}")
    @ApiOperation(value = "????????????", notes = "")
    public Result<MonitorAlertDetailVO> getMonitorAlertDetail(@PathVariable("alertId") Long alertId) {
        Result<MonitorAlertDetail> result = monitorService.getMonitorAlertDetail(alertId);
        if (null == result || result.failed()) {
            return Result.buildFail("??????????????????");
        }
        return Result.buildSucc(MonitorRuleConverter.convert2MonitorAlertDetailVO(result.getData()));
    }

    //?????????

    @PostMapping("/notifyGroups")
    @ApiOperation(value = "???????????????")
    public PaginationResult<NotifyGroupVO> findNotifyGroups(@RequestBody QueryNotifyGroupDTO param) {
        return monitorService.findNotifyGroupPage(param);
    }

    @GetMapping("/notifyGroup/{id}")
    @ApiOperation(value = "???????????????")
    public Result<NotifyGroupVO> getNotifyGroup(@PathVariable Long id) throws NotExistException {
        return Result.buildSucc(monitorService.getNotifyGroupVO(id));
    }

    @PostMapping("/notifyGroup")
    @ApiOperation(value = "???????????????")
    public Result<Void> saveNotifyGroup(@RequestBody NotifyGroupDTO dto) {
        monitorService.saveNotifyGroup(dto);
        return Result.buildSucc();
    }

    @PutMapping("/notifyGroup")
    @ApiOperation(value = "???????????????")
    public Result<Void> modifyNotifyGroup(@RequestBody NotifyGroupDTO dto) throws Exception {
        monitorService.modifyNotifyGroup(dto);
        return Result.buildSucc();
    }

    @DeleteMapping("/notifyGroup/{id}")
    @ApiOperation(value = "???????????????")
    public Result<Void> delNotifyGroup(@PathVariable Long id) throws NotExistException {
        monitorService.removeNotifyGroup(id);
        return Result.buildSucc();
    }

    @PostMapping("/notifyGroup/switch/{id}")
    @ApiOperation(value = "??????/???????????????")
    public Result<Void> switchNotifyGroup(@PathVariable Long id, @RequestParam Integer status) throws NotExistException {
        if (!status.equals(NotifyGroupStatusEnum.ENABLE.getValue())
                && !status.equals(NotifyGroupStatusEnum.DISABLE.getValue())) {
            return Result.buildFail("status ????????????");
        }
        monitorService.switchNotifyGroup(id, status);
        return Result.buildSucc();
    }

    @GetMapping("/notifyGroup/{id}/inuse")
    @ApiOperation(value = "??????????????????????????????")
    public Result<List<String>> checkNotifyGroupUsed(@PathVariable Long id) {
        return Result.buildSucc(monitorService.checkNotifyGroupUsed(id));
    }

    @GetMapping("/notifyGroup/users")
    @ApiOperation(value = "???????????????????????????")
    public Result<List<UserInfo>> getNotifyGroup(@RequestParam String keyword) {
        return Result.buildSucc(monitorService.findN9eUsers(keyword));
    }
}
