package com.ezaigc.hr.talent.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.infrastructure.task.AsyncTaskAvailable;
import com.ezaigc.hr.talent.mapper.ResumeIntakeProcessingMapper;
import com.ezaigc.hr.talent.domain.entity.ResumeIntakeProcessing;
import com.ezaigc.hr.talent.service.ResumeIntakeOutboxService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class ResumeIntakeWakeupTest {
    @Test
    void scopedReparseInsertEnqueuesAndWakesOnlyAfterSuccessfulWrite() {
        ResumeIntakeProcessingPersistenceServiceImpl service = new ResumeIntakeProcessingPersistenceServiceImpl();
        ResumeIntakeProcessingMapper mapper = mock(ResumeIntakeProcessingMapper.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        ResumeIntakeOutboxService outbox = mock(ResumeIntakeOutboxService.class);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        ReflectionTestUtils.setField(service, "events", events);
        var beans = new StaticListableBeanFactory();
        beans.addBean("outbox", outbox);
        ReflectionTestUtils.setField(service, "outboxProvider",
                beans.getBeanProvider(ResumeIntakeOutboxService.class));
        Instant now = Instant.parse("2026-09-16T00:00:00Z");

        service.insertReparseProcessing("reparse", "hash", "resume.pdf", "application/pdf", "key", "actor", now);
        verifyNoInteractions(outbox, events);

        when(mapper.insertReparseProcessing(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        service.insertReparseProcessing("reparse", "hash", "resume.pdf", "application/pdf", "key", "actor", now);
        verify(outbox).enqueue("reparse", 0, "RESUME_INTAKE_PARSE", "actor", now);
        verify(events).publishEvent(new AsyncTaskAvailable("RESUME_INTAKE_PARSE", "reparse", null));
    }

    @Test
    void onlyWinningWritesPublishProcessingWakeups() {
        ResumeIntakeProcessingPersistenceServiceImpl service = new ResumeIntakeProcessingPersistenceServiceImpl();
        ResumeIntakeProcessingMapper mapper = mock(ResumeIntakeProcessingMapper.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        ResumeIntakeOutboxService outbox = mock(ResumeIntakeOutboxService.class);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        ReflectionTestUtils.setField(service, "events", events);
        var beans = new StaticListableBeanFactory();
        beans.addBean("outbox", outbox);
        ReflectionTestUtils.setField(service, "outboxProvider",
                beans.getBeanProvider(ResumeIntakeOutboxService.class));
        Instant now = Instant.parse("2026-09-16T00:00:00Z");
        service.retry("id", "actor", now);
        verifyNoInteractions(events);
        when(mapper.retry(any(), any(), any())).thenReturn(1);
        var processing = new ResumeIntakeProcessing();
        processing.setId("id");
        processing.setVersion(4);
        when(mapper.selectById("id")).thenReturn(processing);
        service.retry("id", "actor", now);
        verify(outbox).enqueue("id", 4, "RESUME_INTAKE_PARSE", "actor", now);
        verify(events).publishEvent(new AsyncTaskAvailable("RESUME_INTAKE_PARSE", "id", null));
    }
}
