package com.ezaigc.hr.talent.controller;

import static com.ezaigc.hr.talent.api.ResumeUploadPolicy.MAX_FILE_SIZE_BYTES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.talent.domain.request.TalentIntakeImportRequest;
import com.ezaigc.hr.talent.domain.request.TalentIntakeBatchJdRequest;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.lang.reflect.Method;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

class TalentIntakeControllerValidationTest {

  private static final String VALID_TOKEN =
      "123e4567-e89b-12d3-a456-426614174000123e4567-e89b-12d3-a456-426614174001";
  private static final String VALID_IDENTIFIER = "01JBATCH000000000000000000";
  private static final String VALID_IDEMPOTENCY_KEY = "upload-key-1";

  @Test
  void downloadTokenHasExecutableBeanValidationAtHttpBoundary() throws NoSuchMethodException {
    Method downloadMethod = TalentIntakeController.class.getDeclaredMethod("download", String.class);
    try (ValidatorFactory factory =
        Validation.byDefaultProvider()
            .configure()
            .messageInterpolator(new ParameterMessageInterpolator())
            .buildValidatorFactory()) {
      var executableValidator = factory.getValidator().forExecutables();

      assertThat(
              executableValidator.validateParameters(
                  new TalentIntakeController(), downloadMethod, new Object[] {VALID_TOKEN}))
          .isEmpty();
      assertThat(
              executableValidator.validateParameters(
                  new TalentIntakeController(), downloadMethod, new Object[] {"short"}))
          .isNotEmpty()
          .allSatisfy(violation -> assertThat(violation.getMessage()).contains("下载令牌"));
    }
  }

  @Test
  void uploadFileHasExecutableBeanValidationAtHttpBoundary() throws NoSuchMethodException {
    Method uploadMethod =
        TalentIntakeController.class.getDeclaredMethod(
            "upload", String.class, MultipartFile.class, String.class);
    try (ValidatorFactory factory =
        Validation.byDefaultProvider()
            .configure()
            .messageInterpolator(new ParameterMessageInterpolator())
            .buildValidatorFactory()) {
      var executableValidator = factory.getValidator().forExecutables();

      assertThat(
              executableValidator.validateParameters(
                  new TalentIntakeController(),
                  uploadMethod,
                  parameters(file("candidate.pdf", 1))))
          .isEmpty();
      assertThat(
              executableValidator.validateParameters(
                  new TalentIntakeController(), uploadMethod, parameters(null)))
          .isNotEmpty();
      assertThat(
              executableValidator.validateParameters(
                  new TalentIntakeController(), uploadMethod, parameters(file("candidate.pdf", 0))))
          .isNotEmpty();
      assertThat(
              executableValidator.validateParameters(
                  new TalentIntakeController(),
                  uploadMethod,
                  parameters(file("candidate.pdf", MAX_FILE_SIZE_BYTES + 1))))
          .isNotEmpty();
      assertThat(
              executableValidator.validateParameters(
                  new TalentIntakeController(), uploadMethod, parameters(file("bad\0.pdf", 1))))
          .isNotEmpty();
      assertThat(
              executableValidator.validateParameters(
                  new TalentIntakeController(),
                  uploadMethod,
                  parameters(file("candidate.exe", 1))))
          .isNotEmpty();
    }
  }

  @Test
  void importRequiresAValidResourceVersionAndIdentifier() throws NoSuchMethodException {
    Method method = TalentIntakeController.class.getDeclaredMethod(
        "importIntake", String.class, TalentIntakeImportRequest.class);
    try (ValidatorFactory factory = Validation.byDefaultProvider().configure()
        .messageInterpolator(new ParameterMessageInterpolator()).buildValidatorFactory()) {
      var validator = factory.getValidator().forExecutables();
      var request = new TalentIntakeImportRequest();
      assertThat(validator.validateParameters(new TalentIntakeController(), method,
          new Object[] {VALID_IDENTIFIER, request})).isNotEmpty();
      request.setVersion("0");
      assertThat(validator.validateParameters(new TalentIntakeController(), method,
          new Object[] {VALID_IDENTIFIER, request})).isEmpty();
      request.setVersion("-1");
      assertThat(validator.validateParameters(new TalentIntakeController(), method,
          new Object[] {VALID_IDENTIFIER, request})).isNotEmpty();
      request.setVersion("1");
      assertThat(validator.validateParameters(new TalentIntakeController(), method,
          new Object[] {"bad-id", request})).isNotEmpty();
    }
  }

  @Test
  void historicalBatchJdLinkRequiresExactIdentifiersAndAuditReason()
      throws NoSuchMethodException {
    Method method = TalentIntakeController.class.getDeclaredMethod(
        "linkBatchJdVersion", String.class, TalentIntakeBatchJdRequest.class);
    try (ValidatorFactory factory = Validation.byDefaultProvider().configure()
        .messageInterpolator(new ParameterMessageInterpolator()).buildValidatorFactory()) {
      var validator = factory.getValidator().forExecutables();
      var request = new TalentIntakeBatchJdRequest();
      request.setJdVersionId("01JJDVERSION00000000000001");
      request.setReason("核对历史收件");
      assertThat(validator.validateParameters(new TalentIntakeController(), method,
          new Object[] {VALID_IDENTIFIER, request})).isEmpty();
      request.setReason(" ");
      assertThat(validator.validateParameters(new TalentIntakeController(), method,
          new Object[] {VALID_IDENTIFIER, request})).isNotEmpty();
      request.setReason("核对历史收件");
      request.setJdVersionId("bad-id");
      assertThat(validator.validateParameters(new TalentIntakeController(), method,
          new Object[] {VALID_IDENTIFIER, request})).isNotEmpty();
      request.setJdVersionId("01JJDVERSION00000000000001");
      assertThat(validator.validateParameters(new TalentIntakeController(), method,
          new Object[] {"bad-id", request})).isNotEmpty();
    }
  }

  private static Object[] parameters(MultipartFile file) {
    return new Object[] {VALID_IDENTIFIER, file, VALID_IDEMPOTENCY_KEY};
  }

  private static MultipartFile file(String name, long size) {
    MultipartFile file = mock(MultipartFile.class);
    when(file.getOriginalFilename()).thenReturn(name);
    when(file.getSize()).thenReturn(size);
    return file;
  }
}
