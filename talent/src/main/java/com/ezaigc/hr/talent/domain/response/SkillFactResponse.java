package com.ezaigc.hr.talent.domain.response;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SkillFactResponse {
  /** 主键 ID。 */
  private String id;
  /** 事实快照 ID。 */
  private String factSnapshotId;
  /** 任务条目业务键。 */
  private String itemKey;
  /** 技能名称。 */
  private String skillName;
  /** 等级名称。 */
  private String levelName;
  /** 年限值。 */
  private BigDecimal yearsValue;
}
