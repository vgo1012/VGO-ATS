package com.ezaigc.hr.intelligence.matching.domain.response;

import java.math.BigDecimal;
import lombok.Value;

/** 已冻结的公司默认评分摘要。 */
@Value
public class DefaultScreeningResultResponse {
  BigDecimal professionalScore;
  int bonusScore;
  BigDecimal referenceScore;
  String mustTier;
}
