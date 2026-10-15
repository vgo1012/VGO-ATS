package com.ezaigc.hr.reporting.application.manager;

import com.ezaigc.hr.common.infrastructure.logging.SafeLogException;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.common.infrastructure.task.PersistentTaskBacklogProbe;
import com.ezaigc.hr.common.infrastructure.task.PersistentTaskBacklogSnapshot;
import com.ezaigc.hr.common.infrastructure.task.ScheduledTaskMonitor;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.web.pagination.CursorCodec;
import com.ezaigc.hr.common.web.pagination.CursorPage;
import com.ezaigc.hr.common.web.pagination.CursorScopes;
import com.ezaigc.hr.common.web.pagination.PageLimits;
import com.ezaigc.hr.common.web.request.RequiredIdentifier;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.reporting.application.assembler.AsyncTaskAssembler;
import com.ezaigc.hr.reporting.application.assembler.TaskCenterAssembler;
import com.ezaigc.hr.reporting.application.support.ScheduledTaskCatalog;
import com.ezaigc.hr.reporting.domain.dto.ScheduledTaskDefinitionDTO;
import com.ezaigc.hr.reporting.domain.response.AsyncTaskResponse;
import com.ezaigc.hr.reporting.domain.response.ScheduledTaskResponse;
import com.ezaigc.hr.reporting.domain.response.TaskCenterOverviewResponse;
import com.ezaigc.hr.reporting.service.AsyncTaskService;
import jakarta.annotation.Resource;
import java.time.Instant;
import java.time.DateTimeException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskCenterApplicationManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskCenterApplicationManager.class);
    @Resource private AuthorizationService authorization;
    @Resource private ScheduledTaskCatalog catalog;
    @Resource private ScheduledTaskMonitor monitor;
    @Resource private AsyncTaskService tasks;
    @Resource private TaskCenterAssembler assembler;
    @Resource private AsyncTaskAssembler taskAssembler;
    @Resource private CursorCodec cursors;
    @Resource private ObjectProvider<PersistentTaskBacklogProbe> backlogProbes;

    public TaskCenterOverviewResponse overview() {
        requireAdmin();
        Instant sampledAt = Instant.now();
        return new TaskCenterOverviewResponse(monitor.instanceId(), monitor.observedSince(), sampledAt,
                catalog.definitions().stream().map(definition -> response(definition, sampledAt)).toList());
    }

    private ScheduledTaskResponse response(
            ScheduledTaskDefinitionDTO definition, Instant sampledAt) {
        List<PersistentTaskBacklogProbe> supported = backlogProbes.orderedStream()
                .filter(probe -> probe.supports(definition.getCode(), definition.getBusinessTypes()))
                .toList();
        if (supported.isEmpty()) {
            return assembler.toResponse(definition,
                    definition.getChannels().stream().map(monitor::snapshot).toList(),
                    "UNSUPPORTED", null, null);
        }
        try {
            PersistentTaskBacklogSnapshot total = PersistentTaskBacklogSnapshot.empty();
            for (PersistentTaskBacklogProbe probe : supported) {
                total = total.plus(probe.snapshot(
                        definition.getCode(), definition.getBusinessTypes(), sampledAt));
            }
            return assembler.toResponse(definition,
                    definition.getChannels().stream().map(monitor::snapshot).toList(),
                    "AVAILABLE", total.getReadyCount(), total.getOldestReadyAt());
        } catch (DataAccessException failure) {
            LOGGER.warn("task-backlog-read-failed scheduleCode={}", definition.getCode(),
                    SafeLogException.from(failure));
            return assembler.toResponse(definition,
                    definition.getChannels().stream().map(monitor::snapshot).toList(),
                    "UNAVAILABLE", null, null);
        }
    }

    @Transactional(readOnly = true)
    public CursorPage<AsyncTaskResponse> history(String jobCode, String cursor, Integer requestedLimit) {
        CurrentActorSnapshot actor = requireAdmin();
        ScheduledTaskDefinitionDTO definition = catalog.definitions().stream()
                .filter(item -> item.getCode().equals(jobCode)).findFirst()
                .orElseThrow(() -> PlatformExceptions.notFound("SCHEDULED_TASK_NOT_FOUND", "调度任务不存在"));
        int limit = PageLimits.resolve(requestedLimit, 20, 100);
        String scope = CursorScopes.scope("task-center.history", actor.getUserId(), jobCode);
        Instant beforeTime = null;
        String beforeId = null;
        if (cursor != null && !cursor.isBlank()) {
            try {
                List<String> values = cursors.decode(cursor, scope, 2);
                beforeTime = Instant.parse(values.getFirst());
                beforeId = RequiredIdentifier.require(values.getLast(), "cursorId");
            } catch (IllegalArgumentException | DateTimeException exception) {
                throw PlatformExceptions.badRequest("CURSOR_INVALID", "分页游标无效，请重新读取任务历史");
            }
        }
        List<TaskSnapshot> history = new ArrayList<>();
        for (String taskType : definition.getBusinessTypes()) {
            history.addAll(tasks.findRecentTasks(null, taskType, null, beforeTime, beforeId, limit + 1));
        }
        history.sort(Comparator.comparing(TaskSnapshot::createTime).thenComparing(TaskSnapshot::id).reversed());
        boolean hasMore = history.size() > limit;
        List<TaskSnapshot> page = history.subList(0, Math.min(history.size(), limit));
        String next = hasMore ? cursors.encode(scope,
                List.of(page.getLast().createTime().toString(), page.getLast().id())) : null;
        return new CursorPage<>(page.stream().map(taskAssembler::toResponse).toList(), next, hasMore);
    }

    private CurrentActorSnapshot requireAdmin() {
        CurrentActorSnapshot actor = authorization.currentActor();
        if (!actor.isSuperAdmin()) throw PlatformExceptions.forbidden("SUPER_ADMIN_REQUIRED", "需要超级管理员权限");
        return actor;
    }
}
