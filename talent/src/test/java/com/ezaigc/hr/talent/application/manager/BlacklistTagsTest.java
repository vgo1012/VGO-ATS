package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import com.ezaigc.hr.talent.domain.response.CandidateResponse;
import com.ezaigc.hr.talent.mapper.RiskMapper;
import com.ezaigc.hr.talent.service.RiskPersistenceService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class BlacklistTagsTest {
  @Test
  void readsTagsOnlyForVisibleCandidatesAndChecksPermissionFirst() {
    var manager = new RiskApplicationManager();
    var auth = mock(AuthorizationService.class);
    var batch = mock(CandidateBatchApplicationManager.class);
    var persistence = mock(RiskPersistenceService.class);
    var visible = mock(CandidateResponse.class);
    when(visible.getId()).thenReturn("visible");
    when(batch.read(List.of("visible", "private"))).thenReturn(List.of(visible));
    when(persistence.blacklistTags(List.of("visible"), Instant.EPOCH)).thenReturn(List.of("visible"));
    ReflectionTestUtils.setField(manager, "authorization", auth);
    ReflectionTestUtils.setField(manager, "library", mock(CandidateLibraryQueryService.class));
    ReflectionTestUtils.setField(manager, "candidateBatch", batch);
    ReflectionTestUtils.setField(manager, "persistence", persistence);
    ReflectionTestUtils.setField(manager, "clock", Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    assertThat(manager.blacklistTags(List.of("visible", "private"))).containsExactly("visible");
    verify(persistence).blacklistTags(List.of("visible"), Instant.EPOCH);
    when(batch.read(List.of("private"))).thenReturn(List.of());
    assertThat(manager.blacklistTags(List.of("private"))).isEmpty();
    clearInvocations(batch, persistence);
    doThrow(PlatformExceptions.forbidden("DENIED", "无风险读取权限")).when(auth).requirePermission("risk.read");
    assertThatThrownBy(() -> manager.blacklistTags(List.of("visible"))).hasMessageContaining("权限");
    verifyNoInteractions(batch, persistence);
  }

  @Test
  void batchSqlBindsBothScopesAndCompanySummaryRetainsRecruitmentBlocking() {
    var config = new Configuration();
    config.addMapper(RiskMapper.class);
    var sql = config.getMappedStatement(RiskMapper.class.getName() + ".blacklistTags")
        .getBoundSql(Map.of("candidateIds", List.of("a", "b"), "now", Instant.EPOCH));
    assertThat(sql.getSql()).contains("risk_company_blacklist_current", "submission_client_restriction_current", "UNION", "expires_at > ?");
    assertThat(sql.getParameterMappings()).hasSize(5);
    var summary = config.getMappedStatement(RiskMapper.class.getName() + ".restrictions")
        .getBoundSql(Map.of("candidateId", "a", "now", Instant.EPOCH)).getSql();
    assertThat(summary).contains("TRUE blocking");
    assertThat(summary.substring(summary.indexOf("SELECT 'CLIENT_RESTRICTION'"))).contains("FALSE");
  }
}
