import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { CompactPagination } from '../components/CompactPagination';
import { countPaginationProps } from '../components/countPaginationProps';
import { PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Card, Drawer, Form, Input, Popconfirm, Space, Table, Tag, Typography } from 'antd';
import { useState } from 'react';
import { ApiProblemError } from '../api/problem';
import { demandApi, type Client } from '../demand/api';
import { deleteClient } from '../demand/clientDeletion';

const errorText = (error: unknown) => error instanceof ApiProblemError ? error.problem.detail : '操作失败，请稍后重试。';

export function CustomerManagementPage() {
  const [pageSize, setPageSize] = useState(20);
  const [cursors, setCursors] = useState<string[]>([]);
  const clients = useQuery({ queryKey: ['demand', 'clients', 'management', cursors.at(-1), pageSize], queryFn: () => demandApi.clients(undefined, cursors.at(-1), pageSize), retry: false });
  const total = useQuery({ queryKey: ['demand', 'clients', 'management', 'count'], queryFn: ({ signal }) => demandApi.countClients(undefined, signal), retry: false });
  return <div className="workspace-page demand-workspace-page">
    {clients.error ? <Alert occurrence={clients.error} type="error" showIcon title="客户列表读取失败" description={errorText(clients.error)} action={<Button onClick={() => void clients.refetch()}>重试</Button>} /> : null}
    <CustomerPanel clients={clients.data?.items ?? []} loading={clients.isFetching} onDeleted={id => {
      if (clients.data?.items.length === 1 && clients.data.items[0]?.id === id && cursors.length) setCursors(value => value.slice(0, -1));
    }} />
    <CompactPagination label="客户分页" page={cursors.length + 1} pageSize={pageSize} onPageSizeChange={size => { setPageSize(size); setCursors([]); }} loading={clients.isFetching}
      hasPrevious={cursors.length > 0} hasNext={Boolean(!clients.error && clients.data?.hasMore && clients.data.nextCursor)}
      onPrevious={() => setCursors(value => value.slice(0, -1))}
      onNext={() => { const next = clients.data?.nextCursor; if (next) setCursors(value => [...value, next]); }}
      onPageChange={page => setCursors(value => value.slice(0, page - 1))} maxPage={cursors.length + 1}
      {...countPaginationProps(total)} />
  </div>;
}
function CustomerPanel({ clients, loading, onDeleted }: { clients: Client[]; loading: boolean; onDeleted: (id: string) => void }) {
  const [editing, setEditing] = useState<Client | 'new'>();
  const [form] = Form.useForm<{ name: string }>();
  const qc = useQueryClient();
  const { message } = App.useApp();
  const save = useMutation({ mutationFn: ({ name }: { name: string }) => editing === 'new' ? demandApi.createClient({ name: name.trim() }) : demandApi.updateClient(editing!.id, { name: name.trim(), version: editing!.version }), onSuccess: async () => { message.success(editing === 'new' ? '客户已创建，编码由系统生成' : '客户名称已更新'); setEditing(undefined); form.resetFields(); await qc.invalidateQueries({ queryKey: ['demand', 'clients'] }); } });
  const disable = useMutation({ mutationFn: demandApi.disableClient, onSuccess: async () => { message.success('客户已停用，历史引用仍保留'); await qc.invalidateQueries({ queryKey: ['demand', 'clients'] }); } });
  const remove = useMutation({ mutationFn: deleteClient, onSuccess: async (_result, deleted) => {
    message.success('客户已删除');
    onDeleted(deleted.id);
    await qc.invalidateQueries({ queryKey: ['demand', 'clients'] });
  } });
  const busy = save.isPending || disable.isPending || remove.isPending;
  const open = (value: Client | 'new') => { save.reset(); setEditing(value); form.setFieldsValue({ name: value === 'new' ? '' : value.name }); };
  return <>
    <section className="ledger-heading"><div><Typography.Title level={1}>客户管理</Typography.Title><Typography.Paragraph>管理员维护客户名称，系统自动生成稳定编码。HR 在新增 JD 时直接选择。</Typography.Paragraph></div><Button type="primary" icon={<PlusOutlined />} disabled={busy} onClick={() => open('new')}>新增客户</Button></section>
    {disable.error ? <Alert occurrence={disable.error} type="error" showIcon title={errorText(disable.error)} /> : null}
    {remove.error ? <Alert occurrence={remove.error} type="error" showIcon title={errorText(remove.error)} /> : null}
    <Card><Table rowKey="id" loading={loading} pagination={false} scroll={{ x: 640 }} dataSource={clients} columns={[
      { title: '客户名称', dataIndex: 'name' },
      { title: '状态', dataIndex: 'status', width: 100, render: value => <Tag color={value === 'ACTIVE' ? 'green' : 'default'}>{value === 'ACTIVE' ? '启用' : '停用'}</Tag> },
      { title: '操作', className: 'table-action-cell', width: 240, render: (_, value: Client) => <Space><Button size="small" disabled={busy} onClick={() => open(value)}>编辑</Button>{value.status === 'ACTIVE' ? <Popconfirm title="停用这个客户？" description="停用后不能用于新 JD，历史数据不受影响。" onConfirm={() => disable.mutate(value)}><Button size="small" danger disabled={busy}>停用</Button></Popconfirm> : null}<Popconfirm title={`删除客户“${value.name}”？`} description="删除后无法恢复。已有业务引用的客户不能删除，请改用停用。" okText="确认删除" cancelText="取消" okButtonProps={{ danger: true }} onConfirm={() => remove.mutate(value)}><Button size="small" danger disabled={busy} loading={remove.isPending && remove.variables?.id === value.id}>删除</Button></Popconfirm></Space> },
    ]} /></Card>
    <Drawer title={editing === 'new' ? '新增客户' : '编辑客户'} open={Boolean(editing)} onClose={() => { if (!save.isPending) setEditing(undefined); }} extra={<Button type="primary" loading={save.isPending} onClick={() => form.submit()}>保存</Button>}>
      {save.error ? <Alert occurrence={save.error} className="modal-alert" type="error" showIcon title={errorText(save.error)} description="客户名称已保留，可以修改后重试。" /> : null}
      <Form form={form} layout="vertical" disabled={save.isPending} onFinish={values => save.mutate(values)}><Form.Item name="name" label="客户名称" rules={[{ required: true, whitespace: true, message: '请输入客户名称' }, { max: 160 }]}><Input autoFocus /></Form.Item><Alert type="info" showIcon title="客户编码由系统自动生成，无需填写。" /></Form>
    </Drawer>
  </>;
}
