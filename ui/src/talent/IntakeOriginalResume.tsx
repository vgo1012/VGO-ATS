import { Button, Empty, Spin, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { ApiProblemError } from '../api/problem';
import { FeedbackAlert } from '../components/FeedbackAlert';
import { FileDownloadButton } from '../components/FileDownloadButton';
import { WorkspaceDialog } from '../components/WorkspaceDialog';
import { talentApi, type ResumeIntake } from './api';

export function IntakeOriginalResume({ value }: { value: ResumeIntake }) {
  const [open, setOpen] = useState(false);
  const blocked = value.status === 'QUARANTINED' || value.status === 'REJECTED';
  const available = value.scanStatus === 'CLEAN' && !blocked;
  return <>
    <Button size="small" disabled={!available} title={!available ? '文件未通过安全扫描或已隔离、拒绝，无法查看原始简历' : undefined} onClick={() => setOpen(true)}>查看原始简历</Button>
    <WorkspaceDialog open={open && available} onCancel={() => setOpen(false)} title="原始简历" size="wide" className="interview-material-dialog intake-original-dialog" resizeSurface footer={null} destroyOnHidden>
      {open && available ? <OriginalFile key={value.id} value={value} /> : null}
    </WorkspaceDialog>
  </>;
}

function OriginalFile({ value }: { value: ResumeIntake }) {
  const [preview, setPreview] = useState<{ url: string; type: string }>();
  const [error, setError] = useState<unknown>();
  const [attempt, setAttempt] = useState(0);
  const supported = value.mediaType === 'application/pdf';
  useEffect(() => {
    if (!supported) return;
    let active = true;
    let objectUrl: string | undefined;
    void talentApi.previewIntake(value.id).then(blob => {
      if (!active) return;
      objectUrl = URL.createObjectURL(blob);
      setPreview({ url: objectUrl, type: blob.type });
    }).catch(failure => { if (active) setError(failure); });
    return () => { active = false; if (objectUrl) URL.revokeObjectURL(objectUrl); };
  }, [value.id, supported, attempt]);
  return <section className="interview-material-pane" style={{ flex: 1 }} aria-label="收件原始文件">
    <header><Typography.Text strong>{value.originalFileName}</Typography.Text><FileDownloadButton size="small" download={() => talentApi.downloadIntake(value.id, value.originalFileName)}>下载原文件</FileDownloadButton></header>
    {error ? <FeedbackAlert occurrence={error} type="error" title="原始简历读取失败" description={error instanceof ApiProblemError ? error.problem.detail : '请检查网络后重试。'} action={<Button onClick={() => { setError(undefined); setPreview(undefined); setAttempt(current => current + 1); }}>重试预览</Button>} />
      : !supported || preview && preview.type !== 'application/pdf' ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="该文件格式暂不支持在线预览，请下载原文件查看。" />
      : !preview ? <div className="interview-material-loading" role="status"><Spin /><span>正在加载原始简历…</span></div>
      : <iframe className="interview-resume-preview" src={preview.url} title={`${value.originalFileName} 原始简历预览`} />}
  </section>;
}
