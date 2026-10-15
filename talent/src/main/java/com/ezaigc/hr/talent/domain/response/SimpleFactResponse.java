package com.ezaigc.hr.talent.domain.response;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SimpleFactResponse {
  /** 原文明示的日期精度，可为年、年月、完整日期或 PRESENT。 */
  private String startDateText;
  private String endDateText;
  /** 主键 ID。 */
  private String id;
  /** 事实快照 ID。 */
  private String factSnapshotId;
  /** 事实类型。 */
  private String factType;
  /** 任务条目业务键。 */
  private String itemKey;
  /** 名称值。 */
  private String nameValue;
  /** 等级值。 */
  private String levelValue;
  /** 签发方值。 */
  private String issuerValue;
  /** 开始日期。 */
  private LocalDate startDate;
  /** 结束日期。 */
  private LocalDate endDate;
  /** 原始事实数组下标；历史快照未记录时为空。 */
  private Integer sourceIndex;
}
