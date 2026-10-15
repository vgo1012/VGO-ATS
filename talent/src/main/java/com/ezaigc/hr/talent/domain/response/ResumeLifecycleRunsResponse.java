package com.ezaigc.hr.talent.domain.response;
import java.time.LocalDate;
import java.util.List;
import lombok.Value;
@Value
public class ResumeLifecycleRunsResponse {
  boolean enabled;
  LocalDate lastSettlementDate;
  boolean recalculationPending;
  List<ResumeLifecycleRunResponse> runs;
}
