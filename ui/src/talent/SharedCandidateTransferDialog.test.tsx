import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { demandApi } from '../demand/api';
import { taskApi } from '../tasks/task';
import { talentApi } from './api';
import { SharedCandidateTransferDialog } from './SharedCandidateTransferDialog';

vi.mock('../demand/JdSelector',()=>({JdSelector:({onChange}:{onChange:(value:unknown)=>void})=><button onClick={()=>onChange({id:'jd',currentVersionNo:2,title:'岗位',status:'ACTIVE'})}>选择测试 JD</button>}));
vi.mock('../demand/api',()=>({demandApi:{jdVersions:vi.fn()}}));
vi.mock('../tasks/task',()=>({taskApi:{get:vi.fn()}}));
vi.mock('./api',()=>({talentApi:{scoreSharedReclaims:vi.fn(),reclaimShared:vi.fn()}}));

const targets=[{candidateId:'candidate',resumeVersionId:'resume',candidateVersion:7}];

function mount(onCompleted=vi.fn(),onClose=vi.fn()){
  const client=new QueryClient({defaultOptions:{queries:{retry:false},mutations:{retry:false}}});
  render(<ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={client}><SharedCandidateTransferDialog open candidates={targets} onCompleted={onCompleted} onClose={onClose}/></QueryClientProvider></App></ConfigProvider>);
  return {onCompleted,onClose};
}

beforeEach(()=>{
  vi.mocked(demandApi.jdVersions).mockResolvedValue({items:[{id:'jd-version',versionNo:2,status:'PUBLISHED'}],hasMore:false,nextCursor:null} as Awaited<ReturnType<typeof demandApi.jdVersions>>);
  vi.mocked(talentApi.scoreSharedReclaims).mockResolvedValue({operationId:'operation',requestedCount:1,items:[{scoreBindingId:'binding',candidateId:'candidate',resumeVersionId:'resume',jdId:'jd',jdVersionId:'jd-version',defaultMatchRunId:'run',taskId:'task'}]});
  vi.mocked(talentApi.reclaimShared).mockResolvedValue({reclaimedCount:1,candidateIds:['candidate']});
});
afterEach(()=>{cleanup();vi.clearAllMocks();});

it('transfers only after the AI score task succeeds',async()=>{
  vi.mocked(taskApi.get).mockResolvedValue({taskId:'task',status:'SUCCEEDED'} as Awaited<ReturnType<typeof taskApi.get>>);
  const callbacks=mount();
  expect(await screen.findByText(/历史 JD 和评分仅供查看；本次需重新选择 JD 并完成新的公司默认 AI 评分/)).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button',{name:'选择测试 JD'}));
  fireEvent.click(screen.getByRole('button',{name:'评分并转入'}));

  await waitFor(()=>expect(talentApi.reclaimShared).toHaveBeenCalledWith([{
    candidateId:'candidate',resumeVersionId:'resume',candidateVersion:7,scoreBindingId:'binding',
  }]));
  expect(callbacks.onCompleted).toHaveBeenCalled();
  expect(callbacks.onClose).toHaveBeenCalled();
});

it('keeps the resume shared when scoring fails',async()=>{
  vi.mocked(taskApi.get).mockResolvedValue({taskId:'task',status:'FAILED',errorCode:'MODEL_UNAVAILABLE'} as Awaited<ReturnType<typeof taskApi.get>>);
  mount();
  fireEvent.click(screen.getByRole('button',{name:'选择测试 JD'}));
  fireEvent.click(screen.getByRole('button',{name:'评分并转入'}));

  expect(await screen.findByText(/AI 评分失败.*简历仍保留在共享库/)).toBeInTheDocument();
  expect(talentApi.reclaimShared).not.toHaveBeenCalled();
});
