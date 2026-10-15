package com.ezaigc.hr.recruiting.interview.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.ezaigc.hr.common.infrastructure.persistence.StandardPersistenceFields;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("interview_external_calendar_projection")
public class InterviewExternalCalendarProjection extends StandardPersistenceFields {
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    @TableField("interview_id") private String interviewId;
    @TableField("provider") private String provider;
    @TableField("desired_type") private String desiredType;
    @TableField(value = "active_type", updateStrategy = FieldStrategy.ALWAYS) private String activeType;
    @TableField("status") private String status;
    @TableField("operation_revision") private long operationRevision;
    @TableField(value = "title", updateStrategy = FieldStrategy.ALWAYS) private String title;
    @TableField(value = "description", updateStrategy = FieldStrategy.ALWAYS) private String description;
    @TableField(value = "admin_user_id", updateStrategy = FieldStrategy.ALWAYS) private String adminUserId;
    @TableField("invitee_user_ids_json") private String inviteeUserIdsJson;
    @TableField("additional_invitee_user_ids_json") private String additionalInviteeUserIdsJson;
    @TableField("start_at") private Instant startAt;
    @TableField("end_at") private Instant endAt;
    @TableField("timezone_id") private String timezoneId;
    @TableField(value = "location_text", updateStrategy = FieldStrategy.ALWAYS) private String locationText;
    @TableField("reminder_seconds") private int reminderSeconds;
    @TableField("waiting_room_enabled") private boolean waitingRoomEnabled;
    @TableField("allow_enter_before_host") private boolean allowEnterBeforeHost;
    @TableField("enter_mute_enabled") private boolean enterMuteEnabled;
    @TableField(value = "external_object_id", updateStrategy = FieldStrategy.ALWAYS) private String externalObjectId;
    @TableField(value = "meeting_code", updateStrategy = FieldStrategy.ALWAYS) private String meetingCode;
    @TableField(value = "meeting_link", updateStrategy = FieldStrategy.ALWAYS) private String meetingLink;
    @TableField(value = "remote_status", updateStrategy = FieldStrategy.ALWAYS) private String remoteStatus;
    @TableField("attempt_count") private int attemptCount;
    @TableField("next_attempt_at") private Instant nextAttemptAt;
    // 收尾必须真正写入 NULL，不能让默认非空更新策略保留旧租约/意图/对象。
    @TableField(value = "lease_token", updateStrategy = FieldStrategy.ALWAYS) private String leaseToken;
    @TableField(value = "lease_until", updateStrategy = FieldStrategy.ALWAYS) private Instant leaseUntil;
    @TableField(value = "create_intent_id", updateStrategy = FieldStrategy.ALWAYS) private String createIntentId;
    @TableField(value = "last_error_code", updateStrategy = FieldStrategy.ALWAYS) private String lastErrorCode;
    @TableField(value = "last_error_detail", updateStrategy = FieldStrategy.ALWAYS) private String lastErrorDetail;
    @TableField("last_synced_at") private Instant lastSyncedAt;
    @Version
    @TableField("version") private long version;
}
