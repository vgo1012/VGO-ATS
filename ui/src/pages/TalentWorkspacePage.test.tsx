import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { useState } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
vi.mock('../demand/JdSelector',()=>({JdSelector:({onChange,disabled}:{onChange:(jd:unknown)=>void;disabled?:boolean})=> <button disabled={disabled} onClick={()=>onChange({id:'jd',currentVersionNo:1})}>选择测试岗位</button>}));
import { talentApi, type ResumeIntake } from '../talent/api';
import { demandApi } from '../demand/api';
afterEach(()=>{cleanup();vi.restoreAllMocks();});
import { UploadDrawer } from '../talent/UploadDrawer';
import type { SourceChannel } from '../talent/api';

const channel: SourceChannel = {
  id: 'channel-1',
  code: 'MANUAL',
  name: '手工上传',
  channelType: 'MANUAL',
  status: 'ACTIVE',
  version: 0,
  updatedAt: '2026-09-03T10:00:00Z',
};

function Harness({onSaved=()=>Promise.resolve(),onReview}:{onSaved?:(hasFailures:boolean)=>Promise<unknown>;onReview?:()=>void}={}) {
  const [open, setOpen] = useState(true);
  return <>
    <button onClick={() => setOpen(true)}>重新打开</button>
    <UploadDrawer
      open={open}
      channels={[channel]}
      onClose={() => setOpen(false)}
      onSaved={onSaved}
      {...(onReview?{onReview}:{})}
    />
  </>;
}

describe('UploadDrawer', () => {
  it('clears a previous upload selection when the drawer is reopened', async () => {
    const { container } = render(
      <ConfigProvider>
        <App>
          <QueryClientProvider client={new QueryClient()}>
            <Harness />
          </QueryClientProvider>
        </App>
      </ConfigProvider>,
    );

    const input = container.ownerDocument.querySelector<HTMLInputElement>('input[type="file"]');
    expect(input).not.toBeNull();
    const file = new File(['%PDF-1.4'], 'integration-candidate.pdf', { type: 'application/pdf' });
    fireEvent.change(input!, { target: { files: [file] } });
    expect(await screen.findByText('integration-candidate.pdf')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Close' }));
    fireEvent.click(screen.getByRole('button', { name: '重新打开' }));

    expect(screen.queryByText('integration-candidate.pdf')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '开始上传' })).toBeDisabled();
    expect(screen.getByText('关联 JD（必选）')).toBeInTheDocument();
  }, 10_000);

  it('rejects unsupported, empty, and oversized resume files before adding them', async () => {
    const { container } = render(
      <ConfigProvider>
        <App>
          <QueryClientProvider client={new QueryClient()}>
            <Harness />
          </QueryClientProvider>
        </App>
      </ConfigProvider>,
    );
    const input = container.ownerDocument.querySelector<HTMLInputElement>('input[type="file"]');
    expect(input).not.toBeNull();
    const oversized = new File(['oversized'], 'oversized.pdf', { type: 'application/pdf' });
    Object.defineProperty(oversized, 'size', { value: 10 * 1024 * 1024 + 1 });

    fireEvent.change(input!, { target: { files: [
      new File(['invalid'], 'notes.txt', { type: 'text/plain' }),
      new File([], 'empty.docx', { type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' }),
      oversized,
    ] } });

    expect(await screen.findByText('notes.txt：仅支持 PDF、DOCX 或 DOC 格式的简历文件。')).toBeVisible();
    expect(await screen.findByText('empty.docx：简历文件不能为空。')).toBeVisible();
    expect(await screen.findByText('oversized.pdf：每份简历不能超过 10 MB。')).toBeVisible();
    expect(screen.queryByText('已选择 1 份简历')).not.toBeInTheDocument();
    expect(screen.queryByText('已选择 2 份简历')).not.toBeInTheDocument();
    expect(screen.queryByText('已选择 3 份简历')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '开始上传' })).toBeDisabled();
  });
});

