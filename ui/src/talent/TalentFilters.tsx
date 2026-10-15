import { Button } from 'antd';
import type { ReactNode } from 'react';
import { emptyTalentFilters, talentFilterGroups, type TalentFilterChoices } from './talentFilterCriteria';

export function TalentFilters({ value, onApply, actions }: { actions?: ReactNode; value: TalentFilterChoices; onApply: (filters: TalentFilterChoices) => void }) {
  return <form className="talent-filters" aria-label="简历筛选条件" onSubmit={event => event.preventDefault()}>
    {talentFilterGroups.map(group => <div className="talent-filter-row" role="group" aria-label={group.label} key={group.key}>
      <span className="talent-filter-label">{group.label}</span>
      <div className="talent-filter-options">{group.options.map(option => <Button htmlType="button" autoInsertSpace={false} key={option} disabled={option === '在校 / 应届'} title={option === '在校 / 应届' ? '暂未提供在校或应届状态' : undefined} aria-pressed={value[group.key] === option} onClick={() => onApply({ ...value, [group.key]: option })}>{option}</Button>)}</div>
    </div>)}
    <div className="talent-filter-row talent-filter-bottom">
      <span className="talent-filter-label">其他筛选</span>
      <div className="talent-filter-options" role="group" aria-label="性别">
        {['不限', '男', '女'].map(gender => <Button htmlType="button" autoInsertSpace={false} key={gender} aria-pressed={(value.gender ?? '不限') === gender} onClick={() => onApply({ ...value, gender })}>{gender}</Button>)}
      </div>
      <div className="talent-filter-actions">
        {actions}
        <Button htmlType="button" onClick={() => onApply(emptyTalentFilters)}>重置</Button>
      </div>
    </div>
  </form>;
}
