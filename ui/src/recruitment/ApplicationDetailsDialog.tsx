import './ApplicationDetailsDialog.css';
import { SharedApplicationHistory } from './SharedApplicationHistory';
import { ApplicationMaterials } from './ApplicationMaterials';
import { interviewApi } from '../interview/api';
import { InterviewMaterials } from '../interview/InterviewMaterials';
import { ApplicationInterviewFeedbacks } from './ApplicationInterviewFeedbacks';
import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { LongTextPreview } from '../components/LongTextPreview';
import { useMutation,useQuery,useQueryClient } from '@tanstack/react-query';
import { App,Button,Card,Descriptions,Form,Input,Select,Space,Tag,Timeline } from 'antd';
import { useState, type ReactNode } from 'react';
import { ApiProblemError } from '../api/problem';
import { useSession } from '../auth/session';
import { WorkspaceDialog as Modal } from '../components/WorkspaceDialog';
import { CandidateName,JobName,PeopleSelect,TechnicalDetails,UserName } from '../display/BusinessReferences';
import { displayEnum,formatDateTime } from '../display/labels';
import { recruitmentApi,type ApplicationDetails,type OfferRecord,type RecruitmentApplication,type WorkflowStep } from '../recruitment/api';
import { canProcessRecruitment,recruitmentProcessingDeniedMessage } from '../recruitment/processingPermissions';
import { currentStageLabel } from './currentStageLabel';
import { interviewStepLabel,stepLabel } from '../recruitment/workflowLabels';
import { compareHistoryTime } from './historyOrder';
import { type ClientSubmission } from '../submission/api';
import { CandidateBlacklistDetails } from '../talent/CandidateBlacklistDetails';

const errorText=(error:unknown)=>error instanceof ApiProblemError?error.problem.detail:error instanceof Error?error.message:'操作失败，请稍后重试。';

const dedicatedStepCodes=new Set(['DECISION','OFFER']);
const duration=(seconds:number)=>seconds<3600?`${Math.floor(seconds/60)} 分钟`:seconds<86400?`${Math.floor(seconds/3600)} 小时`:`${Math.floor(seconds/86400)} 天 ${Math.floor(seconds%86400/3600)} 小时`;

export function ApplicationDrawer({value,loading,error,open,onClose,onChanged,readOnly=false,sharedLibrary=false}:{sharedLibrary?:boolean;value:ApplicationDetails|undefined;loading:boolean;error:unknown;open:boolean;onClose:()=>void;onChanged:()=>Promise<void>;readOnly?:boolean}){
  return <Modal size="wide" title="招聘活动详情" open={open} onCancel={onClose} footer={null}>{error?<Alert occurrence={error} type="error" showIcon title={errorText(error)} action={<Button onClick={()=>void onChanged()}>重试招聘详情</Button>}/>:loading?<Card loading/>:value?<ApplicationContent value={value} onChanged={onChanged} readOnly={readOnly} sharedLibrary={sharedLibrary}/>:null}</Modal>;
}

