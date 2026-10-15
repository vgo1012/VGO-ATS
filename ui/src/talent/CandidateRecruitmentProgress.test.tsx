import { fireEvent, render, screen } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import { CandidateRecruitmentProgress } from './CandidateRecruitmentProgress';
import type { CandidatePoolStatus } from './api';

it('labels ended applications as clickable history instead of current progress',()=>{
  const onOpen=vi.fn();
  const state:CandidatePoolStatus={
    candidateId:'candidate',resumeVersionId:'resume',libraryEligible:true,screenedOut:true,canScreenOut:false,stages:[],
    jobContexts:[{jdId:'jd',jdVersionId:'jd-version',title:'旧岗位',versionNo:1,resumeVersionId:'resume',status:'FINISHED',stage:'TERMINATED',applicationId:'application',matchRunId:null}],
  };

  render(<CandidateRecruitmentProgress state={state} loading={false} failed={false} onOpen={onOpen}/>);

  const history=screen.getByRole('button',{name:'旧岗位 · 历史归档'});
  fireEvent.click(history);
  expect(onOpen).toHaveBeenCalledWith('application');
  expect(screen.queryByText(/流程已结束/)).not.toBeInTheDocument();
});
