import { describe, expect, it } from 'vitest';
import type { ResumeFactBundle } from './api';
import { educationSummary, highestEducation } from './summary';

const factsWithDegrees=(...degrees:string[])=>({
  educations:degrees.map((degreeName,index)=>({id:`education-${index}`,degreeName})),
  workExperiences:[],
} as unknown as ResumeFactBundle);

describe('highest education summary',()=>{
  it('shows a master degree when the resume also contains an earlier bachelor degree',()=>{
    expect(educationSummary(factsWithDegrees('本科','硕士研究生'))).toBe('硕士');
    expect(educationSummary(factsWithDegrees('硕士研究生','本科'))).toBe('硕士');
  });

  it('normalizes common degree aliases and keeps an unknown evidenced value visible',()=>{
    expect(highestEducation(['Bachelor Degree','Master Degree'])).toBe('硕士');
    expect(highestEducation(['荣誉文凭'])).toBe('荣誉文凭');
    expect(highestEducation([])).toBe('学历待补充');
  });
});
