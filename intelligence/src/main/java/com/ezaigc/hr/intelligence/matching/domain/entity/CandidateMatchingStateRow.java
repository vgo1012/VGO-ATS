package com.ezaigc.hr.intelligence.matching.domain.entity;

import lombok.Data;

/** 个人池岗位状态批量读取投影。 */
@Data
public class CandidateMatchingStateRow {
  private String candidateId;
  private String resumeVersionId;
  private String jdVersionId;
  private String matchRunId;
}
