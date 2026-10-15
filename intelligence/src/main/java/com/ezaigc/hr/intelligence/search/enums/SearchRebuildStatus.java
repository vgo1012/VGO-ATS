package com.ezaigc.hr.intelligence.search.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 搜索索引重建状态。
 */
@Getter
@RequiredArgsConstructor
public enum SearchRebuildStatus implements CodeDescEnum {
    QUEUED("排队中"),
    RUNNING("执行中"),
    SUCCEEDED("已成功"),
    FAILED("已失败"),
    CANCELLED("已取消");

    private final String desc;

    public static SearchRebuildStatus fromCode(String code) {
        return SearchRebuildStatus.valueOf(code);
    }
}
