import catalog from './animal-options.json';
import type { ShelterAnimalRecord } from '@/entities/shelter/model';

const speciesCodes = { dog: '417000', cat: '422400', other: '429900' };

export function breedsFor(species: ShelterAnimalRecord['species'] | '') {
  return species
    ? (catalog.species.find((item) => item.code === speciesCodes[species])?.breeds ?? [])
    : [];
}
