import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import zhCN from 'antd/locale/zh_CN';
import { afterEach, expect, it, vi } from 'vitest';
import { IdentityAdminPage } from './IdentityAdminPage';
import { identityApi } from '../identity/api';

afterEach(()=>{cleanup();vi.restoreAllMocks();vi.unstubAllGlobals();});

it('fuzzy filters accounts and organizations across their displayed fields', async () => {
  const departments = [
    { id: 'root', code: 'ROOT', name: '总部', parentDepartmentId: null, assignmentQueueOwnerUserId: null, assignmentQueueOwnerName: null, status: 'ACTIVE' as const, version: 1 },
    { id: 'cloud', code: 'CLOUD', name: '示例技术部门', parentDepartmentId: 'root', assignmentQueueOwnerUserId: 'user-1', assignmentQueueOwnerName: '负责人甲', status: 'ACTIVE' as const, version: 1 },
    { id: 'global', code: 'GLOBAL', name: '示例运营部门', parentDepartmentId: 'root', assignmentQueueOwnerUserId: 'user-2', assignmentQueueOwnerName: '负责人乙', status: 'ACTIVE' as const, version: 1 },
  ];
  const users = [
    { id: 'user-1', displayName: '张三', workEmail: 'zhang@example.com', sourceTitle: '招聘顾问', homeDepartmentId: 'cloud', homeDepartmentName: '示例技术部门', directLeaderUserId: null, directLeaderName: '王经理', status: 'ACTIVE' as const, mustChangePassword: false, version: 1 },
    { id: 'user-2', displayName: '李四', workEmail: 'li@example.com', sourceTitle: '招聘专员', homeDepartmentId: 'global', homeDepartmentName: '示例运营部门', directLeaderUserId: null, directLeaderName: '赵经理', status: 'ACTIVE' as const, mustChangePassword: false, version: 1 },
  ];
  vi.spyOn(identityApi, 'departments').mockResolvedValue(departments);
  vi.spyOn(identityApi, 'users').mockResolvedValue(users);
  vi.spyOn(identityApi, 'superAdmins').mockResolvedValue([]);
  render(<ConfigProvider theme={{ token: { motion: false } }}><App><QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><IdentityAdminPage /></QueryClientProvider></App></ConfigProvider>);

  expect(await screen.findByRole('tab', { name: '账号' })).toBeInTheDocument();
  expect(screen.getByRole('tab', { name: '部门' })).toBeInTheDocument();
  expect(screen.getByRole('tab', { name: '企业微信目录' })).toBeInTheDocument();
  expect(screen.getByRole('tab', { name: '超级管理员' })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '注册账号' })).toBeInTheDocument();
  await screen.findByText('张三');
  expect(screen.getAllByRole('button', { name: '重置凭据' })).toHaveLength(2);
  expect(screen.getAllByRole('button', { name: '交接' })).toHaveLength(2);

  const accountSearch = await screen.findByLabelText('搜索账号');
  fireEvent.change(accountSearch, { target: { value: '技术' } });
  const accountCard = accountSearch.closest<HTMLElement>('.ant-card')!;
  expect(within(accountCard).getByText('张三')).toBeInTheDocument();
  expect(within(accountCard).queryByText('李四')).not.toBeInTheDocument();

  fireEvent.click(screen.getByRole('tab', { name: '部门' }));
  expect(screen.getByRole('button', { name: '新建部门' })).toBeInTheDocument();
  const organizationSearch = screen.getByLabelText('搜索组织');
  fireEvent.change(organizationSearch, { target: { value: '负责人乙' } });
  const organizationCard = organizationSearch.closest<HTMLElement>('.ant-card')!;
  expect(within(organizationCard).getByText('示例运营部门')).toBeInTheDocument();
  expect(within(organizationCard).queryByText('示例技术部门')).not.toBeInTheDocument();
});

