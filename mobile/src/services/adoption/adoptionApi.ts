import type { ApiResponse } from '../api';
import { apiClient } from '../api/client';
import type { PetGender } from '../pets';

export type ShelterAnimalFilterStatus =
  | 'PROTECTING_AVAILABLE'
  | 'PROTECTING_UNAVAILABLE'
  | 'RETURNED'
  | 'ADOPTED'
  | 'ENDED';
export type ProtectionStatus = 'PROTECTING' | 'RETURNED' | 'ADOPTED' | 'ENDED';
export type AdoptionStatus = 'AVAILABLE' | 'UNAVAILABLE' | 'ADOPTED';

export type ShelterAnimalClassification = {
  breedName: string;
  breedSeq?: number;
  colorName: string;
  colorSeq?: number;
  combinationSeq: number;
  speciesCode?: 'DOG' | 'CAT' | 'ETC';
  speciesName: string;
  speciesSeq?: number;
};

export type ShelterAnimalCard = {
  adoptionStatus: AdoptionStatus;
  age: string | null;
  animalSeq: number;
  classification: ShelterAnimalClassification | null;
  gender: PetGender;
  isNeutered: boolean | null;
  noticeEndDate: string;
  noticeStartDate: string;
  protectionStatus: ProtectionStatus;
  regDttm: string | null;
  representativeImageUrl: string | null;
  shelter: {
    districtCode: string | null;
    shelterName: string;
    shelterSeq: number;
  } | null;
  weight: string | null;
};

export type ShelterAnimalImage = {
  displayOrder: number;
  imageSeq: number;
  imageUrl: string | null;
};

export type ShelterAnimalDetail = ShelterAnimalCard & {
  description: string | null;
  endReason: 'NATURAL_DEATH' | 'EUTHANIZED' | 'OTHER' | 'RETURNED' | 'ADOPTED' | null;
  healthCheck: string | null;
  images: ShelterAnimalImage[];
  isBookmarked: boolean;
  microchipNumber: string | null;
  modDttm: string | null;
  protectionEndedDttm: string | null;
  registrationNumber: string | null;
  rescueInfo: {
    rescueAddress: string | null;
    rescueAddressDetail: string | null;
    rescueMemo: string | null;
    rescueReasonCode: string | null;
    rescuedDttm: string | null;
  };
  rescueSeq: number | null;
  shelter: {
    address: string;
    phone: string;
    shelterName: string;
    shelterSeq: number;
  } | null;
  vaccinationStatus: string | null;
};

