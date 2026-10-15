import type { QueryClient } from '@tanstack/react-query';

export const sessionQueryKey = ['session', 'me'] as const;

/** Cancel stale identity reads and remove data that belonged to the previous authenticated session. */
export async function clearAuthenticatedSession(queryClient: QueryClient, isCurrent = () => true): Promise<void> {
  if (!isCurrent()) return;
  await queryClient.cancelQueries();
  if (!isCurrent()) return;
  queryClient.clear();
  queryClient.setQueryData(sessionQueryKey, null);
}