it('confirms a random-password reset and copies the returned credential', async () => {
  Object.defineProperty(HTMLElement.prototype, 'scrollTo', { configurable: true, value: vi.fn() });
  const account = { id: 'user-1', displayName: '张三', workEmail: 'zhang@example.com', sourceTitle: null, homeDepartmentId: 'dep', homeDepartmentName: '招聘部', directLeaderUserId: null, directLeaderName: null, status: 'ACTIVE' as const, mustChangePassword: false, version: 1 };
  vi.spyOn(identityApi, 'departments').mockResolvedValue([]);
  vi.spyOn(identityApi, 'users').mockResolvedValue([account]);
  vi.spyOn(identityApi, 'superAdmins').mockResolvedValue([]);
  const temporaryPassword = 'Synthetic_Reset_Credential_12345';
  const writeText = vi.fn().mockResolvedValue(undefined);
  vi.stubGlobal('navigator', { clipboard: { writeText } });
  const reset = vi.spyOn(identityApi, 'resetCredential').mockResolvedValue({
    userId: account.id, oneTimeCredential: temporaryPassword,
    mustChangePassword: true, version: '2',
  });
  render(<ConfigProvider locale={zhCN} theme={{ token: { motion: false } }}><App><QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><IdentityAdminPage /></QueryClientProvider></App></ConfigProvider>);

  fireEvent.click(await screen.findByRole('button', { name: '重置凭据' }, { timeout: 5_000 }));
  expect(reset).not.toHaveBeenCalled();
  expect(screen.getByText('确认生成新的随机临时密码？')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '确认重置' }));
  await waitFor(() => expect(reset).toHaveBeenCalledWith(account.id, '管理员手工重置'), { timeout: 5_000 });
  const policy = await screen.findByText('已生成随机临时密码', {}, { timeout: 5_000 });
  const dialog = policy.closest<HTMLElement>('[role="dialog"]');
  expect(dialog).not.toBeNull();
  expect(within(dialog!).getByText(temporaryPassword)).toBeInTheDocument();
  expect(within(dialog!).queryByText(account.workEmail)).not.toBeInTheDocument();
  fireEvent.click(within(dialog!).getByRole('button', { name: '复制临时密码' }));
  await waitFor(() => expect(writeText).toHaveBeenCalledWith(temporaryPassword));
  fireEvent.click(within(dialog!).getByRole('button', { name: '我已知悉' }));
  await waitFor(() => expect(screen.queryByText(temporaryPassword)).not.toBeInTheDocument());
});

it('shows the enterprise directory and links a source member to an existing account', async () => {
  Object.defineProperty(HTMLElement.prototype, 'scrollTo', { configurable: true, value: vi.fn() });
  const account = { id: '01H00000000000000000000001', displayName: '张三', workEmail: 'zhang@example.com', sourceTitle: null, homeDepartmentId: 'dep', homeDepartmentName: '招聘部', directLeaderUserId: null, directLeaderName: null, status: 'ACTIVE' as const, mustChangePassword: false, version: 1 };
  vi.spyOn(identityApi, 'departments').mockResolvedValue([]);
  vi.spyOn(identityApi, 'users').mockResolvedValue([account]);
  vi.spyOn(identityApi, 'superAdmins').mockResolvedValue([]);
  vi.spyOn(identityApi, 'wecomDirectory').mockResolvedValue({ corpId: 'corp', publishedBatchId: 'batch', departments: [{ id: 'wd', departmentId: 10, parentId: 1, name: '人才中心', parentOutsideScope: false, linkedLocalDepartmentId: null, linkedLocalDepartmentName: null }], members: [{ id: '01H00000000000000000000002', userId: 'wecom-zhang', name: '张三', position: '招聘专员', businessEmail: 'zhang@example.com', departmentIds: [10], directoryStatus: 'VISIBLE', linkedUserAccountId: null, linkedUserDisplayName: null, linkedUserWorkEmail: null }] });
  const link = vi.spyOn(identityApi, 'linkWecomAccount').mockResolvedValue(undefined);
  render(<ConfigProvider locale={zhCN} theme={{ token: { motion: false } }}><App><QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><IdentityAdminPage /></QueryClientProvider></App></ConfigProvider>);

  fireEvent.click(await screen.findByRole('tab', { name: /企业微信目录/ }));
  expect(await screen.findByText('人才中心')).toBeInTheDocument();
  fireEvent.mouseDown(screen.getByRole('combobox'));
  fireEvent.click(await screen.findByText('张三 · zhang@example.com'));
  fireEvent.click(screen.getByRole('button', { name: '确认关联' }));

  await waitFor(() => expect(link).toHaveBeenCalledWith('01H00000000000000000000002', account.id));
}, 20_000);

