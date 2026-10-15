import { scrollableTableHeader } from '../components/scrollableTableHeader';
import { PaginatedTable } from '../components/PaginatedTable';
import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Card, Descriptions, Popconfirm, Space, Tag, Typography } from 'antd';
import { useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { demandApi, type MatchPlanDetails } from '../demand/api';
import { ScoringPlanEditor } from '../demand/ScoringPlanEditor';
import { ApiProblemError } from '../api/problem';
import { createIdempotencyKey } from '../api/idempotency';

export function ScoringGovernancePage() {
  const queryClient = useQueryClient();
  const { message } = App.useApp();
  const [editing, setEditing] = useState<MatchPlanDetails>();
  const cloneAttempt = useRef<{ sourceId: string; key: string; uncertain: boolean } | undefined>(undefined);
  const [cloneError, setCloneError] = useState<{ error: unknown; uncertain: boolean }>();
  const defaults = useQuery({ queryKey: ['matching', 'defaults'], queryFn: demandApi.scoringDefaults });
  const versions = useQuery({ queryKey: ['matching', 'scoring-plans'], queryFn: () => demandApi.scoringConfigs() });
  const config = defaults.data?.scoringConfiguration;
  const reload = async () => { await Promise.all([queryClient.invalidateQueries({ queryKey: ['matching'] }), queryClient.invalidateQueries({ queryKey: ['demand'] })]); };
  const clone = useMutation({
    mutationFn: (attempt: { sourceId: string; key: string }) => demandApi.cloneScoringConfig(attempt.sourceId, attempt.key),
    onError: error => {
      const rejected = error instanceof ApiProblemError && error.problem.status >= 400 && error.problem.status < 500 && error.problem.status !== 408;
      // A later rejection cannot disprove an earlier save whose response was lost.
      const uncertain = Boolean(cloneAttempt.current?.uncertain) || !rejected;
      if (uncertain && cloneAttempt.current) cloneAttempt.current.uncertain = true;
      else cloneAttempt.current = undefined;
      setCloneError({ error, uncertain });
    },
    onSuccess: async result => { await reload(); setEditing(result); cloneAttempt.current = undefined; setCloneError(undefined); },
  });
  const startClone = (sourceId?: string) => {
    if (clone.isPending) return;
    if (!cloneAttempt.current && sourceId) cloneAttempt.current = { sourceId, key: createIdempotencyKey(), uncertain: false };
    if (cloneAttempt.current) clone.mutate(cloneAttempt.current);
  };
  const cloneUnconfirmed = clone.isPending || Boolean(cloneError?.uncertain);
  const activate = useMutation({ mutationFn: demandApi.activateScoringConfig, onSuccess: async () => { message.success('新版本已激活，之后的匹配自动使用最新已激活版本'); await reload(); } });
  const error = defaults.error ?? versions.error ?? activate.error;
  const rows = (versions.data?.items ?? []).filter(item => item.plan.id === config?.plan.id).sort((a, b) => b.version.versionNo - a.version.versionNo);
  return <div className="workspace-page scoring-governance-page">
    <section className="ledger-heading"><div><Typography.Title level={1}>评分方案</Typography.Title><Typography.Paragraph>统一配置公司评分规则。新版本先保存草稿，激活后自动用于新匹配；历史结果继续保留原评分版本。</Typography.Paragraph></div></section>
    {error ? <Alert occurrence={error} type="error" showIcon title={error instanceof ApiProblemError ? error.problem.detail : '评分配置操作失败，请重试'} action={<Button onClick={() => void reload()}>重试</Button>} /> : null}
    {cloneError ? <Alert occurrence={cloneError} type="error" showIcon title={cloneError.error instanceof ApiProblemError ? cloneError.error.problem.detail : '草稿创建结果尚未确认，请重试核对。'} action={<Button aria-label="重试" loading={clone.isPending} disabled={clone.isPending} onClick={() => startClone(clone.variables?.sourceId)}>重试</Button>} /> : null}
    {cloneError?.uncertain ? <Alert type="warning" showIcon title="草稿创建结果尚未确认" description="请重试核对原创建结果，再编辑、激活或复制其他版本。" /> : null}
    <Card title={config?.plan.name ?? '公司默认五维评分'} loading={defaults.isPending} extra={config || cloneError?.uncertain ? <Button type="primary" loading={clone.isPending} disabled={clone.isPending || activate.isPending} onClick={() => startClone((rows[0] ?? config)?.version.id)}>{cloneError?.uncertain ? '重试创建草稿' : `创建 V${Math.max(config!.version.versionNo, ...rows.map(item => item.version.versionNo)) + 1} 草稿`}</Button> : null}>
      {config ? <Descriptions items={[{ key: 'version', label: '当前生效', children: `V${config.version.versionNo}` }, { key: 'scope', label: '适用范围', children: '全部 JD 的新匹配' }, { key: 'bonus', label: '公司附加分上限', children: `${config.criterionPlan.planMaxBonusPoints} 分` }]} /> : null}
      <PaginatedTable rowKey={item => item.version.id} loading={versions.isPending} initialPageSize={20} totalLabel="已加载" scroll={{ x: 760 }} onHeaderRow={() => scrollableTableHeader('评分方案版本表头，可横向滚动')} dataSource={rows} columns={[
        { title: '规则版本', width: 130, render: (_, item) => `V${item.version.versionNo}` },
        { title: '状态', width: 160, render: (_, item) => <Tag color={item.version.id === config?.version.id ? 'green' : 'default'}>{item.version.id === config?.version.id ? '当前生效' : item.version.status === 'DRAFT' ? '草稿' : '历史已发布'}</Tag> },
        { title: '附加要求', render: (_, item) => `${item.criteria.length} 项 · 加分上限 ${item.criterionPlan.planMaxBonusPoints} 分` },
        { title: '操作', className: 'table-action-cell', width: 220, render: (_, item) => item.version.status === 'DRAFT' ? <Space><Button size="small" disabled={cloneUnconfirmed} onClick={() => setEditing(item)}>编辑</Button><Popconfirm disabled={cloneUnconfirmed} title={`激活 V${item.version.versionNo}？`} description="之后的新匹配自动使用最新已激活版本，已有结果保持不变。" onConfirm={() => activate.mutateAsync(item)}><Button size="small" type="primary" disabled={cloneUnconfirmed} loading={activate.isPending}>激活</Button></Popconfirm></Space> : <Button size="small" disabled={cloneUnconfirmed || activate.isPending} onClick={() => startClone(item.version.id)} loading={clone.isPending}>复制为下一版</Button> },
      ]} locale={{ emptyText: '尚无可维护的评分版本' }} />
      <Typography.Paragraph>在<Link to="/settings/preferences">个人筛选方案</Link>中统一保存五维比例、岗位必须项、附加分和默认排序；公司必须项始终保留，HR 作出最终决定。</Typography.Paragraph>
      <Alert showIcon type="info" title="三个分数各司其职" description="专业适配分按五维比例计算；附加分根据有证据支持的优先条件计算；综合参考分为两者之和。排序方式在个人筛选方案中配置，匹配时可以临时切换。" />
    </Card>
    {editing ? <ScoringPlanEditor value={editing} onClose={() => setEditing(undefined)} onSaved={reload} /> : null}
  </div>;
}
