import { act, cleanup, render } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { AnalyticsNumber } from './AnalyticsNumber';
import { AnalyticsMotionContext } from './analyticsMotion';

afterEach(() => { cleanup(); vi.useRealTimers(); });

it('keeps the accessible value accurate while animating up and down, and cancels on pause', async () => {
  vi.useFakeTimers({ toFake: ['performance', 'requestAnimationFrame', 'cancelAnimationFrame'] });
  const view = render(<AnalyticsMotionContext value={true}><AnalyticsNumber value={100} /></AnalyticsMotionContext>);
  const visible = () => Number(view.container.querySelector('[aria-hidden]')?.textContent);
  expect(view.container.querySelector('[aria-label]')).not.toBeInTheDocument();
  expect(view.container.querySelector('.sr-only')).toHaveTextContent('100');
  await act(() => vi.advanceTimersByTime(400));
  expect(visible()).toBeGreaterThan(0);
  expect(visible()).toBeLessThan(100);
  await act(() => vi.advanceTimersByTime(500));
  expect(visible()).toBe(100);
  view.rerender(<AnalyticsMotionContext value={true}><AnalyticsNumber value={20} /></AnalyticsMotionContext>);
  await act(() => vi.advanceTimersByTime(400));
  expect(visible()).toBeGreaterThan(20);
  expect(visible()).toBeLessThan(100);
  view.rerender(<AnalyticsMotionContext value={false}><AnalyticsNumber value={20} /></AnalyticsMotionContext>);
  expect(visible()).toBe(20);
  expect(vi.getTimerCount()).toBe(0);
});
