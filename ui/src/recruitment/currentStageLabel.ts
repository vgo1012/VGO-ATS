import type { RecruitmentApplication, WorkflowStep } from './api';
import { interviewStepLabel,stepLabel } from './workflowLabels';

const stages: Record<RecruitmentApplication['currentStage'], string> = {
  PATH_SELECTION: '待 HR 跟进', IN_PROCESS: '招聘推进中', DECISION: '待人工决定',
  OFFER: 'Offer / 入职', TERMINATED: '已终止', ONBOARDED: '已入职',
};

export function recruitmentStatus(value: RecruitmentApplication): '进行中' | '已完成' | '已取消' {
  if (value.currentStage === 'ONBOARDED' || value.onboardingStatus === 'IN_PROGRESS' || value.onboardingStatus === 'ONBOARDED') return '已完成';
  if (value.terminalAt || value.currentStage === 'TERMINATED') return '已取消';
  return '进行中';
}

export function currentStageLabel(application: RecruitmentApplication, steps?: readonly WorkflowStep[]): string {
  if (application.currentStage !== 'IN_PROCESS' || application.terminalAt) return stages[application.currentStage];
  const current = steps
    ?.filter(step => step.status === 'PENDING' || step.status === 'IN_PROGRESS')
    .sort((left, right) => left.sequenceNo - right.sequenceNo)[0];
  const code = steps ? current?.stepCode : application.currentStepCode;
  const round = steps ? current?.roundNo : application.currentStepRoundNo;
  const status = steps ? current?.status : application.currentStepStatus;
  if (!code || !['PENDING', 'IN_PROGRESS'].includes(status ?? '')) return stages.IN_PROCESS;
  let label = stepLabel[code];
  if (code === 'PRE_SUBMISSION_INTERNAL_INTERVIEW' || code === 'CLIENT_AI_INTERVIEW' || code.startsWith('INTERVIEW_ROUND_')) {
    const interviewType = !steps || (code === application.currentStepCode && round === application.currentStepRoundNo) ? application.currentInterviewType : undefined;
    label = interviewStepLabel({stepCode:code,roundNo:round,interviewType});
  } else if (code === 'CLIENT_SUBMISSION') return stages.PATH_SELECTION;
  if (!label) return stages.IN_PROCESS;
  return `${label} · ${status === 'PENDING' ? '待处理' : '进行中'}`;
}
