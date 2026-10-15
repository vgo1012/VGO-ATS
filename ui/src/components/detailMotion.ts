export type DetailOrigin = { x: number; y: number };

/** Sample only on activation; keep dense workspaces steady with a bounded arrival. */
export function detailOrigin(source: Element | null): DetailOrigin | undefined {
  if (!source || source === document.body) return undefined;
  const rect = source.getBoundingClientRect();
  if (!rect.width || !rect.height) return undefined;
  return {
    x: Math.max(-28, Math.min(28, (rect.x + rect.width / 2 - window.innerWidth / 2) * .08)),
    y: Math.max(-20, Math.min(20, (rect.y + rect.height / 2 - window.innerHeight / 2) * .08)),
  };
}
