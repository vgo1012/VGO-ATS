import {
  BarChartOutlined,
  CalendarOutlined,
  DatabaseOutlined,
  DownOutlined,
  FileSearchOutlined,
  FolderOpenOutlined,
  LogoutOutlined,
  LoadingOutlined,
  SettingOutlined,
  TeamOutlined,
  UserOutlined,
  UsergroupAddOutlined,
} from '@ant-design/icons';
import { App, Dropdown } from 'antd';
import type { MenuProps } from 'antd';
import { useEffect, useLayoutEffect, useMemo, useRef, useState, type KeyboardEvent } from 'react';
import { Link, Outlet, useLocation, useNavigate, useNavigation } from 'react-router-dom';
import { useSession } from '../auth/session';
import { RequireWorkspaceAccess } from '../auth/guards';
import { canAccessWorkspace } from '../auth/workspaceAccess';
import { ROLES, roleLabel } from '../auth/accessPolicy';
import { ApiProblemError } from '../api/problem';
import { BatchParseNotifications } from '../talent/BatchParseNotifications';
import '../styles/navigation-expansion.css';

const navigation = [
  { key: '/recruitment-analysis', label: '招聘洞察', icon: <BarChartOutlined aria-hidden="true" /> },
  { key: '/talents', label: '我的候选人', icon: <DatabaseOutlined aria-hidden="true" /> },
  { key: '/matching', label: 'AI 评估', icon: <FileSearchOutlined aria-hidden="true" /> },
  { key: '/recruitment', label: '招募管道', icon: <TeamOutlined aria-hidden="true" /> },
  { key: '/schedule', label: '面试日历', icon: <CalendarOutlined aria-hidden="true" /> },
  { key: '/shared-library', label: '人才公海', icon: <FolderOpenOutlined aria-hidden="true" /> },
  { key: '/onboarded', label: '录用交付', icon: <UsergroupAddOutlined aria-hidden="true" /> },
];

function WorkspaceNavigation({ label, items, pathname, pendingPath, onNavigate }: {
  label: string;
  items: typeof navigation;
  pathname: string;
  pendingPath: string | undefined;
  onNavigate: (path: string) => void;
}) {
  const navRef = useRef<HTMLElement>(null);
  const indicatorRef = useRef<HTMLSpanElement>(null);

  useLayoutEffect(() => {
    const nav = navRef.current;
    const indicator = indicatorRef.current;
    if (!nav || !indicator) return;
    const positionIndicator = () => {
      const current = nav.querySelector<HTMLButtonElement>('[aria-current="page"]');
      if (!current || !nav.clientWidth) {
        delete nav.dataset.indicatorReady;
        return;
      }
      indicator.style.width = `${current.offsetWidth}px`;
      indicator.style.height = `${current.offsetHeight}px`;
      indicator.style.transform = `translate3d(${current.offsetLeft}px, ${current.offsetTop}px, 0)`;
      nav.dataset.indicatorReady = 'true';
      const container = nav.parentElement;
      if (nav.scrollWidth > nav.clientWidth || (container && container.scrollWidth > container.clientWidth)) current.scrollIntoView({ block: 'nearest', inline: 'nearest' });
    };
    positionIndicator();
    const observer = new ResizeObserver(positionIndicator);
    observer.observe(nav);
    nav.querySelectorAll('button').forEach(button => observer.observe(button));
    return () => observer.disconnect();
  }, [pathname, items]);

  return <nav ref={navRef} aria-label={label} className="workspace-nav">
    <span ref={indicatorRef} className="workspace-nav-indicator" aria-hidden="true" />
    {items.map(item => <button key={item.key} type="button" aria-current={pathname === item.key ? 'page' : undefined} className={pathname === item.key ? 'active' : ''} onClick={() => onNavigate(item.key)}>
      {pendingPath === item.key ? <LoadingOutlined aria-hidden="true" /> : item.icon}<span>{item.label}</span>
    </button>)}
  </nav>;
}

