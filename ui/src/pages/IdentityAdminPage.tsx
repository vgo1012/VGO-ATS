import { PaginatedTable } from '../components/PaginatedTable';
import { useAppFeedback } from '../components/useAppFeedback';
import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import {
  ApartmentOutlined,
  CloudSyncOutlined,
  DisconnectOutlined,
  DeleteOutlined,
  KeyOutlined,
  PlusOutlined,
  SafetyCertificateOutlined,
  SwapOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Card, Checkbox, Descriptions, Form, Input, Popconfirm, Select, Space, Table, Tabs, Tag, Typography,  } from 'antd';
import { WorkspaceDialog as Modal, WorkspaceDialogGroup } from '../components/WorkspaceDialog';
import { useEffect, useMemo, useRef, useState } from 'react';
import { ApiProblemError } from '../api/problem';
import { identityApi, type AdminUser, type CreatedUser, type Department, type HandoverPreview, type UserOrganizationAssignments, type WecomStoredDepartment, type WecomStoredMember } from '../identity/api';

const ROLE_OPTIONS = [
  { label: '招聘专员', value: 'RECRUITER' },
  { label: '招聘负责人', value: 'RECRUITMENT_MANAGER' },
  { label: '人力负责人', value: 'HR_DIRECTOR' },
  { label: '面试官', value: 'INTERVIEWER' },
];

function mutationError(error: unknown): string {
  return error instanceof ApiProblemError ? error.problem.detail : '操作失败，请稍后重试。';
}

function matchesSearch(query: string, values: Array<string | null | undefined>): boolean {
  const normalizedQuery = query.trim().toLocaleLowerCase();
  return !normalizedQuery || values.some((value) => value?.toLocaleLowerCase().includes(normalizedQuery));
}

