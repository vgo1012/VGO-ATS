import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { useQuery } from '@tanstack/react-query';
import { Button, Descriptions, Empty, Space, Timeline } from 'antd';
import { ApiProblemError } from '../api/problem';
import { demandApi } from '../demand/api';
import { CandidateName, ResumeVersionName, TechnicalDetails, UserName } from '../display/BusinessReferences';
import { displayEnum, formatDateTime } from '../display/labels';
import type { SubmissionDetails } from './api';

const accessDenied = (error: unknown) => error instanceof ApiProblemError && [401, 403].includes(error.problem.status);

export function SubmissionEvidence({ value }: { value: SubmissionDetails }) {
  const { submission, sendSnapshot: snapshot } = value;
  const client = useQuery({ queryKey: ['demand', 'client', submission.clientId], queryFn: () => demandApi.client(submission.clientId), retry: false });
  const position = useQuery({ queryKey: ['demand', 'position', submission.positionId], queryFn: () => demandApi.position(submission.positionId), retry: false });
  const error = client.error ?? position.error;
  const reading = client.isFetching || position.isFetching;
  const clientName = accessDenied(client.error) ? undefined : client.data?.name;
  const positionName = accessDenied(position.error) ? undefined : position.data?.name;
  return <Space orientation="vertical" size={16} style={{ width: '100%' }}>
    {error ? <Alert occurrence={error} type="error" showIcon title="客户或职位名称加载失败" action={<Button loading={reading} disabled={reading} onClick={() => void Promise.all([client.refetch(), position.refetch()])}>重试客户与职位</Button>} /> : null}
    <Descriptions bordered size="small" column={1} items={[
      { key: 'candidate', label: '候选人', children: <CandidateName id={submission.candidateId} /> },
      { key: 'client', label: '客户', children: clientName ?? (client.isPending ? '加载客户…' : '客户名称暂不可用') },
      { key: 'position', label: '职位', children: positionName ?? (position.isPending ? '加载职位…' : '职位名称暂不可用') },
      { key: 'status', label: '推荐状态', children: `${displayEnum(submission.deliveryStatus)} · ${displayEnum(submission.processStatus)}` },
      { key: 'resume', label: '使用简历', children: <ResumeVersionName candidateId={submission.candidateId} versionId={submission.resumeVersionId} /> },
      ...(snapshot ? [
        { key: 'recipient', label: '推荐对象', children: snapshot.recipient },
        { key: 'channel', label: '发送渠道', children: displayEnum(snapshot.channel) },
        { key: 'sender', label: '记录人', children: <UserName id={snapshot.sentByUserId} /> },
        { key: 'sentAt', label: '推荐时间', children: formatDateTime(snapshot.sentAt) },
        { key: 'file', label: '冻结原件', children: snapshot.fileName },
      ] : [{ key: 'unsent', label: '发送事实', children: '尚未发送' }]),
      ...(submission.withdrawnAt ? [
        { key: 'withdrawnAt', label: '撤回时间', children: formatDateTime(submission.withdrawnAt) },
        { key: 'withdrawalReason', label: '撤回原因', children: submission.withdrawalReason ?? '未记录' },
      ] : []),
    ]} />
    {value.feedback.length ? <Timeline items={value.feedback.map(feedback => ({
      key: feedback.id,
      color: feedback.result === 'ACCEPTED' ? 'green' : feedback.result === 'REJECTED' ? 'red' : 'blue',
      content: <span><b>{displayEnum(feedback.result)}</b> · {feedback.reasonCategory}<br />{feedback.note ?? '无补充'}<br />下一步：{feedback.nextAction}<br /><small>来源 {displayEnum(feedback.sourceChannel)} · {formatDateTime(feedback.sourceOccurredAt)} · 登记人 <UserName id={feedback.recordedByUserId} /></small></span>,
    }))} /> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚无客户反馈" />}
    <TechnicalDetails items={[
      ['推荐编号', submission.id], ['招聘活动编号', submission.applicationId], ['客户编号', submission.clientId], ['职位编号', submission.positionId],
      ['发送快照编号', snapshot?.id], ['冻结简历版本编号', snapshot?.resumeVersionId], ['原件 SHA-256', snapshot?.contentSha256],
    ]} />
  </Space>;
}
