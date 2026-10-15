import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App, ConfigProvider } from 'antd';
import type { ReactNode } from 'react';
import { MemoryRouter, useLocation, useNavigate, type InitialEntry } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { ApiProblemError } from '../api/problem';
import { MatchingWorkspacePage } from './MatchingWorkspacePage';

const mocks = vi.hoisted(() => {
  const profile = { profile: { id: 'personal', name: '个人测试方案', ownerUserId: 'user' }, currentVersion: { id: 'personal-v1', skillsWeight: 100 } };
  return { versions: vi.fn(), version: vi.fn(), jd: vi.fn(), create: vi.fn(), active: vi.fn(), admitted: vi.fn(), selectCandidates: vi.fn(), profile };
});
vi.mock('../matching/selectScreeningCandidates', () => ({ selectScreeningCandidates: mocks.selectCandidates }));
vi.mock('../auth/session', () => ({ useSession: () => ({ actor: { userId: 'user', permissions: [] } }) }));
vi.mock('../matching/useScreeningJobs', () => ({ useScreeningJobs: () => ({ data: { pages: [{ items: [{ id: 'job', title: '招聘岗位', currentVersionNo: 2 }] }] }, isFetching: false }) }));
vi.mock('../demand/api', () => ({ demandApi: {
  jdVersions: mocks.versions,
  jdVersion: mocks.version,
  jd: mocks.jd,
  activeScoringForJd: mocks.active,
  scoringDefaults: () => Promise.resolve({ weightProfile: { ...mocks.profile, currentVersion: { ...mocks.profile.currentVersion, id: 'company-v1' } } }),
  weightProfiles: () => Promise.resolve({ items: [mocks.profile] }),
} }));
vi.mock('../talent/api', () => ({ talentApi: { screeningCandidates: mocks.admitted } }));
vi.mock('../matching/api', () => ({ matchingApi: { createRun: mocks.create } }));
vi.mock('../matching/WeightProfiles', () => ({ WeightProfiles: () => null }));
vi.mock('../matching/MatchJobWorkspace', () => ({ MatchJobWorkspace: ({ jdVersionId, selectedJob, basisRunId, basisSelection, schemeLabel }: { jdVersionId?: string; selectedJob?: string; basisRunId?: string; basisSelection?: { profile: { currentVersion: { id: string } } }; schemeLabel: string }) => <div><span data-testid="result-version">{jdVersionId}</span><span data-testid="selected-job">{selectedJob}</span><span data-testid="basis-run">{basisRunId}</span><span data-testid="scheme-label">{schemeLabel}</span><span data-testid="scheme-profile">{basisSelection?.profile.currentVersion.id}</span></div>, ScreeningResultsLoading: () => null }));
vi.mock('../matching/ScreeningJobHeader', () => ({ ScreeningJobHeader: ({ jd, version, loading, schemeControls, onChange, onRescore, rescoreDisabled }: { jd?: { title: string }; version?: { id: string; rawText: string }; loading: boolean; schemeControls: ReactNode; onChange: (jd: { id: string; title: string; currentVersionNo: number }) => void; onRescore: () => void; rescoreDisabled: boolean }) => <section><span data-testid="header-jd">{jd?.title}</span><span data-testid="header-version">{version?.id}</span><span data-testid="header-loading">{String(loading)}</span><p>{version?.rawText}</p>{schemeControls}<button onClick={() => onChange({ id: 'job-2', title: '第二岗位', currentVersionNo: 1 })}>选择第二岗位</button><button onClick={() => onChange({ id: 'job-3', title: '第三岗位', currentVersionNo: 1 })}>选择第三岗位</button><button disabled={rescoreDisabled} onClick={onRescore}>重新评分</button></section> }));

