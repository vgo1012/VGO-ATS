package com.ezaigc.hr.integration.mail.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.integration.config.MailResumeProperties;
import com.ezaigc.hr.integration.mail.application.manager.MailResumeApplicationManager;
import com.ezaigc.hr.integration.mail.application.provider.MailIntakePort;
import com.ezaigc.hr.integration.mail.domain.MailResumeRecord;
import com.ezaigc.hr.integration.mail.domain.MailboxCursor;
import com.ezaigc.hr.integration.mail.service.MailResumeStore;
import com.ezaigc.hr.recruiting.demand.api.MailJdRoutingService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class MailResumeMalformedMessageTest {
  private final MailResumeApplicationManager manager = new MailResumeApplicationManager();
  private final MailResumeProperties properties = new MailResumeProperties();
  private final MailResumeStore store = mock(MailResumeStore.class);
  private final MailIntakePort port = mock(MailIntakePort.class);
  private final AsyncTaskPort tasks = mock(AsyncTaskPort.class);
  private final TaskSnapshot task = mock(TaskSnapshot.class);

  @BeforeEach
  void prepare() {
    var factory = new StaticListableBeanFactory();
    factory.addBean("mail", port);
    var routing = mock(MailJdRoutingService.class);
    var ids = mock(PlatformIdGenerator.class);
    when(ids.newId()).thenReturn("record");
    when(task.resourceId()).thenReturn("run");
    when(routing.activeTargets()).thenReturn(List.of());
    ReflectionTestUtils.setField(manager, "properties", properties);
    ReflectionTestUtils.setField(manager, "mapper", store);
    ReflectionTestUtils.setField(manager, "mailPorts", factory.getBeanProvider(MailIntakePort.class));
    ReflectionTestUtils.setField(manager, "routing", routing);
    ReflectionTestUtils.setField(manager, "tasks", tasks);
    ReflectionTestUtils.setField(manager, "ids", ids);
    ReflectionTestUtils.setField(manager, "clock", Clock.systemUTC());
    ReflectionTestUtils.setField(manager, "transactions",
        new TransactionTemplate(mock(PlatformTransactionManager.class)));
    var cursor = new MailboxCursor();
    cursor.setFolder("INBOX");
    cursor.setUidValidity(7);
    cursor.setLastUid(0);
    when(store.findCursor(properties.getMailboxKey())).thenReturn(cursor);
    when(store.insertMessage(any())).thenReturn(1);
    when(store.advanceCursor(anyString(), anyLong(), anyLong(), anyLong(), anyString(), any())).thenReturn(1);
    when(port.scan(0L, properties.getBatchSize())).thenReturn(new MailIntakePort.Scan(7, 2,
        List.of(new MailIntakePort.MailMessage(1, null, null, null, Instant.now(),
            List.of(), "MAIL_MESSAGE_UNREADABLE"))));
    when(port.scan(1L, properties.getBatchSize())).thenReturn(new MailIntakePort.Scan(7, 2,
        List.of(new MailIntakePort.MailMessage(2, "good", null, "normal mail", Instant.now(), List.of()))));
  }

  @Test
  void recordsBadMailBeforeAdvancingAndContinuesWithTheNextUid() {
    manager.process(task);
    var records = ArgumentCaptor.forClass(MailResumeRecord.class);
    verify(store, times(2)).insertMessage(records.capture());
    assertThat(records.getAllValues()).extracting(MailResumeRecord::getFailureCode)
        .containsExactly("MAIL_MESSAGE_UNREADABLE", "MAIL_RESUME_ATTACHMENT_MISSING");
    verify(store).advanceCursor(eq(properties.getMailboxKey()), eq(7L), eq(0L), eq(1L), anyString(), any());
    verify(store).advanceCursor(eq(properties.getMailboxKey()), eq(7L), eq(1L), eq(2L), anyString(), any());
    verify(tasks).succeed(task, "run");
  }

  @Test
  void failedRejectionRecordDoesNotAdvanceOrReadTheNextMail() {
    when(store.insertMessage(any())).thenReturn(0);
    manager.process(task);
    verify(store, never()).advanceCursor(anyString(), anyLong(), anyLong(), anyLong(), anyString(), any());
    verify(port, never()).scan(1L, properties.getBatchSize());
    verify(tasks).fail(eq(task), eq("MAIL_RESUME_INGEST_FAILED"), anyString(), eq(true));
  }

  @Test
  void emptyBoundedUidWindowsCannotSkipMailAfterAGap() {
    int batch = properties.getBatchSize();
    when(port.scan(0L, batch)).thenReturn(new MailIntakePort.Scan(7, 41, List.of(), 20));
    when(port.scan(20L, batch)).thenReturn(new MailIntakePort.Scan(7, 41, List.of(), 40));
    when(port.scan(40L, batch)).thenReturn(new MailIntakePort.Scan(7, 41,
        List.of(new MailIntakePort.MailMessage(41, "after-gap", null, null, Instant.now(), List.of())), 41));

    manager.process(task);

    var record = ArgumentCaptor.forClass(MailResumeRecord.class);
    verify(store).insertMessage(record.capture());
    assertThat(record.getValue().getMailUid()).isEqualTo(41L);
    verify(store).advanceCursor(eq(properties.getMailboxKey()), eq(7L), eq(0L), eq(20L), anyString(), any());
    verify(store).advanceCursor(eq(properties.getMailboxKey()), eq(7L), eq(20L), eq(40L), anyString(), any());
    verify(store).advanceCursor(eq(properties.getMailboxKey()), eq(7L), eq(40L), eq(41L), anyString(), any());
    verify(store, never()).advanceCursor(eq(properties.getMailboxKey()), eq(7L), eq(0L), eq(41L), anyString(), any());
    verify(tasks).succeed(task, "run");
  }

  @Test
  void oneRunStopsAtMessageBudgetAndNextRunResumesCommittedUid() {
    properties.setBatchSize(2);
    AtomicLong committedUid = progressiveCursor();
    when(port.scan(anyLong(), eq(2))).thenAnswer(call -> {
      long uid = (long) call.getArgument(0) + 1;
      return new MailIntakePort.Scan(7, 10, List.of(new MailIntakePort.MailMessage(
          uid, "mail-" + uid, null, null, Instant.now(), List.of())), uid);
    });

    manager.process(task);

    assertThat(committedUid.get()).isEqualTo(2);
    verify(port, times(2)).scan(anyLong(), eq(2));
    verify(store, times(2)).insertMessage(any());
    clearInvocations(port, store);
    manager.process(task);
    assertThat(committedUid.get()).isEqualTo(4);
    verify(port).scan(2L, 2);
    verify(port).scan(3L, 2);
    verify(store, times(2)).insertMessage(any());
  }

  @Test
  void emptyUidWindowsHavePageBudgetAndResumeWithoutSkippingUncheckedRange() {
    properties.setBatchSize(2);
    AtomicLong committedUid = progressiveCursor();
    when(port.scan(anyLong(), eq(2))).thenAnswer(call ->
        new MailIntakePort.Scan(7, 100, List.of(), (long) call.getArgument(0) + 20));

    manager.process(task);

    assertThat(committedUid.get()).isEqualTo(40);
    verify(port, times(2)).scan(anyLong(), eq(2));
    verify(store, never()).insertMessage(any());
    verify(store).completeRun(eq("run"), eq(0), eq(0), eq(0), eq(0), eq(0), eq(0),
        eq("MAIL_RESUME_BATCH_DEFERRED"), anyString(), anyString(), any(), eq("PARTIAL"));
    clearInvocations(port);
    manager.process(task);
    assertThat(committedUid.get()).isEqualTo(80);
    verify(port).scan(40L, 2);
    verify(port).scan(60L, 2);
  }

  private AtomicLong progressiveCursor() {
    AtomicLong uid = new AtomicLong();
    when(store.findCursor(properties.getMailboxKey())).thenAnswer(ignored -> {
      MailboxCursor cursor = new MailboxCursor();
      cursor.setFolder("INBOX");
      cursor.setUidValidity(7);
      cursor.setLastUid(uid.get());
      return cursor;
    });
    when(store.advanceCursor(anyString(), anyLong(), anyLong(), anyLong(), anyString(), any()))
        .thenAnswer(call -> uid.compareAndSet(call.getArgument(2), call.getArgument(3)) ? 1 : 0);
    return uid;
  }
}
