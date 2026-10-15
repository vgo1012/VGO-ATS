package com.ezaigc.hr.reporting.application.assembler;

import com.ezaigc.hr.common.infrastructure.task.ScheduledChannelSnapshot;
import com.ezaigc.hr.reporting.domain.dto.ScheduledTaskDefinitionDTO;
import com.ezaigc.hr.reporting.domain.response.ScheduledChannelResponse;
import com.ezaigc.hr.reporting.domain.response.ScheduledScanResponse;
import com.ezaigc.hr.reporting.domain.response.ScheduledTaskResponse;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TaskCenterAssembler {
    public ScheduledTaskResponse toResponse(ScheduledTaskDefinitionDTO definition,
            List<ScheduledChannelSnapshot> channels, String backlogStatus,
            Long readyCount, Instant oldestReadyAt) {
        return new ScheduledTaskResponse(definition.getCode(), definition.getName(), definition.getModule(),
                definition.isEnabled(), definition.getScheduleType(), definition.getExpression(), definition.getZone(),
                definition.getIntervalMillis(), definition.getNextCronAt(), definition.getBusinessTypes(),
                channels.stream().map(channel -> new ScheduledChannelResponse(channel.getCode(),
                        channel.getLastTriggeredAt(), channel.getLastCompletedAt(), channel.getTriggers(), channel.getCoalesced(),
                        channel.getRejected(), channel.getFailed(), channel.getQueued(), channel.getRunning(),
                        channel.getRecentScans().stream().map(scan -> new ScheduledScanResponse(scan.getRunId(),
                                scan.getState().name(), scan.getQueuedAt(), scan.getStartedAt(), scan.getFinishedAt(),
                                scan.getDurationMillis(), scan.getErrorCode())).toList())).toList(),
                definition.getIdleBackoffMaxMillis(), backlogStatus, readyCount, oldestReadyAt);
    }
}
