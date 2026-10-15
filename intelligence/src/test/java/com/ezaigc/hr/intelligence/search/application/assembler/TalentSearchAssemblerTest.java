package com.ezaigc.hr.intelligence.search.application.assembler;

import static org.assertj.core.api.Assertions.assertThat;

import com.ezaigc.hr.intelligence.search.domain.request.TalentSearchRequest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class TalentSearchAssemblerTest {

  @Test
  void copiesWorkYearsRangeAndScope() {
    var request = new TalentSearchRequest();
    request.setMinWorkYears(new BigDecimal("1"));
    request.setMaxWorkYears(new BigDecimal("3"));
    request.setScope("PERSONAL_POOL");
    request.setConditions(List.of(" 高压电工证 ", "高压电工证", "低压电工证"));
    request.setJdId("jd-selected");
    request.setCandidateIds(List.of(" candidate-a ", "candidate-a", "candidate-b"));

    var criteria = new TalentSearchAssembler().toDTO(request);

    assertThat(criteria.getMinWorkYears()).isEqualByComparingTo("1");
    assertThat(criteria.getMaxWorkYears()).isEqualByComparingTo("3");
    assertThat(criteria.getConditions()).containsExactly("高压电工证", "低压电工证");
    assertThat(criteria.getScope()).isEqualTo("PERSONAL_POOL");
    assertThat(criteria.getJdId()).isEqualTo("jd-selected");
    assertThat(criteria.getCandidateIds()).containsExactly("candidate-a", "candidate-b");
  }
}
