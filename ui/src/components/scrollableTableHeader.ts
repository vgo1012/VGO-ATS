import type { KeyboardEvent } from 'react';

export function scrollableTableHeader(label: string) {
  return {
    tabIndex: 0,
    'aria-label': label,
    onKeyDown: (event: KeyboardEvent<HTMLTableRowElement>) => {
      if (event.target !== event.currentTarget || (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight')) return;
      const viewport = event.currentTarget.closest<HTMLElement>('.ant-table-content');
      if (!viewport || viewport.scrollWidth <= viewport.clientWidth) return;
      event.preventDefault();
      viewport.scrollLeft += event.key === 'ArrowRight' ? 120 : -120;
    },
  };
}
