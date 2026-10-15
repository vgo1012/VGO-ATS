package com.ezaigc.hr.recruiting.interview.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.argThat;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.identity.api.WecomCollaborationDirectoryService;
import com.ezaigc.hr.recruiting.interview.api.InterviewExternalCalendarPort;
import com.ezaigc.hr.recruiting.interview.domain.dto.InterviewExternalCalendarDTO;
import com.ezaigc.hr.recruiting.interview.domain.dto.InterviewExternalCalendarReconciliationDTO;
import com.ezaigc.hr.recruiting.interview.domain.entity.Interview;
import com.ezaigc.hr.recruiting.interview.domain.entity.InterviewExternalCalendarAttempt;
import com.ezaigc.hr.recruiting.interview.domain.entity.InterviewExternalCalendarProjection;
import com.ezaigc.hr.recruiting.interview.service.InterviewExternalCalendarAttemptPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.InterviewExternalCalendarProjectionPersistenceService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** 当前 API 的行为回归；复制持久对象，避免共享 mock 引用掩盖未成功写入。 */
class InterviewExternalCalendarCreateRecoveryTest {
    private static final Instant NOW = Instant.parse("2026-10-08T03:00:00Z");
    private static final String PROJECTION_ID = "P".repeat(26);
    private static final String INTERVIEW_ID = "I".repeat(26);
    private static final String ACTOR_ID = "A".repeat(26);
    private static final String OLD_LEASE = "L".repeat(26);

    private final InterviewExternalCalendarProjectionPersistenceService projections =
            mock(InterviewExternalCalendarProjectionPersistenceService.class);
    private final InterviewExternalCalendarAttemptPersistenceService attempts =
            mock(InterviewExternalCalendarAttemptPersistenceService.class);
    private final InterviewExternalCalendarPort port = mock(InterviewExternalCalendarPort.class);
    private final AtomicInteger createCalls = new AtomicInteger();
    private final List<InterviewExternalCalendarAttempt> savedAttempts = new ArrayList<>();
    private final ObjectMapper json = new ObjectMapper();
    private InterviewExternalCalendarApplicationManager manager;
    private InterviewExternalCalendarProjection stored;
    private boolean rejectNextProjectionUpdate;
    private boolean rejectNextAttemptUpdate;
    private TransactionTemplate transactions;

