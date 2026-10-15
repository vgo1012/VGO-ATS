import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { useMutation } from '@tanstack/react-query';
import { App, Form, Input, Select, Typography } from 'antd';
import { useLayoutEffect } from 'react';
import { ApiProblemError } from '../api/problem';
import { WorkspaceDialog as Modal } from '../components/WorkspaceDialog';
import type { SourceChannel } from './api';

export interface SourceChannelInput {
  name: string;
  channelType: string;
}

interface SourceChannelModalProps {
  value?: SourceChannel | 'new';
  onClose: () => void;
  onSaved: () => Promise<unknown>;
  onCreate: (input: SourceChannelInput) => Promise<unknown>;
  onUpdate: (channel: SourceChannel, input: SourceChannelInput) => Promise<unknown>;
}

const channelTypes = ['MANUAL', 'BATCH', 'MAIL', 'BOSS'] as const;
const channelTypeLabels: Record<(typeof channelTypes)[number], string> = {
  MANUAL: '人工录入',
  BATCH: '批量上传',
  MAIL: '邮箱来件',
  BOSS: 'Boss 直聘',
};

function errorMessage(error: unknown): string {
  return error instanceof ApiProblemError ? error.problem.detail : '操作失败，请稍后重试。';
}

export function SourceChannelModal({
  value,
  onClose,
  onSaved,
  onCreate,
  onUpdate,
}: SourceChannelModalProps) {
  const [form] = Form.useForm<SourceChannelInput>();
  const { message } = App.useApp();
  const editing = value && value !== 'new' ? value : undefined;
  useLayoutEffect(() => {
    if (value) form.setFieldsValue(value === 'new' ? { name: '', channelType: 'MANUAL' } : { name: value.name, channelType: value.channelType });
  }, [form, value]);
  const mutation = useMutation({
    mutationFn: (input: SourceChannelInput) =>
      editing ? onUpdate(editing, input) : onCreate(input),
    onSuccess: async () => {
      message.success('来源渠道已保存');
      await onSaved();
      onClose();
    },
  });

  return (
    <Modal
      okText="保存渠道"
      title={editing ? '编辑来源渠道' : '新建来源渠道'}
      open={Boolean(value)}
      onCancel={() => { if (!mutation.isPending) onClose(); }}
      closable={!mutation.isPending}
      keyboard={!mutation.isPending}
      mask={{ closable: !mutation.isPending }}
      cancelButtonProps={{ disabled: mutation.isPending }}
      confirmLoading={mutation.isPending}
      onOk={() => form.validateFields().then(input => mutation.mutateAsync({ ...input, name: input.name.trim() }).catch(() => undefined))}
      afterOpenChange={(open) => {
        if (!open) {
          form.resetFields();
          mutation.reset();
        }
      }}
      destroyOnHidden
    >
      {mutation.error ? <Alert type="error" showIcon title={errorMessage(mutation.error)} /> : null}
      {editing ? (
        <Typography.Paragraph type="secondary">
          系统编码：<Typography.Text code>{editing.code}</Typography.Text>
        </Typography.Paragraph>
      ) : (
        <Alert
          type="info"
          showIcon
          title="渠道编码将在保存时自动生成"
          description="编码用于历史记录和报表关联，创建后保持不变，无需手工填写。"
        />
      )}
      <Form form={form} disabled={mutation.isPending} layout="vertical" initialValues={{ name: '', channelType: 'MANUAL' }}>
        <Form.Item name="name" label="名称" rules={[{ required: true, whitespace: true, message: '请输入渠道名称' }, { max: 120, message: '渠道名称最多 120 个字符' }]}>
          <Input maxLength={120} />
        </Form.Item>
        <Form.Item name="channelType" label="类型" rules={[{ required: true, message: '请选择渠道类型' }]}>
          <Select options={channelTypes.map((type) => ({ label: channelTypeLabels[type], value: type }))} />
        </Form.Item>
      </Form>
    </Modal>
  );
}
