import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { InterviewFeedbackModal } from './InterviewFeedbackModal';
import { interviewApi, type FeedbackView, type Interview } from './api';

vi.mock('../auth/session',()=>({useSession:()=>({actor:{userId:'author'}})}));
vi.mock('./api',()=>({interviewApi:{feedbackByAuthor:vi.fn(),feedback:vi.fn()}}));
const existing={feedback:{id:'feedback',authorUserId:'author',status:'DRAFT'},currentVersion:{verdict:'FAIL',shortComment:'已保存的草稿意见',changeReason:'原草稿依据',feedbackOccurredAt:'2026-09-07T17:59:00Z'}} as FeedbackView;
function mount(inline=false,interviewType:'INTERNAL'|'EXTERNAL'='INTERNAL') {
  render(<ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}>
    <InterviewFeedbackModal inline={inline} value={{id:'interview',interviewType,initialComment:'排期时记录的文字'} as Interview} onClose={vi.fn()} onSaved={vi.fn().mockResolvedValue(undefined)}/>
  </QueryClientProvider></App></ConfigProvider>);
}
describe('formal interview feedback',()=>{
  beforeEach(()=>{cleanup();vi.resetAllMocks();});
  it('converts legacy saved content into a formal submission without offering a draft choice',async()=>{
    vi.mocked(interviewApi.feedbackByAuthor).mockResolvedValue(existing);
    vi.mocked(interviewApi.feedback).mockResolvedValue(existing);
    mount();
    expect(await screen.findByLabelText('面评')).toHaveValue('已保存的草稿意见');
    expect(screen.getByText('实际面试官仅作记录')).toBeInTheDocument();
    expect(screen.getByText('提交人按当前登录账号记录。',{exact:false})).toBeInTheDocument();
    expect(screen.queryByLabelText('提交依据')).not.toBeInTheDocument();
    expect(screen.getByText('不通过')).toBeInTheDocument();
    expect(screen.queryByLabelText('保存方式')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button',{name:'正式提交面评'}));
    await waitFor(()=>expect(interviewApi.feedback).toHaveBeenCalledWith('interview',expect.objectContaining({
      verdict:'FAIL',status:'SUBMITTED',changeReason:'正式提交面评',shortComment:'已保存的草稿意见',feedbackOccurredAt:'2026-09-07T17:59:00Z',
    }),expect.any(String)));
  });
  it.each(['', '   '])('allows optional feedback text (%j)',async comment=>{
    vi.mocked(interviewApi.feedbackByAuthor).mockResolvedValue(null);
    mount(true);
    fireEvent.change(await screen.findByLabelText('面评'),{target:{value:comment}});
    fireEvent.click(screen.getByRole('button',{name:'正式提交面评'}));
    await waitFor(()=>expect(interviewApi.feedback).toHaveBeenCalled());
    expect(vi.mocked(interviewApi.feedback).mock.calls[0]?.[1]).toMatchObject({shortComment:comment,status:'SUBMITTED'});
  });
  it('restores scheduling text for an external label without requiring a client feedback source',async()=>{
    vi.mocked(interviewApi.feedbackByAuthor).mockResolvedValue(null);
    mount(true,'EXTERNAL');
    expect(await screen.findByLabelText('面评')).toHaveValue('排期时记录的文字');
    expect(screen.queryByLabelText('甲方反馈实际来源')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button',{name:'正式提交面评'}));
    await waitFor(()=>expect(interviewApi.feedback).toHaveBeenCalled());
    expect(vi.mocked(interviewApi.feedback).mock.calls[0]?.[1]).toMatchObject({shortComment:'排期时记录的文字'});
  });
  it('blocks editing after a failed saved-feedback read and allows recovery',async()=>{
    vi.mocked(interviewApi.feedbackByAuthor).mockRejectedValueOnce(new Error('读取失败')).mockResolvedValue(existing);
    mount();
    expect(await screen.findByText('已保存面评读取失败，读取成功后才能编辑。')).toBeInTheDocument();
    expect(screen.queryByRole('button',{name:'正式提交面评'})).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button',{name:'重试面评'}));
    expect(await screen.findByLabelText('面评')).toHaveValue('已保存的草稿意见');
    expect(interviewApi.feedback).not.toHaveBeenCalled();
  });
  it('keeps formally submitted feedback read-only without version or correction controls',async()=>{
    vi.mocked(interviewApi.feedbackByAuthor).mockResolvedValue({...existing,feedback:{...existing.feedback,status:'SUBMITTED'}});
    mount();
    expect(await screen.findByText('面评已正式提交')).toBeInTheDocument();
    expect(screen.getByText('正式提交后不再支持修改，也不提供版本入口。')).toBeInTheDocument();
    expect(screen.queryByLabelText('面评')).not.toBeInTheDocument();
    expect(screen.queryByRole('button',{name:'正式提交面评'})).not.toBeInTheDocument();
  });
});
