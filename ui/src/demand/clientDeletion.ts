import { request } from '../api/http';
import type { Client } from './api';

export function deleteClient(client: Pick<Client, 'id' | 'version'>) {
  return request<void>(`/clients/${client.id}`, {
    method: 'DELETE',
    body: { version: String(client.version), reason: '管理员删除客户' },
  });
}
