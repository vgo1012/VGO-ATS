package com.ezaigc.hr.talent.domain.response;

import lombok.Value;
import lombok.ToString;

/** 仅包含已授权面试所需的姓名、固定 JD 原文和固定简历版本信息。 */
@Value
public class InterviewMaterialResponse {
  String interviewId;
  String candidateId;
  @ToString.Exclude String candidateName;
  String jdId;
  String jdVersionId;
  int jdVersionNo;
  String jdTitle;
  String jdCode;
  @ToString.Exclude String jdRawText;
  String resumeVersionId;
  int resumeVersionNo;
  @ToString.Exclude String resumeFileName;
  String resumeMediaType;
  long resumeSizeBytes;
}
