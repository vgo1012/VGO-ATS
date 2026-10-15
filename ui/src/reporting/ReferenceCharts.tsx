import { Empty, Table, Modal } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import { init, use as registerCharts, type EChartsCoreOption, type EChartsType } from 'echarts/core';
import { BarChart, LineChart, PieChart } from 'echarts/charts';
import { GraphicComponent, GridComponent, TooltipComponent } from 'echarts/components';
import { SVGRenderer } from 'echarts/renderers';
import { LabelLayout } from 'echarts/features';
import { rate as percent, trendBucketLabel, trendBucketRangeLabel, type AnalyticsPeriod } from './analyticsPeriod';
import type { DateRange } from '../components/dateRange';
import type { AnalyticsChannel, AnalyticsPortrait, AnalyticsTrend } from './analyticsApi';
import { useAnalyticsMotion } from './analyticsMotion';

registerCharts([BarChart, LineChart, PieChart, GraphicComponent, GridComponent, TooltipComponent, SVGRenderer, LabelLayout]);
const colors = ['#2563eb', '#4b8bd2', '#6476a8', '#93b4e8', '#b6cffb', '#dbeafe'];
const ink = '#17233f';
const educationRanks: Record<string, number> = { 博士: 6, 硕士: 5, 本科: 4, 专科: 3, '高中/中专': 2, '其他/未标准化': 1, 未知: 0 };

function Chart({ option, label, className = '' }: { option: EChartsCoreOption; label: string; className?: string }) {
  const container = useRef<HTMLDivElement>(null);
  const instance = useRef<EChartsType | null>(null);
  const motion = useAnalyticsMotion();
  useEffect(() => {
    if (!container.current) return;
    const chart = init(container.current, undefined, { renderer: 'svg' });
    instance.current = chart;
    const observer = new ResizeObserver(() => chart.resize());
    observer.observe(container.current);
    return () => { observer.disconnect(); instance.current = null; chart.dispose(); };
  }, []);
  useEffect(() => {
    instance.current?.setOption({ ...option, animation: motion, animationDuration: 450,
      animationDurationUpdate: 300, animationEasing: 'cubicOut', animationEasingUpdate: 'cubicOut',
      textStyle: { fontFamily: '"Noto Sans SC", "PingFang SC", "Microsoft YaHei", system-ui, sans-serif', color: ink },
    }, { replaceMerge: ['series'] });
  }, [option, motion]);
  return <div ref={container} role="img" aria-label={label} className={`reference-chart ${className}`} />;
}

function Legend({ rows, total, showPercent = true }: { rows: Array<{ name: string; value: number }>; total: number; showPercent?: boolean }) {
  return <ul className="reference-chart-legend">{rows.map((row, index) => <li key={`${row.name}-${index}`}>
    <i style={{ backgroundColor: colors[index % colors.length] }} aria-hidden="true" />
    <span title={row.name}>{row.name}</span><b>{row.value}</b>{showPercent ? <span>{percent(row.value, total)}</span> : null}
  </li>)}</ul>;
}

function AgeDistribution({ rows, total }: { rows: Array<{ name: string; value: number }>; total: number }) {
  return <div className={`reference-age${rows.length === 1 ? ' is-single' : ''}`} role="img" aria-label={rows.map(row => `${row.name} ${row.value}，${percent(row.value, total)}`).join('；')}>
    {rows.map((row, index) => <div className="reference-age-row" key={`${row.name}-${index}`} aria-hidden="true">
      <span className="reference-age-label" title={row.name}>{row.name}</span>
      <span className="reference-age-track"><i className="reference-age-fill" style={{ width: `${row.value / total * 100}%` }} /></span>
      <strong>{percent(row.value, total)}</strong>
    </div>)}
  </div>;
}

