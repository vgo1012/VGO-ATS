package com.ezaigc.hr.recruiting.recruitment.domain.response;
import java.time.Instant;
import lombok.Value;
@Value
public class FollowUpPlanResponse {
  String id;
  String applicationId;
  String candidateId;
  String jdId;
  String jdVersionId;
  String recruitmentType;
  String clientId;
  String clientName;
  String ownerUserId;
  Instant startsAt;
  Instant endsAt;
  String channel;
  String note;
  String status;
  String resultReason;
  String version;
  Instant createTime;
  Instant updateTime;
}
