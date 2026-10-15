import { LeftOutlined, RightOutlined, StepBackwardOutlined, StepForwardOutlined } from '@ant-design/icons';
import { useState } from 'react';
import './compact-pagination.css';

export interface CompactPaginationProps {
  page: number;
  pageSize: number;
  total?: number;
  pagesKnown?: boolean;
  totalLabel?: string;
  totalStatus?: 'pending' | 'error';
  onRetryTotal?: () => void;
  loading?: boolean;
  hasPrevious?: boolean;
  hasNext?: boolean;
  onPrevious?: () => void;
  onNext?: () => void;
  onPageChange?: (page: number) => void;
  onPageSizeChange: (size: number) => void;
  maxPage?: number;
  label?: string;
  note?: string;
  previousLabel?: string;
  nextLabel?: string;
}

export function CompactPagination({ page, pageSize, total, totalLabel = '共', pagesKnown = true, totalStatus, onRetryTotal, loading = false, hasPrevious = page > 1, hasNext, onPrevious, onNext, onPageChange, onPageSizeChange, maxPage, label = '分页', note, previousLabel = '上一页', nextLabel = '下一页' }: CompactPaginationProps) {
  const [draft, setDraft] = useState(String(page));
  const [previousPage, setPreviousPage] = useState(page);
  if (previousPage !== page) { setPreviousPage(page); setDraft(String(page)); }
  const pages = !pagesKnown || total === undefined ? undefined : Math.max(1, Math.ceil(total / pageSize));
  const reachable = maxPage ?? pages ?? page;
  const canNext = hasNext ?? (pages !== undefined && page < pages);
  const go = (target: number) => {
    if (!loading && target !== page && Number.isInteger(target) && target >= 1 && target <= reachable) onPageChange?.(target);
  };
  const changeSize = (value: string) => {
    const size = Number(value);
    if (Number.isInteger(size) && size >= 1 && size <= 100 && size !== pageSize) onPageSizeChange(size);
  };
  return <nav className="data-page-controls compact-pagination" aria-label={label} aria-busy={loading}>
    {note ? <span className="pagination-note">{note}</span> : null}
    <label className="pagination-size">每页显示 <input key={pageSize} type="number" aria-label="每页条数" min={1} max={100} defaultValue={pageSize} disabled={loading} onBlur={event => { changeSize(event.currentTarget.value); event.currentTarget.value = String(pageSize); }} onKeyDown={event => { if (event.key === 'Enter') event.currentTarget.blur(); }} /></label>
    <span className="pagination-total">条/{total === undefined ? (totalStatus === 'pending' ? '统计中…' : totalStatus === 'error' ? '统计失败' : '未提供总数') : `${totalLabel}${total}条记录`}</span>
    {totalStatus === 'error' && onRetryTotal ? <button type="button" onClick={onRetryTotal}>重试统计</button> : null}
    <span className="pagination-pages">{!pagesKnown ? '总页数未提供' : pages === undefined ? '未提供总页数' : `共${pages}页`}</span>
    <div className="pagination-navigation">
      <button type="button" aria-label="首页" title="首页" disabled={loading || page === 1 || !onPageChange} onClick={() => go(1)}><StepBackwardOutlined /></button>
      <button type="button" aria-label={previousLabel} title={previousLabel} disabled={loading || !hasPrevious} onClick={() => onPrevious ? onPrevious() : go(page - 1)}><LeftOutlined /></button>
      <label className="pagination-jump">第 <input aria-label="页码" type="number" min={1} max={reachable} value={draft} readOnly={!onPageChange} disabled={loading} onChange={event => setDraft(event.target.value)} onKeyDown={event => { if (event.key === 'Enter') go(Number(draft)); }} /> 页</label>
      <button type="button" aria-label={nextLabel} title={nextLabel} disabled={loading || !canNext} onClick={() => onNext ? onNext() : go(page + 1)}><RightOutlined /></button>
      <button type="button" aria-label="末页" title={pages === undefined ? '未提供总页数' : '末页'} disabled={loading || pages === undefined || page === pages || pages > reachable || !onPageChange} onClick={() => pages !== undefined && go(pages)}><StepForwardOutlined /></button>
      <button type="button" className="pagination-go" aria-label="跳转页面" disabled={loading || !onPageChange || !Number.isInteger(Number(draft)) || Number(draft) < 1 || Number(draft) > reachable} onClick={() => go(Number(draft))}>GO</button>
    </div>
  </nav>;
}
