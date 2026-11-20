import { useId, useState } from 'react';
import type { ChangeEvent, TextareaHTMLAttributes } from 'react';
import './shelter-ui.css';

function textLength(value: TextareaHTMLAttributes<HTMLTextAreaElement>['value']) {
  if (typeof value === 'string' || typeof value === 'number') return String(value).length;
  if (Array.isArray(value)) return value.join('').length;
  return 0;
}

export function TextAreaField({
  label,
  error,
  id,
  maxLength,
  defaultValue,
  value,
  onChange,
  'aria-describedby': describedBy,
  ...props
}: TextareaHTMLAttributes<HTMLTextAreaElement> & { label: string; error?: string }) {
  const generated = useId();
  const fieldId = id ?? generated;
  const countId = maxLength === undefined ? undefined : `${fieldId}-count`;
  const [uncontrolledLength, setUncontrolledLength] = useState(() => textLength(defaultValue));
  const count = value === undefined ? uncontrolledLength : textLength(value);
  const describedByIds =
    [describedBy, error ? `${fieldId}-error` : null, countId].filter(Boolean).join(' ') ||
    undefined;
  const handleChange = (event: ChangeEvent<HTMLTextAreaElement>) => {
    if (value === undefined) setUncontrolledLength(event.target.value.length);
    onChange?.(event);
  };
  return (
    <div className="ui-field">
      <label className="ui-field__label" htmlFor={fieldId}>
        {label}
        {props.required && ' *'}
      </label>
      <textarea
        {...props}
        id={fieldId}
        maxLength={maxLength}
        defaultValue={defaultValue}
        value={value}
        onChange={handleChange}
        className="ui-input shelter-textarea"
        aria-invalid={error ? true : props['aria-invalid']}
        aria-describedby={describedByIds}
      />
      {(error || countId) && (
        <div className="ui-field__footer">
          {error && (
            <span
              id={`${fieldId}-error`}
              role="alert"
              className="ui-field__message ui-field__message--error"
            >
              {error}
            </span>
          )}
          {countId && (
            <span id={countId} className="ui-field__count">
              {count}/{maxLength}
            </span>
          )}
        </div>
      )}
    </div>
  );
}
