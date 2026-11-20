import AsyncStorage from '@react-native-async-storage/async-storage';

import {
  getAdoptionApplicationRecord,
  removeAdoptionApplicationRecord,
  saveSubmittedAdoptionApplication,
} from '../adoptionApplicationStorage';

describe('adoption application storage', () => {
  beforeEach(async () => {
    await AsyncStorage.clear();
  });

  it('stores a submitted application with its initial status', async () => {
    await saveSubmittedAdoptionApplication('animal-1', { motivation: '함께하고 싶어요.' });

    await expect(getAdoptionApplicationRecord()).resolves.toMatchObject({
      animalId: 'animal-1',
      status: 'SUBMITTED',
      version: 1,
    });
  });

  it('removes a stored application', async () => {
    await saveSubmittedAdoptionApplication('animal-1', {});
    await removeAdoptionApplicationRecord();

    await expect(getAdoptionApplicationRecord()).resolves.toBeNull();
  });
});
