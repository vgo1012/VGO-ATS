export const stepLabel:Record<string,string>={CANDIDATE_COMMUNICATION:'候选人沟通',PRE_SUBMISSION_INTERNAL_INTERVIEW:'推荐前内部面试',CLIENT_SUBMISSION:'甲方推荐',CLIENT_AI_INTERVIEW:'甲方 AI 面试（人工代录）',INTERVIEW_ROUND_1:'一面',INTERVIEW_ROUND_2:'二面',INTERVIEW_ROUND_3:'三面',DECISION:'人工招聘决定',OFFER:'Offer'};

type InterviewStep = { stepCode?:string|null|undefined; roundNo?:number|null|undefined; interviewType?:string|null|undefined };
type RecruitmentType = 'INTERNAL'|'EXTERNAL';

/** 普通轮次的类型以本轮面试事实为准，招聘类型不能替代面试标签。 */
export function interviewStepLabel(value:InterviewStep,_recruitmentType?:RecruitmentType):string {
  void _recruitmentType; // 保留旧调用签名，招聘类型不能推断本轮面试标签。
  const round=value.roundNo&&value.roundNo>0?value.roundNo:undefined;
  if(!value.stepCode)return '面试';
  if(value.stepCode==='PRE_SUBMISSION_INTERNAL_INTERVIEW')return `内部预面${round?` · 第 ${round} 次`:''}`;
  if(value.stepCode==='CLIENT_AI_INTERVIEW')return `甲方 AI 面试${round?` · 第 ${round} 次`:'（人工代录）'}`;
  if(value.stepCode.startsWith('INTERVIEW_ROUND_')){
    const type=value.interviewType==='EXTERNAL'?'外部面试':value.interviewType==='INTERNAL'?'内部面试':value.interviewType?.startsWith('EXTERNAL_CLIENT')?'甲方面试':'面试';
    const fallback=Number(value.stepCode.replace('INTERVIEW_ROUND_',''));
    const actual=round??(fallback>0?fallback:undefined);
    return `${type}${actual?` · 第 ${actual} 轮`:''}`;
  }
  return stepLabel[value.stepCode]??value.stepCode;
}
