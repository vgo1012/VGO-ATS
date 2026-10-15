import { useQuery } from '@tanstack/react-query';
import { Button, Empty, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { ApiProblemError } from '../api/problem';
import { FeedbackAlert } from '../components/FeedbackAlert';
import { FileDownloadButton } from '../components/FileDownloadButton';
import { WorkspaceDialog } from '../components/WorkspaceDialog';
import { demandApi } from '../demand/api';
import { talentApi } from '../talent/api';
import type { RecruitmentApplication } from './api';

const accessDenied = (error: unknown) => error instanceof ApiProblemError && [401, 403].includes(error.problem.status);

export function ApplicationMaterials({ application }: { application: RecruitmentApplication }) {
  const [open, setOpen] = useState(false);
  return <>
    <Button onClick={() => setOpen(true)}>查看简历与 JD</Button>
    <WorkspaceDialog open={open} onCancel={() => setOpen(false)} title="招聘材料" size="wide" width="calc(100vw - 48px)" className="interview-material-dialog" resizeSurface footer={null} destroyOnHidden>
      {open ? <ResumeJdMaterials key={application.id} resumeVersionId={application.resumeVersionId} jdVersionId={application.jdVersionId} /> : null}
    </WorkspaceDialog>
  </>;
}

export function ResumeJdMaterials({ resumeVersionId, jdVersionId }: { resumeVersionId: string | undefined; jdVersionId: string | undefined }) {
  const resume = useQuery({ queryKey: ['material-resume-reference', resumeVersionId], queryFn: async () => {
    const versions = await talentApi.resumeVersionReferences([resumeVersionId!]);
    const bound = versions.find(version => version.id === resumeVersionId);
    if (!bound) throw new ApiProblemError({status:403,title:'简历读取失败',detail:'所选简历版本暂不可用',code:'RESUME_VERSION_REFERENCE_UNAVAILABLE',recoverable:false});
    return bound;
  }, enabled: Boolean(resumeVersionId), retry: false });
  const jd = useQuery({ queryKey: ['demand', 'jd-version', jdVersionId], queryFn: () => demandApi.jdVersion(jdVersionId!), enabled: Boolean(jdVersionId), retry: false });
  const visibleResume = accessDenied(resume.error) ? undefined : resume.data;
  const visibleJd = accessDenied(jd.error) ? undefined : jd.data;
  const resumeUnavailable = Boolean(resume.error && !visibleResume);
  const jdUnavailable = Boolean(jd.error && !visibleJd);
  const [preview, setPreview] = useState<{url:string;type:string}>();
  const [fileError, setFileError] = useState(false);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    if (!visibleResume) return;
    let active = true;
    let objectUrl: string | undefined;
    void talentApi.previewResume(resumeVersionId!).then(blob => {
      if (!active) return;
      objectUrl = URL.createObjectURL(blob);
      setPreview({url:objectUrl,type:blob.type});
    }).catch(() => { if (active) setFileError(true); });
    return () => { active = false; if (objectUrl) URL.revokeObjectURL(objectUrl); };
  }, [resumeVersionId, visibleResume, attempt]);
  const supported = preview && ['application/pdf', 'text/plain'].includes(preview.type);
  return <div className="interview-material-viewer">
    <section className="interview-material-pane" aria-label="简历原文件">
      <header><div><Typography.Title level={4}>本次简历{visibleResume ? ` · 第 ${visibleResume.versionNo} 版` : ''}</Typography.Title><Typography.Text type="secondary">{visibleResume?.originalFileName}</Typography.Text></div>{visibleResume ? <FileDownloadButton size="small" download={() => talentApi.downloadResume(resumeVersionId!, visibleResume.originalFileName)}>下载原文件</FileDownloadButton> : null}</header>
      {resume.error && visibleResume ? <FeedbackAlert type="warning" title="简历信息刷新失败，继续显示上次已授权内容" action={<Button onClick={() => void resume.refetch()}>重试简历</Button>} /> : null}
      {!resumeVersionId ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无简历文件" /> : resumeUnavailable || fileError ? <FeedbackAlert type="error" title="简历读取失败" action={<Button onClick={() => { if (resumeUnavailable) void resume.refetch(); else { setFileError(false); setPreview(undefined); setAttempt(value => value + 1); } }}>重试简历</Button>} /> : !preview ? <p role="status">正在加载简历预览…</p> : supported ? <iframe className="interview-resume-preview" src={preview.url} title="本次推荐简历在线预览" /> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="该文件格式暂不支持在线预览，请下载原文件查看。" />}
    </section>
    <section className="interview-material-pane" aria-label="岗位 JD 原文">
      <header><div><Typography.Title level={4}>岗位 JD{visibleJd ? ` · 第 ${visibleJd.versionNo} 版` : ''}</Typography.Title><Typography.Text type="secondary">{visibleJd?.title}</Typography.Text></div></header>
      {jd.error && visibleJd ? <FeedbackAlert type="warning" title="岗位 JD 刷新失败，继续显示上次已授权内容" action={<Button onClick={() => void jd.refetch()}>重试 JD</Button>} /> : null}
      {!jdVersionId ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="未关联岗位 JD" /> : jdUnavailable ? <FeedbackAlert type="error" title="岗位 JD 读取失败" action={<Button onClick={() => void jd.refetch()}>重试 JD</Button>} /> : jd.isPending ? <p role="status">正在读取岗位 JD…</p> : <Typography.Paragraph className="interview-jd-preview">{visibleJd?.rawText}</Typography.Paragraph>}
    </section>
  </div>;
}
