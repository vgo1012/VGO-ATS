package com.ezaigc.hr.talent.domain.response;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ProjectFactResponse {
  /** 原文明示的日期精度，可为年、年月、完整日期或 PRESENT。 */
  private String startDateText;
  private String endDateText;
  /** 主键 ID。 */
  private String id;
  /** 事实快照 ID。 */
  private String factSnapshotId;
  /** 任务条目业务键。 */
  private String itemKey;
  /** 项目名称。 */
  private String projectName;
  /** 角色名称。 */
  private String roleName;
  /** 开始日期。 */
  private LocalDate startDate;
  /** 结束日期。 */
  private LocalDate endDate;
  /** 描述文本。 */
  private String descriptionText;
  /** 技术栈列表（JSON）。 */
  private String technologiesJson;
  /** 工作成果列表（JSON）。 */
  private String achievementsJson;
  /** 原始事实数组下标，历史快照未标注时为空。 */
  private Integer sourceIndex;
}
