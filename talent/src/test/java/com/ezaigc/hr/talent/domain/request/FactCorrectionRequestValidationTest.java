package com.ezaigc.hr.talent.domain.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.CandidateIdentity;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.ResumeFacts;
import com.ezaigc.hr.talent.controller.FactCorrectionController;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.stream.Collectors;

class FactCorrectionRequestValidationTest {

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
  void rejectsMissingRequiredCorrectionFieldsAtHttpBoundary() {
    FactCorrectionRequest request = new FactCorrectionRequest();

    assertThat(paths(validator.validate(request)))
        .contains("resumeVersionId", "baseFactSnapshotId", "reason", "facts");
  }

  @Test
  void rejectsOversizedCorrectionReason() {
    FactCorrectionRequest request = validRequest();
    request.setReason("R".repeat(501));

    assertThat(paths(validator.validate(request))).contains("reason");
  }

  @Test
  void rejectsIdentifiersThatAreNotTwentySixCharacters() {
    FactCorrectionRequest request = validRequest();
    request.setResumeVersionId("short");
    request.setBaseFactSnapshotId("short");

    assertThat(paths(validator.validate(request)))
        .contains("resumeVersionId", "baseFactSnapshotId");
  }

  @Test
  void acceptsCorrectionRequestAtConfiguredBoundaries() {
    FactCorrectionRequest request = validRequest();
    request.setReason("R".repeat(500));
    assertThat(validator.validate(request)).isEmpty();
  }

  @Test
  void rejectsMalformedAndOversizedNestedResumeFactsAtHttpBoundary() {
    FactCorrectionRequest request = validRequest();
    request.setFacts(
        facts(
            new CandidateIdentity(
                "N".repeat(161),
                "invalid-email",
                "123456",
                null,
                null,
                null,
                null,
                null),
            List.of("I".repeat(121))));

    assertThat(paths(validator.validate(request)))
        .contains(
            "facts.candidate.fullName",
            "facts.candidate.email",
            "facts.industryTags[0].<list element>");
  }

  @Test
  void controllerActivatesBeanValidationForCorrectionBody() throws NoSuchMethodException {
    Method method =
        FactCorrectionController.class.getDeclaredMethod("correct", FactCorrectionRequest.class);

    assertThat(method.getParameters()[0].isAnnotationPresent(Valid.class)).isTrue();
  }

  private static FactCorrectionRequest validRequest() {
    FactCorrectionRequest request = new FactCorrectionRequest();
    request.setResumeVersionId("R".repeat(26));
    request.setBaseFactSnapshotId("S".repeat(26));
    request.setReason("人工核实后纠正");
    request.setFacts(facts(emptyCandidate(), List.of()));
    return request;
  }

  private static ResumeFacts facts(
      CandidateIdentity candidate, List<String> industryTags) {
    return new ResumeFacts(
        candidate,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        industryTags,
        List.of(),
        List.of());
  }

  private static CandidateIdentity emptyCandidate() {
    return new CandidateIdentity(null, null, null, null, null, null, null, null);
  }

  private static Set<String> paths(Set<? extends ConstraintViolation<?>> violations) {
    return violations.stream()
        .map(violation -> violation.getPropertyPath().toString())
        .collect(Collectors.toSet());
  }
}
