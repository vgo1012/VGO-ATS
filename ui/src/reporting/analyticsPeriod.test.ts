import dayjs from 'dayjs';
import { expect, it } from 'vitest';
import { dateRangeParams } from '../components/dateRange';
import { periodRange, rate, readPeriod, trendBucketLabel, trendBucketRangeLabel, trendBuckets } from './analyticsPeriod';

it('defaults to month and preserves explicitly selected periods', () => {
  expect(readPeriod(null)).toBe('month');
  expect(readPeriod('invalid')).toBe('month');
  for (const period of ['all', 'week', 'month', 'quarter', 'year'] as const) expect(readPeriod(period)).toBe(period);
});

it('groups cumulative historical cohorts by month with year labels', () => {
  const range: [dayjs.Dayjs, dayjs.Dayjs] = [dayjs('1970-01-01'), dayjs('2026-10-01')];
  const rows = trendBuckets([
    { date: '2025-09-30', applications: 2, offers: 1, onboarded: 0 },
    { date: '2026-09-01', applications: 3, offers: 2, onboarded: 1 },
    { date: '2026-09-30', applications: 4, offers: 1, onboarded: 1 },
    { date: '2026-10-02', applications: 100, offers: 100, onboarded: 100 },
  ], 'all', range);
  expect(rows).toHaveLength(14);
  expect(rows[0]).toEqual({ date: '2025-09-01', applications: 2, offers: 1, onboarded: 0 });
  expect(rows.at(-2)).toEqual({ date: '2026-09-01', applications: 7, offers: 3, onboarded: 2 });
  expect(rows.at(-1)?.applications).toBe(0);
  expect(trendBucketLabel('2025-09-01', 'all')).toBe('2025-09');
  expect(trendBucketRangeLabel('2026-10-01', 'all', range)).toBe('2026-10-01');
  expect(trendBuckets([], 'all', range)).toEqual([]);
});

it('uses Monday weeks across a year boundary and Shanghai exclusive midnight', () => {
  const range = periodRange('week', dayjs('2027-01-01'));
  expect(range.map(date => date.format('YYYY-MM-DD'))).toEqual(['2026-12-28', '2027-01-03']);
  expect(dateRangeParams(range)).toEqual({ from: '2026-12-27T16:00:00.000Z', to: '2027-01-03T16:00:00.000Z' });
});

it('includes leap days and complete natural quarters and years', () => {
  expect(periodRange('month', dayjs('2028-02-12'))[1].format('YYYY-MM-DD')).toBe('2028-02-29');
  expect(periodRange('quarter', dayjs('2026-05-12')).map(date => date.format('YYYY-MM-DD'))).toEqual(['2026-04-01', '2026-06-30']);
  expect(periodRange('year', dayjs('2026-05-12')).map(date => date.format('YYYY-MM-DD'))).toEqual(['2026-01-01', '2026-12-31']);
});

it('fills empty buckets and sums disjoint cohorts without averaging rates', () => {
  const rows = trendBuckets([
    { date: '2026-01-02', applications: 10, offers: 1, onboarded: 1 },
    { date: '2026-01-03', applications: 2, offers: 2, onboarded: 0 },
  ], 'quarter', periodRange('quarter', dayjs('2026-01-01')));
  expect(rows).toEqual([
    { date: '2026-01-01', applications: 12, offers: 3, onboarded: 1 },
    { date: '2026-02-01', applications: 0, offers: 0, onboarded: 0 },
    { date: '2026-03-01', applications: 0, offers: 0, onboarded: 0 },
  ]);
  expect(rate(3, 12)).toBe('25%');
  expect(rate(10, 35)).toBe('29%');
  expect(rate(1, 3)).toBe('33%');
  expect(rate(1, 8)).toBe('13%');
  expect(rate(1, 1)).toBe('100%');
  expect(rate(0, 1)).toBe('0%');
  expect(rate(0, 0)).toBe('—');
});

