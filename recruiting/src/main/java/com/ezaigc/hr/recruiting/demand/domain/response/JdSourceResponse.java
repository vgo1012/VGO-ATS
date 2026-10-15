package com.ezaigc.hr.recruiting.demand.domain.response;

import java.time.Instant;
import lombok.Data;

@Data
public class JdSourceResponse {
  private String id;
  private String jdVersionId;
  private String sourceType;
  private String sourceLabel;
  private String mailSourceId;
  private String ownerUserId;
  private Instant createTime;
}
