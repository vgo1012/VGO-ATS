package com.ezaigc.hr.talent.domain.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

@Data
@AllArgsConstructor
public class CandidateResponse {

    /**
     * 主键 ID。
     */
    private String id;
    /**
     * 姓名。
     */
    private String fullName;
    /**
     * 主要联系邮箱。
     */
    @ToString.Exclude
    private String primaryEmail;
    /**
     * 主要联系电话。
     */
    @ToString.Exclude
    private String primaryPhone;
    /**
     * 业务负责人用户 ID。
     */
    private String ownerUserId;
    /**
     * 业务归属部门 ID。
     */
    private String ownerDepartmentId;
    /**
     * 当前状态。
     */
    private String status;
    /**
     * 技术乐观锁版本号。
     */
    private String version;
    private String createId;
    /**
     * 创建时间。
     */
    private Instant createTime;
    /**
     * 更新时间。
     */
    private Instant updateTime;
}
