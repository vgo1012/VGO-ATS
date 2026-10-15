package com.ezaigc.hr.reporting.application.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.infrastructure.task.PersistentTaskBacklogSnapshot;
import com.ezaigc.hr.reporting.service.AsyncTaskService;
import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.util.ReflectionTestUtils;

class AsyncTaskPersistentBacklogProbeTest {
  @Test
  void readsAllDeclaredBusinessTypesAsOnePersistentBacklog() {
    var tasks = mock(AsyncTaskService.class);
    var dataSource = mock(DataSource.class);
    var beans = new StaticListableBeanFactory();
    beans.addBean("dataSource", dataSource);
    beans.addBean("tasks", tasks);
    var probe = new AsyncTaskPersistentBacklogProbe();
    ReflectionTestUtils.setField(probe, "dataSourceProvider", beans.getBeanProvider(DataSource.class));
    ReflectionTestUtils.setField(probe, "taskProvider", beans.getBeanProvider(AsyncTaskService.class));
    Instant now = Instant.parse("2026-09-18T00:00:00Z");
    var expected = new PersistentTaskBacklogSnapshot(
        4, Instant.parse("2026-09-17T23:00:00Z"));
    when(tasks.findReadyBacklog(List.of("TYPE_A", "TYPE_B"), now)).thenReturn(expected);

    assertThat(probe.supports("schedule", List.of("TYPE_A", "TYPE_B"))).isTrue();
    assertThat(probe.snapshot("schedule", List.of("TYPE_A", "TYPE_B"), now))
        .isSameAs(expected);
    verify(tasks).findReadyBacklog(List.of("TYPE_A", "TYPE_B"), now);
  }
}