export function AppShell() {
  const location = useLocation();
  const navigate = useNavigate();
  const routeNavigation = useNavigation();
  const { actor, logout } = useSession();
  const { message } = App.useApp();
  const actorRoleLabel = roleLabel(actor);
  const actorName = actor?.displayName ?? '当前用户';
  const [accountOpen, setAccountOpen] = useState(false);
  const profileTriggerRef = useRef<HTMLButtonElement>(null);
  const mainRef = useRef<HTMLElement>(null);
  const matchingLocationRef = useRef<{ search: string; state: unknown } | undefined>(undefined);
  const visibleNavigation = useMemo(
    () => navigation.filter((item) => canAccessWorkspace(actor, item.key)).map(item => item.key === '/talents' ? {...item, label: actor?.superAdmin || actor?.roles.includes(ROLES.hrDirector) ? '候选人库' : actor?.roles.includes(ROLES.recruitmentManager) ? '团队候选人' : '我的候选人'} : item),
    [actor],
  );

  useEffect(() => {
    mainRef.current?.focus({ preventScroll: true });
    window.scrollTo({ top: 0, behavior: 'auto' });
  }, [location.pathname]);

  useEffect(() => {
    if (location.pathname === '/matching') {
      matchingLocationRef.current = { search: location.search, state: location.state };
    }
  }, [location.pathname, location.search, location.state]);

  const settingsPath = actor?.superAdmin ? '/admin/identity' : '/settings/preferences';
  const accountItems: MenuProps['items'] = [
    { key: 'identity', disabled: true, className: 'profile-menu-identity', label: <div className="profile-menu-summary">
      <div className="profile-menu-name"><strong>{actorName}</strong>{actorName !== actorRoleLabel ? <span className="profile-menu-role">{actorRoleLabel}</span> : null}</div>
      <span className="profile-menu-email">{actor?.workEmail ?? '当前账号'}</span>
    </div> },
    { type: 'divider' },
    ...(canAccessWorkspace(actor, settingsPath) ? [{ key: settingsPath, icon: <SettingOutlined aria-hidden="true" />, label: '工作区设置' }] : []),
    { type: 'divider' },
    { key: 'logout', icon: <LogoutOutlined aria-hidden="true" />, label: '退出登录', danger: true },
  ];

  const onAccountClick: MenuProps['onClick'] = ({ key }) => {
    if (key === 'logout') void logout()
      .then(() => navigate('/login', { replace: true }))
      .catch(error => message.error(error instanceof ApiProblemError ? error.problem.detail : '退出失败，请检查网络后重试。'));
    else if (key.startsWith('/')) void navigate(key);
  };

  const onAccountKeyDown = (event: KeyboardEvent<HTMLElement>) => {
    if (event.key === 'Escape' && accountOpen) {
      event.preventDefault();
      event.stopPropagation();
      setAccountOpen(false);
      profileTriggerRef.current?.focus();
    }
  };

  const renderNavigation = (label: string) => (
    <WorkspaceNavigation label={label} items={visibleNavigation} pathname={location.pathname} pendingPath={routeNavigation.location?.pathname} onNavigate={path => {
      const matchingLocation = path === '/matching' ? matchingLocationRef.current : undefined;
      void (matchingLocation
        ? navigate({ pathname: path, search: matchingLocation.search }, { state: matchingLocation.state })
        : navigate(path));
    }} />
  );

  return (
    <div className="app-shell">
      <header className="app-header">
        <div className="workspace-brand">
          <Link className="logo-link" to="/" aria-label="返回品牌首页" title="返回品牌首页">
            <img className="brand-logo" src="/assets/vgo-logo.svg" alt="VGO" />
          </Link>
        </div>

        <div className="desktop-nav">{renderNavigation('主导航')}</div>

        <div className="profile-area">
          <Dropdown trigger={['click']} placement="bottomRight" open={accountOpen} onOpenChange={setAccountOpen} classNames={{ root: 'profile-dropdown' }} menu={{ items: accountItems, onClick: onAccountClick, onKeyDown: onAccountKeyDown }}>
            <button ref={profileTriggerRef} className="profile-trigger" type="button" onKeyDown={onAccountKeyDown} aria-haspopup="menu" aria-expanded={accountOpen} aria-label={`打开${actorName}的个人菜单`}>
              <span className="profile-avatar" aria-hidden="true"><UserOutlined /></span>
              <span className="profile-copy">
                <b>{actorName}</b>
              </span>
              <DownOutlined aria-hidden="true" />
            </button>
          </Dropdown>
        </div>
      </header>

      <main ref={mainRef} id="main-content" className="app-main" tabIndex={-1} aria-busy={routeNavigation.state !== 'idle'}>
        <span className="sr-only" role="status">{routeNavigation.state !== 'idle' ? '正在加载工作区' : ''}</span>
        <RequireWorkspaceAccess><Outlet /></RequireWorkspaceAccess>
      </main>

      <BatchParseNotifications key={actor?.userId} />

      <div className="mobile-bottom-nav">{renderNavigation('移动端主导航')}</div>
    </div>
  );
}

