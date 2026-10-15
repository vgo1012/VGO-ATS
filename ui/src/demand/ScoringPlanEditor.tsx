import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { useMutation } from '@tanstack/react-query';
import { App, Checkbox, ConfigProvider, Form, Input, Space, Typography } from 'antd';
import { Controller, useForm, useWatch } from 'react-hook-form';
import { useState } from 'react';
import { WorkspaceDialog } from '../components/WorkspaceDialog';
import { ApiProblemError } from '../api/problem';
import { createIdempotencyKey } from '../api/idempotency';
import { demandApi, type MatchPlanDetails } from './api';
import { PROFESSIONAL_DIMENSIONS } from './scoringRules';
import { ScreeningCriteriaEditor } from './ScreeningCriteriaEditor';
import { criteriaToForm, criterionRequest, type SimpleCriterion } from './criterionForm';

type Values = { name: string; dimensions: Record<typeof PROFESSIONAL_DIMENSIONS[number][0], boolean>; criteria: SimpleCriterion[] };
type Props = { value: MatchPlanDetails | 'new'; onClose: () => void; onSaved: () => Promise<unknown> };
type Submission = { key: string; body: Record<string, unknown>; uncertain: boolean };
const errorText = (error: unknown) => error instanceof ApiProblemError ? error.problem.detail : error instanceof SyntaxError || error instanceof TypeError ? '保存响应未能确认，请重试核对。' : error instanceof Error ? error.message : '保存失败，请重试';


export function ScoringPlanEditor(props: Props) {
  return <ScoringPlanForm key={props.value === 'new' ? 'new' : props.value.version.id} {...props} />;
}

function ScoringPlanForm({ value, onClose, onSaved }: Props) {
  const editing = value === 'new' ? undefined : value;
  const { message } = App.useApp();
  const [localError, setLocalError] = useState<string>();
  const [submission, setSubmission] = useState<Submission>();
  const { control, handleSubmit, setValue, formState: { errors } } = useForm<Values>({ defaultValues: {
    name: editing?.plan.name ?? '',
    dimensions: { skills: true, roleExperience: true, relatedYears: true, responsibilityMatch: true, industryAndProjects: true },
    criteria: criteriaToForm(editing?.criteria),
  } });
  const criteria = useWatch({control, name:'criteria'});
  const bonus = criteria.filter(item => item.enabled && item.level === 'PREFER').reduce((sum, item) => sum + item.bonusPoints, 0);
  const save = useMutation({ mutationFn: (attempt: Submission) => editing
    ? demandApi.updateScoringConfig(editing.version.id, attempt.body, attempt.key)
    : demandApi.createScoringConfig(attempt.body, attempt.key),
    onError: error => setSubmission(current => {
      if (!current) return current;
      const rejected = error instanceof ApiProblemError && [400, 422].includes(error.problem.status);
      // A later validation rejection cannot disprove an earlier unconfirmed write.
      return rejected && !current.uncertain ? undefined : { ...current, uncertain: true };
    }),
  });
  const versionConflict = save.error instanceof ApiProblemError && save.error.problem.status === 409 && save.error.problem.code === 'MATCH_PLAN_VERSION_CONFLICT';
  const submit = handleSubmit(async values => {
    if (versionConflict) {
      await onSaved();
      onClose();
      return;
    }
    setLocalError(undefined);
    let attempt = submission;
    if (!attempt) {
      try {
        if (!Object.values(values.dimensions).every(Boolean)) throw new Error('五个专业维度必须全部参与评分');
        if (bonus > 30) throw new Error('优先条件附加分合计不能超过 30 分');
        const body = { name: values.name.trim(), professionalDimensions: values.dimensions, criteria: values.criteria.filter(item => item.enabled).map(criterionRequest), ...(editing ? { version: editing.version.version } : {}) };
        attempt = { key: createIdempotencyKey(), body: structuredClone(body), uncertain: false };
        setSubmission(attempt);
      } catch (error) { setLocalError(errorText(error)); return; }
    }
    const confirmed = save.data ?? await save.mutateAsync(attempt).catch(() => undefined);
    if (!confirmed) return;
    await onSaved();
    message.success('评分草稿已保存，激活后用于新的匹配');
    onClose();
  }, () => setLocalError('请填写方案名称'));
  return <WorkspaceDialog size="wide" title={editing ? `编辑评分方案 · V${editing.version.versionNo}` : '新建评分方案'} open onCancel={onClose} okText={versionConflict ? '刷新列表' : '保存草稿'} confirmLoading={save.isPending} onOk={() => submit()} okButtonProps={{ disabled: bonus > 30, 'aria-label': versionConflict ? '刷新列表' : '保存草稿' }}>
    {(save.error || localError) ? <Alert className="modal-alert" type="error" showIcon title={localError ?? errorText(save.error)} /> : null}
    {submission && !save.isPending ? <Alert type="warning" showIcon title={versionConflict ? '草稿已被其他管理员修改。请刷新列表后重新打开，核对最新内容再保存。' : save.data ? '草稿已保存，界面更新未完成，请重试继续。' : '草稿保存结果尚未确认，请重试核对后再修改。'} /> : null}
    <ConfigProvider theme={{ components: { Select: { colorTextDisabled: 'var(--ink)' } } }}>
    <Form layout="vertical" aria-label="评分草稿内容" tabIndex={0} disabled={Boolean(submission) || save.isPending}>
      <Form.Item label="方案名称" required validateStatus={errors.name ? 'error' : ''} help={errors.name?.message}>
        <Controller name="name" control={control} rules={{ required: '请输入方案名称', validate: value => Boolean(value.trim()) || '请输入方案名称', maxLength: { value: 120, message: '名称最多 120 字' } }} render={({ field }) => <Input {...field} aria-label="方案名称" placeholder="例如：技术岗位通用方案" maxLength={120} />} />
      </Form.Item>
      <Typography.Title level={2} className="simple-section-title">专业评分</Typography.Title>
      <p className="dialog-description">五个维度始终参与评分，JD 未写明的维度使用明确标注的评价参照，不作为新增硬门槛。HR 可在“个人筛选方案”调整各维度正权重。</p>
      <Space wrap size={[24, 12]}>{PROFESSIONAL_DIMENSIONS.map(([key, label]) => <Controller key={key} name={`dimensions.${key}`} control={control} render={({ field }) => <Checkbox checked={field.value} disabled>{label}</Checkbox>} />)}</Space>
      <Typography.Title level={2} className="simple-section-title">附加要求（可不填）</Typography.Title>
      <ScreeningCriteriaEditor allowHrSelection value={criteria} onChange={value => setValue('criteria', value)} />
    </Form>
    </ConfigProvider>
  </WorkspaceDialog>;
}
