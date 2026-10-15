package com.ezaigc.hr.talent.domain.response;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class EducationFactResponse {
  /** 原文明示的日期精度，可为年、年月、完整日期或 PRESENT。 */
  private String startDateText;
  private String endDateText;
  /** 主键 ID。 */
  private String id;
  /** 事实快照 ID。 */
  private String factSnapshotId;
  /** 任务条目业务键。 */
  private String itemKey;
  /** 学校名称。 */
  private String school;
  /** 学历或学位名称。 */
  private String degreeName;
  /** 专业名称。 */
  private String major;
  /** 开始日期。 */
  private LocalDate startDate;
  /** 结束日期。 */
  private LocalDate endDate;
  /** 学习形式。 */
  private String learningMode;
}
