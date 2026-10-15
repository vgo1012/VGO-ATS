import { Typography } from 'antd';
import { WeightProfiles } from '../matching/WeightProfiles';

export function PersonalPreferencesPage() {
  return <div className="workspace-page">
    <section className="page-hero"><Typography.Title level={1}>个人筛选方案</Typography.Title><Typography.Paragraph>统一管理五维比例、学历／证书／语言附加要求和默认排序，在不同岗位直接选用。必须项保留核对结果，最终决定由授权 HR 作出。</Typography.Paragraph></section>
    <WeightProfiles />
  </div>;
}
