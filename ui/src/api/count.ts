/** A count continuation contains no candidate records. */
export interface CountPage { count:number; nextCursor:string|null; hasMore:boolean }
export async function collectCount(load:(cursor?:string)=>Promise<CountPage>,signal?:AbortSignal):Promise<number>{
  let total=0;let cursor:string|undefined;
  const seen=new Set<string>();
  for(;;){
    signal?.throwIfAborted();
    const page=await load(cursor);
    signal?.throwIfAborted();
    if(!Number.isSafeInteger(page.count)||page.count<0||!Number.isSafeInteger(total+page.count))throw new Error('统计结果异常，请重试');
    total+=page.count;
    if(!page.hasMore)return total;
    if(!page.nextCursor||seen.has(page.nextCursor))throw new Error('统计游标未推进，请重试');
    cursor=page.nextCursor;seen.add(cursor);
  }
}
