export const changeEvent='hr-ai:parsing-batches-changed';
export const storageKey=(userId:string)=>`hr-ai:parsing-batches:${userId}`;
const fallback=new Map<string,string[]>();

export function readBatches(userId:string){
  try{
    const stored=sessionStorage.getItem(storageKey(userId));
    if(stored===null)return fallback.get(userId)??[];
    const raw=JSON.parse(stored) as unknown;
    return Array.isArray(raw)?raw.filter((value):value is string=>typeof value==='string'):[];
  }catch{return fallback.get(userId)??[];}
}

function writeBatches(userId:string,ids:string[]){
  try{sessionStorage.setItem(storageKey(userId),JSON.stringify(ids));fallback.delete(userId);}
  catch{fallback.set(userId,ids);}
}

export function watchParsingBatch(userId:string,batchId:string){
  const ids=readBatches(userId);
  if(!ids.includes(batchId))writeBatches(userId,[...ids,batchId]);
  window.dispatchEvent(new Event(changeEvent));
}

export function forgetParsingBatch(userId:string,batchId:string){
  const remaining=readBatches(userId).filter(value=>value!==batchId);
  writeBatches(userId,remaining);
  return remaining;
}
