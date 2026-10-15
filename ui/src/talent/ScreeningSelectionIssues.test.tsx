import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { talentApi, type Candidate } from './api';
import { ScreeningSelectionIssues } from './ScreeningSelectionIssues';

afterEach(()=>{cleanup();vi.restoreAllMocks();});
it('names off-page candidates and opens their details',async()=>{
  const batch=vi.spyOn(talentApi,'candidateBatch').mockResolvedValue([{id:'off',fullName:'跨页张三',primaryPhone:'13800000000'} as Candidate]);
  const open=vi.fn();
  render(<QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><ScreeningSelectionIssues issues={[{candidateId:'off',reason:'尚未完成默认评分'},{candidateId:'local',reason:'岗位不明确'}]} items={[{id:'local',fullName:'本页李四'} as Candidate]} onOpen={open}/></QueryClientProvider>);
  fireEvent.click(await screen.findByRole('button',{name:'跨页张三'}));
  expect(open).toHaveBeenCalledWith('off');
  expect(batch).toHaveBeenCalledWith(['off']);
  expect(screen.getByText(/13800000000/)).toBeVisible();
  expect(screen.getByRole('button',{name:'本页李四'})).toBeVisible();
});
