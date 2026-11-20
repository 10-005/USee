import type { ApiResponse } from '../api';
import { apiClient } from '../api/client';

export type PetGender = 'MALE' | 'FEMALE' | 'UNKNOWN';

export type AnimalSpecies = {
  speciesCode: 'DOG' | 'CAT' | 'ETC';
  speciesName: string;
  speciesSeq: number;
};

export type AnimalBreed = {
  breedName: string;
  breedSeq: number;
  personality: string;
  representativeImageUrl: string | null;
  speciesSeq: number;
};

export type AnimalCombination = {
  breedName: string;
  breedSeq: number;
  colorName: string;
  colorSeq: number;
  combinationSeq: number;
  speciesCode: 'DOG' | 'CAT' | 'ETC';
  speciesName: string;
  speciesSeq: number;
};

export type CreatePetRequest = {
  age: string | null;
  combinationSeq: number;
  description: string | null;
  gender: PetGender;
  imageSeqs: number[];
  isNeutered: boolean | null;
  microchipNumber: string | null;
  name: string;
  weight: string | null;
};

export type CreatedPet = {
  imageCount: number;
  petSeq: number;
  representativeImageSeq: number;
};

export type PetClassification = {
  breedName: string;
  breedSeq: number;
  colorName: string;
  colorSeq: number;
  combinationSeq: number;
  speciesCode: AnimalSpecies['speciesCode'];
  speciesName: string;
  speciesSeq: number;
};

export type PetImage = {
  displayOrder: number;
  imageSeq: number;
  imageUrl: string;
};

export type PetListItem = {
  classification: PetClassification;
  gender: PetGender;
  name: string;
  petSeq: number;
  regDttm: string;
  representativeImageUrl: string;
};

export type PetDetail = PetListItem & {
  age: string | null;
  description: string | null;
  images: PetImage[];
  isNeutered: boolean | null;
  microchipNumber: string | null;
  modDttm: string;
  weight: string | null;
};

export type PetPage = {
  content: PetListItem[];
  hasNext: boolean;
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type UpdatePetRequest = Partial<CreatePetRequest>;

export async function getAnimalSpecies(): Promise<AnimalSpecies[]> {
  const response = await apiClient.get<ApiResponse<AnimalSpecies[]>>('/animal-species');
  return response.data.data;
}

export async function getAnimalBreeds(speciesSeq: number): Promise<AnimalBreed[]> {
  const response = await apiClient.get<ApiResponse<AnimalBreed[]>>(
    `/animal-species/${speciesSeq}/breeds`,
  );
  return response.data.data;
}

export async function getAnimalCombinations(breedSeq: number): Promise<AnimalCombination[]> {
  const response = await apiClient.get<ApiResponse<AnimalCombination[]>>('/animal-combinations', {
    params: { breedSeq },
  });
  return response.data.data;
}

export async function createPet(request: CreatePetRequest): Promise<CreatedPet> {
  const response = await apiClient.post<ApiResponse<CreatedPet>>('/users/me/pets', request);
  return response.data.data;
}

export async function getMyPets(): Promise<PetPage> {
  const response = await apiClient.get<ApiResponse<PetPage>>('/users/me/pets', {
    params: { page: 0, size: 100, sort: 'regDttm,desc' },
  });
  return response.data.data;
}

export async function getMyPet(petSeq: number): Promise<PetDetail> {
  const response = await apiClient.get<ApiResponse<PetDetail>>(`/users/me/pets/${petSeq}`);
  return response.data.data;
}

export async function updateMyPet(petSeq: number, request: UpdatePetRequest): Promise<PetDetail> {
  const response = await apiClient.patch<ApiResponse<PetDetail>>(
    `/users/me/pets/${petSeq}`,
    request,
  );
  return response.data.data;
}

export async function deleteMyPet(petSeq: number): Promise<void> {
  await apiClient.delete(`/users/me/pets/${petSeq}`);
}
