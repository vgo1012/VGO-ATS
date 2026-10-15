import { scrollableTableHeader } from '../components/scrollableTableHeader';
import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { Button, Card, Drawer, Empty, Space, Table, Tag, Typography } from 'antd';
import { FeedbackAlert } from '../components/FeedbackAlert';
import { PaginatedTable } from '../components/PaginatedTable';
import { taskCenterApi, taskCenterKeys, type BusinessTask, type Scan, type Schedule } from './api';
import { businessStates, scanErrors, scanStates, taskDuration, taskTime } from './presentation';
import { mailResumeApi } from '../mail-resume/api';

function MailRunSummary({ task }: { task: BusinessTask }) {
  const run = useQuery({
    queryKey: ['mail-resume', 'run', task.resultResourceId ?? task.resourceId],
    queryFn: () => mailResumeApi.run(task.resultResourceId ?? task.resourceId),
    enabled: task.taskType === 'MAIL_RESUME_INGEST', retry: false,
  });
  if (task.taskType !== 'MAIL_RESUME_INGEST') return <>{task.errorDetail ?? task.errorCode ?? '—'}</>;
  if (run.isPending) return <>正在读取采集统计…</>;
  if (run.error || !run.data) return <>{task.errorDetail ?? task.errorCode ?? '采集统计暂不可用'}</>;
  const value=run.data;
  return <>{`邮件 ${value.scannedCount} · 附件 ${value.attachmentCount} · 自动匹配 ${value.autoMatchedCount} · 待校验 ${value.reviewRequiredCount} · 重复 ${value.duplicateCount} · 失败 ${value.failedCount}`}{value.recentErrorDetail?` · ${value.recentErrorDetail}`:''}</>;
}

function BusinessHistory({ task }: { task: Schedule }) {
  const history = useInfiniteQuery({
    queryKey: taskCenterKeys.history(task.code), initialPageParam: '',
    queryFn: ({ pageParam, signal }) => taskCenterApi.history(task.code, pageParam, signal),
    getNextPageParam: last => last.hasMore ? last.nextCursor ?? undefined : undefined,
    retry: false, refetchOnWindowFocus: false, refetchOnReconnect: false,
  });
  const records = history.data?.pages.flatMap(page => page.items) ?? [];
  return <Card title="持久化业务任务" extra={<Button loading={history.isFetching} onClick={() => void history.refetch()}>刷新业务记录</Button>}>
    <Typography.Paragraph type="secondary">以下来自原有数据库任务记录，跨服务重启保留。业务成功与失败以此处为准，不以扫描轮次推断。</Typography.Paragraph>
    {history.error ? <>
      <FeedbackAlert type="error" title="业务记录读取失败" description={history.error.message} occurrence={history.errorUpdatedAt} />
      <Typography.Paragraph role="status">未取得最新业务记录，已加载内容可能过时。请点击“刷新业务记录”重试。</Typography.Paragraph>
    </> : null}
    <PaginatedTable<BusinessTask> rowKey="taskId" dataSource={records} loading={history.isPending}
      scroll={{ x: 1150 }} onHeaderRow={() => scrollableTableHeader('业务记录表头，可横向滚动')}
      totalLabel="已加载" paginationLabel="业务记录分页" locale={{ emptyText: history.error ? '暂时无法读取业务记录' : '尚无此类业务任务，不代表扫描未运行' }}
      columns={[
        { title: '任务编号', dataIndex: 'taskId', width: 210, render: (value: string) => <Typography.Text copyable style={{ wordBreak: 'break-all' }}>{value}</Typography.Text> },
        { title: '类型', dataIndex: 'taskType', width: 170 },
        { title: '业务状态', dataIndex: 'status', width: 115, render: (value: string) => <Tag color={value === 'FAILED' ? 'error' : value === 'SUCCEEDED' ? 'success' : 'default'}>{businessStates[value] ?? value}</Tag> },
        { title: '进度', dataIndex: 'progressPercent', width: 70, render: (value: number) => `${value}%` },
        { title: '尝试次数', width: 90, render: (_, value) => `${value.attemptCount}/${value.maxAttempts}` },
        { title: '创建时间', dataIndex: 'createTime', width: 175, render: taskTime },
        { title: '开始时间', dataIndex: 'startedAt', width: 175, render: taskTime },
        { title: '结束时间', dataIndex: 'finishedAt', width: 175, render: taskTime },
        { title: '结果说明', width: 420, render: (_, value) => <MailRunSummary task={value} /> },
      ]} />
    {history.hasNextPage ? <Button loading={history.isFetchingNextPage} disabled={history.isFetching} onClick={() => void history.fetchNextPage()}>加载更早的业务记录</Button> : null}
  </Card>;
}

export function TaskDetails({ task, close }: { task: Schedule; close: () => void }) {
  return <Drawer open title={task.name} size="large" onClose={close} destroyOnHidden>
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      <Typography.Paragraph>扫描观测仅属于当前服务实例，每通道最多保留最近 20 个结束轮次，重启清空。空轮询、业务失败被内部处理后返回，都可能显示“轮次完成”，不代表业务成功。</Typography.Paragraph>
      {task.channels.map(channel => <Card key={channel.code} title={channel.code}>
        <Space wrap><Tag>触发 {channel.triggers}</Tag><Tag>合并 {channel.coalesced}</Tag><Tag>拒绝 {channel.rejected}</Tag><Tag>扫描异常 {channel.failed}</Tag></Space>
        <Table<Scan> rowKey="runId" size="small" dataSource={channel.recentScans} pagination={false} scroll={{ x: 850 }}
          onHeaderRow={() => scrollableTableHeader('扫描记录表头，可横向滚动')}
          locale={{ emptyText: <Empty description={task.enabled ? '本实例尚未观测到扫描轮次' : '当前任务未启用'} /> }} columns={[
            { title: '轮次状态', dataIndex: 'state', width: 200, render: (value: string) => scanStates[value] ?? value },
            { title: '入队时间', dataIndex: 'queuedAt', width: 175, render: taskTime },
            { title: '实际开始', dataIndex: 'startedAt', width: 175, render: taskTime },
            { title: '实际结束', dataIndex: 'finishedAt', width: 175, render: taskTime },
            { title: '执行耗时', dataIndex: 'durationMillis', width: 110, render: taskDuration },
            { title: '说明', dataIndex: 'errorCode', width: 230, render: (value: string | null) => value ? scanErrors[value] ?? value : '—' },
          ]} />
      </Card>)}
      {task.businessTypes.length ? <BusinessHistory key={task.code} task={task} /> : <Typography.Paragraph type="secondary">
        此入口没有关联通用异步任务表，不伪造持久化业务历史。简历流转请查看“结算业务记录”；其他处理结果仍以原业务页面和服务日志为准。
      </Typography.Paragraph>}
    </Space>
  </Drawer>;
}
