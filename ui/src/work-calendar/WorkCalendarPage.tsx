import { scrollableTableHeader } from '../components/scrollableTableHeader';
import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Card, Input, Select, Space, Spin, Tag, Typography } from 'antd';
import { useBeforeUnload, useBlocker, useSearchParams } from 'react-router-dom';
import { ApiProblemError } from '../api/problem';
import { createIdempotencyKey } from '../api/idempotency';
import { useSession } from '../auth/session';
import { FeedbackAlert } from '../components/FeedbackAlert';
import { PaginatedTable } from '../components/PaginatedTable';
import { calendarApi, calendarKeys, type CalendarDay, type SaveCalendar } from './api';
import { AnnualCalendar } from './AnnualCalendar';
import './work-calendar.css';

const currentYear = () => Number(new Intl.DateTimeFormat('en', { year: 'numeric', timeZone: 'Asia/Shanghai' }).format(new Date()));
const time = (value: string | null) => value ? new Date(value).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' }) : '尚未发布';
const dayLabels = { WORKDAY: '工作日', RESTDAY: '休息日', HOLIDAY: '节假日' };
function templateDays(year: number): CalendarDay[] {
  const days: CalendarDay[] = [];
  for (let date = new Date(Date.UTC(year, 0, 1)); date.getUTCFullYear() === year; date.setUTCDate(date.getUTCDate() + 1)) {
    days.push({ date: date.toISOString().slice(0, 10), dayType: [0, 6].includes(date.getUTCDay()) ? 'RESTDAY' : 'WORKDAY', description: null });
  }
  return days;
}

export function WorkCalendarPage() {
  const { actor } = useSession();
  const [params, setParams] = useSearchParams();
  const rawYear = Number(params.get('year'));
  const year = Number.isInteger(rawYear) && rawYear >= 2000 && rawYear <= 2100 ? rawYear : currentYear();
  if (!actor?.superAdmin) return <FeedbackAlert type="warning" showIcon title="仅管理员可以维护工作日历" />;
  return <CalendarEditor key={year} year={year} onYear={value => setParams({ year: String(value) })} />;
}

