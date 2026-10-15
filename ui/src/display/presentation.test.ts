import { describe, expect, it } from 'vitest';
import { criterionResult, displayEnum, formatDateTime, safeMeetingUrl } from './labels';
import { normalizeDates } from '../api/dates';
import type { ResumeFactBundle } from '../talent/api';
import { workYears } from '../talent/summary';
import { confirmationLabel } from '../interview/presentation';

describe('business data presentation',()=>{
  it('normalizes actual backend timestamps without inventing a missing timestamp',()=>{
    expect(normalizeDates({createdAt:'old',updatedAt:'old',createTime:'2026-09-03T03:00:00Z',updateTime:'2026-09-05T04:00:00Z'})).toMatchObject({createdAt:'2026-09-03T03:00:00Z',updatedAt:'2026-09-05T04:00:00Z'});
    expect(formatDateTime(normalizeDates<{createdAt?:string}>({}).createdAt)).toBe('时间未记录');
  });
  it('distinguishes intake confirmation from interview confirmation and eligibility from hiring',()=>{
    expect(displayEnum('CONFIRMED')).toBe('已确认入库');
    expect(confirmationLabel('CONFIRMED')).toBe('已确认');
    expect(criterionResult('PASS')).toBe('满足');
    expect(displayEnum('PASS')).toBe('通过');
    expect(displayEnum('FUTURE_UNKNOWN_STATE')).toBe('待确认');
  });
  it('only exposes HTTP meeting links',()=>{
    expect(safeMeetingUrl('https://meeting.example.com/room')).toBe('https://meeting.example.com/room');
    expect(safeMeetingUrl('javascript:alert(1)')).toBeUndefined();
    expect(safeMeetingUrl('file:///C:/secret')).toBeUndefined();
    expect(safeMeetingUrl(null)).toBeUndefined();
  });
  it('does not double count overlapping employment or guess missing end dates',()=>{
    const bundle=(jobs:Array<{startDate:string;endDate:string|null}>)=>({workExperiences:jobs}) as ResumeFactBundle;
    expect(workYears(bundle([{startDate:'2020-01-01',endDate:'2023-01-01'},{startDate:'2021-01-01',endDate:'2023-01-01'}]),new Date('2026-09-05'))).toBe('3 年');
    expect(workYears(bundle([{startDate:'2020-01-01',endDate:null}]))).toBe('年限待补充');
    expect(workYears(bundle([]))).toBe('年限待补充');
  });
});
