import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ConfigProvider } from 'antd';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { demandApi, type JdSummary } from './api';
import { JdSelector } from './JdSelector';

vi.mock('../auth/session',()=>({useSession:()=>({actor:{userId:'hr'}})}));
vi.mock('../display/BusinessReferences', () => ({ useUserNames: () => ({ people: new Map([['hr', '招聘专员甲'],['other','招聘专员乙']]), loading: false }) }));

afterEach(()=>{cleanup();vi.restoreAllMocks();});
const job=(id:string):JdSummary=>({id,code:`JD-${id}`,title:`岗位${id}`,clientName:'测试客户',clientId:'client',workRegionName:'杭州',currentVersionNo:2,contentShared:false,appliedFromJdVersionId:null,status:'ACTIVE',ownerUserId:'hr',ownerDepartmentId:'department',createTime:'2026-09-11T00:00:00Z'});
const label=(id:string)=>`岗位${id} · JD-${id} · 测试客户 · 杭州 · V2`;
function setup(value='b'){
  vi.spyOn(demandApi,'jds').mockResolvedValue({items:[job('a')],hasMore:true,nextCursor:'next'});
  const onChange=vi.fn();
  const client=new QueryClient({defaultOptions:{queries:{retry:false}}});
  const view=(id:string)=><ConfigProvider theme={{token:{motion:false}}}><QueryClientProvider client={client}><JdSelector value={id} onChange={onChange}/></QueryClientProvider></ConfigProvider>;
  return {...render(view(value)),onChange,view,client};
}

it('restores an off-page selected label without paging through the catalog or changing the filter',async()=>{
  const details=vi.spyOn(demandApi,'jd').mockResolvedValue(job('b'));
  const {onChange}=setup();
  expect(await screen.findByTitle(label('b'))).toBeVisible();
  expect(screen.getByTitle(label('b'))).toHaveTextContent('归属 招聘专员甲');
  expect(details).toHaveBeenCalledWith('b');
  expect(demandApi.jds).toHaveBeenCalledTimes(1);
  expect(onChange).not.toHaveBeenCalled();
});

it('does not fetch details for a selected job already present in the first page',async()=>{
  const details=vi.spyOn(demandApi,'jd');
  setup('a');
  expect(await screen.findByTitle(label('a'))).toBeVisible();
  expect(details).not.toHaveBeenCalled();
});

it('does not display a late response for the previous selected job',async()=>{
  let resolveOld!:(value:JdSummary)=>void;
  const oldResponse=new Promise<JdSummary>(resolve=>{resolveOld=resolve;});
  const details=vi.spyOn(demandApi,'jd').mockImplementation(id=>id==='b'?oldResponse:Promise.resolve(job(id)));
  const {rerender,view,onChange}=setup();
  await waitFor(()=>expect(details).toHaveBeenCalledWith('b'));
  rerender(view('c'));
  expect(await screen.findByTitle(label('c'))).toBeVisible();
  await act(async()=>{resolveOld(job('b'));await oldResponse;});
  expect(screen.queryByTitle(label('b'))).not.toBeInTheDocument();
  expect(screen.getByTitle(label('c'))).toBeVisible();
  expect(onChange).not.toHaveBeenCalled();
});

it('keeps an unavailable filter clearable and allows its label to be retried',async()=>{
  vi.spyOn(demandApi,'jd').mockRejectedValueOnce(new Error('unavailable')).mockResolvedValue(job('b'));
  const {onChange}=setup();
  expect(await screen.findByText('岗位信息暂不可用')).toBeVisible();
  expect(onChange).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button',{name:'重新加载岗位'}));
  expect(await screen.findByTitle(label('b'))).toBeVisible();
  fireEvent.click(screen.getByRole('button',{name:'清空岗位筛选'}));
  expect(onChange).toHaveBeenCalledWith(undefined);
});

it('hides failed jobs while keeping successful and pending jobs selectable',async()=>{
  const {onChange}=setup('a');
  vi.mocked(demandApi.jds).mockResolvedValue({items:[job('a'),{...job('failed'),currentParseStatus:'FAILED'},{...job('ready'),currentParseStatus:'SUCCEEDED'}],hasMore:false,nextCursor:null});
  await waitFor(()=>expect(screen.getByTitle(label('a'))).toBeVisible());
  fireEvent.mouseDown(screen.getByRole('combobox'));
  fireEvent.change(screen.getByRole('combobox'),{target:{value:'岗位'}});
  await waitFor(()=>expect(screen.getByRole('option',{name:/岗位ready/})).toBeVisible());
  expect(screen.queryByRole('option',{name:/岗位failed/})).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('option',{name:/岗位ready/}));
  expect(onChange).toHaveBeenCalledWith(expect.objectContaining({id:'ready'}));
});

it('can load and retry later pages when the current page contains only failed jobs',async()=>{
  vi.spyOn(demandApi,'jds').mockResolvedValueOnce({items:[{...job('failed'),currentParseStatus:'FAILED'}],hasMore:true,nextCursor:'next'})
    .mockRejectedValueOnce(new Error('offline'))
    .mockResolvedValueOnce({items:[job('ready')],hasMore:false,nextCursor:null});
  const client=new QueryClient({defaultOptions:{queries:{retry:false}}});
  render(<QueryClientProvider client={client}><JdSelector onChange={vi.fn()}/></QueryClientProvider>);
  fireEvent.mouseDown(screen.getByRole('combobox'));
  fireEvent.click(await screen.findByRole('button',{name:'加载更多岗位'}));
  fireEvent.click(await screen.findByRole('button',{name:'加载失败，点击重试'}));
  expect(await screen.findByRole('option',{name:/岗位ready/})).toBeVisible();
  expect(demandApi.jds).toHaveBeenLastCalledWith('', 'next');
  expect(screen.queryByRole('option',{name:/岗位failed/})).not.toBeInTheDocument();
});

it('does not restore a failed off-page selection into the dropdown',async()=>{
  vi.spyOn(demandApi,'jd').mockResolvedValue({...job('b'),currentParseStatus:'FAILED'});
  const {onChange}=setup();
  expect(await screen.findByText('该岗位解析失败，请重新选择')).toBeVisible();
  fireEvent.mouseDown(screen.getByRole('combobox'));
  expect(screen.queryByRole('option',{name:/岗位b/})).not.toBeInTheDocument();
  expect(onChange).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button',{name:'清空岗位筛选'}));
  expect(onChange).toHaveBeenCalledWith(undefined);
});

it('prioritizes the current owners jobs and sorts each owner group by numeric JD code descending',async()=>{
  vi.spyOn(demandApi,'jds').mockResolvedValue({items:[
    {...job('other-new'),code:'JD-100',ownerUserId:'other'},
    {...job('mine-old'),code:'JD-9',ownerUserId:'hr'},
    {...job('other-old'),code:'JD-20',ownerUserId:'other'},
    {...job('mine-new'),code:'JD-10',ownerUserId:'hr'},
  ],hasMore:false,nextCursor:null});
  render(<QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><JdSelector prioritizeCurrentOwner onChange={vi.fn()}/></QueryClientProvider>);
  fireEvent.mouseDown(screen.getByRole('combobox'));
  await waitFor(()=>expect(screen.getAllByRole('option')).toHaveLength(4));
  expect(screen.getAllByRole('option').map(option=>option.textContent)).toEqual([
    expect.stringContaining('JD-10'),
    expect.stringContaining('JD-9'),
    expect.stringContaining('JD-100'),
    expect.stringContaining('JD-20'),
  ]);
});
