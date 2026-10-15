package com.ezaigc.hr.talent.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

/**
 * 确认接收模块内部数据传输对象。
 */
@Data
@AllArgsConstructor
public class ConfirmIntakeDTO {

    /**
     * 候选人 ID。
     */
    private String candidateId;
    /**
     * 姓名。
     */
    private String fullName;
    /**
     * 邮箱地址。
     */
    @ToString.Exclude
    private String email;
    /**
     * 联系电话。
     */
    @ToString.Exclude
    private String phone;
    /**
     * 技术乐观锁版本号。
     */
    private long version;
}
