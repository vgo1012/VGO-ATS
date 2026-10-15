import type { ResumeFactBundle } from './api';
import { displayEnum } from '../display/labels';
import { factDate, resumeDateBoundary } from './resumeDates';

export function workYears(facts: ResumeFactBundle, now = new Date()): string {
  // Merge overlapping jobs. Never treat a missing end date as proof of current employment.
  const intervals = facts.workExperiences.map(job => {
    const start = resumeDateBoundary(factDate(job,'start'),'start',now);
    const end = resumeDateBoundary(factDate(job,'end'),'end',now);
    return [start, Math.min(end, now.getTime())] as const;
  });
  if (!intervals.length || intervals.some(([start,end])=>!Number.isFinite(start)||!Number.isFinite(end)||end<start)) return '年限待补充';
  intervals.sort((a,b)=>a[0]-b[0]);
  let total=0, start=intervals[0]![0], end=intervals[0]![1];
  for(const [nextStart,nextEnd] of intervals.slice(1)) {
    if(nextStart<=end+86400000) end=Math.max(end,nextEnd);
    else {total+=end-start+86400000;start=nextStart;end=nextEnd;}
  }
  const years=(total+end-start+86400000)/(365.2425*24*60*60*1000);
  const partial=facts.workExperiences.some(job=>[factDate(job,'start'),factDate(job,'end')].some(date=>date&&/^\d{4}(-\d{2})?$/.test(date)));
  return years<1?'不足 1 年':`${partial?'至少 ':''}${Math.floor(years)} 年`;
}

export function educationSummary(facts: ResumeFactBundle): string {
  return highestEducation(facts.educations.flatMap(value=>value.degreeName?[value.degreeName]:[]));
}

const degreeLevels = [
  ['博士', ['博士', '博士研究生', '博士及以上', 'doctor', 'doctorate', 'phd']],
  ['硕士', ['硕士', '硕士研究生', '研究生', '硕士及以上', 'master', 'masterdegree']],
  ['本科', ['本科', '大学本科', '学士', '本科及以上', 'bachelor', 'bachelordegree']],
  ['大专', ['专科', '大专', '大学专科', 'associate', 'associatedegree', 'college']],
  ['高中', ['高中', '普通高中', 'highschool']],
  ['中专', ['中专', '中等专科', 'technicalsecondaryschool']],
  ['初中', ['初中', 'middleschool', 'juniorhighschool']],
  ['小学', ['小学', 'primaryschool', 'elementaryschool']],
] as const;

const degreeKey=(value:string)=>value.trim().toLocaleLowerCase().replace(/[\s.'’_-]+/g,'');

/** 多段教育经历统一展示已识别的最高学历；未知原文保持可见而不擅自推断。 */
export function highestEducation(values: readonly string[] | null | undefined): string {
  const degrees=[...new Set((values??[]).map(value=>displayEnum(value,'')).filter(Boolean))];
  for(const [label,aliases] of degreeLevels) {
    if(degrees.some(degree=>aliases.some(alias=>degreeKey(alias)===degreeKey(degree)))) return label;
  }
  return degrees[0]??'学历待补充';
}
