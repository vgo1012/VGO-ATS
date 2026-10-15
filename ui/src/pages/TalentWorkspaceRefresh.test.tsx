import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { afterEach, expect, it, vi } from 'vitest';

vi.mock('../auth/session',()=>({useSession:()=>({actor:{userId:'admin',roles:[],permissions:['talent.manage'],superAdmin:true}})}));
vi.mock('../talent/CandidateLibrary',()=>({CandidateLibrary:({onOpenIntakes}:{onOpenIntakes?:()=>void})=> <div>全部简历列表<button onClick={onOpenIntakes}>打开解析校验</button></div>}));
vi.mock('../talent/TalentFilters',()=>({TalentFilters:()=>null}));
vi.mock('../talent/UploadDrawer',()=>({UploadDrawer:()=>null}));
vi.mock('../talent/CandidateDetails',()=>({CandidateDialog:()=>null}));
vi.mock('./IntakeValidationPage',()=>({IntakeValidationPage:()=> <div>解析校验内容</div>}));

import { talentApi } from '../talent/api';
import { TalentWorkspacePage } from './TalentWorkspacePage';

afterEach(()=>{cleanup();vi.restoreAllMocks();});

it('refreshes all resume queries after closing intake validation',async()=>{
  vi.spyOn(talentApi,'channels').mockResolvedValue({items:[],hasMore:false,nextCursor:null});
  vi.spyOn(talentApi,'candidates').mockResolvedValue({items:[],hasMore:false,nextCursor:null});
  vi.spyOn(talentApi,'countCandidates').mockResolvedValue(0);
  const client=new QueryClient({defaultOptions:{queries:{retry:false}}});
  const invalidate=vi.spyOn(client,'invalidateQueries');
  render(<MemoryRouter initialEntries={['/talents?intake=1']}><ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={client}><TalentWorkspacePage/></QueryClientProvider></App></ConfigProvider></MemoryRouter>);
  expect(await screen.findByText('解析校验内容')).toBeVisible();
  fireEvent.click(screen.getByRole('button',{name:'Close'}));
  await waitFor(()=>expect(invalidate).toHaveBeenCalledWith({queryKey:['talent']}));
  expect(invalidate).toHaveBeenCalledWith({queryKey:['talent-search']});
});

it('opens the task groups after closing a deep-linked batch detail',async()=>{
  vi.spyOn(talentApi,'channels').mockResolvedValue({items:[],hasMore:false,nextCursor:null});
  vi.spyOn(talentApi,'candidates').mockResolvedValue({items:[],hasMore:false,nextCursor:null});
  vi.spyOn(talentApi,'countCandidates').mockResolvedValue(0);
  function Location(){return <output data-testid="location">{useLocation().search}</output>;}
  render(<MemoryRouter initialEntries={['/talents?intake=1&batchId=batch-1&name=old']}><ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><TalentWorkspacePage/><Location/></QueryClientProvider></App></ConfigProvider></MemoryRouter>);
  expect(await screen.findByText('解析校验内容')).toBeVisible();
  fireEvent.click(screen.getByRole('button',{name:'Close'}));
  fireEvent.click(screen.getByRole('button',{name:'打开解析校验'}));
  await waitFor(()=>expect(screen.getByTestId('location')).toHaveTextContent('?intake=1'));
  expect(screen.getByTestId('location')).not.toHaveTextContent('batchId=');
  expect(screen.getByTestId('location')).not.toHaveTextContent('name=');
});
