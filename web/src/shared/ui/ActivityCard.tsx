import type { ReactNode } from 'react';
import './shared-ui.css';

export type ActivityCardProps = {
  title: string;
  description: string;
  timeLabel?: string;
  badge?: ReactNode;
  dateTime?: string;
  onClick?: () => void;
};

export function ActivityCard({
  title,
  description,
  timeLabel,
  badge,
  dateTime,
  onClick,
}: ActivityCardProps) {
  const content = (
    <>
      <span className="ui-activity-card__row">
        <span className="ui-activity-card__title">{title}</span>
        {badge ?? (timeLabel && <time dateTime={dateTime}>{timeLabel}</time>)}
      </span>
      <span className="ui-activity-card__description">{description}</span>
    </>
  );
  return onClick ? (
    <button type="button" className="ui-activity-card" onClick={onClick}>
      {content}
    </button>
  ) : (
    <article className="ui-activity-card">{content}</article>
  );
}
