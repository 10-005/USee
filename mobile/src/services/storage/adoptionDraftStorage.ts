import { STORAGE_KEYS } from '../../constants/storageKeys';
import { getStoredValue, removeStoredValue, setStoredValue } from './jsonStorage';

export type AdoptionApplicationDraft<Form> = {
  form: Form;
  savedAt: string;
  step: number;
  version: 1;
};

export async function getAdoptionApplicationDraft<Form>() {
  return getStoredValue<AdoptionApplicationDraft<Form>>(STORAGE_KEYS.ADOPTION_APPLICATION_DRAFT);
}

export async function saveAdoptionApplicationDraft<Form>(form: Form, step: number) {
  const draft: AdoptionApplicationDraft<Form> = {
    form,
    savedAt: new Date().toISOString(),
    step,
    version: 1,
  };

  await setStoredValue(STORAGE_KEYS.ADOPTION_APPLICATION_DRAFT, draft);
  return draft;
}

export async function removeAdoptionApplicationDraft() {
  await removeStoredValue(STORAGE_KEYS.ADOPTION_APPLICATION_DRAFT);
}