it.each(['A','C'])('discards pending handover preview when reopening source %s',async reopened=>{
  Object.defineProperty(HTMLElement.prototype,'scrollTo',{configurable:true,value:vi.fn()});
  const users=['A','B','C'].map(id=>({id,displayName:`账号${id}`,workEmail:`${id}@example.invalid`,sourceTitle:null,homeDepartmentId:'dep',homeDepartmentName:'部门',directLeaderUserId:null,directLeaderName:null,status:'ACTIVE' as const,mustChangePassword:false,version:1}));
  vi.spyOn(identityApi,'departments').mockResolvedValue([]);vi.spyOn(identityApi,'users').mockResolvedValue(users);vi.spyOn(identityApi,'superAdmins').mockResolvedValue([]);
  let resolveOld!:(value:Awaited<ReturnType<typeof identityApi.previewHandover>>)=>void;
  const preview=vi.spyOn(identityApi,'previewHandover').mockImplementation(()=>new Promise(resolve=>{resolveOld=resolve;}));
  render(<ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><IdentityAdminPage/></QueryClientProvider></App></ConfigProvider>);
  fireEvent.click(within((await screen.findByText('账号A')).closest('tr')!).getByRole('button',{name:/交接/}));
  const dialog=await screen.findByRole('dialog');
  fireEvent.mouseDown(within(dialog).getByRole('combobox',{name:'交接接收人'}));fireEvent.click(await screen.findByText('账号B · 部门'));
  fireEvent.click(within(dialog).getByRole('button',{name:'生成预览'}));
  await waitFor(()=>expect(preview).toHaveBeenCalledOnce());
  fireEvent.click(within(dialog).getByRole('button',{name:/取\s*消/}));
  fireEvent.click(within(screen.getByText(`账号${reopened}`).closest('tr')!).getByRole('button',{name:/交接/}));
  const reopenedDialog=await screen.findByRole('dialog');
  expect(within(reopenedDialog).getByRole('combobox',{name:'交接接收人'})).toBeInTheDocument();
  await act(()=>{resolveOld({sourceUserId:'A',targetUserId:'B',resources:[{resourceType:'CANDIDATE',resourceId:'old',displayName:'已关闭会话资源',mandatory:false,transferable:true,unresolvedReason:null}],mandatoryCount:0,unresolvedCount:0});return Promise.resolve();});
  expect(within(reopenedDialog).getByRole('button',{name:'生成预览'})).toBeDisabled();
  expect(within(reopenedDialog).getByRole('button',{name:'执行勾选交接'})).toBeDisabled();
  expect(within(reopenedDialog).queryByText('已关闭会话资源')).not.toBeInTheDocument();
},20_000);

it('rejects a late handover preview after changing recipient and preserves explicit selections',async()=>{
  Object.defineProperty(HTMLElement.prototype,'scrollTo',{configurable:true,value:vi.fn()});
  const users=['A','B','C'].map(id=>({id,displayName:`账号${id}`,workEmail:`${id}@example.invalid`,sourceTitle:null,homeDepartmentId:'dep',homeDepartmentName:'部门',directLeaderUserId:null,directLeaderName:null,status:'ACTIVE' as const,mustChangePassword:false,version:1}));
  vi.spyOn(identityApi,'departments').mockResolvedValue([]);
  vi.spyOn(identityApi,'users').mockResolvedValue(users);
  vi.spyOn(identityApi,'superAdmins').mockResolvedValue([]);
  let resolveOld!:(value:Awaited<ReturnType<typeof identityApi.previewHandover>>)=>void;
  const resource={resourceType:'CANDIDATE',resourceId:'one',displayName:'旧接收人资源',mandatory:false,transferable:true,unresolvedReason:null};
  const preview=vi.spyOn(identityApi,'previewHandover').mockImplementationOnce(()=>new Promise(resolve=>{resolveOld=resolve;})).mockResolvedValue({sourceUserId:'A',targetUserId:'C',resources:[{...resource,displayName:'可选资源一'},{...resource,resourceId:'two',displayName:'可选资源二'}],mandatoryCount:0,unresolvedCount:0});
  const execute=vi.spyOn(identityApi,'executeHandover').mockImplementation(()=>new Promise(()=>undefined));
  render(<ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><IdentityAdminPage/></QueryClientProvider></App></ConfigProvider>);
  fireEvent.click(within((await screen.findByText('账号A')).closest('tr')!).getByRole('button',{name:/交接/}));
  const dialog=await screen.findByRole('dialog');
  const select=within(dialog).getByRole('combobox');
  fireEvent.mouseDown(select);fireEvent.click(await screen.findByText('账号B · 部门'));
  fireEvent.click(within(dialog).getByRole('button',{name:'生成预览'}));
  await waitFor(()=>expect(preview).toHaveBeenCalledWith('A','B'));
  fireEvent.mouseDown(select);fireEvent.click(await screen.findByText('账号C · 部门'));
  expect(within(dialog).getByRole('button',{name:'执行勾选交接'})).toBeDisabled();
  fireEvent.click(within(dialog).getByRole('button',{name:'生成预览'}));
  fireEvent.click(await screen.findByRole('checkbox',{name:/可选资源一/}));
  await act(()=>{resolveOld({sourceUserId:'A',targetUserId:'B',resources:[resource],mandatoryCount:0,unresolvedCount:0});return Promise.resolve();});
  expect(within(dialog).queryByText('旧接收人资源')).not.toBeInTheDocument();
  expect(screen.getByRole('checkbox',{name:/可选资源一/})).not.toBeChecked();
  fireEvent.click(within(dialog).getByRole('button',{name:'生成预览'}));
  await waitFor(()=>expect(preview).toHaveBeenCalledTimes(3));
  await waitFor(()=>expect(within(dialog).getByRole('button',{name:'执行勾选交接'})).toBeEnabled());
  expect(screen.getByRole('checkbox',{name:/可选资源一/})).not.toBeChecked();
  fireEvent.click(within(dialog).getByRole('button',{name:'执行勾选交接'}));
  fireEvent.click(within(dialog).getByRole('button',{name:/执行勾选交接/}));
  await waitFor(()=>expect(execute).toHaveBeenCalledWith('A',{targetUserId:'C',selectionMode:'SELECTED',selectedResourceKeys:['CANDIDATE:two'],reason:'人员工作交接'}));
  expect(execute).toHaveBeenCalledOnce();
},20_000);

