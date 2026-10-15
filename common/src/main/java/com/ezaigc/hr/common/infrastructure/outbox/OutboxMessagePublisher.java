package com.ezaigc.hr.common.infrastructure.outbox;

/** Broker 适配端口；返回即表示持久消息已经得到肯定确认且没有被路由退回。 */
public interface OutboxMessagePublisher {
    void publish(OutboxEnvelope envelope);
}
