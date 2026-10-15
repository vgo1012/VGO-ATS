package com.ezaigc.hr.common.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import com.ezaigc.hr.common.infrastructure.concurrent.CoalescingTaskDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class PlatformExecutorConfigurationTest {

  private final PlatformExecutorConfiguration configuration =
      new PlatformExecutorConfiguration();

  @Test
  void createsDistinctBoundedExecutorsForEachWorkload() {
    PlatformExecutorProperties properties = new PlatformExecutorProperties();
    ThreadPoolTaskExecutor ai =
        (ThreadPoolTaskExecutor) configuration.aiTaskExecutor(properties);
    ThreadPoolTaskExecutor search =
        (ThreadPoolTaskExecutor) configuration.searchTaskExecutor(properties);
    ThreadPoolTaskExecutor directory =
        (ThreadPoolTaskExecutor) configuration.directoryTaskExecutor(properties);
    ThreadPoolTaskExecutor outbox =
        (ThreadPoolTaskExecutor) configuration.outboxTaskExecutor(properties);
    ai.initialize();
    search.initialize();
    directory.initialize();
    outbox.initialize();
    try {
      assertThat(ai).isNotSameAs(search);
      assertThat(directory).isNotSameAs(ai).isNotSameAs(search);
      assertThat(directory.getCorePoolSize()).isEqualTo(1);
      assertThat(directory.getMaxPoolSize()).isEqualTo(1);
      assertThat(directory.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(1);
      assertThat(ai.getCorePoolSize()).isEqualTo(13);
      assertThat(ai.getMaxPoolSize()).isEqualTo(13);
      assertThat(ai.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(32);
      assertThat(search.getCorePoolSize()).isEqualTo(2);
      assertThat(search.getMaxPoolSize()).isEqualTo(2);
      assertThat(search.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(8);
      assertThat(outbox.getCorePoolSize()).isEqualTo(2);
      assertThat(outbox.getMaxPoolSize()).isEqualTo(2);
      assertThat(outbox.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(4);
    } finally {
      ai.shutdown();
      search.shutdown();
      directory.shutdown();
      outbox.shutdown();
    }
  }

  @Test
  void blockedMailboxDoesNotDelayMaintenanceAndBothQueuesRemainBounded() throws InterruptedException {
    PlatformExecutorProperties properties = new PlatformExecutorProperties();
    ThreadPoolTaskExecutor mail = (ThreadPoolTaskExecutor) configuration.mailTaskExecutor(properties);
    ThreadPoolTaskExecutor maintenance =
        (ThreadPoolTaskExecutor) configuration.maintenanceTaskExecutor(properties);
    CountDownLatch mailStarted = new CountDownLatch(1);
    CountDownLatch releaseMail = new CountDownLatch(1);
    CountDownLatch maintained = new CountDownLatch(1);
    mail.initialize();
    maintenance.initialize();
    try {
      assertThat(mail).isNotSameAs(maintenance);
      assertThat(mail.getCorePoolSize()).isEqualTo(1);
      assertThat(mail.getMaxPoolSize()).isEqualTo(1);
      assertThat(mail.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(1);
      mail.execute(() -> {
        mailStarted.countDown();
        try {
          releaseMail.await();
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
        }
      });
      assertThat(mailStarted.await(5, TimeUnit.SECONDS)).isTrue();
      maintenance.execute(maintained::countDown);
      assertThat(maintained.await(5, TimeUnit.SECONDS)).isTrue();
    } finally {
      releaseMail.countDown();
      mail.shutdown();
      maintenance.shutdown();
    }
  }

  @Test
  void createsDedicatedSchedulerAndRejectsUnsafeSettings() {
    PlatformExecutorProperties properties = new PlatformExecutorProperties();
    ThreadPoolTaskScheduler scheduler = configuration.taskScheduler(properties);
    assertThat(scheduler.getPoolSize()).isEqualTo(4);

    properties.setShutdownAwait(Duration.ZERO);
    assertThatThrownBy(() -> configuration.maintenanceTaskExecutor(properties))
        .isInstanceOf(IllegalArgumentException.class);

    properties.setShutdownAwait(Duration.ofSeconds(30));
    properties.getMaintenance().setCoreSize(3);
    properties.getMaintenance().setMaxSize(2);
    assertThatThrownBy(() -> configuration.maintenanceTaskExecutor(properties))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void runsFiftyIntakeCallsAlongsideReservedWorkloads() throws InterruptedException {
    PlatformExecutorProperties properties = new PlatformExecutorProperties();
    properties.setAi(new PlatformExecutorProperties.Pool(66, 66, 66));
    properties.setAiWorkloadSlots(new PlatformExecutorProperties.AiWorkloadSlots(6, 50, 10));
    assertThat(properties.hasCompleteAiWorkloadAllocation()).isTrue();
    ThreadPoolTaskExecutor ai =
        (ThreadPoolTaskExecutor) configuration.aiTaskExecutor(properties);
    ai.initialize();
    CountDownLatch started = new CountDownLatch(66);
    CountDownLatch release = new CountDownLatch(1);
    Runnable blockedCall = () -> {
      started.countDown();
      try {
        release.await();
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
      }
    };
    CoalescingTaskDispatcher intake = new CoalescingTaskDispatcher(50);
    try {
      for (int i = 0; i < 50; i++) {
        assertThat(intake.dispatch("RESUME_INTAKE_PARSE", ai, blockedCall)).isTrue();
      }
      assertThat(intake.dispatch("RESUME_INTAKE_PARSE", ai, blockedCall)).isFalse();
      for (int i = 0; i < 16; i++) ai.execute(blockedCall);
      assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
      assertThat(ai.getActiveCount()).isEqualTo(66);
    } finally {
      release.countDown();
      ai.shutdown();
    }
  }

  @Test
  void requiresAiWorkloadSlotsToExactlyPartitionTheFixedPool() {
    PlatformExecutorProperties properties = new PlatformExecutorProperties();
    assertThat(properties.hasCompleteAiWorkloadAllocation()).isTrue();

    properties.getAiWorkloadSlots().setResumeParse(3);
    assertThat(properties.hasCompleteAiWorkloadAllocation()).isFalse();

    properties.getAiWorkloadSlots().setResumeParse(2);
    properties.getAi().setMaxSize(5);
    assertThat(properties.hasCompleteAiWorkloadAllocation()).isFalse();
  }

  @Test
  void requiresFixedAiQueueToHoldOneCompleteWorkloadBurst() {
    PlatformExecutorProperties properties = new PlatformExecutorProperties();
    properties.setAi(new PlatformExecutorProperties.Pool(66, 66, 32));
    properties.setAiWorkloadSlots(new PlatformExecutorProperties.AiWorkloadSlots(6, 50, 10));

    assertThat(properties.hasSufficientAiBurstQueue()).isFalse();

    properties.getAi().setQueueCapacity(66);
    assertThat(properties.hasSufficientAiBurstQueue()).isTrue();
  }

  @Test
  void rejectsShutdownWaitsThatTruncateToZeroSeconds() {
    for (Duration wait : List.of(Duration.ofNanos(1), Duration.ofMillis(500),
        Duration.ofSeconds(1).minusNanos(1))) {
      PlatformExecutorProperties properties = new PlatformExecutorProperties();
      properties.setShutdownAwait(wait);
      assertThatThrownBy(() -> configuration.maintenanceTaskExecutor(properties))
          .as("executor shutdown wait %s", wait)
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> configuration.taskScheduler(properties))
          .as("scheduler shutdown wait %s", wait)
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void preservesValidShutdownWaitSecondsAndFractionalUpperBoundary() {
    for (Duration wait : List.of(Duration.ofSeconds(1), Duration.ofSeconds(30),
        Duration.ofSeconds(Integer.MAX_VALUE).plusNanos(999_999_999))) {
      PlatformExecutorProperties properties = new PlatformExecutorProperties();
      properties.setShutdownAwait(wait);
      assertThat(configuration.maintenanceTaskExecutor(properties)).isNotNull();
      assertThat(configuration.taskScheduler(properties)).isNotNull();
    }
  }

}
