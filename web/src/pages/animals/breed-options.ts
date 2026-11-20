import catalog from './animal-options.json';
import type { ShelterAnimalRecord } from '@/entities/shelter/model';
import type { Combination } from '@/entities/shelter/data/animals';

const speciesCodes = { dog: '417000', cat: '422400', other: '429900' };
type Breed = {
  name: string;
  code: string | null;
  originalBreedNames?: string[];
  originalBreedCodes?: string[];
  colors: { name: string; code: string }[];
};
export function breedsFor(species: ShelterAnimalRecord['species'] | ''): Breed[] {
  return species
    ? (catalog.species.find((item) => item.code === speciesCodes[species])?.breeds ?? [])
    : [];
}

function namesFor(breed: Breed) {
  const names = new Set([breed.name, ...(breed.originalBreedNames ?? [])]);
  breed.originalBreedNames?.forEach((name, index) => {
    const code = breed.originalBreedCodes?.[index];
    if (code) names.add(name + '(' + code + ')');
  });
  return names;
}

export function catalogCombinations(
  species: ShelterAnimalRecord['species'] | '',
  available: Combination[],
  savedId?: number,
) {
  if (!species) return [];
  const speciesName = catalog.species.find((item) => item.code === speciesCodes[species])?.name;
  const choices = available.filter((row) => row.speciesName === speciesName);
  const byName = new Map<string, Combination[]>();
  for (const row of choices) {
    const rows = byName.get(row.breedName);
    if (rows) rows.push(row);
    else byName.set(row.breedName, [row]);
  }
  const result = breedsFor(species).flatMap((breed) => {
    const rows = [...namesFor(breed)].flatMap((name) => byName.get(name) ?? []);
    return [...new Set(breed.colors.map((color) => color.name))].flatMap((color) =>
      rows.filter((row) => row.colorName === color).map((row) => ({ ...row, label: breed.name })),
    );
  });
  const saved = choices.find((row) => row.combinationSeq === savedId);
  if (saved && !result.some((row) => row.combinationSeq === savedId))
    result.push({ ...saved, label: saved.breedName });
  return result;
}
