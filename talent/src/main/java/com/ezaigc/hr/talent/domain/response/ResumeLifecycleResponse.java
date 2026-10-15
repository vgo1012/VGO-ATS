package com.ezaigc.hr.talent.domain.response;
import java.time.Instant;
import lombok.Value;
@Value
public class ResumeLifecycleResponse {
  String cycleId;
  String phase;
  String warningLevel;
  Instant enteredAt;
  int elapsedWorkdays;
  Instant nextTransitionAt;
  Instant shareDueAt;
  String pauseReason;
  Instant sharedAt;
  String shareReason;
  String version;
}
