import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { App, Button, Card, Descriptions, Empty, Form, Input, Select, Space, Table, Tag, Typography } from 'antd';
import { lazy, Suspense, useState } from 'react';
import { ApiProblemError } from '../api/problem';
import { canManageRisk, canReadRisk } from '../auth/permissions';
import { useSession } from '../auth/session';
import { WorkspaceDialog } from '../components/WorkspaceDialog';
import { JobName, TechnicalDetails, UserName } from '../display/BusinessReferences';
import { displayEnum, formatDateTime } from '../display/labels';
import { riskApi, type BlacklistCommand, type BlacklistRecord } from './riskApi';

const errorText=(error:unknown)=>error instanceof ApiProblemError?error.problem.detail:'风险记录读取或保存失败，请重试。';
const recordLabel:Record<string,string>={ACTIVATION:'新增',CORRECTION:'追加更正',DISABLE:'停用'};
const ApplicationDetailsDialog = lazy(() => import('../recruitment/ApplicationDetailsDialog').then(module => ({ default: module.ApplicationDetailsDialog })));

export function CompanyBlacklistPanel({candidateId,active=true,readOnly=false,sharedLibrary=false}:{candidateId:string;active?:boolean;readOnly?:boolean;sharedLibrary?:boolean}) {
  const {actor}=useSession();
  return sharedLibrary||canReadRisk(actor)?<BlacklistContent candidateId={candidateId} active={active} sharedLibrary={sharedLibrary} canManage={!readOnly&&canManageRisk(actor)}/>:null;
}

function BlacklistContent({candidateId,active,canManage,sharedLibrary}:{candidateId:string;active:boolean;canManage:boolean;sharedLibrary:boolean}) {
  const qc=useQueryClient(); const {message}=App.useApp();
  const [selectedApplication,setSelectedApplication]=useState<string>();
  const [edit,setEdit]=useState<{kind:'correct'|'disable';record:BlacklistRecord}>();
  const history=useInfiniteQuery({queryKey:['company-blacklist',candidateId],initialPageParam:undefined as string|undefined,
    queryFn:({pageParam})=>riskApi.history(candidateId,pageParam),getNextPageParam:page=>page.records.hasMore?page.records.nextCursor??undefined:undefined,enabled:active,retry:false});
  const current=history.data?.pages[0]?.current;
  const blocking=history.data?.pages[0]?.active;
  const affected=useInfiniteQuery({queryKey:['company-blacklist-affected',current?.id],initialPageParam:undefined as string|undefined,
    queryFn:({pageParam})=>riskApi.affected(current!.id,pageParam),getNextPageParam:page=>page.hasMore?page.nextCursor??undefined:undefined,enabled:active&&Boolean(current&&blocking),retry:false});
  const save=useMutation({mutationFn:async(values:BlacklistCommand&{reason:string})=>{
    if(!edit)throw new Error('未选择风险记录');
    return edit.kind==='correct'?riskApi.correct(edit.record.id,values):riskApi.disable(edit.record.id,values.reason);
  },onSuccess:async()=>{setEdit(undefined);message.success('风险事实已追加保存，原始记录保留');await refresh();}});
  const refresh=async()=>{await Promise.all([
    qc.invalidateQueries({queryKey:['candidate-blacklist-tags']}),qc.invalidateQueries({queryKey:['company-blacklist']}),qc.invalidateQueries({queryKey:['company-blacklist-affected']}),
    qc.invalidateQueries({queryKey:['candidate-restrictions',candidateId]}),qc.invalidateQueries({queryKey:['submission-eligibility']}),
    qc.invalidateQueries({queryKey:['application']}),qc.invalidateQueries({queryKey:['applications']}),
  ]);};
  return <Card title="公司级黑名单与处置历史" size="small" extra={<Button onClick={()=>void refresh()} loading={history.isFetching}>刷新风险记录</Button>}>
    <Space orientation="vertical" style={{width:'100%'}} size={12}>
      {history.error?<Alert occurrence={history.error} type="error" showIcon title={errorText(history.error)} action={<Button onClick={()=>void history.refetch()}>重试风险记录</Button>}/>:history.isPending?<p role="status">正在读取风险记录…</p>:<>
        <Alert showIcon type={blocking?'error':'info'} title={blocking?'黑名单标签生效中，简历在流程结束后进入共享简历库':current?'公司黑名单已停用':'无公司级黑名单记录'} description={blocking?'简历在流程结束后进入共享简历库并保留标签，不进入个人待筛选，招聘推进仍受黑名单限制；历史招聘与处置记录继续保留。':current?'停用不会自动恢复已终止的招聘活动；共享准入会按其他现行事实重新计算。':'如需新增，请在招聘活动的风险面板填写原因、来源与证据。'}/>
        {current?<><Descriptions size="small" column={1} items={[{key:'reason',label:'当前记录原因',children:current.reasonDetail},{key:'evidence',label:'证据引用',children:current.evidenceReference},{key:'source',label:'来源',children:displayEnum(current.sourceChannel)},{key:'actor',label:'记录人 / 时间',children:<><UserName id={current.recordedByUserId}/> · {formatDateTime(current.createTime)}</>}]}/>
          {blocking&&canManage?<Space><Button onClick={()=>{save.reset();setEdit({kind:'correct',record:current});}}>更正黑名单记录</Button><Button onClick={()=>{save.reset();setEdit({kind:'disable',record:current});}}>停用黑名单</Button></Space>:null}</>:null}
        {blocking&&current?<section aria-label="受影响流程清单"><Typography.Title level={5}>历史关联的活动流程与推荐</Typography.Title>
          {affected.error?<Alert occurrence={affected.error} type="error" showIcon title={errorText(affected.error)} action={<Button onClick={()=>void affected.refetch()}>重试影响清单</Button>}/>:null}
          <Table rowKey={row=>`${row.resourceType}-${row.resourceId}`} loading={affected.isPending} pagination={false} scroll={{x:640}} dataSource={affected.data?.pages.flatMap(page=>page.items)??[]} locale={{emptyText:<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前没有待处置的活动流程或推荐"/>}} columns={[
            {title:'业务对象',render:(_,row)=><Space orientation="vertical" size={0}><Tag>{row.resourceType==='APPLICATION'?'招聘活动':'客户推荐'}</Tag><JobName jdId={row.jdId}/><small>{formatDateTime(row.updateTime)}</small></Space>},
            {title:'状态',render:(_,row)=>row.status.split('/').map(status=>displayEnum(status)).join(' / ')},
            {title:'负责人',render:(_,row)=><UserName id={row.ownerUserId}/>},
            {title:'处置', className: 'table-action-cell',render:(_,row)=><><Button type="link" onClick={()=>setSelectedApplication(row.applicationId)}>查看招聘活动</Button><TechnicalDetails items={[["资源",row.resourceId],["招聘活动",row.applicationId]]}/></>},
          ]}/>{affected.hasNextPage?<Button loading={affected.isFetchingNextPage} onClick={()=>void affected.fetchNextPage()}>加载更多受影响记录</Button>:null}
        </section>:null}
        <details><summary>查看不可变更历史（已加载 {history.data?.pages.flatMap(page=>page.records.items).length??0} 条）</summary>
          {history.data?.pages.flatMap(page=>page.records.items).map(record=><Card key={record.id} size="small" title={`${recordLabel[record.recordType]??record.recordType} · ${formatDateTime(record.createTime)}`}><p>{record.reasonDetail}</p><p>来源：{displayEnum(record.sourceChannel)} · 证据：{record.evidenceReference}</p><p>操作人：<UserName id={record.recordedByUserId}/> · 事实发生于 {formatDateTime(record.occurredAt)}</p><TechnicalDetails items={[["记录",record.id],["更正或停用的原记录",record.correctsRecordId]]}/></Card>)}
          {history.hasNextPage?<Button loading={history.isFetchingNextPage} onClick={()=>void history.fetchNextPage()}>加载更多风险历史</Button>:null}
        </details>
      </>}
    </Space>
    {selectedApplication?<Suspense fallback={<p role="status">正在加载招聘详情…</p>}><ApplicationDetailsDialog sharedLibrary={sharedLibrary} applicationId={selectedApplication} onClose={()=>setSelectedApplication(undefined)}/></Suspense>:null}
    {edit?<BlacklistEdit key={`${edit.kind}-${edit.record.id}`} kind={edit.kind} record={edit.record} saving={save.isPending} error={save.error} onClose={()=>setEdit(undefined)} onSave={values=>save.mutate(values)}/>:null}
  </Card>;
}

