import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { Button, Space, Typography } from 'antd';
import { ApiProblemError } from '../api/problem';

interface Props {
  label: string;
  count: number;
  loaded: boolean;
  hasMore: boolean;
  loading: boolean;
  error: unknown;
  onNext: () => void;
  onRetry: () => void;
}

export function HistoryPagination({label,count,loaded,hasMore,loading,error,onNext,onRetry}:Props) {
  const detail=error instanceof ApiProblemError?error.problem.detail:error instanceof Error?error.message:'请稍后重试。';
  return <Space orientation="vertical" style={{width:'100%',marginTop:12}}>
    {error?<Alert occurrence={error} type="error" showIcon title={`${label}加载失败`} description={detail} action={<Button onClick={onRetry} loading={loading}>{`重试${label}`}</Button>}/>:null}
    {loaded?<Typography.Text type="secondary" role="status">{hasMore||error?`已加载 ${count} 条${hasMore?'，还有历史待加载':''}`:`已显示全部 ${count} 条`}</Typography.Text>:null}
    {hasMore&&!error?<Button onClick={onNext} loading={loading}>{`加载更多${label}`}</Button>:null}
  </Space>;
}
