package com.ezaigc.hr.talent.service.impl;

import static com.ezaigc.hr.talent.constant.TalentConstants.MAX_FACT_CORRECTION_CHANGES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ezaigc.hr.talent.domain.entity.FactCorrectionChange;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class FactCorrectionChangePersistenceServiceBatchValidationTest {

  @Test
  void acceptsEmptyBatchWithoutOpeningADataSession() {
    assertThat(
            new FactCorrectionChangePersistenceServiceImpl()
                .insertCorrectionChanges(List.of()))
        .isTrue();
  }

  @Test
  void rejectsNullBatchAndNullEntriesBeforeOpeningADataSession() {
    FactCorrectionChangePersistenceServiceImpl service =
        new FactCorrectionChangePersistenceServiceImpl();

    assertThatThrownBy(() -> service.insertCorrectionChanges(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("fact correction changes batch is invalid");
    assertThatThrownBy(
            () -> service.insertCorrectionChanges(Collections.singletonList(null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("fact correction changes batch is invalid");
  }

  @Test
  void rejectsOversizedBatchBeforeOpeningADataSession() {
    List<FactCorrectionChange> changes =
        Collections.nCopies(MAX_FACT_CORRECTION_CHANGES + 1, new FactCorrectionChange());

    assertThatThrownBy(
            () ->
                new FactCorrectionChangePersistenceServiceImpl()
                    .insertCorrectionChanges(changes))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("fact correction changes batch is invalid");
  }
}
