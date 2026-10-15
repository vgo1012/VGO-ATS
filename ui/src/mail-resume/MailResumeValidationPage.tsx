import { scrollableTableHeader } from '../components/scrollableTableHeader';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Card, Checkbox, DatePicker, Form, Input, Modal, Select, Space, Table, Tag, Tooltip, Typography } from 'antd';
import type { Dayjs } from 'dayjs';
import { useDeferredValue, useState } from 'react';
import { ApiProblemError } from '../api/problem';
import { CompactPagination } from '../components/CompactPagination';
import { countPaginationProps } from '../components/countPaginationProps';
import { dateRangeParams } from '../components/dateRange';
import { FeedbackAlert } from '../components/FeedbackAlert';
import { formatDateTime } from '../display/labels';
import { mailResumeApi, type MailJdOption, type MailResumeFilters, type MailResumeReview } from './api';

type FilterForm={title?:string;region?:string;attachment?:string;range?:[Dayjs,Dayjs]};
const errorText=(error:unknown)=>error instanceof ApiProblemError?error.problem.detail:error instanceof Error?error.message:'操作失败，请稍后重试';

function LinkModal({value,onClose}:{value?:MailResumeReview;onClose:()=>void}){
  const {message}=App.useApp(); const client=useQueryClient(); const [jd,setJd]=useState<MailJdOption>(); const [search,setSearch]=useState(''); const [reason,setReason]=useState(''); const [saveRule,setSaveRule]=useState(false); const deferred=useDeferredValue(search);
  const jds=useQuery({queryKey:['mail-resume','jds',deferred],queryFn:()=>mailResumeApi.jds(deferred),enabled:Boolean(value),staleTime:15_000});
  const link=useMutation({mutationFn:()=>mailResumeApi.link(value!.id,{jdId:jd!.id,reason:reason.trim(),saveExactRule:saveRule}),onSuccess:async result=>{await client.invalidateQueries({queryKey:['mail-resume','reviews']});message.success(result.status==='DISPATCH_FAILED'?'JD 已关联，入库启动失败，系统将在下次采集时重试':'JD 已关联，简历已进入自动入库流程');close();},onError:error=>message.error(errorText(error))});
  const close=()=>{if(link.isPending)return;setJd(undefined);setSearch('');setReason('');setSaveRule(false);onClose();};
  return <Modal centered width={560} rootClassName="workspace-dialog-root" className="workspace-dialog" transitionName="workspace-dialog-motion" maskTransitionName="workspace-mask-motion" open={Boolean(value)} title="人工关联当前 JD" okText="确认关联并下发" cancelText="取消" confirmLoading={link.isPending} okButtonProps={{disabled:!jd||!reason.trim()}} onOk={()=>link.mutate()} onCancel={close} destroyOnHidden>
    <Space orientation="vertical" size="middle" style={{width:'100%'}}>
      <Typography.Text className="mail-resume-cell">附件：{value?.attachmentName??'未识别附件名'}</Typography.Text>
      <Select showSearch filterOption={false} searchValue={search} onSearch={setSearch} onChange={id=>setJd(jds.data?.find(item=>item.id===id))} value={jd?.id} loading={jds.isFetching} placeholder="搜索 JD 编号或岗位名称" aria-label="选择当前 JD" options={(jds.data??[]).map(item=>({value:item.id,label:`${item.code??'无编号'} · ${item.title} · V${item.currentVersionNo}${item.regions.length?` · ${item.regions.join('、')}`:''}`}))}/>
      {jds.error?<FeedbackAlert type="error" title="JD 列表加载失败" description={errorText(jds.error)} action={<Button size="small" onClick={()=>void jds.refetch()}>重试</Button>}/>:null}
      <Input.TextArea maxLength={500} showCount value={reason} onChange={event=>setReason(event.target.value)} placeholder="填写人工关联依据（必填）" aria-label="处理原因"/>
      <Checkbox checked={saveRule} disabled={!value?.parsedJdTitle||!value?.parsedRegion} onChange={event=>setSaveRule(event.target.checked)}>为完全相同的岗位文本和区域保存精确路由规则</Checkbox>
      <FeedbackAlert type="warning" title="关联后不可改绑" description="服务端会绑定提交时的当前 JD 版本和负责人；JD 已关闭或负责人无效时会拒绝下发。"/>
    </Space>
  </Modal>;
}

