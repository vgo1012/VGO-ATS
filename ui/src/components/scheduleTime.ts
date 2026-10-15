import dayjs from 'dayjs';
import utc from 'dayjs/plugin/utc';
import timezone from 'dayjs/plugin/timezone';

dayjs.extend(utc);
dayjs.extend(timezone);

export const isTenMinuteTime=(value?:string)=>Boolean(value&&dayjs(value).isValid()&&dayjs(value).minute()%10===0);
export const addThirtyMinutes=(value:string)=>dayjs(value).isValid()?dayjs(value).add(30,'minute').format('YYYY-MM-DDTHH:mm'):'';
export const defaultScheduleStart=(now=dayjs().tz('Asia/Shanghai'))=>{
  const target=now.add(10,'minute').startOf('minute');
  const remainder=target.minute()%10;
  return target.add(remainder===0?0:10-remainder,'minute').format('YYYY-MM-DDTHH:mm');
};

export const tenMinuteRule={
  validator:(_:unknown,value?:string)=>!value||isTenMinuteTime(value)?Promise.resolve():Promise.reject(new Error('分钟请选择 00、10、20、30、40 或 50')),
};
