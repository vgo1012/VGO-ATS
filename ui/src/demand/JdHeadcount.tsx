import { Alert, Button, Space, Typography } from 'antd';
import type { JdSummary } from './api';

type Capacity = Pick<JdSummary, 'headcount' | 'occupiedHeadcount' | 'remainingHeadcount' | 'onboardedHeadcount'>;

export function JdHeadcount({ value, compact = false, onEdit }: { value: Capacity; compact?: boolean; onEdit?: () => void }) {
  const missing = value.headcount == null;
  const occupied = value.occupiedHeadcount;
  const remaining = value.remainingHeadcount;
  const full = !missing && remaining != null && remaining <= 0;
  const summary = missing ? 'HC 待填写' : `已占用 ${occupied ?? '—'} / ${value.headcount} · 剩余 ${remaining ?? '—'}`;
  const delivery = value.onboardedHeadcount != null && !missing ? `已入职 ${value.onboardedHeadcount} / ${value.headcount} · ${value.onboardedHeadcount >= value.headcount! ? '交付完成' : '交付未完成'}` : undefined;
  if (compact) return <Space orientation="vertical" size={0}><span>{summary}</span>{delivery ? <span>{delivery}</span> : null}{full ? <Typography.Text type="warning">HC 已满</Typography.Text> : null}</Space>;
  return <Alert className="modal-alert" type={missing || full ? 'warning' : 'info'} showIcon
    title={full ? `HC 已满 · ${summary}` : summary}
    description={<>{delivery ? <div>{delivery}</div> : null}{missing ? '请先到 JD 编辑填写招聘人数，再新增流程。' : full ? '已入职人数已达 HC，不能确认新的实际入职。需要更多名额，请到 JD 编辑增加 HC。' : '只有实际入职才占用 HC；Offer 发出及进入入职流程都不占名额。推荐和面试可同时推进多位候选人。'}</>}
    action={onEdit ? <Button size="small" onClick={onEdit}>编辑 HC</Button> : undefined} />;
}
