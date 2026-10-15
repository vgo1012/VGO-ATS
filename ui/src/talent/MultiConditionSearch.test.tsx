import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { MultiConditionSearch } from './MultiConditionSearch';

afterEach(cleanup);

it('submits all nonblank conditions together and does not submit an IME composition', () => {
  const onSearch = vi.fn();
  render(<MultiConditionSearch label="搜索候选人" query="" onSearch={onSearch} />);
  const main = screen.getByRole('textbox', { name: '搜索候选人' });
  fireEvent.change(main, { target: { value: ' 高压电工证 ' } });
  fireEvent.click(screen.getByRole('button', { name: '添加搜索条件' }));
  const second = main;
  fireEvent.change(second, { target: { value: ' 低压电工证 ' } });
  fireEvent.keyDown(second, { key: 'Enter', code: 'Enter', isComposing: true });
  expect(onSearch).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: '添加搜索条件' }));
  fireEvent.click(screen.getByRole('button', { name: '搜索' }));
  expect(onSearch).toHaveBeenLastCalledWith('高压电工证', ['低压电工证']);
  fireEvent.click(screen.getByRole('button', { name: '清空条件' }));
  expect(onSearch).toHaveBeenLastCalledWith('', []);
  expect(screen.getAllByRole('textbox')).toHaveLength(1);
});

it('limits condition rows and restores externally applied values', () => {
  const view = render(<MultiConditionSearch label="搜索候选人" query="姓名" onSearch={vi.fn()} />);
  const add = screen.getByRole('button', { name: '添加搜索条件' });
  for (let index = 1; index < 10; index++) { fireEvent.change(screen.getByRole('textbox'), {target:{value:`证书${index}`}}); fireEvent.click(add); }
  expect(add).toBeDisabled();
  expect(screen.getAllByRole('textbox')).toHaveLength(1);
  view.rerender(<MultiConditionSearch label="搜索候选人" query="高压电工证" conditions={['低压电工证']} onSearch={vi.fn()} />);
  expect(screen.getAllByRole('textbox')).toHaveLength(1);
  expect(screen.getByRole('button', { name: '删除条件 低压电工证' })).toBeInTheDocument();
});
