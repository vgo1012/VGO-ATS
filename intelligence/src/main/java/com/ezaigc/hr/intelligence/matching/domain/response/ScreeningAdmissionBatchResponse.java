package com.ezaigc.hr.intelligence.matching.domain.response;

import java.util.List;
import lombok.Value;

/** 批量准入结果。 */
@Value
public class ScreeningAdmissionBatchResponse {
  int requestedCount;
  int admittedCount;
  int alreadyAdmittedCount;
  List<ScreeningCandidateResponse> items;
}
