package com.ezaigc.hr.recruiting.enums;

import static org.assertj.core.api.Assertions.assertThat;

import com.ezaigc.hr.recruiting.demand.enums.DemandStatus;
import com.ezaigc.hr.recruiting.demand.enums.JdStatus;
import com.ezaigc.hr.recruiting.demand.enums.JdVersionStatus;
import com.ezaigc.hr.recruiting.demand.enums.WorkflowStepCode;
import com.ezaigc.hr.recruiting.interview.enums.InterviewCandidateConfirmationStatus;
import com.ezaigc.hr.recruiting.interview.enums.InterviewMode;
import com.ezaigc.hr.recruiting.interview.enums.InterviewNotificationAttemptStatus;
import com.ezaigc.hr.recruiting.interview.enums.InterviewNotificationStatus;
import com.ezaigc.hr.recruiting.interview.enums.InterviewParticipantResponseStatus;
import com.ezaigc.hr.recruiting.interview.enums.InterviewTemplateStatus;
import com.ezaigc.hr.recruiting.recruitment.enums.ApplicationDecision;
import com.ezaigc.hr.recruiting.recruitment.enums.ApplicationStepStatus;
import com.ezaigc.hr.recruiting.recruitment.enums.OfferStatus;
import com.ezaigc.hr.recruiting.submission.enums.SubmissionProcessStatus;
import com.ezaigc.hr.recruiting.submission.enums.ClientRestrictionStatus;
import org.junit.jupiter.api.Test;

class RecruitingVocabularyEnumTest {

  @Test
  void workflowStepCodeSupportIsExactAndNullSafe() {
    assertThat(WorkflowStepCode.supports("DECISION")).isTrue();
    assertThat(WorkflowStepCode.supports(null)).isFalse();
    assertThat(WorkflowStepCode.supports("")).isFalse();
    assertThat(WorkflowStepCode.supports("decision")).isFalse();
    assertThat(WorkflowStepCode.supports(" DECISION ")).isFalse();
    assertThat(WorkflowStepCode.supports("UNKNOWN")).isFalse();
  }

  @Test
  void offerAndDemandTransitionsAreOwnedByEnums() {
    assertThat(OfferStatus.NOT_STARTED.canTransitionTo(OfferStatus.IN_PROGRESS)).isTrue();
    assertThat(OfferStatus.NOT_STARTED.canTransitionTo(OfferStatus.ISSUED)).isTrue();
    assertThat(OfferStatus.IN_PROGRESS.canTransitionTo(OfferStatus.ISSUED)).isTrue();
    assertThat(OfferStatus.ISSUED.canTransitionTo(OfferStatus.ACCEPTED)).isFalse();
    assertThat(OfferStatus.ACCEPTED.isCorrectionTarget()).isFalse();
    assertThat(OfferStatus.ISSUED.allowsOnboarding()).isTrue();
    assertThat(OfferStatus.ACCEPTED.allowsOnboarding()).isTrue();
    assertThat(OfferStatus.ACCEPTED.canTransitionTo(OfferStatus.ISSUED)).isFalse();
    assertThat(OfferStatus.OCCUPIED_SQL_PREDICATE).contains("ONBOARDED")
        .doesNotContain("ISSUED", "ACCEPTED");
    assertThat(DemandStatus.DRAFT.canTransitionTo(DemandStatus.ACTIVE)).isTrue();
    assertThat(DemandStatus.CLOSED.canTransitionTo(DemandStatus.ACTIVE)).isFalse();
  }

  @Test
  void validationAndStatusGroupsAreTyped() {
    assertThat(InterviewMode.supports("ONLINE")).isTrue();
    assertThat(InterviewMode.supports("REMOTE")).isFalse();
    assertThat(ApplicationDecision.isNegative("WITHDRAW")).isTrue();
    assertThat(ApplicationStepStatus.isActive("PENDING")).isTrue();
    assertThat(ApplicationStepStatus.isFinished("SKIPPED")).isTrue();
    assertThat(SubmissionProcessStatus.isClientWorkflowActive("INTERVIEWING")).isTrue();
  }

  @Test
  void independentPersistedStatusMachinesRemainSeparated() {
    assertThat(JdStatus.values()).extracting(JdStatus::getCode).containsExactly("DRAFT", "ACTIVE", "CLOSED");
    assertThat(JdVersionStatus.values())
        .extracting(JdVersionStatus::getCode)
        .containsExactly("DRAFT", "PUBLISHED", "SUPERSEDED");
    assertThat(InterviewCandidateConfirmationStatus.values())
        .extracting(InterviewCandidateConfirmationStatus::getCode)
        .containsExactly("PENDING", "CONFIRMED", "DECLINED");
    assertThat(InterviewParticipantResponseStatus.values())
        .extracting(InterviewParticipantResponseStatus::getCode)
        .containsExactly("PENDING", "ACCEPTED", "DECLINED");
    assertThat(InterviewNotificationStatus.values())
        .extracting(InterviewNotificationStatus::getCode)
        .containsExactly(
            "PENDING_MANUAL",
            "SENT_MANUAL",
            "SENDING_EXTERNAL",
            "SENT_EXTERNAL",
            "FAILED_EXTERNAL");
    assertThat(InterviewNotificationAttemptStatus.values())
        .extracting(InterviewNotificationAttemptStatus::getCode)
        .containsExactly("SENT", "FAILED");
    assertThat(InterviewTemplateStatus.values())
        .extracting(InterviewTemplateStatus::getCode)
        .containsExactly("ACTIVE", "DISABLED");
    assertThat(ClientRestrictionStatus.values())
        .extracting(ClientRestrictionStatus::getCode)
        .containsExactly("ACTIVE", "CORRECTED", "DISABLED");
  }
}
