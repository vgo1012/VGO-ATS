package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.CandidateIdentity;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.ResumeFacts;
import com.ezaigc.hr.talent.domain.dto.CorrectResumeFactsDTO;
import com.ezaigc.hr.talent.domain.dto.CorrectionPersistenceResultDTO;
import com.ezaigc.hr.talent.domain.entity.FactSnapshot;
import com.ezaigc.hr.talent.domain.entity.ResumeResourceEntity;
import com.ezaigc.hr.talent.service.EducationFactPersistenceService;
import com.ezaigc.hr.talent.service.CandidatePersistenceService;
import com.ezaigc.hr.talent.service.ProjectFactPersistenceService;
import com.ezaigc.hr.talent.service.ResumeParsingPersistenceService;
import com.ezaigc.hr.talent.service.ResumeVersionPersistenceService;
import com.ezaigc.hr.talent.service.SimpleFactPersistenceService;
import com.ezaigc.hr.talent.service.SkillFactPersistenceService;
import com.ezaigc.hr.talent.service.WorkFactPersistenceService;
import com.ezaigc.hr.talent.service.validator.ResumeParseContractValidator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

class ResumeFactCorrectionOwnershipTransactionTest {

  private static final String VERSION_ID = "V".repeat(26);
  private static final String CANDIDATE_ID = "C".repeat(26);
  private static final String OWNER_A = "A".repeat(26);
  private static final String OWNER_B = "B".repeat(26);
  private static final String SNAPSHOT_ID = "S".repeat(26);
  private final ResumeParsingPersistenceService persistence = mock(ResumeParsingPersistenceService.class);
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final ResumeFactPersistenceManager writer = mock(ResumeFactPersistenceManager.class);
  private final CandidatePersistenceService candidates = mock(CandidatePersistenceService.class);
  private final ResumeVersionPersistenceService versions = mock(ResumeVersionPersistenceService.class);
  private final CurrentActorSnapshot actor = new CurrentActorSnapshot();
  private final List<String> stages = new ArrayList<>();
  private final AtomicInteger storedCorrections = new AtomicInteger();
  private final RecordingTransactionManager transactions = new RecordingTransactionManager();
  private final AtomicReference<ResumeResourceEntity> currentResource =
      new AtomicReference<>(resource(OWNER_A));

  @Test
  void rejectsCorrectionWhenOwnershipChangedAfterPreliminaryLookupButFactsDidNotChange() {
    ResumeParsingApplicationManager manager = manager();
    doAnswer(call -> {
      ResumeResourceEntity oldSnapshot = currentResource.get();
      // 预查返回前另一领取事务已提交；旧归属快照与当前事实 ID 仍可留在请求中。
      currentResource.set(resource(OWNER_B));
      return oldSnapshot;
    }).when(persistence).findResumeResource(VERSION_ID);

    assertThatThrownBy(() -> manager.correct(command()))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("OBJECT_SCOPE_FORBIDDEN"));

