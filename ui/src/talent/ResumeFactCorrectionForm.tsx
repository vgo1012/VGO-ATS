import { Button, Col, Form, Input, InputNumber, Row, Select, Space, Tag, Typography } from 'antd';
import type { InputRef } from 'antd';
import { useRef, useState } from 'react';
import { Controller, type FieldPath, useForm, useWatch } from 'react-hook-form';
import { z } from 'zod';
import type { ResumeFactBundle, ResumeFactsCommand } from './api';
import { correctionValuesFromFacts, type ResumeFactCorrectionValues } from './resumeFactCorrection';

const limits = { fullName: 160, email: 320, phone: 40, role: 160, city: 120, intention: 300, organization: 200, shortLabel: 120, level: 100, reason: 500 } as const;
const optionalText = (maximum: number) => z.string().nullable().refine(value => value === null || value.length <= maximum, `最多 ${maximum} 个字符`);
const itemId = z.string().min(1).max(64).regex(/^[A-Za-z0-9_-]+$/);
const schema = z.object({
  fullName: z.string().max(limits.fullName), email: z.string().max(limits.email).refine(value => value === '' || /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(value), '请输入有效的邮箱地址'),
  phone: z.string().max(limits.phone).regex(/^[0-9+() .-]*$/, '手机号包含不支持的字符'), currentRole: z.string().max(limits.role), city: z.string().max(limits.city),
  age: z.number().int('年龄必须是整数').min(16, '年龄不能小于 16 岁').max(100, '年龄不能大于 100 岁').nullable(), gender: z.enum(['MALE', 'FEMALE', 'OTHER']).nullable(),
  jobIntention: z.string().max(limits.intention), skillsText: z.string(),
  reason: z.string().max(limits.reason, `更正原因最多 ${limits.reason} 个字符`).refine(value => value.trim().length > 0, '请输入更正原因'),
  educations: z.array(z.object({ id: itemId, school: optionalText(limits.organization), degree: optionalText(limits.shortLabel), major: optionalText(limits.role), startDate: z.string().nullable(), endDate: z.string().nullable(), learningMode: z.string().nullable() }).refine(item => Boolean(item.school || item.degree || item.major), '教育经历至少填写一项')).max(30),
  credentials: z.array(z.object({ id: itemId, name: z.string().trim().min(1).max(limits.organization, `证书名称最多 ${limits.organization} 个字符`), issuer: z.string().nullable(), issuedDate: z.string().nullable(), expiryDate: z.string().nullable() })).max(100),
  languages: z.array(z.object({ id: itemId, name: z.string().trim().min(1).max(limits.level, `语言名称最多 ${limits.level} 个字符`), level: z.string().nullable() })).max(50),
});
type Education = ResumeFactsCommand['educations'][number];
type Credential = ResumeFactsCommand['credentials'][number];
type Language = ResumeFactsCommand['languages'][number];
const split = (value: string) => value.split(/[;；]/).map(item => item.trim()).filter(Boolean);
const splitEducation = (value: string) => value.trim() ? value.split(/[;；]/).map(item => item.trim()) : [];
const newId = (group: string) => `${group}_${crypto.randomUUID().replaceAll('-', '')}`;
const shown = (value: string | null) => value?.trim() || '未填写';

export interface ResumeFactCorrectionFormProps { bundle: ResumeFactBundle; busy: boolean; onSubmit: (values: ResumeFactCorrectionValues) => void; onCancel: () => void }

