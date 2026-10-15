package com.ezaigc.hr.talent.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.ezaigc.hr.talent.domain.entity.CompanyBlacklistCurrent;
import com.ezaigc.hr.talent.mapper.CompanyBlacklistCurrentMapper;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

class CompanyBlacklistCurrentPersistenceServiceTest {
  private static final Instant NOW = Instant.parse("2026-10-02T00:00:00Z");
  private final CompanyBlacklistCurrentMapper mapper = mock(CompanyBlacklistCurrentMapper.class);
  private final CompanyBlacklistCurrentPersistenceServiceImpl service = new CompanyBlacklistCurrentPersistenceServiceImpl();

  CompanyBlacklistCurrentPersistenceServiceTest() {
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
  }

  @Test
  void commandReadUsesCandidateBoundCurrentLockInsteadOfSnapshotCount() {
    CompanyBlacklistCurrent value = new CompanyBlacklistCurrent();
    value.setCandidateId("candidate"); value.setRecordId("record"); value.setActive(true);
    when(mapper.lockCurrent("candidate")).thenReturn(value);
    assertThat(service.lockCurrent("candidate")).isSameAs(value);
    var configuration = configuration();
    Map<String, Object> parameters = Map.of("candidateId", "candidate");
    var bound = configuration.getMappedStatement(CompanyBlacklistCurrentMapper.class.getName() + ".lockCurrent")
        .getBoundSql(parameters);
    assertThat(bound.getSql()).contains("WHERE candidate_id=? FOR UPDATE");
    assertThat(bound.getParameterMappings()).hasSize(1);
    assertThat(configuration.newMetaObject(parameters).getValue(
        bound.getParameterMappings().getFirst().getProperty())).isEqualTo("candidate");
    verify(mapper).lockCurrent("candidate");
  }

  @Test
  void firstProjectionUsesInsertWithBothAuditFieldsAndNeverUpserts() {
    when(mapper.insert(any(CompanyBlacklistCurrent.class))).thenReturn(1);
    assertThat(service.projectBlacklist("candidate", null, "record", true, "actor", NOW)).isOne();
    var entity = ArgumentCaptor.forClass(CompanyBlacklistCurrent.class);
    verify(mapper).insert(entity.capture());
    assertThat(entity.getValue().getCandidateId()).isEqualTo("candidate");
    assertThat(entity.getValue().getRecordId()).isEqualTo("record");
    assertThat(entity.getValue().isActive()).isTrue();
    assertThat(entity.getValue().getCreateId()).isEqualTo("actor");
    assertThat(entity.getValue().getCreateTime()).isEqualTo(NOW);
    assertThat(entity.getValue().getUpdateId()).isEqualTo("actor");
    assertThat(entity.getValue().getUpdateTime()).isEqualTo(NOW);
    var configuration = configuration();
    String sql = configuration.getMappedStatement(CompanyBlacklistCurrentMapper.class.getName() + ".insert")
        .getBoundSql(Map.of("et", entity.getValue())).getSql();
    assertThat(sql).contains("INSERT INTO risk_company_blacklist_current").doesNotContain("DUPLICATE");
  }

  @Test
  void concurrentFirstInsertReturnsNoAffectedRowsInsteadOfReplacingTheWinner() {
    when(mapper.insert(any(CompanyBlacklistCurrent.class))).thenThrow(new DuplicateKeyException("synthetic primary key race"));
    assertThat(service.projectBlacklist("candidate", null, "record", true, "actor", NOW)).isZero();
  }

  @Test
  void otherDatabaseFailuresAreNotReportedAsProjectionConflicts() {
    var failure = new DataIntegrityViolationException("synthetic foreign key failure");
    when(mapper.insert(any(CompanyBlacklistCurrent.class))).thenThrow(failure);
    assertThatThrownBy(() -> service.projectBlacklist("candidate", null, "record", true, "actor", NOW))
        .isSameAs(failure);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  @SuppressWarnings({"rawtypes", "unchecked"})
  void updateBindsCandidateAndExpectedRecordAndPreservesCreateAudit(boolean active) {
    var configuration = configuration();
    when(mapper.update(any(Wrapper.class))).thenReturn(1);
    assertThat(service.projectBlacklist("candidate", "prior", "new", active, "actor", NOW)).isOne();
    var capture = ArgumentCaptor.forClass(Wrapper.class);
    verify(mapper).update(capture.capture());
    Map<String, Object> parameters = new HashMap<>();
    parameters.put("ew", capture.getValue());
    parameters.put("et", null);
    var bound = configuration.getMappedStatement(CompanyBlacklistCurrentMapper.class.getName() + ".update")
        .getBoundSql(parameters);
    String sql = bound.getSql().replaceAll("\\s+", " ");
    assertThat(sql).contains("UPDATE risk_company_blacklist_current", "record_id=?", "active=?",
        "update_id=?", "update_time=?", "WHERE (candidate_id = ? AND record_id = ?)")
        .doesNotContain("create_id=", "create_time=");
    List<Object> values = bound.getParameterMappings().stream()
        .map(mapping -> configuration.newMetaObject(parameters).getValue(mapping.getProperty())).toList();
    assertThat(values).containsExactly("new", active, "actor", NOW, "candidate", "prior");
  }

  private static MybatisConfiguration configuration() {
    var configuration = new MybatisConfiguration();
    configuration.addMapper(CompanyBlacklistCurrentMapper.class);
    return configuration;
  }
}
