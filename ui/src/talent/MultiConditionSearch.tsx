import { CloseOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons';
import { Button, Input, type InputRef } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { MAX_SEARCH_CONDITIONS, mergeSearchTerms } from './searchTerms';
import './multi-condition-search.css';

export function MultiConditionSearch({ query, conditions = [], onSearch, label, disabled = false, onDraftChange }: {
  query: string; conditions?: string[]; onSearch: (query: string, conditions: string[]) => void; label: string; disabled?: boolean;
  /** 暴露已输入但未提交的文本，供页面上的“筛选”等外部提交一并带上，避免用户已输入的关键词被静默丢弃。 */
  onDraftChange?: (draft: string) => void;
}) {
  const scope = JSON.stringify([query, conditions]);
  const [previousScope, setPreviousScope] = useState(scope);
  const [tags, setTags] = useState([query, ...conditions].filter(Boolean));
  const [draft, setDraft] = useState('');
  const input = useRef<InputRef>(null);
  if (scope !== previousScope) { setPreviousScope(scope); setTags([query, ...conditions].filter(Boolean)); setDraft(''); }
  useEffect(() => { onDraftChange?.(draft); }, [draft, onDraftChange]);
  const collect = () => {
    const next = mergeSearchTerms([...tags, draft]);
    setTags(next); setDraft(''); input.current?.focus();
    return next;
  };
  const submit = () => { const next = collect(); onSearch(next[0] ?? '', next.slice(1)); };
  return <div className="multi-condition-search" role="search" aria-label={label}>
    <div className="multi-condition-bar">
      <div className="multi-condition-field">
        {tags.map((tag, index) => <span className="search-condition-tag" key={`${index}:${tag}`}>
          <span title={tag}>{tag}</span>
          <button type="button" aria-label={`删除条件 ${tag}`} disabled={disabled} onClick={() => { setTags(previous => previous.filter((_, position) => position !== index)); input.current?.focus(); }}><CloseOutlined /></button>
        </span>)}
        <Input ref={input} variant="borderless" aria-label={label} placeholder={tags.length ? '输入条件，＋添加' : '姓名、联系方式、证书或技能'} value={draft} maxLength={200} disabled={disabled || tags.length >= MAX_SEARCH_CONDITIONS} onChange={event => setDraft(event.target.value)} onPressEnter={event => { if (!event.nativeEvent.isComposing) submit(); }} />
      </div>
      <Button aria-label="添加搜索条件" title="将输入内容添加为条件标签" icon={<PlusOutlined />} disabled={disabled || !draft.trim() || tags.length >= MAX_SEARCH_CONDITIONS} onClick={collect} />
      <Button aria-label="搜索" autoInsertSpace={false} icon={<SearchOutlined />} disabled={disabled} onClick={submit}>搜索</Button>
    </div>
    {tags.length || draft ? <div className="multi-condition-hint"><Button size="small" type="link" disabled={disabled} onClick={() => { setTags([]); setDraft(''); onSearch('', []); input.current?.focus(); }}>清空条件</Button></div> : null}
  </div>;
}
