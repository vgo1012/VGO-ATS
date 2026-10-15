import { Table, type TableProps } from 'antd';
import { useState } from 'react';
import { CompactPagination } from './CompactPagination';

type Props<T extends object> = Omit<TableProps<T>, 'pagination'> & {
  initialPageSize?: number;
  totalLabel?: string;
  paginationLabel?: string;
};

export function PaginatedTable<T extends object>({ dataSource = [], initialPageSize = 20, totalLabel, paginationLabel, ...props }: Props<T>) {
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(initialPageSize);
  const currentPage = Math.min(page, Math.max(1, Math.ceil(dataSource.length / pageSize)));
  if (page !== currentPage) setPage(currentPage);
  return <>
    <Table<T> {...props} pagination={false} dataSource={dataSource.slice((currentPage - 1) * pageSize, currentPage * pageSize)} />
    <CompactPagination page={currentPage} pageSize={pageSize} total={dataSource.length}
      {...(totalLabel ? { totalLabel } : {})}
      {...(paginationLabel ? { label: paginationLabel } : {})}
      loading={typeof props.loading === 'boolean' ? props.loading : Boolean(props.loading?.spinning)}
      onPageChange={setPage} onPageSizeChange={size => { setPageSize(size); setPage(1); }} />
  </>;
}
