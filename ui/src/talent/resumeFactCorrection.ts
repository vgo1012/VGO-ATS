import type { QueryClient } from '@tanstack/react-query';
import type { ResumeFactBundle, ResumeFactsCommand } from './api';
import { factDate } from './resumeDates';

export interface ResumeFactCorrectionValues {
  fullName: string;
  email: string;
  phone: string;
  currentRole: string;
  city: string;
  age: number | null;
  gender: 'MALE' | 'FEMALE' | 'OTHER' | null;
  jobIntention: string;
  skillsText: string;
  educations: ResumeFactsCommand['educations'];
  credentials: ResumeFactsCommand['credentials'];
  languages: ResumeFactsCommand['languages'];
  reason: string;
}

const optional = (value: string | null | undefined) => value?.trim() || null;
function jsonList(value: string): string[] {
  try { const parsed: unknown = JSON.parse(value); return Array.isArray(parsed) ? parsed.map(String) : []; }
  catch { return []; }
}
const skillsText = (bundle: ResumeFactBundle) => bundle.skills.map(item => [item.skillName, item.levelName ?? '', item.yearsValue ?? ''].join('|')).join('\n');

export function correctionValuesFromFacts(bundle: ResumeFactBundle): ResumeFactCorrectionValues {
  return {
    fullName: bundle.snapshot.fullName ?? '', email: bundle.snapshot.email ?? '', phone: bundle.snapshot.phone ?? '',
    currentRole: bundle.snapshot.currentRole ?? '', city: bundle.snapshot.city ?? '', age: bundle.snapshot.age ?? null,
    gender: bundle.snapshot.gender ?? null, jobIntention: bundle.snapshot.jobIntention ?? '', skillsText: skillsText(bundle),
    educations: bundle.educations.map(item => ({ id: item.itemKey, school: item.school, degree: item.degreeName, major: item.major,
      startDate: factDate(item, 'start'), endDate: factDate(item, 'end'), learningMode: item.learningMode })),
    credentials: bundle.simpleFacts.filter(item => item.factType === 'CREDENTIAL').map(item => ({ id: item.itemKey,
      name: item.nameValue ?? '', issuer: item.issuerValue, issuedDate: factDate(item, 'start'), expiryDate: factDate(item, 'end') })),
    languages: bundle.simpleFacts.filter(item => item.factType === 'LANGUAGE').map(item => ({ id: item.itemKey, name: item.nameValue ?? '', level: item.levelValue })),
    reason: '人工校正',
  };
}

// 更正接口接收完整事实；未开放编辑的履历、原文日期精度和标签必须原样保留。
export function commandFromFacts(bundle: ResumeFactBundle, values: ResumeFactCorrectionValues): ResumeFactsCommand {
  const simple = (type: string) => bundle.simpleFacts.filter(item => item.factType === type);
  return {
    candidate: { fullName: optional(values.fullName), email: optional(values.email), phone: optional(values.phone),
      currentRole: optional(values.currentRole), city: optional(values.city), age: values.age ?? null,
      birthDate: bundle.snapshot.birthDate ?? null, gender: values.gender ?? null, jobIntention: optional(values.jobIntention) },
    workExperiences: bundle.workExperiences.map(item => ({ id: item.itemKey, employer: item.employer, role: item.roleName,
      startDate: factDate(item, 'start'), endDate: factDate(item, 'end'), description: item.descriptionText,
      responsibilities: jsonList(item.responsibilitiesJson), achievements: jsonList(item.achievementsJson) })),
    projectExperiences: bundle.projectExperiences.map(item => ({ id: item.itemKey, projectName: item.projectName, role: item.roleName,
      startDate: factDate(item, 'start'), endDate: factDate(item, 'end'), description: item.descriptionText,
      technologies: jsonList(item.technologiesJson), achievements: jsonList(item.achievementsJson) })),
    educations: values.educations.map(item => ({ ...item, school: optional(item.school), degree: optional(item.degree),
      major: optional(item.major), startDate: optional(item.startDate), endDate: optional(item.endDate), learningMode: optional(item.learningMode) })),
    skills: values.skillsText === skillsText(bundle)
      ? bundle.skills.map(item => ({ id: item.itemKey, name: item.skillName, level: item.levelName, years: item.yearsValue }))
      : values.skillsText.split('\n').map(line => line.trim()).filter(Boolean).map((line, index) => {
        const [name, level, years] = line.split('|').map(value => value.trim());
        return { id: `skill_${index + 1}`, name: name || `未命名技能${index + 1}`, level: level || null, years: years && Number.isFinite(Number(years)) ? Number(years) : null };
      }),
    credentials: values.credentials.map(item => ({ ...item, name: item.name.trim(), issuer: optional(item.issuer),
      issuedDate: optional(item.issuedDate), expiryDate: optional(item.expiryDate) })),
    languages: values.languages.map(item => ({ ...item, name: item.name.trim(), level: optional(item.level) })),
    industryTags: simple('INDUSTRY_TAG').flatMap(item => item.nameValue ? [item.nameValue] : []),
    responsibilityTags: simple('RESPONSIBILITY_TAG').flatMap(item => item.nameValue ? [item.nameValue] : []),
    unknownItems: simple('UNKNOWN').flatMap(item => item.nameValue ? [item.nameValue] : []),
  };
}

export async function refreshCorrectedFacts(client: QueryClient, candidateId: string, resumeVersionId: string) {
  await Promise.all([
    client.invalidateQueries({ queryKey: ['talent', 'resume-facts', resumeVersionId], exact: true }),
    client.invalidateQueries({ queryKey: ['talent', 'candidate-resume-summaries'] }),
    client.invalidateQueries({ queryKey: ['talent', 'candidate', candidateId], exact: true }),
    client.invalidateQueries({ queryKey: ['talent', 'candidates'] }),
    client.invalidateQueries({ queryKey: ['talent-search'] }),
    client.invalidateQueries({ queryKey: ['matching', 'screening-selection-initial'] }),
    client.invalidateQueries({ queryKey: ['resume-facts', 'submission-risk', resumeVersionId], exact: true }),
  ]);
}
