package com.ezaigc.hr.intelligence.matching.service.impl;

import static com.ezaigc.hr.intelligence.matching.constant.MatchingConstants.MAX_MATCH_HIGHLIGHTS;
import static com.ezaigc.hr.intelligence.matching.constant.MatchingConstants.MAX_SELECTED_CRITERIA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ezaigc.hr.intelligence.matching.domain.entity.CriterionEvaluation;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchingHighlight;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchDetailPersistenceServiceBatchValidationTest {

  @Test
  void acceptsEmptyBatchesWithoutOpeningADataSession() {
    assertThat(new MatchingHighlightPersistenceServiceImpl().insertHighlightBatch(List.of()))
        .isTrue();
    assertThat(
            new CriterionEvaluationPersistenceServiceImpl()
                .insertCriterionEvaluationBatch(List.of()))
        .isTrue();
  }

  @Test
  void rejectsNullBatchesAndNullEntriesBeforeOpeningADataSession() {
    assertThatThrownBy(
            () ->
                new MatchingHighlightPersistenceServiceImpl()
                    .insertHighlightBatch(Collections.singletonList(null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("matching highlights batch is invalid");
    assertThatThrownBy(
            () ->
                new CriterionEvaluationPersistenceServiceImpl()
                    .insertCriterionEvaluationBatch(Collections.singletonList(null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("criterion evaluations batch is invalid");
  }

  @Test
  void rejectsEveryOversizedDetailBatchBeforeOpeningADataSession() {
    List<MatchingHighlight> highlights =
        Collections.nCopies(MAX_MATCH_HIGHLIGHTS + 1, new MatchingHighlight());
    List<CriterionEvaluation> criteria =
        Collections.nCopies(MAX_SELECTED_CRITERIA + 1, new CriterionEvaluation());

    assertThatThrownBy(
            () -> new MatchingHighlightPersistenceServiceImpl().insertHighlightBatch(highlights))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("matching highlights batch is invalid");
    assertThatThrownBy(
            () ->
                new CriterionEvaluationPersistenceServiceImpl()
                    .insertCriterionEvaluationBatch(criteria))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("criterion evaluations batch is invalid");
  }
}
