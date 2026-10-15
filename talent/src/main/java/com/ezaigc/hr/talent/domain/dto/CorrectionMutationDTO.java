package com.ezaigc.hr.talent.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;
import tools.jackson.databind.JsonNode;

@Data
@AllArgsConstructor
public class CorrectionMutationDTO {

    /**
     * 结构化数据中的 JSON Pointer 路径。
     */
    private String jsonPointer;
    /**
     * 原值。
     */
    @ToString.Exclude
    private JsonNode oldValue;
    /**
     * 新值。
     */
    @ToString.Exclude
    private JsonNode newValue;
}
