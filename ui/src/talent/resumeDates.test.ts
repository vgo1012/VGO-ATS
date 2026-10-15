import { describe, expect, it } from 'vitest';
import { factDate, formatResumeDate, resumeDateBoundary } from './resumeDates';
import { workYears } from './summary';
import type { ResumeFactBundle, ResumeWorkFact } from './api';

const facts=(jobs:Partial<ResumeWorkFact>[])=>({workExperiences:jobs} as ResumeFactBundle);
describe('resume date precision',()=>{
  it('displays original month and explicit current employment without inventing a day',()=>{
    expect(formatResumeDate('2022-03')).toBe('2022-03');
    expect(formatResumeDate('PRESENT')).toBe('至今');
    expect(factDate({startDate:null,startDateText:'2022-03'},'start')).toBe('2022-03');
    expect(workYears(facts([{startDate:null,endDate:null,startDateText:'2022-03',endDateText:'PRESENT'}]),new Date('2026-09-08'))).toBe('至少 4 年');
  });
  it('keeps unknown end dates unknown and does not double count concurrent jobs',()=>{
    expect(workYears(facts([{startDate:'2022-03-01',endDate:null}]))).toBe('年限待补充');
    expect(workYears(facts([{startDate:'2020-01-01',endDate:'2023-01-01'},{startDate:'2021-01-01',endDate:'2024-01-01'}]))).toBe('4 年');
  });
  it('rejects invalid calendar days instead of rolling them into the next month',()=>{
    expect(resumeDateBoundary('2023-02-29','start',new Date())).toBeNaN();
    expect(resumeDateBoundary('2024-02-29','start',new Date())).toBe(Date.parse('2024-02-29'));
  });
});
