import { QueryClient, QueryClientProvider, useQuery } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { talentApi } from '../talent/api';
import { ScreeningReturnDialog } from './ScreeningReturnDialog';
import { ApiProblemError } from '../api/problem';

afterEach(()=>{cleanup();vi.restoreAllMocks();});

function AdmissionObserver({load}:{load:()=>Promise<string[]>}){
  useQuery({queryKey:['matching','screening-candidates','admitted'],queryFn:load,staleTime:Infinity});
  return null;
}

function mount(load?:()=>Promise<string[]>){
  const client=new QueryClient({defaultOptions:{queries:{retry:false}}});
  const onClose=vi.fn(),onCompleted=vi.fn();
  for(const key of ['matching','talent','talent-search'])client.setQueryData([key,'list'],['retained']);
  client.setQueryData(['matching','screening-candidates','admitted'],['old']);
  render(<ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={client}>
    {load?<AdmissionObserver load={load}/>:null}
    <ScreeningReturnDialog targets={[{candidateId:'candidate',admissionId:'admission',expectedVersion:'4'}]} onClose={onClose} onCompleted={onCompleted}/>
  </QueryClientProvider></App></ConfigProvider>);
  return {client,onClose,onCompleted};
}

it('does not remove anything when the return dialog is cancelled',()=>{
  const submit=vi.spyOn(talentApi,'returnScreening');
  const {onClose,onCompleted}=mount();
  fireEvent.click(screen.getByRole('button',{name:/取\s*消/}));
  expect(onClose).toHaveBeenCalledOnce();
  expect(onCompleted).not.toHaveBeenCalled();
  expect(submit).not.toHaveBeenCalled();
});

it.each(['SCREENING_ADMISSION_VERSION_CONFLICT','SCREENING_RECRUITMENT_ALREADY_ENTERED'])('refreshes admission state after %s without repeating the stale return or clearing selection',async code=>{
  const submit=vi.spyOn(talentApi,'returnScreening').mockRejectedValue(new ApiProblemError({status:409,code,title:'状态冲突',detail:'准入状态已变化',recoverable:false}));
  const load=vi.fn().mockRejectedValueOnce(new Error('筛选状态读取失败')).mockResolvedValue(['fresh']);
  const {client,onClose,onCompleted}=mount(load);
  fireEvent.click(screen.getByRole('button',{name:'确认退回'}));
  const refresh=await screen.findByRole('button',{name:'刷新筛选状态'});
  fireEvent.click(refresh);
  await screen.findByText('筛选状态读取失败');
  expect(onClose).not.toHaveBeenCalled();
  await waitFor(()=>expect(refresh).toBeEnabled());
  fireEvent.click(refresh);
  await waitFor(()=>expect(onClose).toHaveBeenCalledOnce());
  expect(load).toHaveBeenCalledTimes(2);
  expect(client.getQueryData(['matching','screening-candidates','admitted'])).toEqual(['fresh']);
  expect(submit).toHaveBeenCalledTimes(1);
  expect(onCompleted).not.toHaveBeenCalled();
});

it('keeps the version and idempotency key on retry and refreshes all routing lists only after success',async()=>{
  const submit=vi.spyOn(talentApi,'returnScreening').mockRejectedValueOnce(new Error('网络暂不可用'))
    .mockResolvedValueOnce({requestedCount:1,returnedCount:1,alreadyReturnedCount:0});
  const {client,onClose,onCompleted}=mount();
  fireEvent.click(screen.getByRole('button',{name:'确认退回'}));
  expect(await screen.findByText('网络暂不可用')).toBeInTheDocument();
  expect(onCompleted).not.toHaveBeenCalled();
  expect(onClose).not.toHaveBeenCalled();
  expect(client.getQueryState(['talent-search','list'])?.isInvalidated).toBe(false);
  fireEvent.click(screen.getByRole('button',{name:'重试退回'}));
  await waitFor(()=>expect(onCompleted).toHaveBeenCalledWith(['candidate']));
  expect(submit.mock.calls[0]).toEqual([[{admissionId:'admission',expectedVersion:'4'}],expect.any(String)]);
  expect(submit.mock.calls[1]).toEqual(submit.mock.calls[0]);
  for(const key of ['matching','talent','talent-search'])expect(client.getQueryState([key,'list'])?.isInvalidated).toBe(true);
  expect(onClose).toHaveBeenCalledOnce();
});

it('keeps the dialog locked until a conflict refresh has actually completed',async()=>{
  vi.spyOn(talentApi,'returnScreening').mockRejectedValue(new ApiProblemError({status:409,code:'SCREENING_ADMISSION_VERSION_CONFLICT',title:'状态冲突',detail:'状态已变化',recoverable:false}));
  let resolve!:(value:string[])=>void;
  const load=vi.fn(()=>new Promise<string[]>(done=>{resolve=done;}));
  const {onClose,onCompleted}=mount(load);
  fireEvent.click(screen.getByRole('button',{name:'确认退回'}));
  const refresh=await screen.findByRole('button',{name:'刷新筛选状态'});
  fireEvent.click(refresh);
  await waitFor(()=>expect(load).toHaveBeenCalledOnce());
  expect(refresh).toBeDisabled();
  const cancel=screen.getByRole('button',{name:/取\s*消/});
  expect(cancel).toBeDisabled();
  fireEvent.click(cancel);
  fireEvent.click(refresh);
  expect(onClose).not.toHaveBeenCalled();
  resolve(['fresh']);
  await waitFor(()=>expect(onClose).toHaveBeenCalledOnce());
  expect(load).toHaveBeenCalledOnce();
  expect(onCompleted).not.toHaveBeenCalled();
});

it.each([[409,'IDEMPOTENCY_KEY_REUSED'],[503,'SCREENING_ADMISSION_VERSION_CONFLICT']] as const)('does not reinterpret %s %s as a confirmed admission state conflict',async(status,code)=>{
  const submit=vi.spyOn(talentApi,'returnScreening').mockRejectedValueOnce(new ApiProblemError({status,code,title:'请求失败',detail:'结果未确认',recoverable:false}))
    .mockResolvedValueOnce({requestedCount:1,returnedCount:1,alreadyReturnedCount:0});
  const {onCompleted}=mount();
  fireEvent.click(screen.getByRole('button',{name:'确认退回'}));
  const retry=await screen.findByRole('button',{name:'重试退回'});
  expect(screen.queryByRole('button',{name:'刷新筛选状态'})).not.toBeInTheDocument();
  await waitFor(()=>expect(retry).toBeEnabled());
  fireEvent.click(retry);
  await waitFor(()=>expect(onCompleted).toHaveBeenCalledOnce());
  expect(submit.mock.calls[1]).toEqual(submit.mock.calls[0]);
});
