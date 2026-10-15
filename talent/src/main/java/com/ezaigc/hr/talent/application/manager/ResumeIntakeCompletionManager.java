package com.ezaigc.hr.talent.application.manager;

import com.ezaigc.hr.common.core.util.CommonUtils;
import com.ezaigc.hr.common.infrastructure.logging.SafeLogException;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.common.infrastructure.task.enums.AsyncTaskStatus;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.BackgroundActorExecutor;
import com.ezaigc.hr.talent.api.ResumeIntakeScoringService;
import com.ezaigc.hr.talent.api.ResumeIntakeScoringService.PrecomputedAssessment;
import com.ezaigc.hr.talent.api.ResumeIntakeScoringService.ScoringTaskRef;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.ResumeFacts;
import com.ezaigc.hr.talent.domain.dto.IntakeParsedSourceDTO;
import com.ezaigc.hr.talent.domain.entity.ResumeIntake;
import com.ezaigc.hr.talent.domain.entity.ResumeIntakeProcessing;
import com.ezaigc.hr.talent.enums.ResumeIntakeCompletionConflict;
import com.ezaigc.hr.talent.enums.ResumeIntakeScoreStatus;
import com.ezaigc.hr.talent.enums.ResumeIntakeProcessingStatus;
import com.ezaigc.hr.talent.enums.ResumeIntakeStatus;
import com.ezaigc.hr.talent.service.ResumeIntakeProcessingPersistenceService;
import com.ezaigc.hr.talent.service.TalentIntakePersistenceService;
import jakarta.annotation.Resource;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** 消费共享解析结果，在实际处理人当前权限下完成身份、入池、事实任务和关联岗位评分。 */
@Service
public class ResumeIntakeCompletionManager {
  private static final int BATCH_SIZE = 50;
  private static final Logger LOGGER = LoggerFactory.getLogger(ResumeIntakeCompletionManager.class);

  @Resource private TalentIntakePersistenceService intakes;
  @Resource private ResumeIntakeProcessingPersistenceService processing;
  @Resource private TalentIntakeApplicationManager intakeManager;
  @Resource private ResumeParsingApplicationManager resumeParsing;
  @Resource private ResumeIntakeScoringService scoring;
  @Resource private BackgroundActorExecutor backgroundActors;
  @Resource private ObjectMapper json;
  @Resource private Clock clock;
  @Resource private ResumeLifecycleApplicationManager lifecycle;

  public void completeAvailable() {
    for (ResumeIntake intake : intakes.findPendingIntakes(
        ResumeIntakeProcessingStatus.SUCCEEDED.getCode(), BATCH_SIZE)) {
      completeOne(intake);
    }
    for (ResumeIntake intake : intakes.findAdoptingIntakes(BATCH_SIZE)) {
      adoptOne(intake);
    }
    failTerminalProcessing(ResumeIntakeProcessingStatus.FAILED_FINAL.getCode(),
        ResumeIntakeStatus.FAILED_RETRYABLE);
    for (ResumeIntake intake : intakes.findScorableIntakes(BATCH_SIZE)) {
      requestScore(intake);
    }
  }

