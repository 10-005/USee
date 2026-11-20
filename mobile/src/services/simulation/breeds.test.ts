import { mockAdoptionPets, findMockAdoptionPetForBreed } from '../../screens/MockAdoption/data';
import { getAnimalBreeds, getAnimalSpecies, type AnimalBreed } from '../pets';
import { resolveSimulationBreedSeq } from './breeds';

jest.mock('../pets', () => ({ getAnimalBreeds: jest.fn(), getAnimalSpecies: jest.fn() }));

// Deliberately unrelated to external breed codes or catalog row order.
const catalog = [
  ['dog-1', '비숑 프리제', 501, 17],
  ['dog-2', '도베르만', 502, 17],
  ['dog-3', '푸들', 503, 17],
  ['dog-4', '골든리트리버', 504, 17],
  ['dog-5', '시바견', 812, 17],
  ['dog-6', '시추', 506, 17],
  ['cat-1', '브리티시 쇼트헤어', 601, 29],
  ['cat-2', '러시안 블루', 602, 29],
  ['cat-3', '사바나캣', 603, 29],
  ['cat-4', '페르시안', 604, 29],
  ['cat-5', '스핑크스', 605, 29],
  ['cat-6', '샴', 606, 29],
] as const;
const breedRows: AnimalBreed[] = catalog.map(([, breedName, breedSeq, speciesSeq]) => ({
  breedName,
  breedSeq,
  speciesSeq,
  personality: 'FRIENDLY',
  representativeImageUrl: null,
}));

beforeEach(() => {
  jest
    .mocked(getAnimalSpecies)
    .mockReset()
    .mockResolvedValue([
      { speciesSeq: 29, speciesCode: 'CAT', speciesName: '고양이' },
      { speciesSeq: 17, speciesCode: 'DOG', speciesName: '개' },
    ]);
  jest
    .mocked(getAnimalBreeds)
    .mockReset()
    .mockImplementation(async speciesSeq =>
      [...breedRows, { ...breedRows[0], breedSeq: 100, breedName: '아키타' }]
        .filter(breed => breed.speciesSeq === speciesSeq)
        .reverse(),
    );
});

test.each(catalog)(
  'resolves %s by species and name instead of external code',
  async (id, breedName, breedSeq, speciesSeq) => {
    const pet = mockAdoptionPets.find(item => item.id === id)!;
    await expect(resolveSimulationBreedSeq(pet)).resolves.toBe(breedSeq);
    expect(getAnimalBreeds).toHaveBeenCalledWith(speciesSeq);
    expect(breedSeq).not.toBe(Number(pet.breedCode));
    expect(
      findMockAdoptionPetForBreed({ breedName, speciesType: speciesSeq === 17 ? 'DOG' : 'CAT' })
        ?.id,
    ).toBe(id);
  },
);

test('does not substitute a different breed or pick an ambiguous catalog row', async () => {
  const shiba = mockAdoptionPets.find(pet => pet.id === 'dog-5')!;
  jest
    .mocked(getAnimalBreeds)
    .mockResolvedValue([{ ...breedRows[0], breedSeq: 100, breedName: '아키타' }]);
  await expect(resolveSimulationBreedSeq(shiba)).rejects.toMatchObject({
    code: 'SIMULATION_BREED_NOT_FOUND',
  });
  jest
    .mocked(getAnimalBreeds)
    .mockResolvedValue([{ ...breedRows[4] }, { ...breedRows[4], breedSeq: 999 }]);
  await expect(resolveSimulationBreedSeq(shiba)).rejects.toMatchObject({
    code: 'SIMULATION_BREED_NOT_FOUND',
  });
});

test('does not use a cross-species name or silently fall back after a catalog failure', async () => {
  expect(findMockAdoptionPetForBreed({ breedName: '시바견', speciesType: 'CAT' })).toBeUndefined();
  jest.mocked(getAnimalBreeds).mockRejectedValue(new Error('offline'));
  await expect(resolveSimulationBreedSeq(mockAdoptionPets[4])).rejects.toThrow('offline');
});
