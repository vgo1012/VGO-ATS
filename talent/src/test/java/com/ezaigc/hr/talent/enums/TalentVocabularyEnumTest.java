package com.ezaigc.hr.talent.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TalentVocabularyEnumTest {

  @Test
  void educationAndIntakeVocabulariesRejectUnknownCodes() {
    assertThat(EducationVerificationStatus.supports("VERIFIED")).isTrue();
    assertThat(EducationVerificationStatus.supports("DONE")).isFalse();
    assertThat(EducationAttendanceMode.supports("FULL_TIME")).isTrue();
    assertThat(EducationRecordType.supports("CORRECTION")).isTrue();
    assertThat(IntakeSourceType.supports("MAIL")).isTrue();
    assertThat(DuplicateResolution.supports("MERGE")).isTrue();
    assertThat(DuplicateResolution.supports("UNKNOWN")).isFalse();
  }

  @Test
  void terminalBatchAndFallbackErrorsAreTyped() {
    assertThat(IntakeBatchStatus.isTerminal("COMPLETED_WITH_ERRORS")).isTrue();
    assertThat(IntakeBatchStatus.isTerminal("PROCESSING")).isFalse();
    assertThat(
            ResumeExtractionErrorCode.allowsWholeDocumentFallback(
                "DOCUMENT_PAGE_EXTRACTION_UNAVAILABLE"))
        .isTrue();
    assertThat(ResumeExtractionErrorCode.allowsWholeDocumentFallback("MODEL_TIMEOUT"))
        .isFalse();
    assertThat(
            ResumeExtractionErrorCode.allowsWholeDocumentFallback(
                "DOCUMENT_PAGE_RENDER_LIMIT"))
        .isFalse();
    assertThat(ResumeExtractionErrorCode.allowsWholeDocumentFallback("DOCUMENT_TEXT_LIMIT"))
        .isFalse();
    assertThat(ResumeExtractionErrorCode.allowsWholeDocumentFallback("DOCUMENT_CONTAINER_LIMIT"))
        .isFalse();
    assertThat(ResumeExtractionErrorCode.allowsWholeDocumentFallback("DOCUMENT_CONTAINER_INVALID"))
        .isFalse();
  }

  @Test
  void persistedLifecycleStatesHaveDedicatedEnums() {
    assertThat(IntakeBatchStatus.values())
        .extracting(IntakeBatchStatus::getCode)
        .containsExactly("OPEN", "PROCESSING", "COMPLETED", "COMPLETED_WITH_ERRORS");
    assertThat(IdentityConflictStatus.values())
        .extracting(IdentityConflictStatus::getCode)
        .containsExactly("OPEN", "RESOLVED");
    assertThat(CompanyBlacklistStatus.values())
        .extracting(CompanyBlacklistStatus::getCode)
        .containsExactly("ACTIVE", "CORRECTED", "DISABLED");
    assertThat(ResumeFactType.values())
        .contains(ResumeFactType.INDUSTRY_TAG, ResumeFactType.RESPONSIBILITY_TAG, ResumeFactType.UNKNOWN);
  }
}