  private void completeOne(ResumeIntake intake) {
    String actorId = processingActor(intake);
    ResumeIntakeProcessing result = processing.findProcessing(intake.getProcessingId());
    if (result == null || result.getParsedFactsJson() == null) {
      failIntake(intake, "INTAKE_PROCESSING_RESULT_MISSING", actorId);
      return;
    }
    try {
      ResumeFacts facts = json.readValue(result.getParsedFactsJson(), ResumeFacts.class);
      backgroundActors.execute(actorId, () -> {
        ResumeIntake confirmed = intakeManager.autoConfirm(intake.getId(), facts);
        completeAdoption(confirmed, result, actorId);
        return confirmed;
      });
    } catch (PlatformException ex) {
      if (ResumeIntakeCompletionConflict.INTAKE_NOT_PARSING.matches(ex.code())) {
        return;
      }
      if (requiresIdentityReview(ex.code())) {
        int updated = intakes.advanceIntake(intake.getId(), ResumeIntakeStatus.PARSING.getCode(),
            ResumeIntakeStatus.IDENTITY_REVIEW_REQUIRED.getCode(), ex.code(),
            actorId, clock.instant());
        if (updated == 0) {
          intakes.advanceIntake(intake.getId(), ResumeIntakeStatus.READY_FOR_CONFIRMATION.getCode(),
              ResumeIntakeStatus.IDENTITY_REVIEW_REQUIRED.getCode(), ex.code(),
              actorId, clock.instant());
        }
      } else {
        failIntake(intake, ex.code(), actorId);
      }
    } catch (RuntimeException ex) {
      LOGGER.error("resume-intake-completion-failed intakeId={} processingId={}",
          intake.getId(), intake.getProcessingId(), SafeLogException.from(ex));
      failIntake(intake, "INTAKE_COMPLETION_FAILED", actorId);
    }
  }

  private void adoptOne(ResumeIntake intake) {
    String actorId = processingActor(intake);
    ResumeIntakeProcessing result = processing.findProcessing(intake.getProcessingId());
    if (result == null) {
      failConfirmedIntake(intake, "INTAKE_PROCESSING_RESULT_MISSING", actorId);
      return;
    }
    try {
      backgroundActors.execute(actorId, () -> {
        completeAdoption(intake, result, actorId);
        return null;
      });
    } catch (PlatformException ex) {
      failConfirmedIntake(intake, ex.code(), actorId);
    } catch (RuntimeException ex) {
      LOGGER.error("resume-intake-adoption-failed intakeId={} processingId={}",
          intake.getId(), intake.getProcessingId(), SafeLogException.from(ex));
      failConfirmedIntake(intake, "INTAKE_ADOPTION_STATUS_FAILED", actorId);
    }
  }

  private void completeAdoption(ResumeIntake intake, ResumeIntakeProcessing result, String actorId) {
    TaskSnapshot task = resumeParsing.adoptIntakeResult(intake.getResumeVersionId(), result);
    if (AsyncTaskStatus.SUCCEEDED.matches(task.status())) {
      intakes.advanceIntake(intake.getId(), ResumeIntakeStatus.CONFIRMED.getCode(),
          ResumeIntakeStatus.COMPLETED.getCode(), null,
          actorId, clock.instant());
    } else if (AsyncTaskStatus.FAILED.matches(task.status())) {
      failConfirmedIntake(intake, task.errorCode(), actorId);
    } else if (AsyncTaskStatus.CANCELLING.matches(task.status())) {
      failConfirmedIntake(intake, "INTAKE_ADOPTION_CANCELLING", actorId);
    } else if (AsyncTaskStatus.CANCELLED.matches(task.status())) {
      failConfirmedIntake(intake, "INTAKE_ADOPTION_CANCELLED", actorId);
    } else {
      intakes.advanceIntake(intake.getId(), ResumeIntakeStatus.CONFIRMED.getCode(),
          ResumeIntakeStatus.CONFIRMED.getCode(), null,
          actorId, clock.instant());
    }
  }

  private void failConfirmedIntake(ResumeIntake intake, String code, String actorId) {
    intakes.advanceIntake(intake.getId(), ResumeIntakeStatus.CONFIRMED.getCode(),
        ResumeIntakeStatus.FAILED_RETRYABLE.getCode(), code,
        actorId, clock.instant());
  }

