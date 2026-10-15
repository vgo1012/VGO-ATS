import { request } from '../api/http';

export interface MetricDefinition { code:string; name:string; category:string; versionNo:number; definitionText:string; eventType:string; effectiveFrom:string }
export interface MetricPoint { eventType:string; eventCount:number; eventValue:number; firstOccurredAt:string|null; lastOccurredAt:string|null }
export interface ReportView { reportType:string; from:string; to:string; dataUpdatedAt:string; projectionStatus:'READY'|'LAGGING'; reconciliationUpdatedAt:string|null; definitions:MetricDefinition[]; points:MetricPoint[]; days?:Array<{date:string;eventType:string;eventCount:number}>; parseCoverage?:{resumeVersionCount:number;parsedResumeVersionCount:number}|null }

function params(values:Record<string,string|number|undefined>) { const p=new URLSearchParams(); Object.entries(values).forEach(([key,value])=>{if(value!==undefined&&value!=='')p.set(key,String(value));}); const query=p.toString(); return query?`?${query}`:''; }

export const reportingApi={
  report:(type:'funnel'|'workload'|'ai-effect',filters:{from?:string;to?:string;jdId?:string;jdVersionId?:string;ownerUserId?:string}={})=>request<ReportView>(`/reports/${type}${params(filters)}`),
};
