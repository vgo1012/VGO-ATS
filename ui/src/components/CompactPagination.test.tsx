import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { CompactPagination } from './CompactPagination';
import { PaginatedTable } from './PaginatedTable';

afterEach(cleanup);

const button = (name: string) => screen.getByRole('button', { name });

describe('CompactPagination', () => {
  it('navigates to the first and last pages and accepts only in-range integer jumps', () => {
    const onPageChange = vi.fn();
    render(<CompactPagination onPageSizeChange={vi.fn()} page={2} pageSize={10} total={31} onPageChange={onPageChange} />);
    expect(screen.getByText('条/共31条记录')).toBeVisible();
    expect(screen.getByText('共4页')).toBeVisible();
    fireEvent.click(button('首页'));
    expect(onPageChange).toHaveBeenLastCalledWith(1);
    fireEvent.click(button('末页'));
    expect(onPageChange).toHaveBeenLastCalledWith(4);
    const page = screen.getByRole('spinbutton', { name: '页码' });
    for (const value of ['0', '5', '1.5', '']) {
      onPageChange.mockClear();
      fireEvent.change(page, { target: { value } });
      expect(button('跳转页面')).toBeDisabled();
      fireEvent.keyDown(page, { key: 'Enter' });
      expect(onPageChange).not.toHaveBeenCalled();
    }
    fireEvent.change(page, { target: { value: '3' } });
    fireEvent.click(button('跳转页面'));
    expect(onPageChange).toHaveBeenLastCalledWith(3);
    fireEvent.change(page, { target: { value: '4' } });
    fireEvent.keyDown(page, { key: 'Enter' });
    expect(onPageChange).toHaveBeenLastCalledWith(4);
  });

  it('changes page size on blur and rejects invalid sizes', () => {
    const onPageSizeChange = vi.fn();
    render(<CompactPagination page={1} pageSize={10} total={31} onPageSizeChange={onPageSizeChange} />);
    const size = screen.getByRole('spinbutton', { name: '每页条数' });
    fireEvent.change(size, { target: { value: '20' } });
    fireEvent.blur(size);
    expect(onPageSizeChange).toHaveBeenCalledWith(20);
    onPageSizeChange.mockClear();
    for (const value of ['0', '101', '2.5', '']) {
      fireEvent.change(size, { target: { value } });
      fireEvent.blur(size);
    }
    expect(onPageSizeChange).not.toHaveBeenCalled();
  });

  it('retains cursor callbacks without inventing totals or enabling the last page', () => {
    const onPrevious = vi.fn();
    const onNext = vi.fn();
    render(<CompactPagination onPageSizeChange={vi.fn()} page={2} pageSize={20} hasPrevious hasNext onPrevious={onPrevious} onNext={onNext} />);
    expect(screen.getByText('条/未提供总数')).toBeVisible();
    expect(screen.getByText('未提供总页数')).toBeVisible();
    expect(button('末页')).toBeDisabled();
    expect(button('跳转页面')).toBeDisabled();
    fireEvent.click(button('上一页'));
    fireEvent.click(button('下一页'));
    expect(onPrevious).toHaveBeenCalledOnce();
    expect(onNext).toHaveBeenCalledOnce();
  });

  it('disables navigation and editable controls while loading', () => {
    const onPageChange = vi.fn();
    render(<CompactPagination page={2} pageSize={10} total={31} loading onPageChange={onPageChange} onPageSizeChange={vi.fn()} />);
    for (const control of screen.getAllByRole('button')) expect(control).toBeDisabled();
    for (const control of screen.getAllByRole('spinbutton')) expect(control).toBeDisabled();
    fireEvent.click(button('下一页'));
    expect(onPageChange).not.toHaveBeenCalled();
  });
});

