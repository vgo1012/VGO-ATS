import { Button, Space } from 'antd';
import { lazy, Suspense, useLayoutEffect } from 'react';

const ApplicationDetailsDialog=lazy(()=>import('./ApplicationDetailsDialog').then(module=>({default:module.ApplicationDetailsDialog})));

export function LazyApplicationDetailsDialog({applicationId,onClose,sharedLibrary=false}:{applicationId:string|undefined;onClose:()=>void;sharedLibrary?:boolean}) {
  useLayoutEffect(()=>{
    if(!applicationId)return;
    const trigger=document.activeElement;
    return ()=>{queueMicrotask(()=>{if(trigger instanceof HTMLElement&&trigger.isConnected)trigger.focus({preventScroll:true});});};
  },[applicationId]);
  if(!applicationId)return null;
  return <Suspense fallback={<Space><span role="status">正在加载招聘详情…</span><Button onClick={onClose}>取消加载详情</Button></Space>}>
    <ApplicationDetailsDialog applicationId={applicationId} onClose={onClose} sharedLibrary={sharedLibrary}/>
  </Suspense>;
}
