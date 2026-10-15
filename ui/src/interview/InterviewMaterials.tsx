import { jobLabel } from '../demand/jobLabel';
import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { Button, Empty, Spin, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { WorkspaceDialog } from '../components/WorkspaceDialog';
import { FileDownloadButton } from '../components/FileDownloadButton';
import { interviewApi, type InterviewMaterial } from './api';
import { useInterviewMaterials } from './useInterviewMaterials';

export function InterviewReference({id,field}:{id:string;field:'candidateName'|'jdTitle'}) {
  const material=useInterviewMaterials(id);
  return <>{(material.data ? field === 'jdTitle' ? jobLabel(material.data.jdTitle, material.data.jdCode) : material.data.candidateName : undefined)??(material.isPending?'正在读取…':'面试材料暂不可用')}</>;
}

export function InterviewMaterials({id,scope='interview'}:{id:string;scope?:'interview'|'application'}) {
  const [open,setOpen]=useState(false);
  const material=useInterviewMaterials(id);
  const applicationScope=scope==='application';
  return <>
    <Button onClick={()=>setOpen(true)}>{applicationScope?'查看面试材料':'查看本次面试材料'}</Button>
    <WorkspaceDialog
      size="wide"
      width="calc(100vw - 48px)"
      className="interview-material-dialog"
      resizeSurface
      title={applicationScope?'面试材料':'本次面试材料'}
      open={open}
      onCancel={()=>setOpen(false)}
      footer={null}
      destroyOnHidden
    >
      {material.isPending?<div className="interview-material-loading" role="status"><Spin/><span>正在读取本次面试材料…</span></div>:null}
      {material.error?<Alert occurrence={material.error} type="error" showIcon title="面试材料读取失败，请确认仍有本次面试的查看权限。" action={<Button onClick={()=>void material.refetch()}>重试材料</Button>}/>:null}
      {material.data?<MaterialComparison id={id} value={material.data}/>:null}
    </WorkspaceDialog>
  </>;
}

function MaterialComparison({id,value}:{id:string;value:InterviewMaterial}) {
  return <div className="interview-material-viewer">
    <section className="interview-material-pane" aria-label={`本次简历，第 ${value.resumeVersionNo} 版`}>
      <header><div><Typography.Title level={4}>本次简历 · 第 {value.resumeVersionNo} 版</Typography.Title><Typography.Text type="secondary">{value.resumeFileName}</Typography.Text></div><FileDownloadButton size="small" download={()=>interviewApi.downloadResume(id,value.resumeFileName)}>下载原文件</FileDownloadButton></header>
      <ResumePreview key={value.resumeVersionId} id={id} value={value}/>
    </section>
    <section className="interview-material-pane" aria-label={`岗位 JD，第 ${value.jdVersionNo} 版`}>
      <header><div><Typography.Title level={4}>岗位 JD · 第 {value.jdVersionNo} 版</Typography.Title><Typography.Text type="secondary">{jobLabel(value.jdTitle, value.jdCode)}</Typography.Text></div></header>
      <Typography.Paragraph className="interview-jd-preview">{value.jdRawText}</Typography.Paragraph>
    </section>
  </div>;
}

function ResumePreview({id,value}:{id:string;value:InterviewMaterial}) {
  const supported=value.resumeMediaType==='application/pdf'||value.resumeMediaType.startsWith('image/')||value.resumeMediaType==='text/plain';
  const [url,setUrl]=useState<string>();
  const [error,setError]=useState(false);
  const [attempt,setAttempt]=useState(0);
  useEffect(()=>{
    if(!supported)return;
    let active=true;let objectUrl:string|undefined;
    void interviewApi.previewResume(id).then(blob=>{
      if(!active)return;
      objectUrl=URL.createObjectURL(blob);setUrl(objectUrl);
    }).catch(()=>{if(active)setError(true);});
    return()=>{active=false;if(objectUrl)URL.revokeObjectURL(objectUrl);};
  },[attempt,id,supported,value.resumeVersionId]);
  if(!supported)return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="该文件格式暂不支持在线预览，请下载原文件查看。"/>;
  if(error)return <Alert type="error" showIcon title="简历预览加载失败" action={<Button onClick={()=>{setError(false);setUrl(undefined);setAttempt(value=>value+1);}}>重试预览</Button>}/>;
  if(!url)return <div className="interview-material-loading" role="status"><Spin/><span>正在加载简历预览…</span></div>;
  return value.resumeMediaType.startsWith('image/')?<img className="interview-resume-image" src={url} alt={`${value.resumeFileName} 在线预览`}/>:<iframe className="interview-resume-preview" src={url} title={`${value.resumeFileName} 在线预览`}/>;
}
