import { useState } from 'react';
import placeholder from '@/shared/assets/report-photo-placeholder.webp';

export function AnimalPhoto({ src, alt }: { src?: string | null; alt: string }) {
  const [failedSrc, setFailedSrc] = useState<string>();
  const photo = src?.trim();
  if (!photo || photo === failedSrc)
    return (
      <img
        src={placeholder}
        alt={alt ? '동물 사진 없음' : ''}
        style={{ objectFit: 'contain', backgroundColor: 'white' }}
      />
    );
  return <img src={photo} alt={alt} onError={() => setFailedSrc(photo)} />;
}
