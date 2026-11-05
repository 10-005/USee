import { Link } from 'react-router';
import { InfoRows, RecordBadge } from '@/shared/ui/WorkPage';
import type { Rescue } from '@/entities/shelter/model';

export function LinkedRescueCard({
  rescue,
  compact = false,
}: {
  rescue: Rescue;
  compact?: boolean;
}) {
  return (
    <Link
      className={`linked-rescue-card linked-rescue-card--link${compact ? ' linked-rescue-card--compact' : ''}`}
      to={`/rescues/${rescue.id}`}
      aria-label={`${rescue.id} 구조 상세보기`}
    >
      <img src={rescue.image} alt={rescue.name} />
      <div className="linked-rescue-card__content">
        <div className="linked-rescue-card__heading">
          <strong>{rescue.id}</strong>
          <RecordBadge status={rescue.status} />
          <span className="linked-rescue-card__action">구조 상세보기 →</span>
        </div>
        {!compact && (
          <InfoRows
            rows={[
              ['구조 동물', rescue.name],
              ['구조 장소', rescue.location],
              ['구조 일시', rescue.date.replace('T', ' ')],
              ['접수 경로', rescue.source],
            ]}
          />
        )}
      </div>
    </Link>
  );
}
