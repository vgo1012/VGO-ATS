import { cleanup, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { SettingsLayout } from './SettingsLayout';

const session = vi.hoisted(() => ({ actor: { superAdmin: true, permissions: ['demand.read', 'matching.run'] } }));
vi.mock('../auth/session', () => ({ useSession: () => session }));
beforeEach(() => { session.actor.superAdmin = true; session.actor.permissions = ['demand.read', 'matching.run']; });
afterEach(cleanup);

it('keeps monthly reports in work settings and mail review in team settings', () => {
  render(<MemoryRouter initialEntries={['/admin/clients']}><SettingsLayout><h1>客户目录</h1></SettingsLayout></MemoryRouter>);
  const navigation = within(screen.getByRole('navigation', { name: '设置导航' }));
  const links = navigation.getAllByRole('link');
  expect(links.map(link => link.textContent)).toEqual(['月度汇报', 'JD 库', '个人筛选方案', '邮件简历校验', '客户管理', '来源渠道', '工作日历', '任务运行中心', '身份与组织', '评分方案与岗位关联']);
  expect(navigation.getByRole('link', { name: /月度汇报$/ })).toHaveAttribute('href', '/monthly-reports');
  expect(navigation.getByRole('link', { name: /客户管理/ })).toHaveAttribute('href', '/admin/clients');
  expect(navigation.getByRole('link', { name: /客户管理/ })).toHaveAttribute('aria-current', 'page');
  expect(navigation.queryByRole('link', { name: '招聘报表' })).not.toBeInTheDocument();
});

it('does not expose team customer management to ordinary recruiters', () => {
  session.actor.superAdmin = false;
  render(<MemoryRouter initialEntries={['/recruitment/setup']}><SettingsLayout><h1>JD 库内容</h1></SettingsLayout></MemoryRouter>);
  expect(screen.queryByText('客户管理')).not.toBeInTheDocument();
  expect(screen.queryByText('工作日历')).not.toBeInTheDocument();
  expect(screen.queryByText('任务运行中心')).not.toBeInTheDocument();
  expect(screen.queryByText('云存储')).not.toBeInTheDocument();
  expect(screen.queryByText('团队管理')).not.toBeInTheDocument();
  expect(screen.getByRole('link', { name: /JD 库/ })).toBeInTheDocument();
});

it('labels the preserved report route without adding it to desktop navigation', () => {
  render(<MemoryRouter initialEntries={['/admin/reports']}><SettingsLayout><h1>招聘报表</h1></SettingsLayout></MemoryRouter>);
  const navigation = within(screen.getByRole('navigation', { name: '设置导航' }));
  expect(navigation.getByText('招聘报表')).toBeInTheDocument();
  expect(navigation.queryByRole('link', { name: '招聘报表' })).not.toBeInTheDocument();
});

it('hides mail resume review from ordinary talent managers', () => {
  session.actor.superAdmin = false;
  session.actor.permissions = ['talent.manage'];
  render(<MemoryRouter initialEntries={['/admin/mail-resume-reviews']}><SettingsLayout><h1>邮件简历校验内容</h1></SettingsLayout></MemoryRouter>);
  expect(screen.queryByRole('link', { name: /邮件简历校验/ })).not.toBeInTheDocument();
  expect(screen.queryByText('客户管理')).not.toBeInTheDocument();
});
