import type { Asset } from 'react-native-image-picker';

import { ALLOWED_IMAGE_TYPES, MAX_IMAGE_SIZE_BYTES } from '../../constants/upload';

export const PET_OPTIONS = {
  강아지: {
    breeds: ['말티즈', '푸들', '비숑 프리제', '시츄', '골든 리트리버', '믹스견'],
    colors: ['흰색', '검은색', '갈색', '회색', '크림색', '혼합색'],
  },
  고양이: {
    breeds: ['코리안 숏헤어', '러시안 블루', '페르시안', '샴', '랙돌', '믹스묘'],
    colors: ['흰색', '검은색', '갈색', '회색', '치즈색', '삼색', '혼합색'],
  },
  기타: {
    breeds: ['기타'],
    colors: ['흰색', '검은색', '갈색', '회색', '혼합색', '기타'],
  },
} as const;

export type PetSpecies = keyof typeof PET_OPTIONS;

export function validatePetImage(asset: Asset): string | null {
  if (!asset.uri) {
    return '선택한 사진을 불러올 수 없습니다.';
  }
  if (!asset.type || !(ALLOWED_IMAGE_TYPES as readonly string[]).includes(asset.type)) {
    return 'JPG 또는 PNG 형식의 사진만 등록할 수 있습니다.';
  }
  if (asset.fileSize && asset.fileSize > MAX_IMAGE_SIZE_BYTES) {
    return '사진은 한 장당 10MB 이하만 등록할 수 있습니다.';
  }
  return null;
}
