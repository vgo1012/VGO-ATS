package com.ezaigc.hr.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.ezaigc.hr.recruiting.interview.api.NotificationDeliveryPort;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 仅在实库验收中替代渠道；没有网络行为，以闸门控制真实事务提交后的响应时序。 */
final class ControlledNotificationDelivery implements NotificationDeliveryPort {
  private final ConcurrentHashMap<String, Gate> gates = new ConcurrentHashMap<>();

  Gate control(String notificationId, DeliveryResult result) {
    var gate = new Gate(notificationId, result);
    assertThat(gates.putIfAbsent(notificationId, gate)).isNull();
    return gate;
  }

  @Override
  public DeliveryResult deliver(NotificationMessage message) {
    Gate gate = gates.get(message.notificationId());
    if (gate == null) return DeliveryResult.disabled("实库验收未启用任何外部通知渠道");
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    gate.calls.incrementAndGet();
    gate.entered.countDown();
    try {
      assertThat(gate.release.await(20, TimeUnit.SECONDS)).as("测试渠道必须被显式释放").isTrue();
      return gate.result;
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("测试渠道等待被中断", error);
    }
  }

  final class Gate implements AutoCloseable {
    private final String notificationId;
    private final DeliveryResult result;
    private final AtomicInteger calls = new AtomicInteger();
    private final CountDownLatch entered = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);

    private Gate(String notificationId, DeliveryResult result) {
      this.notificationId = notificationId;
      this.result = result;
    }

    void awaitEntered() throws InterruptedException {
      assertThat(entered.await(10, TimeUnit.SECONDS)).as("真实HTTP请求必须到达测试渠道").isTrue();
    }

    void release() { release.countDown(); }

    int calls() { return calls.get(); }

    @Override
    public void close() {
      release();
      gates.remove(notificationId, this);
    }
  }
}
