import { emptyCriteria, type SearchCriteria } from '../search/api';

export const talentFilterGroups = [
  { key: 'education', label: '学历要求', options: ['不限', '高中及以下', '高中', '大专', '本科', '硕士及以上'] },
  { key: 'experience', label: '经验要求', options: ['不限', '在校 / 应届', '1 年以下', '1-3 年', '3-5 年', '5-10 年', '10 年以上'] },
  { key: 'age', label: '年龄要求', options: ['不限', '20-25', '25-30', '30-35', '35-40', '40-50', '50 以上'] },
] as const;
export type TalentFilterChoices = Record<'education' | 'experience' | 'age', string> & { gender?: string | undefined };
export const emptyTalentFilters: TalentFilterChoices = { education: '不限', experience: '不限', age: '不限' };
export function talentFilterCriteria(filters: TalentFilterChoices): SearchCriteria {
  const criteria = emptyCriteria();
  if (filters.education !== '不限') criteria.degrees = filters.education === '硕士及以上' ? ['硕士', '博士'] : filters.education === '高中及以下' ? ['高中', '中专', '初中', '小学'] : [filters.education];
  const years: Record<string, [number, number?]> = { '1 年以下': [0, 1], '1-3 年': [1, 3], '3-5 年': [3, 5], '5-10 年': [5, 10], '10 年以上': [10] };
  const range = years[filters.experience];
  if (range) { criteria.minWorkYears = range[0]; if (range[1] !== undefined) criteria.maxWorkYears = range[1]; }
  if (filters.age !== '不限') {
    const [min, max] = filters.age.split('-').map(value => Number.parseInt(value, 10));
    if (min !== undefined) criteria.minAge = min;
    if (max !== undefined) criteria.maxAge = max - 1;
  }
  if (filters.gender && filters.gender !== '不限') criteria.gender = filters.gender === '男' ? 'MALE' : 'FEMALE';
  return criteria;
}
export function talentFiltersFromUrl(params: URLSearchParams, tab: string): TalentFilterChoices {
  const filters = { ...emptyTalentFilters };
  for (const group of talentFilterGroups) {
    const value = params.get(`${tab}_${group.key}`);
    if (value && value !== '在校 / 应届' && group.options.some(option => option === value)) filters[group.key] = value;
  }
  const gender = params.get(`${tab}_gender`);
  if (gender === '男' || gender === '女') filters.gender = gender;
  return filters;
}
