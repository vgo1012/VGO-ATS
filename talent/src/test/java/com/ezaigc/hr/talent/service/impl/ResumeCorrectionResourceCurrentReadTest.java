package com.ezaigc.hr.talent.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.talent.domain.entity.ResumeResourceEntity;
import com.ezaigc.hr.talent.mapper.ResumeParsingMapper;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ResumeCorrectionResourceCurrentReadTest {

  @Test
  void currentReadIsBoundToTheAlreadyLockedCandidateAndAvailableVersion() throws Exception {
    Select select = ResumeParsingMapper.class
        .getMethod("findResumeResourceForUpdate", String.class, String.class)
        .getAnnotation(Select.class);
    assertThat(select).isNotNull();
    String sql = String.join(" ", select.value()).replaceAll("\\s+", " ").trim();

    assertThat(sql).contains("v.id=#{versionId}", "c.id=#{candidateId}",
        "v.status='AVAILABLE'", "c.status='ACTIVE'", "c.owner_user_id", "c.owner_department_id",
        "JOIN talent_resume r ON r.id=v.resume_id", "JOIN talent_candidate c ON c.id=r.candidate_id")
        .endsWith("FOR UPDATE");
  }

  @Test
  void persistenceDelegatesTheVersionAndExpectedCandidateTogether() {
    ResumeParsingMapper mapper = mock(ResumeParsingMapper.class);
    ResumeParsingPersistenceServiceImpl service = new ResumeParsingPersistenceServiceImpl();
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
    ResumeResourceEntity current = new ResumeResourceEntity("version", "resume.pdf", "application/pdf",
        "hash", "object", "candidate", "current-owner", "department");
    when(mapper.findResumeResourceForUpdate("version", "candidate")).thenReturn(current);

    assertThat(service.findResumeResourceForUpdate("version", "candidate")).isSameAs(current);

    verify(mapper).findResumeResourceForUpdate("version", "candidate");
  }
}
