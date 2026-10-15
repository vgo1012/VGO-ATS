import { DatePicker, Space } from 'antd';
import { businessDay, type DateRange } from './dateRange';

export function DateRangeFilter({ value, onChange, label = '统计日期' }: { value: DateRange; onChange: (range: DateRange) => void; label?: string }) {
  const today = businessDay();
  return <Space className="date-range-filter" wrap><span>{label}</span><DatePicker.RangePicker
    aria-label={label} placeholder={['开始日期', '结束日期']} value={value} allowClear={false}
    disabledDate={(date, info) => Boolean(info.from && Math.abs(date.diff(info.from, 'day')) > 365)}
    onChange={range => { if (range?.[0] && range[1]) onChange([range[0], range[1]]); }}
    presets={[
      { label: '最近 7 天', value: [today.subtract(6, 'day'), today] },
      { label: '本月', value: [today.startOf('month'), today] },
      { label: '上个月', value: [today.subtract(1, 'month').startOf('month'), today.subtract(1, 'month').endOf('month')] },
      { label: '今年', value: [today.startOf('year'), today] },
    ]}
  /></Space>;
}
