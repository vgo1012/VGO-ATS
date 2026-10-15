import { describe, expect, it } from 'vitest';
import { addThirtyMinutes, isTenMinuteTime } from './scheduleTime';

describe('schedule date time rules',()=>{
  it('accepts only ten-minute boundaries',()=>{
    expect(isTenMinuteTime('2026-09-10T09:00')).toBe(true);
    expect(isTenMinuteTime('2026-09-10T09:50')).toBe(true);
    expect(isTenMinuteTime('2026-09-10T09:07')).toBe(false);
  });

  it('defaults the end to thirty minutes after the selected start',()=>{
    expect(addThirtyMinutes('2026-09-10T09:50')).toBe('2026-09-10T10:20');
  });
});
