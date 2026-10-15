import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App, ConfigProvider } from 'antd';
import { afterEach, expect, it, vi } from 'vitest';
import { clearCsrfToken } from '../api/http';
import { SessionProvider, useSession, type SessionActor } from './session';
import { sessionQueryKey } from './sessionCache';

const actor=(userId:string):SessionActor=>({userId,displayName:userId,workEmail:`${userId}@example.invalid`,homeDepartmentId:'department',roles:['RECRUITER'],permissions:[],superAdmin:false,mustChangePassword:false,version:'1'});
const json=(value:unknown)=>new Response(JSON.stringify(value),{headers:{'content-type':'application/json'}});
function Harness(){
  const session=useSession();
  return <><output>{session.actor?.userId??'未登录'}</output><button onClick={()=>void session.logout().catch(()=>undefined)}>退出</button><button onClick={()=>void session.login({workEmail:'new@example.invalid',password:'example'}).catch(()=>undefined)}>登录新账号</button></>;
}
afterEach(()=>{cleanup();clearCsrfToken();vi.restoreAllMocks();});

it.each(['401','network'])('preserves the new actor and caches when old logout fails with %s',async failure=>{
  let resolveLogout!:(response:Response)=>void;let rejectLogout!:(reason:Error)=>void;
  const pendingLogout=new Promise<Response>((resolve,reject)=>{resolveLogout=resolve;rejectLogout=reject;});
  const fetchMock=vi.spyOn(globalThis,'fetch').mockImplementation(url=>{
    if((typeof url==='string'?url:'').endsWith('/auth/logout'))return pendingLogout;
    if((typeof url==='string'?url:'').endsWith('/auth/csrf'))return Promise.resolve(json({headerName:'X-CSRF-TOKEN',parameterName:'_csrf',token:'token'}));
    return Promise.resolve(json(actor((typeof url==='string'?url:'').endsWith('/auth/login')?'new':'old')));
  });
  const client=new QueryClient({defaultOptions:{queries:{retry:false},mutations:{retry:false}}});
  render(<ConfigProvider><App><QueryClientProvider client={client}><SessionProvider><Harness/></SessionProvider></QueryClientProvider></App></ConfigProvider>);
  await screen.findByText('old');
  fireEvent.click(screen.getByRole('button',{name:'退出'}));
  await waitFor(()=>expect(fetchMock.mock.calls.some(([url])=>(typeof url==='string'?url:'').endsWith('/auth/logout'))).toBe(true));
  fireEvent.click(screen.getByRole('button',{name:'登录新账号'}));
  await screen.findByText('new');
  client.setQueryData(['talent','new-private'],{owner:'new'});
  await act(()=>{if(failure==='401')resolveLogout(new Response(null,{status:401}));else rejectLogout(new TypeError('network lost'));return Promise.resolve();});
  expect(client.getQueryData(sessionQueryKey)).toEqual(actor('new'));
  expect(client.getQueryData(['talent','new-private'])).toEqual({owner:'new'});
  expect(screen.getByText('new')).toBeInTheDocument();
});

it('keeps the current actor and private caches when logout is not confirmed',async()=>{
  const fetchMock=vi.spyOn(globalThis,'fetch').mockImplementation(url=>{
    if((typeof url==='string'?url:'').endsWith('/auth/logout'))return Promise.reject(new TypeError('network lost'));
    return Promise.resolve(json((typeof url==='string'?url:'').endsWith('/auth/csrf')?{headerName:'X-CSRF-TOKEN',parameterName:'_csrf',token:'token'}:actor('old')));
  });
  const client=new QueryClient({defaultOptions:{queries:{retry:false},mutations:{retry:false}}});
  render(<ConfigProvider><App><QueryClientProvider client={client}><SessionProvider><Harness/></SessionProvider></QueryClientProvider></App></ConfigProvider>);
  await screen.findByText('old');client.setQueryData(['talent','old-private'],{owner:'old'});
  fireEvent.click(screen.getByRole('button',{name:'退出'}));
  await waitFor(()=>expect(fetchMock.mock.calls.some(([url])=>(typeof url==='string'?url:'').endsWith('/auth/logout'))).toBe(true));
  expect(client.getQueryData(sessionQueryKey)).toEqual(actor('old'));
  expect(client.getQueryData(['talent','old-private'])).toEqual({owner:'old'});
  expect(screen.getByText('old')).toBeInTheDocument();
});
