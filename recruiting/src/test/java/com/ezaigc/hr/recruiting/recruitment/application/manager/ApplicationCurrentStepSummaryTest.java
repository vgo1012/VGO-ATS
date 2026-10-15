package com.ezaigc.hr.recruiting.recruitment.application.manager;

import static org.assertj.core.api.Assertions.assertThat;

import com.ezaigc.hr.recruiting.recruitment.domain.entity.Application;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.WorkflowStep;
import com.ezaigc.hr.recruiting.recruitment.domain.response.ApplicationResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ApplicationCurrentStepSummaryTest {

  @Test
  void exposesTheActiveStepForAnApplicationInProcess() {
    Application application = new Application();
    application.setCurrentStage("IN_PROCESS");
    WorkflowStep step = new WorkflowStep();
    step.setStepCode("PRE_SUBMISSION_INTERNAL_INTERVIEW");
    step.setRoundNo(2);
    step.setStatus("IN_PROGRESS");
    ApplicationResponse response = response();

    RecruitmentApplicationManager.applyCurrentStep(response, application, step);

    assertThat(response.getCurrentStepCode()).isEqualTo("PRE_SUBMISSION_INTERNAL_INTERVIEW");
    assertThat(response.getCurrentStepRoundNo()).isEqualTo(2);
    assertThat(response.getCurrentStepStatus()).isEqualTo("IN_PROGRESS");
  }

  @Test
  void terminalApplicationDoesNotExposeAnOldActiveStep() {
    Application application = new Application();
    application.setCurrentStage("TERMINATED");
    WorkflowStep oldStep = new WorkflowStep();
    oldStep.setStepCode("CANDIDATE_COMMUNICATION");
    oldStep.setStatus("IN_PROGRESS");
    ApplicationResponse response = response();

    RecruitmentApplicationManager.applyCurrentStep(response, application, oldStep);

    assertThat(response.getCurrentStepCode()).isNull();
    assertThat(response.getCurrentStepRoundNo()).isNull();
    assertThat(response.getCurrentStepStatus()).isNull();
  }

  @Test
  void terminalTimestampAndFinishedStepBothSuppressTheSummary() {
    Application application = new Application();
    application.setCurrentStage("IN_PROCESS");
    application.setTerminalAt(Instant.parse("2026-09-09T00:00:00Z"));
    WorkflowStep step = new WorkflowStep();
    step.setStepCode("PRE_SUBMISSION_INTERNAL_INTERVIEW");
    step.setStatus("IN_PROGRESS");
    ApplicationResponse terminalResponse = response();

    RecruitmentApplicationManager.applyCurrentStep(terminalResponse, application, step);
    assertThat(terminalResponse.getCurrentStepCode()).isNull();

    application.setTerminalAt(null);
    step.setStatus("PASSED");
    ApplicationResponse finishedResponse = response();
    RecruitmentApplicationManager.applyCurrentStep(finishedResponse, application, step);
    assertThat(finishedResponse.getCurrentStepCode()).isNull();
  }

  @Test
  void exposesAllScheduledRoundsEvenWhilePathSelectionIsCurrent() {
    Application application = new Application();
    application.setId("application");
    application.setCurrentStage("PATH_SELECTION");
    ApplicationResponse response = response();

    RecruitmentApplicationManager.applyActiveInterviewRounds(
        response, application, Map.of("application", List.of(3, 5)));

    assertThat(response.getActiveInterviewRoundNos()).containsExactly(3, 5);
  }

  @Test
  void terminalAndOnboardedApplicationsNeverExposeScheduledRounds() {
    for (String stage : List.of("TERMINATED", "ONBOARDED")) {
      Application application = new Application();
      application.setId("application");
      application.setCurrentStage(stage);
      ApplicationResponse response = response();

      RecruitmentApplicationManager.applyActiveInterviewRounds(
          response, application, Map.of("application", List.of(3, 5)));

      assertThat(response.getActiveInterviewRoundNos()).isEmpty();
    }

    Application timestamped = new Application();
    timestamped.setId("application");
    timestamped.setCurrentStage("IN_PROCESS");
    timestamped.setTerminalAt(Instant.parse("2026-09-15T00:00:00Z"));
    ApplicationResponse response = response();
    RecruitmentApplicationManager.applyActiveInterviewRounds(
        response, timestamped, Map.of("application", List.of(3, 5)));
    assertThat(response.getActiveInterviewRoundNos()).isEmpty();
  }

  @Test
  void exposesLatestCompletedRoundUntilTheApplicationEnds() {
    Application application = new Application();
    application.setId("application");
    application.setCurrentStage("PATH_SELECTION");
    ApplicationResponse response = response();

    RecruitmentApplicationManager.applyLatestCompletedInterviewRound(
        response, application, Map.of("application", 1));

    assertThat(response.getLatestCompletedInterviewRoundNo()).isEqualTo(1);

    application.setCurrentStage("TERMINATED");
    RecruitmentApplicationManager.applyLatestCompletedInterviewRound(
        response, application, Map.of("application", 1));
    assertThat(response.getLatestCompletedInterviewRoundNo()).isNull();
  }

  private static ApplicationResponse response() {
    return new ApplicationResponse(null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null, null);
  }
}
