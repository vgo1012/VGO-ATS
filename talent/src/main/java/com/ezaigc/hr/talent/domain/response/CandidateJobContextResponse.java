package com.ezaigc.hr.talent.domain.response;

import lombok.Value;

/** 具体岗位和简历版本的工作进度，不将 AI 筛选当作招聘决定。 */
@Value
public class CandidateJobContextResponse {
  String jdId;
  String jdVersionId;
  String title;
  String jdCode;
  int versionNo;
  String resumeVersionId;
  String status;
  String stage;
  String applicationId;
  String matchRunId;
}
