import { factDate, formatResumeDate, resumeDateBoundary } from './resumeDates';
import type { ResumeFactBundle } from './api';
export interface ResumeReviewFlag { id:string; label:string; detail:string; origin:'AI'|'RULE'|'FACT' }

export function resumeReviewFlags(bundle:ResumeFactBundle, now=new Date()):ResumeReviewFlag[] {
  const flags:ResumeReviewFlag[]=[];
  for(const item of bundle.simpleFacts.filter(v=>v.factType==='UNKNOWN'&&v.nameValue)){
    const detail=item.nameValue!;
    // 年龄、性别、婚育等缺失不是简历质量风险。
    if(/年龄|性别|婚育|婚姻|出生|民族|宗教|\b(age|gender|sex|marital|marriage|pregnancy|ethnicity|religion|birth)\b/i.test(detail))continue;
    flags.push({id:'ai-'+item.id,label:/时间|日期|年份|起止|\b(dates?|years?|duration)\b/i.test(detail)?'时间待核实':/矛盾|冲突|不一致/.test(detail)?'信息一致性待核实':'解析信息待核实',detail,origin:bundle.snapshot.sourceType==='AI_PARSE'?'AI':'FACT'});
  }
  const groups=[{name:'工作经历',path:'workExperiences',items:bundle.workExperiences},{name:'项目经历',path:'projectExperiences',items:bundle.projectExperiences}];
  for(const group of groups)group.items.forEach((item,index)=>{
    const start=factDate(item,'start'),end=factDate(item,'end');
    if(!start||!end)flags.push({id:'dates-'+item.id,label:group.name+'时间待补充',detail:`第 ${index+1} 段${group.name}：${formatResumeDate(start)} — ${formatResumeDate(end)}。请对照原简历核实，缺失时间不计作完整任职年限。`,origin:'RULE'});
  });
  for(const group of [...groups,{name:'教育经历',path:'educations',items:bundle.educations}])group.items.forEach((item,index)=>{
    const start=factDate(item,'start'),end=factDate(item,'end');
    // 最早可能开始仍晚于最晚可能结束才是明确倒置，避免年份/月精度误报。
    const earliestStart=resumeDateBoundary(start,'end',now);
    const latestEnd=end==='PRESENT'?now.getTime():resumeDateBoundary(end,'start',now);
    if(Number.isFinite(earliestStart)&&Number.isFinite(latestEnd)&&earliestStart>latestEnd&&end!=='PRESENT'){
      flags.push({id:'reversed-'+item.id,label:group.name+'起止顺序待核实',detail:`第 ${index+1} 段${group.name}：${formatResumeDate(start)} — ${formatResumeDate(end)}。开始时间晚于结束时间，请核对原简历或更正录入；系统不猜测正确年份。`,origin:'RULE'});
    }
    if(start!=='PRESENT'&&Number.isFinite(earliestStart)&&earliestStart>now.getTime()){
      flags.push({id:'future-'+item.id,label:group.name+'未来时间待核实',detail:`第 ${index+1} 段${group.name}开始于 ${formatResumeDate(start)}，晚于当前日期。可能是计划经历或日期录入差异，请确认后再作为已发生经历使用。`,origin:'RULE'});
    }
  });
  const intervals=bundle.workExperiences.map((item,index)=>({item,index,start:resumeDateBoundary(factDate(item,'start'),'start',now),end:resumeDateBoundary(factDate(item,'end'),'end',now)}));
  for(let i=0;i<intervals.length;i++)for(let j=i+1;j<intervals.length;j++){
    const a=intervals[i]!,b=intervals[j]!;
    if(a.item.employer&&a.item.employer===b.item.employer)continue;
    if(Number.isFinite(a.start)&&Number.isFinite(a.end)&&Number.isFinite(b.start)&&Number.isFinite(b.end)&&Math.max(a.start,b.start)<Math.min(a.end,b.end)){
      flags.push({id:`overlap-${a.item.id}-${b.item.id}`,label:'任职时间重叠待核实',detail:`第 ${a.index+1}、${b.index+1} 段工作经历存在时间重叠，可能涉及兼职或记录方式差异，请人工核实。`,origin:'RULE'});
    }
  }
  return flags;
}
