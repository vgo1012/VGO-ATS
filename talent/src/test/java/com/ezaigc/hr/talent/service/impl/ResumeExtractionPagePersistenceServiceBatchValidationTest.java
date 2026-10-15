package com.ezaigc.hr.talent.service.impl;

import static com.ezaigc.hr.talent.constant.TalentConstants.MAX_RESUME_EXTRACTION_PAGES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ezaigc.hr.talent.domain.entity.ResumeExtractionPage;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResumeExtractionPagePersistenceServiceBatchValidationTest {

  @Test
  void acceptsEmptyBatchWithoutOpeningADataSession() {
    assertThat(new ResumeExtractionPagePersistenceServiceImpl().insertExtractionPages(List.of()))
        .isTrue();
  }

  @Test
  void rejectsNullBatchAndNullEntriesBeforeOpeningADataSession() {
    ResumeExtractionPagePersistenceServiceImpl service =
        new ResumeExtractionPagePersistenceServiceImpl();

    assertThatThrownBy(() -> service.insertExtractionPages(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("resume extraction pages batch is invalid");
    assertThatThrownBy(
            () -> service.insertExtractionPages(Collections.singletonList(null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("resume extraction pages batch is invalid");
  }

  @Test
  void rejectsOversizedBatchBeforeOpeningADataSession() {
    List<ResumeExtractionPage> pages =
        Collections.nCopies(MAX_RESUME_EXTRACTION_PAGES + 1, new ResumeExtractionPage());

    assertThatThrownBy(
            () -> new ResumeExtractionPagePersistenceServiceImpl().insertExtractionPages(pages))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("resume extraction pages batch is invalid");
  }
}
