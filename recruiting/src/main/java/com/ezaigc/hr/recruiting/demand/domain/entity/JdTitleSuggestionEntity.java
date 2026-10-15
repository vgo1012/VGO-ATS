package com.ezaigc.hr.recruiting.demand.domain.entity;

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
@TableName("demand_jd_title_suggestion")
public class JdTitleSuggestionEntity extends StandardVersionedPersistenceFields {
  @TableId(value = "id", type = IdType.INPUT) private String id;
  @TableField("name") private String name;
  @TableField("status") private String status;
  @TableField("submitted_by_user_id") private String submittedByUserId;
  @TableField("reviewed_by_user_id") private String reviewedByUserId;
  @TableField("reviewed_at") private Instant reviewedAt;
  @TableField("rejection_reason") private String rejectionReason;
}
