package com.ezaigc.hr.talent.domain.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.stream.Collectors;

class CandidateContactRequestValidationTest {

  private static ValidatorFactory validatorFactory;
  private static Validator validator;

  @BeforeAll
  static void createValidator() {
    validatorFactory =
        Validation.byDefaultProvider()
            .configure()
            .messageInterpolator(new ParameterMessageInterpolator())
            .buildValidatorFactory();
    validator = validatorFactory.getValidator();
  }

  @AfterAll
  static void closeValidator() {
    validatorFactory.close();
  }

  @Test
  void rejectsMalformedCandidateContactValues() {
    TalentIntakeCandidateRequest request = validCandidateRequest();
    request.setEmail("not-an-email");
    request.setPhone("13800000000<script>");

    assertThat(paths(validator.validate(request))).contains("email", "phone");
  }

  @Test
  void rejectsOversizedCandidateContactValues() {
    TalentIntakeConfirmRequest request = validConfirmRequest();
    request.setEmail("e".repeat(310) + "@example.com");
    request.setPhone("1".repeat(41));

    assertThat(paths(validator.validate(request))).contains("email", "phone");
  }

  @Test
  void rejectsMalformedIdentifiersAndVersions() {
    TalentIntakeConfirmRequest confirmRequest = validConfirmRequest();
    confirmRequest.setCandidateId("short");
    confirmRequest.setVersion("-1");
    TalentIntakeCandidateRequest candidateRequest = validCandidateRequest();
    candidateRequest.setOwnerUserId("short");
    candidateRequest.setOwnerDepartmentId("short");
    candidateRequest.setVersion("1.5");

    assertThat(paths(validator.validate(confirmRequest))).contains("candidateId", "version");
    assertThat(paths(validator.validate(candidateRequest)))
        .contains("ownerUserId", "ownerDepartmentId", "version");
  }

  @Test
  void acceptsNullOptionalContactsAndSupportedFormatting() {
    TalentIntakeConfirmRequest confirmRequest = validConfirmRequest();
    confirmRequest.setCandidateId(null);
    confirmRequest.setEmail(null);
    confirmRequest.setPhone("+86 (138) 0000-0000");
    TalentIntakeCandidateRequest candidateRequest = validCandidateRequest();
    candidateRequest.setEmail("candidate@example.com");
    candidateRequest.setPhone(null);
    candidateRequest.setOwnerUserId(null);
    candidateRequest.setOwnerDepartmentId(null);

    assertThat(validator.validate(confirmRequest)).isEmpty();
    assertThat(validator.validate(candidateRequest)).isEmpty();
  }

  private static TalentIntakeConfirmRequest validConfirmRequest() {
    return new TalentIntakeConfirmRequest(
        "C".repeat(26), "候选人", "candidate@example.com", "13800000000", "0");
  }

  private static TalentIntakeCandidateRequest validCandidateRequest() {
    return new TalentIntakeCandidateRequest(
        "候选人",
        "candidate@example.com",
        "13800000000",
        "U".repeat(26),
        "D".repeat(26),
        "0");
  }

  private static Set<String> paths(Set<? extends ConstraintViolation<?>> violations) {
    return violations.stream()
        .map(violation -> violation.getPropertyPath().toString())
        .collect(Collectors.toSet());
  }
}
