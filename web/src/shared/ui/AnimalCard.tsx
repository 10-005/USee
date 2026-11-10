import { AnimalPhoto } from '@/shared/ui/AnimalPhoto';
import locationIcon from '@/shared/assets/icons/location.svg';
import calendarIcon from '@/shared/assets/icons/calendar-check.svg';
import './shelter-ui.css';

export type AnimalCardProps = {
  name: string;
  imageSrc: string;
  imageAlt: string;
  status: 'protected' | 'closed' | 'returned' | 'adopted';
  adoptable: boolean;
  location: string;
  period: string;
  summary: string;
  description: string;
  onClick: () => void;
};
const statusLabels = {
  protected: '보호 중',
  closed: '보호 종료',
  returned: '반환 완료',
  adopted: '입양 완료',
};
export function AnimalCard({
  name,
  imageSrc,
  imageAlt,
  status,
  adoptable,
  location,
  period,
  summary,
  description,
  onClick,
}: AnimalCardProps) {
  return (
    <button type="button" className="animal-card" onClick={onClick}>
      <span className="animal-card__image">
        <AnimalPhoto src={imageSrc} alt={imageAlt} />
        <span className={`animal-card__status animal-card__status--${status}`}>
          {statusLabels[status]}
        </span>
      </span>
      <span className="animal-card__body">
        <span className="animal-card__heading">
          <strong>{name}</strong>
          <span
            className={`animal-card__adoptable ${adoptable ? '' : 'animal-card__adoptable--no'}`}
          >
            {adoptable ? '입양 가능' : '입양 불가'}
          </span>
        </span>
        <span className="animal-card__detail">
          <img src={locationIcon} alt="" />
          <span>{location}</span>
        </span>
        <span className="animal-card__detail">
          <img src={calendarIcon} alt="" />
          <span>{period}</span>
        </span>
        <span className="animal-card__muted">{summary}</span>
        <span className="animal-card__muted">{description}</span>
      </span>
    </button>
  );
}