  private void requestScore(ResumeIntake intake) {
    if (CommonUtils.isBlank(intake.getJdVersionId()) || CommonUtils.isBlank(intake.getResumeVersionId())) return;
    String actorId = processingActor(intake);
    try {
      ScoringTaskRef ref = backgroundActors.execute(actorId, () -> {
        if (!CommonUtils.isBlank(intake.getScoreRunId())) return scoring.get(intake.getScoreRunId(), actorId);
        PrecomputedAssessment assessment = precomputedAssessment(intake);
        return assessment == null
            ? scoring.enqueue(intake.getJdVersionId(), intake.getResumeVersionId(), actorId)
            : scoring.enqueue(intake.getJdVersionId(), intake.getResumeVersionId(), actorId, assessment);
      });
      String status = ResumeIntakeScoreStatus.fromTask(ref.status(), ref.retryable()).getCode();
      int updated = intakes.updateScore(intake.getId(), intake.getVersion(), ref.runId(), status, ref.errorCode(),
          actorId, clock.instant());
      if (updated == 1 && ResumeIntakeScoreStatus.SUCCEEDED.matches(status))
        lifecycle.entered(intake.getCandidateId(),intake.getResumeVersionId());
    } catch (PlatformException ex) {
      String status = !CommonUtils.isBlank(intake.getScoreRunId()) && ex.recoverable()
          ? intake.getScoreStatus()
          : ResumeIntakeScoreStatus.forFailure(ex.code(), ex.recoverable()).getCode();
      intakes.updateScore(intake.getId(), intake.getVersion(), null, status, ex.code(),
          actorId, clock.instant());
    } catch (RuntimeException ex) {
      LOGGER.error("resume-intake-score-status-failed intakeId={} scoreRunId={}",
          intake.getId(), intake.getScoreRunId(), SafeLogException.from(ex));
      intakes.updateScore(intake.getId(), intake.getVersion(), null,
          CommonUtils.isBlank(intake.getScoreRunId()) ? ResumeIntakeScoreStatus.FAILED_RETRYABLE.getCode()
              : intake.getScoreStatus(), "INTAKE_SCORE_STATUS_FAILED",
          actorId, clock.instant());
    }
  }

  private void failTerminalProcessing(String processingStatus, ResumeIntakeStatus intakeStatus) {
    for (ResumeIntake intake : intakes.findPendingIntakes(processingStatus, BATCH_SIZE)) {
      ResumeIntakeProcessing result = processing.findProcessing(intake.getProcessingId());
      intakes.advanceIntake(intake.getId(), ResumeIntakeStatus.PARSING.getCode(),
          intakeStatus.getCode(), result == null ? "INTAKE_PROCESSING_RESULT_MISSING" : result.getFailureCode(),
          processingActor(intake), clock.instant());
    }
  }

  private void failIntake(ResumeIntake intake, String code, String actorId) {
    int updated = intakes.advanceIntake(intake.getId(), ResumeIntakeStatus.PARSING.getCode(),
        ResumeIntakeStatus.FAILED_RETRYABLE.getCode(), code,
        actorId, clock.instant());
    if (updated == 0) {
      intakes.advanceIntake(intake.getId(), ResumeIntakeStatus.READY_FOR_CONFIRMATION.getCode(),
          ResumeIntakeStatus.FAILED_RETRYABLE.getCode(), code,
          actorId, clock.instant());
    }
  }

  private PrecomputedAssessment precomputedAssessment(ResumeIntake intake) {
    ResumeIntakeProcessing result = processing.findProcessing(intake.getProcessingId());
    if (result == null || result.getExtractedPagesJson() == null) return null;
    try {
      return json.readValue(result.getExtractedPagesJson(),
          IntakeParsedSourceDTO.class).getPrecomputedAssessment();
    } catch (RuntimeException failure) {
      LOGGER.warn("resume-intake-precomputed-score-unreadable intakeId={} processingId={}",
          intake.getId(), intake.getProcessingId(), SafeLogException.from(failure));
      return null;
    }
  }

  private static String processingActor(ResumeIntake intake) {
    return intake.getHandledByUserId() == null
        ? intake.getSourceRecruiterUserId()
        : intake.getHandledByUserId();
  }

  private static boolean requiresIdentityReview(String code) {
    return List.of("IDENTITY_REVIEW_REQUIRED", "IDENTITY_CONFLICT_REQUIRES_REVIEW",
        "RESUME_VERSION_DUPLICATE_CONTENT").contains(code);
  }

}
