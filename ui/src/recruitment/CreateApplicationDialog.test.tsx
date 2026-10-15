import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CreateApplicationModal } from './CreateApplicationDialog';
import { talentApi, type Candidate } from '../talent/api';
import { demandApi, type JdVersion } from '../demand/api';
import { candidateActionsApi } from './candidateActionsApi';
import { recruitmentApi } from './api';

vi.mock('../auth/session',()=>({useSession:()=>({actor:{userId:'owner'}})}));
vi.mock('../display/BusinessReferences', () => ({ PeopleSelect: () => null, useUserNames: () => ({ people: new Map<string, string>(), loading: false }) }));
vi.mock('../talent/api', () => ({ talentApi: { candidates: vi.fn().mockResolvedValue({items:[],hasMore:true,nextCursor:'next'}), resumeVersions: vi.fn() } }));
vi.mock('../demand/api', () => ({ demandApi: { jds: vi.fn().mockResolvedValue({items:[],hasMore:false,nextCursor:null}), jdVersions: vi.fn() } }));
vi.mock('./candidateActionsApi', () => ({ candidateActionsApi: { readiness: vi.fn() } }));
vi.mock('./api', () => ({ recruitmentApi: { create: vi.fn() } }));


const candidate = (fullName: string, primaryEmail: string): Candidate => ({
  id: 'older-candidate', fullName, primaryEmail, primaryPhone: null, ownerUserId: 'owner', ownerDepartmentId: 'dept', status: 'ACTIVE', version: 1, createdAt: '2026-09-07T00:00:00Z', updatedAt: '2026-09-07T00:00:00Z',
});

