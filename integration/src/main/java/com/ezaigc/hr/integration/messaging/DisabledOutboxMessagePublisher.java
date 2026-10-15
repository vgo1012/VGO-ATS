package com.ezaigc.hr.integration.messaging;

import com.ezaigc.hr.common.infrastructure.outbox.OutboxEnvelope;
import com.ezaigc.hr.common.infrastructure.outbox.OutboxMessagePublisher;
import com.ezaigc.hr.common.infrastructure.outbox.OutboxPublishException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 未配置 Broker 时显式禁用，不能把未发送的通知当成成功。 */
@Component
@ConditionalOnProperty(name = "hr.messaging.mode", havingValue = "disabled", matchIfMissing = true)
public class DisabledOutboxMessagePublisher implements OutboxMessagePublisher {
    @Override
    public void publish(OutboxEnvelope envelope) {
        throw new OutboxPublishException("OUTBOX_DISABLED", "消息发布未启用，请先配置消息服务", false, null);
    }
}
