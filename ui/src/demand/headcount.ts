import type { JdSummary } from './api';

export function hasJdCapacity(value: Pick<JdSummary, 'headcount' | 'occupiedHeadcount' | 'remainingHeadcount'>) {
  return value.headcount != null && value.remainingHeadcount != null && value.remainingHeadcount > 0;
}
