package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.EnqueueTask;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.talent.application.provider.ResumeDocumentExtractionPort;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.ResumeFacts;
import com.ezaigc.hr.talent.domain.dto.IntakeParsedSourceDTO;
import com.ezaigc.hr.talent.domain.entity.FactSnapshot;
import com.ezaigc.hr.talent.domain.entity.ResumeIntakeProcessing;
import com.ezaigc.hr.talent.domain.entity.ResumeResourceEntity;
import com.ezaigc.hr.talent.service.ExtractionRunPersistenceService;
import com.ezaigc.hr.talent.service.ResumeExtractionPagePersistenceService;
import com.ezaigc.hr.talent.service.ResumeModelRunPersistenceService;
import com.ezaigc.hr.talent.service.ResumeParsingPersistenceService;
import com.ezaigc.hr.talent.service.ResumeVersionPersistenceService;
import com.ezaigc.hr.talent.service.validator.ResumeParseContractValidator;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.function.Consumer;
import org.springframework.transaction.TransactionStatus;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class ResumeIntakeFactAdoptionTest {
  private final ResumeParsingApplicationManager manager = new ResumeParsingApplicationManager();
  private final ResumeParsingPersistenceService persistence = mock(ResumeParsingPersistenceService.class);
  private final AsyncTaskPort tasks = mock(AsyncTaskPort.class);
  private final ResumeVersionPersistenceService resumeVersions = mock(ResumeVersionPersistenceService.class);
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final ResumeParseModelPort model = mock(ResumeParseModelPort.class);
  private final ResumeDocumentExtractionPort extraction = mock(ResumeDocumentExtractionPort.class);
  private final ResumeIntakeProcessing processing = new ResumeIntakeProcessing();
  private final Instant now = Instant.parse("2026-09-09T08:00:00Z");
  private final JsonMapper json = JsonMapper.builder().build();

  ResumeIntakeFactAdoptionTest() {
    var actor = new CurrentActorSnapshot(); actor.setUserId("requester");
    when(authorization.currentActor()).thenReturn(actor);
    var ids = mock(PlatformIdGenerator.class);
    when(ids.newId()).thenReturn("extract", "page", "model");
    inject("resumeVersions", resumeVersions);
    when(resumeVersions.lockForRouting("resume")).thenReturn("resume");
    inject("persistence", persistence); inject("tasks", tasks); inject("authorization", authorization);
    inject("ids", ids); inject("clock", Clock.fixed(now, ZoneOffset.UTC));
    inject("json", json); inject("model", model); inject("extraction", extraction);
    when(persistence.findResumeResource("resume")).thenReturn(new ResumeResourceEntity(
        "resume", "file.pdf", "application/pdf", "hash", "resume/hash", "candidate", "requester", "dept"));
    processing.setId("processing"); processing.setStatus("SUCCEEDED"); processing.setContentSha256("hash");
    processing.setExtractedPagesJson("{}"); processing.setParsedFactsJson("{}");
    when(tasks.renewLease(any())).thenReturn(true);
  }

  @Test
  void enqueuesDatabaseAdoptionWithStableKeyWithoutCallingAi() {
    manager.adoptIntakeResult("resume", processing);
    var command = ArgumentCaptor.forClass(EnqueueTask.class);
    verify(tasks).enqueue(command.capture());
    assertThat(command.getValue().taskType()).isEqualTo("RESUME_INTAKE_ADOPT");
    assertThat(command.getValue().idempotencyKey()).isEqualTo("intake-adopt:resume:processing");
    assertThat(command.getValue().maxAttempts()).isEqualTo(3);
    verifyNoInteractions(model, extraction);
  }

  @ParameterizedTest
  @ValueSource(strings = {"QUEUED", "RUNNING", "SUCCEEDED", "FAILED", "CANCELLED"})
  void anotherOperatorKeepsOriginalTaskWhilePollingAfterRetry(String status) {
    var actor = new CurrentActorSnapshot(); actor.setUserId("other-hr");
    when(authorization.currentActor()).thenReturn(actor);
    TaskSnapshot original = mock(TaskSnapshot.class);
    when(original.status()).thenReturn(status);
    when(original.requestedByUserId()).thenReturn("requester");
    when(tasks.getByResourceIdempotency("RESUME_INTAKE_ADOPT", "RESUME_VERSION", "resume",
        "intake-adopt:resume:processing")).thenReturn(original);

    assertThat(manager.adoptIntakeResult("resume", processing)).isSameAs(original);
    var order = inOrder(authorization, resumeVersions, tasks);
    order.verify(authorization).requireAccess(eq("talent.manage"), any());
    order.verify(resumeVersions).lockForRouting("resume");
    order.verify(tasks).getByResourceIdempotency("RESUME_INTAKE_ADOPT", "RESUME_VERSION", "resume",
        "intake-adopt:resume:processing");
    verify(tasks, never()).enqueue(any());
    assertThat(original.requestedByUserId()).isEqualTo("requester");
    assertThat(original.status()).isEqualTo(status);
    verifyNoInteractions(model, extraction);
  }

  @Test
  void missingResumeLockPreventsNewTaskCreation() {
    when(resumeVersions.lockForRouting("resume")).thenReturn(null);
    assertThatThrownBy(() -> manager.adoptIntakeResult("resume", processing))
        .hasMessageContaining("归档简历版本已不可用");
    verifyNoInteractions(tasks, model, extraction);
  }

  @Test
  void unauthorizedOperatorCannotFindOrRetryAnotherPersonsTask() {
    doThrow(PlatformExceptions.forbidden("ACCESS_DENIED", "无权处理简历"))
        .when(authorization).requireAccess(eq("talent.manage"), any());
    assertThatThrownBy(() -> manager.retryIntakeAdoption("resume", "processing", "人工重试"))
        .hasMessageContaining("无权处理简历");
    assertThatThrownBy(() -> manager.adoptIntakeResult("resume", processing))
        .hasMessageContaining("无权处理简历");
    verifyNoInteractions(tasks, resumeVersions, model, extraction);
  }

  @Test
  void refusesMismatchedContentBeforeEnqueue() {
    processing.setContentSha256("different");
    assertThatThrownBy(() -> manager.adoptIntakeResult("resume", processing))
        .hasMessageContaining("与简历内容不一致");
    verifyNoInteractions(tasks, model, extraction);
  }

  @Test
  void replayKeepsExistingHumanOrAiSnapshot() {
    var snapshot = new FactSnapshot(); snapshot.setId("existing");
    when(persistence.findCurrentFactSnapshot("resume")).thenReturn(snapshot);
    manager.processIntakeAdoption(task(), processing);
    var order = inOrder(resumeVersions, tasks);
    order.verify(resumeVersions).lockForRouting("resume");
    order.verify(tasks).renewLease(task());
    verify(tasks).succeed(task(), "existing");
    verifyNoInteractions(model, extraction);
  }

  @Test
  void retriesOriginalFailedAdoptionWithoutReparsing() {
    TaskSnapshot failed = mock(TaskSnapshot.class);
    when(failed.id()).thenReturn("original-task");
    when(failed.taskType()).thenReturn("RESUME_INTAKE_ADOPT");
    when(failed.resourceId()).thenReturn("resume");
    when(failed.status()).thenReturn("FAILED");
    when(failed.retryable()).thenReturn(true);
    when(tasks.getByResourceIdempotency("RESUME_INTAKE_ADOPT", "RESUME_VERSION", "resume", "intake-adopt:resume:processing")).thenReturn(failed);
    manager.retryIntakeAdoption("resume", "processing", "人工重试");
    verify(tasks).retryFailed("original-task", "人工重试");
    verify(tasks, never()).enqueue(any());
    verifyNoInteractions(model, extraction);
  }

  @Test
  void anotherAuthorizedOperatorRetriesTheOriginalAdoption() {
    var actor = new CurrentActorSnapshot(); actor.setUserId("other-hr");
    when(authorization.currentActor()).thenReturn(actor);
    TaskSnapshot failed = mock(TaskSnapshot.class);
    when(failed.id()).thenReturn("original-task");
    when(failed.taskType()).thenReturn("RESUME_INTAKE_ADOPT");
    when(failed.resourceId()).thenReturn("resume");
    when(failed.status()).thenReturn("FAILED");
    when(failed.retryable()).thenReturn(true);
    when(tasks.getByResourceIdempotency("RESUME_INTAKE_ADOPT", "RESUME_VERSION", "resume", "intake-adopt:resume:processing")).thenReturn(failed);
    manager.retryIntakeAdoption("resume", "processing", "人工重试");
    verify(tasks).retryFailed("original-task", "人工重试");
    verify(tasks, never()).enqueue(any());
    verifyNoInteractions(model, extraction);
  }

  @Test
  void doesNotPretendCancelledAdoptionWasRetried() {
    TaskSnapshot cancelled = mock(TaskSnapshot.class);
    when(cancelled.taskType()).thenReturn("RESUME_INTAKE_ADOPT");
    when(cancelled.resourceId()).thenReturn("resume");
    when(cancelled.status()).thenReturn("CANCELLED");
    when(tasks.getByResourceIdempotency("RESUME_INTAKE_ADOPT", "RESUME_VERSION", "resume", "intake-adopt:resume:processing")).thenReturn(cancelled);
    assertThatThrownBy(() -> manager.retryIntakeAdoption("resume", "processing", "人工重试"))
        .hasMessageContaining("已取消");
    verify(tasks, never()).retryFailed(anyString(), anyString());
    verifyNoInteractions(model, extraction);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = "2026-09-01")
  void materializesOriginalPagesEvidenceAndModelMetadataWithoutReparsing(String originalDate) {
    var source = new IntakeParsedSourceDTO("extractor", "NATIVE_TEXT", 1, 1, 0, 1, 1,
        List.of(new IntakeParsedSourceDTO.Page(1, "NATIVE_TEXT", "合成文本", 4, 1, 1, "pagehash")),
        "schema", "prompt", 123, 45, 600);
    LocalDate ageAsOf = originalDate == null ? null : LocalDate.parse(originalDate);
    source.setAgeAsOf(ageAsOf);
    ObjectNode sourceJson = json.valueToTree(source);
    if (ageAsOf == null) sourceJson.remove("ageAsOf");
    processing.setExtractedPagesJson(json.writeValueAsString(sourceJson));
    processing.setModelProvider("provider"); processing.setModelVersion("modelversion");
    processing.setRawOutputObjectKey("archive"); processing.setRawOutputHash("rawhash");
    processing.setRawOutputSize(500L); processing.setEncryptionVersion("enc");
    var runs = mock(ExtractionRunPersistenceService.class);
    var pages = mock(ResumeExtractionPagePersistenceService.class);
    var models = mock(ResumeModelRunPersistenceService.class);
    var facts = mock(ResumeFactPersistenceManager.class);
    var validator = mock(ResumeParseContractValidator.class);
    var transaction = mock(TransactionTemplate.class);
    inject("extractionRunPersistenceService", runs); inject("resumeExtractionPagePersistenceService", pages);
    inject("resumeModelRunPersistenceService", models); inject("factPersistence", facts);
    inject("validator", validator); inject("transactionTemplate", transaction);
    doAnswer(invocation -> {
      Consumer<TransactionStatus> action = invocation.getArgument(0);
      action.accept(mock(TransactionStatus.class)); return null;
    }).when(transaction).executeWithoutResult(any());
    when(runs.insertExtractionRun(anyString(), anyString(), anyString(), any())).thenReturn(1);
    when(pages.insertExtractionPages(any())).thenReturn(true);
    when(runs.completeExtraction(anyString(), anyString(), anyString(), anyInt(), anyInt(), anyInt(), any(), any(), any())).thenReturn(1);
    when(models.insertModelRun(anyString(), anyString(), anyString(), anyString(), anyString(), any())).thenReturn(1);
    when(models.completeModelRun("model", "provider", "modelversion", "archive", "rawhash", 500, "enc", 123, 45, 600, now)).thenReturn(1);
    var parsed = new ResumeFacts(null, List.of(), List.of(), List.of(), List.of(), List.of(),
        List.of(), List.of(), List.of(), List.of());
    when(validator.validate(anyString(), any())).thenReturn(parsed);
    var snapshot = new FactSnapshot(); snapshot.setId("snapshot");
    when(facts.storeAiFacts(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), any(), eq(ageAsOf))).thenReturn(snapshot);
    manager.processIntakeAdoption(task(), processing);
    verify(models).completeModelRun("model", "provider", "modelversion", "archive", "rawhash", 500, "enc", 123, 45, 600, now);
    verify(tasks).succeed(task(), "snapshot");
    verify(facts).storeAiFacts(eq("resume"), eq("extract"), eq("model"), eq("schema"),
        eq("prompt"), anyString(), eq(parsed), eq(ageAsOf));
    verifyNoInteractions(model, extraction);
  }

  private TaskSnapshot task() {
    return new TaskSnapshot("task", "RESUME_INTAKE_ADOPT", "RESUME_VERSION", "resume", "requester", "requester",
        "dept", "talent.read", "talent.manage", "RUNNING", 1, 3, 0, null, null, false, null, 0, now, now, null, now);
  }
  private void inject(String field, Object value) { ReflectionTestUtils.setField(manager, field, value); }
}
