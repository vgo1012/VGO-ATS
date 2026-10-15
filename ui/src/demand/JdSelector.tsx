import { jobLabel } from '../demand/jobLabel';
import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { Button, Select } from 'antd';
import { CloseOutlined } from '@ant-design/icons';
import { useDeferredValue, useState } from 'react';
import { demandApi, type JdSummary } from './api';
import { useUserNames } from '../display/BusinessReferences';
import { useSession } from '../auth/session';
import { LoadingStatus } from '../components/LoadingStatus';

interface JdSelectorProps {
  value?: string;
  onChange: (jd: JdSummary | undefined) => void;
  placeholder?: string;
  disabled?: boolean;
  includeSourceJds?: boolean;
  parsedOnly?: boolean;
  prioritizeCurrentOwner?: boolean;
}

const jdCodeCollator=new Intl.Collator('zh-CN',{numeric:true,sensitivity:'base'});
const compareLibraryJds=(actorId:string|undefined)=>(left:JdSummary,right:JdSummary)=>{
  const ownerOrder=Number(left.ownerUserId!==actorId)-Number(right.ownerUserId!==actorId);
  if(ownerOrder)return ownerOrder;
  if(left.code&&right.code){const codeOrder=jdCodeCollator.compare(right.code,left.code);if(codeOrder)return codeOrder;}
  else if(left.code||right.code)return left.code?-1:1;
  return right.createTime.localeCompare(left.createTime)||right.id.localeCompare(left.id);
};

export function JdSelector({ value, onChange, disabled = false, parsedOnly = false, prioritizeCurrentOwner = false, placeholder = '搜索 JD 编号、岗位名称、地区或客户' }: JdSelectorProps) {
  const {actor}=useSession();
  const [search, setSearch] = useState('');
  const [selected, setSelected] = useState<JdSummary>();
  const query = useDeferredValue(search);
  const jds = useInfiniteQuery({
    queryKey: ['demand', 'jd-selector', query],
    initialPageParam: undefined as string | undefined,
    queryFn: ({ pageParam }) => demandApi.jds(query, pageParam),
    getNextPageParam: page => page.hasMore ? page.nextCursor ?? undefined : undefined,
    staleTime: 15_000,
  });
  const items = jds.data?.pages.flatMap(page => page.items) ?? [];
  const knownSelection = items.find(item => item.id === value) ?? (selected?.id === value ? selected : undefined);
  const detail = useQuery({
    queryKey: ['demand', 'jd', value],
    queryFn: () => demandApi.jd(value!),
    enabled: Boolean(value && !knownSelection && !jds.isPending),
    staleTime: 15_000,
    retry: false,
  });
  const currentSelection = knownSelection ?? detail.data;

  const clear = () => { setSearch(''); setSelected(undefined); onChange(undefined); };
  const visibleOptions = (currentSelection && currentSelection.id === value && !items.some(item => item.id === value) ? [currentSelection, ...items] : items)
    .filter(item => parsedOnly ? item.currentParseStatus === 'SUCCEEDED' : item.currentParseStatus !== 'FAILED');
  const options = prioritizeCurrentOwner ? [...visibleOptions].sort(compareLibraryJds(actor?.userId)) : visibleOptions;
  const ownerNames = useUserNames(options.map(item => item.ownerUserId));
  return <div className="jd-selector">
    <Select
      className="jd-selector-input"
      showSearch
      disabled={disabled}
      allowClear={false}
      value={value ?? null}
      labelRender={({ label }) => parsedOnly && currentSelection?.currentParseStatus !== 'SUCCEEDED' ? '该岗位尚未解析成功，请重新选择' : currentSelection?.currentParseStatus === 'FAILED' ? '该岗位解析失败，请重新选择' : currentSelection ? label : detail.isError ? '岗位信息暂不可用' : '正在读取岗位…'}
      searchValue={search}
      popupMatchSelectWidth={false}
      virtual={false}
      classNames={{ popup: { root: 'jd-selector-popup' } }}
      placeholder={placeholder}
      aria-label={placeholder}
      filterOption={false}
      onSearch={setSearch}
      onChange={(id) => { const item = options.find(item => item.id === id); setSelected(item); setSearch(''); onChange(item); }}
      onOpenChange={open => { if (!open) setSearch(''); }}
      onPopupScroll={event => { const node = event.currentTarget; if (node.scrollHeight - node.scrollTop - node.clientHeight < 40 && jds.hasNextPage && !jds.isFetchingNextPage) void jds.fetchNextPage(); }}
      loading={jds.isFetching || detail.isFetching}
      popupRender={menu => <>{menu}{jds.hasNextPage ? <Button type="text" block loading={jds.isFetchingNextPage} onClick={() => void jds.fetchNextPage()}>{jds.isFetchNextPageError ? '加载失败，点击重试' : '加载更多岗位'}</Button> : null}</>}
      notFoundContent={jds.isFetching ? <LoadingStatus size="small" label="正在读取 JD" /> : jds.error ? <Button type="text" onClick={() => void jds.refetch()}>JD 加载失败，点击重试</Button> : search ? '没有匹配的 JD' : '暂无可见 JD'}
      options={options.map((item) => ({
        value: item.id,
        label: <span>{jobLabel(item.title, item.code)}{item.clientName ? ` · ${item.clientName}` : ''}{item.workRegionName ? ` · ${item.workRegionName}` : ''} · V{item.currentVersionNo} · 归属 {ownerNames.people.get(item.ownerUserId) ?? (ownerNames.loading ? '加载姓名…' : '成员姓名暂不可用')}</span>,
        title: `${jobLabel(item.title, item.code)}${item.clientName ? ` · ${item.clientName}` : ''}${item.workRegionName ? ` · ${item.workRegionName}` : ''} · V${item.currentVersionNo}`,
      }))}
    />
    {value && !currentSelection && detail.isError ? <Button type="text" disabled={disabled} onClick={() => void detail.refetch()}>重新加载岗位</Button> : null}
    {value ? <Button type="text" icon={<CloseOutlined />} aria-label="清空岗位筛选" title="清空岗位筛选" disabled={disabled} onClick={clear} /> : null}
  </div>;
}