it('uploads to the selected JD and retains only failed files for retry',async()=>{
  const create=vi.spyOn(talentApi,'createBatch').mockResolvedValue({id:'batch'} as Awaited<ReturnType<typeof talentApi.createBatch>>);
  vi.spyOn(demandApi,'jdVersions').mockResolvedValue({items:[{id:'jd-version',versionNo:1}],hasMore:false,nextCursor:null} as Awaited<ReturnType<typeof demandApi.jdVersions>>);
  const upload=vi.spyOn(talentApi,'upload').mockImplementation((_,file)=>file.name==='failed.pdf'?Promise.reject(new Error('network')):Promise.resolve({id:'intake',status:'PARSING',importStatus:'PROCESSING',scanStatus:'CLEAN',processingStatus:'QUEUED',duplicateContent:true} as ResumeIntake));
  vi.spyOn(talentApi,'completeBatch').mockResolvedValue({id:'batch'} as Awaited<ReturnType<typeof talentApi.completeBatch>>);
  const parse=vi.spyOn(talentApi,'parseResume');const importIntake=vi.spyOn(talentApi,'importIntake');
  const read=vi.spyOn(talentApi,'intake');const saved=vi.fn(()=>Promise.resolve());
  render(<ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><UploadDrawer open channels={[channel]} onClose={()=>undefined} onSaved={saved}/></QueryClientProvider></App></ConfigProvider>);
  fireEvent.mouseDown(screen.getByLabelText('上传来源渠道'));
  fireEvent.click(await screen.findByText('手工上传'));
  fireEvent.change(document.querySelector('input[type="file"]')!,{target:{files:[new File(['ok'],'good.pdf',{type:'application/pdf'}),new File(['fail'],'failed.pdf',{type:'application/pdf'})]}});
  expect(screen.getByRole('button',{name:'开始上传'})).toBeDisabled();
  fireEvent.click(screen.getByRole('button',{name:'选择测试岗位'}));
  await waitFor(()=>expect(screen.getByRole('button',{name:'开始上传'})).toBeEnabled());
  fireEvent.click(screen.getByRole('button',{name:'开始上传'}));
  await waitFor(()=>expect(saved).toHaveBeenCalledTimes(1));
  expect(screen.getByText('good.pdf：上传成功')).toBeVisible();
  expect(screen.getByRole('group', { name: '上传成功 1，上传失败 1' })).toBeVisible();
  expect(screen.queryByText(/继续后台处理|已复用已有简历|已合并|无需重复解析/)).not.toBeInTheDocument();
  expect(parse).not.toHaveBeenCalled();expect(importIntake).not.toHaveBeenCalled();expect(read).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button',{name:'开始上传'}));
  await waitFor(()=>expect(saved).toHaveBeenCalledTimes(2));
  expect(upload.mock.calls.map(call=>call[1].name)).toEqual(['good.pdf','failed.pdf','failed.pdf']);
  expect(create).toHaveBeenCalledTimes(1);
  expect(create).toHaveBeenCalledWith('channel-1','jd-version',expect.any(String));
  expect(upload.mock.calls[1]?.[2]).toEqual(expect.any(String));
  expect(upload.mock.calls[2]?.[2]).toBe(upload.mock.calls[1]?.[2]);
  expect(talentApi.completeBatch).not.toHaveBeenCalled();
});

it('treats a queued upload as successful without a browser import request',async()=>{
  vi.spyOn(talentApi,'createBatch').mockResolvedValue({id:'batch'} as Awaited<ReturnType<typeof talentApi.createBatch>>);
  vi.spyOn(demandApi,'jdVersions').mockResolvedValue({items:[{id:'jd-version',versionNo:1}],hasMore:false,nextCursor:null} as Awaited<ReturnType<typeof demandApi.jdVersions>>);
  const upload=vi.spyOn(talentApi,'upload').mockResolvedValue({id:'intake',status:'PARSING',importStatus:'PROCESSING',scanStatus:'CLEAN',processingStatus:'QUEUED'} as ResumeIntake);
  const read=vi.spyOn(talentApi,'intake').mockRejectedValue(new Error('network'));
  const importIntake=vi.spyOn(talentApi,'importIntake').mockRejectedValue(new Error('network'));
  vi.spyOn(talentApi,'completeBatch').mockResolvedValue({id:'batch'} as Awaited<ReturnType<typeof talentApi.completeBatch>>);
  const saved=vi.fn(()=>Promise.resolve());
  render(<ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><UploadDrawer open channels={[channel]} onClose={()=>undefined} onSaved={saved}/></QueryClientProvider></App></ConfigProvider>);
  fireEvent.mouseDown(screen.getByLabelText('上传来源渠道'));
  fireEvent.click(await screen.findByText('手工上传'));
  fireEvent.change(document.querySelector('input[type="file"]')!,{target:{files:[new File(['ok'],'accepted.pdf',{type:'application/pdf'})]}});
  fireEvent.click(screen.getByRole('button',{name:'选择测试岗位'}));
  await waitFor(()=>expect(screen.getByRole('button',{name:'开始上传'})).toBeEnabled());
  fireEvent.click(screen.getByRole('button',{name:'开始上传'}));
  await waitFor(()=>expect(saved).toHaveBeenCalledWith(false,'batch',true));
  expect(screen.getByText('accepted.pdf：上传成功')).toBeVisible();
  expect(screen.getByRole('group',{name:'上传成功 1，上传失败 0'})).toBeVisible();
  expect(screen.getByRole('button',{name:'开始上传'})).toBeDisabled();
  expect(upload).toHaveBeenCalledTimes(1);
  expect(read).not.toHaveBeenCalled();
  expect(importIntake).not.toHaveBeenCalled();
});

