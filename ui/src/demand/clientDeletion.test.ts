import { expect, it, vi } from 'vitest';
import { request } from '../api/http';
import { deleteClient } from './clientDeletion';

vi.mock('../api/http', () => ({ request: vi.fn() }));

it('删除携带版本和审计原因，不调用停用接口', async () => {
  vi.mocked(request).mockResolvedValue(undefined);
  await deleteClient({ id: 'client-one', version: 3 });
  expect(request).toHaveBeenCalledWith('/clients/client-one', {
    method: 'DELETE', body: { version: '3', reason: '管理员删除客户' },
  });
});
