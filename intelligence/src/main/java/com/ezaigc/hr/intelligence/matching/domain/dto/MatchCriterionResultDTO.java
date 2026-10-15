package com.ezaigc.hr.intelligence.matching.domain.dto;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MatchCriterionResultDTO {
  private String definitionId;
  /** 业务编码。 */
  private String code;
  /** 条件满足级别。 */
  private String level;
  /** 当前状态。 */
  private String status;
  /** 是否已选中。 */
  private boolean selected;
  /** 配置附加分。 */
  private int configuredBonus;
  /** 实际授予附加分。 */
  private int awardedBonus;
  /** 操作原因。 */
  private String reason;
  /** 实际。 */
  private List<String> actual;
  /** 期望。 */
  private List<String> expected;
  public MatchCriterionResultDTO(
      String definitionId,
      String code,
      String level,
      String status,
      boolean selected,
      int configuredBonus,
      int awardedBonus,
      String reason,
      List<String> actual,
      List<String> expected) {
    this.definitionId = definitionId;
    this.code = code;
    this.level = level;
    this.status = status;
    this.selected = selected;
    this.configuredBonus = configuredBonus;
    this.awardedBonus = awardedBonus;
    this.reason = reason;
    this.actual = List.copyOf(actual);
    this.expected = List.copyOf(expected);
  }
}
