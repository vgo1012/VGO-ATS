import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { ApiProblemError } from '../api/problem';
import { demandApi } from '../demand/api';
import { talentApi } from '../talent/api';
import { ApplicationMaterials } from './ApplicationMaterials';
import type { RecruitmentApplication } from './api';

afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.unstubAllGlobals(); });

it('直接推荐无需面试记录即可预览绑定版本，关闭时释放文件', async () => {
  vi.spyOn(talentApi, 'resumeVersionReferences').mockResolvedValue([{ id: 'bound-resume', versionNo: 2, originalFileName: '原始简历.pdf' }]);
  vi.spyOn(talentApi, 'previewResume').mockRejectedValueOnce(new Error('下载暂时失败')).mockResolvedValue(new Blob(['%PDF'], { type: 'application/pdf' }));
  vi.spyOn(demandApi, 'jdVersion').mockResolvedValue({ id: 'bound-jd', versionNo: 3, title: '推荐岗位', rawText: '推荐时绑定的 JD 原文' } as Awaited<ReturnType<typeof demandApi.jdVersion>>);
  const create = vi.fn(() => 'blob:bound-resume'), revoke = vi.fn();
  vi.stubGlobal('URL', class extends URL { static createObjectURL = create; static revokeObjectURL = revoke; });
  render(<ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={new QueryClient()}><ApplicationMaterials application={{ id: 'app', candidateId: 'person', resumeVersionId: 'bound-resume', jdVersionId: 'bound-jd' } as RecruitmentApplication}/></QueryClientProvider></App></ConfigProvider>);
  expect(talentApi.previewResume).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: '查看简历与 JD' }));
  expect(await screen.findByText('推荐时绑定的 JD 原文')).toBeInTheDocument();
  fireEvent.click(await screen.findByRole('button', { name: '重试简历' }));
  expect(await screen.findByTitle('本次推荐简历在线预览')).toHaveAttribute('src', 'blob:bound-resume');
  expect(talentApi.resumeVersionReferences).toHaveBeenCalledWith(['bound-resume']);
  expect(talentApi.previewResume).toHaveBeenCalledWith('bound-resume');
  expect(demandApi.jdVersion).toHaveBeenCalledWith('bound-jd');
  fireEvent.click(screen.getByRole('button', { name: 'Close' }));
  await waitFor(() => expect(revoke).toHaveBeenCalledWith('blob:bound-resume'));
});

it('材料刷新临时失败保留已授权缓存，撤权后立即隐藏', async () => {
  const resume = vi.spyOn(talentApi, 'resumeVersionReferences').mockResolvedValue([{ id: 'bound-resume', versionNo: 2, originalFileName: '已授权简历.pdf' }]);
  const preview = vi.spyOn(talentApi, 'previewResume').mockResolvedValue(new Blob(['%PDF'], { type: 'application/pdf' }));
  const jd = vi.spyOn(demandApi, 'jdVersion').mockResolvedValue({ id: 'bound-jd', versionNo: 3, title: '已授权岗位', rawText: '已授权 JD 原文' } as Awaited<ReturnType<typeof demandApi.jdVersion>>);
  const revoke = vi.fn();
  vi.stubGlobal('URL', class extends URL { static createObjectURL = () => 'blob:authorized'; static revokeObjectURL = revoke; });
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(<ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={client}><ApplicationMaterials application={{ id: 'app', candidateId: 'person', resumeVersionId: 'bound-resume', jdVersionId: 'bound-jd' } as RecruitmentApplication}/></QueryClientProvider></App></ConfigProvider>);
  fireEvent.click(screen.getByRole('button', { name: '查看简历与 JD' }));
  await screen.findByText('已授权简历.pdf');
  await screen.findByText('已授权岗位');
  expect(await screen.findByTitle('本次推荐简历在线预览')).toHaveAttribute('src', 'blob:authorized');

  const failure = (status: number) => new ApiProblemError({ status, title: '材料读取失败', detail: '材料暂不可用', code: status === 503 ? 'SERVICE_UNAVAILABLE' : 'FORBIDDEN', recoverable: status === 503 });
  resume.mockRejectedValue(failure(503));
  jd.mockRejectedValue(failure(503));
  await act(async () => { await client.invalidateQueries(); });
  await screen.findByText('简历信息刷新失败，继续显示上次已授权内容');
  await screen.findByText('岗位 JD 刷新失败，继续显示上次已授权内容');
  expect(screen.getByText('已授权简历.pdf')).toBeInTheDocument();
  expect(screen.getByText('已授权岗位')).toBeInTheDocument();

  resume.mockRejectedValue(failure(403));
  jd.mockRejectedValue(failure(403));
  await act(async () => { await client.invalidateQueries(); });
  await screen.findByText('简历读取失败');
  await screen.findByText('岗位 JD 读取失败');
  expect(screen.queryByText('已授权简历.pdf')).not.toBeInTheDocument();
  expect(screen.queryByText('已授权岗位')).not.toBeInTheDocument();
  await waitFor(() => expect(revoke).toHaveBeenCalledWith('blob:authorized'));
  expect(preview).toHaveBeenCalledTimes(1);
});
