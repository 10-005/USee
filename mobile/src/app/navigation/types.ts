import type { ChatPreview } from '../../stores/chatStore';
import type { AdoptionApplicationStatus } from '../../services/storage/adoptionApplicationStorage';

export type RootStackParamList = {
  ArWalk: undefined;
  AdoptionApplication: { animalId: string };
  AdoptionApplicantEdit: undefined;
  AdoptionApplicationHistory: {
    animalId: string;
    previewStatus?: AdoptionApplicationStatus;
    source?: 'draft' | 'record';
  };
  AdoptionApplicationList: undefined;
  AdoptionApplicationReview: { animalId: string };
  AdoptionDetail: { animalId: string };
  Home: undefined;
  FoundReportDetail: { reportId: string };
  LostReportDetail: { reportId: string };
LostReportForm: undefined;
  LostReportLocationPicker: { target?: 'adoption' | 'lost-report' } | undefined;
  LostReportPetSelection: undefined;
  ChatRoom: { roomId: string; preview?: ChatPreview };
  MockAdoptionActivity: { petId: string };
  NoticeDetail: { noticeId: string };
  NoticeList: undefined;
  MockAdoptionSelection: undefined;
  PetList: undefined;
  PetRegistration: { flow?: 'lost-report'; petId?: string } | undefined;
  PetDetail: { petId: string };
  ProfileEdit: undefined;
  Settings: undefined;
  MockAdoptionBudget: { petId: string };
  ShelterDetail: { shelterId: string };
};
