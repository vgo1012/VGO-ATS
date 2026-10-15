import { request } from '../api/http';

export interface CalendarDay { date: string; dayType: 'WORKDAY' | 'RESTDAY' | 'HOLIDAY'; description: string | null }
export interface CalendarRevision {
  id: string; year: number; revision: number; status: string; version: string;
  sourceNote: string | null; publishedBy: string | null; publishedAt: string | null;
  publishedName: string | null; publishReason: string | null;
  recalculationId: string | null; days: CalendarDay[];
}
export interface CalendarYear { year: number; published: CalendarRevision | null; draft: CalendarRevision | null; nextYearMissing: boolean }
export interface PublishedCalendar { year: number; revisionId: string | null; days: CalendarDay[] }
export interface SaveCalendar {
  publishedRevisionId: string | null; expectedVersion: string | null;
  days: CalendarDay[]; sourceNote: string; reason: string;
}
const path = (year: number) => `/admin/work-calendars/${year}`;
export const calendarKeys = { year: (year: number) => ['work-calendar', year] as const, published: (year: number) => ['work-calendar', 'published', year] as const };
export const calendarApi = {
  published: (year: number, signal?: AbortSignal) => request<PublishedCalendar>(`/work-calendars/${year}/published`, { ...(signal ? { signal } : {}) }),
  year: (year: number, signal?: AbortSignal) => request<CalendarYear>(path(year), { ...(signal ? { signal } : {}) }),
  history: (year: number) => request<CalendarRevision[]>(`${path(year)}/history`),
  save: (year: number, body: SaveCalendar, key: string) => request<CalendarRevision>(path(year), { method: 'PUT', body, idempotencyKey: key }),
};
