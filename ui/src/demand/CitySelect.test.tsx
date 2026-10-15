import { ConfigProvider } from 'antd';
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { CitySelect } from './CitySelect';
import { cities } from './cityCatalog';

describe('JD work cities', () => {
  it('covers the licensed 2023 mainland municipalities, prefecture cities, and county cities with unique saved names', () => {
    expect(cities.filter(city => city.level === '直辖市')).toHaveLength(4);
    expect(cities.filter(city => city.level === '地级市')).toHaveLength(293);
    expect(cities.filter(city => city.level === '县级市')).toHaveLength(397);
    expect(new Set(cities.map(city => city.value)).size).toBe(cities.length);
    expect(cities).toEqual(expect.arrayContaining([
      expect.objectContaining({ name: '北京市', province: '北京市', value: '北京市' }),
      expect.objectContaining({ name: '杭州市', province: '浙江省', value: '杭州市' }),
      expect.objectContaining({ name: '义乌市', province: '浙江省', level: '县级市', value: '义乌市' }),
      expect.objectContaining({ name: '石河子市', province: '新疆维吾尔自治区', level: '县级市', value: '石河子市' }),
    ]));
  });

  it('shows province and official city name while returning the official city name', async () => {
    const onChange = vi.fn();
    render(<ConfigProvider><CitySelect value="北京" onChange={onChange} /></ConfigProvider>);
    expect(screen.getByText('北京市')).toBeInTheDocument();
    fireEvent.mouseDown(screen.getByLabelText('工作地区'));
    fireEvent.change(screen.getByLabelText('工作地区'), { target: { value: '杭州市' } });
    fireEvent.click(await screen.findByText('杭州市 · 浙江省', { selector: '.ant-select-item-option-content' }));
    expect(onChange).toHaveBeenCalledWith('杭州市');
  });
});
