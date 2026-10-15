import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, expect, it } from 'vitest';
import { JdHeadcount } from './JdHeadcount';
import { hasJdCapacity } from './headcount';

afterEach(cleanup);

it('renders missing, available, and full HC states explicitly', () => {
  const { rerender } = render(<JdHeadcount value={{ headcount: null, occupiedHeadcount: 0, remainingHeadcount: null }} />);
  expect(screen.getByText('HC 待填写')).toBeInTheDocument();
  rerender(<JdHeadcount value={{ headcount: 2, occupiedHeadcount: 1, remainingHeadcount: 1 }} />);
  expect(screen.getByText('已占用 1 / 2 · 剩余 1')).toBeInTheDocument();
  rerender(<JdHeadcount value={{ headcount: 2, occupiedHeadcount: 2, remainingHeadcount: 0 }} compact />);
  expect(screen.getByText('HC 已满')).toBeInTheDocument();
});

it('fails closed unless HC is present and has a remaining slot', () => {
  expect(hasJdCapacity({ headcount: null, occupiedHeadcount: 0, remainingHeadcount: null })).toBe(false);
  expect(hasJdCapacity({ headcount: 2, occupiedHeadcount: 2, remainingHeadcount: 0 })).toBe(false);
  expect(hasJdCapacity({ headcount: 2, occupiedHeadcount: 1, remainingHeadcount: 1 })).toBe(true);
});

it('reports actual onboarding as the only HC occupancy', () => {
  const { rerender } = render(<JdHeadcount value={{ headcount: 2, occupiedHeadcount: 0, remainingHeadcount: 2, onboardedHeadcount: 0 }} />);
  expect(screen.getByText('只有实际入职才占用 HC；Offer 发出及进入入职流程都不占名额。推荐和面试可同时推进多位候选人。')).toBeInTheDocument();
  rerender(<JdHeadcount value={{ headcount: 2, occupiedHeadcount: 2, remainingHeadcount: 0, onboardedHeadcount: 2 }} compact />);
  expect(screen.getByText('HC 已满')).toBeInTheDocument();
  expect(screen.getByText('已入职 2 / 2 · 交付完成')).toBeInTheDocument();
});
