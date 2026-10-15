package com.ezaigc.hr.recruiting.activity.domain.dto;

import java.time.Instant;
import lombok.Value;

@Value
public class ActivityRecordQuery {
  String applicationId;
  String candidateId;
  String type;
  Instant cursorTime;
  String cursorId;
  int limit;
}
