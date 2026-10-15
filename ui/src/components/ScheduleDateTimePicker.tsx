import { DatePicker } from 'antd';
import dayjs from 'dayjs';

const DATE_TIME_FORMAT='YYYY-MM-DD HH:mm';

export function ScheduleDateTimePicker({value,onChange,id,'aria-invalid':ariaInvalid}:{value?:string;onChange:(value:string)=>void;id?:string;'aria-invalid'?:boolean}) {
  const pickerValue=value&&dayjs(value).isValid()?dayjs(value):null;
  return <DatePicker
    {...(id?{id}:{})}
    {...(ariaInvalid===undefined?{}:{'aria-invalid':ariaInvalid})}
    value={pickerValue}
    onChange={next=>onChange(next?.format('YYYY-MM-DDTHH:mm')??'')}
    format={DATE_TIME_FORMAT}
    showTime={{minuteStep:10,showSecond:false}}
    showNow={false}
    needConfirm={false}
    style={{width:'100%'}}
  />;
}
