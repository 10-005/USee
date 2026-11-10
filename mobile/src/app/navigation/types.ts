import type { ChatPreview } from '../../stores/chatStore';
import type { AdoptionApplicationStatus } from '../../services/storage/adoptionApplicationStorage';

export type RootStackParamList = {
  PetShop: { petId: string };
  ShopProductDetail: { petId: string; productId: string };
  ShopCart: { petId: string };
  ShopInventory: { petId: string };
  Login: undefined;
  SignupOnboarding: undefined;
  ArWalk: { petName: string } | undefined;
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
  Home:
    | {
        initialReportTab?: 'found' | 'lost';
        initialTab?: 'search';
      }
    | undefined;
  FoundReportDetail: { highlightCommentId?: string; reportId: string };
  FoundReportForm: { reportId?: string } | undefined;
  FoundReportMap: undefined;
  LostReportDetail: { reportId: string };
  LostReportForm: { reportId?: string } | undefined;
  LostReportLocationPicker: { target?: 'adoption' | 'found-report' | 'lost-report' } | undefined;
  LostReportPetSelection: undefined;
  ChatRoom: { roomId: string; preview?: ChatPreview };
  MockAdoptionActivity: { petId: string };
  MockAdoptionOverview: undefined;
  MockAdoptionReport: { sessionSeq: number };
  NoticeDetail: { noticeId: string };
  NoticeList: undefined;
  Notifications: undefined;
  MockAdoptionSelection: undefined;
  PetList: undefined;
  PetRegistration:
    | { flow?: 'found-report' | 'lost-report'; petId?: string; reportId?: string }
    | undefined;
  PetDetail: { petId: string };
  ProfileEdit: undefined;
  Settings: undefined;
  SavedPosts: undefined;
  MockAdoptionName: { petId: string };
  MockAdoptionBudget: { petId: string; petName?: string };
  MyPosts: undefined;
  ShelterDetail: { shelterId: string };
};
