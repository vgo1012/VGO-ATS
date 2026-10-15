package com.ezaigc.hr.identity.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum WecomDirectoryVisibility implements CodeDescEnum {
    VISIBLE("当前可见"),
    PENDING_VERIFICATION("本批未出现待核实");

    private final String desc;
}
