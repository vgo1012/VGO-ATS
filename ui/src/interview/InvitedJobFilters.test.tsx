import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider, Row } from 'antd';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useState } from 'react';
import { InvitedJobFilters } from './InvitedJobFilters';
import { interviewApi } from './api';

vi.mock('./api',()=>({interviewApi:{invitedJobs:vi.fn()}}));
const job=(id:string)=>({jdId:id,jdVersionId:id+'-v1',jdVersionNo:1,title:'受邀岗位'+id});
function Form() {
  const [value,setValue]=useState<Record<string,string|number|undefined>>({});
  return <Row><InvitedJobFilters value={value} onChange={setValue}/></Row>;
}
function mount() {
  render(<ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><Form/></QueryClientProvider></App></ConfigProvider>);
}
describe('invited job filters',()=>{
  beforeEach(()=>{cleanup();vi.resetAllMocks();});
  it('loads further pages and resolves versions through assigned-job queries',async()=>{
    vi.mocked(interviewApi.invitedJobs).mockImplementation(params=>Promise.resolve(params.jdId?
      {items:[job('B')],hasMore:false,nextCursor:null}:params.cursor?
      {items:[job('B')],hasMore:false,nextCursor:null}:{items:[job('A')],hasMore:true,nextCursor:'next'}));
    mount();
    await waitFor(()=>expect(interviewApi.invitedJobs).toHaveBeenCalled());
    fireEvent.mouseDown(screen.getByRole('combobox',{name:'搜索受邀岗位'}));
    fireEvent.click(await screen.findByRole('button',{name:'加载更多受邀岗位'}));
    await waitFor(()=>expect(interviewApi.invitedJobs).toHaveBeenCalledWith({query:'',cursor:'next'}));
    fireEvent.click(await screen.findByText('受邀岗位B'));
    await waitFor(()=>expect(interviewApi.invitedJobs).toHaveBeenCalledWith({jdId:'B'}));
    fireEvent.mouseDown(screen.getByRole('combobox',{name:'岗位版本'}));
    expect(await screen.findByText('第 1 版 · 受邀岗位B')).toBeInTheDocument();
  });
  it('searches on the server and exposes a retry after a failed initial read',async()=>{
    vi.mocked(interviewApi.invitedJobs).mockRejectedValueOnce(new Error('暂不可用'))
      .mockResolvedValue({items:[],hasMore:false,nextCursor:null});
    mount();
    fireEvent.mouseDown(screen.getByRole('combobox',{name:'搜索受邀岗位'}));
    fireEvent.click(await screen.findByRole('button',{name:'岗位读取失败，重试'}));
    await waitFor(()=>expect(interviewApi.invitedJobs).toHaveBeenCalledTimes(2));
    fireEvent.change(screen.getByRole('combobox',{name:'搜索受邀岗位'}),{target:{value:'Linux'}});
    await waitFor(()=>expect(interviewApi.invitedJobs).toHaveBeenCalledWith({query:'Linux'}));
  });
});
