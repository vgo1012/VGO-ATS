package com.ezaigc.hr.bootstrap;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.ezaigc.hr.identity.mapper.AccountHistoryMapper;
import com.ezaigc.hr.identity.mapper.WecomDepartmentMapper;
import com.ezaigc.hr.identity.mapper.WecomAccountLinkMapper;
import com.ezaigc.hr.identity.mapper.WecomDepartmentLinkMapper;
import com.ezaigc.hr.identity.mapper.WecomMemberMapper;
import com.ezaigc.hr.identity.mapper.WecomSyncBatchMapper;
import com.ezaigc.hr.identity.mapper.WecomSyncStateMapper;
import com.ezaigc.hr.recruiting.activity.mapper.ActivityOriginMapper;
import com.ezaigc.hr.identity.mapper.DepartmentAssignmentQueueMapper;
import com.ezaigc.hr.identity.mapper.HandoverBatchMapper;
import com.ezaigc.hr.identity.mapper.HandoverItemMapper;
import com.ezaigc.hr.identity.mapper.IdentityAccountMapper;
import com.ezaigc.hr.identity.mapper.IdentityAdministrationMapper;
import com.ezaigc.hr.identity.mapper.IdentityAuditEventMapper;
import com.ezaigc.hr.identity.mapper.MembershipMapper;
import com.ezaigc.hr.identity.mapper.ObjectGrantMapper;
import com.ezaigc.hr.identity.mapper.RoleMapper;
import com.ezaigc.hr.identity.mapper.ScopeGrantMapper;
import com.ezaigc.hr.identity.mapper.UserRoleMapper;
import com.ezaigc.hr.identity.mapper.UserSupportDepartmentMapper;
import com.ezaigc.hr.intelligence.matching.mapper.BaseEvaluationMapper;
import com.ezaigc.hr.intelligence.matching.mapper.CandidateSelectionSnapshotMapper;
import com.ezaigc.hr.intelligence.matching.mapper.ControlledCriterionMapper;
import com.ezaigc.hr.intelligence.matching.mapper.ControlledCriterionPlanMapper;
import com.ezaigc.hr.intelligence.matching.mapper.CriterionEvaluationMapper;
import com.ezaigc.hr.intelligence.matching.mapper.MatchPlanVersionMapper;
import com.ezaigc.hr.intelligence.matching.mapper.ScoringBindingMapper;
import com.ezaigc.hr.intelligence.matching.mapper.ScreeningAdmissionMapper;
import com.ezaigc.hr.intelligence.matching.mapper.MatchRunAttemptMapper;
import com.ezaigc.hr.intelligence.matching.mapper.MatchRunItemOutcomeMapper;
import com.ezaigc.hr.intelligence.matching.mapper.MatchRunMapper;
import com.ezaigc.hr.intelligence.matching.mapper.AiProfessionalAssessmentMapper;
import com.ezaigc.hr.intelligence.matching.mapper.MatchingAuditEventMapper;
import com.ezaigc.hr.intelligence.matching.mapper.MatchingConfigurationMapper;
import com.ezaigc.hr.intelligence.matching.mapper.MatchingHighlightMapper;
import com.ezaigc.hr.intelligence.matching.mapper.MatchingIdempotencyMapper;
import com.ezaigc.hr.intelligence.matching.mapper.PersonalPreferenceMapper;
import com.ezaigc.hr.intelligence.matching.mapper.SelectionItemMapper;
import com.ezaigc.hr.intelligence.matching.mapper.WeightProfileMapper;
import com.ezaigc.hr.intelligence.matching.mapper.WeightProfileVersionMapper;
import com.ezaigc.hr.intelligence.matching.mapper.WeightedResultMapper;
import com.ezaigc.hr.intelligence.search.mapper.ProjectionCheckpointMapper;
import com.ezaigc.hr.intelligence.search.mapper.StateReplayCheckpointMapper;
import com.ezaigc.hr.reporting.outbox.mapper.SearchTaskRecoveryMapper;
import com.ezaigc.hr.intelligence.search.mapper.RebuildRunMapper;
import com.ezaigc.hr.intelligence.search.mapper.SearchIndexResourceMapper;
import com.ezaigc.hr.recruiting.demand.mapper.DemandAuditEventMapper;
import com.ezaigc.hr.recruiting.demand.mapper.DemandCatalogMapper;
import com.ezaigc.hr.recruiting.demand.mapper.DemandCollaboratorMapper;
import com.ezaigc.hr.recruiting.demand.mapper.DemandEntityMapper;
import com.ezaigc.hr.recruiting.demand.mapper.DemandIdempotencyMapper;
import com.ezaigc.hr.recruiting.demand.mapper.JdAiModelRunMapper;
import com.ezaigc.hr.recruiting.demand.mapper.JdEntityMapper;
import com.ezaigc.hr.recruiting.demand.mapper.JdCodeOptionMapper;
import com.ezaigc.hr.recruiting.demand.mapper.JdTitleSuggestionMapper;
import com.ezaigc.hr.recruiting.demand.mapper.JdSourceMapper;
import com.ezaigc.hr.recruiting.demand.mapper.JdResumeSourceMapper;
import com.ezaigc.hr.recruiting.demand.mapper.MailSourceMessageMapper;
import com.ezaigc.hr.recruiting.demand.mapper.MailJdRoutingMapper;
import com.ezaigc.hr.recruiting.demand.mapper.JdParsingMapper;
import com.ezaigc.hr.recruiting.demand.mapper.JdVersionMapper;
import com.ezaigc.hr.recruiting.demand.mapper.OwnershipHistoryMapper;
import com.ezaigc.hr.recruiting.demand.mapper.PositionMapper;
import com.ezaigc.hr.recruiting.demand.mapper.ProjectMapper;
import com.ezaigc.hr.recruiting.demand.mapper.WorkflowStepMapper;
import com.ezaigc.hr.recruiting.demand.mapper.WorkflowTemplateMapper;
import com.ezaigc.hr.recruiting.interview.mapper.FeedbackMapper;
import com.ezaigc.hr.recruiting.interview.mapper.FeedbackVersionMapper;
import com.ezaigc.hr.recruiting.interview.mapper.IdempotencyRecordMapper;
import com.ezaigc.hr.recruiting.interview.mapper.InterviewMapper;
import com.ezaigc.hr.recruiting.interview.mapper.InterviewExternalCalendarAttemptMapper;
import com.ezaigc.hr.recruiting.interview.mapper.InterviewExternalCalendarProjectionMapper;
import com.ezaigc.hr.recruiting.interview.mapper.NotificationAttemptMapper;
import com.ezaigc.hr.recruiting.interview.mapper.NotificationIntentMapper;
import com.ezaigc.hr.recruiting.interview.mapper.ParticipantMapper;
import com.ezaigc.hr.recruiting.interview.mapper.ScheduleHistoryMapper;
import com.ezaigc.hr.recruiting.recruitment.mapper.ApplicationCollaboratorMapper;
import com.ezaigc.hr.recruiting.recruitment.mapper.ApplicationMapper;
import com.ezaigc.hr.recruiting.recruitment.mapper.CollaborationRequestMapper;
import com.ezaigc.hr.recruiting.recruitment.mapper.CommunicationMapper;
import com.ezaigc.hr.recruiting.recruitment.mapper.DecisionMapper;
import com.ezaigc.hr.recruiting.recruitment.mapper.EngagementLockMapper;
import com.ezaigc.hr.recruiting.recruitment.mapper.FollowUpMapper;
import com.ezaigc.hr.recruiting.recruitment.mapper.FollowUpPlanMapper;
import com.ezaigc.hr.recruiting.recruitment.mapper.FollowUpPlanHistoryMapper;
import com.ezaigc.hr.recruiting.recruitment.mapper.IntentionMapper;
import com.ezaigc.hr.recruiting.recruitment.mapper.LockHistoryMapper;
import com.ezaigc.hr.recruiting.recruitment.mapper.LockPolicyHistoryMapper;
import com.ezaigc.hr.recruiting.recruitment.mapper.LockPolicyMapper;
import com.ezaigc.hr.recruiting.recruitment.mapper.OfferStatusRecordMapper;
import com.ezaigc.hr.recruiting.recruitment.mapper.OnboardingEventMapper;
import com.ezaigc.hr.recruiting.recruitment.mapper.OnboardingProcessMapper;
import com.ezaigc.hr.recruiting.recruitment.mapper.PathChangeMapper;
import com.ezaigc.hr.recruiting.recruitment.mapper.StageHistoryMapper;
import com.ezaigc.hr.recruiting.submission.mapper.ClientRestrictionCurrentMapper;
import com.ezaigc.hr.recruiting.submission.mapper.ClientRestrictionMapper;
import com.ezaigc.hr.recruiting.submission.mapper.RiskAcknowledgementMapper;
import com.ezaigc.hr.recruiting.submission.mapper.SendSnapshotMapper;
import com.ezaigc.hr.recruiting.submission.mapper.SubmissionMapper;
import com.ezaigc.hr.reporting.mapper.AsyncTaskAttemptMapper;
import com.ezaigc.hr.reporting.mapper.AsyncTaskItemMapper;
import com.ezaigc.hr.reporting.mapper.AsyncTaskMapper;
import com.ezaigc.hr.reporting.outbox.mapper.OutboxEventMapper;
import com.ezaigc.hr.reporting.mapper.AuditCaptureCheckpointMapper;
import com.ezaigc.hr.reporting.mapper.MetricCaptureCheckpointMapper;
import com.ezaigc.hr.reporting.mapper.AuditMapper;
import com.ezaigc.hr.reporting.mapper.MetricMapper;
import com.ezaigc.hr.reporting.mapper.RecruitmentAnalyticsMapper;
import com.ezaigc.hr.reporting.monthlyreport.mapper.MonthlyReportFileMapper;
import com.ezaigc.hr.reporting.monthlyreport.mapper.MonthlyReportMapper;
import com.ezaigc.hr.talent.mapper.CandidateIdentityMapper;
import com.ezaigc.hr.talent.mapper.CandidateMapper;
import com.ezaigc.hr.talent.mapper.ResumeLifecycleMapper;
import com.ezaigc.hr.talent.mapper.ResumeLifecycleRuntimeMapper;
import com.ezaigc.hr.talent.mapper.ResumeLifecycleRunMapper;
import com.ezaigc.hr.talent.mapper.ResumeJdScoreBindingMapper;
import com.ezaigc.hr.talent.mapper.WorkCalendarMapper;
import com.ezaigc.hr.talent.mapper.WorkCalendarDayMapper;
import com.ezaigc.hr.talent.mapper.WorkCalendarYearMapper;
import com.ezaigc.hr.talent.mapper.CandidateMergeHistoryMapper;
import com.ezaigc.hr.talent.mapper.CompanyBlacklistCurrentMapper;
import com.ezaigc.hr.talent.mapper.DownloadAuthorizationMapper;
import com.ezaigc.hr.talent.mapper.EducationFactMapper;
import com.ezaigc.hr.talent.mapper.EducationRecordMapper;
import com.ezaigc.hr.talent.mapper.ExtractionRunMapper;
import com.ezaigc.hr.talent.mapper.FactCorrectionChangeMapper;
import com.ezaigc.hr.talent.mapper.IdentityConflictMapper;
import com.ezaigc.hr.talent.mapper.IntakeBatchMapper;
import com.ezaigc.hr.talent.mapper.ProjectFactMapper;
import com.ezaigc.hr.talent.mapper.ResumeExtractionPageMapper;
import com.ezaigc.hr.talent.mapper.ResumeMapper;
import com.ezaigc.hr.talent.mapper.ResumeModelRunMapper;
import com.ezaigc.hr.talent.mapper.ResumeParseSnapshotMapper;
import com.ezaigc.hr.talent.mapper.ResumeParsingMapper;
import com.ezaigc.hr.talent.mapper.ResumeIntakeOutboxMapper;
import com.ezaigc.hr.talent.mapper.ResumeIntakeProcessingMapper;
import com.ezaigc.hr.talent.mapper.ResumeVersionMapper;
import com.ezaigc.hr.talent.mapper.RiskMapper;
import com.ezaigc.hr.talent.mapper.SimpleFactMapper;
import com.ezaigc.hr.talent.mapper.SkillFactMapper;
import com.ezaigc.hr.talent.mapper.SourceChannelMapper;
import com.ezaigc.hr.talent.mapper.TalentIdempotencyMapper;
import com.ezaigc.hr.talent.mapper.TalentIntakeMapper;
import com.ezaigc.hr.talent.mapper.TalentProcessEventMapper;
import com.ezaigc.hr.talent.mapper.WorkFactMapper;
import com.ezaigc.hr.integration.mail.mapper.MailResumeMapper;
import org.springframework.transaction.support.TransactionTemplate;