    @BeforeEach
    void setUp() {
        manager = new InterviewExternalCalendarApplicationManager();
        ReflectionTestUtils.setField(manager, "projections", projections);
        ReflectionTestUtils.setField(manager, "attempts", attempts);
        ReflectionTestUtils.setField(manager, "port", port);
        ReflectionTestUtils.setField(manager, "directory", mock(WecomCollaborationDirectoryService.class));
        ReflectionTestUtils.setField(manager, "json", json);
        PlatformIdGenerator ids = mock(PlatformIdGenerator.class);
        AtomicInteger generatedIds = new AtomicInteger();
        when(ids.newId()).thenAnswer(call -> "N".repeat(22)
                + String.format("%04d", generatedIds.incrementAndGet()));
        ReflectionTestUtils.setField(manager, "ids", ids);
        setClock(NOW);
        transactions = new TransactionTemplate(new MemoryTransactionManager());
        ReflectionTestUtils.setField(manager, "transactions", transactions);

        stored = pending();
        when(projections.findById(PROJECTION_ID)).thenAnswer(call -> copy(stored));
        when(projections.findByInterviewId(INTERVIEW_ID)).thenAnswer(call -> copy(stored));
        when(projections.findByIdForUpdate(PROJECTION_ID)).thenAnswer(call -> copy(stored));
        when(projections.findByInterviewIdForUpdate(INTERVIEW_ID)).thenAnswer(call -> copy(stored));
        when(projections.claim(anyString(), anyLong(), anyString(), any(), any())).thenAnswer(call -> {
            String id = call.getArgument(0);
            long version = call.getArgument(1);
            if (!stored.getId().equals(id) || stored.getVersion() != version) return 0;
            stored.setStatus("PROCESSING");
            stored.setLeaseToken(call.getArgument(2));
            stored.setLeaseUntil(call.getArgument(3));
            stored.setVersion(version + 1);
            return 1;
        });
        when(projections.claimCreate(anyString(), anyLong(), anyString(), any(), any(), nullable(String.class)))
                .thenAnswer(call -> {
                    long version = call.getArgument(1);
                    if (stored.getVersion() != version || !Objects.equals(stored.getCreateIntentId(), call.getArgument(5))) return 0;
                    stored.setStatus("PROCESSING");
                    stored.setLeaseToken(call.getArgument(2));
                    stored.setCreateIntentId(call.getArgument(2));
                    stored.setLeaseUntil(call.getArgument(3));
                    stored.setVersion(version + 1);
                    return 1;
                });
        when(projections.updateById(any())).thenAnswer(call -> {
            InterviewExternalCalendarProjection changed = call.getArgument(0);
            if (rejectNextProjectionUpdate && changed.getActiveType() != null) {
                rejectNextProjectionUpdate = false;
                return false;
            }
            if (changed.getVersion() != stored.getVersion()) return false;
            stored = copy(changed);
            stored.setVersion(stored.getVersion() + 1);
            return true;
        });
        when(attempts.save(any())).thenAnswer(call -> {
            InterviewExternalCalendarAttempt value = call.getArgument(0);
            InterviewExternalCalendarAttempt detached = new InterviewExternalCalendarAttempt();
            BeanUtils.copyProperties(value, detached);
            savedAttempts.add(detached);
            return true;
        });
        when(attempts.findForUpdate(anyString())).thenAnswer(call -> savedAttempts.stream()
                .filter(value -> value.getId().equals(call.getArgument(0))).findFirst()
                .map(InterviewExternalCalendarCreateRecoveryTest::copyAttempt).orElse(null));
        when(attempts.updateById(any())).thenAnswer(call -> {
            if (rejectNextAttemptUpdate) {
                rejectNextAttemptUpdate = false;
                return false;
            }
            InterviewExternalCalendarAttempt changed = call.getArgument(0);
            for (int index = 0; index < savedAttempts.size(); index++) {
                InterviewExternalCalendarAttempt before = savedAttempts.get(index);
                if (before.getId().equals(changed.getId()) && before.getVersion().equals(changed.getVersion())) {
                    InterviewExternalCalendarAttempt updated = copyAttempt(changed);
                    updated.setVersion(before.getVersion() + 1);
                    savedAttempts.set(index, updated);
                    return true;
                }
            }
            return false;
        });
        when(port.create(any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            createCalls.incrementAndGet();
            return new InterviewExternalCalendarPort.Result(true, false, "existing-meeting",
                    null, null, "ACTIVE", null, null);
        });
    }

