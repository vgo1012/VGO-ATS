package com.ezaigc.hr.talent.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.ezaigc.hr.common.infrastructure.persistence.StandardPersistenceFields;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("talent_candidate")
public class Candidate extends StandardPersistenceFields {

    /**
     * 主键 ID。
     */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /**
     * 姓名。
     */
    @TableField("full_name")
    private String fullName;
    /**
     * 主要联系邮箱。
     */
    @TableField("primary_email")
    @ToString.Exclude
    private String primaryEmail;
    /**
     * 主要联系电话。
     */
    @TableField("primary_phone")
    @ToString.Exclude
    private String primaryPhone;
    /**
     * 业务负责人用户 ID。
     */
    @TableField("owner_user_id")
    private String ownerUserId;
    /**
     * 业务归属部门 ID。
     */
    @TableField("owner_department_id")
    private String ownerDepartmentId;
    /**
     * 当前状态。
     */
    @TableField("status")
    private String status;
    /**
     * 技术乐观锁版本号。
     */
    @TableField("version")
    @Version
    private long version;

}