const original = { items: [{ id: 'version-1', versionNo: 1, rawText: '历史版本原文' }, { id: 'version-2', versionNo: 2, rawText: '当前授权原文' }], hasMore: false };
const restored = { ...original, items: original.items.map(item => item.id === 'version-2' ? { ...item, rawText: '恢复当前版本原文' } : item) };
const problem = (status: number) => new ApiProblemError({ status, code: status === 503 ? 'SERVICE_UNAVAILABLE' : 'ACCESS_DENIED', title: '版本读取失败', detail: '岗位版本暂不可用', recoverable: status === 503 });
beforeEach(() => {
  mocks.versions.mockReset().mockResolvedValue(original);
  mocks.version.mockReset().mockResolvedValue({ id: 'version-2', jdId: 'job', versionNo: 2, rawText: '当前授权原文' });
  mocks.jd.mockReset().mockResolvedValue({ id: 'job', title: '招聘岗位', currentVersionNo: 2 });
  mocks.active.mockReset().mockResolvedValue({ jdVersionId: 'version-2', scoringConfiguration: { version: { id: 'config', skillsEnabled: true }, criteria: [] } });
  mocks.create.mockReset();
  mocks.admitted.mockReset().mockResolvedValue([{ candidateId: 'candidate', resumeVersionId: 'resume', jdVersionId: 'version-2', admissionStatus: 'ADMITTED' }]);
  mocks.selectCandidates.mockReset().mockResolvedValue([{ candidateId: 'candidate', resumeVersionId: 'resume' }]);
});
afterEach(() => { cleanup(); });
function NavigationProbe() {
  const location=useLocation();
  const navigate=useNavigate();
  return <><span data-testid="route">{location.search}</span><span data-testid="route-jd">{(location.state as {jdId?:string}|null)?.jdId}</span><button onClick={()=>navigate(-1)}>返回上一页</button><button onClick={()=>navigate(1)}>前往下一页</button></>;
}
function mount(entry: InitialEntry = '/matching') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(<ConfigProvider theme={{ token: { motion: false } }}><App><QueryClientProvider client={client}><MemoryRouter initialEntries={[entry]}><NavigationProbe/><MatchingWorkspacePage /></MemoryRouter></QueryClientProvider></App></ConfigProvider>);
  return client;
}

it('does not show a version retry alert before a job is selected', () => {
  mount({pathname:'/matching',search:'?run=run-1'});
  expect(screen.queryByRole('button',{name:'重试岗位版本'})).not.toBeInTheDocument();
});

it('stores a manual selection in route state and restores the direct version after browser Back', async () => {
  mocks.jd.mockImplementation((id:string)=>Promise.resolve({ id, title: id==='job'?'第一岗位':'第二岗位', currentVersionNo: id==='job'?2:1 }));
  mocks.versions.mockImplementation((id:string)=>Promise.resolve(id==='job-2'?{items:[{id:'version-b',versionNo:1,rawText:'第二岗位版本'}],hasMore:false}:original));
  mount('/matching?scoreJob=version-2');
  await screen.findByText('当前授权原文');
  fireEvent.click(screen.getByRole('button',{name:'选择第二岗位'}));
  await screen.findByText('第二岗位版本');
  expect(screen.getByTestId('route')).toBeEmptyDOMElement();
  expect(screen.getByTestId('route-jd')).toHaveTextContent('job-2');
  fireEvent.click(screen.getByRole('button',{name:'返回上一页'}));
  await waitFor(()=>expect(screen.getByTestId('route')).toHaveTextContent('scoreJob=version-2'));
  await screen.findByText('当前授权原文');
  expect(screen.getByTestId('header-version')).toHaveTextContent('version-2');
});

