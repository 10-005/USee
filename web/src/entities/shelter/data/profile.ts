import { queryOptions } from '@tanstack/react-query';
import { shelterRequest } from '@/shared/api/session';
import type { ShelterProfile } from '../model';

type ImageSummary = { imageUrl: string } | null;
type ProfileResponse = {
  shelterName: string;
  address: string;
  phone: string;
  description: string | null;
  operatingHours: string | null;
  closedDays: string | null;
  profileImage: ImageSummary;
  backgroundImage: ImageSummary;
};
function profileFrom(data?: ProfileResponse): ShelterProfile {
  if (!data) throw new Error('보호소 정보를 확인할 수 없습니다.');
  return {
    name: data.shelterName,
    address: data.address,
    phone: data.phone,
    introduction: data.description ?? '',
    hours: data.operatingHours ?? '',
    holiday: data.closedDays ?? '',
    profileImage: data.profileImage?.imageUrl,
    bannerImage: data.backgroundImage?.imageUrl,
  };
}
export const profileOptions = queryOptions({
  queryKey: ['shelter', 'me'],
  queryFn: async ({ signal }) =>
    profileFrom(await shelterRequest<ProfileResponse>('/shelters/me', { signal })),
});
export type ProfileFiles = Partial<Record<'profileImage' | 'bannerImage', File>>;
export async function updateProfile(
  previous: ShelterProfile,
  next: ShelterProfile,
  files: ProfileFiles,
  upload: (file: File) => Promise<number>,
) {
  const json: Record<string, string | number | null> = {};
  if (next.introduction !== previous.introduction) json.description = next.introduction || null;
  if (next.hours !== previous.hours) json.operatingHours = next.hours || null;
  if (next.holiday !== previous.holiday) json.closedDays = next.holiday || null;
  if (files.profileImage) json.profileImageSeq = await upload(files.profileImage);
  if (files.bannerImage) json.backgroundImageSeq = await upload(files.bannerImage);
  if (!Object.keys(json).length) return previous;
  return profileFrom(
    await shelterRequest<ProfileResponse>('/shelters/me', { method: 'PATCH', json }),
  );
}
