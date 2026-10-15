import { useQueries } from '@tanstack/react-query';
import { App, Button } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useSession } from '../auth/session';
import { talentApi } from './api';
import { changeEvent, forgetParsingBatch, readBatches } from './batchParseWatch';

export function BatchParseNotifications(){
  const {actor}=useSession(); const {notification}=App.useApp(); const navigate=useNavigate();
  const userId=actor?.userId;
  const [ids,setIds]=useState<string[]>(()=>userId?readBatches(userId):[]);
  const announced=useRef(new Set<string>());
  useEffect(()=>{if(!userId)return;const update=()=>setIds(readBatches(userId));window.addEventListener(changeEvent,update);return()=>window.removeEventListener(changeEvent,update);},[userId]);
  const snapshots=useQueries({queries:ids.map(id=>({queryKey:['talent','intake-batch',id],queryFn:()=>talentApi.batch(id),retry:false,refetchInterval:3000,refetchIntervalInBackground:false}))});
  useEffect(()=>{
    if(!userId)return;
    for(let index=0;index<ids.length;index++){
      const batch=snapshots[index]?.data;const id=ids[index];
      if(!id||!batch||!['COMPLETED','COMPLETED_WITH_ERRORS'].includes(batch.status)||batch.parsingCount>0||announced.current.has(id))continue;
      announced.current.add(id);
      const key=`parse-batch-${id}`;
      const review=()=>{notification.destroy(key);void navigate(`/talents?view=pool&intake=1&batchId=${encodeURIComponent(id)}`);};
      const detail=batch.attentionCount>0?`有 ${batch.attentionCount} 份需要核验或重试，请在解析校验查看。`
        :batch.processingCount>0?`仍有 ${batch.processingCount} 份正在入库或评分，请在解析校验查看进度。`
          :batch.completedCount>0?`已入库 ${batch.completedCount} 份，可在简历列表继续筛选。`:'解析已结束，本批暂无可筛选的简历。';
      notification.open({key,placement:'topRight',duration:5,pauseOnHover:false,title:batch.attentionCount>0?'本批简历解析结束，待处理':'本批简历解析完成',description:detail,btn:<Button size="small" type="primary" onClick={review}>查看解析校验</Button>,role:'status'});
      setIds(forgetParsingBatch(userId,id));
    }
  },[ids,snapshots,userId,notification,navigate]);
  return null;
}
