package com.didi.arius.gateway.remote.response;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
* @author weizijun
* @date：2017年2月13日
* 
*/
@Data
@NoArgsConstructor
public class TemplateInfoListResponse extends BaseAdminResponse {
	private Map<String, TemplateInfoResponse> data;

}