const cohort = (date: string, applications = 1) => ({ date, applications, offers: 1, onboarded: 1 });

it('keeps seven daily buckets for a week crossing a month and year', () => {
  const range = periodRange('week', dayjs('2027-01-01'));
  const rows = trendBuckets([cohort('2026-12-27'), cohort('2026-12-28'), cohort('2027-01-03'), cohort('2027-01-04')], 'week', range);
  expect(rows.map(row => row.date)).toEqual(['2026-12-28', '2026-12-29', '2026-12-30', '2026-12-31', '2027-01-01', '2027-01-02', '2027-01-03']);
  expect(rows.map(row => row.applications)).toEqual([1, 0, 0, 0, 0, 0, 1]);
  expect(trendBucketLabel(rows[4]!.date, 'week')).toBe('1/1');
});

it('clips Monday weeks at both month boundaries and excludes neighboring dates', () => {
  const range = periodRange('month', dayjs('2026-09-15'));
  const rows = trendBuckets([
    cohort('2026-08-31', 100), cohort('2026-09-01', 2), cohort('2026-09-06', 3),
    cohort('2026-09-07', 7), cohort('2026-09-30', 9), cohort('2026-10-01', 100),
  ], 'month', range);
  expect(rows.map(row => row.date)).toEqual(['2026-09-01', '2026-09-07', '2026-09-14', '2026-09-21', '2026-09-28']);
  expect(rows.map(row => row.applications)).toEqual([5, 7, 0, 0, 9]);
  expect(rows.map(row => trendBucketLabel(row.date, 'month'))).toEqual(['第1周', '第2周', '第3周', '第4周', '第5周']);
  expect(trendBucketRangeLabel(rows[0]!.date, 'month', range)).toBe('2026-09-01 ～ 2026-09-06');
  expect(trendBucketRangeLabel(rows[4]!.date, 'month', range)).toBe('2026-09-28 ～ 2026-09-30');
});

it('includes leap day and supports a six-week month beginning on Sunday', () => {
  const leapRange = periodRange('month', dayjs('2028-02-01'));
  const leap = trendBuckets([cohort('2028-02-29', 29), cohort('2028-03-01', 100)], 'month', leapRange);
  expect(leap.at(-1)).toEqual(cohort('2028-02-28', 29));
  expect(trendBucketRangeLabel('2028-02-28', 'month', leapRange)).toBe('2028-02-28 ～ 2028-02-29');
  const sixWeeks = trendBuckets([], 'month', periodRange('month', dayjs('2026-03-01')));
  expect(sixWeeks.map(row => row.date)).toEqual(['2026-03-01', '2026-03-02', '2026-03-09', '2026-03-16', '2026-03-23', '2026-03-30']);
  expect(trendBucketLabel('2026-03-30', 'month')).toBe('第6周');
});

it('aggregates four annual quarters with all three metrics and rejects out-of-range rows', () => {
  const range = periodRange('year', dayjs('2026-08-15'));
  const rows = trendBuckets([
    cohort('2025-12-31', 100), cohort('2026-01-01', 2), cohort('2026-03-31', 3),
    cohort('2026-04-01', 4), cohort('2026-09-30', 5), cohort('2026-12-31', 6), cohort('2027-01-01', 100),
  ], 'year', range);
  expect(rows).toEqual([
    { date: '2026-01-01', applications: 5, offers: 2, onboarded: 2 },
    cohort('2026-04-01', 4), cohort('2026-07-01', 5), cohort('2026-10-01', 6),
  ]);
  expect(rows.map(row => trendBucketLabel(row.date, 'year'))).toEqual(['第1季度', '第2季度', '第3季度', '第4季度']);
  expect(trendBucketRangeLabel('2026-10-01', 'year', range)).toBe('2026-10-01 ～ 2026-12-31');
  expect(trendBucketLabel('2026-10-01', 'quarter')).toBe('10月');
});
