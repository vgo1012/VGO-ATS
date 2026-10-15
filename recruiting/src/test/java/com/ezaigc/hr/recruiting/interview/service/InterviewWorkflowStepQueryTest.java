package com.ezaigc.hr.recruiting.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.recruiting.interview.mapper.InterviewMapper;
import com.ezaigc.hr.recruiting.interview.service.impl.InterviewPersistenceServiceImpl;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class InterviewWorkflowStepQueryTest {
  @Test
  void exactWorkflowStepAndApplicationAreBoundBeforeLimit() {
    Configuration configuration = new Configuration();
    configuration.addMapper(InterviewMapper.class);
    var bound = configuration.getMappedStatement(InterviewMapper.class.getName() + ".findInterviews")
        .getBoundSql(Map.of("applicationId", "A".repeat(26), "workflowStepId", "S".repeat(26), "limit", 100));
    assertThat(bound.getSql()).contains("i.application_id=?", "i.workflow_step_id=?");
    assertThat(bound.getSql().indexOf("i.workflow_step_id=?")).isLessThan(bound.getSql().indexOf("LIMIT"));
    assertThat(bound.getParameterMappings()).extracting(ParameterMapping::getProperty)
        .contains("applicationId", "workflowStepId", "limit");
    assertThat(bound.getSql()).doesNotContain("A".repeat(26), "S".repeat(26));
    assertThat(bound.getSql()).contains("i.interview_type=?",
        "?='EXTERNAL' AND i.interview_type IN ('EXTERNAL_CLIENT','EXTERNAL_CLIENT_AI')",
        "EXISTS(SELECT 1 FROM demand_jd_version", "WHEN v.recruitment_type IS NOT NULL THEN v.client_id ELSE j.client_id END");
    assertThat(bound.getParameterMappings()).extracting(ParameterMapping::getProperty)
        .contains("interviewType", "clientId");
    assertThat(bound.getSql().indexOf("i.interview_type=?")).isLessThan(bound.getSql().indexOf("LIMIT"));
    assertThat(bound.getSql().indexOf("END=?")).isLessThan(bound.getSql().indexOf("LIMIT"));
  }

  @Test
  void persistencePassesTheExactStepToMapper() {
    InterviewMapper mapper = mock(InterviewMapper.class);
    InterviewPersistenceServiceImpl service = new InterviewPersistenceServiceImpl();
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
    when(mapper.findInterviews(null, null, null, null, null, "SCHEDULED", null, "app",
        null, null, null, null, "step", "EXTERNAL", "client", 100)).thenReturn(List.of());
    assertThat(service.findInterviews(null, null, null, null, null, "SCHEDULED", null, "app",
        null, null, null, null, "step", "EXTERNAL", "client", 100)).isEmpty();
    verify(mapper).findInterviews(null, null, null, null, null, "SCHEDULED", null, "app",
        null, null, null, null, "step", "EXTERNAL", "client", 100);
  }
}
