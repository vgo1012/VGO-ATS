package com.ezaigc.hr.recruiting.submission.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 客户推荐交付状态。 */
@Getter
@RequiredArgsConstructor
public enum SubmissionDeliveryStatus implements CodeDescEnum {
  DRAFT("草稿"),
  SENT("已发送"),
  WITHDRAWN("已撤回");

  private final String desc;
}
