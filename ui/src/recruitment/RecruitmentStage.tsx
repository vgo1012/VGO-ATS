import { useState } from 'react';
import { Button, Tag } from 'antd';
import type { RecruitmentApplication } from './api';
import type { LedgerAction } from './activityApi';
import { LedgerActionDialog } from './LedgerActionDialog';
import { currentStageLabel, recruitmentStatus } from './currentStageLabel';
import './recruitment-ledger.css';

export function RecruitmentStage({ value, onAction }: { value: RecruitmentApplication; onAction: (action: LedgerAction, trigger: HTMLElement, roundNo?: number) => void }) {
  if (recruitmentStatus(value) === '已完成') {
    return <div className="recruitment-stage recruitment-completed" role="group" aria-label="招聘已完成">
      <Tag color="success">已完成</Tag>
      <small>已转入入职流程</small>
      <div className="recruitment-stage-options"><Button className="stage-blacklist" size="small" onClick={event => onAction('BLACKLIST', event.currentTarget)}>黑名单</Button></div>
    </div>;
  }
  if (recruitmentStatus(value) === '已取消') {
    return <div className="recruitment-stage" role="group" aria-label="招聘已取消">
      <div className="recruitment-stage-options">
        <Button autoInsertSpace={false} size="small" className="stage-ended selected" disabled>放弃</Button>
        <Button className="stage-blacklist" size="small" onClick={event => onAction('BLACKLIST', event.currentTarget)}>黑名单</Button>
      </div>
    </div>;
  }
  const activeRounds = value.activeInterviewRoundNos ?? [];
  const completedRound = (value.currentStage === 'PATH_SELECTION' || value.currentStage === 'IN_PROCESS' && value.currentStepCode?.includes('INTERVIEW')) && !activeRounds.length ? value.latestCompletedInterviewRoundNo : undefined;
  const interview = Boolean(completedRound) || activeRounds.length > 0 || value.currentStage === 'IN_PROCESS' && Boolean(value.currentStepCode?.includes('INTERVIEW'));
  const round = completedRound ?? (interview ? value.currentStepRoundNo ?? Number(value.currentStepCode?.match(/ROUND_(\d+)/)?.[1]) : 0);
  const selected = value.currentStage === 'OFFER' ? 'onboarded' : interview ? 'interview' : (['PATH_SELECTION', 'DECISION'].includes(value.currentStage) || value.currentStepCode === 'CLIENT_SUBMISSION') ? 'pending' : undefined;
  return <div className="recruitment-stage" role="group" aria-label={activeRounds.length ? `进行中面试：${activeRounds.map(number=>`第 ${number} 轮`).join('、')}` : completedRound ? `第 ${completedRound} 轮已完成 · 待安排下一步` : currentStageLabel(value)}>
    <div className="recruitment-stage-options">{([{key:'pending',action:'PENDING',text:'待定'},{key:'interview',action:'INTERVIEW',text:'面试'},{key:'ended',action:'ENDED',text:'放弃'},{key:'onboarded',action:'ONBOARDED',text:'入职'}] as const).map(({key,action,text}) => <Button autoInsertSpace={false} size="small" key={key} className={`stage-${key}${selected === key ? ' selected' : ''}`} onClick={event => onAction(action, event.currentTarget)}>{text}</Button>)}<Button className="stage-blacklist" size="small" onClick={event => onAction('BLACKLIST', event.currentTarget)}>黑名单</Button></div>
    <div className="recruitment-rounds"><small>{value.currentStepCode === 'PRE_SUBMISSION_INTERNAL_INTERVIEW' || value.currentStepCode === 'CLIENT_AI_INTERVIEW' ? '次数' : '轮次'}</small>{Array.from({ length: Math.max(6, round || 0, ...activeRounds) }, (_, index) => index + 1).map(number => <Button size="small" key={number} aria-label={`第 ${number} 轮面试`} data-active={activeRounds.includes(number) ? 'true' : undefined} data-completed={completedRound === number ? 'true' : undefined} aria-description={activeRounds.includes(number) ? '进行中' : completedRound === number ? '已完成' : undefined} onClick={event => onAction('INTERVIEW', event.currentTarget, number)}>{number}</Button>)}</div>
  </div>;
}


export function RecruitmentStageActions({ value }: { value: RecruitmentApplication }) {
  const [target, setTarget] = useState<{ action: LedgerAction; trigger: HTMLElement; roundNo?: number }>();
  return <>
    <RecruitmentStage value={value} onAction={(action, trigger, roundNo) => setTarget({ action, trigger, ...(roundNo ? { roundNo } : {}) })} />
    {target ? <LedgerActionDialog key={`${value.id}-${target.action}`} applicationId={value.id} {...target} onClose={() => {
      setTarget(undefined);
      requestAnimationFrame(() => target.trigger.focus({ preventScroll: true }));
    }} /> : null}
  </>;
}
