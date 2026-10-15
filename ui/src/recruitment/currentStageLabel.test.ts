import { describe, expect, it } from 'vitest';
import type { RecruitmentApplication, WorkflowStep } from './api';
import { currentStageLabel } from './currentStageLabel';

const application = { currentStage: 'IN_PROCESS', terminalAt: null, recruitmentType: 'EXTERNAL', currentStepCode: 'CANDIDATE_COMMUNICATION', currentStepStatus: 'IN_PROGRESS' } as RecruitmentApplication;

describe('当前招聘阶段', () => {
  it('列表显示当前实际沟通步骤及状态', () => {
    expect(currentStageLabel(application)).toBe('候选人沟通 · 进行中');
  });
  it.each([
    ['PRE_SUBMISSION_INTERNAL_INTERVIEW', 'EXTERNAL', 4, '内部预面 · 第 4 次 · 进行中'],
    ['INTERVIEW_ROUND_2', 'EXTERNAL', 2, '面试 · 第 2 轮 · 进行中'],
    ['INTERVIEW_ROUND_2', 'INTERNAL', null, '面试 · 第 2 轮 · 进行中'],
    ['CLIENT_AI_INTERVIEW', 'EXTERNAL', 1, '甲方 AI 面试 · 第 1 次 · 进行中'],
    ['CLIENT_SUBMISSION', 'EXTERNAL', null, '待 HR 跟进'],
  ] as const)('区分环节 %s 的招聘类型和真实轮次', (code, type, round, expected) => {
    expect(currentStageLabel({ ...application, currentStepCode: code, recruitmentType: type, currentStepRoundNo: round })).toBe(expected);
  });
  it('当前面试使用实际标签，不能按招聘类型推断', () => {
    const current = { ...application, recruitmentType: 'INTERNAL' as const, currentStepCode: 'INTERVIEW_ROUND_2', currentStepRoundNo: 2, currentInterviewType: 'EXTERNAL' };
    expect(currentStageLabel(current)).toBe('外部面试 · 第 2 轮 · 进行中');
    expect(currentStageLabel({ ...current, currentInterviewType: 'INTERNAL' })).toBe('内部面试 · 第 2 轮 · 进行中');
  });
  it('详情取最早未结束步骤，不使用历史、后续或旧摘要', () => {
    const steps = [
      { stepCode: 'CLIENT_SUBMISSION', sequenceNo: 3, status: 'PENDING' },
      { stepCode: 'PRE_SUBMISSION_INTERNAL_INTERVIEW', roundNo: 1, sequenceNo: 1, status: 'PASSED' },
      { stepCode: 'PRE_SUBMISSION_INTERNAL_INTERVIEW', roundNo: 2, sequenceNo: 2, status: 'IN_PROGRESS' },
    ] as WorkflowStep[];
    expect(currentStageLabel(application, steps)).toBe('内部预面 · 第 2 次 · 进行中');
    expect(steps[0]!.sequenceNo).toBe(3);
    expect(currentStageLabel(application, [])).toBe('招聘推进中');
  });
  it('未开始、终态及未知摘要不冒充进行中', () => {
    expect(currentStageLabel({ ...application, currentStepStatus: 'PENDING' })).toBe('候选人沟通 · 待处理');
    expect(currentStageLabel({ ...application, currentStage: 'TERMINATED', terminalAt: '2026-09-09T00:00:00Z' })).toBe('已终止');
    expect(currentStageLabel({ ...application, currentStage: 'PATH_SELECTION' })).toBe('待 HR 跟进');
    expect(currentStageLabel({ ...application, currentStepStatus: 'PASSED' })).toBe('招聘推进中');
    expect(currentStageLabel({ ...application, currentStepCode: 'UNKNOWN' })).toBe('招聘推进中');
  });
});
