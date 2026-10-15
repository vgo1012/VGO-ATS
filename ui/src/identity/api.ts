import { createIdempotencyKey } from '../api/idempotency';
import { request } from '../api/http';

export interface Department {
  id: string;
  code: string;
  name: string;
  parentDepartmentId: string | null;
  assignmentQueueOwnerUserId: string | null;
  assignmentQueueOwnerName: string | null;
  status: 'ACTIVE' | 'DISABLED';
  version: number;
}

export interface AdminUser {
  id: string;
  displayName: string;
  workEmail: string;
  sourceTitle: string | null;
  homeDepartmentId: string;
  homeDepartmentName: string;
  directLeaderUserId: string | null;
  directLeaderName: string | null;
  status: 'ACTIVE' | 'DISABLED';
  mustChangePassword: boolean;
  version: number;
}

export interface CreatedUser {
  userId: string;
  oneTimeCredential: string;
  mustChangePassword: boolean;
  version: string;
}

export interface UserOrganizationAssignments {
  userId: string;
  supportDepartmentIds: string[];
  roles: string[];
}

export interface WecomStoredDepartment {
  id: string;
  departmentId: number;
  parentId: number;
  name: string;
  parentOutsideScope: boolean;
  linkedLocalDepartmentId: string | null;
  linkedLocalDepartmentName: string | null;
}

export interface WecomStoredMember {
  id: string;
  userId: string;
  name: string;
  position: string | null;
  businessEmail: string | null;
  departmentIds: number[];
  directoryStatus: 'VISIBLE' | 'PENDING_VERIFICATION';
  linkedUserAccountId: string | null;
  linkedUserDisplayName: string | null;
  linkedUserWorkEmail: string | null;
}

export interface WecomStoredDirectory {
  corpId: string;
  publishedBatchId: string | null;
  departments: WecomStoredDepartment[];
  members: WecomStoredMember[];
}

export interface HandoverResource {
  resourceType: string;
  resourceId: string;
  displayName: string;
  mandatory: boolean;
  transferable: boolean;
  unresolvedReason: string | null;
}

export interface HandoverPreview {
  sourceUserId: string;
  targetUserId: string;
  resources: HandoverResource[];
  mandatoryCount: number;
  unresolvedCount: number;
}

export interface HandoverBatchDetails {
  batch: {
    id: string;
    status: string;
    totalCount: number;
    succeededCount: number;
    failedCount: number;
    unresolvedCount: number;
  };
  items: Array<{
    id: string;
    resourceType: string;
    resourceId: string;
    status: string;
    errorMessage: string | null;
  }>;
}

export const identityApi = {
  departments: () => request<Department[]>('/admin/departments'),
  deleteDepartment: (department: Department) =>
    request<void>(`/admin/departments/${department.id}`, {
      method: 'DELETE', body: { version: String(department.version), reason: '管理员删除部门' },
    }),
  createDepartment: (body: { name: string; parentDepartmentId?: string | undefined }) =>
    request<Department>('/admin/departments', { method: 'POST', body }),
  updateDepartment: (id: string, body: { name: string; parentDepartmentId?: string | undefined; assignmentQueueOwnerUserId?: string | undefined; status: string; version: string }) =>
    request<Department>(`/admin/departments/${id}`, { method: 'PATCH', body }),
  users: () => request<AdminUser[]>('/admin/users?limit=200'),
  createUser: (body: {
    displayName: string;
    workEmail: string;
    sourceTitle?: string | undefined;
    homeDepartmentId: string;
    directLeaderUserId?: string | undefined;
    supportDepartmentIds: string[];
    roles: string[];
    reason: string;
  }) => request<CreatedUser>('/admin/users', { method: 'POST', body }),
  updateUser: (id: string, body: {
    displayName: string;
    workEmail: string;
    sourceTitle?: string | undefined;
    homeDepartmentId: string;
    directLeaderUserId?: string | undefined;
    supportDepartmentIds: string[];
    version: string;
    reason: string;
  }) => request<AdminUser>(`/admin/users/${id}`, { method: 'PATCH', body }),
  organizationAssignments: (userId: string) =>
    request<UserOrganizationAssignments>(`/admin/users/${userId}/organization-assignments`),
  resetCredential: (id: string, reason: string) =>
    request<CreatedUser>(`/admin/users/${id}/credentials/reset`, { method: 'POST', body: { reason } }),
  disableUser: (id: string, version: number, reason: string, retainHistoricalUnresolved: boolean) =>
    request<void>(`/admin/users/${id}/disable`, {
      method: 'POST',
      body: { version: String(version), reason, retainHistoricalUnresolved },
    }),
  deleteUser: (id: string, version: number, reason: string) =>
    request<void>(`/admin/users/${id}`, {
      method: 'DELETE',
      body: { version: String(version), reason },
    }),
  roles: (userId: string) => request<string[]>(`/admin/role-assignments?userId=${encodeURIComponent(userId)}`),
  assignRole: (userId: string, roleCode: string, reason: string) =>
    request<string[]>('/admin/role-assignments', { method: 'POST', body: { userId, roleCode, reason } }),
  revokeRole: (userId: string, roleCode: string, reason: string) =>
    request<string[]>(`/admin/role-assignments/${userId}/${roleCode}`, { method: 'DELETE', body: { reason } }),
  superAdmins: () => request<AdminUser[]>('/admin/super-admins'),
  wecomDirectory: () => request<WecomStoredDirectory>('/admin/wecom/directory/stored'),
  linkWecomAccount: (memberId: string, userAccountId: string) =>
    request<void>(`/admin/wecom/directory/members/${memberId}/account-link`, {
      method: 'POST', body: { userAccountId, reason: '管理员确认企业微信成员与本地账号为同一人' },
    }),
  unlinkWecomAccount: (memberId: string) =>
    request<void>(`/admin/wecom/directory/members/${memberId}/account-link`, {
      method: 'DELETE', body: { reason: '管理员解除企业微信成员与本地账号关联' },
    }),
  linkWecomDepartment: (departmentId: string, localDepartmentId: string) =>
    request<void>(`/admin/wecom/directory/departments/${departmentId}/local-link`, {
      method: 'POST', body: { localDepartmentId, reason: '管理员确认企业微信来源部门与本地部门对应关系' },
    }),
  unlinkWecomDepartment: (departmentId: string) =>
    request<void>(`/admin/wecom/directory/departments/${departmentId}/local-link`, {
      method: 'DELETE', body: { reason: '管理员解除企业微信来源部门与本地部门对应关系' },
    }),
  grantSuperAdmin: (userId: string, reason: string) =>
    request<string[]>('/admin/super-admins', { method: 'POST', body: { userId, reason } }),
  revokeSuperAdmin: (userId: string, reason: string) =>
    request<string[]>(`/admin/super-admins/${userId}`, { method: 'DELETE', body: { reason } }),
  previewHandover: (sourceUserId: string, targetUserId: string) =>
    request<HandoverPreview>(`/admin/users/${sourceUserId}/handover-previews`, {
      method: 'POST',
      body: { targetUserId },
    }),
  executeHandover: (
    sourceUserId: string,
    body: { targetUserId: string; selectionMode: string; selectedResourceKeys: string[]; reason: string },
  ) => request<HandoverBatchDetails>(`/admin/users/${sourceUserId}/handovers`, {
    method: 'POST',
    body,
    idempotencyKey: createIdempotencyKey(),
  }),
};