export function ResumeFactCorrectionForm({ bundle, busy, onSubmit, onCancel }: ResumeFactCorrectionFormProps) {
  const { clearErrors, control, formState: { errors }, handleSubmit, setError, setValue } = useForm<ResumeFactCorrectionValues>({ defaultValues: correctionValuesFromFacts(bundle) });
  const educations = useWatch({ control, name: 'educations' });
  const credentials = useWatch({ control, name: 'credentials' });
  const languages = useWatch({ control, name: 'languages' });
  const [educationDraft, setEducationDraft] = useState({ school: '', degree: '', major: '' });
  const [credentialDraft, setCredentialDraft] = useState('');
  const [languageDraft, setLanguageDraft] = useState('');
  const [editingEducation, setEditingEducation] = useState<string | null>(null);
  const [editingCredential, setEditingCredential] = useState<string | null>(null);
  const [editingLanguage, setEditingLanguage] = useState<string | null>(null);
  const [draftError, setDraftError] = useState<string>();
  const educationFocus = useRef<InputRef>(null);
  const credentialFocus = useRef<InputRef>(null);
  const languageFocus = useRef<InputRef>(null);

  const mergeEducation = (current: Education[]): Education[] | null => {
    const columns = [splitEducation(educationDraft.school), splitEducation(educationDraft.degree), splitEducation(educationDraft.major)];
    const counts = columns.map(items => items.length).filter(Boolean);
    if (!counts.length) return current;
    if ((editingEducation !== null && Math.max(...counts) > 1) || new Set(counts).size > 1) {
      setDraftError(editingEducation !== null ? '编辑单条教育经历时不能批量输入' : '学校、学历、专业的非空列条目数必须一致'); educationFocus.current?.focus(); return null;
    }
    const additions = Array.from({ length: counts[0] ?? 0 }, (_, index): Education => ({ id: newId('edu'), school: columns[0]?.[index] ?? null, degree: columns[1]?.[index] ?? null, major: columns[2]?.[index] ?? null, startDate: null, endDate: null, learningMode: null }));
    const next = [...current];
    if (editingEducation !== null) { const index = next.findIndex(item => item.id === editingEducation); const existing = next[index]; if (existing) next[index] = { ...existing, school: additions[0]?.school || null, degree: additions[0]?.degree || null, major: additions[0]?.major || null }; }
    else next.push(...additions);
    if (next.length > 30) { setDraftError('教育经历最多 30 条'); return null; }
    setDraftError(undefined); return next;
  };
  const clearEducation = () => { setEducationDraft({ school: '', degree: '', major: '' }); setEditingEducation(null); };
  const commitEducation = () => { const next = mergeEducation(educations); if (next) { setValue('educations', next, { shouldDirty: true }); clearEducation(); } };

  const mergeSimple = <T extends Credential | Language>(current: T[], draft: string, editing: string | null, title: string, create: (name: string) => T, maximum: number): T[] | null => {
    const names = split(draft); if (!names.length) return current;
    if (editing !== null && names.length > 1) { setDraftError(`编辑单条${title}时不能批量输入`); return null; }
    const next = [...current];
    if (editing !== null) { const index = next.findIndex(item => item.id === editing); const existing = next[index]; if (existing) next[index] = { ...existing, name: names[0] ?? existing.name }; } else next.push(...names.map(create));
    if (next.length > maximum) { setDraftError(`${title}最多 ${maximum} 条`); return null; }
    setDraftError(undefined); return next;
  };
  const mergeCredentials = (current: Credential[]) => mergeSimple(current, credentialDraft, editingCredential, '证书', name => ({ id: newId('credential'), name, issuer: null, issuedDate: null, expiryDate: null }), 100);
  const mergeLanguages = (current: Language[]) => mergeSimple(current, languageDraft, editingLanguage, '语言', name => ({ id: newId('language'), name, level: null }), 50);
  const commitCredential = () => { const next = mergeCredentials(credentials); if (next) { setValue('credentials', next, { shouldDirty: true }); setCredentialDraft(''); setEditingCredential(null); } };
  const commitLanguage = () => { const next = mergeLanguages(languages); if (next) { setValue('languages', next, { shouldDirty: true }); setLanguageDraft(''); setEditingLanguage(null); } };
  const submit = (values: ResumeFactCorrectionValues) => {
    const nextEducations = mergeEducation(values.educations); if (!nextEducations) return;
    const nextCredentials = mergeCredentials(values.credentials); if (!nextCredentials) return;
    const nextLanguages = mergeLanguages(values.languages); if (!nextLanguages) return;
    clearErrors();
    const result = schema.safeParse({ ...values, educations: nextEducations, credentials: nextCredentials, languages: nextLanguages });
    if (result.success) { setDraftError(undefined); onSubmit(result.data); return; }
    const collectionIssue = result.error.issues.find(issue => ['educations', 'credentials', 'languages'].includes(String(issue.path[0])));
    if (collectionIssue) {
      setDraftError(collectionIssue.message);
      const group = collectionIssue.path[0];
      if (group === 'educations') educationFocus.current?.focus();
      if (group === 'credentials') credentialFocus.current?.focus();
      if (group === 'languages') languageFocus.current?.focus();
    }
    result.error.issues.filter(issue => !['educations', 'credentials', 'languages'].includes(String(issue.path[0]))).forEach((issue, index) => { const path = issue.path.join('.') as FieldPath<ResumeFactCorrectionValues>; if (path) setError(path, { type: 'zod', message: issue.message }, { shouldFocus: index === 0 && !collectionIssue }); });
  };

  return <Form layout="vertical" disabled={busy} onFinish={() => { void handleSubmit(submit)(); }}>
    <Row gutter={12}>
      <Col xs={24} sm={12}><TextField name="fullName" label="姓名" maximum={limits.fullName} control={control} error={errors.fullName?.message} /></Col><Col xs={24} sm={12}><TextField name="currentRole" label="当前岗位" maximum={limits.role} control={control} error={errors.currentRole?.message} /></Col>
      <Col xs={24} sm={12}><TextField name="email" label="邮箱" maximum={limits.email} control={control} error={errors.email?.message} /></Col><Col xs={24} sm={12}><TextField name="phone" label="手机号" maximum={limits.phone} control={control} error={errors.phone?.message} /></Col>
      <Col xs={24} sm={12}><TextField name="city" label="城市" maximum={limits.city} control={control} error={errors.city?.message} /></Col>
      <Col xs={12} sm={6}><Form.Item label="年龄" htmlFor="correction-age" validateStatus={errors.age ? 'error' : ''} help={errors.age?.message}><Controller name="age" control={control} render={({ field }) => <InputNumber {...field} id="correction-age" min={16} max={100} precision={0} style={{ width: '100%' }} />} /></Form.Item></Col>
      <Col xs={12} sm={6}><Form.Item label="性别" htmlFor="correction-gender"><Controller name="gender" control={control} render={({ field }) => <Select {...field} id="correction-gender" onChange={value => field.onChange(value ?? null)} allowClear style={{ width: '100%' }} options={[{ value: 'MALE', label: '男' }, { value: 'FEMALE', label: '女' }, { value: 'OTHER', label: '其他' }]} />} /></Form.Item></Col>
    </Row>
    <TextField name="jobIntention" label="求职意向" maximum={limits.intention} control={control} error={errors.jobIntention?.message} />
    <Form.Item label="技能（每行：名称|级别|年限）" htmlFor="correction-skills"><Controller name="skillsText" control={control} render={({ field }) => <Input.TextArea {...field} id="correction-skills" rows={4} />} /></Form.Item>
    <Typography.Title level={5}>教育经历</Typography.Title>
    <Row gutter={8} align="bottom"><Col xs={24} sm={7}><Form.Item label="学校" htmlFor="education-school"><Input ref={educationFocus} id="education-school" value={educationDraft.school} onChange={event => setEducationDraft(value => ({ ...value, school: event.target.value }))} placeholder="多条用；分隔" /></Form.Item></Col><Col xs={24} sm={7}><Form.Item label="学历" htmlFor="education-degree"><Input id="education-degree" value={educationDraft.degree} onChange={event => setEducationDraft(value => ({ ...value, degree: event.target.value }))} placeholder="多条用；分隔" /></Form.Item></Col><Col xs={24} sm={7}><Form.Item label="专业" htmlFor="education-major"><Input id="education-major" value={educationDraft.major} onChange={event => setEducationDraft(value => ({ ...value, major: event.target.value }))} placeholder="多条用；分隔" /></Form.Item></Col><Col xs={24} sm={3}><Form.Item label=" "><Space.Compact block><Button block disabled={busy} onClick={commitEducation} aria-label={editingEducation === null ? '添加教育经历' : '确认更新教育经历'}>{editingEducation === null ? '+' : '确认'}</Button>{editingEducation !== null ? <Button disabled={busy} onClick={clearEducation} aria-label="取消编辑教育经历">取消</Button> : null}</Space.Compact></Form.Item></Col></Row>
    <ItemTags empty="尚未记录教育经历" items={educations} label={item => `${shown(item.school)} · ${shown(item.degree)} · ${shown(item.major)}`} onEdit={item => { setEducationDraft({ school: item.school ?? '', degree: item.degree ?? '', major: item.major ?? '' }); setEditingEducation(item.id); }} onRemove={index => { const removed = educations[index]; if (removed?.id === editingEducation) clearEducation(); setValue('educations', educations.filter((_, i) => i !== index), { shouldDirty: true }); }} busy={busy} />
    <SimpleCollection inputRef={credentialFocus} title="证书" label="证书名称" value={credentialDraft} onChange={setCredentialDraft} onCommit={commitCredential} onCancel={() => { setCredentialDraft(''); setEditingCredential(null); }} editing={editingCredential !== null} items={credentials} onEdit={item => { setCredentialDraft(item.name); setEditingCredential(item.id); }} onRemove={index => { const removed = credentials[index]; if (removed?.id === editingCredential) { setCredentialDraft(''); setEditingCredential(null); } setValue('credentials', credentials.filter((_, i) => i !== index), { shouldDirty: true }); }} busy={busy} />
    <SimpleCollection inputRef={languageFocus} title="语言能力" label="语言名称" value={languageDraft} onChange={setLanguageDraft} onCommit={commitLanguage} onCancel={() => { setLanguageDraft(''); setEditingLanguage(null); }} editing={editingLanguage !== null} items={languages} onEdit={item => { setLanguageDraft(item.name); setEditingLanguage(item.id); }} onRemove={index => { const removed = languages[index]; if (removed?.id === editingLanguage) { setLanguageDraft(''); setEditingLanguage(null); } setValue('languages', languages.filter((_, i) => i !== index), { shouldDirty: true }); }} busy={busy} />
    {draftError ? <Typography.Text type="danger" role="alert">{draftError}</Typography.Text> : null}
    <Form.Item label="更正原因" htmlFor="correction-reason" required validateStatus={errors.reason ? 'error' : ''} help={errors.reason?.message}><Controller name="reason" control={control} render={({ field }) => <Input.TextArea {...field} id="correction-reason" rows={3} maxLength={limits.reason} showCount />} /></Form.Item>
    <Space wrap className="candidate-editor-actions"><Button disabled={busy} onClick={onCancel}>取消更正</Button><Button type="primary" htmlType="submit" aria-label="保存更正" aria-busy={busy} loading={busy}>保存更正</Button></Space>
  </Form>;
}

