import { useMutation, useQuery } from '@tanstack/react-query';
import { Button, Form, Input, Radio, Select, Space } from 'antd';
import dayjs from 'dayjs';
import timezone from 'dayjs/plugin/timezone';
import utc from 'dayjs/plugin/utc';
import { useState } from 'react';
import { useSession } from '../auth/session';
import { FeedbackAlert } from '../components/FeedbackAlert';
import { WorkspaceDialog } from '../components/WorkspaceDialog';
import { CandidateName, JobName } from '../display/BusinessReferences';
import { recruitmentApi, type RecruitmentApplication } from './api';
import { actionError } from './candidateActions';
import { canProcessRecruitment, recruitmentProcessingDeniedMessage } from './processingPermissions';

dayjs.extend(utc);
dayjs.extend(timezone);

type OnboardingAction = 'REVIEW' | 'RETURN';
interface ReviewFields { timing: 'now' | 'past'; onboardedAt?: string; reason?: string }
interface ReturnFields { reasonCategory?: 'LIBRARY_RETURN' | 'CANDIDATE' | 'POSITION' | 'OTHER'; reasonDetail?: string }

function OnboardingActionDialog({ application, action, onClose, onChanged }: {
  application: RecruitmentApplication;
  action: OnboardingAction;
  onClose: () => void;
  onChanged: () => Promise<unknown>;
}) {
  const { actor } = useSession();
  const [reviewForm] = Form.useForm<ReviewFields>();
  const [returnForm] = Form.useForm<ReturnFields>();
  const timing = Form.useWatch('timing', reviewForm);
  const detail = useQuery({ queryKey: ['application', application.id], queryFn: () => recruitmentApi.application(application.id), staleTime: 0 });
  const value = detail.data;
  const canProcess = value && canProcessRecruitment(actor, { ownerUserId: value.application.ownerUserId, collaboratorUserIds: value.collaboratorUserIds });
  const issuedAt = value?.offerStatusRecords.filter(record => ['ISSUED', 'ACCEPTED'].includes(record.status)).at(-1)?.occurredAt;
  const processStartedAt = value?.application.onboardingStartedAt;
  const completing = value?.application.onboardingStatus === 'IN_PROGRESS';
  const reviewTitle = completing ? '确认实际入职' : '更正入职时间';
  const review = useMutation({ mutationFn: async (fields: ReviewFields) => {
    const onboardedAt = fields.timing === 'past'
      ? dayjs.tz(fields.onboardedAt, 'Asia/Shanghai').toISOString()
      : new Date().toISOString();
    return recruitmentApi.reviewOnboarding(application.id, { onboardedAt, ...(fields.reason?.trim() ? { reason: fields.reason.trim() } : {}) });
  }, onSuccess: async () => { await onChanged(); onClose(); } });
  const returnToShared = useMutation({ mutationFn: (fields: ReturnFields) => recruitmentApi.returnOnboardingToShared(application.id, {
    ...(fields.reasonCategory ? { reasonCategory: fields.reasonCategory } : {}),
    ...(fields.reasonDetail?.trim() ? { reasonDetail: fields.reasonDetail.trim() } : {}),
  }), onSuccess: async () => { await onChanged(); onClose(); } });
  const busy = review.isPending || returnToShared.isPending;
  if (detail.isPending || detail.error || !value || !canProcess) return <WorkspaceDialog open title={action === 'REVIEW' ? (application.onboardingStatus === 'IN_PROGRESS' ? '确认实际入职' : '更正入职时间') : '退回共享简历库'} onCancel={onClose} footer={<Button onClick={onClose}>关闭</Button>}>
    {detail.error ? <FeedbackAlert type="error" title={actionError(detail.error)} action={<Button onClick={() => void detail.refetch()}>重试活动</Button>} /> : detail.isPending ? <p role="status">正在核对入职流程…</p> : <p>{recruitmentProcessingDeniedMessage}</p>}
  </WorkspaceDialog>;
  return <WorkspaceDialog open title={action === 'REVIEW' ? reviewTitle : '退回共享简历库'} onCancel={onClose} confirmLoading={busy}
    okText={action === 'REVIEW' ? (completing ? '确认已入职' : '保存更正') : '直接退回'}
    okButtonProps={{ danger: action === 'RETURN' }}
    onOk={() => action === 'REVIEW'
      ? reviewForm.validateFields().then(fields => review.mutateAsync(fields))
      : returnForm.validateFields().then(fields => returnToShared.mutateAsync(fields))}>
    <p><CandidateName id={value.application.candidateId} /> · <JobName jdId={value.application.jdId} /> · {value.application.clientName ?? '内部招聘'}</p>
    {action === 'REVIEW' ? <Form form={reviewForm} layout="vertical" initialValues={{ timing: 'now', reason: '' }}>
      <Form.Item name="timing" label="入职时间"><Radio.Group options={[{ value: 'now', label: '现在入职' }, { value: 'past', label: '补录入职时间' }]} /></Form.Item>
      {timing === 'past' ? <Form.Item name="onboardedAt" label="实际入职日期与时间（北京时间）" rules={[{ required: true, message: '请选择实际入职时间' }, { validator: (_, input: string | undefined) => {
        if (!input) return Promise.resolve();
        const occurred = dayjs.tz(input, 'Asia/Shanghai');
        if (!occurred.isValid()) return Promise.reject(new Error('请选择有效的入职时间'));
        if (occurred.isAfter(dayjs())) return Promise.reject(new Error('实际入职不能选择未来时间'));
        if (processStartedAt && occurred.isBefore(dayjs(processStartedAt))) return Promise.reject(new Error(`不能早于进入入职流程时间：${dayjs(processStartedAt).tz('Asia/Shanghai').format('YYYY-MM-DD HH:mm:ss')}`));
        if (issuedAt && occurred.isBefore(dayjs(issuedAt))) return Promise.reject(new Error(`不能早于 Offer 发出时间：${dayjs(issuedAt).tz('Asia/Shanghai').format('YYYY-MM-DD HH:mm:ss')}`));
        return Promise.resolve();
      } }]}><Input type="datetime-local" step={1} /></Form.Item> : null}
      <Form.Item name="reason" label="评论（选填）" rules={[{ max: 500 }]}><Input.TextArea rows={3} /></Form.Item>
    </Form> : <Form form={returnForm} layout="vertical">
      <p>退回后该招聘活动结束，候选人的当前简历进入共享简历库；原录用、Offer 和入职记录继续保留。</p>
      <Form.Item name="reasonCategory" label="退回原因（选填）"><Select allowClear placeholder="不选择则按“入职库退回”记录" options={[
        { value: 'LIBRARY_RETURN', label: '入职库退回' },
        { value: 'CANDIDATE', label: '候选人原因' },
        { value: 'POSITION', label: '岗位原因' },
        { value: 'OTHER', label: '其它' },
      ]} /></Form.Item>
      <Form.Item name="reasonDetail" label="补充说明（选填）" rules={[{ max: 460 }]}><Input.TextArea rows={3} /></Form.Item>
    </Form>}
    {review.error || returnToShared.error ? <FeedbackAlert type="error" title={actionError(review.error ?? returnToShared.error)} /> : null}
  </WorkspaceDialog>;
}

export function OnboardingFlowActions({ application, onChanged }: {
  application: RecruitmentApplication;
  onChanged: () => Promise<unknown>;
}) {
  const { actor } = useSession();
  const [target, setTarget] = useState<{ action: OnboardingAction; trigger: HTMLElement }>();
  if (!actor?.superAdmin && !actor?.permissions?.includes('recruitment.manage')) return null;
  const close = () => {
    const trigger = target?.trigger;
    setTarget(undefined);
    requestAnimationFrame(() => trigger?.focus({ preventScroll: true }));
  };
  return <><Space className="onboarding-flow-actions table-action-stack" orientation="vertical" size={6}>
    <Button size="small" onClick={event => setTarget({ action: 'REVIEW', trigger: event.currentTarget })}>{application.onboardingStatus === 'IN_PROGRESS' ? '确认实际入职' : '更正入职时间'}</Button>
    <Button size="small" danger onClick={event => setTarget({ action: 'RETURN', trigger: event.currentTarget })}>退回共享简历库</Button>
  </Space>
  {target ? <OnboardingActionDialog application={application} action={target.action} onClose={close} onChanged={onChanged} /> : null}</>;
}
