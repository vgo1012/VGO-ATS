package com.ezaigc.hr.talent.domain.entity;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("talent_resume_lifecycle")
public class ResumeLifecycle extends StandardVersionedPersistenceFields {
  @TableId(value = "id", type = IdType.INPUT) private String id;
  @TableField("candidate_id")
  private String candidateId;
  @TableField("resume_version_id")
  private String resumeVersionId;
  @TableField("cycle_no")
  private int cycleNo;
  @TableField("active_marker")
  private String activeMarker;
  @TableField("entered_at")
  private Instant enteredAt;
  @TableField("phase")
  private String phase;
  @TableField("warning_level")
  private String warningLevel;
  @TableField("elapsed_workdays")
  private int elapsedWorkdays;
  @TableField("next_check_at")
  private Instant nextCheckAt;
  @TableField("share_due_at")
  private Instant shareDueAt;
  @TableField("settled_through")
  private LocalDate settledThrough;
  @TableField("calendar_revisions")
  private String calendarRevisions;
  @TableField("pause_reason")
  private String pauseReason;
  @TableField("shared_at")
  private Instant sharedAt;
  @TableField("closed_reason")
  private String closedReason;
}
