import { useId } from 'react';
import './shelter-ui.css';

export function ChoiceGroup({
  label,
  name,
  options,
  value,
  onChange,
  required = false,
  disabled = false,
}: {
  label: string;
  name?: string;
  options: { value: string; label: string }[];
  value: string;
  onChange: (value: string) => void;
  required?: boolean;
  disabled?: boolean;
}) {
  const generated = useId();
  return (
    <fieldset className="shelter-choice" disabled={disabled}>
      <legend>
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
            />
            <span>{option.label}</span>
          </label>
        ))}
      </div>
    </fieldset>
  );
}
