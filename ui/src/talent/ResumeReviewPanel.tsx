import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { Card, Collapse, Space, Tag } from 'antd';
import type { ResumeFactBundle } from './api';
import { resumeReviewFlags } from './resumeReview';

export function ResumeReviewPanel({ bundle }: { bundle:ResumeFactBundle }) {
  const flags=resumeReviewFlags(bundle);
  return <Card className="resume-review-panel" size="small" title="AI 风险与简历核对" extra={<span>{flags.length} 项待核实</span>}>
    <p>标签依据当前简历结构化事实生成，由归属人结合原简历核实，不代表已认定简历造假。</p>
    {flags.length?<Collapse items={flags.map(flag=>({key:flag.id,label:<Space wrap><Tag color="gold">{flag.label}</Tag><span>{flag.origin==='AI'?'AI 提示':flag.origin==='RULE'?'时间校验':'事实提示'}</span></Space>,children:<>
      <p>{flag.detail}</p>
      <p>请结合原始简历核实。</p>
    </>}))}/>:<Alert type="info" showIcon title="当前解析未标出待核实项" description="这不代表简历真实性已验证，仍请结合岗位要求和原文人工复核。"/>}
  </Card>;
}
