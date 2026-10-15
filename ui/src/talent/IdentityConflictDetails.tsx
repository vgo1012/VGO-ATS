import { scrollableTableHeader } from '../components/scrollableTableHeader';
import { Button, Table, Tag } from 'antd';
import type { IdentityConflict } from './api';
import { identityConflictReview } from './intakeIdentityReview';
import type { useIdentityConflictDetails } from './useIdentityConflictDetails';

export function IdentityConflictDetails({conflict,details}:{conflict:IdentityConflict;details:ReturnType<typeof useIdentityConflictDetails>}){
  const {intake,candidates,ids,pending,failed,denied,ready,retry}=details;
  const review=identityConflictReview(conflict);
  if(details.restricted)return <section aria-label="身份冲突核对依据" style={{marginBottom:16,overflowWrap:'anywhere'}}><h3>{review.reason}</h3><p>{review.advice}</p></section>;
  const fields=[{key:'name',label:'姓名'},{key:'phone',label:'手机号'},{key:'email',label:'邮箱'}] as const;
  const rows=ready?[{key:'intake',label:'本次简历',name:intake.data?.parsedFullName,phone:intake.data?.parsedPhone,email:intake.data?.parsedEmail},
    ...candidates.map((query,index)=>({key:ids[index]!,label:`已有档案 ${index+1}`,name:query.data?.fullName,phone:query.data?.primaryPhone,email:query.data?.primaryEmail}))]:[];
  return <section aria-label="身份冲突核对依据" style={{marginBottom:16,overflowWrap:'anywhere'}}>
    <h3>{review.reason}</h3><p>{review.advice}</p>
    {pending?<p role="status">正在读取本次简历和已有候选人身份信息，完成后才能确认处理。</p>:null}
    {failed?<div role="status"><p>{denied?'无权读取部分身份信息，请联系有权限的负责人核验。':'身份信息读取失败，请重试后再处理。'}</p><Button loading={pending} onClick={retry}>重试读取身份信息</Button></div>:null}
    {ready?<><p>来源文件：{intake.data?.originalFileName}</p><Table aria-label="身份信息对照" onHeaderRow={() => scrollableTableHeader('身份信息对照表头，可横向滚动')} pagination={false} size="small" rowKey="key" dataSource={fields} tableLayout="fixed" scroll={{x:560}} columns={[
      {title:'核对字段',dataIndex:'label',width:85},
      ...rows.map(row=>({title:row.label,key:row.key,render:(_:unknown,field:typeof fields[number])=><span style={{overflowWrap:'anywhere'}}>{row[field.key]?.trim()||'未提供'}</span>})),
      {title:'核对提示',width:95,render:(_:unknown,field:typeof fields[number])=>{
        const values=rows.map(row=>row[field.key]?.trim()).filter(Boolean);
        return new Set(values).size>1?<Tag color="gold">值不一致</Tag>:values.length<rows.length?<Tag>信息缺失</Tag>:<span>相同</span>;
      }},
    ]}/></>:null}
    <p>关联已有候选人：确认本次简历属于选中的已有档案，不合并两个已有档案。</p>
    <p>创建独立候选人：确认属于不同的人，保留独立档案，不合并已有档案。</p>
    {conflict.candidateIdB?<p>合并候选人：确认两个已有档案属于同一人，保留选中的档案并汇集简历版本与历史记录，请谨慎核实。</p>:null}
  </section>;
}
