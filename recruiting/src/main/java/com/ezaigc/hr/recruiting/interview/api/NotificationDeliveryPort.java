package com.ezaigc.hr.recruiting.interview.api;

import lombok.Value;
import lombok.experimental.Accessors;

/**
 * 通知交付模块能力端口。
 *
 * <p>实现方必须把 {@link NotificationMessage#notificationId()} 作为渠道侧幂等键；同一通知在超时、进程崩溃或租约恢复后可能被再次提交，重复调用不得重复送达。实现方网络超时必须短于五分钟发送租约，并且返回内容不得包含凭据或其他秘密。
 */
public interface NotificationDeliveryPort {
  DeliveryResult deliver(NotificationMessage message);

  @Value
  @Accessors(fluent = true)
  class NotificationMessage {
    String notificationId;
    String channel;
    String recipientReference;
    String subject;
    String body;
  }

  @Value
  @Accessors(fluent = true)
  class DeliveryResult {
    boolean delivered;
    String externalMessageId;
    String errorCode;
    String errorDetail;

    public static DeliveryResult disabled(String detail) {
      return new DeliveryResult(false, null, "DISABLED_NOT_CONFIGURED", detail);
    }
  }
}
