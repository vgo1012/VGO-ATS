import { scrollableTableHeader } from '../components/scrollableTableHeader';
import { CompactPagination } from '../components/CompactPagination';
import { countPaginationProps } from '../components/countPaginationProps';
import { useAppFeedback } from '../components/useAppFeedback';
import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { CloseCircleFilled, DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Checkbox, Empty, Form, Input, InputNumber, Popconfirm, Select, Space, Table, Tag, Typography } from 'antd';
import { useDeferredValue, useEffect, useRef, useState } from 'react';
import { ApiProblemError } from '../api/problem';
import { WorkspaceDialog } from '../components/WorkspaceDialog';
import { PeopleSelect, useUserNames } from '../display/BusinessReferences';
import { demandApi, type Client, type PublicJdSource, type DepartmentOption, type JdSource, type JdSummary } from './api';
import { JdFactsPanel } from './JdFactsPanel';
import { JdHeadcount } from './JdHeadcount';
import { JdRecruitmentCycle } from './JdRecruitmentCycle';
import { CitySelect } from './CitySelect';
import { cities, normalizeCityName } from './cityCatalog';
import './source-jd-panel.css';

const detail = (error: unknown) => error instanceof ApiProblemError ? error.problem.detail : '读取或保存失败，请重试。';
const parseText: Record<string, string> = { NOT_STARTED:'待解析', QUEUED:'等待解析', RUNNING:'解析中', CANCELLING:'正在取消', CANCELLED:'已取消', SUCCEEDED:'解析完成', FAILED:'解析失败' };
interface JdFormValues { workRegionName?: string; recruitmentCycleDays: number; headcount: number; title: string; rawText: string; ownerDepartmentId: string; recruitmentType: 'INTERNAL' | 'EXTERNAL'; clientId?: string; ownerUserId?: string; contentShared?: boolean }

