import { Select } from 'antd';
import { cityOptions, normalizeCityName } from './cityCatalog';

export function CitySelect({ value, onChange, disabled }: { value?: string; onChange?: (value: string) => void; disabled?: boolean }) {
  return <Select<string>
    aria-label="工作地区"
    value={value ? normalizeCityName(value) : null}
    onChange={next => onChange?.(next)}
    disabled={Boolean(disabled)}
    showSearch
    optionLabelProp="value"
    optionFilterProp="label"
    options={cityOptions}
    placeholder="按省份选择或搜索城市"
    notFoundContent="未找到城市"
  />;
}
