import catalog from './animal-options.json';
import type { ShelterAnimalRecord } from '@/entities/shelter/model';
import type { Combination } from '@/entities/shelter/data/animals';

const speciesCodes = { dog: '417000', cat: '422400', other: '429900' };
const breedLabels = new Map<string, string>();
for (const { breeds } of catalog.species) {
  const seen = new Set<string>();
  const duplicates = new Set<string>();
  for (const { name } of breeds) {
    if (seen.has(name)) duplicates.add(name);
    seen.add(name);
  }
  for (const { name, code } of breeds) {
    breedLabels.set(code, duplicates.has(name) ? `${name} (${code})` : name);
  }
}

export function breedLabel(breed: { name: string; code: string }) {
  return breedLabels.get(breed.code) ?? breed.name;
}

export function breedsFor(species: ShelterAnimalRecord['species'] | '') {
  return species
    ? (catalog.species.find((item) => item.code === speciesCodes[species])?.breeds ?? [])
    : [];
}

export function catalogCombinations(
  species: ShelterAnimalRecord['species'] | '',
  available: Combination[],
  savedId?: number,
) {
  if (!species) return [];
  const speciesName = catalog.species.find((item) => item.code === speciesCodes[species])?.name;
  const choices = available.filter((row) => row.speciesName === speciesName);
  const byBreed = new Map<string, Combination[]>();
  for (const row of choices) {
    const group = byBreed.get(row.breedName) ?? [];
    group.push(row);
    byBreed.set(row.breedName, group);
  }
  const labels = new Map<number, string>();
  const ordered = breedsFor(species).flatMap((breed) => {
    const rows = byBreed.get(breed.name + '(' + breed.code + ')') ?? byBreed.get(breed.name) ?? [];
    for (const row of rows) labels.set(row.breedSeq, breedLabel(breed));
    return breed.colors.flatMap((color) => rows.filter((row) => row.colorName === color.name));
  });
  const saved = choices.find((row) => row.combinationSeq === savedId);
  if (saved && !ordered.includes(saved)) ordered.push(saved);
  return ordered.map((row) => ({ ...row, label: labels.get(row.breedSeq) ?? row.breedName }));
}
