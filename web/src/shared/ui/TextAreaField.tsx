import { useId } from 'react';
import type { TextareaHTMLAttributes } from 'react';
import './shelter-ui.css';

export function TextAreaField({
  label,
  error,
  id,
  'aria-describedby': describedBy,
  ...props
}: TextareaHTMLAttributes<HTMLTextAreaElement> & { label: string; error?: string }) {
  const generated = useId();
  const fieldId = id ?? generated;
  return (
    <div className="ui-field">
      <label className="ui-field__label" htmlFor={fieldId}>
        {label}
        {props.required && ' *'}
      </label>
      <textarea
        {...props}
        id={fieldId}
        className="ui-input shelter-textarea"
        aria-invalid={error ? true : props['aria-invalid']}
        aria-describedby={
          [describedBy, error ? `${fieldId}-error` : null].filter(Boolean).join(' ') || undefined
        }
      />
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
