import type { ShelterAnimal } from './model';

export function animalLabel(species?: string | null, breed?: string | null) {
  return `[${species?.trim() || '축종 미상'}] ${breed?.trim() || '품종 미상'}`;
}

export function shelterAnimalLabel(animal: ShelterAnimal) {
  const species = { dog: '개', cat: '고양이', other: '기타' };
  return animalLabel(animal.speciesName ?? species[animal.species], animal.name);
}
