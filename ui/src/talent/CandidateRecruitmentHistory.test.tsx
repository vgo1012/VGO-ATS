import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { CandidateRecruitmentHistoryPanel } from './CandidateRecruitmentHistory';
import { talentApi, type CandidateRecruitmentHistory } from './api';

vi.mock('./api',()=>({talentApi:{recruitmentHistory:vi.fn()}}));
vi.mock('../display/BusinessReferences',()=>({UserName:({id}:{id:string|null})=><span>{id??'未分配'}</span>,JobName:()=> <span>测试岗位</span>}));
vi.mock('../recruitment/LazyApplicationDetailsDialog',()=>({LazyApplicationDetailsDialog:()=>null}));
afterEach(()=>{cleanup();vi.clearAllMocks();});

type OwnershipEvent=CandidateRecruitmentHistory['items'][number]['ownershipEvents'][number];
const initial:OwnershipEvent={actionType:'ASSIGN',fromUserId:null,toUserId:'test-owner-a',reason:null,operatedByUserId:'test-owner-a',occurredAt:'2026-09-22T08:39:00Z'};
async function mount(events:OwnershipEvent[]){
  vi.mocked(talentApi.recruitmentHistory).mockResolvedValue({items:[{
    applicationId:'app',jdId:'jd',jdVersionId:'jd-v1',resumeVersionId:'resume',recruitmentType:'INTERNAL',currentStage:'TERMINATED',outcome:'REJECT',
    originalOwnerUserId:'test-owner-a',currentOwnerUserId:'test-owner-b',createTime:'2026-09-22T08:39:00Z',terminalAt:'2026-09-22T08:42:00Z',stageEvents:[],ownershipEvents:events,interviews:[],
  }]});
  render(<QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><CandidateRecruitmentHistoryPanel candidateId="candidate" active/></QueryClientProvider>);
  fireEvent.click(await screen.findByText('测试岗位'));
}

it.each([{events:[]},{events:[initial]}])('没有实际转交时不展示待确认或未分配记录：$events',async({events})=>{
  await mount(events);
  expect(screen.queryByText(/待确认|未分配|操作人/)).not.toBeInTheDocument();
  expect(screen.getByText('test-owner-a')).toBeInTheDocument();
  expect(screen.getByRole('button',{name:'查看该流程详情'})).toBeInTheDocument();
});

it.each([['TRANSFER','转交'],['HANDOVER','交接'],['ASSIGN','归属更新'],['FUTURE_ACTION','归属变更']])('保留实际归属变更并显示正确文案：%s',async(actionType,label)=>{
  await mount([initial,{...initial,actionType,fromUserId:'test-owner-a',toUserId:'test-owner-b',occurredAt:'2026-09-22T08:40:00Z'}]);
  const record=screen.getByText(new RegExp(` · ${label} · `));
  expect(record).toHaveTextContent('test-owner-a → test-owner-b · 操作人 test-owner-a');
  expect(screen.queryByText(/待确认|未分配/)).not.toBeInTheDocument();
});
