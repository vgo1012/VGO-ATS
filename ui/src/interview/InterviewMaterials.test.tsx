import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { WorkspaceDialog } from '../components/WorkspaceDialog';
import { InterviewMaterials, InterviewReference } from './InterviewMaterials';
import { interviewApi } from './api';

vi.mock('./api',()=>({interviewApi:{materials:vi.fn(),previewResume:vi.fn(),downloadResume:vi.fn()}}));
const material={interviewId:'interview',candidateId:'candidate',candidateName:'合成候选人',jdId:'jd',
  jdVersionId:'jd-v1',jdVersionNo:1,jdTitle:'运维工程师',jdRawText:'固定岗位原文',
  resumeVersionId:'resume-v2',resumeVersionNo:2,resumeFileName:'固定简历.pdf',resumeMediaType:'application/pdf',resumeSizeBytes:12};
function mount() {
  render(<ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}>
    <WorkspaceDialog size="form" title="面试事实与处理" open footer={null} onCancel={()=>undefined}>
      <span><InterviewReference id="interview" field="candidateName"/></span><span><InterviewReference id="interview" field="jdTitle"/></span>
      <InterviewMaterials id="interview"/>
    </WorkspaceDialog>
  </QueryClientProvider></App></ConfigProvider>);
}
describe('assigned interview materials',()=>{
  beforeEach(()=>{
    cleanup();vi.resetAllMocks();
    Object.defineProperty(HTMLElement.prototype,'scrollTo',{configurable:true,value:vi.fn()});
    Object.defineProperty(URL,'createObjectURL',{configurable:true,value:vi.fn(()=> 'blob:interview-resume')});
    Object.defineProperty(URL,'revokeObjectURL',{configurable:true,value:vi.fn()});
    vi.mocked(interviewApi.previewResume).mockResolvedValue(new Blob(['pdf'],{type:'application/pdf'}));
  });
  it('keeps materials behind one action and previews the frozen resume beside the JD',async()=>{
    vi.mocked(interviewApi.materials).mockResolvedValue(material);
    vi.mocked(interviewApi.downloadResume).mockResolvedValue();
    mount();
    expect(await screen.findByText('合成候选人')).toBeInTheDocument();
    expect(screen.queryByText('固定岗位原文')).not.toBeInTheDocument();
    expect(interviewApi.materials).toHaveBeenCalledExactlyOnceWith('interview');
    fireEvent.click(screen.getByRole('button',{name:'查看本次面试材料'}));
    const dialog=await screen.findByRole('dialog',{name:'本次面试材料'});
    expect(dialog.closest('.ant-modal')).toHaveClass('interview-material-dialog');
    expect(dialog.closest('.ant-modal')).toHaveStyle({width:'calc(100vw - 48px)'});
    expect(dialog).toHaveTextContent('本次简历 · 第 2 版');
    expect(dialog).toHaveTextContent('岗位 JD · 第 1 版');
    expect(dialog).toHaveTextContent('固定岗位原文');
    await waitFor(()=>expect(interviewApi.previewResume).toHaveBeenCalledExactlyOnceWith('interview'));
    expect(screen.getByTitle('固定简历.pdf 在线预览')).toHaveAttribute('src','blob:interview-resume');
    fireEvent.click(screen.getByRole('button',{name:'下载原文件'}));
    await waitFor(()=>expect(interviewApi.downloadResume).toHaveBeenCalledExactlyOnceWith('interview','固定简历.pdf'));
  });
  it('provides a recoverable error without exposing a download when authorization fails',async()=>{
    vi.mocked(interviewApi.materials).mockRejectedValueOnce(new Error('403')).mockResolvedValue(material);
    mount();
    expect(await screen.findAllByText('面试材料暂不可用')).toHaveLength(2);
    fireEvent.click(screen.getByRole('button',{name:'查看本次面试材料'}));
    expect(await screen.findByText(/面试材料读取失败/)).toBeInTheDocument();
    expect(screen.queryByRole('button',{name:'下载原文件'})).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button',{name:'重试材料'}));
    expect(await screen.findByText('固定岗位原文')).toBeInTheDocument();
  });
  it('keeps the original-file fallback for formats the browser cannot preview',async()=>{
    vi.mocked(interviewApi.materials).mockResolvedValue({...material,resumeFileName:'固定简历.docx',resumeMediaType:'application/vnd.openxmlformats-officedocument.wordprocessingml.document'});
    mount();
    fireEvent.click(await screen.findByRole('button',{name:'查看本次面试材料'}));
    expect(await screen.findByText('该文件格式暂不支持在线预览，请下载原文件查看。')).toBeInTheDocument();
    expect(interviewApi.previewResume).not.toHaveBeenCalled();
    expect(screen.getByRole('button',{name:'下载原文件'})).toBeInTheDocument();
  });
});
