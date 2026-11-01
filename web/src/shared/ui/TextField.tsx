import { useId } from 'react';
import type { InputHTMLAttributes } from 'react';
import './shared-ui.css';

export type TextFieldProps = InputHTMLAttributes<HTMLInputElement> & {
  label: string;
  hint?: string;
  error?: string;
};

export function TextField({
  label,
  hint,
  error,
  id,
  className = '',
  'aria-describedby': describedBy,
  ...props
}: TextFieldProps) {
  const generatedId = useId();
  const inputId = id ?? generatedId;
  const message = error || hint;
  const messageId = `${inputId}-message`;
  return (
    <div className={`ui-field ${className}`}>
      <label className="ui-field__label" htmlFor={inputId}>
        {label}
      </label>
      <input
        {...props}
        id={inputId}
        className="ui-input"
        aria-invalid={error ? true : props['aria-invalid']}
        aria-describedby={
          [describedBy, message ? messageId : null].filter(Boolean).join(' ') || undefined
        }
      />
      {message && (
        <span
          id={messageId}
          className={`ui-field__message ${error ? 'ui-field__message--error' : ''}`}
          role={error ? 'alert' : undefined}
        >
          {message}
        </span>
      )}
    </div>
  );
}
