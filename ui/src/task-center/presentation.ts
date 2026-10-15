import type { Schedule } from './api';

const formatter = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit',
  hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
});

export function taskTime(value: string | null | undefined) {
  return value ? formatter.format(new Date(value)) : '—';
}

export function taskDuration(value: number | null) {
  return value === null ? '—' : value < 1000 ? `${value} 毫秒` : `${(value / 1000).toFixed(2)} 秒`;
}

export function backlogAge(sampledAt: string, oldestReadyAt: string | null) {
  if (!oldestReadyAt) return '—';
  const seconds = Math.max(0, Math.floor((Date.parse(sampledAt) - Date.parse(oldestReadyAt)) / 1000));
  if (seconds < 60) return `${seconds} 秒`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes} 分钟`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} 小时 ${minutes % 60} 分钟`;
  return `${Math.floor(hours / 24)} 天 ${hours % 24} 小时`;
}

export function scheduleState(task: Schedule): { label: string; color: string } {
  if (!task.enabled) return { label: '未启用', color: 'default' };
  if (task.channels.some(channel => channel.running > 0)) return { label: '扫描中', color: 'processing' };
  if (task.channels.some(channel => channel.queued > 0)) return { label: '已排队', color: 'processing' };
  if (task.channels.some(channel => ['FAILED', 'REJECTED'].includes(channel.recentScans[0]?.state ?? ''))) {
    return { label: '最近扫描异常', color: 'error' };
  }
  if (task.channels.every(channel => channel.lastTriggeredAt === null)) return { label: '尚未观测到触发', color: 'default' };
  return { label: '轮次已完成', color: 'default' };
}

export const scanStates: Record<string, string> = {
  QUEUED: '已排队', RUNNING: '扫描中', COMPLETED: '轮次完成（非业务成功）', FAILED: '扫描失败', REJECTED: '线程池拒绝',
};

export const businessStates: Record<string, string> = {
  QUEUED: '排队中', RUNNING: '处理中', CANCELLING: '取消中', SUCCEEDED: '业务成功', FAILED: '业务失败', CANCELLED: '已取消',
};

export const scanErrors: Record<string, string> = {
  SCAN_FAILED: '扫描处理异常，请结合业务记录和服务日志排查',
  SCAN_ABORTED: '扫描意外中断，请检查服务状态',
  DISPATCH_REJECTED: '执行线程池繁忙，任务未进入执行',
  DISPATCH_FAILED: '任务提交失败，请检查执行器状态',
};
