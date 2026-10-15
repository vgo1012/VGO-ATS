package com.ezaigc.hr.reporting.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.common.infrastructure.task.PersistentTaskBacklogProbe;
import com.ezaigc.hr.common.infrastructure.task.PersistentTaskBacklogSnapshot;
import com.ezaigc.hr.common.infrastructure.task.ScheduledTaskMonitor;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.pagination.CursorCodec;
import com.ezaigc.hr.common.web.pagination.CursorScopes;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.reporting.application.assembler.AsyncTaskAssembler;
import com.ezaigc.hr.reporting.application.assembler.TaskCenterAssembler;
import com.ezaigc.hr.reporting.application.support.ScheduledTaskCatalog;
import com.ezaigc.hr.reporting.domain.dto.ScheduledTaskDefinitionDTO;
import com.ezaigc.hr.reporting.domain.response.AsyncTaskResponse;
import com.ezaigc.hr.reporting.service.AsyncTaskService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.test.util.ReflectionTestUtils;

class TaskCenterApplicationManagerTest {
    private final TaskCenterApplicationManager manager = new TaskCenterApplicationManager();
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final ScheduledTaskCatalog catalog = mock(ScheduledTaskCatalog.class);
    private final AsyncTaskService tasks = mock(AsyncTaskService.class);
    private final CursorCodec cursors = mock(CursorCodec.class);
    private final AsyncTaskAssembler assembler = mock(AsyncTaskAssembler.class);
    private final CurrentActorSnapshot actor = new CurrentActorSnapshot();

    @BeforeEach
    void setUp() {
        actor.setUserId("A".repeat(26));
        when(authorization.currentActor()).thenReturn(actor);
        ReflectionTestUtils.setField(manager, "authorization", authorization);
        ReflectionTestUtils.setField(manager, "catalog", catalog);
        ReflectionTestUtils.setField(manager, "monitor", new ScheduledTaskMonitor());
        ReflectionTestUtils.setField(manager, "tasks", tasks);
        ReflectionTestUtils.setField(manager, "cursors", cursors);
        ReflectionTestUtils.setField(manager, "assembler", new TaskCenterAssembler());
        ReflectionTestUtils.setField(manager, "taskAssembler", assembler);
        setBacklogProbes();
    }

    @Test
    void deniesNonAdminBeforeReadingAnyMetadataOrBusinessData() {
        assertThatThrownBy(manager::overview).isInstanceOf(PlatformException.class);
        assertThatThrownBy(() -> manager.history("test", null, 20)).isInstanceOf(PlatformException.class);
        verifyNoInteractions(catalog, tasks);
    }

    @Test
    void overviewRemainsReadableWithoutBusinessDatabaseAndPreservesDisabledState() {
        actor.setSuperAdmin(true);
        when(catalog.definitions()).thenReturn(List.of(definition(List.of())));
        var response = manager.overview();
        assertThat(response.getInstanceId()).isNotBlank();
        assertThat(response.getTasks()).singleElement().satisfies(task -> {
            assertThat(task.isEnabled()).isFalse();
            assertThat(task.getChannels().getFirst().getRecentScans()).isEmpty();
            assertThat(task.getPersistentBacklogStatus()).isEqualTo("UNSUPPORTED");
            assertThat(task.getPersistentReadyCount()).isNull();
        });
        verifyNoInteractions(tasks);
    }

    @Test
    void aggregatesEveryPersistentSourceAndKeepsTheOldestReadyTime() {
        actor.setSuperAdmin(true);
        when(catalog.definitions()).thenReturn(List.of(definition(List.of("TYPE_A"))));
        PersistentTaskBacklogProbe first = backlogProbe(2, "2026-09-16T00:30:00Z");
        PersistentTaskBacklogProbe second = backlogProbe(3, "2026-09-16T00:10:00Z");
        setBacklogProbes(first, second);

        var task = manager.overview().getTasks().getFirst();

        assertThat(task.getPersistentBacklogStatus()).isEqualTo("AVAILABLE");
        assertThat(task.getPersistentReadyCount()).isEqualTo(5);
        assertThat(task.getOldestReadyAt()).isEqualTo(Instant.parse("2026-09-16T00:10:00Z"));
    }

    @Test
    void databaseFailureKeepsSchedulerOverviewReadableWithoutReportingZeroBacklog() {
        actor.setSuperAdmin(true);
        when(catalog.definitions()).thenReturn(List.of(definition(List.of("TYPE_A"))));
        PersistentTaskBacklogProbe probe = mock(PersistentTaskBacklogProbe.class);
        when(probe.supports("test", List.of("TYPE_A"))).thenReturn(true);
        when(probe.snapshot(eq("test"), eq(List.of("TYPE_A")), any()))
                .thenThrow(new TransientDataAccessResourceException("unavailable"));
        setBacklogProbes(probe);

        var task = manager.overview().getTasks().getFirst();

        assertThat(task.getPersistentBacklogStatus()).isEqualTo("UNAVAILABLE");
        assertThat(task.getPersistentReadyCount()).isNull();
        assertThat(task.getOldestReadyAt()).isNull();
    }

