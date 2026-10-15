import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import type { RecruitmentApplication } from './api';
import { RecruitmentStage } from './RecruitmentStage';

afterEach(cleanup);

it('同时高亮所有真实进行中的轮次，并随完成取消和活动结束清除',()=>{
  const value={id:'app',currentStage:'PATH_SELECTION',terminalAt:null,activeInterviewRoundNos:[3,5]} as unknown as RecruitmentApplication;
  const onAction=vi.fn();
  const view=render(<RecruitmentStage value={value} onAction={onAction}/>);
  expect(screen.getByRole('group',{name:'进行中面试：第 3 轮、第 5 轮'})).toBeInTheDocument();
  const round=(n:number)=>screen.getByRole('button',{name:`第 ${n} 轮面试`});
  for(const n of [3,5])expect(round(n)).toHaveAttribute('data-active','true');
  for(const n of [1,2,4,6])expect(round(n)).not.toHaveAttribute('data-active');
  expect(screen.getByRole('button',{name:'面试'})).toHaveClass('selected');
  fireEvent.click(round(5));expect(onAction).toHaveBeenCalledWith('INTERVIEW',round(5),5);
  view.rerender(<RecruitmentStage value={{...value,activeInterviewRoundNos:[5]}} onAction={onAction}/>);
  expect(round(3)).not.toHaveAttribute('data-active');expect(round(5)).toHaveAttribute('data-active','true');
  view.rerender(<RecruitmentStage value={{...value,activeInterviewRoundNos:[],currentStage:'IN_PROCESS',currentStepCode:'INTERVIEW_ROUND_3',currentStepRoundNo:3}} onAction={onAction}/>);
  expect(round(3)).not.toHaveAttribute('data-active');expect(round(5)).not.toHaveAttribute('data-active');
  view.rerender(<RecruitmentStage value={{...value,activeInterviewRoundNos:[3,7]}} onAction={onAction}/>);
  expect(round(7)).toHaveAttribute('data-active','true');
  view.rerender(<RecruitmentStage value={{...value,currentStage:'ONBOARDED',terminalAt:'2026-09-15T10:00:00Z'}} onAction={onAction}/>);
  expect(screen.queryByRole('button',{name:'第 3 轮面试'})).not.toBeInTheDocument();
  expect(screen.queryByRole('button',{name:'第 5 轮面试'})).not.toBeInTheDocument();
});

it('一面完成后保留高亮，未保存二面排期不切换，保存后切换至二面',()=>{
  const value={id:'app',currentStage:'PATH_SELECTION',terminalAt:null,activeInterviewRoundNos:[],latestCompletedInterviewRoundNo:1} as unknown as RecruitmentApplication;
  const onAction=vi.fn();
  const view=render(<RecruitmentStage value={value} onAction={onAction}/>);
  const round=(n:number)=>screen.getByRole('button',{name:`第 ${n} 轮面试`});
  expect(round(1)).toHaveAttribute('data-completed','true');
  expect(round(1)).toHaveAttribute('aria-description','已完成');
  expect(round(1)).not.toHaveAttribute('data-active');
  expect(screen.getByRole('button',{name:'面试'})).toHaveClass('selected');
  expect(screen.getByRole('button',{name:'待定'})).not.toHaveClass('selected');
  expect(screen.queryByText('第 1 轮已完成 · 待安排下一步')).not.toBeInTheDocument();
  fireEvent.click(round(1));expect(onAction).toHaveBeenCalledWith('INTERVIEW',round(1),1);
  view.rerender(<RecruitmentStage value={{...value,currentStage:'IN_PROCESS',currentStepCode:'INTERVIEW_ROUND_1',currentStepRoundNo:2}} onAction={onAction}/>);
  expect(round(1)).toHaveAttribute('data-completed','true');
  expect(round(2)).not.toHaveAttribute('data-active');
  view.rerender(<RecruitmentStage value={{...value,activeInterviewRoundNos:[2]}} onAction={onAction}/>);
  expect(round(1)).not.toHaveAttribute('data-completed');
  expect(round(2)).toHaveAttribute('data-active','true');
  expect(round(2)).toHaveAttribute('aria-description','进行中');
  view.rerender(<RecruitmentStage value={{...value,currentStage:'IN_PROCESS',currentStepCode:'CANDIDATE_COMMUNICATION'}} onAction={onAction}/>);
  expect(round(1)).not.toHaveAttribute('data-completed');
  for(const currentStage of ['OFFER','DECISION'] as const){
    view.rerender(<RecruitmentStage value={{...value,currentStage}} onAction={onAction}/>);
    expect(round(1)).not.toHaveAttribute('data-completed');
    expect(screen.getByRole('button',{name:'面试'})).not.toHaveClass('selected');
  }
  view.rerender(<RecruitmentStage value={{...value,currentStage:'ONBOARDED'}} onAction={onAction}/>);
  expect(screen.queryByRole('button',{name:'第 1 轮面试'})).not.toBeInTheDocument();
  view.rerender(<RecruitmentStage value={{...value,currentStage:'TERMINATED'}} onAction={onAction}/>);
  expect(screen.queryByRole('button',{name:'第 1 轮面试'})).not.toBeInTheDocument();
});

