import { useQuery } from '@tanstack/react-query';
import { Button, Select } from 'antd';
import { useState } from 'react';
import { FeedbackAlert } from '../components/FeedbackAlert';
import { WorkspaceDialog } from '../components/WorkspaceDialog';
import { jobLabel } from '../demand/jobLabel';
import { ResumeJdMaterials } from '../recruitment/ApplicationMaterials';
import { talentApi } from './api';

export function CandidateInterviewMaterials({candidateId,active,resumeVersionId}:{candidateId:string;active:boolean;resumeVersionId:string|undefined}) {
  const [open,setOpen]=useState(false);
  const [contextId,setContextId]=useState<string>();
  const contexts=useQuery({queryKey:['talent','pool-statuses',[candidateId]],queryFn:()=>talentApi.poolStatuses([candidateId]),enabled:active,retry:false});
  const status=contexts.error?undefined:contexts.data?.find(item=>item.candidateId===candidateId);
  const jobs=status?.jobContexts??[];
  const key=(item:typeof jobs[number])=>`${item.jdVersionId}:${item.resumeVersionId}`;
  const selected=jobs.find(item=>key(item)===contextId)??jobs[0];
  const selectedResume=selected?.resumeVersionId??status?.resumeVersionId??resumeVersionId;
  return <section className="application-materials" aria-label="面试材料">
    <h2>面试材料</h2>
    <Button onClick={()=>setOpen(true)}>查看面试材料</Button>
    <WorkspaceDialog open={open&&active} onCancel={()=>setOpen(false)} title="面试材料" size="wide" width="calc(100vw - 48px)" className="interview-material-dialog" resizeSurface footer={null} destroyOnHidden>
      {open&&active?<>
        {contexts.error?<FeedbackAlert type="error" title="材料关联信息读取失败" action={<Button onClick={()=>void contexts.refetch()}>重试材料关联</Button>}/>:contexts.isPending?<p role="status">正在读取材料关联…</p>:<>
          {jobs.length>1?<Select aria-label="材料对应岗位" style={{width:'100%',marginBottom:12}} value={selected?key(selected):undefined} onChange={setContextId} options={jobs.map(item=>({value:key(item),label:`${jobLabel(item.title,item.jdCode)} · JD 第 ${item.versionNo} 版`}))}/>:null}
          <ResumeJdMaterials key={`${selectedResume}:${selected?.jdVersionId}`} resumeVersionId={selectedResume} jdVersionId={selected?.jdVersionId}/>
        </>}
      </>:null}
    </WorkspaceDialog>
  </section>;
}
