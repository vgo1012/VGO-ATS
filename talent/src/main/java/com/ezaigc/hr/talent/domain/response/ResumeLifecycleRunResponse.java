package com.ezaigc.hr.talent.domain.response;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Value;
@Value
public class ResumeLifecycleRunResponse {
  String id;
  LocalDate settlementDate;
  String status;
  int scannedCount;
  int succeededCount;
  int sharedCount;
  int blockedCount;
  int failedCount;
  String errorCode;
  Instant startedAt;
  Instant finishedAt;
}
