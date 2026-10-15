import { afterEach, expect, it, vi } from 'vitest';
import { createIdempotencyKey } from './idempotency';

afterEach(() => vi.unstubAllGlobals());

it('uses the native UUID implementation when available', () => {
  const randomUUID = vi.fn(() => 'b58da5d0-5b1b-42f4-914d-38f380e507fc');
  vi.stubGlobal('crypto', { randomUUID });
  expect(createIdempotencyKey()).toBe('b58da5d0-5b1b-42f4-914d-38f380e507fc');
  expect(randomUUID).toHaveBeenCalledOnce();
});

it('formats secure random bytes as a version 4 UUID without randomUUID', () => {
  const getRandomValues = vi.fn((bytes: Uint8Array) => {
    bytes.set(Array.from({ length: 16 }, (_, index) => index));
    return bytes;
  });
  vi.stubGlobal('crypto', { getRandomValues });
  expect(createIdempotencyKey()).toBe('00010203-0405-4607-8809-0a0b0c0d0e0f');
  expect(getRandomValues).toHaveBeenCalledOnce();
});

it('generates a fresh key for each operation in the fallback path', () => {
  vi.stubGlobal('crypto', { getRandomValues: globalThis.crypto.getRandomValues.bind(globalThis.crypto) });
  const keys = Array.from({ length: 100 }, () => createIdempotencyKey());
  expect(new Set(keys).size).toBe(100);
  for (const key of keys) expect(key).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
});
