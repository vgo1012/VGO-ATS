package com.ezaigc.hr.talent.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("talent_work_calendar_revision")
public class WorkCalendarRevisionEntity extends StandardVersionedPersistenceFields {
  @TableId(value = "id", type = IdType.INPUT)
  private String id;
  @TableField("calendar_year")
  private Integer calendarYear;
  @TableField("revision")
  private Integer revision;
  @TableField("status")
  private String status;
  @TableField("source_note")
  private String sourceNote;
  @TableField("published_by")
  private String publishedBy;
  @TableField("published_name")
  private String publishedName;
  @TableField("publish_reason")
  private String publishReason;
  @TableField("published_at")
  private Instant publishedAt;
  @TableField("recalculation_id")
  private String recalculationId;
  @TableField("preview_id")
  private String previewId;
  @TableField("preview_fingerprint")
  private String previewFingerprint;
  @TableField("preview_published_id")
  private String previewPublishedId;
  @TableField("preview_expires_at")
  private Instant previewExpiresAt;
}
