import type {
  Adoption,
  AdoptionStatus,
  Rescue,
  ShelterAnimalRecord,
  ShelterProfile,
} from '../model';
import type { Report } from '../report';

/** 프런트엔드 내부 계약이며 HTTP 경로, 응답 포맷, 페이지네이션을 지정하지 않습니다. */
export interface ShelterRepository {
  listAnimals(options?: ReadOptions): Promise<ShelterAnimalRecord[]>;
  getAnimal(id: string, options?: ReadOptions): Promise<ShelterAnimalRecord | null>;
  listRescues(options?: ReadOptions): Promise<Rescue[]>;
  getRescue(id: string, options?: ReadOptions): Promise<Rescue | null>;
  listAdoptions(options?: ReadOptions): Promise<Adoption[]>;
  getAdoption(id: string, options?: ReadOptions): Promise<Adoption | null>;
  listReports(options?: ReadOptions): Promise<Report[]>;
  getProfile(options?: ReadOptions): Promise<ShelterProfile>;
  saveAnimal(animal: ShelterAnimalRecord): Promise<ShelterAnimalRecord>;
  deleteAnimal(id: string): Promise<void>;
  saveRescue(rescue: Rescue): Promise<Rescue>;
  saveProfile(profile: ShelterProfile): Promise<ShelterProfile>;
  reviewAdoption(id: string, status: AdoptionStatus, reason?: string): Promise<void>;
}

export type ReadOptions = { signal?: AbortSignal };
