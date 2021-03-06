package com.didichuxing.datachannel.arius.admin.common.bean.entity.workorder.detail;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TemplateQueryDslOrderDetail extends AbstractOrderDetail {
    private Integer id;

    /**
     * 名字
     */
    private String  name;
    /**
     * dsl语句
     */
    private String  dsl;
    /**
     * 备注
     */
    private String  memo;
}
