import { render, screen } from '@testing-library/react';
import { expect, it } from 'vitest';
import { JdRecruitmentCycle } from './JdRecruitmentCycle';
import type { JdSummary } from './api';

const createdAt = '2026-09-01T00:00:00Z';
const jd = (extra: Partial<JdSummary> = {}): JdSummary => ({ id:'jd', title:'周期测试岗位', clientId:null, clientName:null, status:'ACTIVE', currentVersionNo:1, contentShared:false, appliedFromJdVersionId:null, ownerUserId:'hr', ownerDepartmentId:'dept', recruitmentType:'INTERNAL', version:'1', createTime:createdAt, headcount:2, occupiedHeadcount:1, onboardedHeadcount:0, remainingHeadcount:1, recruitmentCycleDays:10, ...extra });
const due = Date.parse(createdAt) + 10 * 86_400_000;

it('shows a missing recruitment cycle explicitly', () => {
  render(<JdRecruitmentCycle value={jd({ recruitmentCycleDays:null })} now={due + 1} />);
  expect(screen.getByText('招聘周期待填写')).toBeInTheDocument();
});

it('warns only after an active JD without completed onboarding passes its deadline', () => {
  const { rerender } = render(<JdRecruitmentCycle value={jd()} now={due} />);
  expect(screen.getByText('招聘周期 10 天')).toBeInTheDocument();
  rerender(<JdRecruitmentCycle value={jd()} now={due + 1} />);
  expect(screen.getByText('招聘已超期 1 天')).toBeInTheDocument();
  rerender(<JdRecruitmentCycle value={jd({ occupiedHeadcount:2, remainingHeadcount:0 })} now={due + 86_400_000} />);
  expect(screen.getByText('招聘已超期 1 天')).toBeInTheDocument();
  rerender(<JdRecruitmentCycle value={jd({ occupiedHeadcount:2, remainingHeadcount:0, onboardedHeadcount:2 })} now={due + 86_400_000} />);
  expect(screen.queryByText(/招聘已超期/)).not.toBeInTheDocument();
  rerender(<JdRecruitmentCycle value={jd({ status:'CLOSED' })} now={due + 86_400_000} />);
  expect(screen.queryByText(/招聘已超期/)).not.toBeInTheDocument();
});
