import { App, ConfigProvider } from 'antd';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { ApiProblemError } from '../api/problem';
import { talentApi, type ResumeIntake } from './api';
import { IntakeOriginalResume } from './IntakeOriginalResume';

const receipt = { id: 'receipt-a', originalFileName: '本次收件.pdf', mediaType: 'application/pdf', scanStatus: 'CLEAN', status: 'IDENTITY_REVIEW_REQUIRED', resumeVersionId: null } as ResumeIntake;
const create = vi.fn(() => 'blob:receipt-a');
const revoke = vi.fn();
function renderReceipt(value = receipt) {
  return render(<ConfigProvider theme={{ token: { motion: false } }}><App><IntakeOriginalResume value={value} /></App></ConfigProvider>);
}
beforeEach(() => {
  create.mockClear(); revoke.mockClear();
  vi.stubGlobal('URL', class extends URL { static createObjectURL = create; static revokeObjectURL = revoke; });
});
afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.unstubAllGlobals(); });

it.each(['IDENTITY_REVIEW_REQUIRED', 'FAILED_RETRYABLE', 'COMPLETED'] as const)('按收件 ID 读取 %s 原文件，关闭释放预览并重新鉴权下载', async status => {
  const preview = vi.spyOn(talentApi, 'previewIntake').mockResolvedValue(new Blob(['%PDF'], { type: 'application/pdf' }));
  const download = vi.spyOn(talentApi, 'downloadIntake').mockResolvedValue();
  renderReceipt({ ...receipt, status, resumeVersionId: status === 'COMPLETED' ? 'existing-version' : null });
  expect(preview).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: '查看原始简历' }));
  expect(await screen.findByTitle('本次收件.pdf 原始简历预览')).toHaveAttribute('src', 'blob:receipt-a');
  expect(preview).toHaveBeenCalledExactlyOnceWith('receipt-a');
  fireEvent.click(screen.getByRole('button', { name: '下载原文件' }));
  await waitFor(() => expect(download).toHaveBeenCalledExactlyOnceWith('receipt-a', '本次收件.pdf'));
  fireEvent.click(screen.getByRole('button', { name: 'Close' }));
  await waitFor(() => expect(revoke).toHaveBeenCalledExactlyOnceWith('blob:receipt-a'));
});

it('读取失败提供原因和重试，不显示旧的预览内容', async () => {
  const failure = new ApiProblemError({ status: 403, title: '拒绝读取', detail: '无权查看这份收件', code: 'FORBIDDEN', recoverable: false });
  const preview = vi.spyOn(talentApi, 'previewIntake').mockRejectedValueOnce(failure).mockResolvedValue(new Blob(['%PDF'], { type: 'application/pdf' }));
  renderReceipt();
  fireEvent.click(screen.getByRole('button', { name: '查看原始简历' }));
  expect(await screen.findByText('无权查看这份收件')).toBeVisible();
  expect(screen.queryByTitle('本次收件.pdf 原始简历预览')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '重试预览' }));
  await screen.findByTitle('本次收件.pdf 原始简历预览');
  expect(preview).toHaveBeenCalledTimes(2);
});

it.each([
  { scanStatus: 'MALICIOUS', status: 'QUARANTINED' },
  { scanStatus: 'UNAVAILABLE', status: 'PENDING_IMPORT' },
  { scanStatus: 'CLEAN', status: 'REJECTED' },
] as const)('禁止读取未通过扫描、隔离或拒绝的收件 %j', states => {
  const preview = vi.spyOn(talentApi, 'previewIntake');
  renderReceipt({ ...receipt, ...states });
  const button = screen.getByRole('button', { name: '查看原始简历' });
  expect(button).toBeDisabled();
  fireEvent.click(button);
  expect(preview).not.toHaveBeenCalled();
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
});

it('Word 文件保留原文件下载，并说明在线预览限制', async () => {
  const preview = vi.spyOn(talentApi, 'previewIntake');
  const download = vi.spyOn(talentApi, 'downloadIntake').mockResolvedValue();
  renderReceipt({ ...receipt, originalFileName: '本次收件.docx', mediaType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' });
  fireEvent.click(screen.getByRole('button', { name: '查看原始简历' }));
  expect(await screen.findByText('该文件格式暂不支持在线预览，请下载原文件查看。')).toBeVisible();
  expect(preview).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: '下载原文件' }));
  await waitFor(() => expect(download).toHaveBeenCalledWith('receipt-a', '本次收件.docx'));
});

it('关闭后忽略迟到的文件响应，安全状态收窄会释放已打开的预览', async () => {
  let finish!: (blob: Blob) => void;
  const preview = vi.spyOn(talentApi, 'previewIntake').mockReturnValueOnce(new Promise(resolve => { finish = resolve; })).mockResolvedValue(new Blob(['%PDF'], { type: 'application/pdf' }));
  const { rerender } = renderReceipt();
  fireEvent.click(screen.getByRole('button', { name: '查看原始简历' }));
  await waitFor(() => expect(preview).toHaveBeenCalledTimes(1));
  fireEvent.click(screen.getByRole('button', { name: 'Close' }));
  await act(async () => { finish(new Blob(['%PDF'], { type: 'application/pdf' })); await Promise.resolve(); });
  expect(create).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: '查看原始简历' }));
  await screen.findByTitle('本次收件.pdf 原始简历预览');
  rerender(<ConfigProvider theme={{ token: { motion: false } }}><App><IntakeOriginalResume value={{ ...receipt, scanStatus: 'MALICIOUS', status: 'QUARANTINED' }} /></App></ConfigProvider>);
  await waitFor(() => expect(revoke).toHaveBeenCalledWith('blob:receipt-a'));
  expect(screen.queryByTitle('本次收件.pdf 原始简历预览')).not.toBeInTheDocument();
});
