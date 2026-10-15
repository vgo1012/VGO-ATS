import { CopyOutlined } from '@ant-design/icons';
import { App, Button, Input } from 'antd';
import { useState } from 'react';
import { WorkspaceDialog } from '../components/WorkspaceDialog';
import { interviewInfoText } from './interviewInfoText';


import type { InterviewDetails } from './api';
import { useInterviewMaterials } from './useInterviewMaterials';

export function CopyInterviewInfo({value,disabled=false}:{value:InterviewDetails;disabled?:boolean}) {
  const material=useInterviewMaterials(value.interview.id);
  const {message}=App.useApp();
  const [copying,setCopying]=useState(false);
  const [manualText,setManualText]=useState<string>();
  if(material.error)return <Button disabled={disabled} onClick={()=>void material.refetch()}>重试面试信息</Button>;
  return <>
    <Button icon={<CopyOutlined/>} loading={copying||material.isPending} disabled={disabled||!material.data?.candidateName||!material.data.jdTitle} onClick={()=>{
      if(!material.data)return;
      const text=interviewInfoText(value,material.data);
      setCopying(true);
      void (async()=>{
        try{await navigator.clipboard.writeText(text);message.success('面试信息已复制，请手动发送给面试人');}
        catch{setManualText(text);}
        finally{setCopying(false);}
      })();
    }}>复制面试信息</Button>
    <WorkspaceDialog title="手动复制面试信息" open={manualText!==undefined} footer={null} onCancel={()=>setManualText(undefined)} destroyOnHidden>
      <p>浏览器未允许自动复制，请选中下方内容复制后手动发送。</p>
      <Input.TextArea aria-label="待复制的面试信息" readOnly value={manualText??''} rows={12} onFocus={event=>event.currentTarget.select()}/>
    </WorkspaceDialog>
  </>;
}
