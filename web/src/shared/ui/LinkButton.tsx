import { Link, type LinkProps } from 'react-router';
import './shared-ui.css';

/** Link semantics with the same visual variants used by page actions. */
export function LinkButton({
  className = '',
  variant = 'primary',
  ...props
}: LinkProps & { variant?: 'primary' | 'soft' }) {
  return (
    <Link
      {...props}
      className={`page-link${variant === 'soft' ? ' page-link--soft-green' : ''}${className ? ` ${className}` : ''}`}
    />
  );
}
