import { PaginatedTable } from '../components/PaginatedTable';
import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { Button, Checkbox, Input, Progress, Space, Tag } from 'antd';
import { useRef, useState } from 'react';
import { WorkspaceDialog } from '../components/WorkspaceDialog';
import { ApiProblemError } from '../api/problem';
import { confirmCurrentIntake, type ResumeIntake } from './api';

interface Row {intake:ResumeIntake;fullName:string;email:string;phone:string;status:'PENDING'|'SUCCEEDED'|'FAILED';error?:string|undefined}
interface Props {items:ResumeIntake[];onClose:()=>void;onSaved:()=>Promise<unknown>}
/** 姓名仅由 HR 明确填写；不运行解析，不以文件名替代身份事实。 */
export function BatchIntakeConfirmation({items,onClose,onSaved}:Props) {
  const [rows,setRows]=useState<Row[]>(()=>items.map(intake=>({intake,fullName:'',email:'',phone:'',status:'PENDING'})));
  const [reviewed,setReviewed]=useState(false);
  const [busy,setBusy]=useState(false);
  const [paste,setPaste]=useState('');
  const [error,setError]=useState<string>();
  const stopping=useRef(false);
  const running=useRef(false);
  const pending=rows.filter(row=>row.status!=='SUCCEEDED');
  const succeeded=rows.filter(row=>row.status==='SUCCEEDED').length;
  const patch=(id:string,change:Partial<Row>)=>setRows(previous=>previous.map(row=>row.intake.id===id?{...row,...change}:row));
  const applyPaste=()=>{
    const values=paste.split(/\r?\n/).filter(line=>line.trim()).map(line=>line.split('\t'));
    if(values.length!==pending.length){setError(`请按表格顺序提供 ${pending.length} 行姓名、邮箱、手机号，用制表符分隔。`);return;}
    setRows(previous=>{let index=0;return previous.map(row=>row.status==='SUCCEEDED'?row:{...row,fullName:values[index]?.[0]?.trim()??'',email:values[index]?.[1]?.trim()??'',phone:values[index++]?.[2]?.trim()??''});});
    setReviewed(false);setError(undefined);
  };
  const confirm=async()=>{
    if(running.current||!reviewed)return;
    if(pending.some(row=>!row.fullName.trim()||row.fullName.trim().length>160)){setError('请为每一项填写已核对的姓名（1–160 字）。');return;}
    running.current=true;setBusy(true);setError(undefined);stopping.current=false;
    try {
      for(const row of pending){
        if(stopping.current)break;
        try {
          const confirmed=await confirmCurrentIntake(row.intake,{fullName:row.fullName.trim(),...(row.email.trim()?{email:row.email.trim()}:{}),...(row.phone.trim()?{phone:row.phone.trim()}:{})});
          patch(row.intake.id,{intake:confirmed,status:'SUCCEEDED',error:undefined});
        } catch(cause) {
          patch(row.intake.id,{status:'FAILED',error:cause instanceof ApiProblemError?cause.problem.detail:'确认失败，请刷新收件状态后重试'});
        }
      }
      await onSaved();
    } catch {setError('结果已保留，列表刷新失败，可关闭后重新加载。');}
    finally {running.current=false;setBusy(false);}
  };
  return <WorkspaceDialog title={`批量确认入库 · ${rows.length} 份`} open width={1000} onCancel={()=>{if(!busy)onClose();}} closable={!busy} mask={{closable:!busy}} keyboard={!busy} footer={<Space wrap><Button disabled={busy} onClick={onClose}>关闭</Button>{busy?<Button onClick={()=>{stopping.current=true;}}>完成当前项后暂停</Button>:<Button type="primary" disabled={!reviewed||!pending.length} onClick={()=>void confirm()}>{succeeded?'继续确认未完成项':'确认已核对项入库'}</Button>}</Space>}>
    <Alert type="info" showIcon title="此步骤只确认身份并保存简历，不运行 AI 筛选。" description="请逐份核对原简历中的姓名及联系方式。身份冲突由服务端保留待人工处理，已确认项会核对服务端状态后跳过，不重复提交。"/>
    {!busy&&pending.length?<details><summary>从表格粘贴姓名和联系方式</summary><Input.TextArea aria-label="批量身份信息" rows={3} value={paste} onChange={event=>setPaste(event.target.value)} placeholder="按下表未完成项顺序粘贴：姓名、邮箱、手机号三列（邮箱、手机号可空）"/><Button onClick={applyPaste}>填入未完成项</Button></details>:null}
    {error?<Alert occurrence={error} type="error" showIcon title={error}/>:null}
    <Progress percent={Math.round(succeeded/Math.max(1,rows.length)*100)} format={()=>`已确认 ${succeeded} / ${rows.length}`}/>
    <PaginatedTable<Row> rowKey={row=>row.intake.id} dataSource={rows} initialPageSize={20} scroll={{x:820,y:380}} columns={[
      {title:'文件 / 身份核对',width:230,render:(_,row)=><Space orientation="vertical" size={0}><span>{row.intake.originalFileName}</span>{row.error?<span role="alert">{row.error}</span>:null}</Space>},
      {title:'姓名',width:160,render:(_,row)=><Input aria-label={`${row.intake.originalFileName} 姓名`} disabled={busy||row.status==='SUCCEEDED'} value={row.fullName} onChange={event=>{patch(row.intake.id,{fullName:event.target.value});setReviewed(false);}}/>},
      {title:'邮箱（可选）',width:200,render:(_,row)=><Input aria-label={`${row.intake.originalFileName} 邮箱`} disabled={busy||row.status==='SUCCEEDED'} value={row.email} onChange={event=>{patch(row.intake.id,{email:event.target.value});setReviewed(false);}}/>},
      {title:'手机号（可选）',width:160,render:(_,row)=><Input aria-label={`${row.intake.originalFileName} 手机号`} disabled={busy||row.status==='SUCCEEDED'} value={row.phone} onChange={event=>{patch(row.intake.id,{phone:event.target.value});setReviewed(false);}}/>},
      {title:'结果',width:90,render:(_,row)=><Tag color={row.status==='SUCCEEDED'?'green':row.status==='FAILED'?'red':'default'}>{{PENDING:'待确认',SUCCEEDED:'已入库',FAILED:'待处理'}[row.status]}</Tag>},
    ]}/>
    <Checkbox disabled={busy} checked={reviewed} onChange={event=>setReviewed(event.target.checked)}>我已逐份核对以上未完成项的姓名与联系方式</Checkbox>
  </WorkspaceDialog>;
}
