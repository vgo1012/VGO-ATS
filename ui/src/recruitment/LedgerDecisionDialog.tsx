import { useMutation } from '@tanstack/react-query';
import { Form, Input, Select } from 'antd';
import { useState } from 'react';
import { WorkspaceDialog } from '../components/WorkspaceDialog';
import { FeedbackAlert } from '../components/FeedbackAlert';
import { CandidateName, JobName } from '../display/BusinessReferences';
import { displayEnum } from '../display/labels';
import { recruitmentApi, type ApplicationDetails } from './api';
import { newActivityOrigin } from './activityApi';
import { canSelectNextAction } from './nextActionEligibility';
import { actionError } from './candidateActions';

interface Fields { decision: 'REJECT' | 'WITHDRAW' | 'OTHER'; reason: string; status: 'IN_PROGRESS' | 'ISSUED' }
export function LedgerDecisionDialog({ value, action, onClose, onChanged }: { value: ApplicationDetails; action: 'ENDED' | 'ONBOARDED'; onClose: () => void; onChanged: () => Promise<unknown> }) {
  const [form] = Form.useForm<Fields>();
  const [origin] = useState(() => newActivityOrigin(action));
  const app = value.application;
  const offer = app.currentStage === 'OFFER';
  const canDecide = app.currentStage === 'DECISION' || canSelectNextAction(value);
  const blocked = action === 'ONBOARDED' && !offer && !canDecide;
  const mutation = useMutation({ mutationFn: async (fields: Fields) => {
    if (action === 'ENDED') {
      const decision = fields.decision === 'WITHDRAW' ? 'WITHDRAW' : 'REJECT';
      if (offer) return recruitmentApi.addOffer(app.id, { status: 'TERMINATED', terminationReason: decision === 'WITHDRAW' ? 'CANDIDATE_DECLINED' : 'COMPANY_WITHDREW', reason: fields.reason }, origin);
      return recruitmentApi.decide(app.id, decision, fields.reason, undefined, origin);
    }
    return recruitmentApi.hire(app.id, { offerStatus: fields.status, reason: fields.reason }, origin);
  }, onSuccess: async () => { await onChanged(); onClose(); } });
  return <WorkspaceDialog open title={action === 'ENDED' ? '放弃' : '确认录用'} onCancel={onClose} okText={action === 'ENDED' ? '确认放弃' : '确认录用'} confirmLoading={mutation.isPending} okButtonProps={{ danger: action === 'ENDED', disabled: blocked }} onOk={() => form.validateFields().then(fields => mutation.mutateAsync(fields))}>
    <p><CandidateName id={app.candidateId} /> · <JobName jdId={app.jdId} /> · {app.clientName ?? '内部招聘'}</p>
    {blocked ? <FeedbackAlert type="info" title="请先完成当前步骤，再作出人工录用决定。" /> : null}
    <Form form={form} layout="vertical" initialValues={{ reason: '', status: value.offerStatusRecords.at(-1)?.status === 'IN_PROGRESS' ? 'IN_PROGRESS' : 'ISSUED' }}>
      {action === 'ENDED' ? <><Form.Item name="decision" label="放弃类型" rules={[{ required: true }]}><Select options={[{ value: 'REJECT', label: '公司淘汰' }, { value: 'WITHDRAW', label: '候选人退出' }, { value: 'OTHER', label: '其他' }]} /></Form.Item><Form.Item name="reason" label="原因评论" rules={[{ required: true, whitespace: true }, { max: 500 }]}><Input.TextArea rows={3} /></Form.Item></> : <>
        <p>{offer ? '确认后将完成当前 Offer 并进入入职流程。' : '本次确认将同时记录人工录用、Offer 进展并进入入职流程。'}</p>
        <Form.Item name="reason" label="原因评论" rules={[{ required: true, whitespace: true }, { max: 500 }]}><Input.TextArea rows={3} /></Form.Item>
        <Form.Item name="status" label="Offer 进展" rules={[{ required: true, message: '请选择 Offer 进展' }]}><Select options={['ISSUED', 'IN_PROGRESS'].map(status => ({ value: status, label: displayEnum(status) }))} /></Form.Item>
      </>}
    </Form>
    {mutation.error ? <FeedbackAlert type="error" title={actionError(mutation.error)} /> : null}
  </WorkspaceDialog>;
}
