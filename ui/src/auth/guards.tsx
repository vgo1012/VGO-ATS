import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { NotFoundPage } from '../components/RouteErrorPage';
import { Button, Result, Spin } from 'antd';
import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useSession } from './session';
import { canAccessWorkspace, defaultWorkspacePath, isKnownWorkspacePath } from './workspaceAccess';

export function RequireWorkspaceAccess({ children }: { children: ReactNode }) {
  const { actor } = useSession();
  const { pathname } = useLocation();
  if (!isKnownWorkspacePath(pathname)) return <NotFoundPage />;
  if (canAccessWorkspace(actor, pathname)) return children;
  const destination = defaultWorkspacePath(actor);
  if (pathname === '/overview') return <Navigate replace to={destination} />;
  return <Result status="403" title="无权访问" subTitle="当前账号没有此页面的访问权限。" extra={<Button type="primary" href={destination}>返回我的工作区</Button>} />;
}

export function RequireSession({ children }: { children: ReactNode }) {
  const session = useSession();
  const location = useLocation();
  if (session.loading) {
    return <Spin fullscreen description="正在确认登录状态" />;
  }
  if (session.unavailable) {
    return (
      <main className="centered-state">
        <Alert
          showIcon
          type="error"
          title="暂时无法连接服务"
          description="登录状态校验失败，请确认后端服务可用后重试。"
          action={<Button onClick={() => void session.refresh()}>重试</Button>}
        />
      </main>
    );
  }
  if (!session.actor) {
    const returnTo = `${location.pathname}${location.search}${location.hash}`;
    return <Navigate replace to="/login" state={location.pathname === '/change-password' ? null : { from: returnTo }} />;
  }
  if (session.actor.mustChangePassword && location.pathname !== '/change-password') {
    return <Navigate replace to="/change-password" />;
  }
  if (!session.actor.mustChangePassword && location.pathname === '/change-password') {
    return <Navigate replace to={defaultWorkspacePath(session.actor)} />;
  }
  return children;
}

export function RequireSuperAdmin({ children }: { children: ReactNode }) {
  const { actor } = useSession();
  if (!actor?.superAdmin) {
    return (
      <Result
        status="403"
        title="无权访问"
        subTitle="此页面仅向超级管理员开放。"
      />
    );
  }
  return children;
}
