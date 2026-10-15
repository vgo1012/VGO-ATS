package com.ezaigc.hr.common.infrastructure.task;

import java.time.Duration;
import com.ezaigc.hr.common.infrastructure.task.enums.ScheduledScanState;
import com.ezaigc.hr.common.infrastructure.task.enums.ScheduledScanError;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ScheduledTaskMonitor {
    private final String instanceId = UUID.randomUUID().toString();
    private final Instant observedSince = Instant.now();
    private final Map<String, Channel> channels = new HashMap<>();

    public String instanceId() { return instanceId; }
    public Instant observedSince() { return observedSince; }

    public synchronized void triggered(String code) {
        Channel channel = channel(code);
        channel.lastTriggeredAt = Instant.now();
        channel.triggers++;
    }

    public synchronized void coalesced(String code) { channel(code).coalesced++; }

    public synchronized String queued(String code) {
        Channel channel = channel(code);
        String id = UUID.randomUUID().toString();
        channel.active.put(id, new ScheduledScanSnapshot(id, ScheduledScanState.QUEUED, Instant.now(), null, null, null, null));
        return id;
    }

    public synchronized void started(String code, String id) {
        Channel channel = channel(code);
        ScheduledScanSnapshot previous = channel.active.get(id);
        if (previous != null) {
            channel.active.put(id, new ScheduledScanSnapshot(id, ScheduledScanState.RUNNING, previous.getQueuedAt(), Instant.now(), null, null, null));
        }
    }

    public synchronized void finished(String code, String id, String errorCode) {
        Channel channel = channel(code);
        ScheduledScanSnapshot previous = channel.active.remove(id);
        if (previous == null) return;
        Instant now = Instant.now();
        ScheduledScanState state = errorCode == null ? ScheduledScanState.COMPLETED : ScheduledScanState.FAILED;
        if (errorCode != null && ScheduledScanError.valueOf(errorCode) == ScheduledScanError.DISPATCH_REJECTED) {
            channel.rejected++;
            state = ScheduledScanState.REJECTED;
        } else if (errorCode != null) {
            channel.failed++;
        } else {
            channel.lastCompletedAt = now;
        }
        channel.history.addFirst(new ScheduledScanSnapshot(id, state, previous.getQueuedAt(),
                previous.getStartedAt(), now, previous.getStartedAt() == null ? null
                : Math.max(0, Duration.between(previous.getStartedAt(), now).toMillis()), errorCode));
        while (channel.history.size() > 20) channel.history.removeLast();
    }

    public synchronized ScheduledChannelSnapshot snapshot(String code) {
        Channel channel = channels.get(code);
        if (channel == null) return new ScheduledChannelSnapshot(code, null, null, 0, 0, 0, 0, 0, 0, List.of());
        List<ScheduledScanSnapshot> scans = new ArrayList<>(channel.active.values());
        scans.sort((left, right) -> right.getQueuedAt().compareTo(left.getQueuedAt()));
        scans.addAll(channel.history);
        int queued = (int) channel.active.values().stream().filter(scan -> scan.getState() == ScheduledScanState.QUEUED).count();
        return new ScheduledChannelSnapshot(code, channel.lastTriggeredAt, channel.lastCompletedAt,
                channel.triggers, channel.coalesced, channel.rejected, channel.failed,
                queued, channel.active.size() - queued, List.copyOf(scans));
    }

    private Channel channel(String code) {
        return channels.computeIfAbsent(code, ignored -> new Channel());
    }

    private static final class Channel {
        private Instant lastTriggeredAt;
        private Instant lastCompletedAt;
        private long triggers;
        private long coalesced;
        private long rejected;
        private long failed;
        private final Map<String, ScheduledScanSnapshot> active = new HashMap<>();
        private final Deque<ScheduledScanSnapshot> history = new ArrayDeque<>();
    }
}
