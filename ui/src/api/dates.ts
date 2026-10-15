// Persistence responses use createTime/updateTime; views use one date vocabulary.
export type DatedWire<T> = T & { createTime?: string; updateTime?: string };
export function normalizeDates<T extends {createdAt?:string;updatedAt?:string}>(value:DatedWire<T>):T {
  return {...value,createdAt:value.createTime??value.createdAt,updatedAt:value.updateTime??value.updatedAt};
}
