import { hasJdCapacity } from '../demand/headcount';
import { createIdempotencyKey } from '../api/idempotency';
import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { useInfiniteQuery, useMutation, useQuery } from '@tanstack/react-query';
import { App, Button, Card, Col, Form, Input, Row, Select } from 'antd';
import { useDeferredValue, useRef, useState } from 'react';
import { ApiProblemError } from '../api/problem';
import { WorkspaceDialog } from '../components/WorkspaceDialog';
import { LoadingStatus } from '../components/LoadingStatus';
import { PeopleSelect } from '../display/BusinessReferences';
import { demandApi, type JdSummary } from '../demand/api';
import { JdSelector } from '../demand/JdSelector';
import { talentApi, type Candidate, type ResumeVersion } from '../talent/api';
import { recruitmentApi, type ApplicationDetails } from './api';
import { candidateActionsApi } from './candidateActionsApi';
import { RecruitmentPreparation } from './RecruitmentPreparation';

function CandidateSelector({ value, onChange, id }: { value?: string; onChange?: (id: string) => void; id?: string }) {
  const [search, setSearch] = useState('');
  const [selected, setSelected] = useState<Candidate>();
  const deferredSearch = useDeferredValue(search);
  const candidates = useInfiniteQuery({
    queryKey: ['talent', 'application-candidate-selector', 'all-candidates', deferredSearch],
    initialPageParam: undefined as string | undefined,
    queryFn: ({ pageParam }) => talentApi.candidates(deferredSearch, pageParam, 50),
    getNextPageParam: page => page.hasMore ? page.nextCursor ?? undefined : undefined,
  });
  const items = [...new Map((candidates.data?.pages.flatMap(page => page.items) ?? []).map(item => [item.id, item])).values()];
  const options = selected && selected.id === value && !items.some(item => item.id === value) ? [selected, ...items] : items;
  return <Select<string> {...(id ? { id } : {})} aria-label="候选人" value={value ?? null} showSearch searchValue={search} filterOption={false} virtual={false}
    onSearch={setSearch} loading={candidates.isFetching} onOpenChange={open => { if (!open) setSearch(''); }}
    onChange={candidateId => { setSelected(options.find(item => item.id === candidateId)); setSearch(''); onChange?.(candidateId); }}
    options={options.map(item => ({ value: item.id, label: `${item.fullName ?? '姓名未录入'} · ${item.primaryEmail ?? item.primaryPhone ?? '联系方式未录入'}` }))}
    notFoundContent={candidates.isFetching ? <LoadingStatus size="small" label="正在搜索候选人" /> : candidates.error ? '候选人加载失败' : '没有匹配的候选人'}
    popupRender={menu => <>{menu}{candidates.error ? <Button block onClick={() => { void candidates.refetch(); }}>重新加载候选人</Button> : candidates.hasNextPage ? <Button block loading={candidates.isFetchingNextPage} onClick={() => { void candidates.fetchNextPage(); }}>加载更多候选人</Button> : null}</>}
  />;
}

type CreateValues = { candidateId: string; jdId: string; ownerUserId?: string; reason: string };

