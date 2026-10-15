import {
  ApartmentOutlined,
  CalendarOutlined,
  TeamOutlined,
  FileTextOutlined,
  FileWordOutlined,
  SafetyCertificateOutlined,
  MailOutlined,
  SettingOutlined,
  SlidersOutlined,
} from '@ant-design/icons';
import type { ReactNode } from 'react';
import { Select } from 'antd';
import { NavLink, useLocation, useNavigate } from 'react-router-dom';
import { useSession } from '../auth/session';
import { canAccessWorkspace } from '../auth/workspaceAccess';
import '../styles/settings-layout.css';

const personalItems = [
  { to: '/monthly-reports', label: '月度汇报', icon: <FileWordOutlined /> },
  { to: '/recruitment/setup', label: 'JD 库', icon: <FileTextOutlined /> },
  { to: '/settings/preferences', label: '个人筛选方案', icon: <SlidersOutlined /> },
];

const adminItems = [
  { to: '/admin/mail-resume-reviews', label: '邮件简历校验', icon: <MailOutlined /> },
  { to: '/admin/clients', label: '客户管理', icon: <TeamOutlined /> },
  { to: '/admin/source-channels', label: '来源渠道', icon: <FileTextOutlined /> },
  { to: '/admin/work-calendars', label: '工作日历', icon: <CalendarOutlined /> },
  { to: '/admin/task-center', label: '任务运行中心', icon: <SettingOutlined /> },
  { to: '/admin/identity', label: '身份与组织', icon: <ApartmentOutlined /> },
  { to: '/admin/scoring', label: '评分方案与岗位关联', icon: <SafetyCertificateOutlined /> },
];

export function SettingsLayout({ children }: { children: ReactNode }) {
  const { actor } = useSession();
  const location = useLocation();
  const navigate = useNavigate();
  const personal = personalItems.filter(item => canAccessWorkspace(actor, item.to));
  const admin = adminItems.filter(item => canAccessWorkspace(actor, item.to));
  const items = [...personal, ...admin];

  return (
    <div className="settings-layout">
      <aside className="settings-sidebar">
        <header><SettingOutlined /><div><b>工作区设置</b><small>招聘与团队设置</small></div></header>
        <nav aria-label="设置导航">
          <div className="settings-mobile-navigation">
            <span>设置</span>
            <Select
              aria-label="切换设置页面"
              value={location.pathname}
              onChange={path => void navigate(path)}
              options={[
                ...items.map(item => ({ value: item.to, label: item.label })),
                ...(location.pathname === '/admin/reports' ? [{ value: '/admin/reports', label: '招聘报表', disabled: true }] : []),
              ]}
            />
          </div>
          <div className="settings-desktop-navigation">
          <span>招聘工作</span>
          {personal.map((item) => <NavLink key={item.to} to={item.to}>{item.icon}{item.label}</NavLink>)}
          {admin.length ? <><span>团队管理</span>{admin.map((item) => <NavLink key={item.to} to={item.to}>{item.icon}{item.label}</NavLink>)}</> : null}
          </div>
        </nav>
        <p>个人方案仅供自己使用。团队和岗位设置由管理员维护。</p>
      </aside>
      <section className="settings-content">{children}</section>
    </div>
  );
}
