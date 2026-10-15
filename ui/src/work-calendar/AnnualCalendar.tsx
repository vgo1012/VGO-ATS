import { useRef, useState, type CSSProperties, type KeyboardEvent } from 'react';
import { theme } from 'antd';
import type { CalendarDay } from './api';
import './annual-calendar.css';

interface AnnualCalendarProps {
  year: number;
  days: CalendarDay[];
  editable: boolean;
  busy: boolean;
  changedDates: string[];
  onMark: (dates: string[]) => void;
}

const labels = { WORKDAY: '工作日', RESTDAY: '休息日', HOLIDAY: '节假日' };
const marks = { WORKDAY: '班', RESTDAY: '休', HOLIDAY: '节' };
const weekdays = ['一', '二', '三', '四', '五', '六', '日'];
const dateKey = (year: number, month: number, date: number) => `${year}-${String(month + 1).padStart(2, '0')}-${String(date).padStart(2, '0')}`;

export function AnnualCalendar({ year, days, editable, busy, changedDates, onMark }: AnnualCalendarProps) {
  const { token } = theme.useToken();
  const anchor = useRef<string | null>(null);
  const buttons = useRef(new Map<string, HTMLButtonElement>());
  const [focusedDate, setFocusedDate] = useState<string | null>(null);
  const byDate = new Map(days.map((day) => [day.date, day]));
  const changed = new Set(changedDates);
  const orderedDates = days.map((day) => day.date).filter((date) => date.startsWith(`${year}-`)).sort();
  const tabStop = focusedDate && byDate.has(focusedDate) && focusedDate.startsWith(`${year}-`) ? focusedDate : orderedDates[0];

  function mark(date: string, shift: boolean) {
    if (!editable || busy) return;
    if (shift && anchor.current?.startsWith(`${year}-`)) {
      const start = anchor.current < date ? anchor.current : date;
      const end = anchor.current > date ? anchor.current : date;
      onMark(orderedDates.filter((day) => day >= start && day <= end));
    } else {
      anchor.current = date;
      onMark([date]);
    }
  }

  function moveFocus(event: KeyboardEvent<HTMLButtonElement>, date: string) {
    const offsets: Record<string, number> = { ArrowLeft: -1, ArrowRight: 1, ArrowUp: -7, ArrowDown: 7 };
    const offset = offsets[event.key];
    if (offset === undefined) return;
    event.preventDefault();
    const target = new Date(`${date}T12:00:00Z`);
    target.setUTCDate(target.getUTCDate() + offset);
    const key = target.toISOString().slice(0, 10);
    const button = buttons.current.get(key);
    if (button && !button.disabled) {
      setFocusedDate(key);
      button.focus();
    }
  }

  return <div className="annual-calendar" role="group" aria-label={`${year}年全年日历`} aria-busy={busy} style={{ '--annual-workday': token.colorInfo, '--annual-holiday': token.colorWarning } as CSSProperties}>
    <div className="annual-calendar-grid">
      {Array.from({ length: 12 }, (_, month) => {
        const offset = (new Date(Date.UTC(year, month, 1)).getUTCDay() + 6) % 7;
        const count = new Date(Date.UTC(year, month + 1, 0)).getUTCDate();
        return <section className="annual-calendar-month" aria-label={`${year}年${month + 1}月`} key={month}>
          <h2><span>{month + 1}<small>月</small></span><span className="annual-calendar-month-year">{year}</span></h2>
          <div className="annual-calendar-weekdays" aria-hidden="true">{weekdays.map((day) => <span key={day}>{day}</span>)}</div>
          <div className="annual-calendar-dates">
            {Array.from({ length: 42 }, (_, index) => {
              const number = index - offset + 1;
              if (number < 1 || number > count) return <span className="annual-calendar-blank" key={index} aria-hidden="true" />;
              const date = dateKey(year, month, number);
              const day = byDate.get(date);
              const isChanged = changed.has(date);
              const label = `${date} ${day ? labels[day.dayType] : '未配置'}${day?.description ? `，${day.description}` : ''}${isChanged ? '，未保存' : ''}`;
              return <button type="button" key={index} className={`annual-calendar-day ${day ? day.dayType.toLowerCase() : 'unavailable'}${isChanged ? ' is-changed' : ''}`}
                ref={(element) => { if (element) buttons.current.set(date, element); else buttons.current.delete(date); }}
                disabled={!day || !editable || busy} tabIndex={date === tabStop ? 0 : -1}
                aria-label={label} title={label} onFocus={() => setFocusedDate(date)}
                onKeyDown={(event) => moveFocus(event, date)} onClick={(event) => mark(date, event.shiftKey)}>
                <span>{number}</span><small aria-hidden="true">{day ? marks[day.dayType] : '—'}</small>
                {isChanged && <i className="annual-calendar-change-dot" aria-hidden="true" />}
              </button>;
            })}
          </div>
        </section>;
      })}
    </div>
  </div>;
}
