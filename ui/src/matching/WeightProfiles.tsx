import { CompactPagination } from '../components/CompactPagination';
import { countPaginationProps } from '../components/countPaginationProps';
import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Card, Col, Empty, Form, Input, InputNumber, Row, Select, Skeleton, Table, Typography } from 'antd';
import { useState } from 'react';
import { Controller, useForm, useWatch } from 'react-hook-form';
import { ScreeningCriteriaEditor } from '../demand/ScreeningCriteriaEditor';
import { criteriaToForm, criterionRequest, type SimpleCriterion } from '../demand/criterionForm';
import { personalCriteria, screeningSettings, SCREENING_SORT_OPTIONS } from './screeningSettings';
import { WorkspaceDialog as Modal } from '../components/WorkspaceDialog';
import { ApiProblemError } from '../api/problem';
import { createIdempotencyKey } from '../api/idempotency';
import { demandApi, type ProfessionalWeights, type ScreeningSortMode, type WeightProfileDetails } from '../demand/api';
import { PROFESSIONAL_DIMENSIONS } from '../demand/scoringRules';
import { useSession } from '../auth/session';
import { hasManagementScope } from '../auth/accessPolicy';
import { useUserNames } from '../display/BusinessReferences';

const errorMessage = (error: unknown) => error instanceof ApiProblemError ? error.problem.detail : error instanceof SyntaxError || error instanceof TypeError ? '保存响应未能确认，请重试核对。' : error instanceof Error ? error.message : '保存失败，请重试。';
const accessDenied = (error: unknown) => error instanceof ApiProblemError && [401, 403].includes(error.problem.status);

export function WeightProfiles({ onSaved }: { onSaved?: (profile: WeightProfileDetails) => void } = {}) {
  const { actor } = useSession();
  const queryClient = useQueryClient();
  const management = hasManagementScope(actor);
  const [cursor, setCursor] = useState<string>();
  const [pageSize, setPageSize] = useState(20);
  const [cursorHistory, setCursorHistory] = useState<Array<string | undefined>>([]);
  const profiles = useQuery({ queryKey: ['matching', 'weight-profiles', { mineOnly: !management }, cursor, pageSize], queryFn: () => demandApi.weightProfiles(!management, cursor, pageSize) });
  const total = useQuery({ queryKey: ['matching', 'weight-profiles', 'count', { mineOnly: !management }], queryFn: ({ signal }) => demandApi.countWeightProfiles(!management, signal), retry: false });
  const visibleProfiles = accessDenied(profiles.error) ? [] : profiles.data?.items ?? [];
  const ownerNames = useUserNames(visibleProfiles.map(item => item.profile.ownerUserId).filter((id): id is string => Boolean(id)));
  const ownerLabel = (id: string | null) => !id ? '未分配' : ownerNames.people.get(id) ?? (ownerNames.loading ? '加载姓名…' : '成员姓名暂不可用');
  const [editing, setEditing] = useState<WeightProfileDetails | 'new'>();
  return <>
    <Card className="weight-profile-panel" title={management ? '筛选方案' : '我的筛选方案'} extra={<Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing('new')}>新建方案</Button>}>
      <p className="dialog-description">{management ? '可查看管理范围内的方案；每位 HR 只能编辑和实际选用本人的方案。' : '统一保存五维比例、岗位附加要求和默认排序，可在不同岗位复用；历史匹配保留原版本。'}</p>
      {profiles.error ? <Alert occurrence={profiles.error} type="error" showIcon title="方案加载失败" action={<Button onClick={() => void profiles.refetch()}>重试</Button>} /> : null}
      <Table className="weight-profile-table" rowKey={item => item.profile.id} loading={profiles.isPending} dataSource={visibleProfiles} pagination={false} scroll={{ x: 760 }} columns={[
        { title: '方案名称', render: (_, item: WeightProfileDetails) => item.profile.name },
        { title: '归属人', render: (_: unknown, item: WeightProfileDetails) => ownerLabel(item.profile.ownerUserId) },
        ...PROFESSIONAL_DIMENSIONS.map(([key, label]) => ({ title: label, render: (_: unknown, item: WeightProfileDetails) => `${weightFrom(item, key)}%` })),
        { title: '操作', className: 'table-action-cell', render: (_, item: WeightProfileDetails) => item.profile.ownerUserId === actor?.userId ? <Button size="small" onClick={() => setEditing(item)}>编辑</Button> : <Typography.Text type="secondary">仅可查看</Typography.Text> },
      ]} locale={{ emptyText: '还没有个人方案，创建后可在任意岗位使用' }} />
      <section className="weight-profile-mobile" aria-label="个人权重方案列表">
        {profiles.isPending ? <Skeleton active paragraph={{ rows: 2 }} /> : visibleProfiles.length ? visibleProfiles.map(item => <article key={item.profile.id} className="weight-profile-summary">
          <header><h2>{item.profile.name}</h2>{item.profile.ownerUserId === actor?.userId ? <Button size="small" aria-label={`编辑${item.profile.name}`} onClick={() => setEditing(item)}>编辑</Button> : <Typography.Text type="secondary">仅可查看</Typography.Text>}</header>
          <p>归属人：{ownerLabel(item.profile.ownerUserId)}</p>
          <dl>{PROFESSIONAL_DIMENSIONS.map(([key, label]) => <div key={key}><dt>{label}</dt><dd>{weightFrom(item, key)}%</dd></div>)}</dl>
        </article>) : !profiles.error ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="还没有个人方案，创建后可在任意岗位使用" /> : null}
      </section>
      <CompactPagination label="筛选方案分页" page={cursorHistory.length + 1} pageSize={pageSize} onPageSizeChange={size=>{setPageSize(size);setCursor(undefined);setCursorHistory([]);}} loading={profiles.isFetching}
        hasPrevious={cursorHistory.length > 0} hasNext={Boolean(!accessDenied(profiles.error) && profiles.data?.hasMore)} maxPage={cursorHistory.length + 1}
        onPrevious={() => { setCursor(cursorHistory.at(-1)); setCursorHistory(cursorHistory.slice(0, -1)); }}
        onNext={() => { setCursorHistory([...cursorHistory, cursor]); setCursor(profiles.data?.nextCursor ?? undefined); }}
        onPageChange={page => { if (page <= cursorHistory.length) { setCursor(cursorHistory[page - 1]); setCursorHistory(cursorHistory.slice(0, page - 1)); } }} {...countPaginationProps(total)} />
    </Card>
    <WeightProfileModal {...(editing ? { value: editing } : {})} onClose={() => setEditing(undefined)} onSaved={async profile => { await queryClient.invalidateQueries({ queryKey: ['matching', 'weight-profiles'] }); onSaved?.(profile); }} />
  </>;
}

