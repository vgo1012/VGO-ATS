import { useQuery } from '@tanstack/react-query';
import { Button, Card, Space, Tag } from 'antd';
import { canReadRisk } from '../auth/permissions';
import { useSession } from '../auth/session';
import { FeedbackAlert } from '../components/FeedbackAlert';
import { LongTextPreview } from '../components/LongTextPreview';
import { displayEnum, formatDateTime } from '../display/labels';
import { submissionApi } from '../submission/api';

export function CandidateBlacklistDetails({ candidateId, active = true, sharedLibrary = false }: { candidateId: string; active?: boolean; sharedLibrary?: boolean }) {
  const { actor } = useSession();
  const allowed = sharedLibrary || canReadRisk(actor);
  const records = useQuery({ queryKey: ['candidate-restrictions', candidateId], queryFn: () => submissionApi.restrictions(candidateId), enabled: active && allowed, retry: false });
  return <Card className="candidate-blacklist-details" size="small" title="黑名单标签">
    {!allowed ? <span>暂无黑名单查看权限</span> : records.error ? <FeedbackAlert type="error" title="黑名单标签详情读取失败" action={<Button onClick={() => void records.refetch()}>重试标签详情</Button>}/> : records.isPending ? <span role="status">正在读取黑名单标签…</span> : records.data?.length ? <Space orientation="vertical" style={{ width: '100%', overflowWrap: 'anywhere' }}>
      {records.data.map((record, index) => <div key={`${record.clientId ?? 'global'}-${index}`}><Tag color="error">{record.clientName ?? '历史公司级标签'}</Tag><LongTextPreview text={record.reason} label="黑名单原因"/><small> · {formatDateTime(record.occurredAt)} · {displayEnum(record.sourceChannel)}</small></div>)}
      <small>有效黑名单简历在流程结束后进入共享简历库并保留标签，不进入个人待筛选，招聘推进仍受黑名单限制。</small>
    </Space> : <span>暂无黑名单标签</span>}
  </Card>;
}
