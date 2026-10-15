package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.assertThat;

import com.ezaigc.hr.talent.application.assembler.TalentIntakeAssembler;
import com.ezaigc.hr.talent.domain.entity.ResumeIntake;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ResumeIntakeImportStatusTest {
  @ParameterizedTest
  @CsvSource({
      "PENDING_IMPORT,,,PENDING",
      "READY_FOR_CONFIRMATION,,,PENDING",
      "IDENTITY_REVIEW_REQUIRED,processing,,PENDING",
      "FAILED_RETRYABLE,processing,,PENDING",
      "QUARANTINED,,,PENDING",
      "REJECTED,,,PENDING",
      "PARSING,processing,,PROCESSING",
      "CONFIRMED,processing,,PROCESSING",
      "CONFIRMED,,,PROCESSING",
      "COMPLETED,processing,QUEUED,PROCESSING",
      "COMPLETED,processing,FAILED_RETRYABLE,PROCESSING",
      "COMPLETED,processing,SUCCEEDED,IMPORTED"
  })
  void exposesThreeStagesWithoutLosingInternalRecoveryState(
      String status, String processingId, String scoreStatus, String importStatus) {
    var intake = new ResumeIntake();
    intake.setStatus(status);
    intake.setProcessingId(processingId);
    intake.setScoreStatus(scoreStatus);

    var response = new TalentIntakeAssembler().toResponse(intake);

    assertThat(response.getStatus()).isEqualTo(status);
    assertThat(response.getImportStatus().getCode()).isEqualTo(importStatus);
  }
}
