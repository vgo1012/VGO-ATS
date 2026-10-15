import { request } from '../api/http';
import type { CursorPage } from '../demand/api';

export interface DirectoryUser {
  id: string;
  displayName: string;
  homeDepartmentId: string;
  status: 'ACTIVE' | 'DISABLED';
}

export const directoryApi = {
  users: (query: string, cursor?: string) => {
    const params = new URLSearchParams({ limit: '20' });
    if (query.trim()) params.set('query', query.trim());
    if (cursor) params.set('cursor', cursor);
    return request<CursorPage<DirectoryUser>>(`/directory/users?${params}`);
  },
  resolve: (ids: string[]) => request<DirectoryUser[]>(`/directory/users/resolve?${new URLSearchParams({ ids: ids.join(',') })}`),
};
