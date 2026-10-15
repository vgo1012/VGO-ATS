package com.ezaigc.hr.identity.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum WecomDirectoryReadStatus implements CodeDescEnum {
    PREVIEW_READY("已完整读取"),
    DISABLED("读取未启用"),
    NOT_CONFIGURED("缺少配置");

    private final String desc;
}
