package com.ezaigc.hr.intelligence.matching.domain.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MatchDimensionResultDTO {
  /** 业务编码。 */
  private String code;
  /** 是否启用。 */
  private boolean enabled;
  /** 配置权重。 */
  private int configuredWeight;
  /** 当前状态。 */
  private String status;
  /** 得分。 */
  private BigDecimal score;
  /** 结果说明。 */
  private String explanation;
  /** 亮点列表。 */
  private List<String> highlights;

  public MatchDimensionResultDTO(
      String code,
      boolean enabled,
      int configuredWeight,
      String status,
      BigDecimal score,
      String explanation,
      List<String> highlights) {
    this.code = code;
    this.enabled = enabled;
    this.configuredWeight = configuredWeight;
    this.status = status;
    this.score = score;
    this.explanation = explanation;
    this.highlights = List.copyOf(highlights);
  }
}
