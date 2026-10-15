import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App } from 'antd';
import { createContext, useContext, useEffect, useRef, type ReactNode } from 'react';
import { request, subscribeToUnauthorized } from '../api/http';
import { ApiProblemError } from '../api/problem';
import { clearAuthenticatedSession, sessionQueryKey } from './sessionCache';

export interface SessionActor {
  userId: string;
  displayName: string;
  workEmail: string;
  homeDepartmentId: string;
  supportDepartmentIds?: string[];
  roles: string[];
  permissions: string[];
  superAdmin: boolean;
  mustChangePassword: boolean;
  version: string;
}

interface LoginCommand {
  workEmail: string;
  password: string;
}

interface ChangePasswordCommand {
  currentPassword: string;
  newPassword: string;
}

interface SessionContextValue {
  actor: SessionActor | null;
  loading: boolean;
  unavailable: boolean;
  login: (command: LoginCommand) => Promise<SessionActor>;
  logout: () => Promise<void>;
  changePassword: (command: ChangePasswordCommand) => Promise<void>;
  refresh: () => Promise<void>;
}

const SessionContext = createContext<SessionContextValue | null>(null);

function isUnauthenticated(error: unknown): boolean {
  return error instanceof ApiProblemError && error.problem.status === 401;
}

export function SessionProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const loginRevision = useRef(0);
  const { message } = App.useApp();
  const me = useQuery({
    queryKey: sessionQueryKey,
    queryFn: () => request<SessionActor>('/me'),
    retry: false,
    staleTime: 30_000,
  });
  const loginMutation = useMutation({
    mutationFn: (command: LoginCommand) =>
      request<SessionActor>('/auth/login', { method: 'POST', body: command }),
    onSuccess: async (actor) => {
      const revision = ++loginRevision.current;
      await clearAuthenticatedSession(queryClient, () => revision === loginRevision.current);
      if (revision === loginRevision.current) queryClient.setQueryData(sessionQueryKey, actor);
    },
  });
  const logoutMutation = useMutation({
    onMutate: () => loginRevision.current,
    mutationFn: () => request<void>('/auth/logout', { method: 'POST' }),
    onSuccess: (_data, _variables, revision) =>
      clearAuthenticatedSession(queryClient, () => revision === loginRevision.current),
  });
  const passwordMutation = useMutation({
    onMutate: () => loginRevision.current,
    mutationFn: (command: ChangePasswordCommand) =>
      request<void>('/me/password', { method: 'POST', body: command }),
    onSuccess: async (_data, _variables, revision) => {
      await clearAuthenticatedSession(queryClient, () => revision === loginRevision.current);
      if (revision === loginRevision.current) message.success('密码已更新，请使用新密码重新登录');
    },
  });

  const actor = me.data ?? null;
  const unavailable = Boolean(me.error && !isUnauthenticated(me.error));

  useEffect(
    () =>
      subscribeToUnauthorized(() => {
        void queryClient.cancelQueries();
        queryClient.removeQueries({ predicate: (query) => query.queryKey[0] !== 'session' });
        queryClient.setQueryData(sessionQueryKey, null);
      }),
    [queryClient],
  );

  return (
    <SessionContext.Provider
      value={{
        actor,
        loading: me.isPending,
        unavailable,
        login: (command) => loginMutation.mutateAsync(command),
        logout: () => logoutMutation.mutateAsync(),
        changePassword: (command) => passwordMutation.mutateAsync(command),
        refresh: async () => {
          await me.refetch();
        },
      }}
    >
      {children}
    </SessionContext.Provider>
  );
}

// The hook intentionally shares this module with its provider so the session contract stays atomic.
// eslint-disable-next-line react-refresh/only-export-components
export function useSession(): SessionContextValue {
  const context = useContext(SessionContext);
  if (!context) throw new Error('useSession 必须在 SessionProvider 中使用');
  return context;
}
