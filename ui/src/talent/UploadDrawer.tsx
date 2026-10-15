import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { InboxOutlined, CheckCircleOutlined, ExclamationCircleOutlined } from '@ant-design/icons';
import { useMutation, useQuery } from '@tanstack/react-query';
import { App, Button, Drawer, Progress, Select, Tag, Upload } from 'antd';
import type { UploadFile } from 'antd';
import { useState } from 'react';
import { demandApi, type JdSummary } from '../demand/api';
import { JdSelector } from '../demand/JdSelector';
import { MATCH_CANDIDATE_LIMIT } from '../matching/selectionCandidates';
import { ApiProblemError } from '../api/problem';
import { talentApi, type SourceChannel } from './api';
import { createIdempotencyKey } from '../api/idempotency';

import './upload-drawer.css';

type UploadOutcomeStatus='SUCCEEDED'|'FAILED';
interface UploadOutcome { text:string; status:UploadOutcomeStatus; scope:'FILE'|'BATCH' }

const errorMessage=(error:unknown)=>error instanceof ApiProblemError?error.problem.detail:error instanceof Error?error.message:'操作失败，请稍后重试。';
const MAX_RESUME_FILE_SIZE_BYTES=10*1024*1024;
const RESUME_FILE_NAME_PATTERN=/\.(pdf|docx?)$/i;
const BROWSER_UPLOAD_CONCURRENCY=8;

function resumeFileValidationMessage(file:File){
  if(!RESUME_FILE_NAME_PATTERN.test(file.name))return '仅支持 PDF、DOCX 或 DOC 格式的简历文件。';
  if(file.size===0)return '简历文件不能为空。';
  if(file.size>MAX_RESUME_FILE_SIZE_BYTES)return '每份简历不能超过 10 MB。';
  return undefined;
}

interface UploadSession {
  createKey:string;
  batchId?:string;
  channelId:string;
  jdVersionId:string;
  fileKeys:Map<File,string>;
  acceptedOutcomes:UploadOutcome[];
}