export function ApplicationContent({value,onChanged,readOnly=false,operationsOnly=false,sharedLibrary=false}:{sharedLibrary?:boolean;value:ApplicationDetails;onChanged:()=>Promise<void>;initialNextAction?:boolean;initialStepId?:string|undefined;readOnly?:boolean;operationsOnly?:boolean}){
  const app=value.application; const {message}=App.useApp(); const {actor}=useSession(); const qc=useQueryClient();
  const hasProcessingAccess=canProcessRecruitment(actor,{ownerUserId:app.ownerUserId,collaboratorUserIds:value.collaboratorUserIds});
  const canProcess=!readOnly&&hasProcessingAccess;
  const [control,setControl]=useState<ControlKind>();
  const [skipTarget,setSkipTarget]=useState<WorkflowStep>();
  const communications=useQuery({queryKey:['application-communications',app.id],queryFn:()=>recruitmentApi.communications(app.id),enabled:!operationsOnly,retry:false});
  const communicationRecords=communications.error instanceof ApiProblemError&&[401,403].includes(communications.error.problem.status)?[]:communications.data;
  const currentStep=value.workflowSteps.filter(step=>['PENDING','IN_PROGRESS'].includes(step.status)).sort((a,b)=>a.sequenceNo-b.sequenceNo)[0];
  const interviews=useQuery({queryKey:['application-interviews',app.id],queryFn:()=>interviewApi.allForApplication(app.id),enabled:!operationsOnly,retry:false});
  const materialInterviews=interviews.error?[]:interviews.data??[];
  const selectedMaterial=materialInterviews.find(item=>item.workflowStepId===currentStep?.id)??[...materialInterviews].sort((a,b)=>b.scheduledStartAt.localeCompare(a.scheduledStartAt))[0];
  const currentOffer=value.offerStatusRecords.at(-1); const latestDecision=value.decisions.at(-1);
  const changed=onChanged;
  const skip=useMutation({mutationFn:({step,reason}:{step:WorkflowStep;reason:string})=>recruitmentApi.skipStep(app.id,step,reason),onSuccess:async()=>{setSkipTarget(undefined);await changed();}});
  const controlMutation=useMutation({mutationFn:async(v:ControlValues)=>{if(control==='collaborators')return recruitmentApi.updateCollaborators(app,v.userIds??[],v.reason);if(control==='transfer')return recruitmentApi.transfer(app,v.targetUserId!,v.reason);if(control==='decision-correction')return recruitmentApi.correctDecision(app.id,latestDecision!.id,v.result!,v.reason,v.clientSubmissionId);if(control==='offer-correction')return recruitmentApi.correctOffer(app.id,currentOffer!.id,{status:v.offerStatus!,terminationReason:v.terminationReason,applicationOutcome:v.applicationOutcome,reason:v.reason});throw new Error('未选择受控操作');},onSuccess:async()=>{message.success('受控操作已完成并追加历史');setControl(undefined);await changed();await qc.invalidateQueries({queryKey:['demand']});}});
  return <Space className="application-detail" orientation="vertical" size={20} style={{width:'100%'}}>
    {!readOnly&&!hasProcessingAccess?<Alert type="info" showIcon title={sharedLibrary?'当前为共享查看':'当前为管理查看'} description={sharedLibrary?'此候选人已进入共享简历库，所有系统内登录用户均可完整查看相关资料和流程记录；修改资料、推进流程仍遵守原有权限规则。':recruitmentProcessingDeniedMessage}/>:null}
    {!operationsOnly?<>
      <section className="application-profile" aria-label="招聘活动摘要">
        <div className="application-profile-person"><span className="application-profile-name"><CandidateName id={app.candidateId}/></span><div className="application-profile-job"><JobName jdId={app.jdId} demandId={app.demandId} jdVersionId={sharedLibrary?app.jdVersionId:undefined}/></div></div>
        <div className="application-profile-status"><Space wrap><Tag color="blue">{currentStageLabel(app,value.workflowSteps)}</Tag><Tag>{app.recruitmentType==='EXTERNAL'?'外部招聘':'内部招聘'}</Tag>{app.recruitmentType==='EXTERNAL'?<span>{app.clientName??'甲方信息待核对'}</span>:null}</Space><small>招聘历时：{duration(value.elapsedSeconds)}</small></div>
      </section>
      <div className="application-detail-heading">
        <CandidateBlacklistDetails candidateId={app.candidateId} sharedLibrary={sharedLibrary}/>
        <section className="application-materials" aria-label="面试材料">
          <h2>面试材料</h2>
          {interviews.isPending?<span role="status">正在读取面试材料入口…</span>:interviews.error?<Alert type="error" title="面试记录读取失败" action={<Button onClick={()=>void interviews.refetch()}>重试面试记录</Button>}/>:selectedMaterial?<InterviewMaterials id={selectedMaterial.id} scope="application"/>:<ApplicationMaterials application={app}/>}
        </section>
      </div>
    </>:null}
    {!operationsOnly?<ApplicationInterviewFeedbacks sharedLibrary={sharedLibrary} applicationId={app.id} recruitmentType={app.recruitmentType}>
      {communications.isPending?<p role="status">正在读取沟通情况…</p>:null}
      {communications.error?<Alert type="error" title="沟通记录读取失败" action={<Button onClick={()=>void communications.refetch()}>重试沟通记录</Button>}/>:null}
      {communicationRecords?.length?<Timeline style={{marginTop:16}} items={[...communicationRecords].sort((left,right)=>compareHistoryTime(left.communicatedAt,right.communicatedAt)).map(record=>({key:record.id,content:<div><b>沟通情况</b> · {formatDateTime(record.communicatedAt)} · <UserName id={record.recordedByUserId}/><p><LongTextPreview text={record.summary} label="沟通情况"/></p>{sharedLibrary?<><p>方式：{displayEnum(record.channel)} · 结果：{displayEnum(record.result)}</p>{record.nextPlan?<p>下一步：<LongTextPreview text={record.nextPlan} label="下一步计划"/></p>:null}</>:null}</div>}))}/>:null}
    </ApplicationInterviewFeedbacks>:null}
    {sharedLibrary&&!operationsOnly?<SharedApplicationHistory value={value}/>:null}
    {canProcess&&actor?.superAdmin&&currentStep&&!dedicatedStepCodes.has(currentStep.stepCode)?<Card size="small" title="流程纠错"><Button size="small" danger onClick={()=>setSkipTarget(currentStep)}>管理员跳过</Button></Card>:null}
    <Card size="small" title="责任与协作" extra={readOnly||app.terminalAt?null:<Space wrap><Button size="small" onClick={()=>setControl('collaborators')}>调整协作人</Button><Button size="small" onClick={()=>setControl('transfer')}>转交负责人</Button></Space>}><Descriptions size="small" column={{xs:1,md:2}} items={[{key:'owner',label:'负责人',children:<UserName id={app.ownerUserId}/>},{key:'collaborators',label:'协作人',children:value.collaboratorUserIds.length?<Space wrap>{value.collaboratorUserIds.map(id=><UserName key={id} id={id}/>)}</Space>:'无'}]}/></Card>
    {!operationsOnly?<TechnicalDetails items={[["应聘记录",app.id],["岗位版本",app.jdVersionId],["简历版本",app.resumeVersionId],["流程模板",app.workflowTemplateVersionId],["评分配置",app.matchPlanVersionId]]}/>:null}
    <StepSkipModal step={skipTarget} loading={skip.isPending} error={skip.error} onClose={()=>setSkipTarget(undefined)} onSubmit={reason=>skip.mutate({step:skipTarget!,reason})}/>
    <ControlModal kind={control} value={value} loading={controlMutation.isPending} error={controlMutation.error} onClose={()=>setControl(undefined)} onSubmit={v=>controlMutation.mutate(v)}/>
  </Space>;
}

