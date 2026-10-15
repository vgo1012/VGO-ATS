import { cleanup, fireEvent, render } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { LandingExperience } from './LandingExperience';

afterEach(cleanup);

describe('LandingExperience', () => {
  it('introduces VGO recruiting and opens the workspace without a calendar', () => {
    const enter = vi.fn();
    const view = render(<LandingExperience onEnterWorkspace={enter} />);

    expect(view.getByRole('img', { name: 'VGO' })).toBeInTheDocument();
    expect(view.getByRole('heading', { level: 1 })).toHaveTextContent('让人才与机会在这里相遇');
    expect(view.queryByText(/VGO ATS|AI 辅助招聘/)).not.toBeInTheDocument();
    expect(view.getByRole('list', { name: '招聘能力' })).toHaveTextContent('简历解析智能匹配招聘协同');
    expect(view.queryByRole('region', { name: '今日日历' })).not.toBeInTheDocument();
    expect(view.container.querySelector('time')).not.toBeInTheDocument();
    expect(view.queryAllByRole('button')).toHaveLength(1);

    fireEvent.click(view.getByRole('button', { name: '登录工作台' }));
    expect(enter).toHaveBeenCalledOnce();
  });

  it('keeps the introduction and login usable when the decorative image fails', () => {
    const enter = vi.fn();
    const view = render(<LandingExperience onEnterWorkspace={enter} />);
    const art = view.container.querySelector('.landing-art')!;
    expect(art).toHaveAttribute('alt', '');
    fireEvent.error(art);

    expect(view.getByRole('heading', { level: 1 })).toBeVisible();
    fireEvent.click(view.getByRole('button', { name: '登录工作台' }));
    expect(enter).toHaveBeenCalledOnce();
  });
});
