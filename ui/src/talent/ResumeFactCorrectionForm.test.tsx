import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { ResumeFactBundle } from './api';
import { ResumeFactCorrectionForm } from './ResumeFactCorrectionForm';
import type { ResumeFactCorrectionValues } from './resumeFactCorrection';

const facts = (): ResumeFactBundle => ({
  snapshot: { id: 's1', resumeVersionId: 'r1', parseSnapshotId: null, previousSnapshotId: null, snapshotVersion: 1, sourceType: 'AI_PARSE', currentMarker: 'CURRENT', correctedByUserId: null, correctionReason: null, createdAt: '2026-01-01', fullName: '张三', email: null, phone: null, currentRole: null, city: null, age: 25, gender: null, jobIntention: null },
  workExperiences: [], projectExperiences: [], skills: [],
  educations: [{ id: 'db1', factSnapshotId: 's1', itemKey: 'edu_existing', school: '旧学校', degreeName: '本科', major: '计算机', startDate: '2020', endDate: '2024', learningMode: 'FULL_TIME' }],
  simpleFacts: [
    { id: 'db2', factSnapshotId: 's1', itemKey: 'credential_existing', factType: 'CREDENTIAL', nameValue: '旧证书', levelValue: null, issuerValue: '机构', startDate: '2023', endDate: 'PRESENT' },
    { id: 'db3', factSnapshotId: 's1', itemKey: 'language_existing', factType: 'LANGUAGE', nameValue: '英语', levelValue: '熟练', issuerValue: null, startDate: null, endDate: null },
  ],
});
afterEach(cleanup);

