import { jobLabel } from '../demand/jobLabel';
import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { CheckCircleOutlined, CloseCircleFilled, DownOutlined, FileTextOutlined, SearchOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Button, Empty, Input, Space, Tag } from 'antd';
import { useDeferredValue, useState, type ReactNode } from 'react';
import { ApiProblemError } from '../api/problem';
import { WorkspaceDialog } from '../components/WorkspaceDialog';
import { demandApi, type JdSummary, type JdVersion } from '../demand/api';
import { useUserNames } from '../display/BusinessReferences';
import { displayEnum } from '../display/labels';
import { useScreeningJobs } from './useScreeningJobs';
import './screeningJobHeader.css';

export function ScreeningJobHeader({ jd, version, loading, disabled, onChange, schemeControls, onRescore, rescoreDisabled, rescoreLoading=false, weights }: {
  jd: JdSummary | undefined;
  version: JdVersion | undefined;
  loading: boolean;
  disabled: boolean;
  onChange: (jd: JdSummary) => void;
  schemeControls: ReactNode;
  onRescore: () => void;
  rescoreDisabled: boolean;
  rescoreLoading?: boolean;
  weights: Array<{ label: string; value: string }>;
}) {
  const [panel, setPanel] = useState<'jobs' | 'details'>();
  const [search, setSearch] = useState('');
  const [retryingVersion, setRetryingVersion] = useState<string>();
  const [retryingSearch, setRetryingSearch] = useState<string>();
  const query = useDeferredValue(search.trim());
  const jobs = useScreeningJobs(query, panel === 'jobs');
  const jobsDenied = jobs.error instanceof ApiProblemError && [401, 403].includes(jobs.error.problem.status);
  const jobsRetrying = retryingSearch === query;
  const jobsBusy = jobs.isFetching || jobsRetrying || query !== search.trim();
  const items = [...new Map((jobs.data?.pages.flatMap(page => page.items) ?? []).map(item => [item.id, item])).values()];
  const owners = useUserNames(panel === 'jobs' ? items.map(item => item.ownerUserId).filter(Boolean) : []);
  const facts = useQuery({ queryKey: ['demand', 'jd-facts', version?.id], queryFn: () => demandApi.jdFacts(version!.id), enabled: Boolean(version), staleTime: 60_000, retry: false });
  const factsDenied = facts.error instanceof ApiProblemError && [401, 403].includes(facts.error.problem.status);
  const visibleFacts = factsDenied ? undefined : facts.data?.facts;
  const professional = visibleFacts?.professional;
  const conditions = visibleFacts?.controlledConditions;
  const tags = [
    ...(professional?.minimumYears != null ? [`${professional.minimumYears}年以上`] : []),
    ...(conditions?.educations.map(value => displayEnum(value)) ?? []),
    ...(professional?.coreSkills.map(skill => skill.name) ?? []),
  ];
  const retryingFacts = Boolean(version && retryingVersion === version.id);
  const factsBusy = facts.isFetching || retryingFacts;
  const factsFeedback = facts.error || retryingFacts ? <Alert type={factsDenied ? 'info' : 'warning'}
    title={factsDenied ? '暂无岗位事实的查看权限' : '岗位事实读取失败'}
    action={<Button aria-label="重试岗位事实" aria-busy={factsBusy} loading={factsBusy} disabled={factsBusy}
      onClick={() => {
        const versionId = version?.id;
        setRetryingVersion(versionId);
        void facts.refetch({ cancelRefetch: false }).finally(() => setRetryingVersion(current => current === versionId ? undefined : current));
      }}>重试岗位事实</Button>} /> : null;
  const close = () => setPanel(undefined);
  return <section className="screening-job-header" aria-label="当前筛选岗位">
    <div className="screening-job-heading">
      <h1><button type="button" className="screening-job-title" aria-label="选择招聘岗位" aria-haspopup="dialog" disabled={disabled} onClick={() => { setSearch(''); setPanel('jobs'); }}>
        <span>{(jd ? jobLabel(jd.title, jd.code) : undefined) ?? (loading ? '正在读取岗位…' : '选择招聘岗位')}</span><DownOutlined />
      </button></h1>
      <Button type="text" aria-label="JD 详情" icon={<FileTextOutlined aria-hidden />} disabled={!version} onClick={() => setPanel('details')}>JD 详情</Button>
      {jd?.clientName || jd?.distinction ? <span className="screening-job-context">{[jd.clientName, jd.distinction].filter(Boolean).join(' · ')}</span> : null}
    </div>
    <div className="screening-job-model"><div className="screening-model-summary">
      <div className="screening-model-label"><b>评分模型</b><span>（专业分100分）</span></div>
      <div className="screening-job-weights">{weights.map(weight => <span key={weight.label}><SafetyCertificateOutlined /><span>{weight.label}</span><b>{weight.value}</b></span>)}</div>
      {tags.length ? <Space className="screening-job-tags" wrap size={[8, 8]}>{[...new Set(tags)].slice(0, 6).map(tag => <Tag key={tag}>{tag}</Tag>)}</Space> : null}
    </div><div className="screening-model-controls" role="group" aria-label="评分配置与操作">
      {schemeControls}<Button type="primary" title="仅个人方案需要重新评分，公司默认直接使用已有评分" loading={rescoreLoading} disabled={rescoreDisabled} onClick={onRescore}>重新评分</Button>
    </div></div>
    {factsFeedback}
    <WorkspaceDialog title={<h2 className="screening-jd-dialog-title">选择招聘岗位</h2>} width={900} open={panel === 'jobs'} footer={null} onCancel={close} destroyOnHidden>
      <p className="dialog-description">切换后将同步更新岗位标签与筛选要求</p>
      <Input className="screening-jd-search" aria-label="搜索招聘岗位" placeholder="搜索 JD 编号、岗位名称、地区或客户" prefix={<SearchOutlined />} value={search} onChange={event => setSearch(event.target.value)} allowClear={{ clearIcon: <CloseCircleFilled aria-label="清空搜索" /> }} />
      <div className="screening-jd-options" role="region" aria-label="招聘岗位列表" tabIndex={0} aria-busy={jobsBusy} onScroll={event => {
        const node = event.currentTarget;
        if (node.scrollHeight - node.scrollTop - node.clientHeight < 40 && jobs.hasNextPage && !jobsBusy && !jobs.error) void jobs.fetchNextPage({ cancelRefetch: false });
      }}>
        <div className="screening-jd-grid">{items.map(item => {
          const owner = !item.ownerUserId ? '未分配' : owners.people.get(item.ownerUserId) ?? (owners.loading ? '加载姓名…' : '成员姓名暂不可用');
          const metadata = [item.clientName, item.distinction, `V${item.currentVersionNo}`, `所属人：${owner}`].filter(Boolean).join(' · ');
          return <button type="button" className="screening-jd-option" key={item.id} aria-pressed={jd?.id === item.id} disabled={disabled || query !== search.trim()} onClick={() => { onChange(item); close(); }}>
          <span><b>{jobLabel(item.title, item.code)}</b><small title={metadata}>{metadata}</small></span>
          {jd?.id === item.id ? <CheckCircleOutlined aria-label="已选中" /> : null}
        </button>; })}</div>
        {jobs.isPending ? <p role="status">正在读取招聘岗位…</p> : !items.length && !jobs.error ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={query ? '没有匹配的岗位，请更换关键词' : '暂无可见招聘岗位'} /> : null}
        {jobs.error || jobsRetrying ? <Alert occurrence={jobs.error} type={jobsDenied ? 'info' : 'warning'} title={jobsDenied ? '暂无招聘岗位的查看权限' : '岗位加载失败'}
          action={<Button aria-label="重试岗位列表" aria-busy={jobsBusy} loading={jobsBusy} disabled={jobsBusy} onClick={() => {
            const searchKey = query;
            setRetryingSearch(searchKey);
            const request = jobs.isFetchNextPageError && !jobsDenied ? jobs.fetchNextPage({ cancelRefetch: false }) : jobs.refetch({ cancelRefetch: false });
            void request.finally(() => setRetryingSearch(current => current === searchKey ? undefined : current));
          }}>重试岗位列表</Button>} /> : null}
        {jobs.hasNextPage && !jobs.error ? <Button block type="text" loading={jobsBusy} disabled={jobsBusy} onClick={() => void jobs.fetchNextPage({ cancelRefetch: false })}>加载更多岗位</Button> : null}
      </div>
    </WorkspaceDialog>
    <WorkspaceDialog title={<h2 className="screening-jd-dialog-title">{(jd ? jobLabel(jd.title, jd.code) : undefined) ?? '招聘岗位'} · JD 详情</h2>} width={900} open={panel === 'details'} footer={null} onCancel={close} destroyOnHidden>
      <p className="dialog-description">完整岗位职责与任职要求 · V{version?.versionNo}</p>
      {factsFeedback}
      {visibleFacts?.summary ? <p className="screening-jd-detail-summary">{visibleFacts.summary}</p> : null}
      {tags.length ? <Space wrap>{[...new Set(tags)].map(tag => <Tag key={tag}>{tag}</Tag>)}</Space> : null}
      {professional ? <div className="screening-jd-detail-columns">
        <section><h3>岗位职责</h3>{professional.responsibilities.length ? <ul>{professional.responsibilities.map((text, index) => <li key={index}>{text}</li>)}</ul> : <p>请参阅完整 JD 原文</p>}</section>
        <section><h3>任职要求</h3>{tags.length ? <ul>{[...new Set(tags)].map(tag => <li key={tag}>{tag}</li>)}</ul> : <p>请参阅完整 JD 原文</p>}</section>
      </div> : null}
      <section className="screening-jd-original"><h3>完整 JD 原文</h3><p>{version?.rawText || '当前版本未提供 JD 原文'}</p></section>
    </WorkspaceDialog>
  </section>;
}
