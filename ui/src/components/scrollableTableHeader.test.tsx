import { fireEvent, render } from '@testing-library/react';
import { it, expect } from 'vitest';
import { scrollableTableHeader } from './scrollableTableHeader';

it('scrolls a wide table from its header without stealing keys from controls', () => {
  const { container } = render(<div className="ant-table-content"><table><thead><tr {...scrollableTableHeader('结果表头，可横向滚动')}><th><button>选择</button></th></tr></thead></table></div>);
  const viewport = container.querySelector<HTMLElement>('.ant-table-content')!;
  const header = container.querySelector('tr')!;
  Object.defineProperty(viewport, 'clientWidth', { value: 200 });
  Object.defineProperty(viewport, 'scrollWidth', { value: 600 });

  fireEvent.keyDown(header, { key: 'ArrowRight' });
  expect(viewport.scrollLeft).toBe(120);
  fireEvent.keyDown(header.querySelector('button')!, { key: 'ArrowRight' });
  expect(viewport.scrollLeft).toBe(120);
  fireEvent.keyDown(header, { key: 'ArrowLeft' });
  expect(viewport.scrollLeft).toBe(0);
});