function StepSkipModal({step,loading,error,onClose,onSubmit}:{step:WorkflowStep|undefined;loading:boolean;error:unknown;onClose:()=>void;onSubmit:(reason:string)=>void}){
  const [form]=Form.useForm<{reason:string}>();
  return <Modal okText="确认跳过" title={`管理员纠正错录步骤 · ${stepLabel[step?.stepCode??'']??''}`} open={Boolean(step)} onCancel={onClose} confirmLoading={loading} onOk={()=>form.validateFields().then(v=>onSubmit(v.reason))} afterOpenChange={open=>{if(open)form.setFieldsValue({reason:'管理员核验后纠正错录步骤'});else form.resetFields();}} destroyOnHidden>
    {error?<Alert occurrence={error} type="error" showIcon title={errorText(error)}/>:null}
    <Alert type="warning" showIcon title="“跳过”仅用于超级管理员纠正错录" description="该操作会保留步骤与原因、推进当前应聘流程，不能替代正常的完成、失败或候选人退出记录。"/>
    <Form form={form} layout="vertical"><Form.Item name="reason" label="纠正原因" rules={[{required:true},{max:500}]}><Input.TextArea rows={3}/></Form.Item></Form>
  </Modal>;
}

type RiskFormValues={educationFactId?:string;verificationStatus?:string;studyMode?:string;reasonCode?:string;reasonDetail?:string;sourceChannel?:string;evidenceReference?:string;note?:string};
export function RiskFactModal({kind,educations,factsLoading=false,factsError,onRetryFacts,loading,error,onClose,onSubmit}:{kind:'education'|'blacklist'|undefined;educations:Array<{id:string;school:string|null;degreeName:string|null;major:string|null}>;factsLoading?:boolean;factsError?:unknown;onRetryFacts?:()=>void;loading:boolean;error:unknown;onClose:()=>void;onSubmit:(v:RiskFormValues)=>void}){
  const [form]=Form.useForm<RiskFormValues>();
  const missingFacts=factsError instanceof ApiProblemError&&factsError.problem.status===404;
  return <Modal title={kind==='blacklist'?'加入公司级黑名单':'记录学历核验与学习形式'} open={Boolean(kind)} onCancel={onClose} confirmLoading={loading} okButtonProps={{danger:kind==='blacklist',disabled:kind==='education'&&(factsLoading||Boolean(factsError)||!educations.length)}} onOk={()=>form.validateFields().then(onSubmit)} afterOpenChange={open=>{if(open)form.setFieldsValue(kind==='blacklist'?{reasonCode:'COMPLIANCE_RISK',sourceChannel:'MANUAL'}:{verificationStatus:'UNVERIFIED',studyMode:'UNKNOWN',sourceChannel:'MANUAL'});else form.resetFields();}} destroyOnHidden>
    {error?<Alert occurrence={error} type="error" showIcon title={errorText(error)}/>:null}
    {kind==='education'?(factsLoading?<p role="status">正在读取简历学历事实…</p>:factsError&&!missingFacts?<Alert type="error" showIcon title={errorText(factsError)} action={<Button onClick={onRetryFacts}>重试学历事实</Button>}/>:!educations.length?<Alert type="info" showIcon title="当前简历版本没有可核验的学历事实" description="请先完成简历解析或核对已有事实，再记录学历核验。"/>:null):null}
    {kind==='blacklist'?<Alert type="error" showIcon title="黑名单简历在流程结束后进入共享简历库并保留标签" description="保留原因与来源；标签生效期间简历在流程结束后进入共享简历库，不进入个人待筛选，招聘推进仍受黑名单限制。"/>:<Alert type="info" showIcon title="学历与学习形式不进入专业适配分" description="本记录只作为受控业务事实追加保存；UNKNOWN 不会被系统猜测或折算为分数。"/>}
    <Form form={form} layout="vertical">
      {kind==='blacklist'?<><Form.Item name="reasonCode" label="受控原因代码" rules={[{required:true},{max:60}]}><Input/></Form.Item><Form.Item name="reasonDetail" label="完整原因" rules={[{required:true},{max:1000}]}><Input.TextArea rows={3}/></Form.Item></>:<><Form.Item name="educationFactId" label="简历学历事实" rules={[{required:true}]}><Select loading={factsLoading} disabled={factsLoading||Boolean(factsError)||!educations.length} notFoundContent="当前版本暂无学历事实" options={educations.map(v=>({value:v.id,label:`${v.school??'学校未识别'} · ${v.degreeName??'学历未识别'} · ${v.major??'专业未识别'}`}))}/></Form.Item><Form.Item name="verificationStatus" label="核验状态" rules={[{required:true}]}><Select options={['UNVERIFIED','IN_PROGRESS','VERIFIED','MISMATCH','UNABLE_TO_VERIFY'].map(v=>({value:v,label:displayEnum(v)}))}/></Form.Item><Form.Item name="studyMode" label="学习形式" rules={[{required:true}]}><Select options={['UNKNOWN','FULL_TIME','PART_TIME','CORRESPONDENCE','OTHER'].map(v=>({value:v,label:displayEnum(v)}))}/></Form.Item><Form.Item name="note" label="核验备注" rules={[{max:1000}]}><Input.TextArea rows={2}/></Form.Item></>}
      <Form.Item name="sourceChannel" label="事实来源" rules={[{required:true},{max:40}]}><Select options={['MANUAL','DOCUMENT','CLIENT_SYSTEM','PHONE','EMAIL','OTHER'].map(v=>({value:v,label:displayEnum(v)}))}/></Form.Item><Form.Item name="evidenceReference" label="证据引用" rules={kind==='blacklist'?[{required:true},{max:1000}]:[{max:1000}]}><Input.TextArea rows={2} placeholder="文件编号、沟通记录或外部系统引用"/></Form.Item>
    </Form>
  </Modal>;
}

