package com.ezaigc.hr.common.infrastructure.outbox;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import lombok.Value;

/** 不可变通知信封，只携带已持久任务引用，不复制简历正文、联系方式或模型载荷。 */
@Value
public class OutboxEnvelope {
  String eventId;
  String eventType;
  int eventVersion;
  String aggregateType;
  String aggregateId;
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  Instant occurredAt;
  String traceId;
  ActorSnapshot actorSnapshot;
  TaskPayload payload;

  @Value
  public static class ActorSnapshot {
    String actorUserId;
    String ownerUserId;
    String ownerDepartmentId;
  }

  @Value
  public static class TaskPayload {
    String taskId;
    String taskType;
  }
}
