import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { afterEach, expect, it, vi } from 'vitest';
import { talentApi, type IntakeBatch } from './api';
import { BatchParseNotifications } from './BatchParseNotifications';
import { watchParsingBatch } from './batchParseWatch';

vi.mock('../auth/session',()=>({useSession:()=>({actor:{userId:'hr-1'}})}));

const batch:IntakeBatch={id:'batch-1',sourceChannelId:'source-1',sourceRecruiterUserId:'hr-1',defaultOwnerUserId:'hr-1',ownerDepartmentId:'dep-1',status:'COMPLETED',totalCount:2,succeededCount:2,failedCount:0,completedCount:2,processingCount:0,parsingCount:0,parsedCount:2,attentionCount:0,ignoredCount:0,createdAt:'2026-09-09T01:00:00Z'};
function Location(){const location=useLocation();return <span data-testid="location">{location.pathname+location.search}</span>;}
function mount(){return render(<MemoryRouter initialEntries={['/talents']}><ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><BatchParseNotifications/><Location/></QueryClientProvider></App></ConfigProvider></MemoryRouter>);}

afterEach(()=>{cleanup();vi.restoreAllMocks();sessionStorage.clear();});

it('解析完成即提示一次，即使默认评分仍在处理中',async()=>{
  vi.spyOn(talentApi,'batch').mockResolvedValue({...batch,completedCount:1,processingCount:1});
  mount();
  watchParsingBatch('hr-1',batch.id);
  expect(await screen.findByText('本批简历解析完成')).toBeVisible();
  expect(screen.getByText('仍有 1 份正在入库或评分，请在解析校验查看进度。')).toBeVisible();
  expect(screen.queryByText(/可在简历列表继续筛选/)).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button',{name:'查看解析校验'}));
  expect(screen.getByTestId('location')).toHaveTextContent('/talents?view=pool&intake=1&batchId=batch-1');
  expect(sessionStorage.getItem('hr-ai:parsing-batches:hr-1')).toBe('[]');
});

it('解析结束但有待核验文件时提示进入详情处理',async()=>{
  vi.spyOn(talentApi,'batch').mockResolvedValue({...batch,completedCount:1,parsedCount:1,attentionCount:1});
  mount();
  watchParsingBatch('hr-1',batch.id);
  expect(await screen.findByText('本批简历解析结束，待处理')).toBeVisible();
  expect(screen.getByText('有 1 份需要核验或重试，请在解析校验查看。')).toBeVisible();
});

it('刷新后从会话中的任务 ID 恢复解析完成提醒',async()=>{
  sessionStorage.setItem('hr-ai:parsing-batches:hr-1',JSON.stringify([batch.id]));
  vi.spyOn(talentApi,'batch').mockResolvedValue(batch);
  mount();
  expect(await screen.findByText('本批简历解析完成')).toBeVisible();
  expect(screen.getByText('已入库 2 份，可在简历列表继续筛选。')).toBeVisible();
  expect(talentApi.batch).toHaveBeenCalledWith(batch.id);
});
