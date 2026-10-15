import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { JdResumeSources } from './JdResumeSources';
import { mailSourceApi } from './mailSourceApi';

vi.mock('./mailSourceApi', () => ({ mailSourceApi: { sources: vi.fn(), countSources: vi.fn(), message: vi.fn() } }));
afterEach(cleanup);
beforeEach(() => { vi.clearAllMocks(); vi.mocked(mailSourceApi.countSources).mockResolvedValue(1); });
function setup() {
  return render(<ConfigProvider><App><QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <JdResumeSources jdVersionId="jd-v1" />
  </QueryClientProvider></App></ConfigProvider>);
}
it('shows exact candidate and version association, and renders source body as plain text', async () => {
  vi.mocked(mailSourceApi.sources).mockResolvedValue({ items: [{ id: 's1', mailSourceId: 'm1', candidateId: 'c1', candidateName: '合成候选人', resumeVersionId: 'rv1', originalFileName: '原文.pdf', contentSha256: 'a'.repeat(64), createTime: '2026-09-07T10:00:00Z' }], nextCursor: null, hasMore: false });
  vi.mocked(mailSourceApi.message).mockResolvedValue({ id: 'm1', jdVersionId: 'jd-v1', sourceMessageId: '<m1@test>', senderEmail: 'sender@test', recipientEmail: 'hr@test', subject: '模拟来件', plainBody: '<script>do not execute</script>原始 JD', deliveryType: 'MAIL_SIMULATION', createId: 'hr1', createTime: '2026-09-07T10:00:00Z' });
  setup();
  expect(await screen.findByRole('link', { name: '合成候选人' })).toHaveAttribute('href', '/talents?candidate=c1');
  fireEvent.click(screen.getByRole('button', { name: '查看模拟邮件' }));
  expect(await screen.findByLabelText('原始邮件正文')).toHaveValue('<script>do not execute</script>原始 JD');
  expect(mailSourceApi.message).toHaveBeenCalledWith('m1');
  expect(screen.getByText('条/共1条记录')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '下一页来件' })).toBeDisabled();
});
it('keeps the empty state explicit', async () => {
  vi.mocked(mailSourceApi.sources).mockResolvedValue({ items: [], nextCursor: null, hasMore: false });
  setup();
  expect(await screen.findByText('此版本尚无已登记的来件简历')).toBeInTheDocument();
});

it('修改每页条数后重置来件游标并请求新数量', async () => {
  vi.mocked(mailSourceApi.countSources).mockResolvedValue(21);
  vi.mocked(mailSourceApi.sources).mockImplementation((_id, cursor) => Promise.resolve({ items: [], hasMore: !cursor, nextCursor: cursor ? null : 'next' }));
  setup();
  await waitFor(() => expect(screen.getByRole('button', { name: '下一页来件' })).toBeEnabled());
  fireEvent.click(screen.getByRole('button', { name: '下一页来件' }));
  await waitFor(() => expect(screen.getByRole('button', { name: '上一页来件' })).toBeEnabled());
  const size = screen.getByRole('spinbutton', { name: '每页条数' });
  fireEvent.change(size, { target: { value: '30' } }); fireEvent.blur(size);
  await waitFor(() => expect(mailSourceApi.sources).toHaveBeenLastCalledWith('jd-v1', undefined, 30));
  expect(screen.getByRole('button', { name: '上一页来件' })).toBeDisabled();
});
