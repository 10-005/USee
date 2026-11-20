import { useId } from 'react';
import './shelter-ui.css';

export function ChoiceGroup({
  label,
  hideLabel = false,
  name,
  options,
  value,
  onChange,
  required = false,
  disabled = false,
  error,
}: {
  label: string;
  hideLabel?: boolean;
  name?: string;
  options: { value: string; label: string }[];
  value: string;
  onChange: (value: string) => void;
  required?: boolean;
  disabled?: boolean;
  error?: string;
}) {
  const generated = useId();
  return (
    <fieldset
      className="shelter-choice"
      disabled={disabled}
      data-invalid={!!error || undefined}
      aria-describedby={error ? generated + '-error' : undefined}
    >
      <legend className={hideLabel ? 'shelter-sr-only' : undefined}>
        {label}
        {required && ' *'}
      </legend>
      <div>
        {options.map((option) => (
          <label key={option.value}>
            <input
              type="radio"
              name={name ?? generated}
              value={option.value}
              checked={value === option.value}
              onChange={() => onChange(option.value)}
              required={required}
              aria-invalid={!!error || undefined}
            />
            <span>{option.label}</span>
          </label>
        ))}
      </div>
      {error && (
        <span id={generated + '-error'} className="ui-field__message ui-field__message--error">
          {error}
        </span>
      )}
    </fieldset>
  );
}
