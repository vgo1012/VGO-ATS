import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App } from 'antd';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { CompanyBlacklistPanel } from './CompanyBlacklistPanel';
import { riskApi, type BlacklistRecord } from './riskApi';

const session=vi.hoisted(()=>({actor:{superAdmin:false,permissions:['risk.read','risk.manage']}}));
vi.mock('../auth/session',()=>({useSession:()=>session}));
vi.mock('../display/BusinessReferences',()=>({UserName:()=> <span>测试HR</span>,JobName:()=> <span>测试岗位</span>,TechnicalDetails:()=>null}));
vi.mock('./riskApi',()=>({riskApi:{history:vi.fn(),affected:vi.fn(),correct:vi.fn(),disable:vi.fn()}}));
vi.mock('../recruitment/ApplicationDetailsDialog',()=>({ApplicationDetailsDialog:({applicationId,onClose}:{applicationId:string;onClose:()=>void})=><div role="dialog" aria-label="受影响招聘活动">{applicationId}<button onClick={onClose}>关闭详情</button></div>}));
const record:BlacklistRecord={id:'record-1',candidateId:'candidate',recordType:'ACTIVATION',status:'ACTIVE',reasonCode:'QA',reasonDetail:'合成风险原因',sourceChannel:'MANUAL',evidenceReference:'合成证据',correctsRecordId:null,recordedByUserId:'hr',occurredAt:'2026-09-07T00:00:00Z',createTime:'2026-09-07T00:00:00Z'};
const page={items:[record],hasMore:false,nextCursor:null};
afterEach(cleanup);
function mount(sharedLibrary=false){return render(<MemoryRouter><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false},mutations:{retry:false}}})}><App><CompanyBlacklistPanel candidateId="candidate" sharedLibrary={sharedLibrary}/></App></QueryClientProvider></MemoryRouter>);}
beforeEach(()=>{vi.clearAllMocks();session.actor.permissions=['risk.read','risk.manage'];vi.mocked(riskApi.history).mockResolvedValue({active:true,current:record,records:page});vi.mocked(riskApi.affected).mockResolvedValue({items:[{resourceType:'APPLICATION',resourceId:'application',applicationId:'application',clientId:null,jdId:'jd',status:'OFFER',ownerUserId:'hr',updateTime:record.createTime}],nextCursor:null,hasMore:false});});

it('shows current restriction and opens the exact affected activity in place without making decisions',async()=>{
  mount();expect(await screen.findByText('黑名单标签生效中，简历在流程结束后进入共享简历库')).toBeVisible();
  fireEvent.click(await screen.findByRole('button',{name:'查看招聘活动'}));
  expect(await screen.findByRole('dialog',{name:'受影响招聘活动'})).toHaveTextContent('application');
  expect(screen.queryByRole('link',{name:'查看招聘活动'})).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button',{name:'关闭详情'}));
  expect(screen.queryByRole('dialog',{name:'受影响招聘活动'})).not.toBeInTheDocument();
  expect(riskApi.correct).not.toHaveBeenCalled();expect(riskApi.disable).not.toHaveBeenCalled();
});
it('requires a reason before disabling and retains history after success',async()=>{
  mount();fireEvent.click(await screen.findByRole('button',{name:'停用黑名单'}));
  fireEvent.click(screen.getByRole('button',{name:'OK'}));await waitFor(()=>expect(riskApi.disable).not.toHaveBeenCalled());
  fireEvent.change(screen.getByLabelText('停用原因'),{target:{value:'合成风险解除，保留原始事实'}});
  vi.mocked(riskApi.history).mockResolvedValue({active:false,current:{...record,id:'record-2',status:'DISABLED',recordType:'DISABLE'},records:{...page,items:[{...record,id:'record-2',status:'DISABLED',recordType:'DISABLE'},record]}});
  vi.mocked(riskApi.disable).mockResolvedValue({});
  fireEvent.click(screen.getByRole('button',{name:'OK'}));
  await waitFor(()=>expect(riskApi.disable).toHaveBeenCalledWith('record-1','合成风险解除，保留原始事实'));
  expect(await screen.findByText('公司黑名单已停用')).toBeVisible();expect(screen.queryByRole('button',{name:'更正黑名单记录'})).not.toBeInTheDocument();
  expect(screen.getByText('查看不可变更历史（已加载 2 条）')).toBeInTheDocument();
});
it('does not read or render risk data for an interviewer without risk permission',()=>{
  session.actor.permissions=[];mount();expect(riskApi.history).not.toHaveBeenCalled();expect(screen.queryByText('公司级黑名单与处置历史')).not.toBeInTheDocument();
});
it('reads all shared candidate risk history without granting risk editing',async()=>{
  session.actor.permissions=[];mount(true);
  expect(await screen.findByText('查看不可变更历史（已加载 1 条）')).toBeInTheDocument();
  expect(riskApi.history).toHaveBeenCalledWith('candidate',undefined);
  expect(screen.queryByRole('button',{name:'更正黑名单记录'})).not.toBeInTheDocument();
  expect(screen.queryByRole('button',{name:'停用黑名单'})).not.toBeInTheDocument();
});
