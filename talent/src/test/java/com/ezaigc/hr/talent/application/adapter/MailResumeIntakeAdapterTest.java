package com.ezaigc.hr.talent.application.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.identity.api.BackgroundActorExecutor;
import com.ezaigc.hr.talent.api.MailResumeIntakeService;
import com.ezaigc.hr.talent.api.ObjectStoragePort;
import com.ezaigc.hr.talent.application.manager.TalentIntakeApplicationManager;
import com.ezaigc.hr.talent.domain.dto.ImportIntakeDTO;
import com.ezaigc.hr.talent.domain.dto.ResumeUploadDTO;
import com.ezaigc.hr.talent.domain.entity.ResumeIntake;
import com.ezaigc.hr.talent.domain.response.IntakeBatchResponse;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MailResumeIntakeAdapterTest {

  @Test
  void completedMailBatchDoesNotImportAgainAfterUploadQueuedParsing() {
    var actors = mock(BackgroundActorExecutor.class);
    var intakes = mock(TalentIntakeApplicationManager.class);
    var objects = mock(ObjectStoragePort.class);
    var adapter = new MailResumeIntakeAdapter();
    ReflectionTestUtils.setField(adapter, "backgroundActors", actors);
    ReflectionTestUtils.setField(adapter, "intakes", intakes);
    ReflectionTestUtils.setField(adapter, "objects", objects);
    doAnswer(call -> ((Supplier<?>) call.getArgument(1)).get())
        .when(actors).execute(eq("owner-1"), any());
    var batch = mock(IntakeBatchResponse.class);
    when(batch.getId()).thenReturn("batch-1");
    when(intakes.createBatch(any(), eq("mail:message-1:batch"))).thenReturn(batch);
    var intake = new ResumeIntake();
    intake.setId("intake-1");
    intake.setStatus("PARSING");
    when(intakes.upload(eq("batch-1"), any(ResumeUploadDTO.class), eq("mail:message-1:file")))
        .thenReturn(intake);
    var command = new MailResumeIntakeService.Command("message-1", "channel-1", "jd-1",
        "owner-1", "department-1", "resume.pdf", 10, "mail/resume.pdf");

    var result = adapter.importResume(command);

    assertThat(result.intakeId()).isEqualTo("intake-1");
    assertThat(result.status()).isEqualTo("PARSING");
    verify(intakes).completeBatch("batch-1");
    verify(intakes, never()).importIntake(any(), any(ImportIntakeDTO.class));
  }
}