type ControlKind='collaborators'|'transfer'|'decision-correction'|'offer-correction';
type ControlValues={userIds?:string[];targetUserId?:string;result?:string;clientSubmissionId?:string|undefined;offerStatus?:string;terminationReason?:string;applicationOutcome?:string;reason:string};
function ControlModal({kind,value,loading,error,onClose,onSubmit}:{kind:ControlKind|undefined;value:ApplicationDetails;loading:boolean;error:unknown;onClose:()=>void;onSubmit:(v:ControlValues)=>void}){
  const [form]=Form.useForm<ControlValues>(); const offerStatus=Form.useWatch('offerStatus',form); const terminationReason=Form.useWatch('terminationReason',form); const latestOffer=value.offerStatusRecords.at(-1); const latestDecision=value.decisions.at(-1);
  const titles:Record<ControlKind,string>={collaborators:'调整招聘活动协作人',transfer:'转交招聘活动负责人','decision-correction':'追加决定更正','offer-correction':'追加 Offer 更正'};
  return <Modal okText="确认变更" title={kind?titles[kind]:''} open={Boolean(kind)} onCancel={onClose} confirmLoading={loading} onOk={()=>form.validateFields().then(onSubmit)} afterOpenChange={open=>{if(open&&kind)form.setFieldsValue(kind==='collaborators'?{userIds:value.collaboratorUserIds,reason:'根据实际协作安排调整 Application 协作人'}:kind==='transfer'?{reason:'根据招聘责任调整转交负责人'}:kind==='decision-correction'?{...(latestDecision?{result:latestDecision.decision}:{}),...(latestDecision?.clientSubmissionId?{clientSubmissionId:latestDecision.clientSubmissionId}:{}),reason:'核验原决定事实后追加更正'}:{...(latestOffer&&latestOffer.status!=='ACCEPTED'?{offerStatus:latestOffer.status}:{}),...(latestOffer?.terminationReason?{terminationReason:latestOffer.terminationReason}:{}),...(latestOffer?.applicationOutcome?{applicationOutcome:latestOffer.applicationOutcome}:{}),reason:'核验原 Offer 事实后追加更正'});if(!open)form.resetFields();}} destroyOnHidden>
    {error?<Alert occurrence={error} type="error" showIcon title={errorText(error)}/>:null}
    <Form form={form} layout="vertical">
      {kind==='collaborators'?<Form.Item name="userIds" label="协作人"><PeopleSelect mode="multiple" aria-label="协作人"/></Form.Item>:null}
      {kind==='transfer'?<Form.Item name="targetUserId" label="新负责人" rules={[{required:true}]}><PeopleSelect aria-label="新负责人"/></Form.Item>:null}
      {kind==='decision-correction'?<><Form.Item name="result" label="更正后的人工决定" rules={[{required:true}]}><Select options={['PASS','REJECT','WITHDRAW'].map(v=>({value:v,label:displayEnum(v)}))}/></Form.Item></>:null}
      {kind==='offer-correction'?<><Form.Item name="offerStatus" label="更正后的当前状态" rules={[{required:true}]}><Select options={['NOT_STARTED','IN_PROGRESS','ISSUED','TERMINATED'].map(v=>({value:v,label:displayEnum(v)}))}/></Form.Item>{offerStatus==='TERMINATED'?<><Form.Item name="terminationReason" label="终止原因" rules={[{required:true}]}><Select options={['CANDIDATE_DECLINED','EXPIRED','COMPANY_WITHDREW','OTHER'].map(v=>({value:v,label:displayEnum(v)}))}/></Form.Item>{terminationReason==='OTHER'?<Form.Item name="applicationOutcome" label="Application 结果" rules={[{required:true}]}><Select options={['WITHDRAW','REJECT'].map(v=>({value:v,label:displayEnum(v)}))}/></Form.Item>:null}</>:null}</>:null}
      <Form.Item name="reason" label="操作依据" rules={[{required:true},{max:500}]}><Input.TextArea rows={3}/></Form.Item>
    </Form>
  </Modal>;
}

