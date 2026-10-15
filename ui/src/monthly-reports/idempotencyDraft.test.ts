import { expect, it } from "vitest";
import {
  fileObjectFingerprint,
  resolveIdempotencyDraft,
} from "./idempotencyDraft";

it("keeps a key for an uncertain replay and replaces it when the payload changes", () => {
  const first = resolveIdempotencyDraft(undefined, "payload-a");
  expect(resolveIdempotencyDraft(first, "payload-a")).toBe(first);
  const changed = resolveIdempotencyDraft(first, "payload-b");
  expect(changed.key).not.toBe(first.key);
});

it("treats a replacement file as a changed upload even when its metadata matches", () => {
  const identities = new WeakMap<File, string>();
  const first = new File(["same"], "offer.xlsx", {
    type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  });
  const replacement = new File(["same"], "offer.xlsx", { type: first.type });
  expect(fileObjectFingerprint(first, identities)).toBe(
    fileObjectFingerprint(first, identities),
  );
  expect(fileObjectFingerprint(replacement, identities)).not.toBe(
    fileObjectFingerprint(first, identities),
  );
});
