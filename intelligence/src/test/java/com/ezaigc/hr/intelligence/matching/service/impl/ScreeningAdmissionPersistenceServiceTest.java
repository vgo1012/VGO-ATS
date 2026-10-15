package com.ezaigc.hr.intelligence.matching.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.intelligence.matching.domain.entity.ScreeningCandidateRow;
import com.ezaigc.hr.intelligence.matching.mapper.ScreeningAdmissionMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ScreeningAdmissionPersistenceServiceTest {
  private final ScreeningAdmissionMapper mapper = mock(ScreeningAdmissionMapper.class);
  private final ScreeningAdmissionPersistenceServiceImpl service = new ScreeningAdmissionPersistenceServiceImpl();

  @Test
  void emptyCandidateSelectionDoesNotProduceAnEmptySqlInClause() {
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
    assertThat(service.findScreeningCandidates(List.of(), "actor", true, Set.of(), Set.of(), false)).isEmpty();
    verifyNoInteractions(mapper);
  }

  @Test
  void admittedOnlyKeepsItsAuthorizedFullListQueryForEmptyCandidateSelection() {
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
    var row = new ScreeningCandidateRow();
    when(mapper.findScreeningCandidates(List.of(), "actor", false, Set.of("managed"), Set.of("department"), true))
        .thenReturn(List.of(row));
    assertThat(service.findScreeningCandidates(List.of(), "actor", false, Set.of("managed"), Set.of("department"), true))
        .containsExactly(row);
    verify(mapper).findScreeningCandidates(List.of(), "actor", false, Set.of("managed"), Set.of("department"), true);
  }
}
