package com.ezaigc.hr.recruiting.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ezaigc.hr.recruiting.interview.domain.entity.InterviewExternalCalendarProjection;
import com.ezaigc.hr.recruiting.interview.domain.entity.InterviewExternalCalendarAttempt;
import com.ezaigc.hr.recruiting.interview.mapper.InterviewExternalCalendarProjectionMapper;
import com.ezaigc.hr.recruiting.interview.service.impl.InterviewExternalCalendarProjectionPersistenceServiceImpl;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class InterviewExternalCalendarRecoveryPersistenceTest {
    private static final Instant NOW = Instant.parse("2026-10-08T03:00:00Z");
    private final InterviewExternalCalendarProjectionMapper mapper = mock(InterviewExternalCalendarProjectionMapper.class);
    private final InterviewExternalCalendarProjectionPersistenceServiceImpl service =
            new InterviewExternalCalendarProjectionPersistenceServiceImpl();

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "calendar-test"),
                InterviewExternalCalendarProjection.class);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void currentReadsLockExactlyTheRequestedProjection(boolean byInterview) {
        AtomicReference<LambdaQueryWrapper<InterviewExternalCalendarProjection>> query = new AtomicReference<>();
        InterviewExternalCalendarProjection expected = new InterviewExternalCalendarProjection();
        when(mapper.selectOne(any())).thenAnswer(call -> {
            query.set(call.getArgument(0));
            return expected;
        });
        assertThat(byInterview ? service.findByInterviewIdForUpdate("interview") : service.findByIdForUpdate("projection"))
                .isSameAs(expected);
        assertThat(query.get().getSqlSegment()).endsWith("FOR UPDATE");
        assertThat(query.get().getParamNameValuePairs()).containsValue(byInterview ? "interview" : "projection");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void createClaimFencesVersionAndPreviousIntentAndLease(boolean previousIntent) {
        AtomicReference<LambdaUpdateWrapper<InterviewExternalCalendarProjection>> query = new AtomicReference<>();
        when(mapper.update(any())).thenAnswer(call -> {
            query.set(call.getArgument(0));
            return 1;
        });
        assertThat(service.claimCreate("projection", 7, "new-intent", NOW.plusSeconds(120), NOW,
                previousIntent ? "old-intent" : null)).isEqualTo(1);
        String where = query.get().getSqlSegment();
        assertThat(where).contains("version", "lease_until", "next_attempt_at", "active_type IS NULL", "create_intent_id");
        assertThat(query.get().getSqlSet().replace(" ", "")).contains("create_intent_id=", "lease_token=", "version=version+1");
        assertThat(query.get().getParamNameValuePairs()).containsValue(7L).containsValue("new-intent");
        if (previousIntent) assertThat(query.get().getParamNameValuePairs()).containsValue("old-intent");
        else assertThat(where).contains("create_intent_id IS NULL");
    }

    @Test
    void unknownDueQueryRequiresAConfirmedReceiptForTheSameIntentAndProjection() throws Exception {
        var method = InterviewExternalCalendarProjectionMapper.class.getMethod("findDue", Instant.class, int.class,
                String.class, String.class, String.class, String.class, String.class, String.class, String.class);
        String source = String.join(" ", method.getAnnotation(Select.class).value());
        BoundSql rendered = new XMLLanguageDriver().createSqlSource(new MybatisConfiguration(), source, Map.class)
                .getBoundSql(Map.of("now", NOW, "limit", 10, "pending", "PENDING", "failed", "FAILED",
                        "processing", "PROCESSING", "unknown", "UNKNOWN", "succeededAttempt", "SUCCEEDED",
                        "failedAttempt", "FAILED", "createOperation", "CREATE"));
        assertThat(rendered.getSql()).contains("EXISTS", "a.id=p.create_intent_id", "a.projection_id=p.id",
                "a.operation_type=?", "a.status IN (?,?)", "LIMIT ?").doesNotContain("#{", "${", "SELECT *");
        assertThat(rendered.getParameterMappings()).extracting(value -> value.getProperty())
                .contains("unknown", "succeededAttempt", "failedAttempt", "createOperation", "limit");
    }

    @Test
    void projectionCompletionActuallyBindsNullForClearedIntentLeaseAndObjectFields() {
        InterviewExternalCalendarProjection value = new InterviewExternalCalendarProjection();
        value.setId("projection");
        BoundSql update = updateSql(InterviewExternalCalendarProjection.class, value);
        assertThat(update.getParameterMappings()).extracting(mapping -> mapping.getProperty())
                .contains("et.createIntentId", "et.leaseToken", "et.leaseUntil", "et.activeType",
                        "et.externalObjectId", "et.meetingCode", "et.meetingLink", "et.lastErrorCode", "et.lastErrorDetail");
        assertThat(update.getSql()).contains("create_intent_id=", "lease_token=", "external_object_id=");
    }

    @Test
    void confirmedReceiptActuallyClearsPriorUnknownErrors() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "calendar-attempt-test"),
                InterviewExternalCalendarAttempt.class);
        BoundSql update = updateSql(InterviewExternalCalendarAttempt.class, new InterviewExternalCalendarAttempt());
        assertThat(update.getParameterMappings()).extracting(mapping -> mapping.getProperty())
                .contains("et.externalObjectId", "et.errorCode", "et.errorDetail");
    }

    private static BoundSql updateSql(Class<?> type, Object entity) {
        String source = "<script>UPDATE calendar SET " + TableInfoHelper.getTableInfo(type).getAllSqlSet(false, "et.")
                + " WHERE id=#{et.id}</script>";
        return new XMLLanguageDriver().createSqlSource(new MybatisConfiguration(), source, Map.class)
                .getBoundSql(Map.of("et", entity));
    }
}
