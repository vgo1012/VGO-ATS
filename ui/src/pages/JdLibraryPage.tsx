import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { Button } from 'antd';
import { ApiProblemError } from '../api/problem';
import { useSession } from '../auth/session';
import { SourceJdPanel } from '../demand/SourceJdPanel';
import { demandApi } from '../demand/api';
import { PERMISSIONS, ROLES } from '../auth/accessPolicy';

export function JdLibraryPage() {
  const { actor } = useSession();
  const canManageJds = Boolean(actor?.superAdmin || actor?.roles.includes(ROLES.hrDirector));
  const canCreateJds = Boolean(actor?.superAdmin || actor?.permissions.includes(PERMISSIONS.demandManage));
  const clients = useInfiniteQuery({ queryKey: ['demand', 'clients'], initialPageParam: undefined as string | undefined, queryFn: ({ pageParam }) => demandApi.clients(undefined, pageParam), getNextPageParam: page => page.hasMore ? page.nextCursor ?? undefined : undefined });
  const departments = useQuery({ queryKey: ['demand', 'department-directory'], queryFn: demandApi.departments, enabled:canCreateJds });
  const clientItems = clients.data?.pages.flatMap(page => page.items) ?? [];
  return <div className="workspace-page demand-workspace-page settings-list-page jd-library-page">
    {clients.error ? <Alert occurrence={clients.error} type="error" showIcon title="客户列表读取失败" description={clients.error instanceof ApiProblemError ? clients.error.problem.detail : '操作失败，请稍后重试。'} action={<Button onClick={() => void clients.refetch()}>重试</Button>} /> : null}
    {departments.error ? <Alert occurrence={departments.error} type="error" showIcon title="部门列表读取失败" description={departments.error instanceof ApiProblemError ? departments.error.problem.detail : '操作失败，请稍后重试。'} action={<Button onClick={() => void departments.refetch()}>重试部门</Button>} /> : null}
    <SourceJdPanel canAdmin={canManageJds} canReassignOwner={Boolean(actor?.superAdmin)} canCreate={canCreateJds} {...(actor?.userId ? { actorUserId: actor.userId } : {})} {...(actor?.homeDepartmentId ? { actorHomeDepartmentId: actor.homeDepartmentId } : {})} actorSupportDepartmentIds={actor?.supportDepartmentIds ?? []} clients={clientItems} departments={departments.data ?? []} departmentLoading={canCreateJds && departments.isPending} departmentUnavailable={Boolean(departments.error)} onRetryDepartments={() => departments.refetch()} clientHasMore={Boolean(clients.hasNextPage)} clientLoadingMore={clients.isFetchingNextPage} onLoadMoreClients={() => clients.fetchNextPage()} />
  </div>;
}