function weightFrom(profile: WeightProfileDetails, key: typeof PROFESSIONAL_DIMENSIONS[number][0]) {
  const map = { skills: 'skillsWeight', roleExperience: 'roleWeight', relatedYears: 'yearsWeight', responsibilityMatch: 'responsibilityWeight', industryAndProjects: 'industryProjectWeight' } as const;
  return profile.currentVersion[map[key]];
}

type ProfileValues = ProfessionalWeights & { name: string; criteria: SimpleCriterion[]; defaultSortMode: ScreeningSortMode };
type ProfileCreation = { key: string; values: ProfileValues };

function WeightProfileModal({ value, onClose, onSaved }: { value?: WeightProfileDetails | 'new'; onClose: () => void; onSaved: (profile: WeightProfileDetails) => Promise<unknown> }) {
  const [creation, setCreation] = useState<ProfileCreation>();
  if (!value) return null;
  return <WeightProfileForm key={value === 'new' ? 'new' : value.currentVersion.id} value={value} creation={creation} onCreationChange={setCreation} onClose={onClose} onSaved={onSaved} />;
}

function WeightProfileForm({ value, creation, onCreationChange, onClose, onSaved }: { value: WeightProfileDetails | 'new'; creation: ProfileCreation | undefined; onCreationChange: (value: ProfileCreation | undefined) => void; onClose: () => void; onSaved: (profile: WeightProfileDetails) => Promise<unknown> }) {
  const { message } = App.useApp();
  const editing = value === 'new' ? undefined : value;
  const frozen = !editing && Boolean(creation);
  const { control, handleSubmit, setValue, formState: { errors } } = useForm<ProfileValues>({ defaultValues: !editing && creation ? creation.values : {
    name: editing?.profile.name ?? '',
    skills: editing ? weightFrom(editing, 'skills') : 20,
    roleExperience: editing ? weightFrom(editing, 'roleExperience') : 20,
    relatedYears: editing ? weightFrom(editing, 'relatedYears') : 20,
    responsibilityMatch: editing ? weightFrom(editing, 'responsibilityMatch') : 20,
    industryAndProjects: editing ? weightFrom(editing, 'industryAndProjects') : 20,
    criteria: criteriaToForm(editing ? personalCriteria(editing) : []),
    defaultSortMode: screeningSettings(editing).defaultSortMode,
  } });
  const watched = useWatch({ control });
  const criteria = useWatch({ control, name: 'criteria' });
  const total = PROFESSIONAL_DIMENSIONS.reduce((sum, [key]) => sum + Number(watched[key] ?? 0), 0);
  const validWeights = PROFESSIONAL_DIMENSIONS.every(([key]) => {
    const weight = watched[key];
    return typeof weight === 'number' && Number.isInteger(weight) && weight >= 0 && weight <= 100;
  });
  const bonus = criteria.filter(item => item.enabled && item.level === 'PREFER').reduce((sum, item) => sum + item.bonusPoints, 0);
  const mutation = useMutation({
    mutationFn: async (values: ProfileValues) => {
      const attempt = editing ? undefined : creation ?? { key: createIdempotencyKey(), values: structuredClone(values) };
      if (attempt) onCreationChange(attempt);
      const submitted = attempt?.values ?? values;
      const settings = { criteria: submitted.criteria.filter(item => item.enabled && item.code !== 'AGE' && item.code !== 'GENDER').map(item => criterionRequest({ ...item, selectableByHr: false })), defaultSortMode: submitted.defaultSortMode };
      try {
        return editing ? await demandApi.updateWeightProfile(editing, submitted.name, submitted, settings) : await demandApi.createWeightProfile(submitted.name, submitted, settings, attempt!.key);
      } catch (error) {
        // 明确校验拒绝可修正；响应丢失或服务异常必须继续确认原创建请求。
        if (!editing && error instanceof ApiProblemError && [400, 422].includes(error.problem.status)) {
          onCreationChange(undefined);
        }
        throw error;
      }
    },
    onSuccess: async profile => {
      await onSaved(profile);
      if (!editing) onCreationChange(undefined);
      message.success('筛选方案已保存，之后选用该方案的匹配将使用新版本');
      onClose();
    },
  });
  return <Modal size="wide" okText="保存方案" title={editing ? '编辑个人筛选方案' : '新建个人筛选方案'} open onCancel={onClose} okButtonProps={{ disabled: total !== 100 || !validWeights || bonus > 30 }} confirmLoading={mutation.isPending} onOk={() => handleSubmit(values => mutation.mutateAsync(values).then(() => undefined, () => undefined))()}>
    {frozen && !mutation.isPending ? <Alert type="warning" showIcon title="上次保存结果尚未确认，请重试确认后再修改方案。" /> : null}
    {mutation.error ? <Alert occurrence={mutation.error} type="error" showIcon title={errorMessage(mutation.error)} /> : null}
    <Form layout="vertical" aria-label="个人筛选方案内容" tabIndex={0} disabled={mutation.isPending || frozen}>
      <Form.Item label="方案名称" required validateStatus={errors.name ? 'error' : ''} help={errors.name?.message}><Controller name="name" control={control} rules={{ required: '请输入方案名称', validate: value => Boolean(value.trim()) || '请输入方案名称' }} render={({ field }) => <Input {...field} aria-label="方案名称" maxLength={120} />} /></Form.Item>
      <Typography.Title level={2} className="simple-section-title">五维比例</Typography.Title>
      <Row gutter={12}>{PROFESSIONAL_DIMENSIONS.map(([key, label]) => <Col xs={12} key={key}><Form.Item label={label} required><Controller name={key} control={control} rules={{ required: true, min: 0, max: 100 }} render={({ field }) => <InputNumber {...field} aria-label={label} min={0} max={100} precision={0} suffix="%" style={{ width: '100%' }} />} /></Form.Item></Col>)}</Row>
      <Alert type={total === 100 && validWeights ? 'success' : 'warning'} showIcon title={`当前合计 ${total}%，必须等于 100%，单项可为 0%`} />
      <Typography.Title level={2} className="simple-section-title">附加要求</Typography.Title>
      <ScreeningCriteriaEditor value={criteria} onChange={value => setValue('criteria', value)} />
      <p className="dialog-description">公司必须项始终生效。同一事实已配置公司评分要求时，个人同类加分不重复累计；总附加分上限 30 分。</p>
      <Form.Item label="默认排序"><Controller name="defaultSortMode" control={control} render={({ field }) => <Select {...field} aria-label="默认排序" options={SCREENING_SORT_OPTIONS} />} /></Form.Item>
    </Form>
  </Modal>;
}
