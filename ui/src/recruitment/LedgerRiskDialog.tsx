import { useInfiniteQuery, useMutation, useQuery } from '@tanstack/react-query';
import { Button, Form, Input, Select, Space } from 'antd';
import { useState } from 'react';
import { WorkspaceDialog } from '../components/WorkspaceDialog';
import { FeedbackAlert } from '../components/FeedbackAlert';
import { CandidateName, JobName } from '../display/BusinessReferences';
import { demandApi } from '../demand/api';
import type { ApplicationDetails } from './api';
import { activityRiskApi, newActivityOrigin, type CompanyRestrictionInput } from './activityApi';
import { actionError } from './candidateActions';

export function LedgerRiskDialog({ value, onClose, onChanged }: { value: ApplicationDetails; onClose: () => void; onChanged: () => Promise<unknown> }) {
  const [form] = Form.useForm<CompanyRestrictionInput>();
  const clientId = Form.useWatch('clientId', form) as string | undefined;
  const [action, setAction] = useState<'create' | 'correct' | 'disable'>('create');
  const [origin, setOrigin] = useState(() => newActivityOrigin('BLACKLIST'));
  const clients = useInfiniteQuery({ queryKey: ['demand', 'client-filter'], initialPageParam: undefined as string | undefined, queryFn: ({ pageParam }) => demandApi.clients(undefined, pageParam), getNextPageParam: page => page.hasMore ? page.nextCursor ?? undefined : undefined });
  const restrictions = useQuery({ queryKey: ['client-restrictions', value.application.candidateId, clientId], queryFn: () => activityRiskApi.restrictions(value.application.candidateId, clientId!), enabled: Boolean(clientId) });
  const current = restrictions.data?.[0];
  const blocked = Boolean(current && ['ACTIVE','CORRECTED'].includes(current.status) && (!current.expiresAt || Date.parse(current.expiresAt) > restrictions.dataUpdatedAt));
  const options = clients.data?.pages.flatMap(page => page.items).map(client => ({ value: client.id, label: client.name })) ?? [];
  const companyName = options.find(option => option.value === clientId)?.label ?? (clientId === value.application.clientId ? value.application.clientName : undefined);
  const mutation = useMutation({ mutationFn: async (fields: CompanyRestrictionInput) => {
    if (action === 'disable' && current) return activityRiskApi.disable(current.id, fields.reasonDetail, origin);
    if (action === 'correct' && current) return activityRiskApi.correct(current.id, { restrictionIntent: current.restrictionIntent, reasonDetail: fields.reasonDetail, sourceChannel: fields.sourceChannel, expiresAt: current.expiresAt }, origin);
    return activityRiskApi.create(value.application.id, fields, origin);
  }, onSuccess: async () => { await restrictions.refetch(); await onChanged(); onClose(); } });
  return <WorkspaceDialog open title="黑名单" onCancel={onClose} okText={action === 'disable' ? '停用标签' : action === 'correct' ? '保存更正' : '保存并结束当前流程'} confirmLoading={mutation.isPending} okButtonProps={{ danger: action !== 'disable', disabled: !clientId || restrictions.isFetching || Boolean(restrictions.error) }} onOk={() => form.validateFields().then(fields => mutation.mutateAsync(fields))}>
    <p><CandidateName id={value.application.candidateId} /> · <JobName jdId={value.application.jdId} /> · {value.application.clientName ?? '内部招聘'}</p>
    <Form form={form} layout="vertical" initialValues={{ clientId: value.application.recruitmentType === 'EXTERNAL' ? value.application.clientId ?? undefined : undefined, sourceChannel: 'MANUAL', reasonDetail: '' }}>
      <Form.Item name="clientId" label="公司标签" rules={[{ required: true, message: '请选择一家真实公司' }]}><Select showSearch optionFilterProp="label" options={options} placeholder="请选择公司" loading={clients.isFetching} onChange={() => { setAction('create'); setOrigin(newActivityOrigin('BLACKLIST')); }} popupRender={menu => <>{menu}{clients.hasNextPage ? <Button block loading={clients.isFetchingNextPage} onClick={() => void clients.fetchNextPage()}>加载更多公司</Button> : null}{clients.error ? <Button block onClick={() => void clients.refetch()}>重试公司目录</Button> : null}</>} /></Form.Item>
      {clientId && action === 'create' ? <FeedbackAlert type="info" title={`记录【${companyName ?? '所选公司'}】黑名单标签并结束当前招聘流程；简历在流程结束后进入共享简历库并保留标签，不进入个人待筛选，招聘推进仍受黑名单限制。`} /> : null}
      {restrictions.error ? <FeedbackAlert type="error" title={actionError(restrictions.error)} action={<Button onClick={() => void restrictions.refetch()}>重试现有标签</Button>} /> : null}
      {blocked ? <><p>已有有效标签：{current?.reasonDetail}</p><Space><Button onClick={() => { setAction('correct'); setOrigin(newActivityOrigin('BLACKLIST')); }}>更正标签</Button><Button onClick={() => { setAction('disable'); setOrigin(newActivityOrigin('BLACKLIST')); }}>停用标签</Button></Space></> : null}
      <Form.Item name="reasonDetail" label={action === 'disable' ? '停用原因' : '原因评论'} rules={[{ required: true, whitespace: true }, { max: 1000 }]}><Input.TextArea rows={3} /></Form.Item>
      {action !== 'disable' ? <Form.Item name="sourceChannel" label="事实来源" rules={[{ required: true }]}><Select options={[{ value: 'MANUAL', label: '人工核验' }, { value: 'EMAIL', label: '邮件' }, { value: 'PHONE', label: '电话' }, { value: 'CLIENT_SYSTEM', label: '客户系统' }, { value: 'OTHER', label: '其他' }]} /></Form.Item> : null}
    </Form>
    {mutation.error ? <FeedbackAlert type="error" title={actionError(mutation.error)} /> : null}
  </WorkspaceDialog>;
}
