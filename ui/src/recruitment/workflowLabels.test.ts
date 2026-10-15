import { describe, expect, it } from 'vitest';
import { interviewStepLabel } from './workflowLabels';

describe('interview step labels', () => {
  it.each([
    ['CLIENT_AI_INTERVIEW', 1, undefined, '甲方 AI 面试 · 第 1 次'],
    ['INTERVIEW_ROUND_1', 1, 'EXTERNAL', '面试 · 第 1 轮'],
    ['INTERVIEW_ROUND_1', 2, 'EXTERNAL', '面试 · 第 2 轮'],
    ['INTERVIEW_ROUND_1', 6, 'INTERNAL', '面试 · 第 6 轮'],
    ['PRE_SUBMISSION_INTERNAL_INTERVIEW', 2, 'EXTERNAL', '内部预面 · 第 2 次'],
  ] as const)('labels %s distinctly', (stepCode, roundNo, recruitmentType, expected) => {
    expect(interviewStepLabel({ stepCode, roundNo }, recruitmentType)).toBe(expected);
  });

  it('uses the saved per-round label independently of recruitment type',()=>{
    expect(interviewStepLabel({stepCode:'INTERVIEW_ROUND_1',roundNo:2,interviewType:'EXTERNAL'},'INTERNAL')).toBe('外部面试 · 第 2 轮');
    expect(interviewStepLabel({stepCode:'INTERVIEW_ROUND_1',roundNo:1,interviewType:'INTERNAL'},'EXTERNAL')).toBe('内部面试 · 第 1 轮');
  });

  it('keeps the specific type when no round number is available', () => {
    expect(interviewStepLabel({ stepCode: 'CLIENT_AI_INTERVIEW' })).toBe('甲方 AI 面试（人工代录）');
    expect(interviewStepLabel({})).toBe('面试');
  });
});
