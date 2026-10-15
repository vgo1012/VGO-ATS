package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.talent.api.InterviewMaterialAccessService;
import com.ezaigc.hr.talent.api.InterviewMaterialAccessService.InterviewMaterialAccess;
import com.ezaigc.hr.talent.api.ObjectStoragePort;
import com.ezaigc.hr.talent.domain.entity.ResumeVersion;
import com.ezaigc.hr.talent.service.TalentIntakePersistenceService;
import com.ezaigc.hr.talent.service.TalentProcessEventPersistenceService;
import java.io.InputStream;
import java.time.Clock;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class InterviewMaterialDownloadTest {
  private final TalentIntakeApplicationManager manager=new TalentIntakeApplicationManager();
  private final InterviewMaterialAccessService access=mock(InterviewMaterialAccessService.class);
  private final TalentIntakePersistenceService persistence=mock(TalentIntakePersistenceService.class);
  private final ObjectStoragePort storage=mock(ObjectStoragePort.class);
  private final TransactionTemplate transactions=mock(TransactionTemplate.class);
  private final TalentProcessEventPersistenceService events=mock(TalentProcessEventPersistenceService.class);
  @BeforeEach void setup() {
    ReflectionTestUtils.setField(manager,"interviewMaterialAccess",access);
    ReflectionTestUtils.setField(manager,"persistence",persistence);
    ReflectionTestUtils.setField(manager,"storage",storage);
    ReflectionTestUtils.setField(manager,"transactionTemplate",transactions);
    ReflectionTestUtils.setField(manager,"talentProcessEventPersistenceService",events);
    ReflectionTestUtils.setField(manager,"clock",Clock.systemUTC());
    var ids=mock(PlatformIdGenerator.class); when(ids.newId()).thenReturn("event");
    ReflectionTestUtils.setField(manager,"ids",ids);
    var authorization=mock(AuthorizationService.class);
    var actor=mock(CurrentActorSnapshot.class); when(actor.getUserId()).thenReturn("interviewer");
    when(authorization.currentActor()).thenReturn(actor);
    ReflectionTestUtils.setField(manager,"authorization",authorization);
    when(access.requireMaterialAccess("interview")).thenReturn(new InterviewMaterialAccess(
        "interview","candidate","frozen-resume","jd","jd-v1",1,"岗位","JD-000001","原文"));
  }
  @Test void revokedAuthorizationRejectsBothMetadataAndBytesBeforeReadingAnything() {
    when(access.requireMaterialAccess("interview")).thenThrow(PlatformExceptions.forbidden("DENIED","授权已取消"));
    assertThatThrownBy(()->manager.interviewMaterials("interview")).hasMessageContaining("授权已取消");
    assertThatThrownBy(()->manager.downloadInterviewResume("interview")).hasMessageContaining("授权已取消");
    verifyNoInteractions(persistence,storage,events);
  }
  @Test void missingOrUnavailableCandidateBoundVersionNeverOpensStorage() {
    assertThatThrownBy(()->manager.downloadInterviewResume("interview")).hasMessageContaining("不可用");
    var version=version(); version.setStatus("QUARANTINED");
    when(persistence.findCandidateResumeVersion("candidate","frozen-resume")).thenReturn(version);
    assertThatThrownBy(()->manager.downloadInterviewResume("interview")).hasMessageContaining("不可用");
    verifyNoInteractions(storage,events);
  }
  @Test void downloadsExactVersionAndRecordsAudit() throws Exception {
    var stream=mock(InputStream.class);
    when(persistence.findCandidateResumeVersion("candidate","frozen-resume")).thenReturn(version());
    when(storage.open("frozen/object")).thenReturn(stream);
    doAnswer(call->{call.<Consumer<TransactionStatus>>getArgument(0).accept(null);return null;})
        .when(transactions).executeWithoutResult(any());
    assertThat(manager.downloadInterviewResume("interview").getContent()).isSameAs(stream);
    verify(events).insertEvent(anyString(),eq("INTERVIEW_RESUME_DOWNLOADED"),eq("interviewer"),
        eq("RESUME_VERSION"),eq("frozen-resume"),eq("interviewId=interview"),any());
  }
  @Test void auditFailureClosesAlreadyOpenedStream() throws Exception {
    var stream=mock(InputStream.class);
    when(persistence.findCandidateResumeVersion("candidate","frozen-resume")).thenReturn(version());
    when(storage.open("frozen/object")).thenReturn(stream);
    doThrow(new IllegalStateException("audit failed")).when(transactions).executeWithoutResult(any());
    assertThatThrownBy(()->manager.downloadInterviewResume("interview")).hasMessageContaining("audit failed");
    verify(stream).close();
  }
  private ResumeVersion version() {
    var version=new ResumeVersion(); version.setId("frozen-resume"); version.setStatus("AVAILABLE");
    version.setObjectKey("frozen/object"); version.setOriginalFileName("original.pdf");
    version.setMediaType("application/pdf"); version.setSizeBytes(10); return version;
  }
}
