import type { ButtonHTMLAttributes } from 'react';
import './shared-ui.css';

export type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: 'primary' | 'secondary' | 'approval';
  size?: 'small' | 'medium' | 'large';
  fullWidth?: boolean;
};

export function Button({
  variant = 'primary',
  size = 'medium',
  fullWidth = false,
  className = '',
  type = 'button',
  ...props
}: ButtonProps) {
  return (
    <button
      type={type}
      className={`ui-button ui-button--${variant} ui-button--${size} ${fullWidth ? 'ui-full-width' : ''} ${className}`}
      {...props}
    />
  );
}