export function ChannelDonut({ rows }: { rows: AnalyticsChannel[] }) {
  const total = rows.reduce((sum, row) => sum + row.resumes, 0);
  const channels = useMemo(() => {
    const sorted = [...rows].sort((a, b) => b.resumes - a.resumes);
    const top = sorted.slice(0, 3).map(row => ({ name: row.name, value: row.resumes }));
    if (sorted.length > 3) top.push({ name: '其它', value: sorted.slice(3).reduce((sum, row) => sum + row.resumes, 0) });
    return top;
  }, [rows]);
  const option = useMemo<EChartsCoreOption>(() => ({ color: colors, tooltip: { show: false },
    series: [{ type: 'pie', radius: ['58%', '87%'], center: ['50%', '50%'], label: { show: false },
      itemStyle: { borderWidth: 1, borderColor: '#fff' }, data: channels }],
    graphic: [{ type: 'group', left: 'center', top: 'middle', children: [
      { type: 'text', left: 'center', top: 0, style: { text: String(total), fill: ink, fontSize: 28, fontWeight: 700 } },
      { type: 'text', left: 'center', top: 34, style: { text: '入库简历', fill: ink, fontSize: 14 } },
    ] }],
  }), [channels, total]);
  if (!total) return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无招聘渠道数据" />;
  return <div className="reference-channel"><div className="reference-channel-figure"><Chart option={option} label={`招聘渠道，共 ${total} 份入库简历，明细见右侧`} className="reference-channel-donut" /><p className="analytics-description analytics-channel-note">入库简历来源占比</p></div>
    <Legend rows={channels} total={total} showPercent={false} /></div>;
}

function LocationBars({ rows }: { rows: Array<{ name: string; value: number }> }) {
  const locations = useMemo(() => {
    const counts = new Map<string, number>();
    rows.filter(row => row.value > 0).forEach(row => {
      const name = row.name.trim() || '未知';
      counts.set(name, (counts.get(name) ?? 0) + row.value);
    });
    return [...counts].map(([name, value]) => ({ name, value }))
      .sort((left, right) => right.value - left.value || left.name.localeCompare(right.name, 'zh-CN'));
  }, [rows]);
  const total = locations.reduce((sum, row) => sum + row.value, 0);
  const largest = locations[0]?.value ?? 1;
  if (!total) return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无岗位工作城市数据" />;
  return <div className="reference-location">
    <div className="reference-location-summary"><span>{locations.length} 个地区分组</span><span>合计 <strong>{total}</strong> 人</span></div>
    <ol className="reference-location-list" tabIndex={0} aria-label="入库简历工作城市数据">
      {locations.map((row, index) => <li className="reference-location-row" key={row.name}>
        <span className="reference-location-rank" aria-hidden="true">{String(index + 1).padStart(2, '0')}</span>
        <div className="reference-location-main">
          <div className="reference-location-label"><span>{row.name}</span><strong>{row.value}<span> 人</span></strong><span>{percent(row.value, total)}</span></div>
          <div className="reference-location-track" aria-hidden="true"><span className="reference-location-fill" style={{ width: `${row.value / largest * 100}%` }} /></div>
        </div>
      </li>)}
    </ol>
    <p className="analytics-description">按岗位工作地区分组；多地点和未知记录保留原始标签。</p>
  </div>;
}

export function LocationDistribution({ rows }: { rows: AnalyticsPortrait[] }) {
  const locations = useMemo(() => rows.filter(row => row.dimension === 'INTENDED_LOCATION')
    .map(row => ({ name: row.label, value: row.resumes })), [rows]);
  return <LocationBars rows={locations} />;
}

