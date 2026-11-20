import { matchesMockAdoptionBreed, type MockAdoptionPet } from '../../screens/MockAdoption/data';
import { ApiClientError } from '../api/errors';
import { getAnimalBreeds, getAnimalSpecies } from '../pets';

// Public breed codes are not database IDs. Resolve the authenticated server catalog
// before starting a session rather than converting a code such as 000100 to 100.
export async function resolveSimulationBreedSeq(pet: MockAdoptionPet): Promise<number> {
  const speciesType = pet.category === 'cat' ? 'CAT' : 'DOG';
  const species = (await getAnimalSpecies()).filter(item => item.speciesCode === speciesType);
  if (species.length !== 1) {
    throw new ApiClientError({
      code: 'SIMULATION_BREED_NOT_FOUND',
      message: '선택한 품종을 찾지 못했어요.',
    });
  }
  const breeds = (await getAnimalBreeds(species[0].speciesSeq)).filter(
    item =>
      item.speciesSeq === species[0].speciesSeq &&
      matchesMockAdoptionBreed(pet, {
        breedName: item.breedName,
        speciesType,
      }),
  );
  if (breeds.length !== 1 || !Number.isSafeInteger(breeds[0].breedSeq) || breeds[0].breedSeq <= 0) {
    throw new ApiClientError({
      code: 'SIMULATION_BREED_NOT_FOUND',
      message: '선택한 품종을 찾지 못했어요.',
    });
  }
  return breeds[0].breedSeq;
}