    verify(writer, never()).storeCorrection(anyString(), anyString(), anyString(), anyString(),
        anyString(), anyList(), anyList(), any());
    verify(persistence, never()).findCurrentFactSnapshotForUpdate(VERSION_ID);
    assertThat(storedCorrections.get()).isZero();
    assertThat(transactions.rolledBack).isTrue();
  }

  @Test
  void authorizedCorrectionLocksAndWritesInsideOneTransaction() {
    var response = manager().correct(command());

    assertThat(response.getFactSnapshotId()).isEqualTo("N".repeat(26));
    assertThat(stages).containsExactly("CANDIDATE", "VERSION", "RESOURCE", "AUTHORIZE", "FACT", "WRITE");
    assertThat(storedCorrections.get()).isOne();
    assertThat(transactions.committed).isTrue();
    verify(persistence, never()).findCurrentFactSnapshot(VERSION_ID);
  }

  @Test
  void currentOwnerCanCorrectAfterPreliminaryLookupReturnedPreviousOwner() {
    ResumeParsingApplicationManager manager = manager();
    actor.setUserId(OWNER_B);
    doAnswer(call -> {
      ResumeResourceEntity previous = currentResource.get();
      currentResource.set(resource(OWNER_B));
      return previous;
    }).when(persistence).findResumeResource(VERSION_ID);

    manager.correct(command());

    verify(writer).storeCorrection(eq(VERSION_ID), eq(SNAPSHOT_ID), eq(OWNER_B), anyString(),
        anyString(), anyList(), anyList(), any());
    assertThat(transactions.committed).isTrue();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void rejectsUnavailableOrMovedResourceBeforeAuthorizationAndWriting(boolean moved) {
    ResumeParsingApplicationManager manager = manager();
    doAnswer(call -> {
      ResumeResourceEntity previous = currentResource.get();
      ResumeResourceEntity unavailable = resource(OWNER_A);
      unavailable.setCandidateId("X".repeat(26));
      currentResource.set(moved ? unavailable : null);
      return previous;
    }).when(persistence).findResumeResource(VERSION_ID);

    assertThatThrownBy(() -> manager.correct(command()))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("RESUME_VERSION_CHANGED"));

    assertThat(stages).containsExactly("CANDIDATE", "VERSION", "RESOURCE");
    verify(authorization, never()).requireAccess(anyString(), any());
    verify(writer, never()).storeCorrection(anyString(), anyString(), anyString(), anyString(),
        anyString(), anyList(), anyList(), any());
  }

  @Test
  void stopsBeforeVersionLockWhenCandidateNoLongerExists() {
    ResumeParsingApplicationManager manager = manager();
    doReturn(null).when(candidates).lockCandidate(CANDIDATE_ID);

    assertThatThrownBy(() -> manager.correct(command()))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("RESUME_VERSION_CHANGED"));

    verify(versions, never()).lockForRouting(anyString());
    verify(writer, never()).storeCorrection(anyString(), anyString(), anyString(), anyString(),
        anyString(), anyList(), anyList(), any());
  }

  @Test
  void stopsBeforeCurrentResourceReadWhenVersionNoLongerExists() {
    ResumeParsingApplicationManager manager = manager();
    doReturn(null).when(versions).lockForRouting(VERSION_ID);

    assertThatThrownBy(() -> manager.correct(command())).isInstanceOf(PlatformException.class);

    verify(persistence, never()).findResumeResourceForUpdate(anyString(), anyString());
    verify(writer, never()).storeCorrection(anyString(), anyString(), anyString(), anyString(),
        anyString(), anyList(), anyList(), any());
  }

  @Test
  void rejectsChangedBaseSnapshotBeforeComputingOrWritingCorrection() {
    ResumeParsingApplicationManager manager = manager();
    CorrectResumeFactsDTO stale = command();
    stale.setBaseFactSnapshotId("O".repeat(26));

    assertThatThrownBy(() -> manager.correct(stale))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("FACT_SNAPSHOT_CHANGED"));

    assertThat(stages).containsExactly("CANDIDATE", "VERSION", "RESOURCE", "AUTHORIZE", "FACT");
    verify(writer, never()).storeCorrection(anyString(), anyString(), anyString(), anyString(),
        anyString(), anyList(), anyList(), any());
  }

  @Test
  void writerFailureRollsBackTheCorrectionTransaction() {
    ResumeParsingApplicationManager manager = manager();
    doAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      storedCorrections.incrementAndGet();
      throw new IllegalStateException("synthetic persistence failure");
    }).when(writer).storeCorrection(anyString(), anyString(), anyString(), anyString(),
        anyString(), anyList(), anyList(), any());

    assertThatThrownBy(() -> manager.correct(command())).isInstanceOf(IllegalStateException.class);

    assertThat(storedCorrections.get()).isZero();
    assertThat(transactions.rolledBack).isTrue();
    assertThat(transactions.committed).isFalse();
  }

  private ResumeParsingApplicationManager manager() {
    ResumeParsingApplicationManager manager = new ResumeParsingApplicationManager();
    ObjectMapper json = new ObjectMapper();
    Clock clock = Clock.fixed(Instant.parse("2026-10-08T00:00:00Z"), ZoneOffset.UTC);
    ResumeParseContractValidator validator = new ResumeParseContractValidator();
    ReflectionTestUtils.setField(validator, "objectMapper", json);
    ReflectionTestUtils.setField(validator, "clock", clock);
    ReflectionTestUtils.setField(manager, "json", json);
    ReflectionTestUtils.setField(manager, "validator", validator);
    ReflectionTestUtils.setField(manager, "persistence", persistence);
    ReflectionTestUtils.setField(manager, "candidates", candidates);
    ReflectionTestUtils.setField(manager, "resumeVersions", versions);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "factPersistence", writer);
    ReflectionTestUtils.setField(manager, "workFactPersistenceService", mock(WorkFactPersistenceService.class));
    ReflectionTestUtils.setField(manager, "projectFactPersistenceService", mock(ProjectFactPersistenceService.class));
    ReflectionTestUtils.setField(manager, "educationFactPersistenceService", mock(EducationFactPersistenceService.class));
    ReflectionTestUtils.setField(manager, "skillFactPersistenceService", mock(SkillFactPersistenceService.class));
    ReflectionTestUtils.setField(manager, "simpleFactPersistenceService", mock(SimpleFactPersistenceService.class));
    actor.setUserId(OWNER_A);
    when(authorization.currentActor()).thenReturn(actor);
    doAnswer(call -> {
      AuthorizationTarget target = call.getArgument(1);
      recordStage("AUTHORIZE");
      if (!actor.getUserId().equals(target.getOwnerUserId())) {
        throw new PlatformException(HttpStatus.FORBIDDEN, "OBJECT_SCOPE_FORBIDDEN", "归属已变化", false);
      }
      return null;
    }).when(authorization).requireAccess(eq("talent.read"), any());
    when(persistence.findResumeResource(VERSION_ID)).thenAnswer(call -> currentResource.get());
    when(persistence.findResumeResourceForUpdate(VERSION_ID, CANDIDATE_ID))
        .thenAnswer(call -> {
          recordStage("RESOURCE");
          ResumeResourceEntity current = currentResource.get();
          return current != null && CANDIDATE_ID.equals(current.getCandidateId()) ? current : null;
        });
    when(candidates.lockCandidate(CANDIDATE_ID)).thenAnswer(call -> {
      recordStage("CANDIDATE");
      return CANDIDATE_ID;
    });
    when(versions.lockForRouting(VERSION_ID)).thenAnswer(call -> {
      recordStage("VERSION");
      return VERSION_ID;
    });
    FactSnapshot facts = new FactSnapshot();
    facts.setId(SNAPSHOT_ID);
    facts.setResumeVersionId(VERSION_ID);
    facts.setFullName("原始姓名");
    facts.setSnapshotVersion(1);
    when(persistence.findCurrentFactSnapshot(VERSION_ID)).thenReturn(facts);
    when(persistence.findCurrentFactSnapshotForUpdate(VERSION_ID)).thenAnswer(call -> {
      recordStage("FACT");
      return facts;
    });
    when(writer.storeCorrection(anyString(), anyString(), anyString(), anyString(), anyString(),
        anyList(), anyList(), any())).thenAnswer(call -> {
          recordStage("WRITE");
          storedCorrections.incrementAndGet();
          return new CorrectionPersistenceResultDTO("F".repeat(26), "N".repeat(26), 2, List.of());
        });
    ProxyFactory factory = new ProxyFactory(manager);
    factory.setProxyTargetClass(true);
    TransactionInterceptor interceptor = new TransactionInterceptor();
    interceptor.setTransactionManager(transactions);
    interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
    factory.addAdvice(interceptor);
    return (ResumeParsingApplicationManager) factory.getProxy();
  }

  private void recordStage(String stage) {
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
    stages.add(stage);
  }

  /** 只验证真实事务代理的提交/回滚边界，内存状态不代表真实 MySQL 锁实测。 */
  private final class RecordingTransactionManager extends AbstractPlatformTransactionManager {
    private int previousCorrections;
    private boolean committed;
    private boolean rolledBack;

    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
      previousCorrections = storedCorrections.get();
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
      committed = true;
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
      storedCorrections.set(previousCorrections);
      rolledBack = true;
    }
  }

  private static CorrectResumeFactsDTO command() {
    ResumeFacts facts = new ResumeFacts(
        new CandidateIdentity("核实姓名", null, null, null, null, null, null, null),
        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    return new CorrectResumeFactsDTO(VERSION_ID, SNAPSHOT_ID, "已向候选人核实", facts);
  }

  private static ResumeResourceEntity resource(String owner) {
    return new ResumeResourceEntity(VERSION_ID, "resume.pdf", "application/pdf", "hash", "object",
        CANDIDATE_ID, owner, "D".repeat(26));
  }
}
