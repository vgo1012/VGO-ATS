import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ConfigProvider } from 'antd';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { useState } from 'react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { talentApi } from '../talent/api';
import { matchingApi, type ScreeningSelectionCandidate, type ScreeningSelectionIdentity } from './api';
import { CandidateSelection } from './CandidateSelection';

const row=(resumeVersionId:string,candidateName:string):ScreeningSelectionCandidate=>({candidateId:'same-candidate',candidateName,resumeVersionId,resumeVersionNo:resumeVersionId==='resume-a'?1:2,originalFileName:'resume.pdf',jdVersionId:'jd-version',defaultMatchRunId:'company-run',admissionId:resumeVersionId,professionalScore:80,referenceScore:80});
afterEach(()=>{cleanup();vi.restoreAllMocks();});
beforeEach(()=>{vi.spyOn(matchingApi,'countScreeningCandidates').mockResolvedValue(21);});
function Harness(){
  const [items,setItems]=useState<ScreeningSelectionIdentity[]>([]);
  return <CandidateSelection jdVersionId="jd-version" initialCandidateIds={[]} selectedItems={items} onChange={setItems} disabled={false} onBusyChange={()=>undefined}/>;
}
function mount(child=<Harness/>){return render(<ConfigProvider theme={{token:{motion:false}}}><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}>{child}</QueryClientProvider></ConfigProvider>);}

it('已准入简历即使离开我的简历仍可跨页选择，同一候选人的两个版本独立保留',async()=>{
  const personalPool=vi.spyOn(talentApi,'candidates').mockResolvedValue({items:[],hasMore:false,nextCursor:null});
  const load=vi.spyOn(matchingApi,'screeningCandidates').mockImplementation((_,_query,cursor)=>Promise.resolve({items:[row(cursor?'resume-b':'resume-a',cursor?'第二页成员':'第一页成员')],hasMore:!cursor,nextCursor:cursor?null:'page2'}));
  mount();
  fireEvent.click(await screen.findByRole('button',{name:'加入本次匹配'}));
  expect(screen.getByRole('status')).toHaveTextContent('已选 1 份简历');
  fireEvent.click(screen.getByRole('button',{name:'下一页候选人'}));
  await screen.findByText('第二页成员');
  expect(screen.getByText('条/共21条记录')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button',{name:'加入本次匹配'}));
  expect(screen.getByRole('status')).toHaveTextContent('已选 2 份简历');
  fireEvent.click(screen.getByRole('button',{name:'清空候选人选择'}));
  fireEvent.click(screen.getByRole('button',{name:'全选符合条件的候选人'}));
  await waitFor(()=>expect(screen.getByRole('status')).toHaveTextContent('已选 2 份简历'));
  expect(load).toHaveBeenCalledWith('jd-version','',undefined,100);
  expect(personalPool).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button',{name:'上一页候选人'}));
  expect(await screen.findByRole('button',{name:'移除本次匹配'})).toBeInTheDocument();
},12000);

it('达到1000份上限不能追加其他版本',async()=>{
  vi.spyOn(matchingApi,'screeningCandidates').mockResolvedValue({items:[row('new','新增简历')],hasMore:false,nextCursor:null});
  const onChange=vi.fn();
  mount(<CandidateSelection jdVersionId="jd-version" initialCandidateIds={[]} selectedItems={Array.from({length:1000},(_,i)=>({candidateId:`c-${i}`,resumeVersionId:`r-${i}`}))} onChange={onChange} disabled={false} onBusyChange={()=>undefined}/>);
  await screen.findByText('新增简历');
  expect(screen.getByRole('button',{name:'加入本次匹配'})).toBeDisabled();
  expect(onChange).not.toHaveBeenCalled();
});

it('没有当前岗位准入结果时显示准确空态，不退回个人池冒用其他候选人',async()=>{
  vi.spyOn(matchingApi,'screeningCandidates').mockResolvedValue({items:[],hasMore:false,nextCursor:null});
  const personal=vi.spyOn(talentApi,'candidates');
  mount();
  expect(await screen.findByText('当前岗位尚无已加入智能筛选的简历，请先在“我的简历”中加入')).toBeInTheDocument();
  expect(screen.queryByRole('button',{name:'加入本次匹配'})).not.toBeInTheDocument();
  expect(personal).not.toHaveBeenCalled();
});

it('查询失败不会显示成未加入，重试能恢复可选结果',async()=>{
  const load=vi.spyOn(matchingApi,'screeningCandidates').mockRejectedValueOnce(new Error('offline')).mockResolvedValue({items:[row('resume-a','恢复成员')],hasMore:false,nextCursor:null});
  mount();
  fireEvent.click(await screen.findByRole('button',{name:'重试候选人'}));
  expect(await screen.findByText('恢复成员')).toBeInTheDocument();
  expect(screen.getByRole('button',{name:'加入本次匹配'})).toBeEnabled();
  expect(load).toHaveBeenCalledTimes(2);
});