export function CreateApplicationModal({ open, ownerUserId, onClose, onSaved }: {
  open: boolean; ownerUserId: string | undefined; onClose: () => void; onSaved: (value: ApplicationDetails) => Promise<void>;
}) {
  const [form] = Form.useForm<CreateValues>();
  const candidateId = Form.useWatch('candidateId', form);
  const [selectedJd, setSelectedJd] = useState<JdSummary>();
  const requestDraft = useRef<{ signature: string; key: string } | undefined>(undefined);
  const { message } = App.useApp();
  const versions = useQuery({ queryKey: ['jd-versions', selectedJd?.id], queryFn: () => demandApi.jdVersions(selectedJd!.id), enabled: Boolean(open && selectedJd?.id) });
  const resumes = useQuery({ queryKey: ['resume-versions', candidateId], queryFn: () => talentApi.resumeVersions(candidateId), enabled: Boolean(open && candidateId) });
  const jdVersion = versions.data?.items.find(item => item.versionNo === selectedJd?.currentVersionNo);
  const resume = resumes.data?.items.reduce<ResumeVersion | undefined>((latest, item) => !latest || item.versionNo > latest.versionNo ? item : latest, undefined);
  const readiness = useQuery({ queryKey: ['recruitment', 'readiness', jdVersion?.id, ownerUserId], queryFn: () => candidateActionsApi.readiness(jdVersion!.id), enabled: Boolean(open && jdVersion), retry: false });
  const ready = Boolean(jdVersion && resume && readiness.data?.canProcess && readiness.data.activeWorkflowId && hasJdCapacity(readiness.data) && !readiness.isFetching && !readiness.error && !versions.error && !resumes.error);
  const mutation = useMutation({
    mutationFn: async (values: CreateValues) => {
      if (!jdVersion || !resume) throw new Error('候选人简历版本或 JD 版本不存在');
      if (!ready) throw new Error('请先完成岗位权限与流程检查');
      const body = { candidateId: values.candidateId, jdVersionId: jdVersion.id, resumeVersionId: resume.id, ownerUserId: values.ownerUserId, reason: values.reason.trim() };
      const signature = JSON.stringify(body);
      if (requestDraft.current?.signature !== signature) requestDraft.current = { signature, key: createIdempotencyKey() };
      return recruitmentApi.create(body, requestDraft.current.key);
    },
    onSuccess: async value => { message.success('已推荐到招聘流程，待 HR 跟进'); await onSaved(value); },
  });
  const error = mutation.error ?? versions.error ?? resumes.error;
  return <WorkspaceDialog okText="确认推荐" title="推荐候选人到招聘流程" open={open} onCancel={onClose} confirmLoading={mutation.isPending} okButtonProps={{ disabled: !ready }}
    onOk={() => form.validateFields().then(values => mutation.mutate(values))}
    afterOpenChange={visible => { if (visible) form.setFieldsValue({ ...(ownerUserId ? { ownerUserId } : {}), reason: 'HR 确认候选人与当前职位建立招聘关系' }); else { form.resetFields(); setSelectedJd(undefined); requestDraft.current = undefined; mutation.reset(); } }} destroyOnHidden>
    {error ? <Alert occurrence={error} type="error" showIcon title={error instanceof ApiProblemError ? error.problem.detail : error.message} /> : null}
    <p>推荐后进入待 HR 跟进。沟通方式、面试轮次和时间由 HR 后续逐次确认；确认排期后才会出现在日程协同。</p>
    <Form name="application-create" form={form} layout="vertical" disabled={mutation.isPending}>
      <Form.Item name="candidateId" label="候选人" rules={[{ required: true }]}><CandidateSelector /></Form.Item>
      <Form.Item name="jdId" label="推荐岗位" rules={[{ required: true }]} getValueFromEvent={(jd: JdSummary | undefined) => jd?.id}><JdSelector onChange={setSelectedJd} disabled={mutation.isPending} /></Form.Item>
      {readiness.isFetching ? <p role="status">正在核对岗位权限与招聘流程…</p> : null}
      {readiness.error ? <Alert occurrence={readiness.error} type="error" showIcon title="无法核对招聘流程" description={readiness.error.message} action={<Button onClick={() => void readiness.refetch()}>重试流程检查</Button>} /> : null}
      {readiness.data ? <RecruitmentPreparation value={readiness.data} onChanged={() => readiness.refetch()} /> : null}
      <Row gutter={12}>
        <Col xs={24} sm={12}><Card size="small" title="将使用的简历版本">{resume ? `第 ${resume.versionNo} 版 · ${resume.originalFileName}` : candidateId ? '尚无已确认入库的简历版本' : '选择候选人后读取'}</Card></Col>
        <Col xs={24} sm={12}><Card size="small" title="将使用的 JD 版本">{jdVersion ? `第 ${jdVersion.versionNo} 版 · ${jdVersion.title}` : selectedJd ? '当前岗位版本尚未读取，请重新选择岗位' : '选择岗位后读取'}</Card></Col>
      </Row>
      <Form.Item name="ownerUserId" label="负责人"><PeopleSelect /></Form.Item>
      <Form.Item name="reason" label="人工推荐依据" rules={[{ required: true, whitespace: true }, { max: 500 }]}><Input.TextArea rows={3} /></Form.Item>
    </Form>
  </WorkspaceDialog>;
}