it('finds a support department by its displayed name instead of its internal ID', async () => {
  Object.defineProperty(HTMLElement.prototype, 'scrollTo', { configurable: true, value: vi.fn() });
  vi.spyOn(identityApi, 'departments').mockResolvedValue([{
    id: 'department-123', code: 'SUPPORT', name: '示例支持部门', status: 'ACTIVE',
    parentDepartmentId: null, assignmentQueueOwnerUserId: null,
    assignmentQueueOwnerName: null, version: 0,
  }]);
  vi.spyOn(identityApi, 'users').mockResolvedValue([]);
  vi.spyOn(identityApi, 'superAdmins').mockResolvedValue([]);
  render(<ConfigProvider><App><QueryClientProvider client={new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })}><IdentityAdminPage /></QueryClientProvider></App></ConfigProvider>);
  await waitFor(() => expect(identityApi.departments).toHaveBeenCalled());
  fireEvent.click(screen.getByRole('button', { name: /注册账号/ }));
  const field = await screen.findByRole('combobox', { name: '支持部门' });
  fireEvent.mouseDown(field);
  fireEvent.change(field, { target: { value: '示例支持' } });
  expect(await screen.findByRole('option', { name: '示例支持部门' })).toBeInTheDocument();
  fireEvent.keyDown(field, { key: 'Enter', code: 'Enter', keyCode: 13, which: 13 });
  await waitFor(() => expect(field).toHaveValue(''));
  expect(screen.getByText('示例支持部门', { selector: '.ant-select-selection-item-content' })).toBeInTheDocument();
}, 20_000);

it('clears a cancelled department draft before reopening the create form', async () => {
  Object.defineProperty(HTMLElement.prototype, 'scrollTo', { configurable: true, value: vi.fn() });
  vi.spyOn(identityApi, 'departments').mockResolvedValue([]);
  vi.spyOn(identityApi, 'users').mockResolvedValue([]);
  vi.spyOn(identityApi, 'superAdmins').mockResolvedValue([]);
  const createDepartment = vi.spyOn(identityApi, 'createDepartment');
  // 与生产入口 AppProviders 一致使用中文 locale，否则弹窗页脚按钮会退回英文默认文案。
  render(<ConfigProvider locale={zhCN} theme={{ token: { motion: false } }}><App><QueryClientProvider client={new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })}><IdentityAdminPage /></QueryClientProvider></App></ConfigProvider>);
  await waitFor(() => expect(identityApi.departments).toHaveBeenCalled());
  fireEvent.click(screen.getByRole('tab', { name: /部门/ }));

  fireEvent.click(await screen.findByRole('button', { name: /新建部门/ }));
  fireEvent.change(await screen.findByRole('textbox', { name: '部门名称' }), { target: { value: '已放弃的部门' } });
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: /取\s*消/ }));

  // 弹窗不随关闭卸载，取消后必须清空草稿，否则下次新建会带入上一次输入并可能被误提交。
  fireEvent.click(screen.getByRole('button', { name: /新建部门/ }));
  await waitFor(() => expect(screen.getByRole('textbox', { name: '部门名称' })).toHaveValue(''));
  expect(createDepartment).not.toHaveBeenCalled();
}, 20_000);

