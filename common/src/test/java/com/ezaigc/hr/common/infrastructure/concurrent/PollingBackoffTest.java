package com.ezaigc.hr.common.infrastructure.concurrent;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class PollingBackoffTest {
    @Test
    void emptyPollingBacksOffToOneMinuteAndWorkRestoresImmediateEligibility() {
        AtomicLong now = new AtomicLong();
        PollingBackoff backoff = new PollingBackoff(now::get);
        for (int seconds : List.of(5, 10, 20, 40, 60, 60)) {
            assertThat(backoff.ready()).isTrue();
            backoff.completed(backoff.generation(), false);
            now.addAndGet(Duration.ofSeconds(seconds).toNanos() - 1);
            assertThat(backoff.ready()).isFalse();
            now.incrementAndGet();
            assertThat(backoff.ready()).isTrue();
        }
        backoff.completed(backoff.generation(), true);
        assertThat(backoff.ready()).isTrue();
    }

    @Test
    void anOlderEmptyScanCannotOverwriteANewWakeup() {
        PollingBackoff backoff = new PollingBackoff(() -> 0);
        long previous = backoff.generation();
        backoff.wakeUp();
        backoff.completed(previous, false);
        assertThat(backoff.ready()).isTrue();
    }

    @Test
    void emptyOrFailedWorkSkipsDatabaseCallbacksUntilWoken() {
        CoalescingTaskDispatcher dispatcher = new CoalescingTaskDispatcher();
        assertThat(dispatcher.dispatchPolling("test", Runnable::run, () -> 0)).isTrue();
        assertThat(dispatcher.dispatchPolling("test", Runnable::run, () -> { throw new AssertionError(); })).isFalse();
        dispatcher.wakeUp();
        assertThat(dispatcher.dispatchPolling("test", Runnable::run, () -> { throw new IllegalStateException(); })).isTrue();
        assertThat(dispatcher.dispatchPolling("test", Runnable::run, () -> 1)).isFalse();
        dispatcher.wakeUp();
        assertThat(dispatcher.dispatchPolling("test", Runnable::run, () -> 1)).isTrue();
    }

    @Test
    void wakeupDuringQueuedWorkIsNotLostWhenTriggerCoalesces() {
        CoalescingTaskDispatcher dispatcher = new CoalescingTaskDispatcher();
        List<Runnable> queue = new ArrayList<>();
        dispatcher.dispatchPolling("test", queue::add, () -> 0);
        dispatcher.wakeUp();
        assertThat(dispatcher.dispatchPolling("test", queue::add, () -> 1)).isFalse();
        queue.getFirst().run();
        assertThat(dispatcher.dispatchPolling("test", queue::add, () -> 1)).isTrue();
    }
}
