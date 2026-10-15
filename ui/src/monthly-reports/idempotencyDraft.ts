import { createIdempotencyKey } from "../api/idempotency";

export interface IdempotencyDraft {
  fingerprint: string;
  key: string;
}

export function resolveIdempotencyDraft(
  current: IdempotencyDraft | undefined,
  fingerprint: string,
): IdempotencyDraft {
  return current?.fingerprint === fingerprint
    ? current
    : { fingerprint, key: createIdempotencyKey() };
}

export function fileObjectFingerprint(
  file: File,
  identities: WeakMap<File, string>,
): string {
  const existing = identities.get(file);
  if (existing) return existing;
  const identity = createIdempotencyKey();
  identities.set(file, identity);
  return identity;
}
