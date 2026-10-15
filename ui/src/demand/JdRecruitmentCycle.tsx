import { Alert, Space, Typography } from 'antd';
import { formatDateTime } from '../display/labels';
import type { JdSummary } from './api';

export function JdRecruitmentCycle({ value, now, compact = false }: { value: JdSummary; now: number; compact?: boolean }) {
  if (value.recruitmentCycleDays == null) return <Typography.Text type="secondary">招聘周期待填写</Typography.Text>;
  const due = Date.parse(value.createTime) + value.recruitmentCycleDays * 86_400_000;
  if (!Number.isFinite(due)) return <Typography.Text type="secondary">招聘周期 {value.recruitmentCycleDays} 天 · 开始时间待确认</Typography.Text>;
  const deadline = formatDateTime(new Date(due).toISOString());
  const overdue = value.status === 'ACTIVE' && value.headcount != null && value.onboardedHeadcount != null
    && value.onboardedHeadcount < value.headcount && now > due;
  const title = overdue ? `招聘已超期 ${Math.ceil((now - due) / 86_400_000)} 天` : `招聘周期 ${value.recruitmentCycleDays} 天`;
  if (compact) return <Space orientation="vertical" size={0}>
    <Typography.Text {...(overdue ? { type: 'warning' as const } : {})}>{title}</Typography.Text>
    <Typography.Text type="secondary">{deadline} 到期</Typography.Text>
  </Space>;
  return <Alert className="modal-alert" type={overdue ? 'warning' : 'info'} showIcon title={title}
    description={`从 JD 创建日起计算，${deadline} 到期。${overdue ? '实际入职尚未达到目标人数，请及时跟进入职或在 JD 更新招聘周期；超期不影响继续招聘。' : '超期且实际入职未达 HC 时提示，更新 JD 不会重置起算时间。'}`} />;
}
