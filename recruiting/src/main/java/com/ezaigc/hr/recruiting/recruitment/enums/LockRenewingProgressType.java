package com.ezaigc.hr.recruiting.recruitment.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 允许续期候选人跟进锁的进展类型。 */
@Getter
@RequiredArgsConstructor
public enum LockRenewingProgressType implements CodeDescEnum {
  CANDIDATE_REPLY("候选人回复"),
  APPLICATION_ADVANCED("招聘流程推进"),
  INTERVIEW_SCHEDULED("面试已安排"),
  INTERVIEW_COMPLETED("面试已完成"),
  CLIENT_SUBMISSION_SENT("客户推荐已发送"),
  OFFER_ADVANCED("Offer 已推进");

  private final String desc;
  public static boolean supports(String code) {
    return Arrays.stream(values()).anyMatch(value -> value.matches(code));
  }
}
