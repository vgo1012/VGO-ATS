package com.ezaigc.hr.identity.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 招聘专员按归属人隔离的业务对象；同部门任职不自动授权。 */
@Getter
@RequiredArgsConstructor
public enum RecruiterOwnedObjectType implements CodeDescEnum {
    DEMAND("招聘需求"), JD("岗位说明"), JD_VERSION("岗位说明版本"), MATCH_RUN("匹配评分运行");
    private final String desc;
}
