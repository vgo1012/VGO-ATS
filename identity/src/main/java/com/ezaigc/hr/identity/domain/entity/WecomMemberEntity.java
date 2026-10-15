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
@TableName("identity_wecom_member")
public class WecomMemberEntity extends StandardPersistenceFields {
    @TableId("id")
    private String id;
    @TableField("corp_id")
    private String corpId;
    @TableField("user_id")
    private String userId;
    @TableField("name")
    private String name;
    @TableField("position")
    private String position;
    @TableField("business_email")
    private String businessEmail;
    @TableField("source_status")
    private Integer sourceStatus;
    @TableField("department_ids_json")
    private String departmentIdsJson;
    @TableField("direct_leader_ids_json")
    private String directLeaderIdsJson;
    @TableField("directory_status")
    private String directoryStatus;
    @TableField("changed_batch_id")
    private String changedBatchId;
    @Version
    @TableField("version")
    private int version;
}
