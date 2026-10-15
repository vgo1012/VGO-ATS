import { ArrowUpOutlined } from '@ant-design/icons';
import type { MatchResult, ScreeningSummary } from './api';
import { screeningGrade, screeningGradeLabels } from './screeningGrade';

export type ScreeningFilter = ReturnType<typeof screeningGrade> | 'all';

export function ScreeningMetrics({ items, summary, value, onChange }: { items: MatchResult[]; summary?: ScreeningSummary | null | undefined; value: ScreeningFilter; onChange: (value: ScreeningFilter) => void }) {
  const grades = items.map(screeningGrade);
  const counts = summary === undefined ? { total: items.length, strong: grades.filter(grade => grade === 'strong').length, consider: grades.filter(grade => grade === 'consider').length, reject: grades.filter(grade => grade === 'reject').length, missing: grades.filter(grade => grade === 'missing').length } : summary;
  const total = counts?.total;
  const cards = [
    [screeningGradeLabels.strong, 'orange', counts?.strong, 'strong'],
    [screeningGradeLabels.consider, 'blue', counts?.consider, 'consider'],
    [screeningGradeLabels.reject, 'slate', counts?.reject, 'reject'],
    [screeningGradeLabels.missing, 'emerald', counts?.missing, 'missing'],
    [summary === undefined ? '本页简历数' : '简历总数', 'total', total, 'all'],
  ] as const;
  return <section className="screening-overview" aria-label={summary === undefined ? '当前页筛选统计' : '岗位全部候选人统计'}>
    <div className="screening-metrics">{cards.map(([label, tone, count, filter]) => {
      const percentage = total && count !== undefined ? count / total * 100 : 0;
      return <button type="button" aria-label={label} aria-pressed={value === filter} onClick={() => onChange(filter)} className={`screening-metric ${tone}`} key={label}>
        <span className="screening-metric-content"><strong>{count ?? '—'}</strong><span>{label}</span>{filter === 'all' ? <ArrowUpOutlined className="screening-metric-arrow" aria-hidden="true" /> : null}</span>
        <span className="screening-metric-track" aria-hidden="true"><i className="screening-metric-fill" style={{ width: `${percentage}%` }} /></span>
      </button>;
    })}</div>
  </section>;
}