it('restores ordinary manual job history immediately on Back and Forward', async () => {
  mocks.versions.mockImplementation((id:string)=>Promise.resolve(id==='job'?original:{items:[{id:`version-${id}`,versionNo:1,rawText:`${id}版本`}],hasMore:false}));
  mocks.jd.mockImplementation((id:string)=>Promise.resolve({id,title:id==='job-2'?'第二岗位':'第三岗位',currentVersionNo:1}));
  mocks.active.mockImplementation((id:string)=>Promise.resolve({jdVersionId:id,scoringConfiguration:{version:{id:'config',skillsEnabled:true},criteria:[]}}));
  mocks.admitted.mockResolvedValue([{candidateId:'candidate',resumeVersionId:'resume',jdVersionId:'version-job-3',admissionStatus:'ADMITTED'}]);
  mocks.create.mockResolvedValue({id:'run-3'});
  mount();
  fireEvent.click(screen.getByRole('button',{name:'选择第二岗位'}));
  await screen.findByText('job-2版本');
  fireEvent.click(screen.getByRole('button',{name:'选择第三岗位'}));
  await screen.findByText('job-3版本');
  expect(screen.getByTestId('route-jd')).toHaveTextContent('job-3');

  fireEvent.click(screen.getByRole('button',{name:'返回上一页'}));
  await screen.findByText('job-2版本');
  expect(screen.getByTestId('header-jd')).toHaveTextContent('第二岗位');
  expect(screen.getByTestId('result-version')).toHaveTextContent('version-job-2');
  expect(screen.getByTestId('route-jd')).toHaveTextContent('job-2');
  fireEvent.click(screen.getByRole('button',{name:'返回上一页'}));
  await screen.findByText('当前授权原文');
  expect(screen.getByTestId('header-jd')).toHaveTextContent('招聘岗位');
  expect(screen.getByTestId('result-version')).toHaveTextContent('version-2');
  expect(screen.getByTestId('route-jd')).toBeEmptyDOMElement();
  fireEvent.click(screen.getByRole('button',{name:'前往下一页'}));
  await screen.findByText('job-2版本');
  expect(screen.getByTestId('header-jd')).toHaveTextContent('第二岗位');
  fireEvent.click(screen.getByRole('button',{name:'前往下一页'}));
  await screen.findByText('job-3版本');
  expect(screen.getByTestId('header-jd')).toHaveTextContent('第三岗位');
  expect(screen.getByTestId('result-version')).toHaveTextContent('version-job-3');
  expect(screen.getByTestId('route-jd')).toHaveTextContent('job-3');
  fireEvent.click(screen.getByRole('radio',{name:'个人方案'}));
  const rescore=screen.getByRole('button',{name:'重新评分'});
  await waitFor(()=>expect(rescore).toBeEnabled());
  fireEvent.click(rescore);
  await waitFor(()=>expect(mocks.create).toHaveBeenCalledWith(expect.objectContaining({jdVersionId:'version-job-3'})));
});

it('recovers a direct scoreJob link through the exact historical version and its parent JD', async () => {
  mocks.version.mockResolvedValue({ id: 'version-1', jdId: 'job', versionNo: 1, rawText: '历史版本原文' });
  mocks.jd.mockResolvedValue({ id: 'job', title: '已更新岗位标题', currentVersionNo: 2 });

  mount('/matching?scoreJob=version-1');

  await screen.findByText('历史版本原文');
  expect(screen.getByTestId('header-version')).toHaveTextContent('version-1');
  expect(screen.getByTestId('result-version')).toHaveTextContent('version-1');
  expect(mocks.version).toHaveBeenCalledWith('version-1');
  expect(mocks.jd).toHaveBeenCalledWith('job');
  expect(mocks.versions).not.toHaveBeenCalled();
  expect(screen.getByTestId('header-loading')).toHaveTextContent('false');
});

it('fails closed after exact-version access is denied and recovers only after an explicit retry', async () => {
  const client = mount('/matching?scoreJob=version-2');
  await screen.findByText('当前授权原文');
  mocks.version.mockRejectedValue(problem(403));

  await act(() => client.invalidateQueries({ queryKey: ['demand', 'jd-version', 'version-2'] }));

  await waitFor(() => expect(screen.queryByText('当前授权原文')).not.toBeInTheDocument());
  expect(screen.getByTestId('header-version')).toBeEmptyDOMElement();
  expect(screen.getByTestId('result-version')).toBeEmptyDOMElement();
  expect(screen.getByTestId('header-jd')).toBeEmptyDOMElement();
  expect(screen.getByTestId('selected-job')).toBeEmptyDOMElement();
  expect(screen.getByTestId('basis-run')).toBeEmptyDOMElement();
  mocks.version.mockResolvedValue({ id: 'version-2', jdId: 'job', versionNo: 2, rawText: '恢复后的精确版本' });
  fireEvent.click(screen.getByRole('button', { name: '重试岗位版本' }));
  await screen.findByText('恢复后的精确版本');
  expect(screen.getByTestId('result-version')).toHaveTextContent('version-2');
  expect(mocks.versions).not.toHaveBeenCalled();
});

it('hides cached exact context when its parent JD is denied and restores it on retry', async () => {
  const client = mount('/matching?scoreJob=version-2&basis=basis-1');
  await screen.findByText('当前授权原文');
  expect(screen.getByTestId('header-jd')).toHaveTextContent('招聘岗位');
  mocks.jd.mockRejectedValue(problem(403));
  await act(() => client.invalidateQueries({ queryKey: ['demand', 'jd', 'job'] }));
  await waitFor(() => expect(screen.getByTestId('header-jd')).toBeEmptyDOMElement());
  expect(screen.queryByText('当前授权原文')).not.toBeInTheDocument();
  expect(screen.getByTestId('selected-job')).toBeEmptyDOMElement();
  expect(screen.getByTestId('basis-run')).toBeEmptyDOMElement();
  mocks.jd.mockResolvedValue({ id: 'job', title: '恢复岗位', currentVersionNo: 3 });
  fireEvent.click(screen.getByRole('button', { name: '重试岗位版本' }));
  await waitFor(() => expect(screen.getByTestId('header-jd')).toHaveTextContent('恢复岗位'));
  expect(screen.getByTestId('selected-job')).toHaveTextContent('version-2');
  expect(screen.getByTestId('basis-run')).toHaveTextContent('basis-1');
});

