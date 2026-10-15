import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, expect, it } from 'vitest';
import { AnnualCalendar } from './AnnualCalendar';

afterEach(cleanup);
it('aligns a leap-year February to weekdays and exposes every real date', () => {
  const { container } = render(<AnnualCalendar year={2028} days={[{ date: '2028-02-29', dayType: 'HOLIDAY', description: '公司假期' }]} editable busy={false} changedDates={[]} onMark={() => undefined} />);
  expect(screen.getByRole('group', { name: '2028年全年日历' })).toHaveAttribute('aria-busy', 'false');
  expect(container.querySelectorAll('.annual-calendar-month')).toHaveLength(12);
  expect(container.querySelectorAll('.annual-calendar-day')).toHaveLength(366);
  const february = container.querySelectorAll('.annual-calendar-dates')[1]!;
  expect(february.children[0]).toHaveClass('annual-calendar-blank');
  expect(february.children[29]).toHaveAccessibleName('2028-02-29 节假日，公司假期');
  expect(february.children[29]).toBeEnabled();
  expect(february.children[28]).toBeDisabled();
});
