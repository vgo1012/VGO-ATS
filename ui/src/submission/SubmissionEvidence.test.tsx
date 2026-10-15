import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { ApiProblemError } from '../api/problem';
import { demandApi } from '../demand/api';
import type { SubmissionDetails } from './api';
import { SubmissionEvidence } from './SubmissionEvidence';

vi.mock('../display/BusinessReferences', () => ({
  CandidateName: () => <span>已授权候选人</span>,
  ResumeVersionName: () => <span>已授权简历版本</span>,
  UserName: () => <span>记录人</span>,
  TechnicalDetails: () => null,
}));

const instant = '2026-09-12T01:00:00Z';
const value: SubmissionDetails = {
  submission: {
    id: 'submission', applicationId: 'application', candidateId: 'candidate', demandId: 'demand', clientId: 'client', projectId: null,
    positionId: 'position', jdId: 'jd', jdVersionId: 'jd-version', resumeVersionId: 'resume', deliveryStatus: 'SENT',
    processStatus: 'SCREENING', sentAt: instant, withdrawnAt: null, withdrawalReason: null, ownerUserId: 'hr', version: 1,
    createdAt: instant, updatedAt: instant,
  },
  sendSnapshot: {
    id: 'snapshot', submissionId: 'submission', resumeVersionId: 'resume', recipient: '发送时冻结的推荐对象', channel: 'MANUAL_OTHER',
    fileName: '冻结发送简历.pdf', contentSha256: 'a'.repeat(64), sentByUserId: 'hr', sentAt: instant,
  },
  feedback: [{
    id: 'feedback', submissionId: 'submission', result: 'ACCEPTED', reasonCategory: 'SKILLS', nextAction: '继续安排面试',
    note: '已授权的客户反馈内容', sourceChannel: 'PHONE', sourceOccurredAt: instant, recordedByUserId: 'hr', createdAt: instant,
  }],
  riskAcknowledgements: [],
};

afterEach(() => { cleanup(); vi.restoreAllMocks(); });

it('shows a fresh error after dismissing a client-only failure and retrying', async () => {
  const read = vi.spyOn(demandApi, 'client')
    .mockRejectedValueOnce(new Error('首次失败')).mockRejectedValueOnce(new Error('再次失败'));
  vi.spyOn(demandApi, 'position').mockResolvedValue({ id: 'position', name: '可见职位' } as Awaited<ReturnType<typeof demandApi.position>>);
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(<ConfigProvider theme={{ token: { motion: false } }}><App><QueryClientProvider client={client}>
    <SubmissionEvidence value={value} />
  </QueryClientProvider></App></ConfigProvider>);
  await screen.findByText('客户或职位名称加载失败');
  fireEvent.click(screen.getByRole('button', { name: /Close|关闭/ }));
  await waitFor(() => expect(screen.queryByText('客户或职位名称加载失败')).not.toBeInTheDocument());
  fireEvent.click(screen.getByRole('button', { name: /^重\s*试/ }));
  await screen.findByText('客户或职位名称加载失败');
  expect(read).toHaveBeenCalledTimes(2);
  expect(screen.getByText('可见职位')).toBeInTheDocument();
});

function expectIndependentEvidence() {
  expect(screen.getByText('发送时冻结的推荐对象')).toBeInTheDocument();
  expect(screen.getByText('冻结发送简历.pdf')).toBeInTheDocument();
  expect(screen.getByText(/已授权的客户反馈内容/)).toBeInTheDocument();
  expect(screen.getByText('已授权候选人')).toBeInTheDocument();
  expect(screen.getByText('已授权简历版本')).toBeInTheDocument();
}

for (const source of ['client', 'position', 'both'] as const) {
  it.each([401, 403, 503])(`${source} HTTP %s preserves independent submission evidence and recovers through retry`, async status => {
    const clientRead = vi.spyOn(demandApi, 'client').mockResolvedValue({ id: 'client', name: '原客户名称' } as Awaited<ReturnType<typeof demandApi.client>>);
    const positionRead = vi.spyOn(demandApi, 'position').mockResolvedValue({ id: 'position', name: '原职位名称' } as Awaited<ReturnType<typeof demandApi.position>>);
    const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
    render(<ConfigProvider theme={{ token: { motion: false } }}><App><QueryClientProvider client={client}>
      <SubmissionEvidence value={value} />
    </QueryClientProvider></App></ConfigProvider>);
    await screen.findByText('原客户名称');
    await screen.findByText('原职位名称');
    expectIndependentEvidence();
    const error = new ApiProblemError({ status, title: '引用读取失败', detail: '引用访问失败', code: status === 503 ? 'SERVICE_UNAVAILABLE' : 'FORBIDDEN', recoverable: status === 503 });
    if (source !== 'position') clientRead.mockRejectedValue(error);
    if (source !== 'client') positionRead.mockRejectedValue(error);
    await act(async () => { await client.invalidateQueries({ queryKey: ['demand'] }); });
    const retry = await screen.findByRole('button', { name: /^重\s*试/ });
    await waitFor(() => {
      if (status !== 503 && source !== 'position') {
        expect(screen.queryByText('原客户名称')).not.toBeInTheDocument();
        expect(screen.getByText('客户名称暂不可用')).toBeInTheDocument();
      } else expect(screen.getByText('原客户名称')).toBeInTheDocument();
      if (status !== 503 && source !== 'client') {
        expect(screen.queryByText('原职位名称')).not.toBeInTheDocument();
        expect(screen.getByText('职位名称暂不可用')).toBeInTheDocument();
      } else expect(screen.getByText('原职位名称')).toBeInTheDocument();
    });
    expectIndependentEvidence();
    clientRead.mockResolvedValue({ id: 'client', name: '恢复后的客户名称' } as Awaited<ReturnType<typeof demandApi.client>>);
    positionRead.mockResolvedValue({ id: 'position', name: '恢复后的职位名称' } as Awaited<ReturnType<typeof demandApi.position>>);
    fireEvent.click(retry);
    await screen.findByText('恢复后的客户名称');
    await screen.findByText('恢复后的职位名称');
    await waitFor(() => expect(screen.queryByRole('button', { name: /^重\s*试/ })).not.toBeInTheDocument());
    expectIndependentEvidence();
  });
}