it.each([
  ['MALICIOUS','安全校验未通过，文件已隔离'],
  ['UNAVAILABLE','安全扫描暂不可用'],
] as const)('counts %s scan results as upload failures without reuploading accepted files',async(scanStatus,reason)=>{
  vi.spyOn(talentApi,'createBatch').mockResolvedValue({id:'batch'} as Awaited<ReturnType<typeof talentApi.createBatch>>);
  vi.spyOn(demandApi,'jdVersions').mockResolvedValue({items:[{id:'jd-version',versionNo:1}],hasMore:false,nextCursor:null} as Awaited<ReturnType<typeof demandApi.jdVersions>>);
  const upload=vi.spyOn(talentApi,'upload').mockResolvedValue({id:'intake',scanStatus} as ResumeIntake);
  const importIntake=vi.spyOn(talentApi,'importIntake');
  const complete=vi.spyOn(talentApi,'completeBatch').mockResolvedValue({id:'batch'} as Awaited<ReturnType<typeof talentApi.completeBatch>>);
  const saved=vi.fn(()=>Promise.resolve());
  render(<ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><UploadDrawer open channels={[channel]} onClose={()=>undefined} onSaved={saved}/></QueryClientProvider></App></ConfigProvider>);
  fireEvent.mouseDown(screen.getByLabelText('上传来源渠道'));
  fireEvent.click(await screen.findByText('手工上传'));
  fireEvent.change(document.querySelector('input[type="file"]')!,{target:{files:[new File(['file'],'scan.pdf',{type:'application/pdf'})]}});
  fireEvent.click(screen.getByRole('button',{name:'选择测试岗位'}));
  await waitFor(()=>expect(screen.getByRole('button',{name:'开始上传'})).toBeEnabled());
  fireEvent.click(screen.getByRole('button',{name:'开始上传'}));
  expect(await screen.findByText(`scan.pdf：已接收，${reason}。请到解析校验处理，无需重新上传。`)).toBeVisible();
  expect(screen.getByRole('group',{name:'上传成功 0，上传失败 1'})).toBeVisible();
  expect(screen.getByRole('button',{name:'开始上传'})).toBeDisabled();
  expect(upload).toHaveBeenCalledTimes(1);
  expect(importIntake).not.toHaveBeenCalled();
  expect(complete).toHaveBeenCalledWith('batch');
  expect(saved).toHaveBeenCalledWith(true,'batch',true);
});

it('shows a terminal success count and opens the matching intake review',async()=>{
  vi.spyOn(talentApi,'createBatch').mockResolvedValue({id:'batch'} as Awaited<ReturnType<typeof talentApi.createBatch>>);
  vi.spyOn(demandApi,'jdVersions').mockResolvedValue({items:[{id:'jd-version',versionNo:1}],hasMore:false,nextCursor:null} as Awaited<ReturnType<typeof demandApi.jdVersions>>);
  vi.spyOn(talentApi,'upload').mockResolvedValue({id:'intake'} as ResumeIntake);
  vi.spyOn(talentApi,'completeBatch').mockResolvedValue({id:'batch'} as Awaited<ReturnType<typeof talentApi.completeBatch>>);
  const review=vi.fn();
  render(<ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><Harness onReview={review}/></QueryClientProvider></App></ConfigProvider>);
  fireEvent.mouseDown(screen.getByLabelText('上传来源渠道'));
  fireEvent.click(await screen.findByText('手工上传'));
  fireEvent.change(document.querySelector('input[type="file"]')!,{target:{files:[new File(['ok'],'imported.pdf',{type:'application/pdf'})]}});
  fireEvent.click(screen.getByRole('button',{name:'选择测试岗位'}));
  await waitFor(()=>expect(screen.getByRole('button',{name:'开始上传'})).toBeEnabled());
  fireEvent.click(screen.getByRole('button',{name:'开始上传'}));
  expect(await screen.findByRole('group', { name: '上传成功 1，上传失败 0' })).toBeVisible();
  fireEvent.click(screen.getByRole('button',{name:'查看解析校验'}));
  expect(review).toHaveBeenCalledExactlyOnceWith('batch');
});

