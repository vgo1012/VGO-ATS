import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { Descriptions, Space, Tabs, Tag } from 'antd';
import type { JdFacts } from './api';

export function JdFactsPanel({ value }: { value: JdFacts }) {
  const professional = value.facts.professional;
  const controlled = value.facts.controlledConditions;
  const fulfillment = value.facts.fulfillmentConditions;
  const tags = (items: string[]) => <Space wrap>{items.length ? items.map((item) => <Tag key={item}>{item}</Tag>) : '未识别'}</Space>;
  return <Tabs size="small" items={[
    { key: 'professional', label: '专业事实', children: <Space orientation="vertical" style={{ width: '100%' }}><Alert type="success" showIcon title="五个专业维度的输入事实；这里不存储权重、分数或推荐结论。" /><Descriptions bordered size="small" column={1} items={[{ key: 'skills', label: '核心技能', children: <Space wrap>{professional.coreSkills.map((item) => <Tag {...(item.required ? { color: 'blue' } : {})} key={item.id}>{item.name}{item.required ? ' · 必需' : ''}</Tag>)}</Space> },{ key: 'roles', label: '岗位经历', children: professional.roleExperiences.map((item) => `${item.role}${item.level ? ` · ${item.level}` : ''}`).join('；') || '未识别' },{ key: 'years', label: '相关年限', children: `最低 ${professional.minimumYears ?? '未指定'} 年；优选 ${professional.preferredYears ?? '未指定'} 年` },{ key: 'responsibilities', label: '职责匹配事实', children: tags(professional.responsibilities) },{ key: 'industry', label: '行业与项目', children: tags([...professional.industryTags, ...professional.projectTags]) }]} /></Space> },
    { key: 'controlled', label: '受控条件', children: <Descriptions bordered size="small" column={1} items={[{ key: 'age', label: '年龄', children: controlled.ageMin == null && controlled.ageMax == null ? '未指定' : `${controlled.ageMin ?? '不限'}–${controlled.ageMax ?? '不限'}` },{ key: 'sex', label: '性别', children: controlled.sex ?? '未指定' },{ key: 'education', label: '学历', children: tags(controlled.educations) },{ key: 'credentials', label: '证书', children: tags(controlled.credentials) },{ key: 'languages', label: '语言', children: controlled.languages.map((item) => `${item.name}${item.level ? ` · ${item.level}` : ''}`).join('；') || '未识别' }]} /> },
    { key: 'fulfillment', label: '履约条件', children: <Descriptions bordered size="small" column={1} items={[{ key: 'locations', label: '地点', children: tags(fulfillment.locations) },{ key: 'availability', label: '到岗时间', children: fulfillment.availability ?? '未指定' },{ key: 'salary', label: '薪资', children: fulfillment.salaryMin == null && fulfillment.salaryMax == null ? '未指定' : `${fulfillment.salaryMin ?? '不限'}–${fulfillment.salaryMax ?? '不限'} / ${fulfillment.salaryPeriod ?? '周期未指定'}` }]} /> },
  ]} />;
}

