package com.ezaigc.hr.common.infrastructure.outbox;

import com.ezaigc.hr.common.infrastructure.task.AsyncTaskAvailable;

/** 消费端按事件协议校验持久信封，返回不含业务正文的本地唤醒提示。 */
public interface OutboxNotificationVerifier {
  boolean supports(String eventType);

  AsyncTaskAvailable verifyNotification(OutboxEnvelope envelope);
}
