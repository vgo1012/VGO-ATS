import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, expect, it, vi } from 'vitest';
import { searchApi } from '../search/api';
import { talentApi, type Candidate } from '../talent/api';
import { TalentRoutePage } from './TalentRoutePage';

vi.mock('../auth/session',()=>({useSession:()=>({actor:{userId:'reader',superAdmin:false,permissions:['talent.read']}})}));
vi.mock('../talent/CandidateDetails',()=>({CandidateDialog:({candidateId,open}:{candidateId?:string;open:boolean})=>open?<div>候选人详情 {candidateId}</div>:null}));
vi.mock('../talent/UploadDrawer',()=>({UploadDrawer:()=>null}));
vi.mock('../talent/TalentFilters',()=>({TalentFilters:()=>null}));
vi.mock('../talent/CandidateLibrary',()=>({CandidateLibrary:({items,onNext,hasMore}:{items:Candidate[];onNext:()=>void;hasMore:boolean})=><div>{items.map(person=><span key={person.id}>{person.fullName}</span>)}<button disabled={!hasMore} onClick={onNext}>下一页</button></div>}));

afterEach(()=>{cleanup();vi.restoreAllMocks();});
function renderPage(url:string){
  function Location(){return <output aria-label="当前地址">{useLocation().search}</output>;}
  return render(<MemoryRouter initialEntries={[url]}><ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><Routes><Route path="/talents" element={<><TalentRoutePage/><Location/></>}/></Routes></QueryClientProvider></App></ConfigProvider></MemoryRouter>);
}

it('keeps personal candidates and cursor paging together without filtering their lifecycle phase',async()=>{
  const candidates=vi.spyOn(talentApi,'candidates').mockResolvedValueOnce({items:[{id:'legacy',fullName:'旧阶段候选人'}] as Candidate[],hasMore:true,nextCursor:'second'}).mockResolvedValueOnce({items:[],hasMore:false,nextCursor:null});
  const count=vi.spyOn(talentApi,'countCandidates').mockResolvedValue(1);
  const search=vi.spyOn(searchApi,'personalCandidates');
  renderPage('/talents');
  expect(await screen.findByText('旧阶段候选人')).toBeVisible();
  expect(screen.queryByRole('heading',{name:'待筛选'})).not.toBeInTheDocument();
  expect(screen.queryByRole('tab',{name:'待处置'})).not.toBeInTheDocument();
  expect(candidates).toHaveBeenCalledWith('',undefined,20,true);
  expect(count).toHaveBeenCalledWith('',true,undefined,expect.any(AbortSignal));
  expect(search).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button',{name:'下一页'}));
  await waitFor(()=>expect(candidates).toHaveBeenCalledWith('','second',20,true));
});

it('recovers retired task URLs and retains personal filters, candidate and intake context',async()=>{
  const candidates=vi.spyOn(searchApi,'personalCandidates').mockResolvedValue({items:[],hasMore:false,nextCursor:null});
  const count=vi.spyOn(searchApi,'count').mockResolvedValue(0);
  renderPage('/talents?view=todo&todo_kind=recruitment&todo_q=旧任务&todo_conditions=旧条件&pool_q=保留&pool_education=本科&pool_jd=jd-a&pool_conditions=证书&candidate=legacy&intake=1');
  await waitFor(()=>expect(screen.getByLabelText('当前地址')).toHaveTextContent('view=pool'));
  const params=new URLSearchParams(screen.getByLabelText('当前地址').textContent??'');
  expect([...params.keys()].some(key=>key.startsWith('todo_'))).toBe(false);
  expect(params.get('pool_q')).toBe('保留');
  expect(params.get('pool_education')).toBe('本科');
  expect(params.get('pool_jd')).toBe('jd-a');
  expect(params.getAll('pool_conditions')).toEqual(['证书']);
  expect(params.get('candidate')).toBe('legacy');
  expect(params.get('intake')).toBe('1');
  expect(screen.getByText('候选人详情 legacy')).toBeVisible();
  await waitFor(()=>expect(candidates).toHaveBeenCalled());
  expect(candidates.mock.calls[0]?.[0]).toEqual(expect.objectContaining({query:'保留',degrees:['本科'],jdId:'jd-a',conditions:['证书']}));
  expect(candidates.mock.calls.every(([criteria])=>!criteria.poolStage)).toBe(true);
  expect(count).toHaveBeenCalled();
  expect(count.mock.calls.every(([criteria,scope,ids])=>!criteria.poolStage&&scope==='PERSONAL_POOL'&&ids?.length===0)).toBe(true);
});
