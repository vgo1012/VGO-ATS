import { QueryClient } from '@tanstack/react-query';
import { expect, it, vi } from 'vitest';
import { clearAuthenticatedSession, sessionQueryKey } from './sessionCache';

it('preserves the newer login when old cleanup finishes cancelling queries late', async () => {
  const queryClient = new QueryClient();
  let release!: () => void;
  const cancelling = new Promise<void>(resolve => { release = resolve; });
  const cancel = vi.spyOn(queryClient, 'cancelQueries').mockReturnValueOnce(cancelling);
  let current = true;
  const cleanup = clearAuthenticatedSession(queryClient, () => current);
  current = false;
  queryClient.setQueryData(sessionQueryKey, { userId: 'new-user' });
  queryClient.setQueryData(['candidates'], [{ id: 'new-users-candidate' }]);
  release();
  await cleanup;
  expect(cancel).toHaveBeenCalledOnce();
  expect(queryClient.getQueryData(sessionQueryKey)).toEqual({ userId: 'new-user' });
  expect(queryClient.getQueryData(['candidates'])).toEqual([{ id: 'new-users-candidate' }]);
});

it('drops stale forced-password and business data before the next login', async () => {
  const queryClient = new QueryClient();
  queryClient.setQueryData(sessionQueryKey, { userId: 'old-user', mustChangePassword: true });
  queryClient.setQueryData(['candidates'], [{ id: 'another-users-candidate' }]);

  await clearAuthenticatedSession(queryClient);

  expect(queryClient.getQueryData(sessionQueryKey)).toBeNull();
  expect(queryClient.getQueryData(['candidates'])).toBeUndefined();
  expect(queryClient.getQueryCache().getAll()).toHaveLength(1);
});
