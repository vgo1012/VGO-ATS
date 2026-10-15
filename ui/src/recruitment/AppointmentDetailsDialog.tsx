import { RecruitmentStageActions } from './RecruitmentStage';
import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { useEffect, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Descriptions, Space, Tag } from 'antd';
import { useSession } from '../auth/session';
import { WorkspaceDialog } from '../components/WorkspaceDialog';
import { LoadingStatus } from '../components/LoadingStatus';
import { CandidateName, JobName, UserName } from '../display/BusinessReferences';
import { displayEnum, formatDateTime } from '../display/labels';
import { recruitmentApi } from './api';
import { actionError } from './candidateActions';
import { ContinueRecruitmentDialog } from './ContinueRecruitmentDialog';
import { FollowUpPlanDialog } from './FollowUpPlanDialog';
import { FollowUpPlanHistory } from './FollowUpPlanHistory';
import { refreshFollowUpPlans, type FollowUpPlan } from './followUpPlansApi';
import { canProcessRecruitment, recruitmentProcessingDeniedMessage } from './processingPermissions';

export function AppointmentDetailsDialog({ plan, onClose }: { plan: FollowUpPlan; onClose: () => void }) {
  const { actor } = useSession();
  const client = useQueryClient();
  const [continuing, setContinuing] = useState(false);
  const [action, setAction] = useState<'schedule' | 'complete' | 'cancel'>();
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);
  const details = useQuery({ queryKey: ['application', plan.applicationId], queryFn: () => recruitmentApi.application(plan.applicationId) });
  const allowed = details.data && canProcessRecruitment(actor, {
    ownerUserId: details.data.application.ownerUserId, collaboratorUserIds: details.data.collaboratorUserIds,
  });
  if (continuing) return <ContinueRecruitmentDialog applicationId={plan.applicationId} onClose={onClose} />;
  return <WorkspaceDialog open size="form" title="沟通预约详情" footer={null} onCancel={onClose}>
    {details.error ? <Alert occurrence={details.error} type="error" showIcon title={actionError(details.error)} action={<Button onClick={() => void details.refetch()}>重试预约详情</Button>} /> : details.isPending ? <LoadingStatus label="读取预约详情" /> : <Space orientation="vertical" style={{ width: '100%' }}>
      <Descriptions column={1} size="small" items={[
        { key: 'candidate', label: '候选人', children: <CandidateName id={plan.candidateId} /> },
        { key: 'job', label: '应聘岗位', children: <JobName jdId={plan.jdId} /> },
        { key: 'time', label: '约定时间', children: `${formatDateTime(plan.startsAt)} 至 ${formatDateTime(plan.endsAt)}（北京时间）` },
        { key: 'channel', label: '沟通方式', children: displayEnum(plan.channel) },
        { key: 'note', label: '沟通事项', children: plan.note || '未填写' },
        { key: 'owner', label: '负责人', children: <UserName id={plan.ownerUserId} /> },
        { key: 'status', label: '状态', children: <Tag>{({ SCHEDULED: '待沟通', COMPLETED: '已沟通', CANCELLED: '已取消' })[plan.status]}</Tag> },
        ...(plan.clientName ? [{ key: 'client', label: '甲方公司', children: plan.clientName }] : []),
      ]} />
      {allowed && !details.data?.application.terminalAt && plan.status === 'SCHEDULED' ? <Space wrap>
        <Button type="primary" disabled={Date.parse(plan.startsAt) > now} onClick={() => setAction('complete')}>记录沟通结果</Button>
        <Button onClick={() => setAction('schedule')}>改期</Button>
        <Button onClick={() => setAction('cancel')}>取消预约</Button>
      </Space> : !allowed ? <Alert type="info" showIcon title={recruitmentProcessingDeniedMessage} /> : null}
      {allowed && details.data && !details.data.application.terminalAt ? <RecruitmentStageActions value={details.data.application} /> : null}
      <FollowUpPlanHistory applicationId={plan.applicationId} />
      {action ? <FollowUpPlanDialog plan={plan} action={action} onClose={() => setAction(undefined)} onSaved={async result => {
        await refreshFollowUpPlans(client, plan.applicationId);
        setAction(undefined);
        if (result === 'CONTINUE') setContinuing(true);
        else onClose();
      }} /> : null}
    </Space>}
  </WorkspaceDialog>;
}
