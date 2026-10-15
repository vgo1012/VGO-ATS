package com.ezaigc.hr.recruiting.recruitment.domain.response;

import lombok.Getter;

/** 当前用户对指定 JD 版本的招聘流程处理资格。 */
@Getter
public class RecruitmentProcessingAccessResponse {
  private final boolean processingAllowed;

  public RecruitmentProcessingAccessResponse(boolean processingAllowed) {
    this.processingAllowed = processingAllowed;
  }
}