export type ShelterAnimalPage = {
  content: ShelterAnimalCard[];
  hasNext: boolean;
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type ShelterAnimalSearchParams = {
  districtCode?: string;
  gender?: PetGender;
  isNeutered?: boolean;
  keyword?: string;
  page?: number;
  regFrom?: string;
  regTo?: string;
  size?: number;
  sort?: string;
  speciesSeq?: number;
  status?: ShelterAnimalFilterStatus;
};

export type AdoptionApplicationStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'APPROVED'
  | 'REJECTED'
  | 'CANCELED'
  | 'ADOPTED';

export type AdoptionAnswer = string | string[];

export type AdoptionApplicationFormRequest = {
  animalSeq: number;
  answers?: Record<string, AdoptionAnswer>;
  careAddress?: string | null;
  careAddressDetail?: string | null;
  email?: string | null;
  visitHopeDate?: string | null;
};

export type SaveAdoptionDraftRequest = Omit<AdoptionApplicationFormRequest, 'animalSeq'>;

export type AdoptionAnimalSummary = {
  age: string | null;
  animalSeq: number;
  breedName: string | null;
  colorName: string | null;
  gender: PetGender;
  isNeutered: boolean | null;
  microchipNumber: string | null;
  registrationNumber: string | null;
  representativeImageUrl: string | null;
  shelterAddress: string | null;
  shelterName: string | null;
  shelterSeq: number;
  speciesName: string | null;
  weight: string | null;
};

export type MyAdoptionApplication = {
  animalSummary: AdoptionAnimalSummary | null;
  applicationSeq: number;
  displayDttm: string | null;
  rejectionReason: string | null;
  status: AdoptionApplicationStatus;
  submittedDttm: string | null;
};

export type MyAdoptionApplicationPage = {
  content: MyAdoptionApplication[];
  hasNext: boolean;
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type AdoptionQuestion = {
  condition: {
    depends_on: number;
    operator: 'EQ' | 'NE' | 'IN';
    required_when_matched: boolean;
    value: string | string[];
  } | null;
  displayOrder: number;
  isRequired: boolean;
  options: string[] | null;
  questionSeq: number;
  questionText: string;
  questionType: 'TEXT' | 'SINGLE_CHOICE' | 'MULTI_CHOICE' | 'DATE';
};

export type AdoptionApplicationDetail = {
  animalSummary: AdoptionAnimalSummary | null;
  answers: Record<string, AdoptionAnswer>;
  applicant: { birthDate: string | null; name: string | null; phone: string | null };
  applicationSeq: number;
  careAddress: string | null;
  careAddressDetail: string | null;
  email: string | null;
  questions: Array<
    Pick<AdoptionQuestion, 'displayOrder' | 'questionSeq' | 'questionText' | 'questionType'>
  >;
  rejectionReason: string | null;
  reviewedDttm: string | null;
  status: AdoptionApplicationStatus;
  submittedDttm: string | null;
  userSeq: number;
  visitHopeDate: string | null;
};

export async function searchShelterAnimals(
  params: ShelterAnimalSearchParams = {},
): Promise<ShelterAnimalPage> {
  const response = await apiClient.get<ApiResponse<ShelterAnimalPage>>('/shelter-animals', {
    params: { page: 0, size: 100, sort: 'regDttm,desc', ...params },
  });
  return response.data.data;
}

export async function getShelterAnimal(animalSeq: number): Promise<ShelterAnimalDetail> {
  const response = await apiClient.get<ApiResponse<ShelterAnimalDetail>>(
    `/shelter-animals/${animalSeq}`,
  );
  return response.data.data;
}

export async function getAdoptionQuestions(): Promise<AdoptionQuestion[]> {
  const response = await apiClient.get<ApiResponse<{ questions: AdoptionQuestion[] }>>(
    '/adoption/questions',
  );
  return response.data.data.questions;
}

export async function getMyAdoptionApplications(params?: {
  animalSeq?: number;
  page?: number;
  size?: number;
  status?: AdoptionApplicationStatus;
}): Promise<MyAdoptionApplicationPage> {
  const response = await apiClient.get<ApiResponse<MyAdoptionApplicationPage>>(
    '/adoption-applications',
    { params: { page: 0, size: 100, ...params } },
  );
  return response.data.data;
}

export async function getAdoptionApplication(
  applicationSeq: number,
): Promise<AdoptionApplicationDetail> {
  const response = await apiClient.get<ApiResponse<AdoptionApplicationDetail>>(
    `/adoption-applications/${applicationSeq}`,
  );
  return response.data.data;
}

export async function createAdoptionDraft(form: AdoptionApplicationFormRequest) {
  const response = await apiClient.post<
    ApiResponse<{ animalSeq: number; applicationSeq: number; status: 'DRAFT' }>
  >('/adoption-applications', form);
  return response.data.data;
}

export async function saveAdoptionDraft(applicationSeq: number, form: SaveAdoptionDraftRequest) {
  const response = await apiClient.patch<
    ApiResponse<{ applicationSeq: number; savedQuestionCount: number; status: 'DRAFT' }>
  >(`/adoption-applications/${applicationSeq}`, form);
  return response.data.data;
}

export async function deleteAdoptionDraft(applicationSeq: number): Promise<void> {
  await apiClient.delete(`/adoption-applications/${applicationSeq}`);
}

export async function submitAdoptionApplication(form: AdoptionApplicationFormRequest) {
  const response = await apiClient.post<
    ApiResponse<{ applicationSeq: number; status: 'SUBMITTED'; submittedDttm: string }>
  >('/adoption-applications/submit', form);
  return response.data.data;
}

export async function cancelAdoptionApplication(applicationSeq: number) {
  const response = await apiClient.post<
    ApiResponse<{ applicationSeq: number; status: 'CANCELED' }>
  >(`/adoption-applications/${applicationSeq}/cancel`);
  return response.data.data;
}
