package com.didichuxing.datachannel.arius.admin.v3.op.cluster.phy;

import com.didichuxing.datachannel.arius.admin.base.BasePhyClusterInfoTest;
import com.didichuxing.datachannel.arius.admin.client.bean.common.PaginationResult;
import com.didichuxing.datachannel.arius.admin.client.bean.common.Result;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.cluster.ClusterPhyConditionDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.dto.cluster.ESRoleClusterHostDTO;
import com.didichuxing.datachannel.arius.admin.client.bean.vo.cluster.ConsoleClusterPhyVO;
import com.didichuxing.datachannel.arius.admin.client.bean.vo.cluster.ESRoleClusterHostVO;
import com.didichuxing.datachannel.arius.admin.client.bean.vo.cluster.ESRoleClusterVO;
import com.didichuxing.datachannel.arius.admin.client.constant.resource.ResourceLogicTypeEnum;
import com.didichuxing.datachannel.arius.admin.method.v3.op.cluster.phy.ESPhyClusterControllerMethod;
import com.didichuxing.datachannel.arius.admin.source.LogicClusterInfoSource;
import com.didichuxing.datachannel.arius.admin.source.PhyClusterInfoSource;
import com.didichuxing.datachannel.arius.admin.source.CustomDataSource;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author cjm
 */
public class ESPhyPhyClusterTest extends BasePhyClusterInfoTest {

    @Test
    public void clusterJoinTest() throws IOException {
        Assertions.assertNotNull(phyClusterInfo.getPhyClusterId());
        Assertions.assertNotNull(phyClusterInfo.getPhyClusterName());
    }

    @Test
    public void roleListTest() throws IOException {
        Result<List<ESRoleClusterVO>> result = ESPhyClusterControllerMethod.roleList(phyClusterInfo.getPhyClusterId());
        Assertions.assertEquals(result.getData().size(), phyClusterInfo.getConsoleClusterPhyVO().getEsRoleClusterVOS().size());
    }

    @Test
    public void getClusterPhyRegionInfosTest() throws IOException {
        Result<List<ESRoleClusterHostVO>> result = ESPhyClusterControllerMethod.getClusterPhyRegionInfos(phyClusterInfo.getPhyClusterId());
        Assertions.assertTrue(result.success());
    }

    @Test
    public void listCanBeAssociatedRegionOfClustersPhysTest() throws IOException {
        // ?????????????????????
        LogicClusterInfoSource.LogicClusterInfo logicClusterInfo = LogicClusterInfoSource.applyLogicCluster(phyClusterInfo.getPhyClusterName(), phyClusterInfo.getPhyClusterName());

        Result<List<String>> result = ESPhyClusterControllerMethod
                .listCanBeAssociatedRegionOfClustersPhys(ResourceLogicTypeEnum.PRIVATE.getCode(), logicClusterInfo.getLogicClusterId());
        Assertions.assertTrue(result.success());
        Assertions.assertTrue(result.getData().contains(phyClusterInfo.getPhyClusterName()));
        
        // ??????????????????
        LogicClusterInfoSource.removeLogicCluster(logicClusterInfo.getLogicClusterName(), logicClusterInfo.getLogicClusterId());
    }

    @Test
    public void listCanBeAssociatedClustersPhysTest() throws IOException {
        // ????????? type
        Result<List<String>> result1 = ESPhyClusterControllerMethod.listCanBeAssociatedClustersPhys(-1);
        Assertions.assertTrue(result1.failed());
        Result<List<String>> result2 = ESPhyClusterControllerMethod.listCanBeAssociatedClustersPhys(ResourceLogicTypeEnum.PRIVATE.getCode());
        Assertions.assertTrue(result2.success());
    }

    @Test
    public void getClusterPhyNamesTest() throws IOException {
        Result<List<String>> result = ESPhyClusterControllerMethod.getClusterPhyNames();
        Assertions.assertTrue(result.success());
        Assertions.assertTrue(result.getData().contains(phyClusterInfo.getPhyClusterName()));
    }

    @Test
    public void getAppClusterPhyNodeNamesTest() throws IOException {
        Result<List<String>> result1 = ESPhyClusterControllerMethod.getAppClusterPhyNodeNames(phyClusterInfo.getPhyClusterName());
        Assertions.assertTrue(result1.success());
        if(result1.success()) {
            // ?????????????????????????????????????????????ip??????
            Set<String> ipSet = phyClusterInfo.getClusterJoinDTO()
                    .getRoleClusterHosts().stream().map(ESRoleClusterHostDTO::getIp).collect(Collectors.toSet());
            // ??????????????????
            Assertions.assertEquals(result1.getData().size(), ipSet.size());
        }
    }