describe('ResumeFactCorrectionForm compact collections', () => {
  it('按分号保持教育对应关系，并在直接保存时合并全部草稿', async () => {
    const onSubmit = vi.fn<(value: ResumeFactCorrectionValues) => void>();
    render(<ResumeFactCorrectionForm bundle={facts()} busy={false} onSubmit={onSubmit} onCancel={vi.fn()} />);
    fireEvent.change(screen.getByLabelText('学校'), { target: { value: '甲校；乙校' } });
    fireEvent.change(screen.getByLabelText('学历'), { target: { value: '本科;硕士' } });
    fireEvent.change(screen.getByLabelText('专业'), { target: { value: '中文；数学' } });
    fireEvent.change(screen.getByLabelText('证书名称'), { target: { value: 'PMP；软考' } });
    fireEvent.change(screen.getByLabelText('语言名称'), { target: { value: '日语;法语' } });
    fireEvent.click(screen.getByRole('button', { name: '保存更正' }));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    const value = onSubmit.mock.calls[0]?.[0];
    if (!value) throw new Error('表单未提交');
    expect(value.educations.slice(1).map(item => [item.school, item.degree, item.major])).toEqual([['甲校', '本科', '中文'], ['乙校', '硕士', '数学']]);
    expect(value.credentials.slice(1).map(item => item.name)).toEqual(['PMP', '软考']);
    expect(value.languages.slice(1).map(item => item.name)).toEqual(['日语', '法语']);
  }, 15_000);

  it('拒绝教育非空列条目数不一致', async () => {
    const onSubmit = vi.fn<(value: ResumeFactCorrectionValues) => void>();
    render(<ResumeFactCorrectionForm bundle={facts()} busy={false} onSubmit={onSubmit} onCancel={vi.fn()} />);
    fireEvent.change(screen.getByLabelText('学校'), { target: { value: '甲校；乙校' } });
    fireEvent.change(screen.getByLabelText('学历'), { target: { value: '本科' } });
    fireEvent.click(screen.getByRole('button', { name: '添加教育经历' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('非空列条目数必须一致');
    expect(onSubmit).not.toHaveBeenCalled();
  }, 15_000);

  it('编辑保留隐藏元数据，支持移除、取消和忙碌禁用', async () => {
    const onSubmit = vi.fn<(value: ResumeFactCorrectionValues) => void>(); const onCancel = vi.fn();
    const { rerender } = render(<ResumeFactCorrectionForm bundle={facts()} busy={false} onSubmit={onSubmit} onCancel={onCancel} />);
    fireEvent.click(screen.getByRole('button', { name: '旧学校 · 本科 · 计算机' }));
    fireEvent.change(screen.getByLabelText('学校'), { target: { value: '新学校' } });
    fireEvent.click(screen.getByRole('button', { name: '确认更新教育经历' }));
    fireEvent.click(screen.getByRole('button', { name: '旧证书' }));
    fireEvent.change(screen.getByLabelText('证书名称'), { target: { value: '新证书' } });
    fireEvent.click(screen.getByRole('button', { name: '确认更新证书' }));
    fireEvent.click(screen.getByRole('button', { name: '保存更正' }));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect(onSubmit.mock.calls[0]?.[0].educations[0]).toMatchObject({ id: 'edu_existing', school: '新学校', startDate: '2020', endDate: '2024', learningMode: 'FULL_TIME' });
    expect(onSubmit.mock.calls[0]?.[0].credentials[0]).toMatchObject({ id: 'credential_existing', name: '新证书', issuer: '机构', issuedDate: '2023', expiryDate: 'PRESENT' });
    const languageTag = screen.getByRole('button', { name: '英语' }).closest('.ant-tag');
    fireEvent.click(languageTag!.querySelector('.ant-tag-close-icon')!);
    expect(screen.getByText('尚未记录语言能力')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '取消更正' })); expect(onCancel).toHaveBeenCalled();
    rerender(<ResumeFactCorrectionForm bundle={facts()} busy onSubmit={onSubmit} onCancel={onCancel} />);
    expect(screen.getByRole('button', { name: '添加教育经历' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '保存更正' })).toBeDisabled();
  }, 30_000);

  it('按稳定 id 编辑，删除前项后不会改错后项', async () => {
    const onSubmit = vi.fn<(value: ResumeFactCorrectionValues) => void>();
    render(<ResumeFactCorrectionForm bundle={facts()} busy={false} onSubmit={onSubmit} onCancel={vi.fn()} />);
    fireEvent.change(screen.getByLabelText('学校'), { target: { value: '甲校；乙校' } });
    fireEvent.change(screen.getByLabelText('学历'), { target: { value: '本科；硕士' } });
    fireEvent.change(screen.getByLabelText('专业'), { target: { value: '中文；数学' } });
    fireEvent.click(screen.getByRole('button', { name: '添加教育经历' }));
    fireEvent.click(screen.getByRole('button', { name: '乙校 · 硕士 · 数学' }));
    const firstAdded = screen.getByRole('button', { name: '甲校 · 本科 · 中文' }).closest('.ant-tag');
    fireEvent.click(firstAdded!.querySelector('.ant-tag-close-icon')!);
    fireEvent.change(screen.getByLabelText('学校'), { target: { value: '乙校（更新）' } });
    fireEvent.click(screen.getByRole('button', { name: '确认更新教育经历' }));
    fireEvent.click(screen.getByRole('button', { name: '保存更正' }));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect(onSubmit.mock.calls[0]?.[0].educations.map(item => item.school)).toEqual(['旧学校', '乙校（更新）']);
  }, 30_000);

  it('集合校验失败后修正可再次保存', async () => {
    const onSubmit = vi.fn<(value: ResumeFactCorrectionValues) => void>();
    render(<ResumeFactCorrectionForm bundle={facts()} busy={false} onSubmit={onSubmit} onCancel={vi.fn()} />);
    fireEvent.change(screen.getByLabelText('证书名称'), { target: { value: '证'.repeat(201) } });
    fireEvent.click(screen.getByRole('button', { name: '保存更正' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('证书名称最多 200 个字符');
    fireEvent.change(screen.getByLabelText('证书名称'), { target: { value: '新证书' } });
    fireEvent.click(screen.getByRole('button', { name: '保存更正' }));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
  }, 30_000);
});
