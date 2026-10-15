import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { ConfigProvider } from 'antd';
import { afterEach, expect, it, vi } from 'vitest';
import { SharedApplicationHistory } from './SharedApplicationHistory';
import { recruitmentApi, type ApplicationDetails } from './api';

vi.mock('./api',()=>({recruitmentApi:{intentions:vi.fn(),followUps:vi.fn()}}));
vi.mock('../display/BusinessReferences',()=>({UserName:({id}:{id:string})=><span>{id}</span>}));
afterEach(()=>{cleanup();vi.clearAllMocks();});
const details={application:{id:'application',recruitmentType:'INTERNAL',onboardingStatus:'RETURNED'},workflowSteps:[{id:'step',stepCode:'CANDIDATE_COMMUNICATION',status:'PASSED',sequenceNo:1,resultReason:'历史步骤备注'}],decisions:[{id:'decision',decision:'REJECT',reason:'招聘决定依据'}],offerStatusRecords:[{id:'offer',status:'TERMINATED',reason:'Offer 终止依据'}]} as unknown as ApplicationDetails;
it('移除意向薪酬展示与请求，保留步骤历史及失败重试',async()=>{
  vi.mocked(recruitmentApi.followUps).mockRejectedValueOnce(new Error('暂不可用')).mockResolvedValue([{id:'follow',workflowStepId:'step',channel:'PHONE',summary:'另一归属人的跟进内容',nextPlan:'下周回访',contactedAt:'2026-09-01T00:00:00Z',recordedByUserId:'other-hr'}]);
  render(<ConfigProvider theme={{token:{motion:false}}}><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><SharedApplicationHistory value={details}/></QueryClientProvider></ConfigProvider>);
  fireEvent.click(await screen.findByRole('button',{name:'重试步骤跟进'}));
  expect(await screen.findByText('另一归属人的跟进内容')).toBeInTheDocument();
  expect(screen.getByText('下周回访').closest('p')).toHaveTextContent('下一步：下周回访');
  expect(screen.getByText('招聘决定依据')).toBeInTheDocument();
  expect(screen.getByText('Offer 终止依据')).toBeInTheDocument();
  expect(screen.getByText('历史步骤备注')).toBeInTheDocument();
  expect(screen.queryByText('意向与薪酬记录')).not.toBeInTheDocument();
  expect(screen.queryByText('暂无意向与薪酬记录')).not.toBeInTheDocument();
  expect(recruitmentApi.intentions).not.toHaveBeenCalled();
  expect(recruitmentApi.followUps).toHaveBeenCalledWith('application','step');
});

it('按实际流程时间倒序展示步骤、跟进及跨类型决定历史，无时间步骤排最后',async()=>{
  const value = { application: { id: 'application', recruitmentType: 'INTERNAL' }, workflowSteps: [
    { id: 'undated', stepCode: 'CANDIDATE_COMMUNICATION', sequenceNo: 1, status: 'NOT_SELECTED', startedAt: null, completedAt: null, resultReason: '未发生步骤' },
    { id: 'earlier', stepCode: 'INTERVIEW_ROUND_1', sequenceNo: 2, status: 'PASSED', startedAt: '2026-09-22T16:30:00+08:00', completedAt: '2026-09-22T16:41:00+08:00', resultReason: '较早完成步骤' },
    { id: 'later', stepCode: 'DECISION', sequenceNo: 3, status: 'FAILED', startedAt: '2026-09-22T16:41:00+08:00', completedAt: '2026-09-22T16:42:00+08:00', resultReason: '较晚完成步骤' },
    { id: 'active', stepCode: 'OFFER', sequenceNo: 4, status: 'IN_PROGRESS', startedAt: '2026-09-22T16:43:00+08:00', completedAt: null, resultReason: '进行中步骤' },
  ], decisions: [
    { id: 'decision-old', decision: 'PASS', decidedAt: '2026-09-22T16:40:00+08:00', reason: '较早决定' },
    { id: 'decision-new', decision: 'REJECT', decidedAt: '2026-09-22T16:44:00+08:00', reason: '最新决定' },
  ], offerStatusRecords: [{ id: 'offer-middle', status: 'ISSUED', occurredAt: '2026-09-22T16:42:00+08:00', reason: '中间 Offer' }] } as unknown as ApplicationDetails;
  vi.mocked(recruitmentApi.followUps).mockImplementation((_applicationId, stepId) => Promise.resolve(stepId === 'later' ? [
    { id: 'follow-old', workflowStepId: stepId, channel: 'PHONE', summary: '较早跟进', nextPlan: '', contactedAt: '2026-09-22T16:41:00+08:00', recordedByUserId: 'hr' },
    { id: 'follow-new', workflowStepId: stepId, channel: 'PHONE', summary: '较晚跟进', nextPlan: '', contactedAt: '2026-09-22T16:42:00+08:00', recordedByUserId: 'hr' },
  ] : []));
  render(<ConfigProvider theme={{token:{motion:false}}}><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><SharedApplicationHistory value={value}/></QueryClientProvider></ConfigProvider>);
  await screen.findByText('较晚跟进');
  const precedes = (first: string, second: string) => expect(screen.getByText(first).compareDocumentPosition(screen.getByText(second)) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  precedes('进行中步骤', '较晚完成步骤');
  precedes('较晚完成步骤', '较早完成步骤');
  precedes('较早完成步骤', '未发生步骤');
  precedes('较晚跟进', '较早跟进');
  precedes('最新决定', '中间 Offer');
  precedes('中间 Offer', '较早决定');
  expect(recruitmentApi.followUps).toHaveBeenCalledWith('application','later');
});
