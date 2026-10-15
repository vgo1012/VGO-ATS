package com.ezaigc.hr.recruiting.recruitment.domain.response;
import java.time.Instant;
import lombok.Value;
@Value
public class FollowUpPlanHistoryResponse {
  String id;
  String planId;
  String action;
  String reason;
  String actorUserId;
  Instant occurredAt;
  FollowUpPlanSnapshotResponse before;
  FollowUpPlanSnapshotResponse after;
}