it('进行中各阶段不增加说明行，Offer 仅高亮入职按钮',()=>{
  const value={id:'app',currentStage:'PATH_SELECTION',activeInterviewRoundNos:[]} as unknown as RecruitmentApplication;
  const view=render(<RecruitmentStage value={value} onAction={vi.fn()}/>);
  for(const currentStage of ['PATH_SELECTION','IN_PROCESS','DECISION','OFFER'] as const){
    view.rerender(<RecruitmentStage value={{...value,currentStage}} onAction={vi.fn()}/>);
    expect(view.container.querySelector('.recruitment-stage')?.children).toHaveLength(2);
    expect(view.container.querySelectorAll('small')).toHaveLength(1);
    expect(screen.getByRole('button',{name:'入职'}).classList.contains('selected')).toBe(['OFFER','ONBOARDED'].includes(currentStage));
  }
});

it.each([
  ['IN_PROGRESS',{currentStage:'OFFER',onboardingStatus:'IN_PROGRESS'}],
  ['ONBOARDED',{currentStage:'OFFER',onboardingStatus:'ONBOARDED'}],
  ['CURRENT_STAGE_ONBOARDED',{currentStage:'ONBOARDED'}],
] as const)('转入入职流程后招聘状态显示已完成且仅保留黑名单操作（%s）',(_case,value)=>{
  const onAction=vi.fn();
  render(<RecruitmentStage value={{id:'app',...value} as RecruitmentApplication} onAction={onAction}/>);

  expect(screen.getByRole('group',{name:'招聘已完成'})).toHaveTextContent('已完成');
  expect(screen.getByText('已转入入职流程')).toBeInTheDocument();
  const blacklist=screen.getByRole('button',{name:'黑名单'});
  expect(screen.getAllByRole('button')).toEqual([blacklist]);
  fireEvent.click(blacklist);
  expect(onAction).toHaveBeenCalledWith('BLACKLIST',blacklist);
});

it('已取消活动只显示放弃状态和黑名单操作',()=>{
  const onAction=vi.fn();
  render(<RecruitmentStage value={{id:'app',currentStage:'TERMINATED',terminalAt:'2026-09-22T08:00:00Z'} as RecruitmentApplication} onAction={onAction}/>);
  expect(screen.getByRole('group',{name:'招聘已取消'})).toBeInTheDocument();

  const abandoned=screen.getByRole('button',{name:'放弃'});
  const blacklist=screen.getByRole('button',{name:'黑名单'});
  expect(screen.getAllByRole('button')).toEqual([abandoned,blacklist]);
  expect(abandoned).toBeDisabled();
  expect(abandoned).toHaveClass('selected');
  expect(screen.queryByText('轮次')).not.toBeInTheDocument();
  fireEvent.click(blacklist);
  expect(onAction).toHaveBeenCalledWith('BLACKLIST',blacklist);
});
