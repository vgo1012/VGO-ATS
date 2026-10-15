package com.ezaigc.hr.identity.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("identity_user_account_history")
public class AccountHistory extends StandardVersionedPersistenceFields {

    /**
     * 主键 ID。
     */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /**
     * 用户 ID。
     */
    @TableField("user_id")
    private String userId;
    /**
     * 变更类型。
     */
    @TableField("change_type")
    private String changeType;
    /**
     * 变更前数据快照（JSON）。
     */
    @TableField("before_snapshot")
    private String beforeSnapshot;
    /**
     * 变更后数据快照（JSON）。
     */
    @TableField("after_snapshot")
    private String afterSnapshot;
    /**
     * 操作原因。
     */
    @TableField("reason")
    private String reason;
    /**
     * 实际操作人用户 ID。
     */
    @TableField("actor_user_id")
    private String actorUserId;
    /**
     * 事件发生时间。
     */
    @TableField("occurred_at")
    private Instant occurredAt;
    /**
     * 账户版本。
     */
    @TableField("account_version")
    private long accountVersion;
}
