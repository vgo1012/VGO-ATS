import { expect, it } from 'vitest';
import { resumeReviewFlags } from './resumeReview';
import type { ResumeFactBundle, ResumeWorkFact } from './api';

const empty=():ResumeFactBundle=>({snapshot:{} as ResumeFactBundle['snapshot'],workExperiences:[],projectExperiences:[],educations:[],skills:[],simpleFacts:[]});

it('labels missing dates and AI uncertainty without treating personal characteristics as risks',()=>{
 const facts=empty();
 facts.workExperiences=[{id:'work',startDateText:'2022-03',endDate:null} as ResumeWorkFact];
 facts.simpleFacts=[{id:'unknown',factType:'UNKNOWN',nameValue:'项目时间前后矛盾，待核实'},{id:'age',factType:'UNKNOWN',nameValue:'年龄未提供'}] as ResumeFactBundle['simpleFacts'];
 const flags=resumeReviewFlags(facts);
 expect(flags.map(value=>value.label)).toEqual(['时间待核实','工作经历时间待补充']);
 expect(flags.some(value=>value.detail.includes('年龄'))).toBe(false);
});

it('flags only definite overlaps, allowing coarse dates and transfers in one company',()=>{
 const facts=empty();
 facts.workExperiences=[{id:'a',employer:'甲',startDateText:'2020',endDateText:'2021'},{id:'b',employer:'乙',startDateText:'2021',endDateText:'2022'}] as ResumeWorkFact[];
 expect(resumeReviewFlags(facts)).toHaveLength(0);
 facts.workExperiences[1]={id:'b',employer:'乙',startDateText:'2020-06',endDateText:'PRESENT'} as ResumeWorkFact;
 expect(resumeReviewFlags(facts,new Date('2026-09-08'))[0]?.label).toBe('任职时间重叠待核实');
 facts.workExperiences[1].employer='甲';
 expect(resumeReviewFlags(facts)).toHaveLength(0);
});

it('flags reversed and future dates and keeps human correction provenance',()=>{
 const facts=empty();
 facts.snapshot.sourceType='HUMAN_CORRECTION';
 facts.simpleFacts=[{id:'dates',factType:'UNKNOWN',nameValue:'Employment dates not provided.'}] as ResumeFactBundle['simpleFacts'];
 facts.workExperiences=[{id:'reverse',employer:'甲',startDateText:'2024-06',endDateText:'2023-06'},{id:'future',employer:'乙',startDateText:'2028',endDateText:'PRESENT'}] as ResumeWorkFact[];
 const flags=resumeReviewFlags(facts,new Date('2026-09-08'));
 expect(flags.find(value=>value.id==='ai-dates')?.origin).toBe('FACT');
 expect(flags.some(value=>value.id==='reversed-reverse')).toBe(true);
 expect(flags.some(value=>value.id==='future-future')).toBe(true);
});

it('handles large overlapping histories without evidence payloads',()=>{
 const facts=empty();
 facts.workExperiences=Array.from({length:20},(_,index)=>({id:`work-${index}`,employer:`公司${index}`,startDateText:'2020-01',endDateText:'2022-01'} as ResumeWorkFact));
 const flags=resumeReviewFlags(facts,new Date('2026-09-12'));
 expect(flags).toHaveLength(190);
 expect(flags[0]?.id).toBe('overlap-work-0-work-1');
});
