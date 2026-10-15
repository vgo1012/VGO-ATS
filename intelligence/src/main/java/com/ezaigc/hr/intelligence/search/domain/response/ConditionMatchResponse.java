package com.ezaigc.hr.intelligence.search.domain.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConditionMatchResponse {
  /** 业务编码。 */
  private String code;
  /** 期望。 */
  private String expected;
  /** 是否匹配。 */
  private boolean matched;
  private List<String> matchedValues;
}