    @Test
    void mergesBusinessTypesWithStableCursorWithoutReplayingOrModifyingTasks() {
        actor.setSuperAdmin(true);
        when(catalog.definitions()).thenReturn(List.of(definition(List.of("TYPE_A", "TYPE_B"))));
        TaskSnapshot first = task("C".repeat(26), "2026-09-16T02:00:00Z");
        TaskSnapshot second = task("B".repeat(26), "2026-09-16T01:00:00Z");
        TaskSnapshot third = task("D".repeat(26), "2026-09-15T23:00:00Z");
        when(tasks.findRecentTasks(isNull(), eq("TYPE_A"), isNull(), isNull(), isNull(), eq(3)))
                .thenReturn(List.of(first, third));
        when(tasks.findRecentTasks(isNull(), eq("TYPE_B"), isNull(), isNull(), isNull(), eq(3)))
                .thenReturn(List.of(second));
        String scope = CursorScopes.scope("task-center.history", actor.getUserId(), "test");
        when(cursors.encode(eq(scope), any())).thenReturn("cursor-next");
        when(assembler.toResponse(any())).thenReturn(mock(AsyncTaskResponse.class));
        var page = manager.history("test", null, 2);
        assertThat(page.getItems()).hasSize(2);
        assertThat(page.isHasMore()).isTrue();
        assertThat(page.getNextCursor()).isEqualTo("cursor-next");
        verify(cursors).encode(scope, List.of(second.createTime().toString(), second.id()));
        List<String> cursorValues = List.of(second.createTime().toString(), second.id());
        when(cursors.decode("cursor-next", scope, 2)).thenReturn(cursorValues);
        Instant cursorTime = Instant.parse(cursorValues.getFirst());
        String cursorId = cursorValues.getLast();
        when(tasks.findRecentTasks(isNull(), any(), isNull(), eq(cursorTime), eq(cursorId), anyInt()))
                .thenReturn(List.of());
        assertThat(manager.history("test", "cursor-next", 2).isHasMore()).isFalse();
    }

    @Test
    void unknownJobCannotTurnIntoUnfilteredGlobalHistoryQuery() {
        actor.setSuperAdmin(true);
        when(catalog.definitions()).thenReturn(List.of(definition(List.of())));
        assertThatThrownBy(() -> manager.history("unknown", null, 20)).isInstanceOf(PlatformException.class);
        assertThat(manager.history("test", null, 20).getItems()).isEmpty();
        verifyNoInteractions(tasks);
    }

    @Test
    void rejectsCursorFromAnotherJobBeforeReadingHistory() {
        actor.setSuperAdmin(true);
        when(catalog.definitions()).thenReturn(List.of(definition(List.of("TYPE_A"))));
        when(cursors.decode(any(), any(), eq(2))).thenThrow(new IllegalArgumentException("invalid"));
        assertThatThrownBy(() -> manager.history("test", "wrong-scope", 20)).isInstanceOf(PlatformException.class);
        verifyNoInteractions(tasks);
    }

    @Test
    void rejectsMalformedCursorTimestampBeforeReadingHistory() {
        actor.setSuperAdmin(true);
        when(catalog.definitions()).thenReturn(List.of(definition(List.of("TYPE_A"))));
        when(cursors.decode(any(), any(), eq(2))).thenReturn(List.of("invalid-time", "A".repeat(26)));
        assertThatThrownBy(() -> manager.history("test", "invalid-date", 20)).isInstanceOf(PlatformException.class);
        verifyNoInteractions(tasks);
    }

    private ScheduledTaskDefinitionDTO definition(List<String> types) {
        return new ScheduledTaskDefinitionDTO("test", "测试", "测试模块", false,
                "FIXED_DELAY", "2000", "Asia/Shanghai", 2000L, null, List.of("test"), types, 0);
    }

    private TaskSnapshot task(String id, String time) {
        TaskSnapshot result = mock(TaskSnapshot.class);
        when(result.id()).thenReturn(id);
        when(result.createTime()).thenReturn(Instant.parse(time));
        return result;
    }

    private PersistentTaskBacklogProbe backlogProbe(long count, String oldest) {
        PersistentTaskBacklogProbe probe = mock(PersistentTaskBacklogProbe.class);
        when(probe.supports("test", List.of("TYPE_A"))).thenReturn(true);
        when(probe.snapshot(eq("test"), eq(List.of("TYPE_A")), any()))
                .thenReturn(new PersistentTaskBacklogSnapshot(count, Instant.parse(oldest)));
        return probe;
    }

    private void setBacklogProbes(PersistentTaskBacklogProbe... probes) {
        var beans = new StaticListableBeanFactory();
        for (int index = 0; index < probes.length; index++) {
            beans.addBean("backlogProbe" + index, probes[index]);
        }
        ReflectionTestUtils.setField(manager, "backlogProbes",
                beans.getBeanProvider(PersistentTaskBacklogProbe.class));
    }
}
