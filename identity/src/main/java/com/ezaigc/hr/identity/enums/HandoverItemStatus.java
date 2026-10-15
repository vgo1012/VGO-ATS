package com.ezaigc.hr.identity.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 账号交接事项状态。
 */
@Getter
@RequiredArgsConstructor
public enum HandoverItemStatus implements CodeDescEnum {
    PENDING("待处理"),
    SUCCEEDED("已成功"),
    FAILED("已失败"),
    UNRESOLVED("未解决");

    private final String desc;

    public static HandoverItemStatus fromCode(String code) {
        return HandoverItemStatus.valueOf(code);
    }
}
