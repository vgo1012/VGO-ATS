package com.ezaigc.hr.recruiting.activity.domain.dto;

import java.time.Instant;
import lombok.Data;

@Data
public class ActivityRecordRowDTO {
  private String id;
  private String type;
  private String title;
  private Instant occurredAt;
  private String factsJson;
}