describe('PaginatedTable', () => {
  it('slices rows, clamps the page after rows shrink, and returns to the first page on size changes', () => {
    const rows = Array.from({ length: 12 }, (_, index) => ({ id: index + 1, name: `候选人${index + 1}` }));
    const columns = [{ title: '姓名', dataIndex: 'name' }];
    const view = render(<PaginatedTable rowKey="id" dataSource={rows} columns={columns} initialPageSize={5} />);
    expect(screen.getByText('候选人1')).toBeVisible();
    expect(screen.queryByText('候选人6')).not.toBeInTheDocument();
    fireEvent.click(button('末页'));
    expect(screen.getByRole('spinbutton', { name: '页码' })).toHaveValue(3);
    expect(screen.getByText('候选人11')).toBeVisible();
    expect(screen.queryByText('候选人1')).not.toBeInTheDocument();
    view.rerender(<PaginatedTable rowKey="id" dataSource={rows.slice(0, 7)} columns={columns} initialPageSize={5} />);
    expect(screen.getByRole('spinbutton', { name: '页码' })).toHaveValue(2);
    expect(screen.getByText('候选人6')).toBeVisible();
    expect(button('下一页')).toBeDisabled();
    const size = screen.getByRole('spinbutton', { name: '每页条数' });
    fireEvent.change(size, { target: { value: '10' } });
    fireEvent.blur(size);
    expect(screen.getByRole('spinbutton', { name: '每页条数' })).toHaveValue(10);
    expect(screen.getByRole('spinbutton', { name: '页码' })).toHaveValue(1);
    expect(screen.getByText('候选人1')).toBeVisible();
    expect(screen.getByText('候选人7')).toBeVisible();
    expect(button('上一页')).toBeDisabled();
    view.rerender(<PaginatedTable rowKey="id" dataSource={[]} columns={columns} initialPageSize={5} />);
    expect(screen.getByText('条/共0条记录')).toBeVisible();
    expect(screen.getByRole('spinbutton', { name: '页码' })).toHaveValue(1);
    expect(button('下一页')).toBeDisabled();
  });
});


it('defaults tables to twenty rows and keeps the next row accessible on page two', () => {
  const rows = Array.from({ length: 21 }, (_, index) => ({ id: index + 1, name: `默认分页候选人${index + 1}` }));
  render(<PaginatedTable rowKey="id" dataSource={rows} columns={[{ title: '姓名', dataIndex: 'name' }]} />);
  expect(screen.getByRole('spinbutton', { name: '每页条数' })).toHaveValue(20);
  expect(screen.getByText('默认分页候选人20')).toBeVisible();
  expect(screen.queryByText('默认分页候选人21')).not.toBeInTheDocument();
  fireEvent.click(button('下一页'));
  expect(screen.getByText('默认分页候选人21')).toBeVisible();
  expect(screen.queryByText('默认分页候选人1')).not.toBeInTheDocument();
});

it('shows count progress and a retry without blocking cursor navigation or inventing reachable pages', () => {
  const retry=vi.fn();
  const props={onPageSizeChange:vi.fn(),page:1,pageSize:20,hasNext:true,maxPage:1,onPageChange:vi.fn()};
  const view=render(<CompactPagination {...props} totalStatus="pending"/>);
  expect(screen.getByText('条/统计中…')).toBeVisible();
  expect(button('下一页')).toBeEnabled();
  view.rerender(<CompactPagination {...props} totalStatus="error" onRetryTotal={retry}/>);
  fireEvent.click(button('重试统计'));
  expect(retry).toHaveBeenCalledOnce();
  view.rerender(<CompactPagination {...props} total={43}/>);
  expect(screen.getByText('共3页')).toBeVisible();
  expect(button('末页')).toBeDisabled();
});

it('does not derive a last page from result counts for bounded search scans',()=>{
 render(<CompactPagination onPageSizeChange={vi.fn()} page={2} pageSize={20} total={1} pagesKnown={false} maxPage={2} hasNext onPageChange={vi.fn()}/>);
 expect(screen.getByText('条/共1条记录')).toBeVisible();
 expect(screen.getByText('总页数未提供')).toBeVisible();
 expect(button('下一页')).toBeEnabled();
 expect(button('末页')).toBeDisabled();
});
