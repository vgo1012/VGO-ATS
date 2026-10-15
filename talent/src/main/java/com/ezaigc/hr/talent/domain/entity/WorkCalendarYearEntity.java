package com.ezaigc.hr.talent.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("talent_work_calendar_year")
public class WorkCalendarYearEntity extends StandardVersionedPersistenceFields {
  @TableId(value = "calendar_year", type = IdType.INPUT)
  private Integer calendarYear;
  @TableField("published_revision_id")
  private String publishedRevisionId;
}
