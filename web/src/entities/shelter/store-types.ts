import type {
  Adoption,
  AdoptionStatus,
  Rescue,
  ShelterAnimalRecord,
  ShelterProfile,
} from './model';

export type ShelterStore = {
  animals: ShelterAnimalRecord[];
  adoptions: Adoption[];
  rescues: Rescue[];
  profile: ShelterProfile;
  saveAnimal: (animal: ShelterAnimalRecord) => void;
  deleteAnimal: (id: string) => void;
  reviewAdoption: (id: string, status: AdoptionStatus, reason: string) => void;
  saveRescue: (rescue: Rescue) => void;
  saveProfile: (profile: ShelterProfile) => void;
};
