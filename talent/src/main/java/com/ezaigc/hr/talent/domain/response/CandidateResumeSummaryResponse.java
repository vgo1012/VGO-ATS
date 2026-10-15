package com.ezaigc.hr.talent.domain.response;

import com.ezaigc.hr.talent.enums.ResumeSummaryFactsStatus;
import lombok.Value;

/** 本页候选人的简历列表投影；facts 不含描述正文和证据原文，不能代替详情。 */
@Value
public class CandidateResumeSummaryResponse {
  String candidateId;
  ResumeVersionResponse resumeVersion;
  ResumeSourceResponse source;
  FactBundleResponse facts;
  /** READY、FORBIDDEN、NOT_READY 或 NO_RESUME。 */
  ResumeSummaryFactsStatus factsStatus;
}
