import { STORAGE_KEYS } from '../../constants/storageKeys';
import { getStoredValue, removeStoredValue, setStoredValue } from './jsonStorage';

export type AdoptionApplicationStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'DOCUMENT_APPROVED'
  | 'ADOPTION_APPROVED'
  | 'REJECTED'
  | 'CANCELED';

export type AdoptionApplicationRecord<Form = unknown> = {
  animalId: string;
  applicantName: string;
  applicationNumber: string;
  form: Form;
  rejectionReason?: string;
  shelterName: string;
  status: AdoptionApplicationStatus;
  submittedAt: string;
  version: 1;
};

export async function getAdoptionApplicationRecord<Form>() {
  return getStoredValue<AdoptionApplicationRecord<Form>>(STORAGE_KEYS.ADOPTION_APPLICATION_RECORD);
}

export async function saveSubmittedAdoptionApplication<Form>(animalId: string, form: Form) {
  const record: AdoptionApplicationRecord<Form> = {
    animalId,
    applicantName: '우사기',
    applicationNumber: '113122600015',
    form,
    shelterName: '두둥이보호소',
    status: 'SUBMITTED',
    submittedAt: new Date().toISOString(),
    version: 1,
  };

  await setStoredValue(STORAGE_KEYS.ADOPTION_APPLICATION_RECORD, record);
  return record;
}

export async function removeAdoptionApplicationRecord() {
  await removeStoredValue(STORAGE_KEYS.ADOPTION_APPLICATION_RECORD);
}

export async function updateAdoptionApplicationStatus(status: AdoptionApplicationStatus) {
  const record = await getAdoptionApplicationRecord();
  if (!record) {
    return null;
  }
  const updated = { ...record, status };
  await setStoredValue(STORAGE_KEYS.ADOPTION_APPLICATION_RECORD, updated);
  return updated;
}
