package com.ezaigc.hr.recruiting.demand.service.validator;

import com.ezaigc.hr.common.web.error.FieldViolation;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.recruiting.demand.domain.dto.ValidatedWorkflowStepDTO;
import com.ezaigc.hr.recruiting.demand.domain.dto.WorkflowStepDraft;
import com.ezaigc.hr.recruiting.demand.enums.RecruitmentType;
import com.ezaigc.hr.recruiting.demand.enums.WorkflowCommunicationChannel;
import com.ezaigc.hr.recruiting.demand.enums.WorkflowStepCode;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.util.stream.IntStream;

/** 流程模板业务校验器，负责验证输入和领域约束。 */
@Component
public class WorkflowTemplateValidator {

  private static final Set<WorkflowStepCode> CLIENT_STEPS =
      /** 根据给定值创建当前对象。 */
      EnumSet.of(
          WorkflowStepCode.PRE_SUBMISSION_INTERNAL_INTERVIEW,
          WorkflowStepCode.CLIENT_SUBMISSION,
          WorkflowStepCode.CLIENT_AI_INTERVIEW);

  public List<ValidatedWorkflowStepDTO> validate(
      String recruitmentType, List<WorkflowStepDraft> drafts) {
    if (!RecruitmentType.supports(recruitmentType)) {
      throw invalid("recruitmentType", "招聘类型只允许 INTERNAL 或 EXTERNAL");
    }
    if (drafts == null || drafts.isEmpty()) {
      throw invalid("steps", "流程至少需要人工决定步骤");
    }
    Set<WorkflowStepCode> seen = new HashSet<>();
    for (WorkflowStepDraft draft : drafts) {
      if (draft == null || draft.getCode() == null || !seen.add(draft.getCode())) {
        throw invalid("steps", "流程步骤不能为空或重复");
      }
    }
    if (!seen.contains(WorkflowStepCode.DECISION)) {
      throw invalid("steps", "流程必须且只能包含一个人工决定步骤");
    }
    if (RecruitmentType.INTERNAL.matches(recruitmentType)
        && seen.stream().anyMatch(CLIENT_STEPS::contains)) {
      throw new PlatformException(
          HttpStatus.UNPROCESSABLE_CONTENT, "CLIENT_STEP_NOT_ALLOWED", "内部招聘流程不能包含甲方或外派步骤", false);
    }

    int lastRank = -1;
    for (WorkflowStepDraft draft : drafts) {
      int rank = draft.getCode().ordinal();
      if (rank <= lastRank) {
        throw invalid("steps", "流程步骤顺序不合法");
      }
      lastRank = rank;
    }
    int decisionIndex = indexOf(drafts, WorkflowStepCode.DECISION);
    if (decisionIndex != drafts.size() - 1
        && !(decisionIndex == drafts.size() - 2
            && drafts.get(drafts.size() - 1).getCode() == WorkflowStepCode.OFFER)) {
      throw invalid("steps", "人工决定之后只能配置 Offer");
    }

    boolean hasAiInterview = seen.contains(WorkflowStepCode.CLIENT_AI_INTERVIEW);
    boolean hasInterviewRound =
        seen.contains(WorkflowStepCode.INTERVIEW_ROUND_1)
            || seen.contains(WorkflowStepCode.INTERVIEW_ROUND_2)
            || seen.contains(WorkflowStepCode.INTERVIEW_ROUND_3);
    if (RecruitmentType.EXTERNAL.matches(recruitmentType) && hasAiInterview != hasInterviewRound) {
      throw invalid("steps", "外派流程启用甲方 AI 面试时必须同时配置至少一轮甲方面试");
    }

    return IntStream.range(0, drafts.size())
        .mapToObj(index -> validated(index + 1, drafts.get(index)))
        .toList();
  }

  /** 校验 D。 */
  private static ValidatedWorkflowStepDTO validated(int sequence, WorkflowStepDraft draft) {
    String channel = draft.getDefaultFollowUpChannel();
    if (draft.getCode() == WorkflowStepCode.CANDIDATE_COMMUNICATION) {
      channel =
          channel == null || channel.isBlank()
              ? WorkflowCommunicationChannel.PHONE.name()
              : channel.trim().toUpperCase(Locale.ROOT);
      if (!WorkflowCommunicationChannel.supports(channel)) {
        throw invalid("steps.defaultFollowUpChannel", "沟通渠道只允许 PHONE、WECHAT 或 EMAIL");
      }
    } else if (channel != null && !channel.isBlank()) {
      throw invalid("steps.defaultFollowUpChannel", "只有候选人沟通步骤可以配置默认渠道");
    } else {
      channel = null;
    }
    boolean optional =
        switch (draft.getCode()) {
          case DECISION, OFFER, CLIENT_SUBMISSION -> false;
          default -> true;
        };
    return new ValidatedWorkflowStepDTO(draft.getCode(), sequence, optional, channel);
  }

  private static int indexOf(List<WorkflowStepDraft> drafts, WorkflowStepCode code) {
    for (int index = 0; index < drafts.size(); index++) {
      if (drafts.get(index).getCode() == code) {
        return index;
      }
    }
    return -1;
  }

  private static PlatformException invalid(String field, String message) {
    return new PlatformException(
        HttpStatus.UNPROCESSABLE_CONTENT,
        "WORKFLOW_TEMPLATE_INVALID",
        message,
        false,
        List.of(new FieldViolation(field, message)));
  }
}
