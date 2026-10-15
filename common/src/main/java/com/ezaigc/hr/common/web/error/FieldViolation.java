package com.ezaigc.hr.common.web.error;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FieldViolation {

    /**
     * 校验失败的字段名称。
     */
    private String field;
    /**
     * 提示信息。
     */
    private String message;

}
