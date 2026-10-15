import { ApiProblemError, type ApiProblem } from './problem';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api/v1';
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS']);

interface CsrfResponse {
  headerName: string;
  parameterName: string;
  token: string;
}

let csrfToken: CsrfResponse | undefined;
let csrfRequest: Promise<CsrfResponse> | undefined;
// 身份轮换与普通 CSRF 刷新分别计数，旧请求不能借用新会话继续写入。
let authGeneration = 0;
const unauthorizedListeners = new Set<() => void>();

export interface RequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown;
  idempotencyKey?: string;
}

function isProblem(value: unknown): value is ApiProblem {
  if (!value || typeof value !== 'object') return false;
  const candidate = value as Partial<ApiProblem>;
  return (
    typeof candidate.title === 'string' &&
    typeof candidate.status === 'number' &&
    typeof candidate.detail === 'string' &&
    typeof candidate.code === 'string' &&
    typeof candidate.recoverable === 'boolean'
  );
}

async function parseBody(response: Response): Promise<unknown> {
  if (response.status === 204) return undefined;
  const contentType = response.headers.get('content-type') ?? '';
  if (contentType.includes('json')) {
    try {
      return await response.json();
    } catch (error) {
      // 错误响应可能被代理截断；仍按 HTTP 状态封装错误，成功响应不得静默降级。
      if (!response.ok && error instanceof SyntaxError) return undefined;
      throw error;
    }
  }
  return response.text();
}

async function loadCsrfToken(): Promise<CsrfResponse> {
  if (csrfToken) return csrfToken;
  if (!csrfRequest) {
    const generation = authGeneration;
    const pending: Promise<CsrfResponse> = fetch(`${API_BASE_URL}/auth/csrf`, {
      method: 'GET',
      headers: { Accept: 'application/json' },
      credentials: 'include',
    })
      .then(async (response) => {
        if (response.status === 401) notifyUnauthorized(generation);
        const payload = await parseBody(response);
        if (!response.ok || !payload || typeof payload !== 'object') {
          throw new ApiProblemError({
            title: '安全令牌获取失败',
            status: response.status,
            detail: '暂时无法建立安全会话，请刷新页面重试。',
            code: 'CSRF_BOOTSTRAP_FAILED',
            recoverable: true,
          });
        }
        const candidate = payload as Partial<CsrfResponse>;
        if (
          typeof candidate.headerName !== 'string' ||
          typeof candidate.parameterName !== 'string' ||
          typeof candidate.token !== 'string'
        ) {
          throw new ApiProblemError({
            title: '安全令牌格式错误',
            status: 502,
            detail: '服务返回了无法识别的安全令牌。',
            code: 'CSRF_RESPONSE_INVALID',
            recoverable: true,
          });
        }
        const token = candidate as CsrfResponse;
        // 会话轮换后，旧请求只能完成自身，不能覆盖新一轮安全令牌。
        if (csrfRequest === pending) csrfToken = token;
        return token;
      })
      .finally(() => {
        if (csrfRequest === pending) csrfRequest = undefined;
      });
    csrfRequest = pending;
  }
  return csrfRequest;
}

export function clearCsrfToken(): void {
  csrfToken = undefined;
  csrfRequest = undefined;
}

export function subscribeToUnauthorized(listener: () => void): () => void {
  unauthorizedListeners.add(listener);
  return () => unauthorizedListeners.delete(listener);
}

function notifyUnauthorized(generation: number): void {
  if (generation !== authGeneration) return;
  clearCsrfToken();
  unauthorizedListeners.forEach((listener) => listener());
}

function requireCurrentGeneration(generation: number): void {
  if (generation !== authGeneration) throw new ApiProblemError({
    title: '会话已变化', status: 409, detail: '登录状态已变化，请在当前账号下重新确认操作。',
    code: 'SESSION_CHANGED', recoverable: false,
  });
}

async function execute<T>(path: string, options: RequestOptions, retryCsrf: boolean, file = false, generation = authGeneration): Promise<T> {
  requireCurrentGeneration(generation);
  const { body, idempotencyKey, ...init } = options;
  const method = (options.method ?? 'GET').toUpperCase();
  const multipart = typeof FormData !== 'undefined' && body instanceof FormData;
  const headers = new Headers(options.headers);
  headers.set('Accept', file ? '*/*' : 'application/json');
  if (body !== undefined && !multipart) headers.set('Content-Type', 'application/json');
  if (idempotencyKey) headers.set('Idempotency-Key', idempotencyKey);
  const csrf = SAFE_METHODS.has(method) ? undefined : await loadCsrfToken();
  requireCurrentGeneration(generation);
  if (csrf) {
    headers.set(csrf.headerName, csrf.token);
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    method,
    headers,
    credentials: 'include',
    ...(body === undefined ? {} : { body: multipart ? body : JSON.stringify(body) }),
  });
  if (response.status === 401) notifyUnauthorized(generation);
  const payload = response.ok && file ? response : await parseBody(response);

  if (!response.ok) {
    if (isProblem(payload)) {
      if (payload.code === 'CSRF_TOKEN_INVALID' && retryCsrf && generation === authGeneration) {
        // 同一旧令牌的并发失败共享刷新，迟到响应不能清除已刷新的令牌。
        if (!csrf || csrfToken === csrf) clearCsrfToken();
        return execute<T>(path, options, false, file, generation);
      }
      throw new ApiProblemError(payload);
    }
    const traceId = response.headers.get('X-Trace-Id');
    throw new ApiProblemError({
      title: '请求失败',
      status: response.status,
      detail: response.status === 429 ? '请求过于频繁，请稍后重试。' : '服务返回了无法识别的错误，请稍后重试。',
      code: response.status === 429 ? 'TOO_MANY_REQUESTS' : 'UNEXPECTED_RESPONSE',
      ...(traceId ? { traceId } : {}),
      recoverable: response.status === 429 || response.status >= 500,
    });
  }

  requireCurrentGeneration(generation);
  if (path === '/auth/login' || path === '/auth/logout' || path === '/me/password') {
    authGeneration++;
    clearCsrfToken();
  }
  return payload as T;
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  return execute<T>(path, options, true);
}

export async function requestFile(path: string): Promise<Blob> {
  // Single-use grants must not be retried or cached. Keep failures in the app.
  const response = await execute<Response>(path, { cache: 'no-store', redirect: 'error' }, false, true);
  if (!/^attachment(?:;|$)/i.test(response.headers.get('content-disposition')?.trim() ?? '')) {
    throw new ApiProblemError({
      title: '下载失败', status: 502, code: 'DOWNLOAD_RESPONSE_INVALID', recoverable: true,
      detail: '服务未返回有效下载文件，请稍后重试。',
    });
  }
  return response.blob();
}
