package com.ezaigc.hr.recruiting.recruitment.domain.dto;
import java.time.Instant;
import lombok.Value;
@Value
public class FollowUpPlanDTO {
  Instant startsAt;
  Instant endsAt;
  String channel;
  String note;
  long version;
}
