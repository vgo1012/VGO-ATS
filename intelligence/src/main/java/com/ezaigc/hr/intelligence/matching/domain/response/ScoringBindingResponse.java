package com.ezaigc.hr.intelligence.matching.domain.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ScoringBindingResponse {
  private String jdVersionId;
  private String planVersionId;
  private String version;
}
