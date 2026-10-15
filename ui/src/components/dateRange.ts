import dayjs, { type Dayjs } from 'dayjs';
import utc from 'dayjs/plugin/utc';
import timezone from 'dayjs/plugin/timezone';

dayjs.extend(utc);
dayjs.extend(timezone);
export type DateRange = [Dayjs, Dayjs];
export const businessDay = () => dayjs().tz('Asia/Shanghai').startOf('day');
export function businessWeek(date = businessDay()): DateRange {
  const start = date.startOf('day').subtract((date.day() + 6) % 7, 'day');
  return [start, start.add(6, 'day')];
}
export function dateRangeParams(range: DateRange) {
  return {
    from: dayjs.tz(range[0].format('YYYY-MM-DD'), 'Asia/Shanghai').toISOString(),
    to: dayjs.tz(range[1].add(1, 'day').format('YYYY-MM-DD'), 'Asia/Shanghai').toISOString(),
  };
}
