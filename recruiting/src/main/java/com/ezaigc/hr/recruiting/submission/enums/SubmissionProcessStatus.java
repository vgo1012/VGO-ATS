package com.ezaigc.hr.recruiting.submission.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 客户推荐处理状态。 */
@Getter
@RequiredArgsConstructor
public enum SubmissionProcessStatus implements CodeDescEnum {
  NOT_STARTED("未开始"),
  SCREENING("客户筛选中"),
  INTERVIEWING("客户面试中"),
  ACCEPTED("客户已接受"),
  REJECTED("客户已拒绝"),
  EXPIRED("已过期");

  private final String desc;
  public static boolean supports(String code) {
    return Arrays.stream(values()).anyMatch(value -> value.matches(code));
  }

  public static boolean isClientWorkflowActive(String code) {
    return SCREENING.matches(code) || INTERVIEWING.matches(code);
  }
}
