import { useId } from 'react';
import type { ReactNode } from 'react';
import './shelter-ui.css';

export function FormSection({
  title,
  description,
  children,
}: {
  title: string;
  description?: string;
  children: ReactNode;
}) {
  const titleId = useId();
  return (
    <section className="shelter-form-section" aria-labelledby={titleId}>
      <h2 id={titleId}>{title}</h2>
      {description && <p className="shelter-form-section__description">{description}</p>}
      <div className="shelter-form-section__fields">{children}</div>
    </section>
  );
}