async function prepareRecoveryUpload() {
  const create=vi.spyOn(talentApi,'createBatch').mockResolvedValue({id:'batch-recovery'} as Awaited<ReturnType<typeof talentApi.createBatch>>);
  vi.spyOn(demandApi,'jdVersions').mockResolvedValue({items:[{id:'jd-version',versionNo:1}],hasMore:false,nextCursor:null} as Awaited<ReturnType<typeof demandApi.jdVersions>>);
  const uploaded={id:'intake-recovery',status:'PARSING',importStatus:'PROCESSING',scanStatus:'CLEAN',processingStatus:'QUEUED',jdVersionId:'jd-version'} as ResumeIntake;
  const upload=vi.spyOn(talentApi,'upload').mockResolvedValue(uploaded);
  const read=vi.spyOn(talentApi,'intake').mockResolvedValue(uploaded);
  const importIntake=vi.spyOn(talentApi,'importIntake');
  const complete=vi.spyOn(talentApi,'completeBatch').mockResolvedValue({id:'batch-recovery'} as Awaited<ReturnType<typeof talentApi.completeBatch>>);
  const saved=vi.fn(()=>Promise.resolve());
  render(<ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><Harness onSaved={saved}/></QueryClientProvider></App></ConfigProvider>);
  fireEvent.mouseDown(screen.getByLabelText('上传来源渠道'));
  fireEvent.click(await screen.findByText('手工上传'));
  fireEvent.change(document.querySelector('input[type="file"]')!,{target:{files:[new File(['resume'],'recovery.pdf',{type:'application/pdf'})]}});
  fireEvent.click(screen.getByRole('button',{name:'选择测试岗位'}));
  await waitFor(()=>expect(screen.getByRole('button',{name:'开始上传'})).toBeEnabled());
  return {create,upload,read,importIntake,complete,saved};
}

it('replays a lost batch creation response after closing and reopening with the same key',async()=>{
  const {create,upload,complete}=await prepareRecoveryUpload();
  create.mockRejectedValueOnce(new Error('batch response lost'));
  fireEvent.click(screen.getByRole('button',{name:'开始上传'}));
  await screen.findByText('batch response lost');
  expect(screen.getByRole('button',{name:'选择测试岗位'})).toBeDisabled();
  expect(screen.getByLabelText('上传来源渠道')).toBeDisabled();
  fireEvent.click(screen.getByRole('button',{name:'Close'}));
  fireEvent.click(screen.getByRole('button',{name:'重新打开'}));
  expect(screen.getByText('recovery.pdf')).toBeVisible();
  fireEvent.click(screen.getByRole('button',{name:'开始上传'}));
  await waitFor(()=>expect(complete).toHaveBeenCalledWith('batch-recovery'));
  expect(create).toHaveBeenCalledTimes(2);
  expect(create.mock.calls[1]).toEqual(create.mock.calls[0]);
  expect(create.mock.calls[0]?.[2]).toEqual(expect.any(String));
  expect(upload).toHaveBeenCalledTimes(1);
  await waitFor(()=>expect(screen.getByRole('button',{name:'开始上传'})).toBeDisabled());
  expect(screen.getByRole('button',{name:'选择测试岗位'})).toBeEnabled();
});

it('retries only batch completion and keeps queued files successful',async()=>{
  const {create,upload,read,importIntake,complete,saved}=await prepareRecoveryUpload();
  complete.mockRejectedValueOnce(new Error('completion response lost'));
  fireEvent.click(screen.getByRole('button',{name:'开始上传'}));
  await screen.findByText(/批次汇总未完成/);
  expect(screen.getByText('recovery.pdf：上传成功')).toBeVisible();
  fireEvent.click(screen.getByRole('button',{name:'重试完成批次'}));
  await waitFor(()=>expect(complete).toHaveBeenCalledTimes(2));
  await waitFor(()=>expect(screen.queryByText(/批次汇总未完成/)).not.toBeInTheDocument());
  expect(screen.getByText('recovery.pdf：上传成功')).toBeVisible();
  expect(create).toHaveBeenCalledTimes(1);
  expect(upload).toHaveBeenCalledTimes(1);
  expect(read).not.toHaveBeenCalled();
  expect(importIntake).not.toHaveBeenCalled();
  expect(saved).toHaveBeenLastCalledWith(false,'batch-recovery',true);
  expect(screen.getByRole('button',{name:'开始上传'})).toBeDisabled();
});

it('can finish a response-unknown batch after removing its last file',async()=>{
  const {create,upload,complete,saved}=await prepareRecoveryUpload();
  create.mockRejectedValueOnce(new Error('batch response lost'));
  fireEvent.click(screen.getByRole('button',{name:'开始上传'}));
  await screen.findByText('batch response lost');
  fireEvent.click(screen.getByTitle('Remove file'));
  fireEvent.click(await screen.findByRole('button',{name:'重试完成批次'}));
  await waitFor(()=>expect(complete).toHaveBeenCalledWith('batch-recovery'));
  expect(create.mock.calls[1]).toEqual(create.mock.calls[0]);
  expect(upload).not.toHaveBeenCalled();
  await waitFor(()=>expect(screen.getByRole('button',{name:'选择测试岗位'})).toBeEnabled());
  expect(screen.getByText('recovery.pdf：已停止重试，接收状态未确认，请到解析校验核对。')).toBeVisible();
  expect(saved).toHaveBeenLastCalledWith(true,'batch-recovery',true);
});
