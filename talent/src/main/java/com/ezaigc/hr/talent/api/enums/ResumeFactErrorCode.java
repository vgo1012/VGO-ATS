package com.ezaigc.hr.talent.api.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 简历事实端口可由调用方处理的业务错误。
 */
@Getter
@RequiredArgsConstructor
public enum ResumeFactErrorCode implements CodeDescEnum {

    RESUME_FACTS_REQUIRED("简历当前版本尚无有效事实快照"),
    HUMAN_CORRECTION_CURRENT("当前事实已人工更正，请继续人工更正，或上传新简历版本"),
    FACT_CACHE_LINEAGE_CONFLICT("事实缓存血缘冲突，请重试");

    private final String desc;
}
