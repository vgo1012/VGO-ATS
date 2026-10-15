import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { ConfigProvider } from 'antd';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { MatchSummary } from './MatchSummary';

beforeEach(() => vi.useFakeTimers());
afterEach(() => { cleanup(); vi.useRealTimers(); });
const settle = () => act(async () => { await vi.advanceTimersByTimeAsync(300); });
function mount() {
  render(<ConfigProvider theme={{ token: { motion: false } }}><MatchSummary text="完整岗位匹配说明" /><button>其他操作</button></ConfigProvider>);
  return screen.getByRole('button', { name: '查看完整匹配说明' });
}

it('closes a hover preview when the pointer leaves', async () => {
  const trigger = mount();
  fireEvent.mouseEnter(trigger);
  await settle();
  expect(trigger).toHaveAttribute('aria-expanded', 'true');
  fireEvent.mouseLeave(trigger);
  await settle();
  expect(trigger).toHaveAttribute('aria-expanded', 'false');
});

it('keeps an explicitly opened explanation visible after the pointer leaves', async () => {
  const trigger = mount();
  fireEvent.click(trigger);
  await settle();
  fireEvent.mouseLeave(trigger);
  await settle();
  expect(trigger).toHaveAttribute('aria-expanded', 'true');
});

it('pins an existing hover preview when its button is activated', async () => {
  const trigger = mount();
  fireEvent.mouseEnter(trigger);
  await settle();
  fireEvent.click(trigger);
  fireEvent.mouseLeave(trigger);
  await settle();
  expect(trigger).toHaveAttribute('aria-expanded', 'true');
});

it('keeps a focused explanation visible when the pointer leaves the popup', async () => {
  const trigger = mount();
  fireEvent.mouseEnter(trigger);
  await settle();
  const panel = screen.getByRole('region', { name: '完整匹配说明' });
  act(() => panel.querySelector('p')!.focus());
  fireEvent.mouseLeave(panel.closest('.ant-popover')!);
  await settle();
  expect(trigger).toHaveAttribute('aria-expanded', 'true');
});

it('keeps reading when focus enters during a pending hover dismissal', async () => {
  const trigger = mount();
  fireEvent.mouseEnter(trigger);
  await settle();
  const panel = screen.getByRole('region', { name: '完整匹配说明' });
  fireEvent.mouseLeave(trigger);
  act(() => panel.querySelector('p')!.focus());
  await settle();
  expect(trigger).toHaveAttribute('aria-expanded', 'true');
});

it('dismisses focused reading when focus moves outside without taking focus back', async () => {
  const trigger = mount();
  fireEvent.click(trigger);
  await settle();
  const panel = screen.getByRole('region', { name: '完整匹配说明' });
  act(() => panel.querySelector('p')!.focus());
  const outside = screen.getByRole('button', { name: '其他操作' });
  fireEvent.mouseDown(outside);
  act(() => outside.focus());
  await settle();
  expect(trigger).toHaveAttribute('aria-expanded', 'false');
  expect(outside).toHaveFocus();
});

it.each(['escape', 'collapse', 'outside', 'toggle'])('dismisses explicit reading via %s', async method => {
  const trigger = mount();
  fireEvent.click(trigger);
  await settle();
  const panel = screen.getByRole('region', { name: '完整匹配说明' });
  if (method === 'escape') {
    act(() => panel.querySelector('p')!.focus());
    fireEvent.keyDown(panel, { key: 'Escape' });
  } else if (method === 'collapse') fireEvent.click(screen.getByRole('button', { name: '收起' }));
  else if (method === 'outside') fireEvent.mouseDown(screen.getByRole('button', { name: '其他操作' }));
  else fireEvent.click(trigger);
  await settle();
  expect(trigger).toHaveAttribute('aria-expanded', 'false');
  if (method === 'escape' || method === 'collapse') expect(trigger).toHaveFocus();
});