export function SourceJdPanel({ canAdmin, canReassignOwner=false, canCreate=true, actorUserId, actorHomeDepartmentId, actorSupportDepartmentIds = [], clients, departments, departmentLoading=false, departmentUnavailable=false, onRetryDepartments, clientHasMore=false, clientLoadingMore=false, onLoadMoreClients }: { canAdmin: boolean; canReassignOwner?: boolean; canCreate?: boolean; actorUserId?: string; actorHomeDepartmentId?: string; actorSupportDepartmentIds?: string[]; clients: Client[]; departments: DepartmentOption[]; departmentLoading?: boolean; departmentUnavailable?: boolean; onRetryDepartments?:()=>Promise<unknown>; clientHasMore?:boolean; clientLoadingMore?:boolean; onLoadMoreClients?:()=>Promise<unknown> }) {
  const [now, setNow] = useState(Date.now);
  useEffect(() => { const timer = window.setInterval(() => setNow(Date.now()), 60_000); return () => window.clearInterval(timer); }, []);
  const [query, setQuery] = useState(() => new URLSearchParams(window.location.search).get('query') ?? '');
  const [pageSize, setPageSize] = useState(20);
  const [cursor, setCursor] = useState<string>();
  const [history, setHistory] = useState<Array<string | undefined>>([]);
  const [selected, setSelected] = useState<JdSummary>();
  const [editing, setEditing] = useState<JdSummary | 'new'>();
  const [departmentOpen, setDepartmentOpen] = useState(false);
  const [departmentSelected, setDepartmentSelected] = useState<string>();
  const [departmentQuery, setDepartmentQuery] = useState('');
  const [departmentPageSize, setDepartmentPageSize] = useState(20);
  const [departmentCursor, setDepartmentCursor] = useState<string>();
  const [departmentHistory, setDepartmentHistory] = useState<Array<string | undefined>>([]);
  const [appliedSource, setAppliedSource] = useState<PublicJdSource>();
  const [applyError, setApplyError] = useState<unknown>();
  const [versionId, setVersionId] = useState<string>();
  const [form] = Form.useForm<JdFormValues>();
  const recruitmentType = Form.useWatch('recruitmentType', form);
  const formOwnerUserId = Form.useWatch('ownerUserId', form);
  const newTitle = useDeferredValue((Form.useWatch('title', form) ?? '').trim());
  const newRegion = normalizeCityName((Form.useWatch('workRegionName', form) ?? '').trim());
  const similar = useQuery({ queryKey: ['demand', 'public-sources', 'similar', newTitle], queryFn: () => demandApi.publicJds(newTitle, undefined, 100), enabled: editing === 'new' && !appliedSource && newTitle.length > 0, retry: false });
  const sameTitle = similar.isError ? [] : similar.data?.items.filter(item => item.title.trim() === newTitle && newRegion && normalizeCityName(item.workRegionName?.trim() ?? '') === newRegion) ?? [];
  useEffect(() => {
    if (editing === 'new' && actorHomeDepartmentId && !form.getFieldValue('ownerDepartmentId')) form.setFieldValue('ownerDepartmentId',actorHomeDepartmentId);
  },[actorHomeDepartmentId,editing,form]);
  const { message } = useAppFeedback();
  const qc = useQueryClient();
  const list = useQuery({ queryKey: ['demand', 'source-library', query, cursor, pageSize], queryFn: () => demandApi.jds(query, cursor, pageSize), refetchInterval: state => state.state.data?.items.some(item => ['QUEUED','RUNNING','CANCELLING'].includes(item.currentParseStatus ?? '')) ? 2000 : false });
  const total = useQuery({ queryKey: ['demand', 'source-library', 'count', query], queryFn: ({ signal }) => demandApi.countJds(query, signal), retry: false });
  const departmentList = useQuery({ queryKey: ['demand', 'public-sources', 'list', departmentQuery, departmentCursor, departmentPageSize], queryFn: () => demandApi.publicJds(departmentQuery, departmentCursor, departmentPageSize), enabled: departmentOpen, retry: false });
  const departmentTotal = useQuery({ queryKey: ['demand', 'public-sources', 'count', departmentQuery], queryFn: ({ signal }) => demandApi.countPublicJds(departmentQuery, signal), enabled: departmentOpen, retry: false });
  const departmentDetail = useQuery({ queryKey: ['demand', 'public-sources', 'detail', departmentSelected], queryFn: () => demandApi.publicJd(departmentSelected!), enabled: departmentOpen && Boolean(departmentSelected), retry: false });
  const versions = useQuery({ queryKey: ['demand', 'jd-versions-by-jd', selected?.id], queryFn: () => demandApi.jdVersions(selected!.id), enabled: Boolean(selected) });
  const version = versions.data?.items.find(item => item.id === versionId) ?? versions.data?.items[0];
  const selectedRecord = list.data?.items.find(item => item.id === selected?.id) ?? selected;
  const ownerNames = useUserNames((list.data?.items ?? []).map(item => item.ownerUserId));
  const departmentNames = new Map(departments.map(item => [item.id, item.name]));
  const appliedCopy = Boolean(appliedSource || (editing && editing !== 'new' && editing.appliedFromJdVersionId));
  const selectableDepartments = departments.filter(item => item.status === 'ACTIVE' && (!appliedCopy || (canReassignOwner && editing !== 'new') || item.id === actorHomeDepartmentId || actorSupportDepartmentIds.includes(item.id)));
  const editable = (item: JdSummary) => canAdmin || item.ownerUserId === actorUserId;
  const canShare = (item: JdSummary | 'new', ownerUserId = formOwnerUserId) => !appliedCopy && (canReassignOwner || (item === 'new' ? !canAdmin || ownerUserId === actorUserId : item.ownerUserId === actorUserId));
  const save = useMutation({
    mutationFn: async (values: JdFormValues) => {
      const body = { recruitmentCycleDays: values.recruitmentCycleDays, headcount: values.headcount, title: values.title.trim(), rawText: values.rawText, recruitmentType: values.recruitmentType, clientId: values.recruitmentType === 'EXTERNAL' ? values.clientId! : null, workRegionName: normalizeCityName(values.workRegionName!.trim()), ...(canShare(editing!, values.ownerUserId) ? { contentShared: Boolean(values.contentShared) } : {}) };
      const saved = editing === 'new'
        ? await demandApi.createSourceJd({ ...body, ownerDepartmentId:values.ownerDepartmentId, ...(appliedSource ? { appliedFromJdVersionId:appliedSource.currentVersionId } : canAdmin && values.ownerUserId ? { ownerUserId:values.ownerUserId } : {}) })
        : await demandApi.updateJd(editing!, { ...body, ownerDepartmentId:values.ownerDepartmentId, ...(canReassignOwner && values.ownerUserId ? { ownerUserId:values.ownerUserId } : {}) });
      if (editing !== 'new' && saved.versionNo === editing?.currentVersionNo) return { saved, parseStarted: false, configurationOnly: true };
      try { await demandApi.parseJd(saved.id); return { saved, parseStarted:true }; }
      catch { return { saved, parseStarted:false }; }
    },
    onSuccess: async result => { if(result.configurationOnly)message.success('JD 配置已更新'); else if(result.parseStarted)message.success(editing === 'new' ? 'JD 已保存，系统正在解析' : 'JD 新版本已保存，系统正在解析'); else message.warning('JD 已保存，但自动解析未启动；可在详情中重试。'); setEditing(undefined); setAppliedSource(undefined); form.resetFields(); await Promise.all([qc.invalidateQueries({ queryKey: ['demand'] }), qc.invalidateQueries({ queryKey: ['recruitment', 'readiness'] })]); },
  });
  const remove = useMutation({ mutationFn: demandApi.deleteJd, onSuccess: async () => { message.success('JD 已删除，历史招聘记录仍可查看'); setSelected(undefined); await qc.invalidateQueries({ queryKey: ['demand'] }); } });
  const openEditor = async (item: JdSummary | 'new') => {
    save.reset(); form.resetFields(); setAppliedSource(undefined);
    if (item === 'new') { form.setFieldsValue({ recruitmentCycleDays:45, contentShared:false, ...(actorHomeDepartmentId ? {ownerDepartmentId:actorHomeDepartmentId} : {}) }); setEditing(item); }
    else {
      try { const page = item.id === selected?.id && versions.data ? versions.data : await qc.fetchQuery({ queryKey: ['demand', 'jd-versions-by-jd', item.id], queryFn: () => demandApi.jdVersions(item.id) }); const rawText=page.items[0]?.rawText; if(!rawText)throw new Error('empty');
        form.setFieldsValue({ ...(item.recruitmentCycleDays != null ? {recruitmentCycleDays:item.recruitmentCycleDays} : {}), ...(item.headcount != null ? {headcount:item.headcount} : {}), title:item.title, rawText, ownerUserId:item.ownerUserId, ownerDepartmentId:item.ownerDepartmentId, recruitmentType:item.recruitmentType ?? (item.clientId ? 'EXTERNAL' : 'INTERNAL'), ...(item.clientId?{clientId:item.clientId}:{}), workRegionName:item.workRegionName ?? '', contentShared:item.contentShared ?? false }); setEditing(item);
      } catch { message.error('JD 原文读取失败，请重试后再更新。'); }
    }
  };
  const applyPublicJd = async (id: string) => {
    setApplyError(undefined);
    try {
      const source = await qc.fetchQuery({ queryKey: ['demand', 'public-sources', 'detail', id], queryFn: () => demandApi.publicJd(id), staleTime: 0 });
      if (!source.rawText) throw new Error('来源 JD 原文为空');
      save.reset(); form.resetFields();
      form.setFieldsValue({ title: source.title, rawText: source.rawText, workRegionName: source.workRegionName ?? '', contentShared:false, ...(actorHomeDepartmentId ? { ownerDepartmentId: actorHomeDepartmentId } : {}) });
      setAppliedSource(source); setDepartmentOpen(false); setDepartmentSelected(undefined); setEditing('new');
    } catch (error) { setApplyError(error); }
  };
  return <>
    <section className="ledger-heading"><div><Typography.Title level={1}>JD 库</Typography.Title><Typography.Paragraph><strong>同名同地区可创建独立 JD</strong><br />每份 JD 有独立编号、负责人和 HC；多人分别招聘时填写各自分配的名额，工作地区如实填写。</Typography.Paragraph></div><Space wrap><Button onClick={() => { setDepartmentOpen(true); setDepartmentSelected(undefined); setApplyError(undefined); }}>公共 JD 列表</Button>{canCreate?<Button type="primary" icon={<PlusOutlined aria-hidden />} onClick={() => void openEditor('new')}>新增 JD</Button>:null}</Space></section>
    <Input.Search defaultValue={query} aria-label="搜索 JD 库" placeholder="搜索 JD 编号、岗位名称、地区或客户" allowClear={{ clearIcon: <CloseCircleFilled aria-label="清空搜索" /> }} enterButton={<Button>搜索 JD</Button>} onSearch={value => { setQuery(value); setCursor(undefined); setHistory([]); }} />
    {list.error ? <Alert occurrence={list.error} type="error" showIcon title={detail(list.error)} action={<Button onClick={() => void list.refetch()}>重试列表</Button>} /> : null}
    {remove.error ? <Alert occurrence={remove.error} type="error" showIcon title="JD 删除失败" description={detail(remove.error)} /> : null}
    <Table className="source-jd-table" tableLayout="fixed" rowKey="id" loading={list.isPending} dataSource={list.data?.items ?? []} pagination={false} scroll={{ x: 1000 }} onHeaderRow={() => scrollableTableHeader('JD 库表头，可横向滚动')} locale={{ emptyText: <Empty description="暂无 JD，新增 JD 并填写岗位要求与招聘信息" /> }} columns={[
      { align: 'center', title: '岗位', width: 180, render: (_, item: JdSummary) => <div className="source-jd-identity"><Button type="link" className="table-link source-jd-title" title={item.title} onClick={() => { setSelected(item); setVersionId(undefined); }}>{item.title}</Button><div className="source-jd-meta">{item.code ? <span className="source-jd-code" title={item.code}>{item.code}</span> : null}<span className="source-jd-distinction" title={normalizeCityName(item.workRegionName ?? '') || '工作地区待补充'}>{normalizeCityName(item.workRegionName ?? '') || '工作地区待补充'}</span></div></div> },
      { align: 'center', title: '客户', width: 110, render: (_, item: JdSummary) => item.clientName ?? <Tag>内部招聘</Tag> },
      { align: 'center', title: '所属人 / 部门', width: 120, render: (_, item: JdSummary) => <div className="source-jd-owner"><span>{ownerNames.people.get(item.ownerUserId) ?? (ownerNames.loading ? '加载姓名…' : '成员姓名暂不可用')}</span><Typography.Text type="secondary">{departmentNames.get(item.ownerDepartmentId) ?? '原归属部门（已停用或暂不可用）'}</Typography.Text></div> },
      { align: 'center', title: '招聘名额（HC）', width: 175, render: (_, item: JdSummary) => <JdHeadcount value={item} compact /> },
      { align: 'center', title: '招聘周期', width: 185, render: (_, item: JdSummary) => <JdRecruitmentCycle value={item} now={now} compact /> },
      { align: 'center', title: '解析 / 版本', width: 100, render: (_, item: JdSummary) => <Space orientation="vertical" size={4}><Tag color={item.currentParseStatus === 'SUCCEEDED' ? 'green' : item.currentParseStatus === 'FAILED' ? 'red' : 'blue'}>{parseText[item.currentParseStatus ?? 'NOT_STARTED']}</Tag><Typography.Text type="secondary">V{item.currentVersionNo}</Typography.Text><Tag color={item.contentShared ? 'green' : 'default'}>{item.contentShared ? '已公开' : '未公开'}</Tag></Space> },
      { align: 'center', title: '操作', className: 'table-action-cell', width: 150, fixed: 'right', render: (_, item: JdSummary) => editable(item) ? <Space><Button size="small" icon={<EditOutlined aria-hidden />} onClick={() => { setSelected(item); setVersionId(undefined); void openEditor(item); }}>更新</Button><Popconfirm title="删除这份 JD？" description="关联简历仍在我的简历、智能筛选或招聘流程中时不可删除。删除后不再出现在日常选择中，历史招聘记录仍保留。" okText="删除" cancelText="取消" onConfirm={() => remove.mutate(item)}><Button size="small" danger icon={<DeleteOutlined aria-hidden />}>删除</Button></Popconfirm></Space> : <Tag>可直接使用</Tag> },
    ]} />
    <CompactPagination label="JD 库分页" page={history.length + 1} pageSize={pageSize} onPageSizeChange={size => { setPageSize(size); setCursor(undefined); setHistory([]); }} loading={list.isFetching}
      hasPrevious={history.length > 0} hasNext={Boolean(list.data?.hasMore)} maxPage={history.length + 1}
      onPrevious={() => { setCursor(history.at(-1)); setHistory(history.slice(0, -1)); }}
      onNext={() => { setHistory([...history, cursor]); setCursor(list.data?.nextCursor ?? undefined); }}
      onPageChange={page => { if (page <= history.length) { setCursor(history[page - 1]); setHistory(history.slice(0, page - 1)); } }} {...countPaginationProps(total)} />
    <WorkspaceDialog size="wide" className={departmentOpen ? `source-jd-department-dialog${departmentSelected ? ' workspace-dialog-reader' : ''}` : ''} title={editing ? appliedSource ? '应用公共 JD' : editing === 'new' ? '新增 JD' : '更新 JD' : departmentSelected ? '公共 JD 详情' : '公共 JD 列表'} open={departmentOpen || Boolean(editing)} onCancel={() => { if (save.isPending) return; if (editing) { setEditing(undefined); setAppliedSource(undefined); } else { setDepartmentOpen(false); setDepartmentSelected(undefined); } }} footer={editing ? undefined : departmentSelected ? <Space wrap>
      <Button onClick={() => setDepartmentSelected(undefined)}>返回公共 JD 列表</Button>
      {departmentDetail.data && canCreate && actorHomeDepartmentId ? <Button type="primary" onClick={() => void applyPublicJd(departmentDetail.data.id)}>应用此 JD</Button> : null}
    </Space> : null} mask={{ closable: departmentOpen }} confirmLoading={save.isPending} okText={editing === 'new' ? '保存并解析' : '保存修改'} onOk={async () => {
      const values = await form.validateFields();
      await save.mutateAsync(values).catch(() => undefined);
    }}>
      {departmentOpen ? <>
      {departmentSelected ? <>
        {departmentDetail.error ? <Alert occurrence={departmentDetail.error} type="error" showIcon title={detail(departmentDetail.error)} action={<Button onClick={() => void departmentDetail.refetch()}>重试详情</Button>} /> : null}
        {departmentDetail.data ? <section className="source-jd-department-detail"><Typography.Title level={4}>{departmentDetail.data.title}</Typography.Title><Typography.Paragraph>编号：{departmentDetail.data.code ?? '历史编号暂不可用'} · 地区：{normalizeCityName(departmentDetail.data.workRegionName ?? '') || '待补充'} · V{departmentDetail.data.currentVersionNo}</Typography.Paragraph><Typography.Paragraph>所属部门：{departmentDetail.data.ownerDepartmentName || '未分配部门'} · 所属人：{departmentDetail.data.ownerUserName || '未分配所属人'}</Typography.Paragraph><h3>JD 原文</h3><div className="source-jd-detail-text">{departmentDetail.data.rawText}</div>{canCreate && !actorHomeDepartmentId ? <Typography.Paragraph>当前账号未设置所属部门，无法应用公共 JD。请联系管理员设置后重试。</Typography.Paragraph> : null}</section> : departmentDetail.isPending ? <Empty description="正在读取 JD 原文…" /> : null}
      </> : <>
        <Input.Search aria-label="搜索公共 JD" placeholder="搜索编号、岗位名称或地区" allowClear={{ clearIcon: <CloseCircleFilled aria-label="清空搜索" /> }} enterButton={<Button>搜索 JD</Button>} onSearch={value => { setDepartmentQuery(value); setDepartmentCursor(undefined); setDepartmentHistory([]); }} />
        {departmentList.error ? <Alert occurrence={departmentList.error} type="error" showIcon title={detail(departmentList.error)} action={<Button onClick={() => void departmentList.refetch()}>重试公共列表</Button>} /> : null}
        <Table className="source-jd-department-table" rowKey="id" loading={departmentList.isPending} dataSource={departmentList.data?.items ?? []} pagination={false} scroll={{ x: 1030 }} locale={{ emptyText: <Empty description="暂无公开的来源 JD" /> }} columns={[
          { title:'编号', dataIndex:'code', width:150, align:'center', render:(value:string|null)=>value || '历史编号暂不可用' },
          { title:'岗位名称', dataIndex:'title', width:180, align:'center', ellipsis:true },
          { title:'地区', dataIndex:'workRegionName', width:100, align:'center', render:(value:string|null)=>normalizeCityName(value ?? '') || '待补充' },
          { title:'所属部门', dataIndex:'ownerDepartmentName', width:140, align:'center', render:(value:string|null)=>value || '未分配部门' },
          { title:'所属人', dataIndex:'ownerUserName', width:120, align:'center', render:(value:string|null)=>value || '未分配所属人' },
          { title:'版本', dataIndex:'currentVersionNo', width:70, align:'center', render:(value:number)=>`V${value}` },
          { title:'操作', width:150, align:'center', fixed:'right' as const, render:(_:unknown,item:PublicJdSource)=><Space><Button size="small" onClick={() => setDepartmentSelected(item.id)}>查看原文</Button>{canCreate && actorHomeDepartmentId ? <Button size="small" type="primary" onClick={() => void applyPublicJd(item.id)}>应用</Button>:null}</Space> },
        ]} />
        {canCreate && !actorHomeDepartmentId ? <Typography.Paragraph>当前账号未设置所属部门，无法应用公共 JD。请联系管理员设置后重试。</Typography.Paragraph> : null}
        <CompactPagination label="公共 JD 分页" page={departmentHistory.length + 1} pageSize={departmentPageSize} onPageSizeChange={size => { setDepartmentPageSize(size); setDepartmentCursor(undefined); setDepartmentHistory([]); }} loading={departmentList.isFetching} hasPrevious={departmentHistory.length > 0} hasNext={Boolean(departmentList.data?.hasMore)} maxPage={departmentHistory.length + 1} onPrevious={() => { setDepartmentCursor(departmentHistory.at(-1)); setDepartmentHistory(departmentHistory.slice(0,-1)); }} onNext={() => { setDepartmentHistory([...departmentHistory,departmentCursor]); setDepartmentCursor(departmentList.data?.nextCursor ?? undefined); }} onPageChange={page => { if (page <= departmentHistory.length) { setDepartmentCursor(departmentHistory[page-1]); setDepartmentHistory(departmentHistory.slice(0,page-1)); } }} {...countPaginationProps(departmentTotal)} />
      </>}
      {applyError ? <Alert occurrence={applyError} type="error" showIcon title="来源 JD 读取失败，请重试应用。" description={detail(applyError)} /> : null}
      </> : <>
      <div>
      {save.error ? <Alert occurrence={save.error} className="modal-alert" type="error" showIcon title={detail(save.error)} description={<Space orientation="vertical">填写内容已保留，可以修改后重试。{appliedSource && save.error instanceof ApiProblemError && ['JD_SOURCE_CHANGED','JD_SOURCE_NOT_FOUND'].includes(save.error.problem.code) ? <Button onClick={() => { const sourceId = appliedSource.id; setEditing(undefined); setAppliedSource(undefined); setDepartmentSelected(save.error instanceof ApiProblemError && save.error.problem.code === 'JD_SOURCE_CHANGED' ? sourceId : undefined); setDepartmentOpen(true); void qc.invalidateQueries({ queryKey: ['demand', 'public-sources', 'detail', sourceId] }); }}>{save.error.problem.code === 'JD_SOURCE_CHANGED' ? '查看最新来源并重新应用' : '返回公共 JD 列表重新选择'}</Button> : null}</Space>} /> : null}
      {departmentUnavailable ? <Alert type="error" showIcon title="部门列表读取失败" description="保存 JD 前请重新读取部门库。" action={<Button loading={departmentLoading} onClick={() => void onRetryDepartments?.()}>重试部门</Button>} /> : null}
      {appliedSource ? <Typography.Paragraph type="secondary">来自公共 JD {appliedSource.code ?? appliedSource.title} · V{appliedSource.currentVersionNo}。请填写自己的招聘类型、客户（如适用）、HC 和招聘周期；岗位名称和原文保持只读，地区和归属部门可调整；保存后生成独立编号，招聘配置与来源独立。</Typography.Paragraph> : null}
      {editing && editing !== 'new' ? <Typography.Paragraph type="secondary">原编号{editing.code ? ` ${editing.code}` : ''}保持不变，修改岗位信息或归属部门不会重新编号。</Typography.Paragraph> : null}
      {editing === 'new' && !appliedSource && similar.isError ? <Typography.Paragraph type="secondary">公共 JD 同名检查暂不可用，可在公共 JD 列表重试核对；填写内容已保留。</Typography.Paragraph> : null}
      {editing === 'new' && !appliedSource && !sameTitle.length && similar.data?.hasMore ? <Typography.Paragraph type="secondary">还有更多同名来源，请到公共 JD 列表核对；仍可独立新建。</Typography.Paragraph> : null}
      {editing === 'new' && !appliedSource && sameTitle.length ? <div role="status"><Typography.Text strong>公共 JD 列表已有同名同地区 JD，可先从公共 JD 列表应用</Typography.Text><ul>{sameTitle.map(item => <li key={item.id}>{item.title} · {item.code ?? '历史编号暂不可用'} · {item.workRegionName}</li>)}</ul><Typography.Paragraph type="secondary">此处仅提示已查到的来源；仍可独立新建，不会自动合并。{similar.data?.hasMore ? '更多岗位可在公共 JD 列表搜索。' : ''}</Typography.Paragraph></div> : null}
      <Form form={form} layout="vertical" className="source-jd-form">
        <div className="source-jd-form-row">
          <Form.Item name="ownerDepartmentId" label="JD 归属部门" rules={[{ required: true, message: '请选择 JD 归属部门' }, { validator:(_,value:string|undefined)=>(!appliedCopy && editing !== 'new') || (editing && editing !== 'new' && value === editing.ownerDepartmentId) || selectableDepartments.some(item=>item.id===value) ? Promise.resolve() : Promise.reject(new Error('请选择部门库中的有效部门')) }]}><Select showSearch optionFilterProp="label" loading={departmentLoading} disabled={departmentUnavailable} placeholder={departmentUnavailable ? '部门列表读取失败，请先重试' : '请选择归属部门'} options={editing !== 'new' && editing && !selectableDepartments.some(item => item.id === editing.ownerDepartmentId) ? [{value:editing.ownerDepartmentId,label:departmentNames.get(editing.ownerDepartmentId) ?? '原归属部门（已停用或暂不可用）',disabled:appliedCopy},...selectableDepartments.map(item => ({ value:item.id, label:item.name }))] : selectableDepartments.map(item => ({ value:item.id, label:item.name }))} /></Form.Item>
          <Form.Item name="title" label="岗位名称" rules={[{ required: true, whitespace: true, message: '请输入岗位名称' }, { max: editing === 'new' ? 80 : 180, message: editing === 'new' ? '岗位名称不能超过 80 字' : '岗位名称不能超过 180 字' }]}><Input autoFocus={!appliedCopy} disabled={appliedCopy} placeholder="例如：运维工程师" /></Form.Item>
          <Form.Item name="workRegionName" label="工作地区" rules={[{ required: true, whitespace: true, message: '请输入工作地区' }, { validator: (_, value: string | undefined) => !value || (editing && editing !== 'new' && normalizeCityName(value.trim()) === normalizeCityName(editing.workRegionName?.trim() ?? '')) || cities.some(city => city.value === normalizeCityName(value.trim())) ? Promise.resolve() : Promise.reject(new Error('请选择城市列表中的真实城市')) }]}><CitySelect /></Form.Item>
        </div>
        <div className="source-jd-form-row">
          <Form.Item name="headcount" label="招聘人数（HC）" rules={[{ required: true, message: '请填写招聘人数（HC）' }, { type: 'integer', min: 1, max: 10000, message: 'HC 必须为 1～10000 的整数' }, { validator: (_, value: number | undefined) => value != null && editing && editing !== 'new' && value < (editing.occupiedHeadcount ?? 0) ? Promise.reject(new Error(`不能少于已占用的 ${editing.occupiedHeadcount} 个名额`)) : Promise.resolve() }]}><InputNumber aria-label="招聘人数（HC）" min={1} max={10000} step={1} placeholder="例如：3" /></Form.Item>
          <Form.Item name="recruitmentCycleDays" label="招聘周期（天）" rules={[{ required: true, message: '请填写招聘周期' }, { type: 'integer', min: 1, max: 3650, message: '招聘周期必须为 1～3650 的整数' }]}><InputNumber aria-label="招聘周期（天）" min={1} max={3650} step={1} placeholder="例如：45" /></Form.Item>
          <Form.Item name="recruitmentType" label="招聘类型" rules={[{ required: true, message: '请选择招聘类型' }]}><Select placeholder="请选择内部招聘或外部招聘" options={[{value:'INTERNAL',label:'内部招聘'},{value:'EXTERNAL',label:'外部招聘'}]} onChange={value => { if (value === 'INTERNAL') form.setFieldValue('clientId', undefined); }} /></Form.Item>
        </div>
        {recruitmentType === 'EXTERNAL' ? <Form.Item name="clientId" label="客户" rules={[{ required:true, message:'外部招聘必须选择客户' }]} extra={<Space wrap>从客户库选择本次外部招聘所属客户。{clientHasMore?<Button type="link" loading={clientLoadingMore} onClick={()=>void onLoadMoreClients?.()}>加载更多客户</Button>:null}</Space>}><Select showSearch optionFilterProp="label" placeholder="请选择客户" options={clients.filter(item => item.status === 'ACTIVE').map(item => ({ value: item.id, label: item.name }))} /></Form.Item> : null}
        <Form.Item className="source-jd-form-wide" name="rawText" label="JD 原文" rules={[{ required: true, whitespace: true, message: '请粘贴 JD 原文' }, { max: 200000 }]} extra={appliedCopy ? '公共 JD 应用副本的岗位名称和原文保持只读。' : '职责、要求和待遇可以直接原样粘贴，保存后自动解析。'}><Input.TextArea rows={12} readOnly={appliedCopy} /></Form.Item>
        {editing && canShare(editing) ? <Form.Item className="source-jd-form-wide" name="contentShared" valuePropName="checked"><Checkbox>公开到公共 JD 列表</Checkbox></Form.Item> : null}
        {(!appliedSource && (editing === 'new' ? canAdmin : canReassignOwner)) ? <Form.Item className="source-jd-form-wide" name="ownerUserId" label="JD 所属人" rules={[{ required: true, message: editing === 'new' ? '代建 JD 时必须选择所属人' : '请选择 JD 所属人' }]} extra={editing === 'new' ? 'JD 保存后按所选人员归属并进入其业务范围。' : '仅超级管理员可调整；保存后 JD 将进入新所属人的业务范围。'}><PeopleSelect aria-label="JD 所属人" /></Form.Item> : null}
      </Form>
      </div>
      </>}
    </WorkspaceDialog>
    <WorkspaceDialog size="wide" className="source-jd-detail" title={selectedRecord?.title ?? 'JD'} open={Boolean(selected && !editing)} onCancel={() => setSelected(undefined)} footer={null}>
      {versions.error ? <Alert occurrence={versions.error} type="error" title={detail(versions.error)} action={<Button onClick={() => void versions.refetch()}>重试版本</Button>} /> : null}
      {selectedRecord?.code ? <Space orientation="vertical" size={4} style={{ marginBottom: 16, maxWidth: '100%' }}><Typography.Text copyable={{ text: selectedRecord.code }}>JD 编号：{selectedRecord.code}</Typography.Text><Typography.Text type="secondary">邮件主题示例：<Typography.Text copyable>{`【${selectedRecord.code}】${selectedRecord.title}—张三`}</Typography.Text></Typography.Text><Typography.Text type="secondary">附件名示例：<Typography.Text copyable>{`【${selectedRecord.code}】张三.pdf`}</Typography.Text></Typography.Text></Space> : null}
      <div className="source-jd-detail-summary">
        {selectedRecord ? <JdHeadcount value={selectedRecord} {...(editable(selectedRecord) ? { onEdit: () => void openEditor(selectedRecord) } : {})} /> : null}
        {selectedRecord ? <JdRecruitmentCycle value={selectedRecord} now={now} /> : null}
      </div>
      {version ? <>
        <div className="source-jd-detail-toolbar"><Select aria-label="JD 版本" value={version.id} onChange={setVersionId} options={(versions.data?.items ?? []).map(item => ({ value: item.id, label: `V${item.versionNo} · ${item.title}` }))} /><JdOrigins versionId={version.id} /></div>
        <div className="source-jd-detail-columns">
          <section aria-label="JD 原文"><h3>JD 原文</h3><div className="source-jd-detail-text">{version.rawText}</div></section>
          <section aria-label="解析事实"><h3>解析事实</h3><JdFacts versionId={version.id} parseStatus={selectedRecord?.currentParseStatus} canRetry={Boolean(selectedRecord && editable(selectedRecord))} /></section>
        </div>
      </> : <Empty description={versions.isPending ? '正在读取 JD…' : '暂无版本'} />}
    </WorkspaceDialog>
  </>;
}

