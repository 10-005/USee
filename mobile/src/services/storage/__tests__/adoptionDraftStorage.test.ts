import AsyncStorage from '@react-native-async-storage/async-storage';

import {
  getAdoptionApplicationDraft,
  removeAdoptionApplicationDraft,
  saveAdoptionApplicationDraft,
} from '../adoptionDraftStorage';

describe('adoptionDraftStorage', () => {
  beforeEach(async () => {
    await AsyncStorage.clear();
  });

  it('saves an incomplete form with its current step', async () => {
    const form = { address: '', motivation: '입양 동기 작성 중' };

    const saved = await saveAdoptionApplicationDraft(form, 3);

    expect(saved.step).toBe(3);
    await expect(getAdoptionApplicationDraft()).resolves.toMatchObject({ form, step: 3 });
  });

  it('removes a saved draft', async () => {
    await saveAdoptionApplicationDraft({ address: '구미시' }, 1);

    await removeAdoptionApplicationDraft();

    await expect(getAdoptionApplicationDraft()).resolves.toBeNull();
  });
});