export function UploadDrawer({open,channels,onClose,onSaved,onReview}:{open:boolean;channels:SourceChannel[];onClose:()=>void;onSaved:(hasFailures:boolean,batchId?:string,completed?:boolean)=>Promise<unknown>;onReview?:(batchId?:string)=>void}){
  const {message}=App.useApp();
  const [channelId,setChannelId]=useState<string>();
  const [jd,setJd]=useState<JdSummary>();
  const versions=useQuery({queryKey:['demand','jd-versions-by-id',jd?.id],queryFn:()=>demandApi.jdVersions(jd!.id),enabled:Boolean(open&&jd)});
  const jdVersionId=versions.data?.items.find(value=>value.versionNo===jd?.currentVersionNo)?.id;
  const [progress,setProgress]=useState(0);
  const [files,setFiles]=useState<UploadFile[]>([]);
  const [result,setResult]=useState<UploadOutcome[]>();
  const [resultBatchId,setResultBatchId]=useState<string>();
  const [session,setSession]=useState<UploadSession>();
  const upload=useMutation({
    mutationFn:async(selection:{session:UploadSession;files:File[]})=>{
      const current=selection.session;
      const batchId=current.batchId??(await talentApi.createBatch(current.channelId,current.jdVersionId,current.createKey)).id;
      setSession({...current,batchId});
      const outcomes:UploadOutcome[]=[...current.acceptedOutcomes]; const acceptedOutcomes=[...current.acceptedOutcomes]; const failedFiles:File[]=[]; let hasFailures=current.acceptedOutcomes.some(value=>value.status==='FAILED'); let processed=0;
      setProgress(0);
      let nextIndex=0;
      const processFile=async()=>{while(nextIndex<selection.files.length){
        const file=selection.files[nextIndex++];
        if(!file)return;
        try{
          const intake=await talentApi.upload(batchId,file,current.fileKeys.get(file));
          if(intake.scanStatus==='MALICIOUS'||intake.scanStatus==='UNAVAILABLE'){
            hasFailures=true;
            const reason=intake.scanStatus==='MALICIOUS'?'安全校验未通过，文件已隔离':'安全扫描暂不可用';
            const outcome:UploadOutcome={text:`${file.name}：已接收，${reason}。请到解析校验处理，无需重新上传。`,status:'FAILED',scope:'FILE'};
            outcomes.push(outcome);acceptedOutcomes.push(outcome);
          }else{
            const outcome:UploadOutcome={text:`${file.name}：上传成功`,status:'SUCCEEDED',scope:'FILE'};
            outcomes.push(outcome);acceptedOutcomes.push(outcome);
          }
        }
        catch(error){hasFailures=true;outcomes.push({text:`${file.name}：上传失败 · ${errorMessage(error)}`,status:'FAILED',scope:'FILE'});failedFiles.push(file);}
        processed++;setProgress(processed);setResult([...outcomes]);
      }};
      await Promise.all(Array.from({length:Math.min(BROWSER_UPLOAD_CONCURRENCY,selection.files.length)},()=>processFile()));
      setSession({...current,batchId,acceptedOutcomes});
      let completed=false;
      if(!failedFiles.length){
        try{await talentApi.completeBatch(batchId);completed=true;}
        catch(error){hasFailures=true;outcomes.push({text:`批次汇总未完成：${errorMessage(error)}。已接收文件保留在解析校验，请重试完成批次。`,status:'FAILED',scope:'BATCH'});}
      }
      return {outcomes,failedFiles,hasFailures,completed,batchId};
    },
    onSuccess:async values=>{setResult(values.outcomes);setResultBatchId(values.batchId);setFiles(previous=>previous.filter(file=>file.originFileObj instanceof File&&values.failedFiles.includes(file.originFileObj)));if(values.completed){setSession(undefined);setChannelId(undefined);setJd(undefined);setProgress(0);}await onSaved(values.hasFailures,values.batchId,values.completed);},
  });
  const close=()=>{if(upload.isPending)return;if(!session){setChannelId(undefined);setJd(undefined);setProgress(0);setFiles([]);setResult(undefined);setResultBatchId(undefined);upload.reset();}onClose();};
  const start=()=>{const selectedFiles=files.flatMap(item=>item.originFileObj instanceof File?[item.originFileObj]:[]);if(!session&&(!channelId||!jdVersionId||!selectedFiles.length))return;const current=session??{createKey:createIdempotencyKey(),channelId:channelId!,jdVersionId:jdVersionId!,fileKeys:new Map(selectedFiles.map(file=>[file,createIdempotencyKey()])),acceptedOutcomes:[]};setSession(current);setResult(undefined);upload.mutate({session:current,files:selectedFiles});};
  const jdUnavailable=Boolean(jd&&(versions.isPending||versions.isError||!jdVersionId));
  const fileResults=result?.filter(value=>value.scope==='FILE')??[];
  const batchWarnings=result?.filter(value=>value.scope==='BATCH')??[];
  const succeeded=fileResults.filter(value=>value.status==='SUCCEEDED').length;
  const failed=fileResults.filter(value=>value.status==='FAILED').length;
  const needsAttention=failed>0||batchWarnings.length>0;
  const review=()=>{if(!session){setChannelId(undefined);setJd(undefined);setProgress(0);setFiles([]);setResult(undefined);upload.reset();}onReview?.(resultBatchId);};
  return <Drawer title="批量上传简历" className="resume-upload-drawer" open={open} onClose={close} closable={!upload.isPending} maskClosable={!upload.isPending} keyboard={!upload.isPending} size="min(94vw,620px)" extra={<Button type="primary" disabled={upload.isPending||(!session&&(!channelId||!jd||jdUnavailable||!files.length))} loading={upload.isPending} onClick={start}>{session&&!files.length?'重试完成批次':'开始上传'}</Button>}>
    <div className="upload-workspace">
      {upload.error?<Alert occurrence={upload.error} type="error" showIcon title={errorMessage(upload.error)}/>:null}
      <div className="upload-fields">
        <div><label id="upload-jd-label">关联 JD（必选）</label><JdSelector includeSourceJds parsedOnly {...(jd?{value:jd.id}:{})} onChange={setJd} disabled={upload.isPending||Boolean(session)} placeholder="选择已解析成功的 JD"/></div>
        <div><label id="upload-source-label">来源渠道</label><Select aria-label="上传来源渠道" disabled={upload.isPending||Boolean(session)} placeholder="选择来源" value={channelId} onChange={setChannelId} options={channels.filter(v=>v.status==='ACTIVE').map(v=>({label:v.name,value:v.id}))}/></div>
      </div>
      {versions.error?<Alert occurrence={versions.error} type="error" title="岗位版本加载失败" action={<Button onClick={()=>void versions.refetch()}>重试</Button>}/>:null}
      {session&&!upload.isPending?<div className="upload-session-note"><strong>继续完成当前批次</strong><p>岗位和来源已锁定；关闭后可继续，请勿刷新。移除文件只停止重试，接收状态请到解析校验核对。</p></div>:null}
      <Upload.Dragger disabled={upload.isPending} openFileDialogOnClick={!session} multiple accept=".pdf,.doc,.docx" beforeUpload={file=>{if(session)return Upload.LIST_IGNORE;const validation=resumeFileValidationMessage(file);if(validation){void message.error(`${file.name}：${validation}`);return Upload.LIST_IGNORE;}return false;}} fileList={files} maxCount={MATCH_CANDIDATE_LIMIT} onRemove={file=>{if(session){const outcome:UploadOutcome={text:`${file.name}：已停止重试，接收状态未确认，请到解析校验核对。`,status:'FAILED',scope:'FILE'};setSession({...session,acceptedOutcomes:[...session.acceptedOutcomes,outcome]});setResult(previous=>[...(previous??[]),outcome]);}}} onChange={({fileList})=>setFiles(fileList)}><p className="ant-upload-drag-icon"><InboxOutlined/></p><p className="upload-drop-title">{session?'重试剩余文件':'拖入简历，或点击选择文件'}</p><p className="upload-drop-hint">PDF / DOCX / DOC · 每份不超过 10 MB</p></Upload.Dragger>
      {files.length?<div className="upload-selection-count">{session?'待重试':'已选择'} {files.length} 份简历</div>:null}
      {upload.isPending?<div className="upload-progress" role="status" aria-atomic="true"><span>已处理 {progress} / {files.length} 份</span><Progress percent={files.length?Math.round(progress/files.length*100):0} showInfo={false}/></div>:null}
      {result?<section className="upload-results" aria-label="批次处理结果">
        <div className="upload-result-summary" role="status"><span className={needsAttention?'upload-result-warning':'upload-result-success'}>{needsAttention?<ExclamationCircleOutlined/>:<CheckCircleOutlined/>}</span><div><strong>{upload.isPending?'正在上传':'本次上传结果'}</strong><p>解析与评分状态请到解析校验按任务查看。</p></div></div>
        <div className="upload-result-counts" role="group" aria-label={`上传成功 ${succeeded}，上传失败 ${failed}`}>
          <div><span className="upload-count-success">{succeeded}</span><small>成功</small></div>
          <div><span className="upload-count-failed">{failed}</span><small>失败</small></div>
        </div>
        {fileResults.length?<ul className="upload-result-list" aria-label="本次上传文件结果" tabIndex={0}>{fileResults.map((value,index)=><li key={`${value.text}-${index}`}><Tag color={value.status==='SUCCEEDED'?'green':'red'}>{value.status==='SUCCEEDED'?'成功':'失败'}</Tag><span>{value.text}</span></li>)}</ul>:null}
        {batchWarnings.length?<ul className="upload-result-list upload-attention-list" aria-label="批次提醒" tabIndex={0}>{batchWarnings.map((value,index)=><li key={`${value.text}-${index}`}><Tag color="red">批次异常</Tag><span>{value.text}</span></li>)}</ul>:null}
        {onReview?<div className="upload-result-actions"><Button onClick={review}>查看解析校验</Button></div>:null}
      </section>:null}
    </div>
  </Drawer>;
}
