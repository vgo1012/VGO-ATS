import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import { act, cleanup, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { ApiProblemError } from '../api/problem';
import * as http from '../api/http';
import { demandApi } from '../demand/api';
import { directoryApi } from '../identity/directory';
import { talentApi } from '../talent/api';
import { CandidateName, JobName, PeopleSelect, ResumeVersionName, UserName, useUserNames } from './BusinessReferences';

vi.mock('../auth/session', () => ({ useSession: () => ({ actor: { userId: 'self', displayName: '当前登录成员' } }) }));

afterEach(() => { cleanup(); vi.restoreAllMocks(); });

const failure = (status: number) => new ApiProblemError({
  status, title: '引用数据读取失败', detail: '引用数据暂不可用', code: status === 503 ? 'SERVICE_UNAVAILABLE' : 'FORBIDDEN', recoverable: status === 503,
});

function queryClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
}

function OwnerList() {
  const names = useUserNames(['owner-b', 'owner-a', 'owner-b', 'self']);
  return <>{['owner-a', 'owner-b', 'self'].map(id => <span key={id}>{names.people.get(id) ?? '加载中'}</span>)}</>;
}

it('resolves all distinct owners on a page with one directory request', async () => {
  const resolve = vi.spyOn(directoryApi, 'resolve').mockResolvedValue([
    { id: 'owner-a', displayName: '归属人甲', status: 'ACTIVE', homeDepartmentId: 'dept-a' },
    { id: 'owner-b', displayName: '归属人乙', status: 'ACTIVE', homeDepartmentId: 'dept-b' },
  ]);
  render(<QueryClientProvider client={queryClient()}><OwnerList /></QueryClientProvider>);
  await screen.findByText('归属人甲');
  expect(screen.getByText('当前登录成员')).toBeInTheDocument();
  expect(resolve).toHaveBeenCalledExactlyOnceWith(['owner-a', 'owner-b']);
});

it('bounds directory resolution batches to one hundred owners', async () => {
  const ids = Array.from({ length: 205 }, (_, index) => `owner-${String(index).padStart(3, '0')}`);
  const resolve = vi.spyOn(directoryApi, 'resolve').mockImplementation(batch => Promise.resolve(batch.map(id => ({ id, displayName: id, status: 'ACTIVE', homeDepartmentId: 'dept' }))));
  function LargeOwnerList() {
    const names = useUserNames(ids);
    return <span>{names.people.size}</span>;
  }
  render(<QueryClientProvider client={queryClient()}><LargeOwnerList /></QueryClientProvider>);
  await screen.findByText('206');
  expect(resolve).toHaveBeenCalledTimes(3);
  expect(resolve.mock.calls.map(([batch]) => batch.length)).toEqual([100, 100, 5]);
});

it('reads a historical version name exactly without depending on the candidate history page',async()=>{
  const history=vi.spyOn(talentApi,'resumeVersions').mockResolvedValue({items:[],hasMore:true,nextCursor:'next'});
  const request=vi.spyOn(http,'request').mockResolvedValue([{id:'historical',versionNo:1,originalFileName:'历史精确原件.pdf'}]);
  render(<QueryClientProvider client={queryClient()}><ResumeVersionName candidateId="candidate" versionId="historical"/></QueryClientProvider>);
  await screen.findByText('第 1 版 · 历史精确原件.pdf');
  expect(request).toHaveBeenCalledExactlyOnceWith('/resume-version-references?resumeVersionIds=historical');
  expect(history).not.toHaveBeenCalled();
});

it('共享岗位名称只读取绑定版本，拒绝后隐藏缓存且不回退到完整版本列表',async()=>{
  const list=vi.spyOn(demandApi,'jdVersions').mockRejectedValue(failure(403));
  const version=vi.spyOn(demandApi,'jdVersion').mockResolvedValue({id:'frozen',title:'共享冻结岗位',jdCode:'JD-001'} as Awaited<ReturnType<typeof demandApi.jdVersion>>);
  const client=queryClient();
  render(<QueryClientProvider client={client}><JobName jdId="private-jd" jdVersionId="frozen"/></QueryClientProvider>);
  expect(await screen.findByText('共享冻结岗位 · JD-001')).toBeInTheDocument();
  expect(version).toHaveBeenCalledExactlyOnceWith('frozen');
  expect(list).not.toHaveBeenCalled();
  version.mockRejectedValue(failure(403));
  await act(()=>client.invalidateQueries({queryKey:['demand','jd-version','frozen']}));
  expect(await screen.findByText('岗位名称暂不可用')).toBeInTheDocument();
  expect(screen.queryByText('共享冻结岗位 · JD-001')).not.toBeInTheDocument();
  expect(list).not.toHaveBeenCalled();
  version.mockResolvedValue({id:'frozen',title:'恢复后的冻结岗位'} as Awaited<ReturnType<typeof demandApi.jdVersion>>);
  await act(()=>client.invalidateQueries({queryKey:['demand','jd-version','frozen']}));
  expect(await screen.findByText('恢复后的冻结岗位')).toBeInTheDocument();
});