function JdOrigins({ versionId }: { versionId: string }) {
  const sources = useQuery({ queryKey:['demand','jd-sources',versionId], queryFn:()=>demandApi.jdSources(versionId) });
  if (sources.error) return <Alert occurrence={sources.error} type="error" title="JD 来源读取失败" action={<Button onClick={()=>void sources.refetch()}>重试来源</Button>} />;
  return <Space wrap role="group" aria-label="JD 来源">{(sources.data?.items ?? []).map((source:JdSource)=><Tag key={source.id}>{source.sourceLabel || (source.sourceType === 'MAIL' ? '邮件' : '手动录入')}</Tag>)}</Space>;
}

function JdFacts({ versionId, parseStatus, canRetry }: { versionId: string; parseStatus?: JdSummary['currentParseStatus']; canRetry: boolean }) {
  const qc=useQueryClient();
  const parsing=['QUEUED','RUNNING','CANCELLING'].includes(parseStatus ?? '');
  const facts = useQuery({ queryKey: ['demand', 'jd-facts', versionId], queryFn: () => demandApi.jdFacts(versionId), retry: false, refetchInterval: parsing ? 2000 : false });
  const previous = useRef({ versionId, parseStatus });
  useEffect(() => {
    const changedToSuccess = previous.current.versionId === versionId && previous.current.parseStatus !== 'SUCCEEDED' && parseStatus === 'SUCCEEDED';
    previous.current = { versionId, parseStatus };
    if (!changedToSuccess) return;
    let active = true;
    const queryKey = ['demand', 'jd-facts', versionId];
    // 丢弃完成前仍在途的旧读取，确保终态停止轮询前补读最终事实。
    void qc.cancelQueries({ queryKey, exact: true }).then(() => {
      if (active) return qc.invalidateQueries({ queryKey, exact: true });
    });
    return () => { active = false; };
  }, [parseStatus, versionId, qc]);
  const parse=useMutation({mutationFn:()=>demandApi.parseJd(versionId),onSuccess:async()=>{message.success('已重新启动解析');await qc.invalidateQueries({queryKey:['demand']});}});
  const {message}=useAppFeedback();
  const absent = facts.error instanceof ApiProblemError && facts.error.problem.status === 404;
  if (facts.data) return <JdFactsPanel value={facts.data} />;
  if (facts.error && !absent) return <Alert occurrence={facts.error} type="error" title={detail(facts.error)} action={<Button onClick={() => void facts.refetch()}>重试读取</Button>} />;
  return <>{parse.error?<Alert occurrence={parse.error} type="error" showIcon title="解析启动失败" description={detail(parse.error)} />:null}<Empty description={parsing ? '系统正在解析' : '尚无解析结果'} children={canRetry&&!parsing?<Button type="primary" loading={parse.isPending} onClick={()=>parse.mutate()}>重新解析</Button>:undefined} /></>;
}