it('keeps all cached context hidden when version recovery fails transiently and parent access is denied', async () => {
  const client = mount('/matching?scoreJob=version-2&basis=basis-1');
  await screen.findByText('当前授权原文');
  mocks.version.mockRejectedValue(problem(503));
  mocks.jd.mockRejectedValue(problem(403));
  await act(() => Promise.all([
    client.invalidateQueries({ queryKey: ['demand', 'jd-version', 'version-2'] }),
    client.invalidateQueries({ queryKey: ['demand', 'jd', 'job'] }),
  ]));
  await waitFor(() => expect(screen.getByText('暂无岗位版本的查看权限')).toBeInTheDocument());
  expect(screen.queryByText('当前授权原文')).not.toBeInTheDocument();
  expect(screen.getByTestId('header-jd')).toBeEmptyDOMElement();
  expect(screen.getByTestId('header-version')).toBeEmptyDOMElement();
  expect(screen.getByTestId('result-version')).toBeEmptyDOMElement();
  expect(screen.getByTestId('selected-job')).toBeEmptyDOMElement();
  expect(screen.getByTestId('basis-run')).toBeEmptyDOMElement();
  fireEvent.click(screen.getByRole('radio', { name: '个人方案' }));
  expect(screen.getByRole('button', { name: '重新评分' })).toBeDisabled();
});

it('keeps exact-version retry controls mounted while a failed initial read is retried', async () => {
  let rejectInitial!: (error: unknown) => void;
  mocks.version.mockImplementationOnce(() => new Promise((_, reject) => { rejectInitial = reject; }));
  mount('/matching?scoreJob=version-2');
  expect(screen.getByTestId('header-loading')).toHaveTextContent('true');
  await act(() => { rejectInitial(problem(503)); return Promise.resolve(); });
  const retry = await screen.findByRole('button', { name: '重试岗位版本' });
  let resolveRetry!: (value: { id: string; jdId: string; versionNo: number; rawText: string }) => void;
  mocks.version.mockImplementationOnce(() => new Promise(resolve => { resolveRetry = resolve; }));
  retry.focus();
  fireEvent.click(retry);
  await waitFor(() => expect(retry).toBeDisabled());
  expect(retry).toHaveFocus();
  expect(screen.getByTestId('header-loading')).toHaveTextContent('true');
  await act(() => { resolveRetry({ id: 'version-2', jdId: 'job', versionNo: 2, rawText: '重试恢复版本' }); return Promise.resolve(); });
  await screen.findByText('重试恢复版本');
  expect(screen.getByTestId('header-loading')).toHaveTextContent('false');
});

it('creates a personal rescore from a directly linked historical version', async () => {
  mocks.version.mockResolvedValue({ id: 'version-1', jdId: 'job', versionNo: 1, rawText: '历史版本原文' });
  mocks.active.mockResolvedValue({ jdVersionId: 'version-1', scoringConfiguration: { version: { id: 'config', skillsEnabled: true }, criteria: [] } });
  mocks.admitted.mockResolvedValue([{ candidateId: 'candidate', resumeVersionId: 'resume', jdVersionId: 'version-1', admissionStatus: 'ADMITTED' }]);
  mocks.create.mockResolvedValue({ id: 'run-1' });
  mount('/matching?scoreJob=version-1');
  await screen.findByText('历史版本原文');
  fireEvent.click(screen.getByRole('radio', { name: '个人方案' }));
  const rescore = screen.getByRole('button', { name: '重新评分' });
  await waitFor(() => expect(rescore).toBeEnabled());
  fireEvent.click(rescore);
  await waitFor(() => expect(mocks.create).toHaveBeenCalledWith(expect.objectContaining({ jdVersionId: 'version-1' })));
  expect(mocks.versions).not.toHaveBeenCalled();
});

