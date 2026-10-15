package com.ezaigc.hr.recruiting.recruitment.domain.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.ezaigc.hr.common.infrastructure.persistence.StandardPersistenceFields;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("recruitment_follow_up_plan")
public class FollowUpPlan extends StandardPersistenceFields {
  @TableId(value = "id", type = IdType.INPUT) private String id;
  @TableField("application_id") private String applicationId;
  @TableField("starts_at") private Instant startsAt;
  @TableField("ends_at") private Instant endsAt;
  @TableField("channel") private String channel;
  @TableField("note") private String note;
  @TableField("status") private String status;
  @TableField("result_reason") private String resultReason;
  @Version @TableField("version") private long version;
}
