package com.ezaigc.hr.talent.domain.response;

import lombok.Value;

/** 指定简历版本的轻量事实投影，不含描述正文和证据原文，不能代替详情。 */
@Value
public class ResumeVersionFactSummaryResponse {
  String resumeVersionId;
  FactBundleResponse facts;
}
