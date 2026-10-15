package com.ezaigc.hr.intelligence.matching.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class VersionReasonDTO {

    /**
     * 技术乐观锁版本号。
     */
    private long version;
    /**
     * 操作原因。
     */
    private String reason;
}