it('logically deletes an account from the identity table', async () => {
  Object.defineProperty(HTMLElement.prototype, 'scrollTo', { configurable: true, value: vi.fn() });
  vi.spyOn(identityApi, 'departments').mockResolvedValue([]);
  vi.spyOn(identityApi, 'users').mockResolvedValue([{
    id: 'user-1', displayName: '待删除账号', workEmail: 'removed@example.invalid', sourceTitle: null,
    homeDepartmentId: 'department-1', homeDepartmentName: '招聘部', directLeaderUserId: null,
    directLeaderName: null, status: 'DISABLED', mustChangePassword: false, version: 3,
  }]);
  vi.spyOn(identityApi, 'superAdmins').mockResolvedValue([]);
  const deleteUser = vi.spyOn(identityApi, 'deleteUser').mockResolvedValue(undefined);
  render(<ConfigProvider><App><QueryClientProvider client={new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })}><IdentityAdminPage /></QueryClientProvider></App></ConfigProvider>);

  expect(await screen.findByText('待删除账号')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: /删除/ }));
  fireEvent.click(await screen.findByRole('button', { name: '确认删除' }));

  await waitFor(() => expect(deleteUser).toHaveBeenCalledWith('user-1', 3, '管理员逻辑删除账号'));
}, 20_000);


it('shows the server-generated temporary password after registering an account', async () => {
  Object.defineProperty(HTMLElement.prototype, 'scrollTo', { configurable: true, value: vi.fn() });
  vi.spyOn(identityApi, 'departments').mockResolvedValue([{
    id: 'dept', code: 'SAMPLE', name: '示例部门', status: 'ACTIVE', parentDepartmentId: null,
    assignmentQueueOwnerUserId: null, assignmentQueueOwnerName: null, version: 1,
  }]);
  vi.spyOn(identityApi, 'users').mockResolvedValue([]);
  vi.spyOn(identityApi, 'superAdmins').mockResolvedValue([]);
  const temporaryPassword = 'Synthetic_Create_Credential_1234';
  const create = vi.spyOn(identityApi, 'createUser').mockResolvedValue({
    userId: 'new-user', oneTimeCredential: temporaryPassword, mustChangePassword: true, version: '1',
  });
  render(<ConfigProvider locale={zhCN} theme={{ token: { motion: false } }}><App><QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><IdentityAdminPage /></QueryClientProvider></App></ConfigProvider>);

  fireEvent.click(await screen.findByRole('button', { name: '注册账号' }));
  const form = await screen.findByRole('dialog', { name: '注册账号' });
  fireEvent.change(within(form).getByLabelText('姓名'), { target: { value: '示例用户' } });
  fireEvent.change(within(form).getByLabelText('工作邮箱'), { target: { value: 'new-user@example.invalid' } });
  fireEvent.mouseDown(within(form).getByRole('combobox', { name: '所属部门' }));
  fireEvent.click(await screen.findByText('示例部门', { selector: '.ant-select-item-option-content' }));
  expect(within(form).getByLabelText('姓名')).toHaveValue('示例用户');
  expect(within(form).getByLabelText('工作邮箱')).toHaveValue('new-user@example.invalid');
  expect(within(form).getByRole('checkbox', { name: '招聘专员' })).toBeChecked();
  fireEvent.click(within(form).getByRole('button', { name: '保存账号' }));
  await waitFor(() => expect(create).toHaveBeenCalledWith(expect.objectContaining({
    displayName: '示例用户', workEmail: 'new-user@example.invalid', homeDepartmentId: 'dept', roles: ['RECRUITER'],
  })));
  const credential = await screen.findByRole('dialog', { name: '临时登录密码' });
  expect(within(credential).getByText('已生成随机临时密码')).toBeInTheDocument();
  expect(within(credential).getByText(temporaryPassword)).toBeInTheDocument();
  expect(within(credential).queryByText('new-user@example.invalid')).not.toBeInTheDocument();
  expect(within(credential).getByRole('button', { name: '复制临时密码' })).toBeEnabled();
});
