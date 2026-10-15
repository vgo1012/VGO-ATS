package com.ezaigc.hr.intelligence.matching.domain.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchDimensionScoreDTO {
  /** 当前状态。 */
  private String status;
  /** 得分。 */
  private BigDecimal score;
  /** 结果说明。 */
  private String explanation;
  /** 亮点列表。 */
  private List<String> highlights;
}
