package com.ezaigc.hr.recruiting.submission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.recruiting.submission.mapper.SubmissionMapper;
import com.ezaigc.hr.recruiting.submission.service.impl.SubmissionPersistenceServiceImpl;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ClientRestrictionProjectionQueryTest {
  @Test
  void mappedUpdateRequiresBothScopeKeysAndExpectedFactWithoutInterpolatingValues() {
    Configuration configuration = new Configuration();
    configuration.addMapper(SubmissionMapper.class);
    var statement = configuration.getMappedStatement(SubmissionMapper.class.getName() + ".replaceRestrictionProjection");
    Map<String, Object> parameters = new HashMap<>();
    parameters.put("clientId", "C".repeat(26));
    parameters.put("candidateId", "A".repeat(26));
    parameters.put("expectedRecordId", "old' OR '1'='1");
    parameters.put("recordId", "N".repeat(26));
    parameters.put("active", false);
    parameters.put("expiresAt", null);
    parameters.put("actorId", "U".repeat(26));
    parameters.put("now", Instant.parse("2026-09-07T00:00:00Z"));
    BoundSql bound = statement.getBoundSql(parameters);

    assertThat(statement.getSqlCommandType()).isEqualTo(SqlCommandType.UPDATE);
    assertThat(bound.getSql().replaceAll("\\s+", " ").strip())
        .startsWith("UPDATE submission_client_restriction_current SET ")
        .endsWith("WHERE client_id=? AND candidate_id=? AND record_id=?")
        .doesNotContain("old'", " OR ", "INSERT");
    assertThat(bound.getParameterMappings()).extracting(ParameterMapping::getProperty)
        .containsExactly("recordId", "active", "expiresAt", "actorId", "now", "clientId", "candidateId", "expectedRecordId");
  }

  @Test
  void persistencePreservesExpectedRecordAndReportsAConcurrentConflict() {
    SubmissionMapper mapper = mock(SubmissionMapper.class);
    SubmissionPersistenceServiceImpl service = new SubmissionPersistenceServiceImpl();
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
    Instant now = Instant.parse("2026-09-07T00:00:00Z");
    when(mapper.replaceRestrictionProjection("client", "candidate", "old", "new", false, null, "actor", now))
        .thenReturn(0);

    assertThat(service.replaceRestrictionProjection("client", "candidate", "old", "new", false, null, "actor", now))
        .isZero();
    verify(mapper).replaceRestrictionProjection("client", "candidate", "old", "new", false, null, "actor", now);
  }
}
