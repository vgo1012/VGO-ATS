package com.ezaigc.hr.recruiting.demand.service.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.recruiting.demand.domain.dto.WorkflowStepDraft;
import com.ezaigc.hr.recruiting.demand.enums.WorkflowStepCode;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WorkflowTemplateValidatorTest {

  private final WorkflowTemplateValidator validator = new WorkflowTemplateValidator();

  @ParameterizedTest
  @ValueSource(strings = {"en-US", "tr-TR", "az-AZ"})
  @ResourceLock(Resources.LOCALE)
  void normalizesCommunicationChannelsIndependentlyOfDefaultLocale(String languageTag) {
    Locale previous = Locale.getDefault();
    Locale previousDisplay = Locale.getDefault(Locale.Category.DISPLAY);
    Locale previousFormat = Locale.getDefault(Locale.Category.FORMAT);
    try {
      Locale.setDefault(Locale.forLanguageTag(languageTag));
      for (String[] channel : new String[][] {
          {"phone", "PHONE"}, {"wechat", "WECHAT"}, {"email", "EMAIL"},
          {"Email", "EMAIL"}, {"EMAIL", "EMAIL"}, {" \temail\t ", "EMAIL"}}) {
        var result = validator.validate("INTERNAL", List.of(
            new WorkflowStepDraft(WorkflowStepCode.CANDIDATE_COMMUNICATION, channel[0]),
            step(WorkflowStepCode.DECISION)));
        assertThat(result.getFirst().getDefaultFollowUpChannel())
            .isEqualTo(channel[1]);
        assertThat(result.getFirst().isCandidateOptional()).isTrue();
        assertThat(result.getFirst().getSequenceNo()).isEqualTo(1);
      }
    } finally {
      Locale.setDefault(previous);
      Locale.setDefault(Locale.Category.DISPLAY, previousDisplay);
      Locale.setDefault(Locale.Category.FORMAT, previousFormat);
    }
  }

  @Test
  void preservesBlankDefaultsAndChannelValidationBoundaries() {
    assertThat(validator.validate("INTERNAL", List.of(
        new WorkflowStepDraft(WorkflowStepCode.CANDIDATE_COMMUNICATION, " \t "),
        new WorkflowStepDraft(WorkflowStepCode.DECISION, " \t "))))
        .extracting(value -> value.getDefaultFollowUpChannel())
        .containsExactly("PHONE", null);
    for (WorkflowStepDraft draft : List.of(
        new WorkflowStepDraft(WorkflowStepCode.CANDIDATE_COMMUNICATION, "sms"),
        new WorkflowStepDraft(WorkflowStepCode.CANDIDATE_COMMUNICATION, "\u00a0email\u00a0"),
        new WorkflowStepDraft(WorkflowStepCode.CANDIDATE_COMMUNICATION, "\u2003email\u2003"),
        new WorkflowStepDraft(WorkflowStepCode.DECISION, "email"))) {
      var steps = draft.getCode() == WorkflowStepCode.DECISION
          ? List.of(draft) : List.of(draft, step(WorkflowStepCode.DECISION));
      assertThatThrownBy(() -> validator.validate("INTERNAL", steps))
          .isInstanceOfSatisfying(PlatformException.class, error -> {
            assertThat(error.status().value()).isEqualTo(422);
            assertThat(error.code()).isEqualTo("WORKFLOW_TEMPLATE_INVALID");
            assertThat(error.recoverable()).isFalse();
            assertThat(error.fieldErrors()).singleElement()
                .satisfies(field -> assertThat(field.getField()).isEqualTo("steps.defaultFollowUpChannel"));
          });
    }
  }

  @Test
  void acceptsZeroInterviewInternalPathAndDefaultsCommunicationToPhone() {
    var result =
        validator.validate(
            "INTERNAL",
            List.of(
                new WorkflowStepDraft(WorkflowStepCode.CANDIDATE_COMMUNICATION, null),
                new WorkflowStepDraft(WorkflowStepCode.DECISION, null),
                new WorkflowStepDraft(WorkflowStepCode.OFFER, null)));

    assertThat(result).hasSize(3);
    assertThat(result.getFirst().getDefaultFollowUpChannel()).isEqualTo("PHONE");
    assertThat(result.get(1).isCandidateOptional()).isFalse();
  }

  @Test
  void acceptsOptionalInternalInterviewRoundsWithoutClientSubmission() {
    var result = validator.validate("INTERNAL", List.of(
        step(WorkflowStepCode.CANDIDATE_COMMUNICATION),
        step(WorkflowStepCode.INTERVIEW_ROUND_1),
        step(WorkflowStepCode.INTERVIEW_ROUND_2),
        step(WorkflowStepCode.INTERVIEW_ROUND_3),
        step(WorkflowStepCode.DECISION), step(WorkflowStepCode.OFFER)));
    assertThat(result).hasSize(6);
    assertThat(result.subList(1, 4)).allMatch(value -> value.isCandidateOptional());
    assertThat(result.get(4).isCandidateOptional()).isFalse();
  }

  @Test
  void acceptsDirectInternalInterviewWithoutCommunicationOrClientAi() {
    assertThat(validator.validate("INTERNAL", List.of(
        step(WorkflowStepCode.INTERVIEW_ROUND_1), step(WorkflowStepCode.DECISION))))
        .hasSize(2);
  }

  @Test
  void acceptsExternalThreeRoundPathAfterSubmission() {
    assertThat(
            validator.validate(
                "EXTERNAL",
                List.of(
                    step(WorkflowStepCode.CLIENT_SUBMISSION),
                    step(WorkflowStepCode.CLIENT_AI_INTERVIEW),
                    step(WorkflowStepCode.INTERVIEW_ROUND_1),
                    step(WorkflowStepCode.INTERVIEW_ROUND_2),
                    step(WorkflowStepCode.INTERVIEW_ROUND_3),
                    step(WorkflowStepCode.DECISION))))
        .hasSize(6);
  }

  @Test
  void rejectsClientStepsForInternalRecruitment() {
    assertThatThrownBy(
            () ->
                validator.validate(
                    "INTERNAL",
                    List.of(
                        step(WorkflowStepCode.CLIENT_SUBMISSION), step(WorkflowStepCode.DECISION))))
        .isInstanceOfSatisfying(
            PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("CLIENT_STEP_NOT_ALLOWED"));
  }

  @Test
  void rejectsMissingDecisionDuplicateAndUnorderedSteps() {
    assertInvalid(List.of(step(WorkflowStepCode.INTERVIEW_ROUND_1)));
    assertInvalid(List.of(step(WorkflowStepCode.DECISION), step(WorkflowStepCode.DECISION)));
    assertInvalid(
        List.of(
            step(WorkflowStepCode.INTERVIEW_ROUND_1),
            step(WorkflowStepCode.CLIENT_SUBMISSION),
            step(WorkflowStepCode.DECISION)));
  }

  @Test
  void allowsClientInterviewWithoutSubmission() {
    assertThat(validator.validate("EXTERNAL",
        List.of(
            step(WorkflowStepCode.CLIENT_AI_INTERVIEW),
            step(WorkflowStepCode.INTERVIEW_ROUND_1),
            step(WorkflowStepCode.DECISION)))).hasSize(3);
    assertInvalid(
        List.of(
            step(WorkflowStepCode.CLIENT_SUBMISSION),
            step(WorkflowStepCode.INTERVIEW_ROUND_1),
            step(WorkflowStepCode.DECISION)));
  }

  private void assertInvalid(List<WorkflowStepDraft> steps) {
    assertThatThrownBy(() -> validator.validate("EXTERNAL", steps))
        .isInstanceOfSatisfying(
            PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("WORKFLOW_TEMPLATE_INVALID"));
  }

  private static WorkflowStepDraft step(WorkflowStepCode code) {
    return new WorkflowStepDraft(code, null);
  }
}
