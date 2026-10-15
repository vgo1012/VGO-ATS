package com.ezaigc.hr.common.infrastructure.task;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/** 有限尝试使用指数上限与半区间抖动，避免恢复后所有任务在同一时刻冲击外部系统。 */
public final class TaskRetryBackoff {
    private TaskRetryBackoff() {}

    /** 第一次等待 2.5～5 秒，指数增长至 150～300 秒；次数上限仍由持久任务决定。 */
    public static Instant nextRetryAt(Instant now, int attemptNo) {
        Objects.requireNonNull(now, "now");
        if (attemptNo < 1 || attemptNo > 20) {
            throw new IllegalArgumentException("attempt number must be 1..20");
        }
        long ceilingMillis = Math.min(300_000L, 5_000L << Math.min(attemptNo - 1, 6));
        return now.plusMillis(ThreadLocalRandom.current().nextLong(ceilingMillis / 2, ceilingMillis + 1));
    }
}
