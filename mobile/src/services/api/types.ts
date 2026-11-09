export const API_SERVICES = ['core', 'chat', 'notification', 'simulation'] as const;

export type ApiService = (typeof API_SERVICES)[number];

export type AccessTokenProvider = () => Promise<string | null> | string | null;

export type ApiClientOptions = {
  baseURL: string;
  getAccessToken?: AccessTokenProvider;
  timeout?: number;
};

export type ApiFieldError = {
  field: string;
  reason: string;
};

export type ApiResponse<T> = {
  status: number;
  code: string;
  message: string;
  data: T;
  errors: ApiFieldError[];
  traceId: string;
};

export type PageResponse<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
};

export type ImageRef = {
  imageSeq: number;
  imageUrl: string | null;
  displayOrder: number;
};

export type AnimalClassification = {
  combinationSeq: number;
  speciesSeq: number;
  speciesName: string;
  breedSeq: number;
  breedName: string;
  colorSeq: number;
  colorName: string;
};

export type UserRole = 'USER' | 'SHELTER';

export type ActorSummary = {
  userSeq: number;
  displayName: string;
  role: UserRole;
  profileImage?: ImageRef | null;
};

export type GeoPoint = {
  address: string;
  latitude: number;
  longitude: number;
  districtCode: string | null;
};

export type District = {
  districtCode: string;
  sidoName: string;
  sigunguName: string;
};

export type ApiError = {
  code: string;
  message: string;
  status?: number;
  errors?: ApiFieldError[];
  traceId?: string;
};
