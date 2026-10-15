package com.ezaigc.hr.intelligence.matching.domain.response;

import lombok.Value;

/** 退回结果；幂等重放不重新修改当前准入状态。 */
@Value
public class ScreeningReturnBatchResponse {
  int requestedCount;
  int returnedCount;
  int alreadyReturnedCount;
}
