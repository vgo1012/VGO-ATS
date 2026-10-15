package com.ezaigc.hr.recruiting.activity.domain.response;

import java.time.Instant;
import java.util.List;
import lombok.Value;

@Value
public class ActivityRecordResponse {
  String id;
  String type;
  String title;
  String comment;
  Instant occurredAt;
  List<ActivityFactResponse> facts;
}
