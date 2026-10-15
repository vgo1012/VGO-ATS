package com.ezaigc.hr.identity.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.ezaigc.hr.common.infrastructure.persistence.StandardPersistenceFields;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("identity_wecom_department_link")
public class WecomDepartmentLinkEntity extends StandardPersistenceFields {
    @TableId("id")
    private String id;
    @TableField("wecom_department_id")
    private String wecomDepartmentId;
    @TableField("local_department_id")
    private String localDepartmentId;
    @TableField("linked_by_user_id")
    private String linkedByUserId;
    @Version
    @TableField("version")
    private int version;
}
