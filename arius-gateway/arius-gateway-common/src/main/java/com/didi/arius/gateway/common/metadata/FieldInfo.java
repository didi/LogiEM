package com.didi.arius.gateway.common.metadata;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class FieldInfo {
	/**
	 * 字段类型
	 */
	private String type;

	/**
	 * 字段基数
	 */
	private int cardinality;

}
