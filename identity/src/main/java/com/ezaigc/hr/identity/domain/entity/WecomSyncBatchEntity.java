package com.ezaigc.hr.identity.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.ezaigc.hr.common.infrastructure.persistence.StandardPersistenceFields;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("identity_wecom_sync_batch")
public class WecomSyncBatchEntity extends StandardPersistenceFields {
    @TableId("id")
    private String id;
    @TableField("corp_id")
    private String corpId;
    @TableField("request_key")
    private String requestKey;
    @TableField("trigger_type")
    private String triggerType;
    @TableField("actor_user_id")
    private String actorUserId;
    @TableField("retry_of_batch_id")
    private String retryOfBatchId;
    @TableField("scope_version")
    private String scopeVersion;
    @TableField("status")
    private String status;
    @TableField("requested_at")
    private Instant requestedAt;
    @TableField("started_at")
    private Instant startedAt;
    @TableField("finished_at")
    private Instant finishedAt;
    @TableField("lease_until")
    private Instant leaseUntil;
    @TableField("run_token")
    private String runToken;
    @TableField("error_code")
    private String errorCode;
    @TableField("safe_message")
    private String safeMessage;
    @TableField("department_count")
    private int departmentCount;
    @TableField("member_count")
    private int memberCount;
    @TableField("inserted_count")
    private int insertedCount;
    @TableField("changed_count")
    private int changedCount;
    @TableField("missing_count")
    private int missingCount;
    @Version
    @TableField("version")
    private int version;
}