function ItemTags<T extends { id: string }>({ items, label, empty, onEdit, onRemove, busy }: { items: T[]; label: (item: T) => string; empty: string; onEdit: (item: T, index: number) => void; onRemove: (index: number) => void; busy: boolean }) {
  if (!items.length) return <Typography.Text type="secondary">{empty}</Typography.Text>;
  return <Space wrap>{items.map((item, index) => <Tag key={item.id} closable={!busy} onClose={event => { event.preventDefault(); onRemove(index); }}><Button type="link" size="small" disabled={busy} onClick={() => onEdit(item, index)}>{label(item)}</Button></Tag>)}</Space>;
}
function SimpleCollection<T extends { id: string; name: string }>({ inputRef, title, label, value, onChange, onCommit, onCancel, editing, items, onEdit, onRemove, busy }: { inputRef: React.Ref<InputRef>; title: string; label: string; value: string; onChange: (value: string) => void; onCommit: () => void; onCancel: () => void; editing: boolean; items: T[]; onEdit: (item: T, index: number) => void; onRemove: (index: number) => void; busy: boolean }) {
  return <><Typography.Title level={5}>{title}</Typography.Title><Space.Compact block><Input ref={inputRef} aria-label={label} value={value} onChange={event => onChange(event.target.value)} placeholder="多条用；分隔" /><Button disabled={busy} onClick={onCommit} aria-label={editing ? `确认更新${title}` : `添加${title}`}>{editing ? '确认' : '+'}</Button>{editing ? <Button disabled={busy} onClick={onCancel} aria-label={`取消编辑${title}`}>取消</Button> : null}</Space.Compact><ItemTags items={items} label={item => item.name} empty={`尚未记录${title}`} onEdit={onEdit} onRemove={onRemove} busy={busy} /></>;
}
type Control = ReturnType<typeof useForm<ResumeFactCorrectionValues>>['control'];
function TextField({ name, label, maximum, error, control }: { name: FieldPath<ResumeFactCorrectionValues>; label: string; maximum: number; error?: unknown; control: Control }) {
  const id = `correction-${name}`;
  return <Form.Item label={label} htmlFor={id} validateStatus={error ? 'error' : ''} help={typeof error === 'string' ? error : undefined}><Controller name={name} control={control} render={({ field }) => <Input {...field} value={typeof field.value === 'string' ? field.value : ''} id={id} maxLength={maximum} />} /></Form.Item>;
}
