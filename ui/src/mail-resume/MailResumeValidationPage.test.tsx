import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { mailResumeApi } from './api';
import { MailResumeValidationPage } from './MailResumeValidationPage';

vi.mock('./api',()=>({mailResumeApi:{reviews:vi.fn(),countReviews:vi.fn(),jds:vi.fn(),link:vi.fn(),run:vi.fn()}}));
afterEach(cleanup);
beforeEach(()=>{vi.clearAllMocks();vi.mocked(mailResumeApi.countReviews).mockResolvedValue(1);});

it('shows only server-provided review items and opens logical JD association',async()=>{
  vi.mocked(mailResumeApi.reviews).mockResolvedValue({items:[{id:'01JABCDEF23456789GHJKMNPQR',receivedAt:'2026-09-17T02:00:00Z',sender:'jobs@example.com',subject:'候选人简历',attachmentName:'【Java 开发_东莞_20k】张三.pdf',parsedJdCode:null,parsedJdTitle:'Java 开发',parsedRegion:'东莞',failureCode:'JD_EXACT_MATCH_NOT_FOUND',failureDetail:'岗位文本和区域未精确命中当前 JD',status:'REVIEW_REQUIRED',matchType:'REVIEW_REQUIRED',jdId:null,jdVersionId:null,intakeId:null,linkedByUserId:null,linkedAt:null,version:'0'}],hasMore:false,nextCursor:null});
  vi.mocked(mailResumeApi.jds).mockResolvedValue([]);
  const client=new QueryClient({defaultOptions:{queries:{retry:false}}});
  render(<ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={client}><MailResumeValidationPage/></QueryClientProvider></App></ConfigProvider>);
  expect(screen.queryByText(/这里展示未能严格匹配 JD/)).not.toBeInTheDocument();
  expect(screen.getByRole('button',{name:/查\s*询/})).toBeInTheDocument();
  expect(screen.getByRole('button',{name:/重\s*置/})).toBeInTheDocument();
  expect(await screen.findByText('【Java 开发_东莞_20k】张三.pdf')).toBeInTheDocument();
  expect(screen.getByText('待关联 JD')).toBeInTheDocument();
  expect(screen.getByText('岗位文本和区域未精确命中当前 JD')).toBeInTheDocument();
  expect(await screen.findByText('条/共1条记录')).toBeInTheDocument();
  screen.getByRole('button',{name:'关联 JD'}).click();
  expect(await screen.findByRole('dialog',{name:'人工关联当前 JD'})).toBeInTheDocument();
  expect(screen.getByText('关联后不可改绑')).toBeInTheDocument();
});

it('修改条数会清空邮件校验游标并请求指定数量', async () => {
  vi.mocked(mailResumeApi.countReviews).mockResolvedValue(21);
  vi.mocked(mailResumeApi.reviews).mockImplementation(cursor => Promise.resolve({ items: [], hasMore: !cursor, nextCursor: cursor ? null : 'next' }));
  render(<ConfigProvider><App><QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><MailResumeValidationPage/></QueryClientProvider></App></ConfigProvider>);
  await waitFor(() => expect(screen.getByRole('navigation', { name: '邮件简历校验分页' })).toHaveAttribute('aria-busy', 'false'), { timeout: 5000 });
  expect(screen.getByRole('button', { name: '下一页' })).toBeEnabled();
  fireEvent.click(screen.getByRole('button', { name: '下一页' }));
  await waitFor(() => expect(screen.getByRole('button', { name: '上一页' })).toBeEnabled());
  const size = screen.getByRole('spinbutton', { name: '每页条数' });
  fireEvent.change(size, { target: { value: '30' } }); fireEvent.blur(size);
  await waitFor(() => expect(mailResumeApi.reviews).toHaveBeenLastCalledWith(undefined, {}, 30));
  expect(screen.getByRole('button', { name: '上一页' })).toBeDisabled();
});