    @Test
    public void pageGetConsoleClusterPhyVOSTest() throws IOException {

        // ??????????????????????????????id???????????????????????????????????????????????????
        ClusterPhyConditionDTO clusterPhyConditionDTO = CustomDataSource.getClusterPhyConditionDTO(null);
        PaginationResult<ConsoleClusterPhyVO> result1 = ESPhyClusterControllerMethod.pageGetConsoleClusterPhyVOS(clusterPhyConditionDTO);
        List<ConsoleClusterPhyVO> data = result1.getData().getBizData();
        Assertions.assertTrue(data.size() >= 1);

        // ???????????????????????????
        ClusterPhyConditionDTO clusterPhyConditionDTO2 = CustomDataSource.getClusterPhyConditionDTO(phyClusterInfo.getPhyClusterName());
        PaginationResult<ConsoleClusterPhyVO> result2 = ESPhyClusterControllerMethod.pageGetConsoleClusterPhyVOS(clusterPhyConditionDTO2);
        List<ConsoleClusterPhyVO> data2 = result2.getData().getBizData();
        boolean flag = false;
        for(ConsoleClusterPhyVO consoleClusterPhyVO : data2) {
            if(consoleClusterPhyVO.getCluster().contains(phyClusterInfo.getPhyClusterName())) {
                flag = true;
                break;
            }
        }
        Assertions.assertTrue(flag);
    }

    @Test
    public void getTest() throws IOException {
        Result<ConsoleClusterPhyVO> result1 = ESPhyClusterControllerMethod.get(phyClusterInfo.getPhyClusterId());
        Assertions.assertEquals(result1.getData().getCluster(), phyClusterInfo.getPhyClusterName());
    }

    @Test
    public void getPhyClusterNameWithSameEsVersionTest() throws IOException {
        Result<List<String>> result = ESPhyClusterControllerMethod
                .getPhyClusterNameWithSameEsVersion(ResourceLogicTypeEnum.PRIVATE.getCode(), phyClusterInfo.getPhyClusterName());
        Assertions.assertTrue(result.success());
        // ???????????????????????????
        PhyClusterInfoSource.PhyClusterInfo phyClusterInfo = PhyClusterInfoSource.phyClusterJoin();
        try {
            Result<List<String>> result2 = ESPhyClusterControllerMethod
                    .getPhyClusterNameWithSameEsVersion(ResourceLogicTypeEnum.PRIVATE.getCode(), phyClusterInfo.getPhyClusterName());
            Assertions.assertTrue(result2.success());
            // ??????????????????????????????????????????????????????
            Assertions.assertTrue(result2.getData().contains(phyClusterInfo.getPhyClusterName()));
        } finally {
            // ??????????????????????????????
            PhyClusterInfoSource.phyClusterRemove(phyClusterInfo.getPhyClusterName(), phyClusterInfo.getPhyClusterId());
        }
    }

    @Test
    public void getPhyClusterNameWithSameEsVersionAfterBuildLogicTest() throws IOException {
        // ????????????????????????
        LogicClusterInfoSource.LogicClusterInfo logicClusterInfo =
                LogicClusterInfoSource.applyLogicCluster(phyClusterInfo.getPhyClusterName(), phyClusterInfo.getPhyClusterName());

        Result<List<String>> result =
                ESPhyClusterControllerMethod.getPhyClusterNameWithSameEsVersionAfterBuildLogic(logicClusterInfo.getLogicClusterId());
        Assertions.assertTrue(result.success());
        // ??????????????????
        LogicClusterInfoSource.removeLogicCluster(logicClusterInfo.getLogicClusterName(), logicClusterInfo.getLogicClusterId());
    }

    @Test
    public void getValidRacksListByDiskSizeTest() throws IOException {
        // ????????????????????????
        LogicClusterInfoSource.LogicClusterInfo logicClusterInfo =
                LogicClusterInfoSource.applyLogicCluster(phyClusterInfo.getPhyClusterName(), phyClusterInfo.getPhyClusterName());
        Result<List<String>> result =
                ESPhyClusterControllerMethod.getValidRacksListByDiskSize(phyClusterInfo.getPhyClusterName(), logicClusterInfo.getLogicClusterName(), "30");
        Assertions.assertTrue(result.success());
    }

    @Test
    public void getAppNodeNamesTest() throws IOException {
        Result<List<String>> result = ESPhyClusterControllerMethod.getAppNodeNames();
        Assertions.assertTrue(result.success());
    }

    @Test
    public void pluginDeleteTest() {

    }

    @Test
    public void packageDeleteTest() {

    }

}
