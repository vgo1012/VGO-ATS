package com.ezaigc.hr.intelligence.matching.domain.dto;

import lombok.Data;

/** 当前岗位版本选择的评分版本，技术版本用于防止覆盖他人修改。 */
@Data
public class ScoringBindingDTO {
  private String jdVersionId;
  private String planVersionId;
  private long version;
}
