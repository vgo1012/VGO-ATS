package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import com.ezaigc.hr.talent.api.CandidateMatchingStateQuery;
import com.ezaigc.hr.talent.mapper.TalentIntakeMapper;
import com.ezaigc.hr.talent.service.TalentIntakePersistenceService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SharedCandidateJdReadTest {
  @Test void frozenIntakeAndScoreLinksRequireCurrentSharedEligibility() {
    var manager = new SharedCandidateJdApplicationManager();
    var persistence = mock(TalentIntakePersistenceService.class);
    var library = mock(CandidateLibraryQueryService.class);
    var matching = mock(CandidateMatchingStateQuery.class);
    ReflectionTestUtils.setField(manager, "persistence", persistence);
    ReflectionTestUtils.setField(manager, "library", library);
    ReflectionTestUtils.setField(manager, "matching", matching);
    when(persistence.findCandidateIdsForJdVersion("jd", null, 200)).thenReturn(List.of("candidate"));
    when(library.eligibleCandidateIds(any())).thenReturn(Set.of("candidate"));
    assertThat(manager.hasSharedCandidate("jd")).isTrue();
    when(library.eligibleCandidateIds(any())).thenReturn(Set.of());
    assertThat(manager.hasSharedCandidate("jd")).isFalse();
    when(persistence.findCandidateIdsForJdVersion("jd", null, 200)).thenReturn(List.of());
    when(matching.candidateIdsForJdVersion("jd", null, 200)).thenReturn(List.of("candidate"));
    when(library.eligibleCandidateIds(any())).thenReturn(Set.of("candidate"));
    assertThat(manager.hasSharedCandidate("jd")).isTrue();
  }

  @Test void associationQueryBindsVersionAndCursorWithoutFilteringHistoricalResumes() {
    var config = new Configuration();
    config.addMapper(TalentIntakeMapper.class);
    var query = config.getMappedStatement(TalentIntakeMapper.class.getName() + ".findCandidateIdsForJdVersion")
        .getBoundSql(Map.of("jdVersionId", "jd", "afterId", "candidate", "limit", 200));
    assertThat(query.getSql()).contains("talent_intake_batch", "talent_resume_jd_score_binding", "candidate_id >", "ORDER BY candidate_id")
        .doesNotContain("current_version_no", "owner_user_id");
    assertThat(query.getParameterMappings()).hasSize(4);
  }
}
