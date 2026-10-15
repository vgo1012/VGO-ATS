import { scrollableTableHeader } from '../components/scrollableTableHeader';
import { PaginatedTable } from '../components/PaginatedTable';
import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Card, Popconfirm, Space, Tag, Typography } from 'antd';
import { useState } from 'react';
import { ApiProblemError } from '../api/problem';
import { useSession } from '../auth/session';
import { SourceChannelModal } from '../talent/SourceChannelModal';
import { talentApi, type SourceChannel } from '../talent/api';

const errorText = (error: unknown) => error instanceof ApiProblemError ? error.problem.detail : '来源渠道加载或保存失败，请重试。';

export function SourceChannelsPage() {
  const { actor } = useSession();
  const qc = useQueryClient();
  const { message } = App.useApp();
  const [editing, setEditing] = useState<SourceChannel | 'new'>();
  const channels = useQuery({ queryKey: ['talent', 'channels'], queryFn: talentApi.channels, enabled: Boolean(actor?.superAdmin) });
  const refresh = () => qc.invalidateQueries({ queryKey: ['talent', 'channels'] });
  const disable = useMutation({ mutationFn: talentApi.disableChannel, onSuccess: async () => { await refresh(); void message.success('来源渠道已停用，历史简历来源保留'); } });
  const remove = useMutation({ mutationFn: talentApi.deleteChannel, onSuccess: async () => { await refresh(); void message.success('来源渠道已删除'); } });
  const busy = disable.isPending || remove.isPending;
  if (!actor?.superAdmin) return <Alert type="warning" showIcon title="仅管理员可以维护公共来源渠道" />;
  return <div className="workspace-page settings-list-page">
    <section className="ledger-heading"><div><Typography.Title level={1}>来源渠道</Typography.Title><Typography.Paragraph>管理员统一维护，全员共享使用，用于标记简历来源。停用后保留历史记录。</Typography.Paragraph></div><Button type="primary" icon={<PlusOutlined aria-hidden />} onClick={() => setEditing('new')}>新建来源渠道</Button></section>
    {channels.error ? <Alert occurrence={channels.error} type="error" showIcon title={errorText(channels.error)} action={<Button onClick={() => void channels.refetch()}>重试</Button>} /> : null}
    {disable.error ? <Alert occurrence={disable.error} type="error" showIcon title={errorText(disable.error)} /> : null}
    {remove.error ? <Alert occurrence={remove.error} type="error" showIcon title={errorText(remove.error)} action={<Button onClick={() => void channels.refetch()}>刷新列表</Button>} /> : null}
    <Card><PaginatedTable<SourceChannel> rowKey="id" dataSource={channels.data?.items ?? []} loading={channels.isPending} initialPageSize={20} scroll={{ x: 640 }} onHeaderRow={() => scrollableTableHeader('公共来源渠道表头，可横向滚动')} columns={[
      { title: '来源名称', dataIndex: 'name' },
      { title: '使用范围', render: () => '全员共享' },
      { title: '状态', dataIndex: 'status', render: (status: string) => <Tag color={status === 'ACTIVE' ? 'green' : 'default'}>{status === 'ACTIVE' ? '启用' : '停用'}</Tag> },
      { title: '操作', className: 'table-action-cell', render: (_, value) => <Space wrap><Button disabled={busy} onClick={() => setEditing(value)}>编辑</Button>{value.status === 'ACTIVE' ? <Popconfirm title="停用此来源渠道？" description="停用后无法用于新上传，历史来源保持不变。" onConfirm={() => disable.mutateAsync(value)}><Button danger loading={disable.isPending && disable.variables?.id === value.id} disabled={busy}>停用</Button></Popconfirm> : null}<Popconfirm title={`确认删除来源渠道“${value.name}”？`} description="仅可删除未使用的渠道；已关联收件或简历时请使用停用。" okText="确认删除" cancelText="取消" okButtonProps={{ danger: true }} onConfirm={() => remove.mutateAsync(value).catch(() => undefined)}><Button danger loading={remove.isPending && remove.variables?.id === value.id} disabled={busy}>删除</Button></Popconfirm></Space> },
    ]} /></Card>
    <SourceChannelModal {...(editing ? { value: editing } : {})} onCreate={talentApi.createChannel} onUpdate={talentApi.updateChannel} onSaved={refresh} onClose={() => setEditing(undefined)} />
  </div>;
}
