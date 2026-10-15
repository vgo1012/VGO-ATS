import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Button } from 'antd';
import { WorkspaceDialog } from '../components/WorkspaceDialog';
import { LoadingStatus } from '../components/LoadingStatus';
import { recruitmentApi } from './api';
import { ApplicationContent } from './ApplicationDetailsDialog';
import { actionError } from './candidateActions';

/** Continue from fresh workflow facts after a separately saved appointment or interview result. */
export function ContinueRecruitmentDialog({ applicationId, completedStepId, onClose }: {
  applicationId: string; completedStepId?: string | undefined; onClose: () => void;
}) {
  const client = useQueryClient();
  const details = useQuery({ queryKey: ['application', applicationId], queryFn: () => recruitmentApi.application(applicationId), refetchOnMount: 'always', retry: false });
  const refresh = async () => {
    await Promise.all([['application', applicationId], ['applications'], ['interviews'], ['interview-tasks'], ['follow-up-plans']].map(queryKey => client.invalidateQueries({ queryKey })));
  };
  return <WorkspaceDialog open size="wide" title="继续招聘跟进" footer={null} onCancel={onClose}>
    {details.error ? <Alert occurrence={details.error} type="error" showIcon title="结果已保存，暂时无法读取下一步" description={actionError(details.error)} action={<Button onClick={() => void details.refetch()}>重试下一步</Button>} />
      : !details.isFetchedAfterMount || !details.data ? <LoadingStatus label="读取最新招聘进展" />
        : <ApplicationContent value={details.data} onChanged={refresh} initialNextAction initialStepId={completedStepId} />}
  </WorkspaceDialog>;
}
