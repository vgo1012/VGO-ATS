package com.ezaigc.hr.recruiting.activity.domain.response;

import java.time.Instant;
import java.util.Map;
import lombok.Data;

@Data
public class ActivityFactResponse {
  private String sourceType;
  private String sourceId;
  private String title;
  private String action;
  private String comment;
  private String actorUserId;
  private String actorName;
  private Instant occurredAt;
  private String interviewId;
  private String companyId;
  private Map<String, Object> details;
}
