import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Button } from 'antd';
import { useSession } from '../auth/session';
import { WorkspaceDialog } from '../components/WorkspaceDialog';
import { FeedbackAlert } from '../components/FeedbackAlert';
import { recruitmentApi } from './api';
import { canProcessRecruitment, recruitmentProcessingDeniedMessage } from './processingPermissions';
import { ledgerActionLabels, refreshRecruitmentActivity, type LedgerAction } from './activityApi';
import { LedgerPendingDialog } from './LedgerPendingDialog';
import { LedgerDecisionDialog } from './LedgerDecisionDialog';
import { LedgerRiskDialog } from './LedgerRiskDialog';
import { LedgerInterviewDialog } from './LedgerInterviewDialog';
import { actionError } from './candidateActions';

export function LedgerActionDialog({ applicationId, action, roundNo, onClose }: { applicationId: string; action: LedgerAction; roundNo?: number; onClose: () => void }) {
  const client = useQueryClient();
  const { actor } = useSession();
  const detail = useQuery({ queryKey: ['application', applicationId], queryFn: () => recruitmentApi.application(applicationId), staleTime: 0 });
  const value = detail.data;
  const canProcess = value && canProcessRecruitment(actor, { ownerUserId: value.application.ownerUserId, collaboratorUserIds: value.collaboratorUserIds });
  const canManageCompany = Boolean(actor?.superAdmin || actor?.permissions.includes('submission.manage'));
  const denied = !canProcess || (action === 'BLACKLIST' && !canManageCompany);
  if (detail.isPending || detail.error || !value || denied || (value.application.terminalAt && action !== 'BLACKLIST')) return <WorkspaceDialog open title={ledgerActionLabels[action]} onCancel={onClose} footer={<Button onClick={onClose}>关闭</Button>}>
    {detail.error ? <FeedbackAlert type="error" title={actionError(detail.error)} action={<Button onClick={() => void detail.refetch()}>重试活动</Button>} /> : detail.isPending ? <p role="status">正在核对招聘活动…</p> : <p>{denied ? action === 'BLACKLIST' && canProcess ? '当前账号无权管理公司限制。' : recruitmentProcessingDeniedMessage : '当前活动已结束，请在详情中查看完整历史。'}</p>}
  </WorkspaceDialog>;
  const props = { value, onClose, onChanged: () => refreshRecruitmentActivity(client, applicationId) };
  if (action === 'PENDING') return <LedgerPendingDialog {...props} />;
  if (action === 'INTERVIEW') return <LedgerInterviewDialog {...props} {...(roundNo?{roundNo}:{})} />;
  if (action === 'BLACKLIST') return <LedgerRiskDialog {...props} />;
  return <LedgerDecisionDialog {...props} action={action} />;
}
