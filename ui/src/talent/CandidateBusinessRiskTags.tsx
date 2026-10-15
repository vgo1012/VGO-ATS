import { Space, Tag } from 'antd';
import type { CandidatePoolStatus } from './api';

export function CandidateBusinessRiskTags({ state, loading, failed }: {
  state: CandidatePoolStatus | undefined;
  loading: boolean;
  failed: boolean;
}) {
  const hasFlow = state?.jobContexts?.some(context => context.status === 'ADVANCED' && Boolean(context.applicationId));
  const flowKnown = !loading && !failed && Boolean(state);
  return <Space size={4} wrap>
    {hasFlow ? <Tag>已有招聘流程</Tag> : null}
    {!hasFlow && flowKnown ? <span>无</span> : null}
    {!flowKnown ? <small>{loading ? '读取流程状态…' : '流程状态暂不可用'}</small> : null}
  </Space>;
}
