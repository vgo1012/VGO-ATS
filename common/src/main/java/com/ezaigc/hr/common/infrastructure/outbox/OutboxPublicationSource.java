package com.ezaigc.hr.common.infrastructure.outbox;

/** 一个持久 Outbox 来源每次最多领取并发布一条消息，供共享发布 Worker 公平轮询。 */
public interface OutboxPublicationSource {
  int publishOne();
}
