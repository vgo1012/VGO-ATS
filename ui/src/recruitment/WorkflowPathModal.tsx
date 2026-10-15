import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { useMutation } from '@tanstack/react-query';
import { Checkbox, Form, Input } from 'antd';
import { WorkspaceDialog } from '../components/WorkspaceDialog';
import { ApiProblemError } from '../api/problem';
import { stepLabel } from './workflowLabels';
import { recruitmentApi, type ApplicationDetails } from './api';

export function WorkflowPathModal({ open, value, onClose, onSaved }: { open: boolean; value: ApplicationDetails; onClose: () => void; onSaved: () => Promise<void> }) {
  const [form] = Form.useForm<{ selectedStepIds: string[]; reason: string }>();
  const steps = value.workflowSteps;
  // 撤回会回到 PATH_SELECTION，但已选择过的路径必须走追加修改接口。
  const amendment = steps.some(step => step.status !== 'AVAILABLE');
  const finished = new Set(['PASSED', 'FAILED', 'WITHDRAWN', 'SKIPPED']);
  const lastFinished = Math.max(0, ...steps.filter(step => finished.has(step.status)).map(step => step.sequenceNo));
  const optional = steps.filter(step => step.candidateOptional);
  const mutation = useMutation({ mutationFn: (fields: { selectedStepIds: string[]; reason: string }) => recruitmentApi.selectPath(value.application, fields.selectedStepIds, fields.reason, amendment), onSuccess: onSaved });
  const error = mutation.error instanceof ApiProblemError ? mutation.error.problem.detail : mutation.error?.message;
  return <WorkspaceDialog open={open} title={amendment ? '修改后续路径' : '选择候选人实际路径'} okText="确认路径" onCancel={onClose} confirmLoading={mutation.isPending}
    onOk={() => form.validateFields().then(fields => mutation.mutateAsync(fields).catch(() => undefined))}
    afterOpenChange={visible => { mutation.reset(); if (visible) form.setFieldsValue({ selectedStepIds: optional.filter(step => step.status !== 'NOT_SELECTED').map(step => step.id), reason: amendment ? '候选人后续安排调整' : '根据招聘类型和候选人情况确认本次实际路径' }); else form.resetFields(); }} destroyOnHidden>
    {error ? <Alert occurrence={error} type="error" showIcon title={error} /> : null}
    <Alert type="warning" showIcon title={amendment ? '保留已完成和撤回的历史，只调整后续未开始的步骤。' : '必选步骤自动加入，可选步骤逐候选人确认。'} />
    <Form form={form} layout="vertical" initialValues={{ selectedStepIds: optional.filter(step => step.status !== 'NOT_SELECTED').map(step => step.id), reason: amendment ? '候选人后续安排调整' : '根据招聘类型和候选人情况确认本次实际路径' }}>
      <Form.Item name="selectedStepIds" label="采用的可选步骤"><Checkbox.Group style={{ display: 'grid', gap: 8 }} options={optional.map(step => ({ value: step.id, label: stepLabel[step.stepCode] ?? step.stepCode, disabled: step.sequenceNo <= lastFinished || finished.has(step.status) }))} /></Form.Item>
      <Form.Item name="reason" label="选路原因" rules={[{ required: true }, { max: 500 }]}><Input.TextArea rows={3} /></Form.Item>
    </Form>
  </WorkspaceDialog>;
}
