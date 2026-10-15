import { afterEach, describe, expect, it, vi } from 'vitest';
import { clearCsrfToken, request, requestFile, subscribeToUnauthorized } from './http';
import type { ApiProblemError } from './problem';

describe('HTTP client', () => {
  const requestUrl=(value:RequestInfo|URL)=>typeof value==='string'?value:value instanceof URL?value.href:value.url;
  const tokenResponse=(token:string)=>new Response(JSON.stringify({headerName:'X-CSRF-TOKEN',parameterName:'_csrf',token}),{headers:{'content-type':'application/json'}});

  it.each(['business','download'])('ignores old %s unauthorized responses after login',async kind=>{
    let release!:(value:Response)=>void;
    const delayed=new Promise<Response>(resolve=>{release=resolve;});
    vi.spyOn(globalThis,'fetch').mockImplementation((url)=>Promise.resolve(requestUrl(url).endsWith('/auth/csrf')?tokenResponse('token'):requestUrl(url).endsWith('/auth/login')?new Response(null,{status:204}):delayed));
    const listener=vi.fn();const unsubscribe=subscribeToUnauthorized(listener);
    try{
      const old=kind==='download'?requestFile('/old'):request('/old');
      const failure=expect(old).rejects.toMatchObject({problem:{status:401}});
      await request('/auth/login',{method:'POST'});
      release(new Response(null,{status:401}));await failure;
      expect(listener).not.toHaveBeenCalled();
    }finally{unsubscribe();}
  });

  it('does not retry an old CSRF rejected write after a new login',async()=>{
    let release!:(value:Response)=>void;
    const delayed=new Promise<Response>(resolve=>{release=resolve;});
    const fetchMock=vi.spyOn(globalThis,'fetch').mockImplementation((url)=>Promise.resolve(requestUrl(url).endsWith('/auth/csrf')?tokenResponse('token'):requestUrl(url).endsWith('/auth/login')?new Response(null,{status:204}):delayed));
    const old=request('/old',{method:'POST'});
    const failure=expect(old).rejects.toBeDefined();
    await vi.waitFor(()=>expect(fetchMock.mock.calls.filter(([url])=>requestUrl(url).endsWith('/old'))).toHaveLength(1));
    await request('/auth/login',{method:'POST'});
    release(new Response(JSON.stringify({title:'令牌失效',detail:'失效',status:403,code:'CSRF_TOKEN_INVALID',recoverable:true}),{status:403,headers:{'content-type':'application/json'}}));
    await failure;
    expect(fetchMock.mock.calls.filter(([url])=>requestUrl(url).endsWith('/old'))).toHaveLength(1);
  });

  it.each(['/auth/login','/auth/logout','/me/password'])('blocks a waiting write after successful %s rotation',async path=>{
    let releaseAuth!:(value:Response)=>void;let releaseCsrf!:(value:Response)=>void;
    const auth=new Promise<Response>(resolve=>{releaseAuth=resolve;});
    const csrf=new Promise<Response>(resolve=>{releaseCsrf=resolve;});
    let bootstraps=0;
    const fetchMock=vi.spyOn(globalThis,'fetch').mockImplementation(url=>Promise.resolve(requestUrl(url).endsWith('/auth/csrf')?(++bootstraps===1?tokenResponse('initial'):csrf):auth));
    const rotation=request(path,{method:'POST'});
    await vi.waitFor(()=>expect(fetchMock.mock.calls.some(([url])=>requestUrl(url).endsWith(path))).toBe(true));
    clearCsrfToken();
    const old=request('/waiting-write',{method:'POST',body:{privateData:'old-user'}});
    const failure=expect(old).rejects.toMatchObject({problem:{code:'SESSION_CHANGED'}});
    releaseAuth(new Response(null,{status:204}));await rotation;
    releaseCsrf(tokenResponse('stale'));await failure;
    expect(fetchMock.mock.calls.some(([url])=>requestUrl(url).endsWith('/waiting-write'))).toBe(false);
  });

  it('ignores a stale CSRF bootstrap 401 after successful login',async()=>{
    let releaseAuth!:(value:Response)=>void;let releaseCsrf!:(value:Response)=>void;
    const auth=new Promise<Response>(resolve=>{releaseAuth=resolve;});
    const csrf=new Promise<Response>(resolve=>{releaseCsrf=resolve;});
    let bootstraps=0;
    const fetchMock=vi.spyOn(globalThis,'fetch').mockImplementation(url=>Promise.resolve(requestUrl(url).endsWith('/auth/csrf')?(++bootstraps===1?tokenResponse('initial'):csrf):auth));
    const listener=vi.fn();const unsubscribe=subscribeToUnauthorized(listener);
    try{
      const login=request('/auth/login',{method:'POST'});
      await vi.waitFor(()=>expect(fetchMock.mock.calls.some(([url])=>requestUrl(url).endsWith('/auth/login'))).toBe(true));
      clearCsrfToken();
      const old=request('/waiting-write',{method:'POST'});
      const failure=expect(old).rejects.toMatchObject({problem:{code:'CSRF_BOOTSTRAP_FAILED'}});
      releaseAuth(new Response(null,{status:204}));await login;
      releaseCsrf(new Response(null,{status:401}));await failure;
      expect(listener).not.toHaveBeenCalled();
    }finally{unsubscribe();}
  });

  it('keeps a concurrent login valid when a current business request returns 401',async()=>{
    let releaseLogin!:(value:Response)=>void;
    const loginResponse=new Promise<Response>(resolve=>{releaseLogin=resolve;});
    const fetchMock=vi.spyOn(globalThis,'fetch').mockImplementation(url=>Promise.resolve(requestUrl(url).endsWith('/auth/csrf')?tokenResponse('token'):requestUrl(url).endsWith('/auth/login')?loginResponse:new Response(null,{status:401})));
    const listener=vi.fn();const unsubscribe=subscribeToUnauthorized(listener);
    try{
      const login=request('/auth/login',{method:'POST'});
      await vi.waitFor(()=>expect(fetchMock.mock.calls.some(([url])=>requestUrl(url).endsWith('/auth/login'))).toBe(true));
      await expect(request('/expired')).rejects.toMatchObject({problem:{status:401}});
      expect(listener).toHaveBeenCalledOnce();
      releaseLogin(new Response(null,{status:204}));await expect(login).resolves.toBeUndefined();
    }finally{unsubscribe();}
  });

  it('does not accept a late login success after a newer logout succeeded',async()=>{
    let releaseLogin!:(value:Response)=>void;
    const loginResponse=new Promise<Response>(resolve=>{releaseLogin=resolve;});
    const fetchMock=vi.spyOn(globalThis,'fetch').mockImplementation(url=>Promise.resolve(requestUrl(url).endsWith('/auth/csrf')?tokenResponse('token'):requestUrl(url).endsWith('/auth/login')?loginResponse:new Response(null,{status:204})));
    const login=request('/auth/login',{method:'POST'});
    const failure=expect(login).rejects.toMatchObject({problem:{code:'SESSION_CHANGED'}});
    await vi.waitFor(()=>expect(fetchMock.mock.calls.some(([url])=>requestUrl(url).endsWith('/auth/login'))).toBe(true));
    await request('/auth/logout',{method:'POST'});
    releaseLogin(new Response(null,{status:204}));await failure;
  });

  it('rejects old me success after login instead of returning the previous actor',async()=>{
    let releaseMe!:(value:Response)=>void;
    const me=new Promise<Response>(resolve=>{releaseMe=resolve;});
    vi.spyOn(globalThis,'fetch').mockImplementation(url=>Promise.resolve(requestUrl(url).endsWith('/auth/csrf')?tokenResponse('token'):requestUrl(url).endsWith('/me')?me:new Response(null,{status:204})));
    const old=request('/me');
    const failure=expect(old).rejects.toMatchObject({problem:{code:'SESSION_CHANGED'}});
    await request('/auth/login',{method:'POST'});
    releaseMe(new Response(JSON.stringify({userId:'previous-user'}),{headers:{'content-type':'application/json'}}));
    await failure;
  });

  it('keeps the new token cached when a cleared bootstrap finishes late',async()=>{
    let finishOld!:(response:Response)=>void;
    let finishNew!:(response:Response)=>void;
    const oldResponse=new Promise<Response>(resolve=>{finishOld=resolve;});
    const newResponse=new Promise<Response>(resolve=>{finishNew=resolve;});
    let bootstraps=0;
    const tokens:string[]=[];
    vi.spyOn(globalThis,'fetch').mockImplementation(async(url,init)=>{
      if(typeof url==='string' && url.endsWith('/auth/csrf'))return ++bootstraps===1?oldResponse:newResponse.then(response=>response.clone());
      tokens.push(new Headers(init?.headers).get('X-CSRF-TOKEN')??'');
      return new Response(null,{status:204});
    });
    const oldRequest=request('/old',{method:'POST'});
    clearCsrfToken();
    const newRequest=request('/new',{method:'POST'});
    finishNew(tokenResponse('new-token'));
    await newRequest;
    finishOld(tokenResponse('old-token'));
    await oldRequest;
    await request('/current',{method:'POST'});
    expect(tokens).toEqual(['new-token','old-token','new-token']);
    expect(bootstraps).toBe(2);
  });

  it('shares the new pending bootstrap after a cleared request fails',async()=>{
    let finishOld!:(response:Response)=>void;
    let finishNew!:(response:Response)=>void;
    const oldResponse=new Promise<Response>(resolve=>{finishOld=resolve;});
    const newResponse=new Promise<Response>(resolve=>{finishNew=resolve;});
    let bootstraps=0;
    const tokens:string[]=[];
    vi.spyOn(globalThis,'fetch').mockImplementation(async(url,init)=>{
      if(typeof url==='string' && url.endsWith('/auth/csrf'))return ++bootstraps===1?oldResponse:newResponse.then(response=>response.clone());
      tokens.push(new Headers(init?.headers).get('X-CSRF-TOKEN')??'');
      return new Response(null,{status:204});
    });
    const oldRequest=request('/old',{method:'POST'});
    const failed=expect(oldRequest).rejects.toMatchObject({problem:{code:'CSRF_BOOTSTRAP_FAILED'}});
    clearCsrfToken();
    const newRequest=request('/new',{method:'POST'});
    finishOld(new Response('unavailable',{status:503}));
    await failed;
    const concurrentRequest=request('/concurrent',{method:'POST'});
    finishNew(tokenResponse('new-token'));
    await Promise.all([newRequest,concurrentRequest]);
    expect(bootstraps).toBe(2);
    expect(tokens).toEqual(['new-token','new-token']);
  });

  afterEach(() => {
    clearCsrfToken();
    vi.restoreAllMocks();
  });

  it.each([false, true])('shares one refresh for concurrent rejected tokens (late response: %s)', async (late) => {
    let finishLate!: (response: Response) => void;
    const lateResponse = new Promise<Response>((resolve) => { finishLate = resolve; });
    const invalidToken = () => new Response(JSON.stringify({
      title: '安全令牌失效', status: 403, detail: '请重试', code: 'CSRF_TOKEN_INVALID', recoverable: true,
    }), { status: 403, headers: { 'content-type': 'application/problem+json' } });
    let bootstraps = 0;
    const writes: RequestInit[] = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (url, init) => {
      if (typeof url === 'string' && url.endsWith('/auth/csrf')) {
        bootstraps += 1;
        return tokenResponse(bootstraps === 1 ? 'expired' : 'fresh');
      }
      if (typeof url === 'string' && url.endsWith('/warmup')) return new Response(null, { status: 204 });
      if (init) writes.push(init);
      if (new Headers(init?.headers).get('X-CSRF-TOKEN') === 'expired') {
        return late && typeof url === 'string' && url.endsWith('/second') ? lateResponse : invalidToken();
      }
      return new Response(null, { status: 204 });
    });
    await request('/warmup', { method: 'POST' });
    const options = { method: 'POST', body: { reason: 'test' }, idempotencyKey: 'stable-key' };
    const first = request('/first', options);
    const second = request('/second', options);
    await first;
    finishLate(invalidToken());
    await second;
    expect(bootstraps).toBe(2);
    expect(writes).toHaveLength(4);
    expect(writes.every((init) => init.body === JSON.stringify(options.body)
      && new Headers(init.headers).get('Idempotency-Key') === 'stable-key')).toBe(true);
  });

  it('stops after one CSRF retry when the refreshed token is rejected', async () => {
    const fetch = vi.spyOn(globalThis, 'fetch').mockImplementation((url) => {
      if (typeof url === 'string' && url.endsWith('/auth/csrf')) return Promise.resolve(tokenResponse('rejected'));
      return Promise.resolve(new Response(JSON.stringify({
        title: '拒绝', status: 403, detail: '令牌失效', code: 'CSRF_TOKEN_INVALID', recoverable: true,
      }), { status: 403, headers: { 'content-type': 'application/problem+json' } }));
    });
    await expect(request('/write', { method: 'POST' })).rejects.toMatchObject({ problem: { code: 'CSRF_TOKEN_INVALID' } });
    expect(fetch).toHaveBeenCalledTimes(4);
  });

  it.each(['business', 'download', 'csrf'] as const)(
    'invalidates the session even when the %s 401 response contains malformed JSON',
    async (kind) => {
      const listener = vi.fn();
      const unsubscribe = subscribeToUnauthorized(listener);
      vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('{', {
        status: 401,
        headers: { 'content-type': 'application/problem+json', 'X-Trace-Id': 'expired-trace' },
      }));
      try {
        const result = kind === 'download' ? requestFile('/download-authorizations/grant')
          : request('/applications', kind === 'csrf' ? { method: 'POST' } : {});
        await expect(result).rejects.toMatchObject({ problem: { status: 401 } });
        expect(listener).toHaveBeenCalledOnce();
      } finally {
        unsubscribe();
      }
    },
  );

  it('throws the stable ProblemDetail returned by the backend', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({
          title: 'Forbidden',
          status: 403,
          detail: '无权访问',
          code: 'ACCESS_DENIED',
          traceId: 'trace-1',
          recoverable: false,
        }),
        { status: 403, headers: { 'content-type': 'application/problem+json' } },
      ),
    );

    await expect(request('/platform/status')).rejects.toMatchObject({
      problem: { code: 'ACCESS_DENIED', traceId: 'trace-1' },
    } satisfies { problem: Partial<ApiProblemError['problem']> });
  });

  it('preserves the HTTP status and trace when an error response has malformed JSON', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('{', {
      status: 502, headers: { 'content-type': 'application/json', 'X-Trace-Id': 'proxy-trace' },
    }));
    await expect(request('/applications')).rejects.toMatchObject({
      problem: { code: 'UNEXPECTED_RESPONSE', status: 502, traceId: 'proxy-trace', recoverable: true },
    });
  });

  it('explains a proxy rate limit response without exposing its HTML', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('<html>429</html>', {
      status: 429, headers: { 'content-type': 'text/html' },
    }));
    await expect(request('/auth/login')).rejects.toMatchObject({
      problem: { code: 'TOO_MANY_REQUESTS', status: 429, detail: '请求过于频繁，请稍后重试。', recoverable: true },
    });
  });

  it('still rejects malformed JSON in a successful response', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('{', {
      headers: { 'content-type': 'application/json' },
    }));
    await expect(request('/applications')).rejects.toBeInstanceOf(SyntaxError);
  });

  it('reads a binary attachment with credentials and without caching or redirecting', async () => {
    const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(new Uint8Array([0, 128, 255]), {
      headers: { 'content-type': 'application/pdf', 'content-disposition': 'attachment; filename="resume.pdf"' },
    }));
    const blob = await requestFile('/download-authorizations/grant');
    const buffer = typeof blob.arrayBuffer === 'function'
      ? await blob.arrayBuffer()
      : await new Promise<ArrayBuffer>((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(reader.result as ArrayBuffer);
        reader.onerror = () => reject(new Error(reader.error?.message ?? '读取文件失败'));
        reader.readAsArrayBuffer(blob);
      });
    expect(new Uint8Array(buffer)).toEqual(new Uint8Array([0, 128, 255]));
    expect(fetch.mock.calls[0]?.[1]).toMatchObject({ credentials: 'include', cache: 'no-store', redirect: 'error' });
    expect(new Headers(fetch.mock.calls[0]?.[1]?.headers).get('Accept')).toBe('*/*');
  });

  it('never downloads a login page returned with status 200', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('<html>登录</html>', { headers: { 'content-type': 'text/html' } }));
    await expect(requestFile('/download-authorizations/grant')).rejects.toMatchObject({ problem: { code: 'DOWNLOAD_RESPONSE_INVALID' } });
  });

  it('does not retry a failed single-use download even for a CSRF error', async () => {
    const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({ title: '拒绝', status: 403, detail: '授权无效', code: 'CSRF_TOKEN_INVALID', recoverable: false }), { status: 403, headers: { 'content-type': 'application/problem+json' } }));
    await expect(requestFile('/download-authorizations/grant')).rejects.toMatchObject({ problem: { detail: '授权无效' } });
    expect(fetch).toHaveBeenCalledOnce();
  });

  it('notifies the session boundary when a download returns 401', async () => {
    const listener = vi.fn();
    const unsubscribe = subscribeToUnauthorized(listener);
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('Unauthorized', { status: 401 }));
    await expect(requestFile('/download-authorizations/grant')).rejects.toMatchObject({ problem: { status: 401 } });
    expect(listener).toHaveBeenCalledOnce();
    unsubscribe();
  });

  it('notifies the session boundary when a business request loses authentication', async () => {
    const listener = vi.fn();
    const unsubscribe = subscribeToUnauthorized(listener);
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({
          title: 'Unauthorized',
          status: 401,
          detail: '登录会话已失效',
          code: 'SESSION_REQUIRED',
          recoverable: true,
        }),
        { status: 401, headers: { 'content-type': 'application/problem+json' } },
      ),
    );

    await expect(request('/applications')).rejects.toMatchObject({
      problem: { code: 'SESSION_REQUIRED' },
    });
    expect(listener).toHaveBeenCalledOnce();
    unsubscribe();
  });

  it('bootstraps a CSRF token before unsafe requests', async () => {
    const fetch = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({ headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'token-1' }),
          { status: 200, headers: { 'content-type': 'application/json' } },
        ),
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ userId: 'user-1' }), {
          status: 200,
          headers: { 'content-type': 'application/json' },
        }),
      );

    await request('/auth/login', {
      method: 'POST',
      body: { workEmail: 'hr@example.com', password: 'secret' },
    });

    expect(fetch).toHaveBeenCalledTimes(2);
    const loginInit = fetch.mock.calls[1]?.[1];
    expect(new Headers(loginInit?.headers).get('X-CSRF-TOKEN')).toBe('token-1');
  });

  it('notifies the session boundary when CSRF bootstrap reports an expired session', async () => {
    const listener = vi.fn();
    const unsubscribe = subscribeToUnauthorized(listener);
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({
          title: 'Unauthorized',
          status: 401,
          detail: '登录会话已失效',
          code: 'SESSION_REQUIRED',
          recoverable: true,
        }),
        { status: 401, headers: { 'content-type': 'application/problem+json' } },
      ),
    );

    await expect(request('/applications/1', { method: 'PATCH', body: { reason: 'test' } })).rejects.toMatchObject({
      problem: { code: 'CSRF_BOOTSTRAP_FAILED' },
    });
    expect(listener).toHaveBeenCalledOnce();
    unsubscribe();
  });

  it('passes multipart bodies through without forcing a JSON content type', async () => {
    const fetch = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({ headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'token-2' }),
          { status: 200, headers: { 'content-type': 'application/json' } },
        ),
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ id: 'intake-1' }), {
          status: 200,
          headers: { 'content-type': 'application/json' },
        }),
      );
    const body = new FormData();
    body.set('file', new File(['resume'], 'resume.pdf', { type: 'application/pdf' }));

    await request('/intake-batches/batch-1/files', { method: 'POST', body });

    const uploadInit = fetch.mock.calls[1]?.[1];
    expect(uploadInit?.body).toBe(body);
    expect(new Headers(uploadInit?.headers).has('Content-Type')).toBe(false);
    expect(new Headers(uploadInit?.headers).get('X-CSRF-TOKEN')).toBe('token-2');
  });
});
