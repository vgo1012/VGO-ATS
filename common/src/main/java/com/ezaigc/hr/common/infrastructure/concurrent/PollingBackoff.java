package com.ezaigc.hr.common.infrastructure.concurrent;

import java.time.Duration;
import java.util.function.LongSupplier;

public final class PollingBackoff {
    private final LongSupplier ticker;
    private long generation;
    private long nextPoll;
    private long delaySeconds;

    public PollingBackoff() { this(System::nanoTime); }

    PollingBackoff(LongSupplier ticker) { this.ticker = ticker; }

    public synchronized boolean ready() { return nextPoll == 0 || ticker.getAsLong() - nextPoll >= 0; }
    public synchronized long generation() { return generation; }

    public synchronized void wakeUp() {
        generation++;
        delaySeconds = 0;
        nextPoll = 0;
    }

    public synchronized void completed(long observedGeneration, boolean worked) {
        if (generation != observedGeneration) return;
        delaySeconds = worked ? 0 : Math.min(60, Math.max(5, delaySeconds * 2));
        nextPoll = worked ? 0 : ticker.getAsLong() + Duration.ofSeconds(delaySeconds).toNanos();
    }
}