export function IdentityAdminPage() {
  const queryClient = useQueryClient();
  const { message } = useAppFeedback();
  const departments = useQuery({ queryKey: ['identity', 'departments'], queryFn: identityApi.departments });
  const users = useQuery({ queryKey: ['identity', 'users'], queryFn: identityApi.users });
  const superAdmins = useQuery({ queryKey: ['identity', 'super-admins'], queryFn: identityApi.superAdmins });
  const [departmentOpen, setDepartmentOpen] = useState(false);
  const [editingDepartment, setEditingDepartment] = useState<Department>();
  const [userOpen, setUserOpen] = useState(false);
  const [editingUser, setEditingUser] = useState<AdminUser>();
  const [handoverUser, setHandoverUser] = useState<AdminUser>();
  const [credential, setCredential] = useState<CreatedUser>();
  const [userSearch, setUserSearch] = useState('');
  const [departmentSearch, setDepartmentSearch] = useState('');

  const refresh = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['identity', 'departments'] }),
      queryClient.invalidateQueries({ queryKey: ['identity', 'users'] }),
      queryClient.invalidateQueries({ queryKey: ['identity', 'super-admins'] }),
    ]);
  };

  const activeUsers = (users.data ?? []).filter((user) => user.status === 'ACTIVE');
  const filteredUsers = useMemo(() => (users.data ?? []).filter((user) => matchesSearch(userSearch, [
    user.displayName,
    user.workEmail,
    user.sourceTitle,
    user.homeDepartmentName,
    user.directLeaderName,
  ])), [userSearch, users.data]);
  const removeDepartment = useMutation({
    mutationFn: identityApi.deleteDepartment,
    onSuccess: async () => { await refresh(); message.success('部门已删除'); },
    onError: async (error) => { message.error(mutationError(error)); await queryClient.invalidateQueries({ queryKey: ['identity', 'departments'] }); },
  });
  const activeDepartments = (departments.data ?? []).filter((department) => department.status === 'ACTIVE');
  const departmentNames = useMemo(() => new Map((departments.data ?? []).map((department) => [department.id, department.name])), [departments.data]);
  const filteredDepartments = useMemo(() => (departments.data ?? []).filter((department) => matchesSearch(departmentSearch, [
    department.code,
    department.name,
    departmentNames.get(department.parentDepartmentId ?? ''),
    department.assignmentQueueOwnerName,
  ])), [departmentNames, departmentSearch, departments.data]);

  const resetCredential = async (user: AdminUser) => {
    try {
      const result = await identityApi.resetCredential(user.id, '管理员手工重置');
      setCredential(result);
      await refresh();
    } catch (error) {
      message.error(mutationError(error));
    }
  };

  const disableUser = async (user: AdminUser) => {
    try {
      await identityApi.disableUser(user.id, user.version, '管理员停用账号', false);
      message.success('账号已停用，已有会话已失效');
      await refresh();
    } catch (error) {
      message.error(mutationError(error));
    }
  };

  const deleteUser = async (user: AdminUser) => {
    try {
      await identityApi.deleteUser(user.id, user.version, '管理员逻辑删除账号');
      message.success('账号已删除，历史业务记录和审计仍保留');
      await refresh();
    } catch (error) {
      message.error(mutationError(error));
    }
  };

  const userColumns = [
    {
      title: '账号',
      key: 'identity',
      width: 230,
      render: (_: unknown, user: AdminUser) => (
        <Space orientation="vertical" size={0}>
          <Typography.Text strong>{user.displayName}</Typography.Text>
          <Typography.Text type="secondary">{user.workEmail}</Typography.Text>
        </Space>
      ),
    },
    { title: '部门', dataIndex: 'homeDepartmentName', key: 'department', width: 150 },
    { title: '直属领导', dataIndex: 'directLeaderName', key: 'leader', width: 100, render: (value: string | null) => value ?? '—' },
    {
      title: '状态',
      key: 'status',
      width: 110,
      render: (_: unknown, user: AdminUser) => (
        <Space wrap>
          <Tag color={user.status === 'ACTIVE' ? 'green' : 'default'}>{user.status === 'ACTIVE' ? '启用' : '停用'}</Tag>
          {user.mustChangePassword ? <Tag className="identity-password-change-tag" color="orange">待改密</Tag> : null}
        </Space>
      ),
    },
    {
      title: '操作', className: 'table-action-cell',
      key: 'actions',
      width: 290,
      render: (_: unknown, user: AdminUser) => (
        <Space wrap size={[6, 6]}>
          <Button size="small" onClick={() => setEditingUser(user)}>编辑</Button>
          {user.status === 'ACTIVE' ? (
            <>
              <Popconfirm
                title="确认生成新的随机临时密码？"
                description="该账号现有会话将失效，下次登录必须修改密码。"
                okText="确认重置"
                cancelText="取消"
                onConfirm={() => resetCredential(user)}
              >
                <Button size="small" icon={<KeyOutlined aria-hidden="true" />}>重置凭据</Button>
              </Popconfirm>
              <Button size="small" icon={<SwapOutlined aria-hidden="true" />} onClick={() => setHandoverUser(user)}>交接</Button>
              <Popconfirm title="确认停用该账号？" description="存在必须交接事项时服务端会拒绝。" onConfirm={() => void disableUser(user)}>
                <Button size="small" danger>停用</Button>
              </Popconfirm>
            </>
          ) : null}
          <Popconfirm
            title="确认删除该账号？"
            description="仅做逻辑删除；存在直属下属、待分配队列或未交接工作时服务端会拒绝。"
            okText="确认删除"
            okButtonProps={{ danger: true }}
            onConfirm={() => void deleteUser(user)}
          >
            <Button size="small" danger icon={<DeleteOutlined aria-hidden="true" />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const departmentColumns = [
    { title: '编码', dataIndex: 'code', key: 'code' },
    { title: '名称', dataIndex: 'name', key: 'name' },
    {
      title: '上级部门',
      key: 'parent',
      render: (_: unknown, department: Department) =>
        departments.data?.find((item) => item.id === department.parentDepartmentId)?.name ?? '—',
    },
    { title: '待分配队列', dataIndex: 'assignmentQueueOwnerName', key: 'queueOwner', render: (value: string | null) => value ?? '未配置' },
    {
      title: '状态', dataIndex: 'status', key: 'status',
      render: (status: Department['status']) => <Tag color={status === 'ACTIVE' ? 'green' : 'default'}>{status === 'ACTIVE' ? '启用' : '停用'}</Tag>,
    },
    {
      title: '操作', className: 'table-action-cell',
      key: 'actions',
      render: (_: unknown, department: Department) => (
        <Space wrap>
          <Button size="small" disabled={removeDepartment.isPending} onClick={() => setEditingDepartment(department)}>编辑</Button>
          {department.status === 'ACTIVE' ? (
            <Popconfirm
              title="确认停用该部门？"
              description="存在启用账号或启用子部门时服务端会拒绝。"
              onConfirm={() => void identityApi.updateDepartment(department.id, {
                name: department.name,
                ...(department.parentDepartmentId ? { parentDepartmentId: department.parentDepartmentId } : {}),
                ...(department.assignmentQueueOwnerUserId ? { assignmentQueueOwnerUserId: department.assignmentQueueOwnerUserId } : {}),
                status: 'DISABLED',
                version: String(department.version),
              }).then(refresh).catch((error) => message.error(mutationError(error)))}
            >
              <Button size="small" danger disabled={removeDepartment.isPending}>停用</Button>
            </Popconfirm>
          ) : null}
          <Popconfirm title={`确认删除部门“${department.name}”？`} description="存在关联账号、子部门或业务记录时无法删除；历史记录保留。" okText="确认删除" cancelText="取消" okButtonProps={{ danger: true }} onConfirm={() => removeDepartment.mutateAsync(department).catch(() => undefined)}>
            <Button size="small" danger icon={<DeleteOutlined aria-hidden="true" />} loading={removeDepartment.isPending && removeDepartment.variables?.id === department.id} disabled={removeDepartment.isPending}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div className="workspace-page identity-admin-page settings-list-page">
      <section className="page-hero">
        <Typography.Title level={1}>组织与账号</Typography.Title>
        <Typography.Paragraph>管理员维护部门、直属领导、业务角色与超级管理员。全部写操作由服务端留痕并执行版本校验。</Typography.Paragraph>
      </section>
      <Tabs
        items={[
          {
            key: 'users',
            label: <span><TeamOutlined aria-hidden="true" />账号</span>,
            children: (
              <Card
                className="responsive-card-header"
                title="招聘账号"
                extra={<Space wrap><Input.Search aria-label="搜索账号" allowClear placeholder="姓名、邮箱、职位、部门或领导" value={userSearch} onChange={(event) => setUserSearch(event.target.value)} style={{ width: 320 }} /><Button type="primary" icon={<PlusOutlined aria-hidden="true" />} onClick={() => setUserOpen(true)}>注册账号</Button></Space>}
              >
                <PaginatedTable rowKey="id" loading={users.isPending} dataSource={filteredUsers} columns={userColumns} scroll={{ x: 880 }} paginationLabel="招聘账号分页" />
              </Card>
            ),
          },
          {
            key: 'departments',
            label: <span><ApartmentOutlined aria-hidden="true" />部门</span>,
            children: (
              <Card className="responsive-card-header" title="组织结构" extra={<Space wrap><Input.Search aria-label="搜索组织" allowClear placeholder="名称、编码、上级部门或队列负责人" value={departmentSearch} onChange={(event) => setDepartmentSearch(event.target.value)} style={{ width: 320 }} /><Button type="primary" icon={<PlusOutlined aria-hidden="true" />} onClick={() => setDepartmentOpen(true)}>新建部门</Button></Space>}>
                <PaginatedTable rowKey="id" loading={departments.isPending} dataSource={filteredDepartments} columns={departmentColumns} />
              </Card>
            ),
          },
          {
            key: 'wecom-directory',
            label: <span><CloudSyncOutlined aria-hidden="true" />企业微信目录</span>,
            children: <WecomDirectoryPanel users={activeUsers} departments={activeDepartments} />,
          },
          {
            key: 'super-admins',
            label: <span><SafetyCertificateOutlined aria-hidden="true" />超级管理员</span>,
            children: (
              <SuperAdminPanel
                users={activeUsers}
                superAdmins={superAdmins.data ?? []}
                loading={superAdmins.isPending}
                onChanged={refresh}
              />
            ),
          },
        ]}
      />
      <DepartmentModal
        open={departmentOpen || Boolean(editingDepartment)}
        editing={editingDepartment}
        departments={activeDepartments}
        users={activeUsers}
        onClose={() => { setDepartmentOpen(false); setEditingDepartment(undefined); }}
        onSaved={refresh}
      />
    <WorkspaceDialogGroup>
      <UserModal
        open={userOpen || Boolean(editingUser)}
        editing={editingUser}
        departments={activeDepartments}
        users={activeUsers}
        onClose={() => { setUserOpen(false); setEditingUser(undefined); }}
        onCreated={(result) => setCredential(result)}
        onSaved={refresh}
      />
      {handoverUser?<HandoverModal key={handoverUser.id}
        source={handoverUser}
        users={activeUsers}
        onClose={() => setHandoverUser(undefined)}
        onCompleted={refresh}
      />:null}
      <Modal title="临时登录密码" open={Boolean(credential)} footer={<Button type="primary" onClick={() => setCredential(undefined)}>我已知悉</Button>} closable={false}>
        <Alert showIcon type="warning" title="已生成随机临时密码" description="仅本次显示，请复制并通过安全渠道告知本人；首次登录或重置后必须修改密码。" />
        <Descriptions column={1} className="credential-details" items={[
          { key: 'user', label: '账号 ID', children: credential?.userId },
          { key: 'secret', label: '临时登录密码', children: <Typography.Text copyable={{ tooltips: ['复制临时密码', '已复制'] }} code>{credential?.oneTimeCredential}</Typography.Text> },
        ]} />
      </Modal>
    </WorkspaceDialogGroup>
    </div>
  );
}

function WecomDirectoryPanel({ users, departments }: { users: AdminUser[]; departments: Department[] }) {
  const queryClient = useQueryClient();
  const { message } = useAppFeedback();
  const [search, setSearch] = useState('');
  const [selectedAccounts, setSelectedAccounts] = useState<Record<string, string | null>>({});
  const [selectedDepartments, setSelectedDepartments] = useState<Record<string, string | null>>({});
  const directory = useQuery({ queryKey: ['identity', 'wecom-directory'], queryFn: identityApi.wecomDirectory, retry: false });
  const link = useMutation({
    mutationFn: ({ memberId, userAccountId }: { memberId: string; userAccountId: string }) => identityApi.linkWecomAccount(memberId, userAccountId),
    onSuccess: async () => { message.success('已关联本地账号'); await queryClient.invalidateQueries({ queryKey: ['identity', 'wecom-directory'] }); },
    onError: (error) => message.error(mutationError(error)),
  });
  const unlink = useMutation({
    mutationFn: identityApi.unlinkWecomAccount,
    onSuccess: async () => { message.success('已解除账号关联'); await queryClient.invalidateQueries({ queryKey: ['identity', 'wecom-directory'] }); },
    onError: (error) => message.error(mutationError(error)),
  });
  const linkDepartment = useMutation({
    mutationFn: ({ departmentId, localDepartmentId }: { departmentId: string; localDepartmentId: string }) => identityApi.linkWecomDepartment(departmentId, localDepartmentId),
    onSuccess: async () => { message.success('已关联本地部门'); await queryClient.invalidateQueries({ queryKey: ['identity', 'wecom-directory'] }); },
    onError: (error) => message.error(mutationError(error)),
  });
  const unlinkDepartment = useMutation({
    mutationFn: identityApi.unlinkWecomDepartment,
    onSuccess: async () => { message.success('已解除部门关联'); await queryClient.invalidateQueries({ queryKey: ['identity', 'wecom-directory'] }); },
    onError: (error) => message.error(mutationError(error)),
  });
  const departmentNames = useMemo(() => new Map((directory.data?.departments ?? []).map((item) => [item.departmentId, item.name])), [directory.data]);
  const normalizedSearch = search.trim().toLocaleLowerCase();
  const members = useMemo(() => (directory.data?.members ?? []).filter((member) => !normalizedSearch ||
    [member.name, member.businessEmail, member.position, member.linkedUserDisplayName].some((value) => value?.toLocaleLowerCase().includes(normalizedSearch))), [directory.data, normalizedSearch]);
  const availableAccounts = (member: WecomStoredMember) => users.filter((user) => !directory.data?.members.some((item) => item.id !== member.id && item.linkedUserAccountId === user.id));
  const columns = [
    { title: '来源成员', key: 'member', width: 220, render: (_: unknown, member: WecomStoredMember) => <Space orientation="vertical" size={0}><Typography.Text strong>{member.name}</Typography.Text><Typography.Text type="secondary">{member.businessEmail ?? member.userId}</Typography.Text></Space> },
    { title: '职位', dataIndex: 'position', key: 'position', width: 180, render: (value: string | null) => value ?? '—' },
    { title: '来源部门', key: 'departments', width: 260, render: (_: unknown, member: WecomStoredMember) => member.departmentIds.map((id) => departmentNames.get(id) ?? `部门 ${id}`).join('、') },
    { title: '本地账号', key: 'account', width: 280, render: (_: unknown, member: WecomStoredMember) => member.linkedUserAccountId
      ? <Space orientation="vertical" size={0}><Tag color="success">已关联</Tag><Typography.Text>{member.linkedUserDisplayName} · {member.linkedUserWorkEmail}</Typography.Text></Space>
      : <Select showSearch optionFilterProp="label" placeholder="选择现有账号" style={{ width: 250 }} value={selectedAccounts[member.id] ?? null} onChange={(value) => setSelectedAccounts((current) => ({ ...current, [member.id]: value }))} options={availableAccounts(member).map((user) => ({ value: user.id, label: `${user.displayName} · ${user.workEmail}` }))} /> },
    { title: '操作', className: 'table-action-cell', key: 'actions', width: 120, render: (_: unknown, member: WecomStoredMember) => member.linkedUserAccountId
      ? <Popconfirm title="解除该来源成员与本地账号的关联？" onConfirm={() => unlink.mutateAsync(member.id).catch(() => undefined)}><Button size="small" danger icon={<DisconnectOutlined aria-hidden="true" />}>解除</Button></Popconfirm>
      : <Button size="small" type="primary" disabled={!selectedAccounts[member.id]} loading={link.isPending && link.variables?.memberId === member.id} onClick={() => void link.mutateAsync({ memberId: member.id, userAccountId: selectedAccounts[member.id] as string }).catch(() => undefined)}>确认关联</Button> },
  ];
  const departmentColumns = [
    { title: '来源部门', dataIndex: 'name', key: 'name', width: 260 },
    { title: '来源上级', key: 'parent', width: 220, render: (_: unknown, department: WecomStoredDepartment) => departmentNames.get(department.parentId) ?? (department.parentOutsideScope ? '授权范围外' : '—') },
    { title: '本地部门', key: 'local', width: 300, render: (_: unknown, source: WecomStoredDepartment) => source.linkedLocalDepartmentId
      ? <Space><Tag color="success">已关联</Tag><Typography.Text>{source.linkedLocalDepartmentName}</Typography.Text></Space>
      : <Select showSearch optionFilterProp="label" placeholder="选择本地部门" style={{ width: 260 }} value={selectedDepartments[source.id] ?? null} onChange={(value) => setSelectedDepartments((current) => ({ ...current, [source.id]: value }))} options={departments.map((department) => ({ value: department.id, label: department.name }))} /> },
    { title: '操作', className: 'table-action-cell', key: 'actions', width: 120, render: (_: unknown, source: WecomStoredDepartment) => source.linkedLocalDepartmentId
      ? <Popconfirm title="解除来源部门与本地部门的关联？" onConfirm={() => unlinkDepartment.mutateAsync(source.id).catch(() => undefined)}><Button size="small" danger icon={<DisconnectOutlined aria-hidden="true" />}>解除</Button></Popconfirm>
      : <Button size="small" type="primary" disabled={!selectedDepartments[source.id]} loading={linkDepartment.isPending && linkDepartment.variables?.departmentId === source.id} onClick={() => void linkDepartment.mutateAsync({ departmentId: source.id, localDepartmentId: selectedDepartments[source.id] as string }).catch(() => undefined)}>确认关联</Button> },
  ];
  if (directory.isError) return <Alert occurrence={directory.error} type="error" showIcon title="企业微信来源目录加载失败" action={<Button onClick={() => void directory.refetch()}>重试</Button>} />;
  return <Card title={`来源通讯录${directory.data ? ` · ${directory.data.departments.length} 个部门 / ${directory.data.members.length} 人` : ''}`} extra={<Input.Search allowClear placeholder="姓名、邮箱、职位或本地账号" onSearch={setSearch} onChange={(event) => { if (!event.target.value) setSearch(''); }} style={{ width: 300 }} />}>
    <Alert type="info" showIcon title="来源目录与登录权限分开管理" description="关联只确认人员身份，不创建账号、不改变部门、角色或数据权限。" />
    <Tabs items={[
      { key: 'members', label: `成员 ${directory.data?.members.length ?? 0}`, children: <PaginatedTable rowKey="id" loading={directory.isPending} dataSource={members} columns={columns} scroll={{ x: 1080 }} /> },
      { key: 'departments', label: `部门 ${directory.data?.departments.length ?? 0}`, children: <PaginatedTable rowKey="id" loading={directory.isPending} dataSource={directory.data?.departments ?? []} columns={departmentColumns} scroll={{ x: 900 }} /> },
    ]} />
  </Card>;
}

function DepartmentModal({ open, editing, departments, users, onClose, onSaved }: { open: boolean; editing: Department | undefined; departments: Department[]; users: AdminUser[]; onClose: () => void; onSaved: () => Promise<void> }) {
  const [form] = Form.useForm<{ name: string; parentDepartmentId?: string; assignmentQueueOwnerUserId?: string }>();
  const { message } = useAppFeedback();
  const mutation = useMutation({
    mutationFn: async (values: { name: string; parentDepartmentId?: string; assignmentQueueOwnerUserId?: string }) => editing
      ? identityApi.updateDepartment(editing.id, {
          name: values.name,
          ...(values.parentDepartmentId ? { parentDepartmentId: values.parentDepartmentId } : {}),
          ...(values.assignmentQueueOwnerUserId ? { assignmentQueueOwnerUserId: values.assignmentQueueOwnerUserId } : {}),
          status: editing.status,
          version: String(editing.version),
        })
      : identityApi.createDepartment({ name: values.name, ...(values.parentDepartmentId ? { parentDepartmentId: values.parentDepartmentId } : {}) }),
  });
  const submit = async () => {
    try {
      await mutation.mutateAsync(await form.validateFields());
      message.success(editing ? '部门已更新' : '部门已创建');
      form.resetFields();
      onClose();
      await onSaved();
    } catch (error) {
      if (error instanceof ApiProblemError) message.error(error.problem.detail);
      else throw error;
    }
  };
  return (
    <Modal okText="保存部门" title={editing ? '编辑部门' : '新建部门'} open={open} onCancel={onClose} onOk={() => submit()} confirmLoading={mutation.isPending} afterOpenChange={(visible) => { if (visible) form.setFieldsValue(editing ? { name: editing.name, ...(editing.parentDepartmentId ? { parentDepartmentId: editing.parentDepartmentId } : {}), ...(editing.assignmentQueueOwnerUserId ? { assignmentQueueOwnerUserId: editing.assignmentQueueOwnerUserId } : {}) } : {}); else form.resetFields(); }}>
      <Form form={form} layout="vertical">
        <Form.Item label="部门名称" name="name" rules={[{ required: true }]}><Input /></Form.Item>
        <Form.Item label="上级部门" name="parentDepartmentId"><Select allowClear options={departments.filter((item) => item.id !== editing?.id).map((item) => ({ label: item.name, value: item.id }))} /></Form.Item>
        {editing ? <Form.Item label="待分配队列负责人" name="assignmentQueueOwnerUserId" extra="自动入库无法映射招聘人时，任务进入该账号的待分配队列。"><Select allowClear showSearch optionFilterProp="label" options={users.map((user) => ({ label: `${user.displayName} · ${user.workEmail}`, value: user.id }))} /></Form.Item> : null}
      </Form>
    </Modal>
  );
}

interface UserModalProps {
  open: boolean;
  editing: AdminUser | undefined;
  departments: Department[];
  users: AdminUser[];
  onClose: () => void;
  onCreated: (result: CreatedUser) => void;
  onSaved: () => Promise<void>;
}
export function UserModal(props: UserModalProps) {
  const assignments = useQuery({
    queryKey: ['identity', 'organization-assignments', props.editing?.id],
    queryFn: () => identityApi.organizationAssignments(props.editing!.id),
    enabled: Boolean(props.open && props.editing), retry: false,
  });
  if (!props.open) return null;
  if (props.editing && (assignments.isPending || assignments.isError)) return <Modal title="编辑账号" open onCancel={props.onClose} footer={null}>
    {assignments.isError ? <Alert occurrence={assignments.error} type="error" showIcon title="账号授权资料加载失败，读取成功后才能保存。" action={<Button onClick={()=>void assignments.refetch()}>重试授权资料</Button>}/> : <Alert type="info" showIcon title="正在读取账号角色和支持部门…"/>}
  </Modal>;
  return <UserFormModal {...props} assignmentsData={assignments.data}/>;
}
function UserFormModal({ open, editing, departments, users, onClose, onCreated, onSaved, assignmentsData }: UserModalProps & {assignmentsData: UserOrganizationAssignments | undefined}) {
  interface UserFormValues {
    displayName: string;
    workEmail: string;
    sourceTitle?: string;
    homeDepartmentId: string;
    directLeaderUserId?: string | undefined;
    supportDepartmentIds?: string[];
    roles?: string[];
    reason: string;
  }
  const [form] = Form.useForm<UserFormValues>();
  const { message } = useAppFeedback();
  const submit = async () => {
    try {
      if (editing && !assignmentsData) return;
      const values = await form.validateFields();
      if (editing) {
        await identityApi.updateUser(editing.id, {
          displayName: values.displayName,
          workEmail: values.workEmail,
          sourceTitle: values.sourceTitle,
          homeDepartmentId: values.homeDepartmentId,
          directLeaderUserId: values.directLeaderUserId,
          supportDepartmentIds: values.supportDepartmentIds ?? [],
          version: String(editing.version),
          reason: values.reason,
        });
        const before = new Set((assignmentsData?.roles ?? []).filter((role) => role !== 'SUPER_ADMIN'));
        const after = new Set(values.roles ?? []);
        for (const role of after) {
          if (!before.has(role)) await identityApi.assignRole(editing.id, role, values.reason);
        }
        for (const role of before) {
          if (!after.has(role)) await identityApi.revokeRole(editing.id, role, values.reason);
        }
        message.success('账号已更新');
      } else {
        const result = await identityApi.createUser({
          displayName: values.displayName,
          workEmail: values.workEmail,
          sourceTitle: values.sourceTitle,
          homeDepartmentId: values.homeDepartmentId,
          directLeaderUserId: values.directLeaderUserId,
          supportDepartmentIds: values.supportDepartmentIds ?? [],
          roles: values.roles ?? [],
          reason: values.reason,
        });
        onCreated(result);
      }
      form.resetFields();
      onClose();
      await onSaved();
    } catch (error) {
      if (error instanceof ApiProblemError) message.error(error.problem.detail);
      else throw error;
    }
  };
  const initialValues = useMemo<Partial<UserFormValues>>(() => editing ? {
    displayName: editing.displayName,
    workEmail: editing.workEmail,
    sourceTitle: editing.sourceTitle ?? '',
    homeDepartmentId: editing.homeDepartmentId,
    directLeaderUserId: editing.directLeaderUserId ?? undefined,
    supportDepartmentIds: assignmentsData?.supportDepartmentIds ?? [],
    roles: (assignmentsData?.roles ?? []).filter(role => role !== 'SUPER_ADMIN'),
    reason: '管理员更新账号资料',
  } : { displayName: '', workEmail: '', sourceTitle: '', homeDepartmentId: '', directLeaderUserId: undefined, roles: ['RECRUITER'], supportDepartmentIds: [], reason: '新员工直接注册' }, [editing, assignmentsData]);
  useEffect(() => { if (open) form.setFieldsValue(initialValues); }, [open, initialValues, form]);
  return (
    <Modal okText="保存账号" title={editing ? '编辑账号' : '注册账号'} open={open} onCancel={onClose} onOk={() => submit()} afterOpenChange={(visible) => { if (visible) form.setFieldsValue(initialValues); else form.resetFields(); }} width={620}>
      <Form form={form} layout="vertical" initialValues={initialValues}>
        <Form.Item label="姓名" name="displayName" rules={[{ required: true }]}><Input /></Form.Item>
        <Form.Item label="工作邮箱" name="workEmail" normalize={(value: string) => value.trim().toLowerCase()} rules={[{ required: true }, { type: 'email', max: 320 }]}><Input /></Form.Item>
        <Form.Item label="岗位名称" name="sourceTitle"><Input /></Form.Item>
        <Form.Item label="所属部门" name="homeDepartmentId" rules={[{ required: true }]}><Select showSearch optionFilterProp="label" options={departments.map((item) => ({ label: item.name, value: item.id }))} /></Form.Item>
        <Form.Item label="直属领导" name="directLeaderUserId"><Select allowClear showSearch optionFilterProp="label" options={users.filter((user) => user.id !== editing?.id).map((user) => ({ label: `${user.displayName} · ${user.workEmail}`, value: user.id }))} /></Form.Item>
        <Form.Item label="支持部门" name="supportDepartmentIds"><Select mode="multiple" showSearch optionFilterProp="label" options={departments.map((item) => ({ label: item.name, value: item.id }))} /></Form.Item>
        <Form.Item label="业务角色（至少一项）" name="roles" rules={[{ validator: (_, value: string[] | undefined) => value?.length ? Promise.resolve() : Promise.reject(new Error('至少选择一个业务角色')) }]}><Checkbox.Group options={ROLE_OPTIONS} /></Form.Item>
        <Form.Item label="原因" name="reason" rules={[{ required: true }]}><Input.TextArea rows={2} maxLength={500} showCount /></Form.Item>
      </Form>
    </Modal>
  );
}

function SuperAdminPanel({ users, superAdmins, loading, onChanged }: { users: AdminUser[]; superAdmins: AdminUser[]; loading: boolean; onChanged: () => Promise<void> }) {
  const { message } = useAppFeedback();
  const [selected, setSelected] = useState<string>();
  const grant = async () => {
    if (!selected) return;
    try {
      await identityApi.grantSuperAdmin(selected, '管理员授权');
      setSelected(undefined);
      message.success('已授予超级管理员');
      await onChanged();
    } catch (error) { message.error(mutationError(error)); }
  };
  return (
    <Card title="超级管理员" extra={<Space><Select value={selected} onChange={setSelected} placeholder="选择启用账号" style={{ width: 240 }} options={users.filter((user) => !superAdmins.some((admin) => admin.id === user.id)).map((user) => ({ label: user.displayName, value: user.id }))} /><Button type="primary" onClick={() => void grant()} disabled={!selected}>授权</Button></Space>}>
      <Alert showIcon type="info" title="至少保留一名启用的超级管理员" description="服务端在事务内锁定并校验，不能通过并发撤权降为零。" />
      <Table rowKey="id" loading={loading} dataSource={superAdmins} pagination={false} columns={[
        { title: '姓名', dataIndex: 'displayName' },
        { title: '工作邮箱', dataIndex: 'workEmail' },
        { title: '状态', dataIndex: 'status' },
        { title: '操作', className: 'table-action-cell', render: (_: unknown, user: AdminUser) => <Popconfirm title="确认撤销超级管理员？" onConfirm={() => void identityApi.revokeSuperAdmin(user.id, '管理员撤权').then(onChanged).catch((error) => message.error(mutationError(error)))}><Button danger size="small">撤销</Button></Popconfirm> },
      ]} />
    </Card>
  );
}

function HandoverModal({ source, users, onClose, onCompleted }: { source: AdminUser | undefined; users: AdminUser[]; onClose: () => void; onCompleted: () => Promise<void> }) {
  const { message } = useAppFeedback();
  const [targetUserId, setTargetUserId] = useState<string>();
  const [preview, setPreview] = useState<HandoverPreview>();
  const [selected, setSelected] = useState<string[]>([]);
  const [reason, setReason] = useState('人员工作交接');
  const revision=useRef(0);
  const loadingRevision=useRef<number|undefined>(undefined);
  const executing=useRef(false);
  const [loading,setLoading]=useState(false);
  const [saving,setSaving]=useState(false);
  useEffect(()=>()=>{revision.current++;},[]);
  const matchingPreview=preview?.sourceUserId===source?.id&&preview?.targetUserId===targetUserId;
  const load = async () => {
    if (!source || !targetUserId || loadingRevision.current===revision.current || executing.current) return;
    const requestRevision=revision.current;
    loadingRevision.current=requestRevision;setLoading(true);
    try {
      const result = await identityApi.previewHandover(source.id, targetUserId);
      if(requestRevision!==revision.current)return;
      if(result.sourceUserId!==source.id||result.targetUserId!==targetUserId){setPreview(undefined);setSelected([]);throw new Error('交接预览与当前人员不一致，请重新生成预览');}
      setPreview(result);
      const available=result.resources.filter((item) => item.transferable).map((item) => `${item.resourceType}:${item.resourceId}`);
      setSelected(matchingPreview?selected.filter(key=>available.includes(key)):available);
    } catch (error) { if(requestRevision===revision.current)message.error(mutationError(error)); }
    finally{if(requestRevision===revision.current){loadingRevision.current=undefined;setLoading(false);}}
  };
  const execute = async () => {
    if (!source || !targetUserId || !matchingPreview || loadingRevision.current===revision.current || executing.current) return;
    executing.current=true;setSaving(true);
    try {
      const result = await identityApi.executeHandover(source.id, { targetUserId, selectionMode: 'SELECTED', selectedResourceKeys: selected, reason });
      message.success(`交接完成：成功 ${result.batch.succeededCount}，失败 ${result.batch.failedCount}，未解决 ${result.batch.unresolvedCount}`);
      onClose();
      setPreview(undefined);
      await onCompleted();
    } catch (error) { message.error(mutationError(error)); }
    finally{executing.current=false;setSaving(false);}
  };
  return (
    <Modal title={`工作交接${source ? ` · ${source.displayName}` : ''}`} open={Boolean(source)} onCancel={onClose} closable={!saving} keyboard={!saving} mask={{closable:!saving}} width={720} footer={[
      <Button key="cancel" disabled={saving} onClick={onClose}>取消</Button>,
      <Button key="preview" loading={loading} onClick={() => void load()} disabled={!targetUserId||loading||saving}>生成预览</Button>,
      <Button key="execute" type="primary" loading={saving} onClick={() => void execute()} disabled={!matchingPreview||loading||saving}>执行勾选交接</Button>,
    ]}>
      <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
        <Select aria-label="交接接收人" disabled={saving} placeholder="选择接收人" value={targetUserId} onChange={(value) => { revision.current++;loadingRevision.current=undefined;setLoading(false);setTargetUserId(value); setPreview(undefined);setSelected([]); }} options={users.filter((user) => user.id !== source?.id).map((user) => ({ label: `${user.displayName} · ${user.homeDepartmentName}`, value: user.id }))} />
        <Input.TextArea aria-label="交接原因" disabled={saving} value={reason} onChange={(event) => setReason(event.target.value)} rows={2} maxLength={500} showCount />
        {preview ? preview.resources.length ? (
          <Checkbox.Group disabled={loading||saving} value={selected} onChange={(values) => setSelected(values.map(String))} style={{ display: 'grid', gap: 10 }}>
            {preview.resources.map((item) => {
              const key = `${item.resourceType}:${item.resourceId}`;
              return <Checkbox key={key} value={key} disabled={!item.transferable}>{item.displayName} <Tag>{item.resourceType}</Tag>{item.mandatory ? <Tag color="red">停用前必须交接</Tag> : null}{!item.transferable ? <Typography.Text type="danger">{item.unresolvedReason}</Typography.Text> : null}</Checkbox>;
            })}
          </Checkbox.Group>
        ) : <Alert showIcon type="success" title="当前没有待交接事项" /> : null}
      </Space>
    </Modal>
  );
}
