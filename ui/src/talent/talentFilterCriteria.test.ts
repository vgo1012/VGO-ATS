import { describe, expect, it } from 'vitest';
import { emptyTalentFilters, talentFilterCriteria, talentFiltersFromUrl } from './talentFilterCriteria';

describe('resume button filters', () => {
  it('maps selected groups to combined ES criteria with exclusive experience bounds', () => {
    expect(talentFilterCriteria({education:'硕士及以上',experience:'1-3 年',age:'25-30',gender:'女'})).toMatchObject({degrees:['硕士','博士'],minWorkYears:1,maxWorkYears:3,minAge:25,maxAge:29,gender:'FEMALE'});
    expect(talentFilterCriteria({...emptyTalentFilters,experience:'10 年以上',age:'50 以上'})).toMatchObject({minWorkYears:10,minAge:50});
    expect(talentFilterCriteria(emptyTalentFilters)).not.toHaveProperty('minWorkYears');
  });
  it('restores only valid choices and keeps personal and shared filters independent', () => {
    const params=new URLSearchParams('pool_education=本科&shared_education=大专&pool_age=bad');
    expect(talentFiltersFromUrl(params,'pool')).toEqual({...emptyTalentFilters,education:'本科'});
    expect(talentFiltersFromUrl(params,'shared')).toEqual({...emptyTalentFilters,education:'大专'});
  });
});
