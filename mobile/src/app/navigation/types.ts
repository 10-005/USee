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
  PetList: undefined;
  PetDetail: { petId: string };
  ShelterDetail: { shelterId: string };
};
