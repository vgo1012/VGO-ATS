export function compareHistoryTime(left: string | null | undefined, right: string | null | undefined): number {
  return (right ? Date.parse(right) : 0) - (left ? Date.parse(left) : 0);
}
