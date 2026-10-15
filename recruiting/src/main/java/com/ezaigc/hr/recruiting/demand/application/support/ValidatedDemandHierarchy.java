package com.ezaigc.hr.recruiting.demand.application.support;

import lombok.Value;

/**
 * 客户、项目和岗位关系校验通过后的规范化标识。
 */
@Value
public class ValidatedDemandHierarchy {

    String clientId;
    String projectId;
    String positionId;
}