export function PortraitOverview({ rows, result, showGeography = true, mode = 'all' }: { rows: AnalyticsPortrait[]; result: 'ALL' | 'PASSED' | 'REJECTED'; showGeography?: boolean; mode?: 'all' | 'age' | 'education' }) {
  const groups = useMemo(() => {
    const values = (dimension: AnalyticsPortrait['dimension']) => rows.filter(row => row.dimension === dimension).map(row => ({ name: row.label,
      value: result === 'PASSED' ? row.passed : result === 'REJECTED' ? row.rejected : row.resumes }));
    return { age: values('AGE'), education: values('EDUCATION').sort((left, right) => (educationRanks[right.name] ?? 0) - (educationRanks[left.name] ?? 0)), location: values('INTENDED_LOCATION') };
  }, [rows, result]);
  const ageTotal = groups.age.reduce((sum, row) => sum + row.value, 0);
  const educationTotal = groups.education.reduce((sum, row) => sum + row.value, 0);
  const educationOption = useMemo<EChartsCoreOption>(() => ({ color: colors, grid: { left: 0, right: 0, top: 0, bottom: 0 }, tooltip: { show: false },
    xAxis: { type: 'value', show: false, max: educationTotal || 1 }, yAxis: { type: 'category', show: false, data: ['学历'] },
    series: groups.education.map(row => ({ type: 'bar', name: row.name, stack: 'education', barWidth: 17, data: [row.value] })),
  }), [groups.education, educationTotal]);
  const educationBars = useMemo<EChartsCoreOption>(() => ({ grid: { left: 4, right: 4, top: 24, bottom: 27 }, tooltip: { trigger: 'axis', renderMode: 'richText' },
    xAxis: { type: 'category', data: groups.education.map(row => row.name), axisTick: { show: false }, axisLine: { lineStyle: { color: '#dbe5f4' } }, axisLabel: { color: '#596a83', fontSize: 12, interval: 0, overflow: 'truncate', width: 80 } },
    yAxis: { type: 'value', show: false }, series: [{ type: 'bar', barMaxWidth: 56, data: groups.education.map(row => row.value),
      itemStyle: { color: colors[0], borderRadius: [3, 3, 0, 0] },
      label: { show: true, position: 'top', color: '#17233f', formatter: (params: { value?: unknown }) => percent(Number(params.value), educationTotal) } }],
  }), [groups.education, educationTotal]);
  if (!rows.length) return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={mode === 'age' ? '暂无年龄分布数据' : mode === 'education' ? '暂无学历分布数据' : '暂无候选人画像'} />;
  if (mode === 'age') return ageTotal ? <AgeDistribution rows={groups.age} total={ageTotal} /> : <p className="reference-chart-note">当前结果暂无年龄数据</p>;
  if (mode === 'education') return educationTotal ? <Chart option={educationBars} label={groups.education.map(row => `${row.name} ${row.value}，${percent(row.value, educationTotal)}`).join('；')} className="reference-education-bars" /> : <p className="reference-chart-note">当前结果暂无学历数据</p>;
  return <div className="reference-portrait"><div className="reference-demographics">
    <h3>年龄分布</h3>{ageTotal ? <AgeDistribution rows={groups.age} total={ageTotal} /> : <p className="reference-chart-note">当前结果暂无年龄数据</p>}
    <h3>学历分布</h3>{educationTotal ? <><Chart option={educationOption} label="学历分布，准确数据见下方图例" className="reference-education" /><Legend rows={groups.education} total={educationTotal} /></> : <p className="reference-chart-note">当前结果暂无学历数据</p>}
  </div>{showGeography && <div className="reference-geography"><h3>岗位工作城市分布</h3>{groups.location.some(row => row.value > 0) ? <LocationBars rows={groups.location} /> : <p className="reference-chart-note">当前结果暂无岗位工作地区数据</p>}</div>}</div>;
}

