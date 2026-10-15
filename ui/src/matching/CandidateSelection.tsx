import { scrollableTableHeader } from '../components/scrollableTableHeader';
import { CompactPagination } from '../components/CompactPagination';
import { countPaginationProps } from '../components/countPaginationProps';
import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Button, Input, Space, Table } from 'antd';
import { useState } from 'react';
import { talentApi } from '../talent/api';
import { matchingApi, type ScreeningSelectionCandidate, type ScreeningSelectionIdentity } from './api';
import { MATCH_CANDIDATE_LIMIT } from './selectionCandidates';
import { selectScreeningCandidates } from './selectScreeningCandidates';

export function CandidateSelection({ selectedItems, initialCandidateIds, onChange, disabled, onBusyChange, jdVersionId }: {
  selectedItems: ScreeningSelectionIdentity[] | undefined;
  initialCandidateIds: string[];
  onChange: (items: ScreeningSelectionIdentity[]) => void;
  disabled: boolean;
  onBusyChange: (busy: boolean) => void;
  jdVersionId: string;
}) {
  const [query, setQuery] = useState('');
  const [pageSize, setPageSize] = useState(20);
  const [cursor, setCursor] = useState<string>();
  const [history, setHistory] = useState<Array<string | undefined>>([]);
  const [selectionError, setSelectionError] = useState('');
  const needsInitial = selectedItems === undefined && initialCandidateIds.length > 0;
  const initial = useQuery({queryKey:['matching','screening-selection-initial',jdVersionId,initialCandidateIds],queryFn:()=>talentApi.screeningCandidates(initialCandidateIds),enabled:needsInitial,retry:false});
  const selection = selectedItems ?? (initial.data ?? []).filter(item=>item.jdVersionId===jdVersionId&&item.admissionStatus==='ADMITTED').map(({candidateId,resumeVersionId})=>({candidateId,resumeVersionId}));
  const candidates = useQuery({queryKey:['matching','screening-selection',jdVersionId,query,cursor,pageSize],queryFn:()=>matchingApi.screeningCandidates(jdVersionId,query,cursor,pageSize),retry:false});
  const total = useQuery({queryKey:['matching','screening-selection','count',jdVersionId,query],queryFn:({signal})=>matchingApi.countScreeningCandidates(jdVersionId,query,signal),retry:false});
  const changeSelection = (items: ScreeningSelectionIdentity[]) => {
    if (items.length > MATCH_CANDIDATE_LIMIT) { setSelectionError(`每次最多选择 ${MATCH_CANDIDATE_LIMIT} 份简历，请先取消部分选择或缩小搜索范围`); return; }
    setSelectionError('');
    onChange(items);
  };
  const all = useMutation({
    mutationFn: () => selectScreeningCandidates(jdVersionId,query),
    onMutate: () => onBusyChange(true),
    onSuccess: changeSelection,
    onSettled: () => onBusyChange(false),
  });
  const busy = disabled || all.isPending || (needsInitial && (initial.isPending || initial.isError));
  const selectedIds = selection.map(item=>item.resumeVersionId);
  const toggle = (item: ScreeningSelectionCandidate) => changeSelection(selectedIds.includes(item.resumeVersionId)
    ? selection.filter(value=>value.resumeVersionId!==item.resumeVersionId)
    : [...selection,{candidateId:item.candidateId,resumeVersionId:item.resumeVersionId}]);
  return <Space orientation="vertical" size={12} style={{width:'100%'}}>
    <Input.Search allowClear disabled={busy} aria-label="搜索本次匹配候选人" placeholder="搜索已加入当前岗位的候选人姓名" onSearch={value=>{setQuery(value.trim());setCursor(undefined);setHistory([]);all.reset();}} />
    <Space wrap>
      <Button disabled={busy || candidates.isPending || Boolean(candidates.error)} loading={all.isPending} onClick={()=>all.mutate()}>全选符合条件的候选人</Button>
      <Button disabled={busy || !selection.length} onClick={()=>changeSelection([])}>清空候选人选择</Button>
      <span role="status">已选 {selection.length} 份简历 · 每次最多 {MATCH_CANDIDATE_LIMIT} 份，跨页保留选择</span>
    </Space>
    {all.error ? <Alert occurrence={all.error} type="error" showIcon title={all.error.message} /> : null}
    {selectionError ? <Alert occurrence={selectionError} type="warning" showIcon title={selectionError} /> : null}
    {needsInitial && initial.error ? <Alert occurrence={initial.error} type="error" showIcon title="已选简历读取失败，原有选择已保留" action={<Button onClick={()=>void initial.refetch()}>重试已选简历</Button>} /> : null}
    {candidates.error ? <Alert occurrence={candidates.error} type="error" showIcon title="当前岗位已加入的简历读取失败，已有选择已保留" action={<Button onClick={()=>void candidates.refetch()}>重试候选人</Button>} /> : null}
    <Table<ScreeningSelectionCandidate> rowKey="resumeVersionId" size="small" tableLayout="fixed" scroll={{x:540}} pagination={false}
      onHeaderRow={() => scrollableTableHeader('候选集合表头，可横向滚动')}
      loading={candidates.isPending} dataSource={candidates.data?.items??[]}
      rowSelection={{preserveSelectedRowKeys:true,selectedRowKeys:selectedIds,onChange:keys=>{
        const byResume = new Map([...selection,...(candidates.data?.items??[])].map(item=>[item.resumeVersionId,{candidateId:item.candidateId,resumeVersionId:item.resumeVersionId}]));
        changeSelection(keys.map(String).flatMap(id=>{const item=byResume.get(id);return item?[item]:[];}));
      },getCheckboxProps:item=>({disabled:busy || (selection.length>=MATCH_CANDIDATE_LIMIT&&!selectedIds.includes(item.resumeVersionId))})}}
      columns={[
        {title:'候选人 / 简历版本',width:230,render:(_,item)=><Space orientation="vertical" size={0}><span>{item.candidateName}</span><span title={item.originalFileName}>第 {item.resumeVersionNo} 版简历</span></Space>},
        {title:'公司专业分',dataIndex:'professionalScore',width:120,render:(value:number)=>value.toFixed(2)},
        {title:'公司参考分',dataIndex:'referenceScore',width:120,render:(value:number)=>value.toFixed(2)},
        {title:'选择',width:160,render:(_,item)=><Button size="small" disabled={busy||(!selectedIds.includes(item.resumeVersionId)&&selection.length>=MATCH_CANDIDATE_LIMIT)} onClick={()=>toggle(item)}>{selectedIds.includes(item.resumeVersionId)?'移除本次匹配':'加入本次匹配'}</Button>},
      ]} locale={{emptyText:query?'当前岗位没有符合姓名的已加入简历':'当前岗位尚无已加入智能筛选的简历，请先在“我的简历”中加入'}} />
    <CompactPagination previousLabel="上一页候选人" nextLabel="下一页候选人" label="匹配候选人分页" page={history.length + 1} pageSize={pageSize} onPageSizeChange={size=>{setPageSize(size);setCursor(undefined);setHistory([]);}} loading={busy || candidates.isFetching}
      hasPrevious={history.length > 0} hasNext={Boolean(!candidates.error && candidates.data?.hasMore && candidates.data.nextCursor)} maxPage={history.length + 1}
      onPrevious={() => { setCursor(history.at(-1)); setHistory(history.slice(0, -1)); }}
      onNext={() => { setHistory([...history, cursor]); setCursor(candidates.data?.nextCursor ?? undefined); }}
      onPageChange={page => { if (page <= history.length) { setCursor(history[page - 1]); setHistory(history.slice(0, page - 1)); } }} {...countPaginationProps(total)} />
  </Space>;
}
