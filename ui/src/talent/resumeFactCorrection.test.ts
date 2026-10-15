import { QueryClient } from '@tanstack/react-query';
import { expect, it } from 'vitest';
import type { ResumeFactBundle } from './api';
import { commandFromFacts, correctionValuesFromFacts, refreshCorrectedFacts } from './resumeFactCorrection';

const bundle: ResumeFactBundle = {
  snapshot: { id: 'snapshot', resumeVersionId: 'resume', parseSnapshotId: 'parse', previousSnapshotId: null,
    snapshotVersion: 1, sourceType: 'AI_PARSE', currentMarker: 'Y', correctedByUserId: null, correctionReason: null,
    createdAt: '2026-09-14', fullName: '候选人', email: null, phone: null, currentRole: '开发', city: '原地点',
    age: 30, birthDate: '1996', gender: null, jobIntention: '原意向' },
  workExperiences: [{ id: 'work-row', factSnapshotId: 'snapshot', itemKey: 'work-stable', employer: '公司', roleName: '工程师',
    startDate: '2020-01-01', startDateText: '2020', endDate: null, endDateText: 'PRESENT',
    descriptionText: '职责原文', responsibilitiesJson: '["开发"]', achievementsJson: '["交付"]' }],
  projectExperiences: [{ id: 'project-row', factSnapshotId: 'snapshot', itemKey: 'project-stable', projectName: '项目', roleName: null,
    startDate: '2021-09-01', startDateText: '2021-09', endDate: null, descriptionText: '项目原文', technologiesJson: '["Java"]', achievementsJson: '[]' }],
  educations: [{ id: 'edu-row', factSnapshotId: 'snapshot', itemKey: 'education-stable', school: '原学校', degreeName: '本科',
    major: '原专业', startDate: '2014-09-01', startDateText: '2014-09', endDate: '2018-01-01', endDateText: '2018', learningMode: '全日制' }],
  skills: [{ id: 'skill-row', factSnapshotId: 'snapshot', itemKey: 'skill-stable', skillName: 'Java', levelName: null, yearsValue: 5 }],
  simpleFacts: [
    { id: 'cert-row', factSnapshotId: 'snapshot', factType: 'CREDENTIAL', itemKey: 'cert-stable', nameValue: '原证书', issuerValue: '颁发机构',
      levelValue: null, startDate: '2022-01-01', startDateText: '2022', endDate: null },
    { id: 'lang-row', factSnapshotId: 'snapshot', factType: 'LANGUAGE', itemKey: 'lang-stable', nameValue: '英语', levelValue: '熟练', issuerValue: null, startDate: null, endDate: null },
    { id: 'tag-row', factSnapshotId: 'snapshot', factType: 'INDUSTRY_TAG', itemKey: 'tag-stable', nameValue: '制造业', levelValue: null, issuerValue: null, startDate: null, endDate: null },
  ],
};

it('edits controlled and fulfillment fields without losing unrelated facts, stable IDs or date precision', () => {
  const values = correctionValuesFromFacts(bundle);
  expect(values.reason).toBe('人工校正');
  values.educations[0]!.school = '更正学校';
  values.educations[0]!.degree = '硕士';
  values.educations[0]!.major = '更正专业';
  values.credentials[0]!.name = '更正证书';
  values.languages[0]!.level = '专业';
  values.city = '新地点'; values.jobIntention = '新意向';
  const result = commandFromFacts(bundle, values);
  expect(result.educations[0]).toEqual({ id: 'education-stable', school: '更正学校', degree: '硕士', major: '更正专业', startDate: '2014-09', endDate: '2018', learningMode: '全日制' });
  expect(result.credentials[0]).toEqual({ id: 'cert-stable', name: '更正证书', issuer: '颁发机构', issuedDate: '2022', expiryDate: null });
  expect(result.languages[0]).toEqual({ id: 'lang-stable', name: '英语', level: '专业' });
  expect(result.candidate).toMatchObject({ city: '新地点', jobIntention: '新意向', birthDate: '1996' });
  expect(result.workExperiences[0]).toMatchObject({ id: 'work-stable', startDate: '2020', endDate: 'PRESENT', responsibilities: ['开发'], achievements: ['交付'] });
  expect(result.projectExperiences[0]).toMatchObject({ id: 'project-stable', startDate: '2021-09', technologies: ['Java'] });
  expect(result.skills[0]?.id).toBe('skill-stable');
  expect(result.industryTags).toEqual(['制造业']);
  expect(bundle.educations[0]?.school).toBe('原学校');
});

it('submits deliberately emptied collections and fields without restoring stale parsed values', () => {
  const values = correctionValuesFromFacts(bundle);
  values.educations = []; values.credentials = []; values.languages = [];
  values.city = '  '; values.jobIntention = '';
  const result = commandFromFacts(bundle, values);
  expect(result).toMatchObject({ educations: [], credentials: [], languages: [], candidate: { city: null, jobIntention: null } });
  expect(result.workExperiences).toHaveLength(1);
});

it('refreshes current consumers while leaving frozen snapshots and historical scores unchanged', async () => {
  const client = new QueryClient();
  const currentKeys = [
    ['talent', 'resume-facts', 'resume'], ['talent', 'candidate-resume-summaries', ['candidate']],
    ['talent', 'candidate', 'candidate'], ['talent', 'candidates', '筛选'], ['talent-search', '查询'],
    ['resume-facts', 'submission-risk', 'resume'], ['matching', 'screening-selection-initial', 'jd', ['candidate']],
  ];
  const frozenKeys = [
    ['talent', 'resume-facts', 'resume', 'historical-snapshot'], ['matching', 'run', 'historical-run'],
    ['matching', 'job-results', 'jd', 'historical-run'], ['talent', 'resume-facts', 'other-resume'],
  ];
  for (const key of [...currentKeys, ...frozenKeys]) client.setQueryData(key, { preserved: true });
  await refreshCorrectedFacts(client, 'candidate', 'resume');
  for (const key of currentKeys) expect(client.getQueryState(key)?.isInvalidated).toBe(true);
  for (const key of frozenKeys) {
    expect(client.getQueryState(key)?.isInvalidated).toBe(false);
    expect(client.getQueryData(key)).toEqual({ preserved: true });
  }
  client.clear();
});
