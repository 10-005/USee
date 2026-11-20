import { useId } from 'react';
import type { SelectHTMLAttributes } from 'react';
import './shelter-ui.css';

export function SelectField({
  label,
  className,
  error,
  id,
  children,
  'aria-describedby': describedBy,
  ...props
}: SelectHTMLAttributes<HTMLSelectElement> & { label: string; error?: string }) {
  const generated = useId();
  const fieldId = id ?? generated;
  return (
    <div className="ui-field">
      <label className="ui-field__label" htmlFor={fieldId}>
        {label}
        {props.required && ' *'}
      </label>
      <select
        {...props}
        id={fieldId}
        className={['ui-input shelter-select', className].filter(Boolean).join(' ')}
        aria-invalid={error ? true : props['aria-invalid']}
        aria-describedby={
          [describedBy, error ? `${fieldId}-error` : null].filter(Boolean).join(' ') || undefined
        }
      >
        {children}
      </select>
      {error && (
        <span
          id={`${fieldId}-error`}
          role="alert"
          className="ui-field__message ui-field__message--error"
        >
          {error}
        </span>
      )}
    </div>
  );
}