function BlacklistEdit({kind,record,saving,error,onClose,onSave}:{kind:'correct'|'disable';record:BlacklistRecord;saving:boolean;error:unknown;onClose:()=>void;onSave:(values:BlacklistCommand&{reason:string})=>void}) {
  const [form]=Form.useForm<BlacklistCommand&{reason:string}>();
  return <WorkspaceDialog open title={kind==='correct'?'追加更正黑名单':'停用公司黑名单'} onCancel={onClose} confirmLoading={saving} onOk={()=>form.submit()}>
    <Alert type="info" showIcon title="原记录及操作者、原因和时间会永久保留"/>{error?<Alert occurrence={error} type="error" showIcon title={errorText(error)}/>:null}
    <Form form={form} layout="vertical" initialValues={record} onFinish={onSave}>
      {kind==='disable'?<Form.Item name="reason" label="停用原因" rules={[{required:true,whitespace:true},{max:1000}]}><Input.TextArea rows={3}/></Form.Item>:<>
        <Form.Item name="reasonCode" label="原因代码" rules={[{required:true,whitespace:true},{max:60}]}><Input/></Form.Item>
        <Form.Item name="reasonDetail" label="更正后的完整原因" rules={[{required:true,whitespace:true},{max:1000}]}><Input.TextArea rows={3}/></Form.Item>
        <Form.Item name="sourceChannel" label="事实来源" rules={[{required:true}]}><Select options={['MANUAL','DOCUMENT','CLIENT_SYSTEM','PHONE','EMAIL','OTHER'].map(value=>({value,label:displayEnum(value)}))}/></Form.Item>
        <Form.Item name="evidenceReference" label="证据引用" rules={[{required:true,whitespace:true},{max:1000}]}><Input.TextArea rows={2}/></Form.Item>
      </>}
    </Form>
  </WorkspaceDialog>;
}
