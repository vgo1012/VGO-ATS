import { scrollableTableHeader } from '../components/scrollableTableHeader';
import { useQuery } from '@tanstack/react-query';
import { Button, Card, Empty, Space, Tag, Typography } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { FeedbackAlert } from '../components/FeedbackAlert';
import { PaginatedTable } from '../components/PaginatedTable';
import { taskCenterApi, taskCenterKeys, type Schedule } from '../task-center/api';
import { TaskDetails } from '../task-center/TaskDetails';
import { backlogAge, scheduleState, taskTime } from '../task-center/presentation';

function backlogCount(task: Schedule) {
  if (task.persistentBacklogStatus === 'UNAVAILABLE') return <Tag color="error">读取失败</Tag>;
  if (task.persistentBacklogStatus === 'UNSUPPORTED') return <Tag>非持久队列</Tag>;
  const count = task.persistentReadyCount ?? 0;
  if (count > 0) return <Tag
    style={{ color: '#7a3e00', background: '#fff7e6', borderColor: '#d46b08' }}>
      可领取 {count}
    </Tag>;
  return <Tag color="success">可领取 0</Tag>;
}

function oldestBacklog(task: Schedule, sampledAt: string | undefined) {
  if (task.persistentBacklogStatus === 'UNAVAILABLE') return '读取失败';
  if (task.persistentBacklogStatus === 'UNSUPPORTED') return '不适用';
  if (!sampledAt || !task.oldestReadyAt) return '—';
  return <Typography.Text title={`最早可领取：${taskTime(task.oldestReadyAt)}`}>
    {backlogAge(sampledAt, task.oldestReadyAt)}
  </Typography.Text>;
}

function ScheduleOverview() {
  const [selected, setSelected] = useState<string>();
  const detailsTriggers = useRef(new Map<string, HTMLElement>());
  const lastSelected = useRef<string | undefined>(undefined);
  useEffect(() => {
    if (!selected && lastSelected.current) detailsTriggers.current.get(lastSelected.current)?.focus();
  }, [selected]);
  const overview = useQuery({ queryKey: taskCenterKeys.overview,
    queryFn: ({ signal }) => taskCenterApi.overview(signal), retry: false,
    refetchOnMount: 'always', refetchOnWindowFocus: false, refetchOnReconnect: false });
  const tasks = overview.data?.tasks ?? [];
  const selectedTask = tasks.find(task => task.code === selected);
  return <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
    <FeedbackAlert type="info" showIcon title="调度观测与业务结果分开查看" description="此处只读，不修改执行计划或开关。扫描轮次完成不等于业务成功；可领取积压来自持久库的瞬时采样，读取失败不会显示为零。企微每日同步尚未实现，不列为已上线任务。时间按北京时间展示，Cron 使用的时区单独标注。" />
    <Space wrap>
      <Tag>登记入口 {tasks.length}</Tag><Tag>已启用 {tasks.filter(task => task.enabled).length}</Tag>
      <Tag>未启用 {tasks.filter(task => !task.enabled).length}</Tag>
      <Button loading={overview.isFetching} onClick={() => void overview.refetch()}>刷新运行状态</Button>
    </Space>
    {overview.error ? <>
      <FeedbackAlert type="error" title="运行状态读取失败" description={overview.error.message} occurrence={overview.errorUpdatedAt} />
      <Typography.Paragraph role="status">当前未取得最新运行状态；已显示内容可能过时，请刷新重试。</Typography.Paragraph>
    </> : null}
    {overview.data ? <Card size="small">
      <Typography.Paragraph>观测范围：当前服务实例 <Typography.Text code style={{ wordBreak: 'break-all' }}>{overview.data.instanceId}</Typography.Text></Typography.Paragraph>
      <Typography.Text type="secondary">观测起点：{taskTime(overview.data.observedSince)} · 最近采样：{taskTime(overview.data.sampledAt)}。调度轮次仅代表当前实例；持久积压读取共享数据库。进入页面查询一次，需要最新状态请手动刷新，本页不自动轮询。</Typography.Text>
    </Card> : null}
    <Card title="调度任务清单">
      <PaginatedTable<Schedule> rowKey="code" dataSource={tasks} loading={overview.isPending} scroll={{ x: 1500 }}
        paginationLabel="调度任务分页" onHeaderRow={() => scrollableTableHeader('调度任务表头，可横向滚动')}
        locale={{ emptyText: <Empty description={overview.error ? '无法读取任务清单' : '尚未发现已登记的调度入口'} /> }} columns={[
          { title: '任务', dataIndex: 'name', width: 220 },
          { title: '所属模块', dataIndex: 'module', width: 110 },
          { title: '观测状态', width: 160, render: (_, task) => { const state = scheduleState(task); return <Tag color={state.color}>{state.label}</Tag>; } },
          { title: '执行规则', width: 220, render: (_, task) => task.scheduleType === 'CRON' ? `${task.expression}（${task.zone}）` : task.idleBackoffMaxMillis ? `提交后唤醒；空闲退避至 ${task.idleBackoffMaxMillis / 1000} 秒检查` : `每 ${Number(task.intervalMillis) / 1000} 秒检查` },
          { title: '持久积压', width: 130, render: (_, task) => backlogCount(task) },
          { title: '最老等待', width: 150, render: (_, task) => oldestBacklog(task, overview.data?.sampledAt) },
          { title: '最近触发', width: 175, render: (_, task) => taskTime(task.channels.map(channel => channel.lastTriggeredAt).filter((time): time is string => Boolean(time)).sort().at(-1)) },
          { title: '下次计划', width: 175, render: (_, task) => !task.enabled ? '未启用' : task.scheduleType === 'CRON' ? taskTime(task.nextCronAt) : task.idleBackoffMaxMillis ? '事件唤醒或退避到期后的调度触发' : '按间隔检查，不保证立即执行' },
          { title: '详情', width: 130, render: (_, task) => <Button ref={element => { if (element) detailsTriggers.current.set(task.code, element); else detailsTriggers.current.delete(task.code); }} aria-label={`查看${task.name}`} onClick={() => { lastSelected.current = task.code; setSelected(task.code); }}>查看运行记录</Button> },
        ]} />
    </Card>
    {selectedTask ? <TaskDetails task={selectedTask} close={() => setSelected(undefined)} /> : null}
  </Space>;
}

export function TaskCenterPage() {
  return <section aria-label="任务运行中心">
    <header className="page-hero">
      <Typography.Title level={1}>任务运行中心</Typography.Title>
      <Typography.Paragraph type="secondary">统一查看系统调度、扫描异常与已有业务执行记录，不另建一套任务执行引擎。</Typography.Paragraph>
    </header>
    <ScheduleOverview />
  </section>;
}