it('caches a personal rescore by its exact scoring basis and restores that basis after switching schemes', async () => {
  mocks.create.mockResolvedValue({ id: 'personal-run' });
  const client = mount();
  await screen.findByText('当前授权原文');
  fireEvent.click(screen.getByRole('radio', { name: '个人方案' }));
  expect(screen.getByTestId('scheme-label')).toHaveTextContent('个人方案');
  expect(screen.getByTestId('scheme-profile')).toHaveTextContent('personal-v1');
  const rescore = screen.getByRole('button', { name: '重新评分' });
  await waitFor(() => expect(rescore).toBeEnabled());
  fireEvent.click(rescore);
  await waitFor(() => expect(screen.getByTestId('route')).toHaveTextContent('run=personal-run'));

  const cachedBasis = client.getQueriesData({ queryKey: ['matching', 'scheme-basis', 'version-2'] });
  expect(cachedBasis).toHaveLength(1);
  expect(cachedBasis[0]?.[1]).toBe('personal-run');

  fireEvent.click(screen.getByRole('radio', { name: '公司默认' }));
  await waitFor(() => expect(screen.getByTestId('scheme-label')).toHaveTextContent('公司默认'));
  fireEvent.click(screen.getByRole('radio', { name: '个人方案' }));
  expect(screen.getByTestId('scheme-label')).toHaveTextContent('个人方案');
  expect(screen.getByTestId('scheme-profile')).toHaveTextContent('personal-v1');
  expect(mocks.create).toHaveBeenCalledTimes(1);
});

it('blocks a direct historical rescore when exact-version permission is withdrawn while candidates load', async () => {
  mocks.version.mockResolvedValue({ id: 'version-1', jdId: 'job', versionNo: 1, rawText: '历史版本原文' });
  mocks.active.mockResolvedValue({ jdVersionId: 'version-1', scoringConfiguration: { version: { id: 'config', skillsEnabled: true }, criteria: [] } });
  mocks.admitted.mockResolvedValue([{ candidateId: 'candidate', resumeVersionId: 'resume', jdVersionId: 'version-1', admissionStatus: 'ADMITTED' }]);
  const client = mount('/matching?scoreJob=version-1');
  await screen.findByText('历史版本原文');
  fireEvent.click(screen.getByRole('radio', { name: '个人方案' }));
  const rescore = screen.getByRole('button', { name: '重新评分' });
  await waitFor(() => expect(rescore).toBeEnabled());
  let resolve!: (value: Array<{ candidateId: string; resumeVersionId: string }>) => void;
  mocks.selectCandidates.mockImplementation(() => new Promise(done => { resolve = done; }));
  fireEvent.click(rescore);
  await waitFor(() => expect(mocks.selectCandidates).toHaveBeenCalled());
  mocks.version.mockRejectedValue(problem(403));
  await act(() => client.invalidateQueries({ queryKey: ['demand', 'jd-version', 'version-1'] }));
  await act(() => { resolve([{ candidateId: 'candidate', resumeVersionId: 'resume' }]); return Promise.resolve(); });
  await waitFor(() => expect(client.isMutating()).toBe(0));
  expect(mocks.create).not.toHaveBeenCalled();
});

it.each([401, 403, 503])('version %s controls cached raw text and the rescore action independently of transient failure', async status => {
  const client = mount();
  await screen.findByText('当前授权原文');
  fireEvent.click(screen.getByRole('radio', { name: '个人方案' }));
  await waitFor(() => expect(screen.getByRole('button', { name: '重新评分' })).toBeEnabled());
  mocks.versions.mockRejectedValue(problem(status));
  await act(() => client.invalidateQueries({ queryKey: ['demand', 'jd-versions-by-id', 'job'] }));
  if (status !== 503) {
    await waitFor(() => expect(screen.queryByText('当前授权原文')).not.toBeInTheDocument());
    expect(screen.getByTestId('header-version')).toBeEmptyDOMElement();
    expect(screen.getByTestId('result-version')).toBeEmptyDOMElement();
    expect(screen.getByRole('button', { name: '重新评分' })).toBeDisabled();
  } else {
    expect(screen.getByText('当前授权原文')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '重新评分' })).toBeEnabled();
  }
  mocks.versions.mockResolvedValue(restored);
  fireEvent.click(await screen.findByRole('button', { name: '重试岗位版本' }));
  await screen.findByText('恢复当前版本原文');
  expect(screen.getByTestId('header-version')).toHaveTextContent('version-2');
  expect(screen.queryByText('历史版本原文')).not.toBeInTheDocument();
  await waitFor(() => expect(screen.getByRole('button', { name: '重新评分' })).toBeEnabled());
  expect(mocks.versions.mock.calls.every(args => args.length === 1 && args[0] === 'job')).toBe(true);
  expect(mocks.create).not.toHaveBeenCalled();
}, 15_000);