    @Test
    void unconfirmedCreateIsNotAutomaticallyRepeated() {
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            createCalls.incrementAndGet();
            throw new IllegalStateException("synthetic response lost after dispatch");
        }).when(port).create(any());

        manager.process(PROJECTION_ID);
        setClock(NOW.plusSeconds(16));
        manager.process(PROJECTION_ID);

        assertThat(createCalls.get()).isEqualTo(1);
        assertThat(stored.getStatus()).isEqualTo("UNKNOWN");
    }

    @Test
    void expiredCreateLeaseDoesNotInvokeCreateAgain() {
        stored.setStatus("PROCESSING");
        stored.setLeaseToken(OLD_LEASE);
        stored.setLeaseUntil(NOW.minusSeconds(1));

        manager.process(PROJECTION_ID);

        assertThat(createCalls.get()).isZero();
        assertThat(stored.getStatus()).isEqualTo("UNKNOWN");
    }

    @Test
    void cancellationPreservesOutstandingCreateWhileChangingLocalDesire() {
        outstandingCreate();

        manager.cancel(INTERVIEW_ID, ACTOR_ID);

        assertThat(stored.getDesiredType()).isEqualTo("LOCAL_ONLY");
        assertThat(stored.getStatus()).isEqualTo("PROCESSING");
        assertThat(stored.getLeaseToken()).isEqualTo(OLD_LEASE);
        assertThat(stored.getOperationRevision()).isEqualTo(2);
    }

    @Test
    void configurationPreservesOutstandingCreateWhenSwitchingToLocal() {
        outstandingCreate();

        manager.configure(interview(), List.of(),
                new InterviewExternalCalendarDTO("LOCAL_ONLY", null, null, null,
                        List.of(), null, null, null, null), ACTOR_ID);

        assertThat(stored.getDesiredType()).isEqualTo("LOCAL_ONLY");
        assertThat(stored.getStatus()).isEqualTo("PROCESSING");
        assertThat(stored.getLeaseToken()).isEqualTo(OLD_LEASE);
        assertThat(stored.getOperationRevision()).isEqualTo(2);
    }

    @Test
    void manualRetryCannotResetLegacyAmbiguousCreateFailure() {
        stored.setStatus("FAILED");
        stored.setLastErrorCode("WECOM_503");
        stored.setLastErrorDetail("旧版本未区分已发请求和网络失败");

        assertThatThrownBy(() -> manager.retry(INTERVIEW_ID, ACTOR_ID))
                .isInstanceOf(RuntimeException.class);
        assertThat(stored.getStatus()).isEqualTo("FAILED");
        assertThat(stored.getLastErrorCode()).isEqualTo("WECOM_503");
    }

    @Test
    void successfulCreateSurvivesProjectionCasFailureWithoutCreatingAgain() {
        rejectNextProjectionUpdate = true;
        // 允许新实现暴露持久化失败；恢复的关键是保留回执并且不再 CREATE。
        catchThrowable(() -> manager.process(PROJECTION_ID));
        setClock(NOW.plusSeconds(121));

        manager.process(PROJECTION_ID);

        assertThat(createCalls.get()).isEqualTo(1);
        assertThat(stored.getExternalObjectId()).isEqualTo("existing-meeting");
        assertThat(savedAttempts).anySatisfy(attempt -> {
            assertThat(attempt.getStatus()).isEqualTo("SUCCEEDED");
            assertThat(attempt.getExternalObjectId()).isEqualTo("existing-meeting");
        });
    }

    @Test
    void failedIntentSavePreventsTheRemoteCall() {
        doReturn(false).when(attempts).save(any());
        assertThatThrownBy(() -> manager.process(PROJECTION_ID)).isInstanceOf(RuntimeException.class);
        assertThat(createCalls.get()).isZero();
        assertThat(stored.getStatus()).isEqualTo("PENDING");
        assertThat(stored.getCreateIntentId()).isNull();
        assertThat(savedAttempts).isEmpty();
    }

    @Test
    void failedIntentClaimRollsBackTheIntentAndPreventsTheRemoteCall() {
        doReturn(0).when(projections).claimCreate(anyString(), anyLong(), anyString(), any(), any(), nullable(String.class));
        assertThatThrownBy(() -> manager.process(PROJECTION_ID)).isInstanceOf(RuntimeException.class);
        assertThat(createCalls.get()).isZero();
        assertThat(stored.getStatus()).isEqualTo("PENDING");
        assertThat(savedAttempts).isEmpty();
    }

    @Test
    void failedReceiptPersistenceCannotCauseAnotherCreate() {
        rejectNextAttemptUpdate = true;
        assertThatThrownBy(() -> manager.process(PROJECTION_ID)).isInstanceOf(RuntimeException.class);
        setClock(NOW.plusSeconds(121));
        manager.process(PROJECTION_ID);
        assertThat(createCalls.get()).isEqualTo(1);
        assertThat(stored.getStatus()).isEqualTo("UNKNOWN");
        assertThat(stored.getCreateIntentId()).isNotNull();
    }

    @Test
    void cancellationAfterUnknownThenAdoptionCancelsOnlyTheOriginalObject() {
        makeUnknown();
        String originalIntent = stored.getCreateIntentId();
        manager.cancel(INTERVIEW_ID, ACTOR_ID);
        assertThat(stored.getStatus()).isEqualTo("UNKNOWN");
        assertThat(stored.getCreateIntentId()).isEqualTo(originalIntent);
        reconcile("ADOPT_EXISTING", "existing-meeting", "已由管理员核对原会议", false);
        doReturn(new InterviewExternalCalendarPort.Result(true, false, "existing-meeting", null, null, "CANCELLED", null, null))
                .when(port).cancel(any());
        manager.process(PROJECTION_ID);
        verify(port).cancel(argThat(value -> "existing-meeting".equals(value.externalObjectId())));
        assertThat(createCalls.get()).isEqualTo(1);
        assertThat(stored.getStatus()).isEqualTo("CANCELLED");
        assertThat(savedAttempts).anySatisfy(value -> {
            assertThat(value.getErrorCode()).isEqualTo("WECOM_MANUAL_CREATE_ADOPT");
            assertThat(value.getErrorDetail()).isEqualTo("已由管理员核对原会议");
            assertThat(value.getCreateId()).isEqualTo(ACTOR_ID);
            assertThat(value.getRemark()).contains(originalIntent);
        });
        assertThat(savedAttempts).anySatisfy(value -> {
            assertThat(value.getId()).isEqualTo(originalIntent);
            assertThat(value.getStatus()).isEqualTo("UNKNOWN");
        });
    }

    @Test
    void confirmingNoCreationRequiresExplicitRequestTermination() {
        makeUnknown();
        String originalIntent = stored.getCreateIntentId();
        assertThatThrownBy(() -> reconcile("CONFIRM_NOT_CREATED", null, "已核对远端", false))
                .isInstanceOf(RuntimeException.class);
        assertThat(stored.getStatus()).isEqualTo("UNKNOWN");
        assertThat(stored.getCreateIntentId()).isEqualTo(originalIntent);
        assertThat(savedAttempts).hasSize(1);
    }

    @Test
    void explicitConfirmedNoCreationIsAuditedBeforeStartingANewIntent() {
        makeUnknown();
        String originalIntent = stored.getCreateIntentId();
        reconcile("CONFIRM_NOT_CREATED", null, "原实例已停机且管理员确认无会议", true);
        successfulPort("new-meeting");
        manager.process(PROJECTION_ID);
        assertThat(createCalls.get()).isEqualTo(2);
        assertThat(stored.getExternalObjectId()).isEqualTo("new-meeting");
        assertThat(savedAttempts).anySatisfy(value -> {
            assertThat(value.getErrorCode()).isEqualTo("WECOM_MANUAL_CREATE_NOT_CREATED");
            assertThat(value.getCreateId()).isEqualTo(ACTOR_ID);
            assertThat(value.getErrorDetail()).contains("原实例已停机");
            assertThat(value.getRemark()).contains(originalIntent);
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reconciliationRejectsChangedVersionOrIntent(boolean changedVersion) {
        makeUnknown();
        String originalIntent = stored.getCreateIntentId();
        InterviewExternalCalendarReconciliationDTO command = new InterviewExternalCalendarReconciliationDTO(
                stored.getVersion() + (changedVersion ? 1 : 0), changedVersion ? originalIntent : "X".repeat(26),
                "ADOPT_EXISTING", "existing-meeting", "已核对", false);
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> manager.reconcile(INTERVIEW_ID, command, ACTOR_ID)))
                .isInstanceOf(RuntimeException.class);
        assertThat(stored.getCreateIntentId()).isEqualTo(originalIntent);
        assertThat(savedAttempts).hasSize(1);
    }

    @Test
    void reconciliationRejectsMissingReasonWithoutChangingTheIntent() {
        makeUnknown();
        assertThatThrownBy(() -> reconcile("ADOPT_EXISTING", "existing-meeting", " ", false))
                .isInstanceOf(RuntimeException.class);
        assertThat(stored.getStatus()).isEqualTo("UNKNOWN");
        assertThat(savedAttempts).hasSize(1);
    }

    @Test
    void failedReconciliationAuditRollsBackTheBinding() {
        makeUnknown();
        doReturn(false).when(attempts).save(any());
        assertThatThrownBy(() -> reconcile("ADOPT_EXISTING", "existing-meeting", "已核对", false))
                .isInstanceOf(RuntimeException.class);
        assertThat(stored.getStatus()).isEqualTo("UNKNOWN");
        assertThat(stored.getExternalObjectId()).isNull();
        assertThat(stored.getCreateIntentId()).isNotNull();
    }

    @Test
    void lateReceiptCannotBindAnIntentClosedByManualConfirmation() {
        doAnswer(call -> {
            createCalls.incrementAndGet();
            setClock(NOW.plusSeconds(121));
            manager.process(PROJECTION_ID);
            reconcile("CONFIRM_NOT_CREATED", null, "已核对原请求已终止", true);
            return new InterviewExternalCalendarPort.Result(true, false, "late-old-meeting", null, null, "ACTIVE", null, null);
        }).when(port).create(any());
        manager.process(PROJECTION_ID);
        assertThat(stored.getStatus()).isEqualTo("PENDING");
        assertThat(stored.getExternalObjectId()).isNull();
        successfulPort("new-meeting");
        manager.process(PROJECTION_ID);
        assertThat(createCalls.get()).isEqualTo(2);
        assertThat(stored.getExternalObjectId()).isEqualTo("new-meeting");
        assertThat(savedAttempts).anySatisfy(value -> {
            assertThat(value.getExternalObjectId()).isEqualTo("late-old-meeting");
            assertThat(value.getRemark()).startsWith("原创建意图 ");
        });
    }

    private void makeUnknown() {
        doAnswer(call -> {
            createCalls.incrementAndGet();
            throw new IllegalStateException("synthetic unconfirmed creation");
        }).when(port).create(any());
        manager.process(PROJECTION_ID);
        assertThat(stored.getStatus()).isEqualTo("UNKNOWN");
    }

    private void successfulPort(String id) {
        doAnswer(call -> {
            createCalls.incrementAndGet();
            return new InterviewExternalCalendarPort.Result(true, false, id, null, null, "ACTIVE", null, null);
        }).when(port).create(any());
    }

    private void reconcile(String action, String externalId, String reason, boolean terminated) {
        InterviewExternalCalendarReconciliationDTO command = new InterviewExternalCalendarReconciliationDTO(
                stored.getVersion(), stored.getCreateIntentId(), action, externalId, reason, terminated);
        transactions.executeWithoutResult(status -> manager.reconcile(INTERVIEW_ID, command, ACTOR_ID));
    }

    private void outstandingCreate() {
        stored.setStatus("PROCESSING");
        stored.setLeaseToken(OLD_LEASE);
        stored.setLeaseUntil(NOW.plusSeconds(120));
    }

    private void setClock(Instant value) {
        ReflectionTestUtils.setField(manager, "clock", Clock.fixed(value, ZoneOffset.UTC));
    }

    private static InterviewExternalCalendarProjection copy(InterviewExternalCalendarProjection value) {
        InterviewExternalCalendarProjection detached = new InterviewExternalCalendarProjection();
        BeanUtils.copyProperties(value, detached);
        return detached;
    }

    private static InterviewExternalCalendarAttempt copyAttempt(InterviewExternalCalendarAttempt value) {
        InterviewExternalCalendarAttempt detached = new InterviewExternalCalendarAttempt();
        BeanUtils.copyProperties(value, detached);
        return detached;
    }

    /** 验证真实 TransactionTemplate 的独立提交/回滚，不代表真实 MySQL 或跨实例锁实测。 */
    private final class MemoryTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Snapshot();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            Snapshot snapshot = (Snapshot) transaction;
            snapshot.projection = copy(stored);
            snapshot.attempts = savedAttempts.stream().map(InterviewExternalCalendarCreateRecoveryTest::copyAttempt).toList();
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {}

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            Snapshot snapshot = (Snapshot) status.getTransaction();
            stored = copy(snapshot.projection);
            savedAttempts.clear();
            snapshot.attempts.forEach(value -> savedAttempts.add(copyAttempt(value)));
        }
    }

    private static final class Snapshot {
        private InterviewExternalCalendarProjection projection;
        private List<InterviewExternalCalendarAttempt> attempts;
    }

    private static InterviewExternalCalendarProjection pending() {
        InterviewExternalCalendarProjection value = new InterviewExternalCalendarProjection();
        value.setId(PROJECTION_ID);
        value.setInterviewId(INTERVIEW_ID);
        value.setProvider("WECOM");
        value.setDesiredType("WECOM_MEETING");
        value.setStatus("PENDING");
        value.setOperationRevision(1);
        value.setTitle("小型合法面试夹具");
        value.setAdminUserId("admin-wecom");
        value.setInviteeUserIdsJson("[\"admin-wecom\"]");
        value.setAdditionalInviteeUserIdsJson("[]");
        value.setStartAt(NOW.plusSeconds(3600));
        value.setEndAt(NOW.plusSeconds(5400));
        value.setTimezoneId("Asia/Shanghai");
        value.setNextAttemptAt(NOW);
        value.setVersion(0);
        return value;
    }

    private static Interview interview() {
        Interview value = new Interview();
        value.setId(INTERVIEW_ID);
        value.setScheduledStartAt(NOW.plusSeconds(3600));
        value.setScheduledEndAt(NOW.plusSeconds(5400));
        value.setTimezoneId("Asia/Shanghai");
        value.setLocationText("线上");
        return value;
    }
}
