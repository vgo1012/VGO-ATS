import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { DeleteOutlined } from '@ant-design/icons';
import { Button, Checkbox, Form, Select, Space } from 'antd';
import { CONTROLLED_FACTS } from './scoringRules';
import type { SimpleCriterion } from './criterionForm';

const facts = CONTROLLED_FACTS.filter(([code]) => code !== 'AGE' && code !== 'GENDER');
const education = ['高中', '中专', '大专', '本科', '硕士', '博士'].map(value => ({ value, label: value }));

export function ScreeningCriteriaEditor({ value, onChange, allowHrSelection = false }: { value: SimpleCriterion[]; onChange: (value: SimpleCriterion[]) => void; allowHrSelection?: boolean }) {
  const update = (code: string, patch: Partial<SimpleCriterion>) => onChange(value.map(item => item.code === code ? { ...item, ...patch } : item));
  const bonus = value.filter(item => item.enabled && item.level === 'PREFER').reduce((sum, item) => sum + item.bonusPoints, 0);
  return <>
    <p className="dialog-description">学历、证书、语言等岗位要求可设为必须满足、优先加分或仅供参考。必须项单独核对，不自动淘汰。没有证据时显示待核实。</p>
    <div className="simple-criteria">{value.filter(item => item.enabled && facts.some(([code]) => code === item.code)).map(item => <section className="simple-criterion" key={item.code}>
      <header><b>{facts.find(([code]) => code === item.code)?.[1]}</b><Button type="text" icon={<DeleteOutlined />} aria-label={`移除${facts.find(([code]) => code === item.code)?.[1]}要求`} onClick={() => update(item.code, { enabled: false })} /></header>
      <div className="simple-criterion-fields">
        <Form.Item label="如何使用"><Select aria-label={`${item.code}使用方式`} value={item.level} onChange={level => update(item.code, { level, selectableByHr: false })} options={[{ value: 'MUST', label: '必须满足' }, { value: 'PREFER', label: '优先加分' }, { value: 'DISPLAY_ONLY', label: '仅供参考' }]} /></Form.Item>
        {item.level !== 'DISPLAY_ONLY' ? <Form.Item label="符合以下任意一项"><Select aria-label={`${facts.find(([code]) => code === item.code)?.[1]}要求`} mode={item.code === 'EDUCATION' ? 'multiple' : 'tags'} allowClear value={item.values} onChange={values => update(item.code, { values })} tokenSeparators={['，', ',']} placeholder={item.code === 'EDUCATION' ? '可多选学历档位' : '输入岗位要求，回车添加'} options={item.code === 'EDUCATION' ? education : []} /></Form.Item> : null}
        {item.level === 'PREFER' ? <Form.Item label="符合时加分"><Select aria-label={`${item.code}附加分`} value={item.bonusPoints} onChange={bonusPoints => update(item.code, { bonusPoints })} options={[5, 10, 20].map(value => ({ value, label: `加 ${value} 分` }))} /></Form.Item> : null}
      </div>
      {allowHrSelection && item.level === 'PREFER' ? <Checkbox checked={item.selectableByHr} onChange={event => update(item.code, { selectableByHr: event.target.checked })}>HR 可自行选择是否使用这项加分</Checkbox> : null}
    </section>)}</div>
    <Space wrap><Select aria-label="添加附加要求" value={null} placeholder="添加学历、证书、语言要求" style={{ minWidth: 240 }} onChange={(code: string) => update(code, { enabled: true })} options={facts.filter(([code]) => !value.some(item => item.code === code && item.enabled)).map(([value, label]) => ({ value, label }))} /><span>附加分合计 {bonus} / 30 分</span></Space>
    {bonus > 30 ? <Alert showIcon type="error" title="附加分合计不能超过 30 分" /> : null}
  </>;
}
