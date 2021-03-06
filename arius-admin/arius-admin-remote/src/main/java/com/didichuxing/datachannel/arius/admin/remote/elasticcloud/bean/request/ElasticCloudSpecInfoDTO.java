package com.didichuxing.datachannel.arius.admin.remote.elasticcloud.bean.request;

import com.didichuxing.datachannel.arius.admin.client.bean.dto.BaseDTO;
import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ApiModel(description = "容器信息DTO")
public class ElasticCloudSpecInfoDTO extends BaseDTO {

    private static final long serialVersionUID = 4450544850109458979L;
    /**
     * 部署超时
     */
    private int deployTimeout;

    /**
     *  部署并发度
     */
    private int deployConcurrency;

    /**
     *  部署分组信息
     */
    private ElasticCloudAppDeployGroupsDTO deployGroups;

    /**
     *  容器规格
     */
    private ElasticCloudMachineSpecDTO machineSpec;

    /**
     *  容器镜像信息
     */
    private String imageAddress;

    /**
     *  容器存储卷列表
     */
    private List<ElasticCloudVolumeDTO> volumes;

    /**
     *  环境变量
     */
    private List<ElasticCloudEnvDTO> envs;
}