export function MailResumeValidationPage(){
  const [pageSize,setPageSize]=useState(20);
  const [form]=Form.useForm<FilterForm>(); const [filters,setFilters]=useState<MailResumeFilters>({}); const [cursor,setCursor]=useState<string>(); const [trail,setTrail]=useState<Array<string|undefined>>([]); const [linking,setLinking]=useState<MailResumeReview>();
  const reviews=useQuery({queryKey:['mail-resume','reviews',cursor,filters,pageSize],queryFn:()=>mailResumeApi.reviews(cursor,filters,pageSize),retry:false});
  const total=useQuery({queryKey:['mail-resume','reviews','count',filters],queryFn:({signal})=>mailResumeApi.countReviews(filters,signal),retry:false});
  const submit=(values:FilterForm)=>{const range=values.range?dateRangeParams(values.range):undefined;setFilters({...(values.title?.trim()?{title:values.title.trim()}:{}),...(values.region?.trim()?{region:values.region.trim()}:{}),...(values.attachment?.trim()?{attachment:values.attachment.trim()}:{}),...(range??{})});setCursor(undefined);setTrail([]);};
  const reset=()=>{form.resetFields();setFilters({});setCursor(undefined);setTrail([]);};
  const next=()=>{if(!reviews.data?.nextCursor)return;setTrail(current=>[...current,cursor]);setCursor(reviews.data.nextCursor);};
  const previous=()=>{const prior=trail.at(-1);setTrail(current=>current.slice(0,-1));setCursor(prior);};
  const page=trail.length+1;
  return <div className="workspace-page settings-list-page mail-resume-validation-page">
    <section className="ledger-heading"><Typography.Title level={1}>邮件简历校验</Typography.Title></section>
    <Card className="mail-resume-filter-card"><Form form={form} className="mail-resume-filters" onFinish={submit}><Form.Item name="title" label="岗位文本"><Input allowClear maxLength={180}/></Form.Item><Form.Item name="region" label="区域"><Input allowClear maxLength={180}/></Form.Item><Form.Item name="attachment" label="附件名"><Input allowClear maxLength={255}/></Form.Item><Form.Item name="range" label="收件日期"><DatePicker.RangePicker allowClear/></Form.Item><Form.Item><Space.Compact><Button type="primary" htmlType="submit">查询</Button><Button onClick={reset}>重置</Button></Space.Compact></Form.Item></Form></Card>
    {reviews.error?<FeedbackAlert occurrence={reviews.error} type="error" title="未匹配邮件读取失败" description={errorText(reviews.error)} action={<Button onClick={()=>void reviews.refetch()}>重试</Button>}/>:null}
    <Card className="paged-panel mail-resume-results-card" title="待处理邮件">
      <Table<MailResumeReview> rowKey="id" pagination={false} tableLayout="fixed" loading={reviews.isPending} dataSource={reviews.data?.items??[]} scroll={{x:1260}} onHeaderRow={() => scrollableTableHeader('邮件简历校验表头，可横向滚动')} locale={{emptyText:'当前条件下没有待匹配邮件'}} columns={[
        {title:'收件时间',dataIndex:'receivedAt',width:170,render:formatDateTime},
        {title:'发件人 / 主题',width:230,render:(_,value)=><div className="mail-resume-cell"><span>{value.sender??'未知发件人'}</span><Typography.Text type="secondary">{value.subject??'无主题'}</Typography.Text></div>},
        {title:'附件名',dataIndex:'attachmentName',width:260,render:value=><span className="mail-resume-cell">{value??'未识别附件名'}</span>},
        {title:'识别结果',width:250,render:(_,value)=><div className="mail-resume-cell">{value.parsedJdCode?<Tag>{value.parsedJdCode}</Tag>:null}<span>{value.parsedJdTitle??'岗位文本未识别'}</span><span>区域：{value.parsedRegion??'未识别'}</span></div>},
        {title:'未匹配原因',width:230,render:(_,value)=><div className="mail-resume-cell"><Tooltip title={`错误代码：${value.failureCode??'REVIEW_REQUIRED'}`} trigger={['hover','focus']}><Tag color="gold" tabIndex={0}>{value.status==='DISPATCH_FAILED'?'下发失败':'待关联 JD'}</Tag></Tooltip><span>{value.failureDetail??'需要人工确认'}</span></div>},
        {title:'操作', className: 'table-action-cell',fixed:'right',width:120,render:(_,value)=>value.status==='REVIEW_REQUIRED'?<Button type="primary" size="small" onClick={()=>setLinking(value)}>关联 JD</Button>:<Tag>系统重试中</Tag>},
      ]}/>
      <CompactPagination label="邮件简历校验分页" page={page} pageSize={pageSize} onPageSizeChange={size=>{setPageSize(size);setCursor(undefined);setTrail([]);}} loading={reviews.isFetching} hasPrevious={trail.length>0} hasNext={Boolean(reviews.data?.hasMore&&reviews.data.nextCursor)} onPrevious={previous} onNext={next} {...countPaginationProps(total)}/>
    </Card>
    <LinkModal {...(linking?{value:linking}:{})} onClose={()=>setLinking(undefined)}/>
  </div>;
}
