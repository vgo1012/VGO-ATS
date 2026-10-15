package com.ezaigc.hr.talent.domain.entity;

import lombok.Data;

/** 当前简历业务版本的批量查询投影。 */
@Data
public class CandidateResumeRow {
  private String candidateId;
  private String resumeVersionId;
  private String jdVersionId;
  private String defaultMatchRunId;
}
