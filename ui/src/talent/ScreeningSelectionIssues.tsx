import { useQuery } from '@tanstack/react-query';
import { Button } from 'antd';
import { talentApi, type Candidate } from './api';
import type { ScreeningSelectionIssue } from './screeningAdmissionSelection';

export function ScreeningSelectionIssues({issues,items,onOpen}:{issues:ScreeningSelectionIssue[];items:Candidate[];onOpen:(id:string)=>void}) {
  const missingIds=issues.map(issue=>issue.candidateId).filter(id=>!items.some(item=>item.id===id));
  const names=useQuery({
    queryKey:['talent','screening-issue-candidates',missingIds],
    queryFn:async()=>{
      const people:Candidate[]=[];
      for(let index=0;index<missingIds.length;index+=100)people.push(...await talentApi.candidateBatch(missingIds.slice(index,index+100)));
      return people;
    },
    enabled:missingIds.length>0, retry:false,
  });
  const byId=new Map([...(names.data??[]),...items].map(person=>[person.id,person]));
  return <section aria-label="智能筛选待处理候选人" role="status">
    <strong>{issues.length} 位候选人暂不能加入智能筛选</strong>
    <p>请核对以下人员，处理后重试；原有勾选已保留。</p>
    {names.isError?<Button onClick={()=>void names.refetch()}>重试读取跨页候选人姓名</Button>:null}
    <ul>{issues.map((issue,index)=>{
      const person=byId.get(issue.candidateId);
      return <li key={issue.candidateId}><Button type="link" onClick={()=>onOpen(issue.candidateId)}>{person?.fullName??(names.isFetching?'姓名加载中…':`候选人 ${index+1}（姓名暂不可用，查看详情）`)}</Button>{person?.primaryPhone??person?.primaryEmail}：{issue.reason}</li>;
    })}</ul>
  </section>;
}
