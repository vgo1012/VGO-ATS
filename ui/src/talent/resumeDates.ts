type Dates = {startDate?:string|null;endDate?:string|null;startDateText?:string|null;endDateText?:string|null};
export const factDate=(value:Dates,side:'start'|'end')=>value[`${side}DateText`]??value[`${side}Date`]??null;
export const formatResumeDate=(value:string|null|undefined)=>value==='PRESENT'?'至今':value??'时间未记录';

// Partial dates are bounded for duration calculation; the bounds never replace displayed facts.
export function resumeDateBoundary(value:string|null,side:'start'|'end',now:Date):number {
  if(value==='PRESENT'||value==='至今')return side==='end'?now.getTime():NaN;
  if(!value)return NaN;
  if(/^\d{4}$/.test(value))return side==='start'?Date.UTC(Number(value),11,31):Date.UTC(Number(value),0,1);
  if(/^\d{4}-\d{2}$/.test(value)){
    const [year,month]=value.split('-').map(Number) as [number,number];
    if(month<1||month>12)return NaN;
    return side==='start'?Date.UTC(year,month,0):Date.UTC(year,month-1,1);
  }
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return NaN;
  const time = Date.parse(value);
  return Number.isFinite(time) && new Date(time).toISOString().slice(0, 10) === value ? time : NaN;
}
