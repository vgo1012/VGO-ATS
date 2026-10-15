import { afterEach, expect, it, vi } from 'vitest';
import { request } from '../api/http';
import { talentApi } from './api';

vi.mock('../api/http', () => ({ request: vi.fn(), downloadFile: vi.fn() }));
afterEach(() => vi.resetAllMocks());

it('requests the frozen fact snapshot without falling back to the latest version', async () => {
  await talentApi.resumeFacts('resume-version', 'frozen-snapshot');
  expect(request).toHaveBeenCalledWith('/resume-versions/resume-version/facts/frozen-snapshot');
});

it('keeps current facts available to the candidate detail editor', async () => {
  await talentApi.resumeFacts('resume-version');
  expect(request).toHaveBeenCalledWith('/resume-versions/resume-version/facts');
});
