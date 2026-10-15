import { jobLabel } from '../demand/jobLabel';
import { Button, Space } from 'antd';
import { displayEnum } from '../display/labels';
import type { CandidatePoolStatus } from './api';

export function CandidateRecruitmentProgress({ state, loading, failed, onOpen }: {
  state: CandidatePoolStatus | undefined;
  loading: boolean;
  failed: boolean;
  onOpen: ((id: string) => void) | undefined;
}) {
  if (loading) return <small>读取招聘流程…</small>;
  if (failed || !state) return <small>招聘流程暂不可用</small>;
  const contexts = state.jobContexts?.filter(context => context.applicationId) ?? [];
  if (!contexts.length) return <small>暂无招聘流程</small>;
  return <Space orientation="vertical" size={4}>{contexts.map(context => {
    const label = `${jobLabel(context.title, context.jdCode)} · ${context.status === 'FINISHED' ? '历史归档' : context.stage ? displayEnum(context.stage) : '已推进'}`;
    return <div key={`${context.applicationId}:${context.resumeVersionId}`}>
      {onOpen ? <Button data-application-id={context.applicationId} type="link" size="small" onClick={() => onOpen(context.applicationId!)}>{label}</Button> : <small>{label}</small>}
      {context.resumeVersionId !== state.resumeVersionId ? <small> · 历史简历版本</small> : null}
    </div>;
  })}</Space>;
}
