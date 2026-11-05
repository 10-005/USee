import type { Asset } from 'react-native-image-picker';

import catalog from '../../constants/animal-options.json';
import { ALLOWED_IMAGE_TYPES, MAX_IMAGE_SIZE_BYTES } from '../../constants/upload';

export const SPECIES_CODES = { 강아지: '417000', 고양이: '422400', 기타: '429900' };
export type PetSpecies = keyof typeof SPECIES_CODES;

function optionsWithOtherLast(names: string[], current: string) {
  const options = new Set(names);
  if (current) {options.add(current);}
  if (options.delete('기타')) {options.add('기타');}
  return [...options];
}

export function petOptions(species: PetSpecies | '', breed: string, color: string) {
  const breeds = species
    ? catalog.species.find(item => item.code === SPECIES_CODES[species])?.breeds ?? []
    : [];
  const matchingColors = breeds.filter(item => item.name === breed).flatMap(item => item.colors);
  return {
    breeds: optionsWithOtherLast(breeds.map(item => item.name), breed),
    colors: optionsWithOtherLast(matchingColors.map(item => item.name), color),
  };
}

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
