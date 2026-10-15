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
@TableName("identity_wecom_department")
public class WecomDepartmentEntity extends StandardPersistenceFields {
    @TableId("id")
    private String id;
    @TableField("corp_id")
    private String corpId;
    @TableField("department_id")
    private long departmentId;
    @TableField("parent_id")
    private long parentId;
    @TableField("name")
    private String name;
    @TableField("source_order")
    private long sourceOrder;
    @TableField("parent_outside_scope")
    private boolean parentOutsideScope;
    @TableField("changed_batch_id")
    private String changedBatchId;
    @Version
    @TableField("version")
    private int version;
}
