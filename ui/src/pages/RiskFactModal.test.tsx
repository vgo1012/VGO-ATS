import { App } from 'antd';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { RiskFactModal } from './RecruitmentWorkspacePage';

afterEach(cleanup);
it('explains missing education facts without spinning indefinitely or allowing submission',()=>{
  render(<App><RiskFactModal kind="education" educations={[]} loading={false} error={null} onClose={vi.fn()} onSubmit={vi.fn()}/></App>);
  expect(screen.getByText('当前简历版本没有可核验的学历事实')).toBeVisible();
  expect(screen.getByRole('button',{name:'OK'})).toBeDisabled();
  expect(screen.queryByText('正在读取简历学历事实…')).not.toBeInTheDocument();
});
it('offers retry for failed fact reads while preventing invalid verification',()=>{
  const retry=vi.fn();render(<App><RiskFactModal kind="education" educations={[]} factsError={new Error('网络暂不可用')} onRetryFacts={retry} loading={false} error={null} onClose={vi.fn()} onSubmit={vi.fn()}/></App>);
  fireEvent.click(screen.getByRole('button',{name:'重试学历事实'}));expect(retry).toHaveBeenCalledOnce();expect(screen.getByRole('button',{name:'OK'})).toBeDisabled();
});
