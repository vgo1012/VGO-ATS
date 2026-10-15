package com.ezaigc.hr.recruiting.recruitment.domain.response;

import lombok.Value;

/** 所选创建时间范围内，当前可见招聘活动的阶段人数。 */
@Value
public class ApplicationStageCountResponse {
    String stage;
    long count;
}