/** Shared full-application test setup that replaces database Mappers with test doubles. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ActiveProfiles("test")
@SpringBootTest(
    properties = {
      "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration",
      "hr.resume-parsing.worker-enabled=false",
      "hr.resume-intake.worker-enabled=false",
      "hr.jd-parsing.worker-enabled=false",
      "hr.matching.worker-enabled=false",
      "hr.search.worker-enabled=false",
      "hr.reporting.audit-capture.enabled=false",
      "hr.reporting.metric-capture.enabled=false",
      "hr.pagination.cursor-signing-key=test-only-cursor-signing-key-32-bytes"
    })
@AutoConfigureMockMvc
@MockitoBean(name = "com.ezaigc.hr.reporting.mapper.RecruitmentAnalyticsMapper", types = RecruitmentAnalyticsMapper.class)
@MockitoBean(
    types = {
      AccountHistoryMapper.class,
      WecomAccountLinkMapper.class,
      WecomDepartmentLinkMapper.class,
      WecomDepartmentMapper.class,
      WecomMemberMapper.class,
      WecomSyncBatchMapper.class,
      WecomSyncStateMapper.class,
      DepartmentAssignmentQueueMapper.class,
      HandoverBatchMapper.class,
      HandoverItemMapper.class,
      IdentityAccountMapper.class,
      IdentityAdministrationMapper.class,
      IdentityAuditEventMapper.class,
      MembershipMapper.class,
      ObjectGrantMapper.class,
      RoleMapper.class,
      ScopeGrantMapper.class,
      UserRoleMapper.class,
      UserSupportDepartmentMapper.class,
      DemandCatalogMapper.class,
      DemandCollaboratorMapper.class,
      DemandAuditEventMapper.class,
      DemandEntityMapper.class,
      DemandIdempotencyMapper.class,
      JdEntityMapper.class,
      JdCodeOptionMapper.class,
      JdTitleSuggestionMapper.class,
      JdSourceMapper.class,
      JdResumeSourceMapper.class,
      MailSourceMessageMapper.class,
      MailJdRoutingMapper.class,
      JdAiModelRunMapper.class,
      JdVersionMapper.class,
      OwnershipHistoryMapper.class,
      PositionMapper.class,
      ProjectMapper.class,
      WorkflowStepMapper.class,
      WorkflowTemplateMapper.class,
      JdParsingMapper.class,
      FeedbackMapper.class,
      FeedbackVersionMapper.class,
      IdempotencyRecordMapper.class,
      InterviewMapper.class,
      InterviewExternalCalendarAttemptMapper.class,
      InterviewExternalCalendarProjectionMapper.class,
      NotificationAttemptMapper.class,
      NotificationIntentMapper.class,
      ParticipantMapper.class,
      ScheduleHistoryMapper.class,
      MatchRunMapper.class,
      AiProfessionalAssessmentMapper.class,
      BaseEvaluationMapper.class,
      CandidateSelectionSnapshotMapper.class,
      CriterionEvaluationMapper.class,
      MatchRunAttemptMapper.class,
      MatchRunItemOutcomeMapper.class,
      MatchingHighlightMapper.class,
      SelectionItemMapper.class,
      WeightedResultMapper.class,
      MatchingConfigurationMapper.class,
      ControlledCriterionMapper.class,
      ControlledCriterionPlanMapper.class,
      MatchPlanVersionMapper.class,
      ScoringBindingMapper.class,
      ScreeningAdmissionMapper.class,
      MatchingAuditEventMapper.class,
      MatchingIdempotencyMapper.class,
      PersonalPreferenceMapper.class,
      WeightProfileMapper.class,
      WeightProfileVersionMapper.class,
      ApplicationMapper.class,
      ActivityOriginMapper.class,
      ApplicationCollaboratorMapper.class,
      CollaborationRequestMapper.class,
      CommunicationMapper.class,
      DecisionMapper.class,
      EngagementLockMapper.class,
      FollowUpMapper.class,
      FollowUpPlanMapper.class,
      FollowUpPlanHistoryMapper.class,
      com.ezaigc.hr.recruiting.recruitment.mapper.IdempotencyRecordMapper.class,
      IntentionMapper.class,
      LockHistoryMapper.class,
      LockPolicyHistoryMapper.class,
      LockPolicyMapper.class,
      OfferStatusRecordMapper.class,
      OnboardingEventMapper.class,
      OnboardingProcessMapper.class,
      com.ezaigc.hr.recruiting.recruitment.mapper.OwnershipHistoryMapper.class,
      PathChangeMapper.class,
      StageHistoryMapper.class,
      com.ezaigc.hr.recruiting.recruitment.mapper.WorkflowStepMapper.class,
      AsyncTaskAttemptMapper.class,
      AsyncTaskItemMapper.class,
      AsyncTaskMapper.class,
      OutboxEventMapper.class,
      AuditCaptureCheckpointMapper.class,
      MetricCaptureCheckpointMapper.class,
      AuditMapper.class,
      MetricMapper.class,
      MonthlyReportMapper.class,
      MonthlyReportFileMapper.class,
      ProjectionCheckpointMapper.class,
      StateReplayCheckpointMapper.class,
      SearchTaskRecoveryMapper.class,
      RebuildRunMapper.class,
      SearchIndexResourceMapper.class,
      ClientRestrictionCurrentMapper.class,
      ClientRestrictionMapper.class,
      com.ezaigc.hr.recruiting.submission.mapper.FeedbackMapper.class,
      com.ezaigc.hr.recruiting.submission.mapper.IdempotencyRecordMapper.class,
      RiskAcknowledgementMapper.class,
      SendSnapshotMapper.class,
      SubmissionMapper.class,
      ResumeParsingMapper.class,
      ResumeIntakeOutboxMapper.class,
      ResumeIntakeProcessingMapper.class,
      RiskMapper.class,
      CompanyBlacklistCurrentMapper.class,
      CandidateIdentityMapper.class,
      CandidateMapper.class,
      ResumeLifecycleMapper.class,
      ResumeLifecycleRuntimeMapper.class,
      ResumeLifecycleRunMapper.class,
      ResumeJdScoreBindingMapper.class,
      WorkCalendarMapper.class,
      WorkCalendarDayMapper.class,
      WorkCalendarYearMapper.class,
      CandidateMergeHistoryMapper.class,
      DownloadAuthorizationMapper.class,
      EducationRecordMapper.class,
      EducationFactMapper.class,
      ExtractionRunMapper.class,
      FactCorrectionChangeMapper.class,
      IdentityConflictMapper.class,
      IntakeBatchMapper.class,
      ResumeMapper.class,
      ResumeExtractionPageMapper.class,
      ResumeModelRunMapper.class,
      ResumeParseSnapshotMapper.class,
      ResumeVersionMapper.class,
      ProjectFactMapper.class,
      SimpleFactMapper.class,
      SkillFactMapper.class,
      SourceChannelMapper.class,
      TalentIdempotencyMapper.class,
      TalentProcessEventMapper.class,
      TalentIntakeMapper.class,
      WorkFactMapper.class,
      MailResumeMapper.class,
      TransactionTemplate.class
    })
@interface NoDatabasePlatformTest {}
