import { describe, expect, it } from 'vitest';
import { displayEnum } from './labels';

describe('persisted fact source labels', () => {
  it('renders the actual backend snapshot source codes', () => {
    expect(displayEnum('AI_PARSE')).toBe('AI 解析');
    expect(displayEnum('HUMAN_CORRECTION')).toBe('人工更正');
    expect(displayEnum('AI_PARSED')).toBe('AI 解析');
  });
});

describe('recruitment demand lifecycle labels', () => {
  it('distinguishes paused and closed demands from unconfirmed state', () => {
    expect(displayEnum('PAUSED')).toBe('已暂停');
    expect(displayEnum('CLOSED')).toBe('已关闭');
  });
});
