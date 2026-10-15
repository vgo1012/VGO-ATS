import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, expect, it, vi } from 'vitest';
import { ReportingPage } from './ReportingPage';
import { reportingApi, type ReportView } from '../reporting/api';
import { ApiProblemError } from '../api/problem';
import dayjs from 'dayjs';

vi.mock('../demand/JdSelector', () => ({ JdSelector: () => null }));
vi.mock('../components/DateRangeFilter',()=>({DateRangeFilter:()=>null}));
vi.mock('../reporting/api', () => ({ reportingApi: { report: vi.fn() } }));
beforeEach(() => cleanup());

it('uses distinct parsed versions for coverage instead of repeated parse event counts', async () => {
  vi.mocked(reportingApi.report).mockResolvedValue({
    reportType:'ai-effect',from:'2026-09-01T00:00:00Z',to:'2026-09-08T00:00:00Z',dataUpdatedAt:'2026-09-07T00:00:00Z',projectionStatus:'READY',reconciliationUpdatedAt:null,definitions:[],
    points:[{eventType:'RESUME_INGESTED',eventCount:2,eventValue:2,firstOccurredAt:null,lastOccurredAt:null},{eventType:'RESUME_PARSED',eventCount:3,eventValue:3,firstOccurredAt:null,lastOccurredAt:null}],
    ...{parseCoverage:{resumeVersionCount:2,parsedResumeVersionCount:1}},
  });
  render(<QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><ReportingPage/></QueryClientProvider>);
  fireEvent.click(screen.getByText('AI 效果'));
  const title = await screen.findByText('解析覆盖率');
  expect(within(title.closest('.ant-card')!).getByText('50')).toBeInTheDocument();
  expect(screen.queryByText('150')).not.toBeInTheDocument();
});

const reportData:ReportView={reportType:'funnel',from:'2026-09-01T00:00:00Z',to:'2026-09-08T00:00:00Z',dataUpdatedAt:'2026-09-07T01:23:00Z',projectionStatus:'READY',reconciliationUpdatedAt:null,definitions:[{code:'resumes',name:'简历入库',category:'funnel',versionNo:1,definitionText:'按实际入库统计',eventType:'RESUME_INGESTED',effectiveFrom:'2026-09-01T00:00:00Z'}],points:[{eventType:'RESUME_INGESTED',eventCount:7,eventValue:7,firstOccurredAt:null,lastOccurredAt:null}]};
const projectionError=(code:string)=>new ApiProblemError({code,status:503,title:'统计暂不可用',detail:'等待统计更新',recoverable:true});
function mount(){
  const client=new QueryClient({defaultOptions:{queries:{retry:false}}});
  render(<QueryClientProvider client={client}><ReportingPage/></QueryClientProvider>);
  return client;
}

it.each([['METRIC_PROJECTION_INITIALIZING','统计准备中'],['METRIC_PROJECTION_DISABLED','统计暂未启用']])('首次%s显示状态并允许重试，不伪造零值',async(code,title)=>{
  vi.mocked(reportingApi.report).mockRejectedValueOnce(projectionError(code)).mockResolvedValue(reportData);
  const client=mount();
  expect(await screen.findByText(title)).toBeInTheDocument();
  expect(screen.queryByText('指标说明')).not.toBeInTheDocument();
  expect(screen.queryByText('0')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button',{name:/重\s*试/}));
  expect(await screen.findByText('指标说明')).toBeInTheDocument();
  client.clear();
});

it('延迟统计显示真实完成水位，刷新失败仍保留已有数字',async()=>{
  vi.mocked(reportingApi.report).mockResolvedValue({...reportData,projectionStatus:'LAGGING'});
  const client=mount();
  expect(await screen.findByText('统计更新延迟')).toBeInTheDocument();
  expect(screen.getByText(`更新于 ${dayjs(reportData.dataUpdatedAt).format('YYYY-MM-DD HH:mm')}`)).toBeInTheDocument();
  vi.mocked(reportingApi.report).mockRejectedValue(new Error('offline'));
  await act(async()=>{await client.refetchQueries({queryKey:['reporting','report']});});
  expect(await screen.findByRole('button',{name:/重\s*试/})).toBeInTheDocument();
  expect(screen.getByText('指标说明')).toBeInTheDocument();
  expect(screen.getAllByText('7')).toHaveLength(2);
  client.clear();
});

it('后台刷新进行中保留已有统计，完成后移除更新提示',async()=>{
  vi.mocked(reportingApi.report).mockResolvedValue(reportData);
  const client=mount();
  await screen.findByText('指标说明');
  let resolveRefresh!:(value:ReportView)=>void;
  vi.mocked(reportingApi.report).mockReturnValue(new Promise<ReportView>(resolve=>{resolveRefresh=resolve;}));
  let refreshing:Promise<void>;
  await act(async()=>{refreshing=client.refetchQueries({queryKey:['reporting','report']});await Promise.resolve();});
  expect(await screen.findByRole('status')).toHaveTextContent('正在更新统计');
  expect(screen.getAllByText('7')).toHaveLength(2);
  await act(async()=>{resolveRefresh(reportData);await refreshing;});
  await waitFor(()=>expect(screen.queryByText('正在更新统计，已加载结果仍可查看。')).not.toBeInTheDocument());
  client.clear();
});
