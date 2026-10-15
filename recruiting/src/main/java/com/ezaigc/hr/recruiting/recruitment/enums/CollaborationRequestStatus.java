package com.ezaigc.hr.recruiting.recruitment.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 跟进协作请求状态。 */
@Getter
@RequiredArgsConstructor
public enum CollaborationRequestStatus implements CodeDescEnum {
  REQUESTED("待处理"),
  ACCEPTED("已接受"),
  REJECTED("已拒绝"),
  CANCELLED("已取消");

  private final String desc;
}