it('hides a cached filename when its exact version is omitted on refresh',async()=>{
  const references=vi.spyOn(talentApi,'resumeVersionReferences').mockResolvedValue([{id:'old',versionNo:1,originalFileName:'不再授权的文件.pdf'}]);
  const client=queryClient();
  render(<QueryClientProvider client={client}><ResumeVersionName candidateId="candidate" versionId="old"/></QueryClientProvider>);
  await screen.findByText('第 1 版 · 不再授权的文件.pdf');
  references.mockResolvedValue([]);
  await act(()=>client.invalidateQueries({queryKey:['talent','resume-version-reference','old']}));
  await screen.findByText('简历版本信息暂不可用');
  expect(screen.queryByText('第 1 版 · 不再授权的文件.pdf')).not.toBeInTheDocument();
});

function renderNames(client: QueryClient) {
  render(<QueryClientProvider client={client}>
    <section data-testid="candidate"><CandidateName id="candidate" /></section>
    <section data-testid="job"><JobName jdId="jd" /></section>
    <section data-testid="resume"><ResumeVersionName candidateId="candidate" versionId="resume" /></section>
    <section data-testid="user"><UserName id="other" /></section>
    <section data-testid="self"><UserName id="self" /></section>
  </QueryClientProvider>);
}

it.each([401, 403, 503])('name references handle cached data after HTTP %s and recover with fresh names', async status => {
  const candidate = vi.spyOn(talentApi, 'candidate').mockResolvedValue({ id: 'candidate', fullName: '原候选人姓名' } as Awaited<ReturnType<typeof talentApi.candidate>>);
  const jobs = vi.spyOn(demandApi, 'jdVersions').mockResolvedValue({ items: [{ id: 'version', title: '原岗位名称' }], hasMore: false, nextCursor: null } as Awaited<ReturnType<typeof demandApi.jdVersions>>);
  const resumes = vi.spyOn(talentApi, 'resumeVersionReferences').mockResolvedValue([{ id: 'resume', versionNo: 1, originalFileName: '原敏感简历.pdf' }]);
  const people = vi.spyOn(directoryApi, 'resolve').mockResolvedValue([{ id: 'other', displayName: '原目录成员', status: 'ACTIVE' }] as Awaited<ReturnType<typeof directoryApi.resolve>>);
  const client = queryClient();
  renderNames(client);
  for (const name of ['原候选人姓名', '原岗位名称', '原目录成员']) await screen.findByText(name);
  await screen.findByText('第 1 版 · 原敏感简历.pdf');
  candidate.mockRejectedValue(failure(status));
  jobs.mockRejectedValue(failure(status));
  resumes.mockRejectedValue(failure(status));
  people.mockRejectedValue(failure(status));
  await act(async () => { await client.invalidateQueries(); });
  await waitFor(() => expect(client.getQueryCache().findAll().filter(query => query.state.status === 'error')).toHaveLength(4));
  if (status === 503) {
    expect(screen.getByTestId('candidate')).toHaveTextContent('原候选人姓名');
    expect(screen.getByTestId('job')).toHaveTextContent('原岗位名称');
    expect(screen.getByTestId('resume')).toHaveTextContent('原敏感简历.pdf');
    expect(screen.getByTestId('user')).toHaveTextContent('原目录成员');
  } else {
    await waitFor(() => expect(screen.getByTestId('candidate')).toHaveTextContent('候选人信息暂不可用'));
    expect(screen.getByTestId('job')).toHaveTextContent('岗位名称暂不可用');
    expect(screen.getByTestId('resume')).toHaveTextContent('简历版本信息暂不可用');
    expect(screen.getByTestId('user')).toHaveTextContent('成员姓名暂不可用');
    for (const name of ['原候选人姓名', '原岗位名称', '原目录成员', '第 1 版 · 原敏感简历.pdf']) expect(screen.queryByText(name)).not.toBeInTheDocument();
  }
  expect(screen.getByTestId('self')).toHaveTextContent('当前登录成员');
  candidate.mockResolvedValue({ id: 'candidate', fullName: '恢复后的候选人' } as Awaited<ReturnType<typeof talentApi.candidate>>);
  jobs.mockResolvedValue({ items: [{ id: 'version', title: '恢复后的岗位' }], hasMore: false, nextCursor: null } as Awaited<ReturnType<typeof demandApi.jdVersions>>);
  resumes.mockResolvedValue([{ id: 'resume', versionNo: 1, originalFileName: '恢复后的简历.pdf' }]);
  people.mockResolvedValue([{ id: 'other', displayName: '恢复后的成员', status: 'ACTIVE' }] as Awaited<ReturnType<typeof directoryApi.resolve>>);
  await act(async () => { await client.invalidateQueries(); });
  await screen.findByText('恢复后的候选人');
  expect(screen.getByTestId('job')).toHaveTextContent('恢复后的岗位');
  expect(screen.getByTestId('resume')).toHaveTextContent('恢复后的简历.pdf');
  expect(screen.getByTestId('user')).toHaveTextContent('恢复后的成员');
});

