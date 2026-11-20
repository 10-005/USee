import { useState, type ComponentPropsWithoutRef } from 'react';
import avatar from '@/shared/assets/shelter-avatar-default.webp';
import banner from '@/shared/assets/shelter-banner-default.webp';

type Props = ComponentPropsWithoutRef<'img'> & { kind: 'profileImage' | 'bannerImage' };

export function ShelterPhoto({ kind, src, ...props }: Props) {
  const [failedSrc, setFailedSrc] = useState<string>();
  const photo = src?.trim();
  const fallback = kind === 'bannerImage' ? banner : avatar;
  const usePhoto = Boolean(photo && photo !== failedSrc);
  return (
    <img
      {...props}
      src={usePhoto ? photo : fallback}
      onError={usePhoto ? () => setFailedSrc(photo) : undefined}
    />
  );
}
