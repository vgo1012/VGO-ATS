import { LeftOutlined, ReloadOutlined, RightOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Button, DatePicker, Radio, Select, Skeleton, Table, Tag } from 'antd';
import { useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ApiProblemError } from '../api/problem';
import { FeedbackAlert } from '../components/FeedbackAlert';
import { businessDay, dateRangeParams } from '../components/dateRange';
import { JdSelector } from '../demand/JdSelector';
import { ChannelDonut, CompletionDonut, LocationDistribution, PortraitOverview, ReferenceTrend } from '../reporting/ReferenceCharts';
import { analyticsApi, analyticsKeys, type AnalyticsFilters, type AnalyticsHrWorkload } from '../reporting/analyticsApi';
import { periodOptions, periodRange, rate, readAnchor, readPeriod, trendBuckets } from '../reporting/analyticsPeriod';
import '../reporting/analytics.css';
import { AnalyticsMotionContext, useAnalyticsMotionPreference } from '../reporting/analyticsMotion';
import { AnalyticsNumber } from '../reporting/AnalyticsNumber';

function days(value: number | null) {
  return value === null ? '—' : (value / 24).toLocaleString('zh-CN', { maximumFractionDigits: 0 });
}

export function RecruitmentAnalyticsPage() {
  const motionAllowed = useAnalyticsMotionPreference();
  const [search, setSearch] = useSearchParams();
  const period = readPeriod(search.get('period'));
  const anchorValue = readAnchor(search.get('date')).format('YYYY-MM-DD');
  const range = useMemo(() => periodRange(period, readAnchor(anchorValue)), [period, anchorValue]);
  const departmentId = search.get('departmentId') || undefined;
  const jdId = search.get('jdId') || undefined;
  const recruitmentType = search.get('recruitmentType');
  const filters = useMemo<AnalyticsFilters>(() => ({
    ...(period === 'all' ? {} : dateRangeParams(range)),
    ...(departmentId ? { departmentId } : {}),
    ...(jdId ? { jdId } : {}),
    ...(recruitmentType === 'INTERNAL' || recruitmentType === 'EXTERNAL' ? { recruitmentType } : {}),
  }), [period, range, departmentId, jdId, recruitmentType]);
  const report = useQuery({
    queryKey: analyticsKeys.report(filters),
    queryFn: ({ signal }) => analyticsApi.report(filters, signal),
    staleTime: 30_000,
    retry: false,
  });
  const [portraitResult, setPortraitResult] = useState<'ALL' | 'PASSED' | 'REJECTED'>('ALL');
  const [hrId, setHrId] = useState<string>('ALL');
  const data = report.data;
  const periodLabel = period === 'all' ? `累计至 ${data ? businessDayTimestamp(data.asOf) : businessDay().format('YYYY.MM.DD')}`
    : `${range[0].format('YYYY.MM.DD')} — ${range[1].format('YYYY.MM.DD')}`;
  const denied = report.error instanceof ApiProblemError && report.error.problem.status === 403;
  const resumes = data?.funnel.find(item => item.key === 'RESUME')?.count ?? 0;
  const applications = data?.funnel.find(item => item.key === 'APPLICATION')?.count ?? 0;
  const offers = data?.funnel.find(item => item.key === 'OFFER')?.count ?? 0;
  const onboarded = data?.funnel.find(item => item.key === 'ONBOARDED')?.count ?? 0;
  const rejected = data?.portraits.filter(item => item.dimension === 'AGE').reduce((total, item) => total + item.rejected, 0) ?? 0;
  const funnelSteps = data ? [
    { key: 'RESUME', label: period === 'all' ? '累计简历' : '本期简历', count: resumes },
    { key: 'APPLICATION', label: '进入流程', count: applications },
    { key: 'INTERVIEW_1', label: '一面', count: data.funnel.find(item => item.key === 'INTERVIEW_1')?.count ?? 0 },
    { key: 'INTERVIEW_2', label: '二面', count: data.funnel.find(item => item.key === 'INTERVIEW_2')?.count ?? 0 },
    { key: 'INTERVIEW_3_PLUS', label: '三面+', count: data.funnel.find(item => item.key === 'INTERVIEW_3_PLUS')?.count ?? 0 },
    { key: 'OFFER', label: 'Offer', count: offers },
    { key: 'ONBOARDED', label: '已入职', count: onboarded },
    { key: 'REJECTED', label: '不通过', count: rejected },
  ] : [];
  const funnelMaximum = Math.max(1, ...funnelSteps.map(step => step.count));
  const selectedHrId = data?.hrWorkload?.some(row => row.ownerUserId === hrId) ? hrId : 'ALL';
  const hrRows = data?.hrWorkload?.filter(row => selectedHrId === 'ALL' || row.ownerUserId === selectedHrId) ?? [];
  const hrTotal = data?.hrWorkload?.length ?? 0;
  const series = useMemo(() => trendBuckets(data?.trend ?? [], period, range), [data?.trend, period, range]);
  const setFilter = (key: string, value: string | undefined) => {
    setSearch(previous => {
      const next = new URLSearchParams(previous);
      if (value) next.set(key, value); else next.delete(key);
      return next;
    });
  };
  const shiftPeriod = (direction: number) => {
    if (period === 'all') return;
    setFilter('date', (period === 'quarter' ? readAnchor(anchorValue).add(direction * 3, 'month')
      : readAnchor(anchorValue).add(direction, period)).format('YYYY-MM-DD'));
  };

  return <AnalyticsMotionContext value={motionAllowed}><div className="workspace-page analytics-page" data-motion={motionAllowed ? 'on' : 'off'}>
    <header className="analytics-heading">
      <h1>招聘分析</h1>
      <div className="analytics-heading-actions">
        {data && !denied && <span className="analytics-muted">统计于 {readTimestamp(data.asOf)}</span>}
        <Button type="primary" aria-label="刷新" icon={<ReloadOutlined aria-hidden="true" />} loading={report.isFetching} onClick={() => void report.refetch()}>刷新数据</Button>
      </div>
    </header>
    <section className="analytics-filters" aria-label="招聘分析筛选">
      <div className="analytics-period-controls">
        <Radio.Group aria-label="统计周期" optionType="button" buttonStyle="solid" value={period} options={periodOptions} onChange={event => setFilter('period', String(event.target.value))} />
        {period !== 'all' && <div className="analytics-date-controls">
          <Button aria-label="上一周期" icon={<LeftOutlined />} onClick={() => shiftPeriod(-1)} />
          <DatePicker aria-label="选择统计期间" value={readAnchor(anchorValue)} picker={period} format={period === 'year' ? 'YYYY' : period === 'quarter' ? 'YYYY [Q]Q' : period === 'month' ? 'YYYY-MM' : 'YYYY-MM-DD'} allowClear={false} onChange={value => { if (value) setFilter('date', value.format('YYYY-MM-DD')); }} />
          <Button aria-label="下一周期" icon={<RightOutlined />} onClick={() => shiftPeriod(1)} />
        </div>}
        <span className="analytics-period-label">{periodLabel}</span>
      </div>
      <div className="analytics-dimension-controls">
        <label><span>岗位归属</span><Select aria-label="岗位归属" virtual={false} value={filters.recruitmentType ?? 'ALL'} onChange={value => setFilter('recruitmentType', value === 'ALL' ? undefined : value)} options={[{ value: 'ALL', label: '全部岗位' }, { value: 'INTERNAL', label: '内部招聘' }, { value: 'EXTERNAL', label: '外部招聘' }]} /></label>
        <label><span>部门</span><Select aria-label="部门" virtual={false} showSearch optionFilterProp="label" value={departmentId ?? 'ALL'} onChange={value => setFilter('departmentId', value === 'ALL' ? undefined : value)} options={[
          { value: 'ALL', label: '全部可见部门' },
          ...(data?.departments.map(item => ({ value: item.id, label: item.name })) ?? []),
          ...(departmentId && !data?.departments.some(item => item.id === departmentId) ? [{ value: departmentId, label: '已选部门' }] : []),
        ]} /></label>
        <div className="analytics-jd"><span>岗位</span><JdSelector {...(jdId ? { value: jdId } : {})} onChange={jd => setFilter('jdId', jd?.id)} placeholder="筛选岗位（含历史版本）" /></div>
        <Button type="text" onClick={() => setSearch({})}>重置筛选</Button>
      </div>
    </section>

    {report.error && <FeedbackAlert occurrence={report.error} type={denied ? 'warning' : 'error'} showIcon title={denied ? '暂无招聘分析查看权限' : '招聘分析加载失败'} description={denied ? '请使用有报表查看权限的账号。' : '请重试；当前筛选条件已保留。'} action={!denied ? <Button aria-label="重试" onClick={() => void report.refetch()}>重试</Button> : undefined} />}
    {report.isPending && <section className="analytics-panel" aria-label="正在加载招聘分析"><Skeleton active paragraph={{ rows: 8 }} /></section>}
    {data && !denied && <>
      {report.isFetching && <p role="status" className="sr-only">正在刷新，当前显示上次统计结果。</p>}
      <dl className="analytics-summary" aria-label="招聘核心指标">
        {[
          { label: '入库简历', value: resumes, unit: '人', note: '按人去重' },
          { label: '进入流程', value: applications, unit: '条', note: '按流程创建时间统计' },
          { label: '进入入职流程', value: offers, unit: '条', note: `占流程 ${rate(offers, applications)}` },
          { label: '已入职', value: onboarded, unit: '条', note: `占流程 ${rate(onboarded, applications)}` },
        ].map(item => <div key={item.label}><dt>{item.label}</dt><dd><strong><AnalyticsNumber value={item.value} /></strong><span>{item.unit}</span></dd><dd className="analytics-summary-note">{item.note}</dd></div>)}
      </dl>
      <div className="analytics-dashboard">
        <div className="analytics-main-row">
          <section id="analysis-funnel" className="analytics-panel analytics-funnel-panel" aria-labelledby="analytics-funnel-title">
            <div className="analytics-section-heading"><h2 id="analytics-funnel-title">招聘流程转化</h2><span className="analytics-data-label">截至当前的流程结果</span></div>
            <div className="analytics-funnel" role="region" aria-labelledby="analytics-funnel-title" tabIndex={0}>
              {funnelSteps.map((item, index) => {
                const next = funnelSteps[index + 1];
                const stageRate = rate(item.count, applications);
                const shareLabel = item.key === 'RESUME' ? '入库简历与进入流程统计队列不同，不计算转化率'
                  : item.key === 'APPLICATION' ? `进入流程为比例基数 ${applications} 条`
                    : `${item.label}占进入流程比例 ${stageRate}（${item.count} ÷ ${applications}）`;
                return <div className={`analytics-funnel-stage ${item.key === 'REJECTED' ? 'analytics-funnel-outcome' : ''}`} key={item.key}>
                  <div className="analytics-funnel-step"><span>{item.label}</span><div><strong><AnalyticsNumber value={item.count} /></strong><small>{item.key === 'RESUME' ? '人' : '条'}</small></div></div>
                  <div className="analytics-funnel-bar" aria-hidden="true"><i style={{ height: `${item.count / funnelMaximum * 100}%` }} /></div>
                  <div className="analytics-funnel-arrow" data-has-next={Boolean(next && next.key !== 'REJECTED')}><span role="img" aria-label={shareLabel}>{item.key === 'RESUME' ? '按人去重' : item.key === 'APPLICATION' ? '比例基数' : `占流程 ${stageRate}`}</span>{next && next.key !== 'REJECTED' && <i aria-hidden="true" />}</div>
                </div>;
              })}
            </div>
            <p className="analytics-footnote">简历人数与流程数量分别统计；比例以进入流程为基数。</p>
            <div className="analytics-conversion-summary"><span>平均招聘周期 <b>{days(data.efficiency.averageTotalHours)} 天</b></span><span>在途平均等待 <b>{days(data.efficiency.averageActiveHours)} 天</b></span><span>流程至 Offer <b>{rate(offers, applications)}</b></span><span>流程至入职 <b>{rate(onboarded, applications)}</b></span></div>
          </section>
          <section className="analytics-panel analytics-trend-panel" aria-labelledby="analytics-trend-title">
            <div className="analytics-section-heading"><h2 id="analytics-trend-title">招聘趋势</h2><span className="analytics-data-label">{{ all: '累计每月趋势', week: '每日趋势', month: '每周趋势', quarter: '每月趋势', year: '每季度趋势' }[period]}</span></div>
            <ReferenceTrend rows={series} period={period} range={range} />
          </section>
        </div>
        <div className="analytics-detail-row">
          <section className="analytics-panel analytics-region-panel" aria-labelledby="analytics-region-title">
            <div className="analytics-section-heading"><h2 id="analytics-region-title">地区分布</h2><span className="analytics-data-label">岗位工作地区 · 入库简历</span></div>
            <LocationDistribution rows={data.portraits} />
          </section>
          <section id="analysis-demand" className="analytics-panel analytics-hc-panel" aria-labelledby="analytics-demand-title">
            <div className="analytics-section-heading"><h2 id="analytics-demand-title">招聘需求与风险</h2><span className="analytics-data-label">{data.demand.activeJobs} 个在招岗位</span></div>
            <div className="analytics-hc-metrics">
              <div><span>HC 数量</span><strong><AnalyticsNumber value={data.demand.headcount} /></strong></div><div><span>已占用</span><strong><AnalyticsNumber value={data.demand.occupied} /></strong></div><div><span>剩余名额</span><strong><AnalyticsNumber value={data.demand.openHeadcount} /></strong></div><div title="按候选人当前负责人和部门权限统计"><span>有效黑名单</span><strong><AnalyticsNumber value={data.risk.activeBlacklists} /></strong></div>
            </div>
            <div className="analytics-hc-completion"><CompletionDonut occupied={data.demand.occupied} headcount={data.demand.headcount} /><div className="analytics-hc-detail"><h3>编制占用</h3><p>已占用 {data.demand.occupied} / HC {data.demand.headcount}</p><div className="analytics-capacity-track" aria-hidden="true"><i style={{ width: `${Math.min(1, data.demand.occupied / Math.max(1, data.demand.headcount)) * 100}%` }} /></div><span className="analytics-muted">剩余 {data.demand.openHeadcount} 个名额</span></div></div>
            {data.demand.unknownHeadcountJobs > 0 && <p className="analytics-footnote">{data.demand.unknownHeadcountJobs} 个岗位未填写编制</p>}
          </section>
        </div>
        <div className="analytics-portrait-row">
          <section id="analysis-portraits" className="analytics-panel analytics-age-panel" aria-labelledby="analytics-portraits-title">
            <div className="analytics-section-heading analytics-portrait-heading"><h2 id="analytics-portraits-title">年龄分布</h2><Radio.Group aria-label="画像结果" size="small" optionType="button" buttonStyle="solid" value={portraitResult} options={[{ value: 'ALL', label: '全部' }, { value: 'PASSED', label: '通过' }, { value: 'REJECTED', label: '不通过' }]} onChange={event => setPortraitResult(event.target.value as 'ALL' | 'PASSED' | 'REJECTED')} /></div>
            <PortraitOverview rows={data.portraits} result={portraitResult} mode="age" />
          </section>
          <section className="analytics-panel analytics-education-panel" aria-labelledby="analytics-education-title">
            <div className="analytics-section-heading"><h2 id="analytics-education-title">学历分布</h2></div>
            <PortraitOverview rows={data.portraits} result={portraitResult} mode="education" />
          </section>
          <section id="analysis-channels" className="analytics-panel analytics-channel-panel" aria-labelledby="analytics-channels-title">
            <div className="analytics-section-heading"><h2 id="analytics-channels-title">招聘渠道</h2><span className="analytics-data-label">{period === 'all' ? '累计入库简历' : '本期入库简历'}</span></div>
            <ChannelDonut rows={data.channels} />
          </section>
        </div>
      </div>
    </>}

    {data && !denied && data.hrWorkload != null && <section className="analytics-panel analytics-hr-panel" aria-labelledby="analytics-hr-title">
      <div className="analytics-section-heading"><h2 id="analytics-hr-title">员工招聘统计</h2><Select aria-label="招聘人员" virtual={false} showSearch optionFilterProp="label" value={selectedHrId} onChange={setHrId} options={[{ value: 'ALL', label: '全部人员' }, ...data.hrWorkload.map(row => ({ value: row.ownerUserId, label: `${row.displayName}${row.self ? '（我）' : ''}${row.inactive ? '（已停用）' : ''}` }))]} /></div>
      <p className="analytics-footnote">与上方筛选一致；流程按当前负责人统计，展示所选流程截至当前的结果；入库简历按人数统计。</p>
      <Table<AnalyticsHrWorkload> key={selectedHrId} size="small" rowKey="ownerUserId" dataSource={hrRows} pagination={{ defaultPageSize: 10, showSizeChanger: false, hideOnSinglePage: true, showTotal: () => `共 ${hrTotal} 位员工` }} scroll={{ x: 1040 }} locale={{ emptyText: '当前权限范围内暂无员工' }} columns={[
        { title: '员工', dataIndex: 'displayName', width: 200, fixed: 'left', render: (_: string, row: AnalyticsHrWorkload) => <div className="analytics-employee-name"><span>{row.displayName}</span>{row.self && <Tag>我</Tag>}{row.inactive && <Tag>已停用</Tag>}</div> },
        ...([{ key: 'resumes', title: '入库简历人数' }, { key: 'applications', title: '进入流程' }, { key: 'interview1', title: '一面' }, { key: 'interview2', title: '二面' }, { key: 'interview3Plus', title: '三面+' }, { key: 'offers', title: 'Offer' }, { key: 'rejected', title: '不通过' }, { key: 'onboarded', title: '入职' }] as const).map(column => ({ title: column.title, dataIndex: column.key, key: column.key, width: column.key === 'resumes' ? 140 : 100, align: 'center' as const,
          render: (value: number) => <span className={`analytics-employee-count${value === 0 ? ' is-zero' : ''}`}>{value}</span> })),
      ]} />
      {hrRows.length <= 10 && <p className="analytics-hr-total">共 {hrTotal} 位员工</p>}
    </section>}

  </div></AnalyticsMotionContext>;
}

function readTimestamp(value: string) {
  return new Date(value).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' });
}

function businessDayTimestamp(value: string) {
  return new Date(value).toLocaleDateString('zh-CN', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).replaceAll('/', '.');
}