function CalendarEditor({ year, onYear }: { year: number; onYear: (year: number) => void }) {
  const qc = useQueryClient();
  const { message } = App.useApp();
  const [changes, setChanges] = useState<Record<string, CalendarDay>>({});
  const [editVersion, setEditVersion] = useState<string>();
  const [editRevisionId, setEditRevisionId] = useState<string | null>();
  const [dayType, setDayType] = useState<CalendarDay['dayType']>('HOLIDAY');
  const [description, setDescription] = useState('');
  const [rangeStart, setRangeStart] = useState('');
  const [rangeEnd, setRangeEnd] = useState('');
  const [historyOpen, setHistoryOpen] = useState(false);
  const [lastAttempt, setLastAttempt] = useState<{ fingerprint: string; key: string } | null>(null);
  const form = useForm({ defaultValues: { sourceNote: '', reason: '' } });
  const calendar = useQuery({ queryKey: calendarKeys.year(year), queryFn: ({ signal }) => calendarApi.year(year, signal) });
  const history = useQuery({ queryKey: [...calendarKeys.year(year), 'history'], queryFn: () => calendarApi.history(year), enabled: historyOpen });
  const refresh = () => Promise.all([qc.invalidateQueries({ queryKey: calendarKeys.year(year) }), qc.invalidateQueries({ queryKey: calendarKeys.published(year) })]);
  const published = calendar.data?.published;
  const savedDays = published?.days ?? calendar.data?.draft?.days ?? templateDays(year);
  const mutation = useMutation({
    mutationFn: (action: () => Promise<unknown>) => action(),
    onError: error => {
      if (error instanceof ApiProblemError && error.problem.status === 409) {
        void refresh();
      }
    },
  });
  const busy = mutation.isPending;
  const act = (action: () => Promise<unknown>) => mutation.mutate(action);
  const changedDates = Object.keys(changes);
  const dirty = changedDates.length > 0 || form.formState.isDirty;
  const revisionReplaced = editRevisionId !== undefined && (editRevisionId !== (published?.id ?? null) || editVersion !== published?.version);
  const displayedDays = savedDays.map(day => !revisionReplaced ? changes[day.date] ?? day : day);
  const discardChanges = () => { setChanges({}); setEditVersion(undefined); setEditRevisionId(undefined); mutation.reset(); };
  const markDates = (dates: string[]) => {
    if (busy || revisionReplaced) return;
    setEditRevisionId(published?.id ?? null);
    setEditVersion(value => changedDates.length ? value ?? published?.version : published?.version);
    setChanges(current => {
      const next = { ...current };
      for (const date of dates) {
        const original = savedDays.find(day => day.date === date);
        if (!original) continue;
        const value = { date, dayType, description: description.trim() || dayLabels[dayType] };
        if (dates.length === 1 && current[date]?.dayType === value.dayType && current[date]?.description === value.description) {
          delete next[date];
          continue;
        }
        if (original.dayType === value.dayType && original.description === value.description) delete next[date];
        else next[date] = value;
      }
      return next;
    });
  };
  const saveChanges = form.handleSubmit(values => {
    if (busy || revisionReplaced) return;
    const body: SaveCalendar = {
      publishedRevisionId: published?.id ?? null, expectedVersion: published?.version ?? null,
      days: displayedDays, sourceNote: values.sourceNote.trim(), reason: values.reason.trim(),
    };
    const fingerprint = JSON.stringify(body);
    const key = lastAttempt?.fingerprint === fingerprint ? lastAttempt.key : createIdempotencyKey();
    setLastAttempt({ fingerprint, key });
    setEditRevisionId(published?.id ?? null); setEditVersion(published?.version);
    act(async () => {
      const saved = await calendarApi.save(year, body, key);
      qc.setQueryData(calendarKeys.year(year), (current: typeof calendar.data) => current ? { ...current, published: saved, draft: null } : current);
      setChanges({}); setEditVersion(undefined); setEditRevisionId(undefined);
      setLastAttempt(null); form.reset({ sourceNote: saved.sourceNote ?? values.sourceNote, reason: '' });
      await refresh();
      void message.success('工作日历已保存并生效');
    });
  });
  useBeforeUnload(event => { if (dirty) { event.preventDefault(); event.returnValue = ''; } });
  const navigation = useBlocker(({ currentLocation, nextLocation }) => dirty && (currentLocation.pathname !== nextLocation.pathname || currentLocation.search !== nextLocation.search));
  const changeYear = (value: number) => onYear(value);
  return <div className="workspace-page work-calendar-page">
    {navigation.state === 'blocked' ? <FeedbackAlert type="warning" showIcon title="有尚未保存的日历输入" description="离开后已生效的安排会保留，未保存的输入将丢弃。" action={<Space wrap><Button onClick={() => navigation.reset()}>继续编辑</Button><Button onClick={() => navigation.proceed()}>丢弃输入并离开</Button></Space>} /> : null}
    <section className="ledger-heading"><div><Typography.Title level={1}>工作日历</Typography.Title><Typography.Paragraph>维护公司节假日与补班安排，保存后立即生效，无需另行发布。</Typography.Paragraph></div></section>
    <Space wrap><Select aria-label="日历年份" value={year} disabled={busy} onChange={changeYear} options={Array.from({ length: 101 }, (_, index) => ({ value: 2000 + index, label: `${2000 + index} 年` }))} /><Button onClick={() => setHistoryOpen(!historyOpen)}>{historyOpen ? '收起维护历史' : '维护历史'}</Button><Button disabled={busy || changedDates.length > 0} onClick={() => void calendar.refetch()}>刷新日历</Button></Space>
    {calendar.isPending ? <Spin description="正在读取工作日历" /> : null}
    {calendar.error ? <FeedbackAlert occurrence={calendar.error} type="error" title="工作日历加载失败" action={<Button onClick={() => void calendar.refetch()}>重试加载</Button>} /> : null}
    {mutation.error ? <FeedbackAlert occurrence={mutation.error} type="error" title={mutation.error instanceof ApiProblemError ? mutation.error.problem.detail : '保存未完成，输入已保留，请重试。'} action={<Button onClick={() => void calendar.refetch()}>读取最新版本后重试</Button>} /> : null}
    {revisionReplaced ? <FeedbackAlert type="warning" title="日历已被其他管理员更新" description="已显示最新日历，原有未保存标记未提交，请丢弃后重新标记。" action={<Button onClick={discardChanges}>丢弃旧版本标记</Button>} /> : null}
    {calendar.data ? <>
      {calendar.data.nextYearMissing ? <FeedbackAlert type="warning" showIcon title={`${year + 1} 年日历尚未配置`} description="请维护并保存下一年度的节假日与补班安排。" /> : null}
      <Card size="small" title="当前生效安排"><Space wrap><Tag color={published ? 'green' : 'orange'}>{published ? `已发布 · 第 ${published.revision} 版` : '尚未发布'}</Tag><span>发布时间：{time(published?.publishedAt ?? null)}</span><span>维护人：{published?.publishedName ?? published?.publishedBy ?? '—'}</span></Space>{published?.sourceNote ? <Typography.Paragraph>信息来源：{published.sourceNote}</Typography.Paragraph> : null}{!published ? <Typography.Paragraph>{calendar.data.draft ? '已带入此前未生效的日期安排，请核对后保存。' : '下方为周一至周五工作、周末休息的未保存模板，请核对节假日和调休补班。'}</Typography.Paragraph> : null}</Card>
      <Card size="small" title="编辑年度安排">
        <p className="calendar-instruction"><strong>保存后立即生效</strong><span>选类型后点击日期，再次点击取消；Shift + 点击连续标记。</span></p>
        <form onSubmit={event => { void saveChanges(event); }}>
          <div className="calendar-save-fields">
            <label htmlFor="calendar-source">节假日与补班信息来源<Controller name="sourceNote" control={form.control} rules={{ validate: value => Boolean(value.trim()) || '请填写信息来源' }} render={({ field }) => <Input id="calendar-source" maxLength={1000} disabled={busy} aria-invalid={Boolean(form.formState.errors.sourceNote)} {...field} />} />{form.formState.errors.sourceNote ? <span role="alert">{form.formState.errors.sourceNote.message}</span> : null}</label>
            <label htmlFor="calendar-reason">保存或更正原因<Controller name="reason" control={form.control} rules={{ validate: value => Boolean(value.trim()) || '请填写保存原因' }} render={({ field }) => <Input id="calendar-reason" maxLength={500} disabled={busy} aria-invalid={Boolean(form.formState.errors.reason)} {...field} />} />{form.formState.errors.reason ? <span role="alert">{form.formState.errors.reason.message}</span> : null}</label>
          </div>
          <div className="calendar-year-summary"><span>共 {displayedDays.length} 天</span>{(['WORKDAY', 'RESTDAY', 'HOLIDAY'] as const).map(type => <span key={type} className={`calendar-legend calendar-legend-${type.toLowerCase()}`}>{dayLabels[type]} {displayedDays.filter(day => day.dayType === type).length} 天</span>)}</div>
          <div className="calendar-edit-toolbar">
            <div className="calendar-paint-tools" role="group" aria-label="日期标记类型">{(['WORKDAY', 'RESTDAY', 'HOLIDAY'] as const).map(type => <Button key={type} className={`calendar-paint-${type.toLowerCase()}`} aria-pressed={dayType === type} disabled={busy} onClick={() => setDayType(type)}>标记{dayLabels[type]}</Button>)}<Input aria-label="节日或调休说明" maxLength={200} value={description} disabled={busy} onChange={event => setDescription(event.target.value)} placeholder="节日名称或说明（选填）" /></div>
            <div className="calendar-range-tools"><label>起始日期<Input type="date" aria-label="起始日期" min={`${year}-01-01`} max={`${year}-12-31`} value={rangeStart} disabled={busy} onChange={event => setRangeStart(event.target.value)} /></label><label>结束日期<Input type="date" aria-label="结束日期" min={`${year}-01-01`} max={`${year}-12-31`} value={rangeEnd} disabled={busy} onChange={event => setRangeEnd(event.target.value)} /></label><Button disabled={busy || !rangeStart || !rangeEnd || rangeStart > rangeEnd} onClick={() => markDates(savedDays.filter(day => day.date >= rangeStart && day.date <= rangeEnd).map(day => day.date))}>标记连续日期</Button><span role="status">{changedDates.length ? `${changedDates.length} 天待保存` : published ? '所有修改已保存' : '年度安排尚未保存'}</span><Button disabled={!changedDates.length || busy} onClick={discardChanges}>撤销未保存修改</Button><Button htmlType="submit" type="primary" disabled={busy || revisionReplaced || Boolean(published && !dirty)} loading={busy}>保存并生效</Button></div>
          </div>
          <AnnualCalendar year={year} days={displayedDays} editable={!revisionReplaced} busy={busy} changedDates={revisionReplaced ? [] : changedDates} onMark={markDates} />
        </form>
      </Card>
    </> : null}
    {historyOpen ? <Card size="small" title="维护历史">{history.error ? <FeedbackAlert type="error" title="维护历史加载失败" action={<Button onClick={() => void history.refetch()}>重试历史</Button>} /> : null}<PaginatedTable onHeaderRow={() => scrollableTableHeader('维护历史表头，可横向滚动')} rowKey="id" loading={history.isPending} paginationLabel="维护历史分页" dataSource={history.data ?? []} scroll={{ x: 600 }} columns={[{ title: '版本', dataIndex: 'revision' }, { title: '状态', dataIndex: 'status', render: (value: string) => ({ PUBLISHED: '已发布', SUPERSEDED: '历史版本' })[String(value)] ?? value }, { title: '维护人', dataIndex: 'publishedName' }, { title: '发布时间', dataIndex: 'publishedAt', render: time }, { title: '信息来源', dataIndex: 'sourceNote' }, { title: '保存原因', dataIndex: 'publishReason' }]} /></Card> : null}
  </div>;
}