type ActionValues={result?:string;reason:string;channel?:string;summary?:string;nextPlan?:string;clientSubmissionId?:string|undefined;offerStatus?:string;terminationReason?:string;applicationOutcome?:string};
export function ActionModal({value,currentOffer,loading,error,onClose,onSubmit,application,dialogTitle,introduction}:{dialogTitle?:string;introduction?:ReactNode;value:{kind:'step'|'decision'|'offer'|'onboard';step?:WorkflowStep}|undefined;external:boolean;submissions:ClientSubmission[];currentOffer:OfferRecord|undefined;loading:boolean;error:unknown;onClose:()=>void;onSubmit:(v:ActionValues)=>void;application?:RecruitmentApplication}){
  const [form]=Form.useForm<ActionValues>(); const offerStatus=Form.useWatch('offerStatus',form); const terminationReason=Form.useWatch('terminationReason',form); const title=value?.kind==='step'?`完成当前步骤 · ${interviewStepLabel({stepCode:value.step?.stepCode??'',roundNo:value.step?.roundNo},application?.recruitmentType)}`:value?.kind==='decision'?'记录人工招聘决定':value?.kind==='offer'?'记录 Offer 进展':'确认入职';
  const communicationStep=value?.kind==='step'&&value.step?.stepCode==='CANDIDATE_COMMUNICATION';
  const offerOptions=!currentOffer||currentOffer.status==='NOT_STARTED'?['ISSUED','IN_PROGRESS','TERMINATED']:currentOffer.status==='IN_PROGRESS'?['ISSUED','TERMINATED']:['TERMINATED'];
  return <Modal title={dialogTitle??title} open={Boolean(value)} onCancel={onClose} confirmLoading={loading} onOk={()=>form.validateFields().then(onSubmit)} afterOpenChange={open=>{if(open)form.setFieldsValue(value?.kind==='step'?{...(communicationStep?{channel:value.step?.defaultFollowUpChannel??'OTHER'}:{}),reason:'',nextPlan:''}:value?.kind==='decision'?{reason:''}:value?.kind==='offer'?{offerStatus:offerOptions[0]??'TERMINATED',reason:'HR 核验后推进 Offer 状态'}:{reason:'HR 已核验候选人真实入职事实'});else form.resetFields();}} destroyOnHidden>{introduction}{error?<Alert occurrence={error} type="error" showIcon title={errorText(error)}/>:null}<Form form={form} layout="vertical">
    {value?.kind==='step'?<>{communicationStep?<><Form.Item name="channel" label="沟通方式（选填）"><Select options={['PHONE','EMAIL','WECHAT','ENTERPRISE_WECHAT','IN_PERSON','OTHER'].map(v=>({value:v,label:displayEnum(v)}))}/></Form.Item><Form.Item name="nextPlan" label="下一步计划（选填）" rules={[{max:500}]}><Input.TextArea rows={2}/></Form.Item></>:null}<Form.Item name="result" label="步骤结果" rules={[{required:true}]}><Select options={['PASSED','FAILED','WITHDRAWN'].map(v=>({value:v,label:displayEnum(v)}))}/></Form.Item></>:null}
    {value?.kind==='decision'?<><Form.Item name="result" label="人工决定" rules={[{required:true}]}><Select options={[{value:'PASS',label:'通过 · 进入录用沟通'},{value:'REJECT',label:'未通过 · 结束本次应聘'},{value:'WITHDRAW',label:'候选人退出'}]}/></Form.Item></>:null}
    {value?.kind==='offer'?<><p role="status">{offerStatus==='TERMINATED'?'终止 Offer；未真实入职不会占用 HC。':offerStatus==='ISSUED'?'Offer 发出不占用 HC；确认实际入职时才占用。':'处理中不占用 HC。'}</p><Form.Item name="offerStatus" label="新状态" rules={[{required:true}]}><Select options={offerOptions.map(v=>({value:v,label:displayEnum(v)}))}/></Form.Item>{offerStatus==='TERMINATED'?<><Form.Item name="terminationReason" label="终止原因" rules={[{required:true}]}><Select options={['CANDIDATE_DECLINED','EXPIRED','COMPANY_WITHDREW','OTHER'].map(v=>({value:v,label:displayEnum(v)}))}/></Form.Item>{terminationReason==='OTHER'?<Form.Item name="applicationOutcome" label="Application 结果" rules={[{required:true}]}><Select options={['WITHDRAW','REJECT'].map(v=>({value:v,label:displayEnum(v)}))}/></Form.Item>:null}</>:null}</>:null}
    <Form.Item name="reason" label={value?.kind==='step'?(communicationStep?'实际沟通情况（选填）':'结果备注（选填）'):'事实与原因'} rules={[{required:value?.kind!=='step'},{max:500}]}><Input.TextArea rows={3}/></Form.Item>
  </Form></Modal>;
}

export function ApplicationDetailsDialog({applicationId,onClose,readOnly=false,sharedLibrary=false}:{sharedLibrary?:boolean;applicationId?:string|undefined;onClose:()=>void;readOnly?:boolean}) {
  const client=useQueryClient();
  const detail=useQuery({queryKey:['application',applicationId],queryFn:()=>recruitmentApi.application(applicationId!),enabled:Boolean(applicationId)});
  const refresh=async()=>{await Promise.all([['application',applicationId],['applications'],['interviews'],['follow-up-plans'],['company-blacklist'],['company-blacklist-affected']].map(queryKey=>client.invalidateQueries({queryKey})));};
  return <ApplicationDrawer key={applicationId} value={detail.data} loading={detail.isPending} error={detail.error} open={Boolean(applicationId)} onClose={onClose} onChanged={refresh} readOnly={readOnly} sharedLibrary={sharedLibrary}/>;
}
