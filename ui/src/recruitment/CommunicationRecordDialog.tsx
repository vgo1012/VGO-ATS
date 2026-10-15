import { useMutation } from '@tanstack/react-query';
import { Alert, Form, Input, Select } from 'antd';
import { useRef, useState, type ReactNode } from 'react';
import { type ActivityOrigin } from './activityApi';
import { createIdempotencyKey } from '../api/idempotency';
import { ApiProblemError } from '../api/problem';
import { WorkspaceDialog } from '../components/WorkspaceDialog';
import { displayEnum } from '../display/labels';
import { recruitmentApi, type CommunicationInput } from './api';

type Submission = { body: CommunicationInput; key: string };

export function CommunicationRecordDialog({ open, applicationId, onClose, onSaved, origin, introduction, title, simple=false }: {
  simple?: boolean; origin?: ActivityOrigin | undefined; introduction?: ReactNode; title?: string; open: boolean; applicationId: string; onClose: () => void; onSaved: () => Promise<void>;
}) {
  const [form] = Form.useForm<CommunicationInput>();
  const submission = useRef<Submission | undefined>(undefined);
  const submitting = useRef(false);
  const confirmed = useRef(false);
  const [frozen, setFrozen] = useState(false);
  const mutation = useMutation({
    mutationFn: (value: Submission) => recruitmentApi.communicate(applicationId, value.body, value.key, ...(origin ? [origin] as const : [] as const)),
    onError: (error) => {
      // 明确的输入拒绝允许修正；响应丢失或服务异常必须核对原请求。
      if (error instanceof ApiProblemError && [400, 422].includes(error.problem.status)) {
        submission.current = undefined;
        setFrozen(false);
      }
    },
  });
  const submit = async () => {
    if (submitting.current) return;
    submitting.current = true;
    try {
      if (!submission.current) {
        const fields = await form.validateFields();
        submission.current = { key: createIdempotencyKey(), body: {
          channel: simple?'OTHER':fields.channel, result: simple?'FOLLOW_UP':fields.result,
          summary: fields.summary ?? '', nextPlan: fields.nextPlan ?? '',
        } };
        setFrozen(true);
      }
      if (!confirmed.current) {
        await mutation.mutateAsync(submission.current);
        confirmed.current = true;
      }
      await onSaved();
      submission.current = undefined;
      confirmed.current = false;
      setFrozen(false);
      form.resetFields();
      mutation.reset();
    } finally {
      submitting.current = false;
    }
  };
  const uncertain = frozen && mutation.isError;
  return <WorkspaceDialog open={open} title={title ?? '记录结构化沟通'} onCancel={onClose}
    okText={uncertain ? '重试保存记录' : '保存记录'} confirmLoading={mutation.isPending} onOk={submit}>
    {introduction}
    {uncertain ? <Alert type="warning" showIcon title="保存结果尚未确认，请重试核对；当前内容已保留。" /> : null}
    <Form form={form} layout="vertical" disabled={frozen} initialValues={{ channel: 'OTHER', summary: '', nextPlan: '' }}>
      {!simple?<><Form.Item name="channel" label="渠道" rules={[{ required: true }]}><Select options={['PHONE', 'EMAIL', 'WECHAT', 'ENTERPRISE_WECHAT', 'IN_PERSON', 'OTHER'].map(value => ({ value, label: displayEnum(value) }))} /></Form.Item>
      <Form.Item name="result" label="沟通结果" rules={[{ required: true }]}><Select options={['CONTINUE', 'DECLINED', 'NO_RESPONSE', 'FOLLOW_UP'].map(value => ({ value, label: displayEnum(value) }))} /></Form.Item></>:null}
      <Form.Item name="summary" label={simple?"沟通情况":"沟通情况（选填）"} rules={[{ ...(simple?{required:true,whitespace:true}:{}), max: 1000 }]}><Input.TextArea rows={simple?5:3} /></Form.Item>
      {!simple?<Form.Item name="nextPlan" label="下一步计划（选填）" rules={[{ max: 500 }]}><Input.TextArea rows={2} /></Form.Item>:null}
    </Form>
  </WorkspaceDialog>;
}
