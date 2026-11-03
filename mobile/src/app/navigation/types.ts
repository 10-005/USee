import type { AdoptionApplicationStatus } from '../../services/storage/adoptionApplicationStorage';

export type RootStackParamList = {
  ArWalk: undefined;
  AdoptionApplication: { animalId: string };
  AdoptionApplicationHistory: {
    animalId: string;
    previewStatus?: AdoptionApplicationStatus;
    source?: 'draft' | 'record';
  };
  AdoptionApplicationList: undefined;
  AdoptionApplicationReview: { animalId: string };
  AdoptionDetail: { animalId: string };
  Home: undefined;
  MockAdoptionActivity: { petId: string };
  MockAdoptionSelection: undefined;
  PetList: undefined;
  PetRegistration: { petId?: string } | undefined;
  PetDetail: { petId: string };
  MockAdoptionBudget: { petId: string };
  ShelterDetail: { shelterId: string };
};
