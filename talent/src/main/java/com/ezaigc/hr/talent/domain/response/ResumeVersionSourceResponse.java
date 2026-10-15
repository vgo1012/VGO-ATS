package com.ezaigc.hr.talent.domain.response;

import lombok.Value;

/** 历史简历版本及其实际收件来源；来源记录缺失时 source 为 null。 */
@Value
public class ResumeVersionSourceResponse {
  String resumeVersionId;
  ResumeSourceResponse source;
}