it.each(['denied', 'removed'])('does not submit a prepared score after its selected version becomes %s', async state => {
  const client = mount();
  await screen.findByText('当前授权原文');
  fireEvent.click(screen.getByRole('radio', { name: '个人方案' }));
  const rescore = screen.getByRole('button', { name: '重新评分' });
  await waitFor(() => expect(rescore).toBeEnabled());
  let resolve!: (value: Array<{ candidateId: string; resumeVersionId: string }>) => void;
  mocks.selectCandidates.mockImplementation(() => new Promise(done => { resolve = done; }));
  fireEvent.click(rescore);
  await waitFor(() => expect(mocks.selectCandidates).toHaveBeenCalledTimes(1));
  if (state === 'denied') mocks.versions.mockRejectedValue(problem(403));
  else mocks.versions.mockResolvedValue({ ...original, items: [original.items[0]] });
  await act(() => client.invalidateQueries({ queryKey: ['demand', 'jd-versions-by-id', 'job'] }));
  await act(() => { resolve([{ candidateId: 'candidate', resumeVersionId: 'resume' }]); return Promise.resolve(); });
  await waitFor(() => expect(client.isMutating()).toBe(0));
  expect(mocks.create).not.toHaveBeenCalled();
}, 15_000);

it('does not submit a manual-job score after parent access is withdrawn while candidates load', async () => {
  mocks.versions.mockImplementation((id:string)=>Promise.resolve({items:[{id:`version-${id}`,versionNo:1,rawText:`${id}版本`}],hasMore:false}));
  mocks.jd.mockImplementation((id:string)=>Promise.resolve({id,title:'第二岗位',currentVersionNo:1}));
  mocks.active.mockImplementation((id:string)=>Promise.resolve({jdVersionId:id,scoringConfiguration:{version:{id:'config',skillsEnabled:true},criteria:[]}}));
  mocks.admitted.mockResolvedValue([{candidateId:'candidate',resumeVersionId:'resume',jdVersionId:'version-job-2',admissionStatus:'ADMITTED'}]);
  const client=mount();
  fireEvent.click(screen.getByRole('button',{name:'选择第二岗位'}));
  await screen.findByText('job-2版本');
  fireEvent.click(screen.getByRole('radio',{name:'个人方案'}));
  const rescore=screen.getByRole('button',{name:'重新评分'});
  await waitFor(()=>expect(rescore).toBeEnabled());
  let resolve!: (value:Array<{candidateId:string;resumeVersionId:string}>)=>void;
  mocks.selectCandidates.mockImplementation(()=>new Promise(done=>{resolve=done;}));
  fireEvent.click(rescore);
  await waitFor(()=>expect(mocks.selectCandidates).toHaveBeenCalled());
  mocks.jd.mockRejectedValue(problem(403));
  await act(()=>client.invalidateQueries({queryKey:['demand','jd','job-2']}));
  await act(()=>{resolve([{candidateId:'candidate',resumeVersionId:'resume'}]);return Promise.resolve();});
  await waitFor(()=>expect(client.isMutating()).toBe(0));
  expect(mocks.create).not.toHaveBeenCalled();
});

it('recovers the first failed version read and shares one slow retry', async () => {
  mocks.versions.mockRejectedValue(problem(503));
  mount();
  const retry = await screen.findByRole('button', { name: '重试岗位版本' });
  expect(screen.getByTestId('header-version')).toBeEmptyDOMElement();
  expect(screen.getByRole('button', { name: '重新评分' })).toBeDisabled();
  let resolve!: (value: typeof restored) => void;
  mocks.versions.mockImplementation(() => new Promise(done => { resolve = done; }));
  const before = mocks.versions.mock.calls.length;
  fireEvent.click(retry); fireEvent.click(retry);
  await waitFor(() => expect(retry).toBeDisabled());
  expect(mocks.versions).toHaveBeenCalledTimes(before + 1);
  await act(() => { resolve(restored); return Promise.resolve(); });
  await screen.findByText('恢复当前版本原文');
  expect(screen.getByTestId('header-version')).toHaveTextContent('version-2');
}, 15_000);
