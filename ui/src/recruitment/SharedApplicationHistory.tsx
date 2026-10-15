import { useQueries } from '@tanstack/react-query';
import { Button, Card, Descriptions, Empty, Space, Timeline } from 'antd';
import { FeedbackAlert } from '../components/FeedbackAlert';
import { LongTextPreview } from '../components/LongTextPreview';
import { UserName } from '../display/BusinessReferences';
import { displayEnum, formatDateTime } from '../display/labels';
import { recruitmentApi, type ApplicationDetails } from './api';
import { compareHistoryTime } from './historyOrder';
import { interviewStepLabel } from './workflowLabels';

export function SharedApplicationHistory({ value }: { value: ApplicationDetails }) {
  const app = value.application;
  const steps = [...value.workflowSteps].sort((left, right) => compareHistoryTime(left.completedAt ?? left.startedAt, right.completedAt ?? right.startedAt) || right.sequenceNo - left.sequenceNo);
  const followUps = useQueries({ queries: steps.map(step => ({ queryKey: ['application-follow-ups', app.id, step.id], queryFn: () => recruitmentApi.followUps(app.id, step.id), retry: false })) });
  const decisionAndOfferHistory = [
    ...value.decisions.map(record => ({ key: record.id, time: record.decidedAt, content: <><b>招聘决定：{displayEnum(record.decision)}</b><p>{formatDateTime(record.decidedAt)} · <UserName id={record.decidedByUserId} /> · {displayEnum(record.recordType)}</p><p><LongTextPreview text={record.reason} label="招聘决定原因"/></p></> })),
    ...value.offerStatusRecords.map(record => ({ key: record.id, time: record.occurredAt, content: <><b>Offer：{displayEnum(record.status)}</b><p>{formatDateTime(record.occurredAt)} · <UserName id={record.operatedByUserId} /> · {displayEnum(record.recordType)}</p><p><LongTextPreview text={record.reason} label="Offer 变更原因"/></p>{record.terminationReason ? <p>终止原因：{displayEnum(record.terminationReason)}</p> : null}{record.sourceReference ? <p>来源：{displayEnum(record.source)} · <LongTextPreview text={record.sourceReference} label="来源引用"/></p> : null}</> })),
  ].sort((left, right) => compareHistoryTime(left.time, right.time));
  return <Space orientation="vertical" style={{ width: '100%' }}>
    <Card size="small" title="流程步骤与跟进历史">
      {steps.length ? <Timeline items={steps.map((step, index) => ({ key: step.id, content: <>
        <b>{interviewStepLabel(step, app.recruitmentType)} · {displayEnum(step.status)}</b>
        <p>负责人：<UserName id={step.ownerUserId} /> · 开始：{formatDateTime(step.startedAt)} · 完成：{formatDateTime(step.completedAt)}</p>
        {step.resultReason ? <p><LongTextPreview text={step.resultReason} label="步骤结果备注"/></p> : null}
        {followUps[index]?.error ? <FeedbackAlert type="error" title="步骤跟进记录读取失败" action={<Button onClick={() => void followUps[index]?.refetch()}>重试步骤跟进</Button>} /> : followUps[index]?.isPending ? <p role="status">正在读取步骤跟进…</p> : [...(followUps[index]?.data ?? [])].sort((left, right) => compareHistoryTime(left.contactedAt, right.contactedAt)).map(record => <div key={record.id}>
          <p>{formatDateTime(record.contactedAt)} · {displayEnum(record.channel)} · <UserName id={record.recordedByUserId} /></p>
          <p><LongTextPreview text={record.summary} label="跟进记录"/></p>{record.nextPlan ? <p>下一步：<LongTextPreview text={record.nextPlan} label="下一步计划"/></p> : null}
        </div>)}
      </> }))} /> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无流程步骤记录" />}
    </Card>
    <Card size="small" title="招聘决定与 Offer 历史">
      <Timeline items={decisionAndOfferHistory} />
      {!value.decisions.length && !value.offerStatusRecords.length ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无招聘决定与 Offer 记录" /> : null}
      {app.onboardingStatus ? <Descriptions size="small" column={{ xs: 1, md: 2 }} items={[{ key: 'status', label: '入职状态', children: displayEnum(app.onboardingStatus) }, { key: 'started', label: '进入入职流程', children: formatDateTime(app.onboardingStartedAt) }, { key: 'actual', label: '实际入职时间', children: formatDateTime(app.actualOnboardedAt) }]} /> : null}
    </Card>
  </Space>;
}
