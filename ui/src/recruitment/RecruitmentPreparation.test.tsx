import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import { RecruitmentPreparation } from './RecruitmentPreparation';
import { candidateActionsApi } from './candidateActionsApi';
vi.mock('./candidateActionsApi',()=>({candidateActionsApi:{activateStandard:vi.fn()}}));
it('allows a source JD to activate its standard workflow without binding a demand', async()=>{
  vi.mocked(candidateActionsApi.activateStandard).mockResolvedValue({} as never); const changed=vi.fn().mockResolvedValue(undefined);
  render(<QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><RecruitmentPreparation value={{headcount:2,occupiedHeadcount:0,remainingHeadcount:2,jdId:'jd',jdVersionId:'jv',jdTitle:'来源岗位',demandId:null,recruitmentType:'INTERNAL',canProcess:true,canManageWorkflow:true,activeWorkflowId:null,activeWorkflow:null,draftWorkflows:[],standardSteps:['CANDIDATE_COMMUNICATION','DECISION']}} onChanged={changed}/></QueryClientProvider>);
  expect(screen.queryByText('关联招聘需求')).not.toBeInTheDocument(); fireEvent.click(screen.getByRole('button',{name:'使用并激活标准流程'}));
  await waitFor(()=>expect(candidateActionsApi.activateStandard).toHaveBeenCalledWith('jv',expect.any(String)));
});
