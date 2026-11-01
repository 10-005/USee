import './shared-ui.css';

export type ActivityCardProps = {
  title: string;
  description: string;
  timeLabel: string;
  dateTime?: string;
  onClick?: () => void;
};

export function ActivityCard({
  title,
  description,
  timeLabel,
  dateTime,
  onClick,
}: ActivityCardProps) {
  const content = (
    <>
      <span className="ui-activity-card__row">
        <span className="ui-activity-card__title">{title}</span>
        <time dateTime={dateTime}>{timeLabel}</time>
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
