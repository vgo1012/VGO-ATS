package com.ezaigc.hr.integration.mail.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.integration.config.MailResumeProperties;
import com.ezaigc.hr.integration.mail.application.manager.MailResumeApplicationManager;
import com.ezaigc.hr.integration.mail.application.provider.MailIntakePort;
import com.ezaigc.hr.integration.mail.domain.MailboxCursor;
import com.ezaigc.hr.integration.mail.service.MailResumeStore;
import com.ezaigc.hr.recruiting.demand.api.MailJdRoutingService;
import com.ezaigc.hr.recruiting.demand.api.MailJdRoutingService.Target;
import com.ezaigc.hr.talent.api.MailResumeIntakeService;
import com.ezaigc.hr.talent.api.MalwareScanPort;
import com.ezaigc.hr.talent.api.ObjectStoragePort;
import com.ezaigc.hr.talent.enums.MalwareScanVerdict;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class MailResumeOwnerRoutingTest {

  @Test
  void importsPublicMailboxAttachmentForTheOwnerOfItsExplicitJdCode() throws Exception {
    var manager = new MailResumeApplicationManager();
    var properties = new MailResumeProperties();
    properties.setSourceChannelId("source");
    var store = mock(MailResumeStore.class);
    var port = mock(MailIntakePort.class);
    var routing = mock(MailJdRoutingService.class);
    var scanner = mock(MalwareScanPort.class);
    var intakes = mock(MailResumeIntakeService.class);
    var tasks = mock(AsyncTaskPort.class);
    var ids = mock(PlatformIdGenerator.class);
    var factory = new StaticListableBeanFactory();
    factory.addBean("mail", port);
    ReflectionTestUtils.setField(manager, "properties", properties);
    ReflectionTestUtils.setField(manager, "mapper", store);
    ReflectionTestUtils.setField(manager, "mailPorts", factory.getBeanProvider(MailIntakePort.class));
    ReflectionTestUtils.setField(manager, "routing", routing);
    ReflectionTestUtils.setField(manager, "scanner", scanner);
    ReflectionTestUtils.setField(manager, "objects", mock(ObjectStoragePort.class));
    ReflectionTestUtils.setField(manager, "intakes", intakes);
    ReflectionTestUtils.setField(manager, "tasks", tasks);
    ReflectionTestUtils.setField(manager, "ids", ids);
    ReflectionTestUtils.setField(manager, "clock", Clock.systemUTC());
    ReflectionTestUtils.setField(manager, "transactions",
        new TransactionTemplate(mock(PlatformTransactionManager.class)));
    var cursor = new MailboxCursor();
    cursor.setFolder("INBOX");
    cursor.setUidValidity(1);
    cursor.setLastUid(0);
    when(store.findCursor(properties.getMailboxKey())).thenReturn(cursor);
    when(store.insertMessage(any())).thenReturn(1);
    when(store.advanceCursor(anyString(), anyLong(), anyLong(), anyLong(), anyString(), any()))
        .thenReturn(1);
    when(ids.newId()).thenReturn("mail-record");
    when(scanner.scan(anyString(), any())).thenReturn(MalwareScanVerdict.CLEAN);
    when(routing.activeTargets()).thenReturn(List.of(
        new Target("jd-a", "version-a", "JD-901902001", "运维工程师", 3,
            "hr-a", "department", List.of("杭州"), true),
        new Target("jd-b", "version-b", "JD-9019021000", "运维工程师", 2,
            "hr-b", "department", List.of("杭州"), true)));
    when(port.scan(0L, properties.getBatchSize())).thenReturn(new MailIntakePort.Scan(1, 1,
        List.of(new MailIntakePort.MailMessage(1, "message", "source@example.invalid",
            "应聘【JD-9019021000】", Instant.now(), List.of(new MailIntakePort.Attachment(
                "【JD-9019021000_运维工程师_杭州_20k】简历.pdf",
                "%PDF-test".getBytes(StandardCharsets.UTF_8)))))));
    when(intakes.importResume(any())).thenReturn(new MailResumeIntakeService.Result("intake", "QUEUED"));
    var task = mock(TaskSnapshot.class);
    when(task.resourceId()).thenReturn("run");

    manager.process(task);

    var command = ArgumentCaptor.forClass(MailResumeIntakeService.Command.class);
    verify(intakes).importResume(command.capture());
    assertThat(command.getValue().ownerUserId()).isEqualTo("hr-b");
    assertThat(command.getValue().ownerDepartmentId()).isEqualTo("department");
    assertThat(command.getValue().jdVersionId()).isEqualTo("version-b");
    verify(tasks).succeed(task, "run");
  }
}
