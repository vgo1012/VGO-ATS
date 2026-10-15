import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { MatchResult } from './api';
import { ScreeningMetrics } from './ScreeningMetrics';

afterEach(cleanup);

const result = (id: string, score: number, status: 'KNOWN' | 'UNKNOWN' = 'KNOWN') => ({
  resultId: id,
  referenceScore: score,
  mustTier: 'PASS',
  details: { dimensions: ['CORE_SKILLS', 'ROLE_EXPERIENCE', 'RELATED_YEARS', 'RESPONSIBILITY_MATCH', 'INDUSTRY_AND_PROJECTS'].map(code => ({ code, enabled: true, status })) },
}) as MatchResult;

describe('ScreeningMetrics', () => {
  it('岗位统计使用全部候选人的汇总，不随当前页变化', () => {
    const props = { summary: { total: 41, strong: 12, consider: 24, reject: 4, missing: 1 }, value: 'all' as const, onChange: vi.fn() };
    const view = render(<ScreeningMetrics items={[result('first', 90)]} {...props} />);
    expect(within(screen.getByRole('button', { name: '简历总数' })).getByText('41')).toBeVisible();
    expect(within(screen.getByRole('button', { name: '强推荐' })).getByText('12')).toBeVisible();
    view.rerender(<ScreeningMetrics items={[result('last', 0, 'UNKNOWN')]} {...props} />);
    expect(within(screen.getByRole('button', { name: '简历总数' })).getByText('41')).toBeVisible();
    expect(within(screen.getByRole('button', { name: '信息缺失' })).getByText('1')).toBeVisible();
  });

  it('汇总未就绪不将本页数量冒充岗位总数', () => {
    render(<ScreeningMetrics items={[result('first', 90)]} summary={null} value="all" onChange={vi.fn()} />);
    expect(within(screen.getByRole('button', { name: '简历总数' })).getByText('—')).toBeVisible();
  });

  it('renders the five-part metric strip and keeps each segment as a filter', () => {
    const onChange = vi.fn();
    render(<ScreeningMetrics
      items={[result('strong', 90), result('consider', 75), result('reject', 60), result('missing', 90, 'UNKNOWN')]}
      value="all"
      onChange={onChange}
    />);

    const strong = screen.getByRole('button', { name: '强推荐' });
    const total = screen.getByRole('button', { name: '本页简历数' });
    expect(within(total).getByText('4')).toBeVisible();
    expect(screen.queryByRole('button', { name: '简历总数' })).not.toBeInTheDocument();
    expect(within(strong).getByText('1')).toBeInTheDocument();
    expect(strong.querySelector('.screening-metric-fill')).toHaveStyle({ width: '25%' });
    expect(total.querySelector('.screening-metric-fill')).toHaveStyle({ width: '100%' });
    expect(total.querySelector('.screening-metric-arrow')).toBeInTheDocument();
    expect(total).toHaveAttribute('aria-pressed', 'true');

    fireEvent.click(strong);
    expect(onChange).toHaveBeenCalledWith('strong');
  });
});
