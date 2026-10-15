package com.ezaigc.hr.intelligence.matching.domain.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ControlledCriterionResponse {
  /** 主键 ID。 */
  private String id;
  /** 匹配方案版本 ID。 */
  private String planVersionId;
  /** 受控条件编码。 */
  private String criterionCode;
  private String criterionCodeDesc;
  /** 条件满足级别。 */
  private String level;
  private String levelDesc;
  /** 操作符。 */
  private String operator;
  /** 期望值（JSON）。 */
  private String expectedValueJson;
  /** 附加分值。 */
  private int bonusPoints;
  /** 操作原因。 */
  private String reason;
  /** 证据使用策略。 */
  private String evidencePolicy;
  /** 是否允许 HR 选择。 */
  private boolean selectableByHr;
  /** 顺序号。 */
  private int sequenceNo;
}