it.each([401, 403, 503])('PeopleSelect handles cached selected labels and search options after HTTP %s', async status => {
  const references = vi.spyOn(directoryApi, 'resolve').mockResolvedValue([{ id: 'selected', displayName: '原已选成员', status: 'ACTIVE' }] as Awaited<ReturnType<typeof directoryApi.resolve>>);
  const users = vi.spyOn(directoryApi, 'users').mockResolvedValue({ items: [{ id: 'search-result', displayName: '原搜索成员', status: 'ACTIVE' }], hasMore: false, nextCursor: null } as Awaited<ReturnType<typeof directoryApi.users>>);
  const client = queryClient();
  const changed = vi.fn();
  render(<ConfigProvider theme={{ token: { motion: false } }}><App><QueryClientProvider client={client}>
    <PeopleSelect aria-label="选择成员" value="selected" onChange={changed} open virtual={false} />
  </QueryClientProvider></App></ConfigProvider>);
  await waitFor(() => expect(screen.getAllByText('原已选成员').length).toBeGreaterThan(0));
  await screen.findByText('原搜索成员');
  references.mockRejectedValue(failure(status));
  users.mockRejectedValue(failure(status));
  await act(async () => { await client.invalidateQueries({ queryKey: ['identity'] }); });
  await waitFor(() => expect(client.getQueryCache().findAll().every(query => query.state.status === 'error')).toBe(true));
  if (status === 503) {
    expect(screen.getAllByText('原已选成员').length).toBeGreaterThan(0);
    expect(screen.getByText('原搜索成员')).toBeInTheDocument();
  } else {
    await waitFor(() => expect(screen.queryByText('原搜索成员')).not.toBeInTheDocument());
    expect(screen.queryByText('原已选成员')).not.toBeInTheDocument();
    expect(screen.getAllByText('成员姓名暂不可用').length).toBeGreaterThan(0);
  }
  expect(changed).not.toHaveBeenCalled();
  references.mockResolvedValue([{ id: 'selected', displayName: '恢复后的已选成员', status: 'ACTIVE' }] as Awaited<ReturnType<typeof directoryApi.resolve>>);
  users.mockResolvedValue({ items: [{ id: 'search-result', displayName: '恢复后的搜索成员', status: 'ACTIVE' }], hasMore: false, nextCursor: null } as Awaited<ReturnType<typeof directoryApi.users>>);
  await act(async () => { await client.invalidateQueries({ queryKey: ['identity'] }); });
  await screen.findByText('恢复后的搜索成员');
  expect(screen.getAllByText('恢复后的已选成员').length).toBeGreaterThan(0);
  expect(within(document.body).getByRole('combobox', { name: '选择成员' })).toBeEnabled();
  expect(changed).not.toHaveBeenCalled();
});

it('renders the stable JD code from the existing authorized version response', async () => {
  const versions = vi.spyOn(demandApi, 'jdVersions').mockResolvedValue({ items: [{ id: 'version', title: 'IDC工程师', jdCode: 'JD-000005' }], hasMore: false, nextCursor: null } as Awaited<ReturnType<typeof demandApi.jdVersions>>);
  render(<QueryClientProvider client={queryClient()}><JobName jdId="jd" /></QueryClientProvider>);
  expect(await screen.findByText('IDC工程师 · JD-000005')).toBeInTheDocument();
  expect(versions).toHaveBeenCalledOnce();
});
