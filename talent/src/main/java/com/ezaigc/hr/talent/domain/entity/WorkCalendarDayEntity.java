package com.ezaigc.hr.talent.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("talent_work_calendar_day")
public class WorkCalendarDayEntity extends StandardVersionedPersistenceFields {
  @TableId(value = "id", type = IdType.INPUT)
  private String id;
  @TableField("calendar_revision_id")
  private String calendarRevisionId;
  @TableField("calendar_date")
  private LocalDate calendarDate;
  @TableField("day_type")
  private String dayType;
  @TableField("description")
  private String description;
}
