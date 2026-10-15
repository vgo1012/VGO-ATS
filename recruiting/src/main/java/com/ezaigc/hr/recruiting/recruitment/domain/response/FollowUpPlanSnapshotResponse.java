package com.ezaigc.hr.recruiting.recruitment.domain.response;
import java.time.Instant;
import lombok.Value;
@Value
public class FollowUpPlanSnapshotResponse {
  Instant startsAt;
  Instant endsAt;
  String channel;
  String note;
  String status;
  String resultReason;
}