describe('application candidate selection', () => {
  beforeEach(() => { cleanup(); vi.clearAllMocks(); });
  it('searches the server so candidates beyond the first loaded page can be selected', async () => {
    vi.mocked(talentApi.candidates).mockImplementation(query => Promise.resolve({ items: query === 'Synthetic' ? [candidate('Synthetic Candidate','synthetic@example.invalid')] : [],hasMore:false,nextCursor:null }));
    render(<ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><CreateApplicationModal open ownerUserId={undefined} onClose={()=>undefined} onSaved={()=>Promise.resolve()}/></QueryClientProvider></App></ConfigProvider>);
    fireEvent.change(await screen.findByRole('combobox',{name:/候选人/}),{target:{value:'Synthetic'}});
    expect(await screen.findByText('Synthetic Candidate · synthetic@example.invalid')).toBeInTheDocument();
  });

  it('loads additional candidate pages through the visible paging control', async () => {
    vi.mocked(talentApi.candidates).mockImplementation((_criteria, cursor) => Promise.resolve({ items: cursor ? [candidate('Older Candidate','older@example.invalid')] : [],hasMore:!cursor,nextCursor:cursor?null:'page-2' }));
    render(<ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><CreateApplicationModal open ownerUserId={undefined} onClose={()=>undefined} onSaved={()=>Promise.resolve()}/></QueryClientProvider></App></ConfigProvider>);
    fireEvent.mouseDown(await screen.findByRole('combobox',{name:/候选人/}));
    fireEvent.click(await screen.findByRole('button',{name:'加载更多候选人'}));
    expect(await screen.findByText('Older Candidate · older@example.invalid')).toBeInTheDocument();
  }, 15_000);

  it('offers visible JDs without requiring a recruitment demand', async () => {
    vi.mocked(talentApi.candidates).mockResolvedValue({items:[],hasMore:false,nextCursor:null});
    vi.mocked(demandApi.jds).mockResolvedValue({items:[
      {id:'formal',demandId:'demand-1',demandCode:'D-01',title:'Formal JD',status:'DRAFT',currentVersionNo:1,contentShared:false,appliedFromJdVersionId:null,clientId:null,clientName:null,ownerUserId:'owner',ownerDepartmentId:'dept',createTime:'2026-09-07T00:00:00Z'},
      {id:'raw',demandId:null,demandCode:null,title:'Unassigned Raw JD',status:'DRAFT',currentVersionNo:1,contentShared:false,appliedFromJdVersionId:null,clientId:null,clientName:null,ownerUserId:'owner',ownerDepartmentId:'dept',createTime:'2026-09-07T00:00:00Z'},
    ],hasMore:false,nextCursor:null});
    render(<ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><CreateApplicationModal open ownerUserId={undefined} onClose={()=>undefined} onSaved={()=>Promise.resolve()}/></QueryClientProvider></App></ConfigProvider>);
    fireEvent.mouseDown(await screen.findByRole('combobox',{name:'搜索 JD 编号、岗位名称、地区或客户'}));
    expect(await screen.findByText(/Formal JD/)).toBeInTheDocument();
    expect(await screen.findByText(/Unassigned Raw JD/)).toBeInTheDocument();
  }, 15_000);

  it('按当前岗位精确版本显示甲方，并在失败后保留草稿与幂等键、不提交预定轮次', async () => {
    vi.mocked(talentApi.candidates).mockResolvedValue({items:[candidate('测试候选人','candidate@example.invalid')],hasMore:false,nextCursor:null});
    vi.mocked(talentApi.resumeVersions).mockResolvedValue({items:[{id:'resume-current',resumeId:'resume',intakeId:'intake',versionNo:2,originalFileName:'candidate.pdf',mediaType:'application/pdf',sizeBytes:1,contentSha256:'hash',status:'CONFIRMED',createdAt:'2026-09-09T00:00:00Z'}],hasMore:false,nextCursor:null});
    vi.mocked(demandApi.jds).mockResolvedValue({items:[{id:'jd-external',demandId:'demand',demandCode:'D',title:'外部岗位',status:'DRAFT',currentVersionNo:2,contentShared:false,appliedFromJdVersionId:null,clientId:'list-company',clientName:'岗位列表公司',ownerUserId:'owner',ownerDepartmentId:'dept',createTime:'2026-09-09T00:00:00Z'}],hasMore:false,nextCursor:null});
    const version = (id:string,versionNo:number):JdVersion => ({id,jdId:'jd-external',versionNo,title:'外部岗位',rawText:'岗位要求',status:'PUBLISHED',sourceType:'MANUAL',createId:'owner',createTime:'2026-09-09T00:00:00Z'});
    vi.mocked(demandApi.jdVersions).mockResolvedValue({items:[version('old-jv',1),version('current-jv',2)],hasMore:false,nextCursor:null});
    vi.mocked(candidateActionsApi.readiness).mockResolvedValue({headcount:2,occupiedHeadcount:1,remainingHeadcount:1,jdId:'jd-external',jdVersionId:'current-jv',jdTitle:'外部岗位',demandId:'demand',recruitmentType:'EXTERNAL',clientId:'bound-company',clientName:'当前版本甲方',canProcess:true,activeWorkflowId:'workflow',activeWorkflow:null,draftWorkflows:[],standardSteps:[]});
    vi.mocked(recruitmentApi.create).mockRejectedValue(new Error('连接中断，请重试'));
    render(<ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><CreateApplicationModal open ownerUserId={undefined} onClose={()=>undefined} onSaved={()=>Promise.resolve()}/></QueryClientProvider></App></ConfigProvider>);
    fireEvent.mouseDown(await screen.findByRole('combobox',{name:/候选人/}));
    fireEvent.click(await screen.findByText('测试候选人 · candidate@example.invalid'));
    fireEvent.mouseDown(screen.getByRole('combobox',{name:'搜索 JD 编号、岗位名称、地区或客户'}));
    fireEvent.click(await screen.findByTitle('外部岗位 · 岗位列表公司 · V2'));
    expect(await screen.findByText('甲方公司：当前版本甲方')).toBeInTheDocument();
    expect(candidateActionsApi.readiness).toHaveBeenCalledWith('current-jv');
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
    fireEvent.change(screen.getByRole('textbox',{name:'人工推荐依据'}),{target:{value:'已核对候选人意向'}});
    fireEvent.click(screen.getByRole('button',{name:'确认推荐'}));
    const errorNotice = await screen.findByText('连接中断，请重试');
    expect(errorNotice.closest('[role="alert"]')).toBeInTheDocument();
    expect(screen.getByRole('textbox',{name:'人工推荐依据'})).toHaveValue('已核对候选人意向');
    fireEvent.click(screen.getByRole('button',{name:'确认推荐'}));
    await waitFor(()=>expect(recruitmentApi.create).toHaveBeenCalledTimes(2));
    const calls=vi.mocked(recruitmentApi.create).mock.calls;
    expect(calls[0]?.[0]).toEqual({candidateId:'older-candidate',jdVersionId:'current-jv',resumeVersionId:'resume-current',ownerUserId:undefined,reason:'已核对候选人意向'});
    expect(calls[1]?.[1]).toEqual(calls[0]?.[1]);
  }, 20_000);
});

