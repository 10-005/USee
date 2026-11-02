import { useId, useRef, useState } from 'react';
import calendarIcon from '@/shared/assets/figma/calendar-check.svg';

const pad = (value: number) => String(value).padStart(2, '0');

function lastDay(start: string, days: number) {
  const end = new Date(start + 'T00:00:00Z');
  end.setUTCDate(end.getUTCDate() + days - 1);
  return end.toISOString().slice(0, 10);
}

export function CalendarField({
  initialValue,
  label,
  name,
  withTime = false,
  range,
  error,
  onConfirm,
  placeholder = '날짜를 선택해 주세요',
}: {
  initialValue: string;
  label: string;
  name: string;
  withTime?: boolean;
  error?: string;
  onConfirm?: (value: string) => void;
  placeholder?: string;
  range?: { days: number; endName: string; initialEnd?: string };
}) {
  const [value, setValue] = useState(initialValue);
  const [end, setEnd] = useState(() =>
    range && initialValue ? range.initialEnd || lastDay(initialValue, range.days) : '',
  );
  const [open, setOpen] = useState(false);
  const [day, setDay] = useState(initialValue.slice(0, 10));
  const [time, setTime] = useState(initialValue.slice(11, 16));
  const viewDate = value ? new Date(value.slice(0, 10) + 'T12:00:00') : new Date();
  const [year, setYear] = useState(viewDate.getFullYear());
  const [month, setMonth] = useState(viewDate.getMonth() + 1);
  const trigger = useRef<HTMLButtonElement>(null);
  const id = useId();
  const firstYear = Math.min(1900, year);
  const lastYear = Math.max(new Date().getFullYear() + 10, year);
  const firstWeekday = new Date(year, month - 1, 1).getDay();
  const daysInMonth = new Date(year, month, 0).getDate();
  const draftEnd = range && day ? lastDay(day, range.days) : '';
  const close = () => {
    setOpen(false);
    trigger.current?.focus();
  };

  return (
    <div
      className="ui-field rescue-date"
      onKeyDown={(event) => {
        if (open && event.key === 'Escape') {
          event.preventDefault();
          close();
        }
      }}
    >
      <label className="ui-field__label" htmlFor={id}>
        {label} *
      </label>
      <input type="hidden" name={name} value={value} />
      {range && <input type="hidden" name={range.endName} value={end} />}
      <button
        type="button"
        id={id}
        ref={trigger}
        className="ui-input rescue-date__trigger"
        data-empty={!value || undefined}
        aria-invalid={!!error || undefined}
        aria-describedby={error ? id + '-error' : undefined}
        aria-expanded={open}
        aria-controls={open ? id + '-panel' : undefined}
        onClick={() => {
          setDay(value.slice(0, 10));
          setTime(value.slice(11, 16) || '09:00');
          setYear(viewDate.getFullYear());
          setMonth(viewDate.getMonth() + 1);
          setOpen(!open);
        }}
      >
        {!value ? placeholder : range ? value + ' ~ ' + end : value.replace('T', ' ')}{' '}
        <img src={calendarIcon} width="20" height="20" alt="" />
      </button>
      {error && (
        <span id={id + '-error'} className="ui-field__message ui-field__message--error">
          {error}
        </span>
      )}
      {open && (
        <fieldset id={id + '-panel'} className="rescue-date__panel">
          <legend className="shelter-sr-only">{label} 선택</legend>
          <div className="rescue-date__month">
            <select
              aria-label="연도"
              className="ui-input shelter-select shelter-filter--menu"
              value={year}
              onChange={(e) => setYear(Number(e.target.value))}
            >
              {Array.from({ length: lastYear - firstYear + 1 }, (_, i) => firstYear + i).map(
                (y) => (
                  <option key={y} value={y}>
                    {y}년
                  </option>
                ),
              )}
            </select>
            <select
              aria-label="월"
              className="ui-input shelter-select shelter-filter--menu"
              value={month}
              onChange={(e) => setMonth(Number(e.target.value))}
            >
              {Array.from({ length: 12 }, (_, i) => i + 1).map((m) => (
                <option key={m} value={m}>
                  {m}월
                </option>
              ))}
            </select>
          </div>
          <div className="rescue-date__days">
            {Array.from('일월화수목금토', (label) => (
              <span aria-hidden="true" key={label}>
                {label}
              </span>
            ))}
            {Array.from({ length: daysInMonth }, (_, i) => {
              const date = year + '-' + pad(month) + '-' + pad(i + 1);
              return (
                <button
                  key={date}
                  type="button"
                  aria-label={date}
                  aria-pressed={date === day}
                  data-range={
                    range && date === draftEnd
                      ? 'end'
                      : range && date > day && date < draftEnd
                        ? 'inside'
                        : undefined
                  }
                  style={i === 0 ? { gridColumnStart: firstWeekday + 1 } : undefined}
                  onClick={() => setDay(date)}
                >
                  {i + 1}
                </button>
              );
            })}
          </div>
          {range && (
            <p className="calendar-range-hint" aria-live="polite">
              {day
                ? `시작일 포함 ${range.days}일 · ${day} ~ ${draftEnd}`
                : `시작일을 선택하면 그날부터 ${range.days}일이 선택돼요.`}
            </p>
          )}
          {withTime && (
            <div className="rescue-date__time" role="group" aria-label="구조 시각">
              <span>구조 시각</span>
              <div className="rescue-date__clock">
                <select
                  aria-label="시"
                  className="ui-input shelter-select shelter-filter--menu"
                  value={time.slice(0, 2)}
                  onChange={(e) => setTime(e.target.value + ':' + time.slice(3))}
                >
                  {Array.from({ length: 24 }, (_, i) => pad(i)).map((hour) => (
                    <option key={hour} value={hour}>
                      {hour}시
                    </option>
                  ))}
                </select>
                <select
                  aria-label="분"
                  className="ui-input shelter-select shelter-filter--menu"
                  value={time.slice(3)}
                  onChange={(e) => setTime(time.slice(0, 2) + ':' + e.target.value)}
                >
                  {Array.from({ length: 60 }, (_, i) => pad(i)).map((minute) => (
                    <option key={minute} value={minute}>
                      {minute}분
                    </option>
                  ))}
                </select>
              </div>
            </div>
          )}
          <div className="rescue-date__actions">
            <button type="button" onClick={close}>
              취소
            </button>
            <button
              type="button"
              disabled={
                !day ||
                (withTime && !time) ||
                (!range && !day.startsWith(year + '-' + pad(month) + '-'))
              }
              onClick={() => {
                setValue(withTime ? day + 'T' + time : day);
                onConfirm?.(withTime ? day + 'T' + time : day);
                if (range) setEnd(draftEnd);
                close();
              }}
            >
              확인
            </button>
          </div>
        </fieldset>
      )}
    </div>
  );
}