const trendSeries = [{ key: 'applications', label: '新建流程' }, { key: 'offers', label: '进入入职流程' }, { key: 'onboarded', label: '已入职' }] as const;
export function ReferenceTrend({ rows, period = 'week', range }: { rows: AnalyticsTrend[]; period?: AnalyticsPeriod; range?: DateRange }) {
  const [table, setTable] = useState(false);
  const option = useMemo<EChartsCoreOption>(() => ({ color: colors, grid: { left: 40, right: 24, top: 38, bottom: 27 },
    tooltip: { trigger: 'axis', renderMode: 'richText' }, xAxis: { type: 'category', boundaryGap: false, data: rows.map(row => trendBucketLabel(row.date, period)),
      axisLine: { lineStyle: { color: '#dbe5f4' } }, axisLabel: { color: '#596a83', fontSize: 13, margin: 12, hideOverlap: true }, splitLine: { show: true, lineStyle: { color: '#eef2f6' } } },
    yAxis: { type: 'value', minInterval: 1, splitNumber: 3, axisLabel: { color: '#596a83', fontSize: 13, margin: 12, hideOverlap: true }, splitLine: { lineStyle: { color: '#eef2f6' } } },
    series: trendSeries.map((item, index) => ({ type: 'line', name: item.label, smooth: 0.3, symbol: 'circle', symbolSize: 9,
      lineStyle: { width: 2 }, itemStyle: { borderWidth: 1, borderColor: '#fff' }, label: { show: rows.length <= 14, position: 'top', distance: 5, color: '#17233f', fontSize: 13,
        formatter: (point: { dataIndex: number; value: unknown }) => {
          const row = rows[point.dataIndex];
          if (!row) return '';
          const value = Number(point.value);
          return trendSeries.slice(0, index).some(previous => row[previous.key] === value) ? '' : String(value);
        } }, labelLayout: { hideOverlap: true }, clip: true,
      areaStyle: { opacity: index === 0 ? 0.15 : 0.06 }, data: rows.map(row => row[item.key]) })),
  }), [rows, period]);
  return <div className="reference-trend"><div className="reference-trend-toolbar"><div className="reference-trend-legend">{trendSeries.map((item, index) => <span key={item.key}><i style={{ background: colors[index] ?? colors[0] }} />{item.label}</span>)}</div>
    <button className="analytics-text-button" type="button" aria-expanded={table} onClick={() => setTable(value => !value)}>{table ? '收起数据表' : '查看数据表'}</button></div>
    {rows.some(row => row.applications || row.offers || row.onboarded) ? <Chart option={option} label="按流程创建日期统计新建流程，以及这些流程截至当前的 Offer 和入职结果；展开数据表可查看准确数值" className="reference-trend-chart" /> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="所选期间暂无招聘流程" />}
    <Modal centered rootClassName="workspace-dialog-root" className="workspace-dialog" transitionName="workspace-dialog-motion" maskTransitionName="workspace-mask-motion" title="招聘趋势明细" open={table} onCancel={() => setTable(false)} footer={null} width={760} destroyOnHidden><Table<AnalyticsTrend> size="small" pagination={false} rowKey="date" dataSource={rows} scroll={{ x: 420, y: 220 }} columns={[{ title: '流程创建日期', dataIndex: 'date', render: (date: string) => range ? trendBucketRangeLabel(date, period, range) : date }, ...trendSeries.map(item => ({ title: item.label, dataIndex: item.key }))]} /></Modal>
  </div>;
}

export function CompletionDonut({ occupied, headcount }: { occupied: number; headcount: number }) {
  const option = useMemo<EChartsCoreOption>(() => ({ color: [colors[0] ?? ink, '#dbeafe'], series: [{ type: 'pie', silent: true, radius: ['69%', '91%'], label: { show: false },
    data: [{ value: Math.max(0, Math.min(occupied, headcount)) }, { value: Math.max(0, headcount - occupied) || (headcount <= 0 ? 1 : 0) }] }],
    graphic: [{ type: 'text', left: 'center', top: 'center', style: { text: percent(occupied, headcount), fill: ink, fontSize: 16, fontWeight: 700 } }],
  }), [occupied, headcount]);
  return <Chart option={option} label={`HC 占用 ${occupied}，计划 ${headcount}，占用率 ${percent(occupied, headcount)}`} className="reference-completion" />;
}
