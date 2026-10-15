package com.ezaigc.hr.talent.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 列表事实摘要的可读状态，不覆盖简历解析任务状态。 */
@Getter
@RequiredArgsConstructor
public enum ResumeSummaryFactsStatus implements CodeDescEnum {
  READY("已解析"),
  FORBIDDEN("摘要需授权"),
  NOT_READY("待解析"),
  NO_RESUME("暂无简历");

  private final String desc;
}
