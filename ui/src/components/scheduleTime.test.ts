import dayjs from 'dayjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { defaultScheduleStart } from './scheduleTime';

afterEach(() => vi.useRealTimers());

describe('defaultScheduleStart',()=>{
  it('uses Beijing time for the default even when the runtime timezone is UTC',()=>{
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-09-28T10:00:00Z'));
    expect(defaultScheduleStart()).toBe('2026-09-28T18:10');
  });
  it('defaults to a ten-minute slot at least ten minutes after the current minute',()=>{
    expect(defaultScheduleStart(dayjs('2026-09-10T21:17:45'))).toBe('2026-09-10T21:30');
    expect(defaultScheduleStart(dayjs('2026-09-10T21:20:45'))).toBe('2026-09-10T21:30');
  });
});
