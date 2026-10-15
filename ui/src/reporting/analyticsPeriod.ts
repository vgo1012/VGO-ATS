import dayjs, { type Dayjs } from 'dayjs';
import quarterOfYear from 'dayjs/plugin/quarterOfYear';
import { businessDay, businessWeek, type DateRange } from '../components/dateRange';
import type { AnalyticsTrend } from './analyticsApi';

dayjs.extend(quarterOfYear);

export type AnalyticsPeriod = 'all' | 'week' | 'month' | 'quarter' | 'year';
export const periodOptions: Array<{ value: AnalyticsPeriod; label: string }> = [
  { value: 'all', label: '累计' },
  { value: 'week', label: '周' }, { value: 'month', label: '月' },
  { value: 'quarter', label: '季度' }, { value: 'year', label: '年' },
];

export function readPeriod(value: string | null): AnalyticsPeriod {
  return periodOptions.find(item => item.value === value)?.value ?? 'month';
}

export function readAnchor(value: string | null): Dayjs {
  if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return businessDay();
  const parsed = dayjs(value);
  return parsed.isValid() && parsed.format('YYYY-MM-DD') === value ? parsed : businessDay();
}

export function periodRange(period: AnalyticsPeriod, date: Dayjs): DateRange {
  if (period === 'all') return [dayjs('1970-01-01'), businessDay()];
  if (period === 'week') return businessWeek(date);
  return [date.startOf(period), date.endOf(period).startOf('day')];
}

function bucketStart(date: Dayjs, period: AnalyticsPeriod, range: DateRange): Dayjs {
  const start = period === 'month' ? businessWeek(date)[0]
    : period === 'quarter' || period === 'all' ? date.startOf('month')
      : period === 'year' ? date.startOf('quarter') : date.startOf('day');
  return start.isBefore(range[0], 'day') ? range[0] : start;
}

export function trendBucketLabel(date: string, period: AnalyticsPeriod): string {
  const parsed = dayjs(date);
  if (period === 'all') return parsed.format('YYYY-MM');
  if (period === 'year') return `第${parsed.quarter()}季度`;
  if (period === 'quarter') return `${parsed.month() + 1}月`;
  if (period === 'month') {
    const offset = (parsed.startOf('month').day() + 6) % 7;
    return `第${Math.floor((parsed.date() - 1 + offset) / 7) + 1}周`;
  }
  return parsed.format('M/D');
}

/** 提示与数据表显示真实分桶日期，月首末的自然周裁剪到所选范围。 */
export function trendBucketRangeLabel(date: string, period: AnalyticsPeriod, range: DateRange): string {
  const start = dayjs(date);
  const naturalEnd = period === 'month' ? businessWeek(start)[1]
    : period === 'quarter' || period === 'all' ? start.endOf('month')
      : period === 'year' ? start.endOf('quarter') : start;
  const end = naturalEnd.isAfter(range[1], 'day') ? range[1] : naturalEnd;
  return start.isSame(end, 'day') ? start.format('YYYY-MM-DD')
    : `${start.format('YYYY-MM-DD')} ～ ${end.format('YYYY-MM-DD')}`;
}

/** 累计从首条可见流程按月分桶，周按日、月按周、季度按月、年按季度；空桶补零。 */
export function trendBuckets(rows: AnalyticsTrend[], period: AnalyticsPeriod, range: DateRange): AnalyticsTrend[] {
  const result = new Map<string, AnalyticsTrend>();
  const dates = period === 'all' ? rows.map(row => dayjs(row.date))
    .filter(date => date.isValid() && !date.isBefore(range[0], 'day') && !date.isAfter(range[1], 'day'))
    .sort((left, right) => left.valueOf() - right.valueOf()) : [];
  if (period === 'all' && !dates.length) return [];
  const start = period === 'all' ? dates[0]!.startOf('month') : range[0];
  for (let date = start; !date.isAfter(range[1], 'day'); date = date.add(1, period === 'all' ? 'month' : 'day')) {
    const key = bucketStart(date, period, range).format('YYYY-MM-DD');
    if (!result.has(key)) result.set(key, { date: key, applications: 0, offers: 0, onboarded: 0 });
  }
  for (const row of rows) {
    const date = dayjs(row.date);
    if (!date.isValid() || date.isBefore(range[0], 'day') || date.isAfter(range[1], 'day')) continue;
    const bucket = result.get(bucketStart(date, period, range).format('YYYY-MM-DD'));
    if (bucket) {
      bucket.applications += row.applications;
      bucket.offers += row.offers;
      bucket.onboarded += row.onboarded;
    }
  }
  return [...result.values()];
}

export function rate(numerator: number, denominator: number): string {
  return denominator > 0 ? `${Math.round(numerator / denominator * 100)}%` : '—';
}

export function hours(value: number | null): string {
  return value === null ? '—' : value.toLocaleString('zh-CN', { maximumFractionDigits: 1 });
}
