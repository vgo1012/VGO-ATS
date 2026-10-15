import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ConfigProvider } from 'antd';
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ActionModal, Applications } from './RecruitmentWorkspacePage';
import dayjs from 'dayjs';
import { businessWeek, dateRangeParams } from '../components/dateRange';
import { demandApi } from '../demand/api';
import type { RecruitmentApplication, WorkflowStep } from '../recruitment/api';

afterEach(cleanup);

vi.mock('../demand/api', () => ({ demandApi: { jds: vi.fn(), jdVersions: vi.fn().mockResolvedValue({items:[]}), jdVersionsForDemand: vi.fn().mockResolvedValue({items:[]}) } }));
vi.mock('../auth/session', () => ({useSession:()=>({actor:{userId:'owner-1',displayName:'招聘负责人',superAdmin:false,permissions:[]}})}));
vi.mock('../talent/api', () => ({talentApi:{candidate:vi.fn().mockResolvedValue({id:'candidate-1',fullName:'林雨桐'}),candidateBatch:vi.fn().mockResolvedValue([{id:'candidate-1',fullName:'林雨桐'}]),resumeVersionFactSummaries:vi.fn().mockResolvedValue([]),resumeVersionReferences:vi.fn().mockResolvedValue([]),resumeVersions:vi.fn().mockResolvedValue({items:[]})}}));

const application: RecruitmentApplication = {
  id: 'application-1',
  candidateId: 'candidate-1',
  demandId: 'demand-1',
  jdId: 'jd-1',
  jdVersionId: 'jd-version-1',
  resumeVersionId: 'resume-version-1',
  workflowTemplateVersionId: 'workflow-version-1',
  matchPlanVersionId: 'match-plan-version-1',
  recruitmentType: 'EXTERNAL',
  currentStage: 'PATH_SELECTION',
  outcome: null,
  ownerUserId: 'owner-1',
  ownerDepartmentId: 'department-1',
  terminalAt: null,
  version: 0,
  createId: 'owner-1',
  createTime: '2026-09-03T10:00:00Z',
  updateId: 'owner-1',
  updateTime: '2026-09-03T10:00:00Z',
};

describe('Applications', () => {
  beforeEach(() => {
    vi.mocked(demandApi.jds).mockResolvedValue({ items: [], nextCursor: null, hasMore: false });
  });

  it('keeps Sunday in the same business week across a year boundary', () => {
    expect(dateRangeParams(businessWeek(dayjs.tz('2027-01-03', 'Asia/Shanghai')))).toEqual({
      from: '2026-12-27T16:00:00.000Z', to: '2027-01-03T16:00:00.000Z',
    });
  });

  it('renders compact columns and actual workflow stage', () => {
    render(
      <ConfigProvider>
        <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
          <Applications
            items={[application]}
            loading={false}
            error={null}
            filters={{}}
            onFilters={vi.fn()}
            onOpen={vi.fn()}
            page={1}
            pageSize={20}
            onPageSizeChange={vi.fn()}
            hasPrevious={false}
            hasNext={false}
            onPrevious={vi.fn()}
            onNext={vi.fn()}
          />
        </QueryClientProvider>
      </ConfigProvider>,
    );

    expect(screen.queryByText('更多条件')).not.toBeInTheDocument();
    for (const name of ['招聘类型', 'JD 版本', '当前步骤']) expect(screen.queryByRole('combobox', { name })).not.toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: '甲方公司' })).toBeEnabled();
    expect(screen.queryByText('Invalid Date')).not.toBeInTheDocument();
    expect(screen.queryByRole('combobox', { name: '面试类型' })).not.toBeInTheDocument();
    expect(screen.queryByRole('columnheader', { name: '面试类型' })).not.toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: '应聘岗位' })).toBeInTheDocument();
    expect(screen.queryByText('待 HR 跟进', { selector: '.recruitment-stage-detail' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '详情与版本' })).toBeInTheDocument();
  }, 10_000);
});

describe('Internal interview completion', () => {
  it.each([false, true])('completes interview rounds without client recommendation (%s)', (external) => {
    render(<ConfigProvider><ActionModal
      value={{kind:'step',step:{stepCode:'INTERVIEW_ROUND_1'} as WorkflowStep}}
      external={external} submissions={[]} currentOffer={undefined} loading={false} error={null}
      application={application}
      onClose={vi.fn()} onSubmit={vi.fn()}
    /></ConfigProvider>);
    expect(screen.queryByLabelText(/已发送的客户推荐/)).not.toBeInTheDocument();
    expect(screen.queryByText('请先完成推荐给客户')).not.toBeInTheDocument();
    expect(screen.getByRole('button', {name:/^OK$|确\s*定/})).toBeEnabled();
    expect(screen.queryByLabelText('跟进渠道')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('跟进事实')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('下一步计划（选填）')).not.toBeInTheDocument();
    expect(screen.getByLabelText('结果备注（选填）')).toBeInTheDocument();
  });
});
