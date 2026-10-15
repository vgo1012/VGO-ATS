package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ezaigc.hr.talent.domain.entity.Candidate;
import com.ezaigc.hr.talent.domain.entity.ResumeJdScoreBinding;
import com.ezaigc.hr.talent.domain.entity.TalentProcessEvent;
import com.ezaigc.hr.talent.service.TalentProcessEventPersistenceService;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class SharedReclaimScoreApplicationManagerTest {
  private final TalentProcessEventPersistenceService events = mock(TalentProcessEventPersistenceService.class);
  private final SharedReclaimScoreApplicationManager manager = new SharedReclaimScoreApplicationManager();

  SharedReclaimScoreApplicationManagerTest() {
    TableInfoHelper.initTableInfo(
        new MapperBuilderAssistant(new MybatisConfiguration(), "shared-reclaim-score"),
        TalentProcessEvent.class);
    ReflectionTestUtils.setField(manager, "events", events);
  }

  @Test
  void usesStoredBindingWithoutReadingAudit() {
    Candidate candidate = candidate("{\"reclaimedScoreBindingId\":\"current\"}");

    assertThat(manager.claimedBindingId(candidate, List.of())).isEqualTo("current");
    verify(events, never()).getOne(any(LambdaQueryWrapper.class));
  }

  @Test
  void restoresOldClaimOnlyFromItsExactAuditAndScoreBinding() {
    Candidate candidate = candidate("{\"reclaimedResumeVersionId\":\"resume\",\"reclaimedAt\":\"2026-09-22T03:00:00Z\"}");
    TalentProcessEvent event = new TalentProcessEvent();
    event.setExt("{\"operationId\":\"reclaim-operation\",\"resumeVersionId\":\"resume\",\"jdVersionId\":\"jd\",\"defaultMatchRunId\":\"run\",\"toOwnerUserId\":\"owner\"}");
    when(events.getOne(any(LambdaQueryWrapper.class))).thenReturn(event);
    ResumeJdScoreBinding old = binding("old", "old-run");
    ResumeJdScoreBinding current = binding("claimed", "run");

    assertThat(manager.claimedBindingId(candidate, List.of(old, current))).isEqualTo("claimed");
    ArgumentCaptor<LambdaQueryWrapper> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    verify(events).getOne(query.capture());
    assertThat(query.getValue().getSqlSegment())
        .contains("target_type", "target_id", "event_type", "occurred_at", "LIMIT 1");
    assertThat(query.getValue().getParamNameValuePairs().values())
        .contains("CANDIDATE", "candidate", "SHARED_CANDIDATE_RECLAIMED",
            Instant.parse("2026-09-22T03:00:00Z"));
  }

  @Test
  void missingOrMismatchedAuditCannotSelectAnOldScore() {
    Candidate candidate = candidate("{\"reclaimedResumeVersionId\":\"resume\",\"reclaimedAt\":\"2026-09-22T03:00:00Z\"}");
    ResumeJdScoreBinding binding = binding("claimed", "run");
    assertThat(manager.claimedBindingId(candidate, List.of(binding))).isNull();

    TalentProcessEvent event = new TalentProcessEvent();
    event.setExt("{\"operationId\":\"operation\",\"resumeVersionId\":\"resume\",\"jdVersionId\":\"jd\",\"defaultMatchRunId\":\"run\",\"toOwnerUserId\":\"another-owner\"}");
    when(events.getOne(any(LambdaQueryWrapper.class))).thenReturn(event);
    assertThat(manager.claimedBindingId(candidate, List.of(binding))).isNull();
  }

  private static Candidate candidate(String ext) {
    Candidate candidate = new Candidate();
    candidate.setId("candidate");
    candidate.setOwnerUserId("owner");
    candidate.setExt(ext);
    return candidate;
  }

  private static ResumeJdScoreBinding binding(String id, String runId) {
    ResumeJdScoreBinding binding = new ResumeJdScoreBinding();
    binding.setId(id);
    binding.setCandidateId("candidate");
    binding.setResumeVersionId("resume");
    binding.setJdVersionId("jd");
    binding.setDefaultMatchRunId(runId);
    binding.setSelectedByUserId("owner");
    binding.setOperationId("score-operation");
    binding.setSelectedAt(Instant.parse("2026-09-22T02:59:00Z"));
    return binding;
  }
}
