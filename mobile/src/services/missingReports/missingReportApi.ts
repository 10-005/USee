import type { ApiResponse } from '../api';
import { apiClient } from '../api/client';
import type { PetGender, PetClassification } from '../pets';

export type MissingReportStatus = 'ACTIVE' | 'CLOSED';

export type MissingReportSummary = {
  age: string | null;
  address: string;
  classification: PetClassification | null;
  districtCode: string | null;
  gender: PetGender;
  isNeutered: boolean | null;
  missingReportSeq: number;
  occurredDttm: string;
  regDttm: string | null;
  representativeImageUrl: string | null;
  status: MissingReportStatus;
  weight: string | null;
};

export type MissingReportImage = {
  displayOrder: number;
  imageSeq: number;
  imageUrl: string | null;
};

export type MissingReportDetail = MissingReportSummary & {
  commentCount: number;
  contactPhone: string | null;
  description: string | null;
  images: MissingReportImage[];
  isBookmarked: boolean;
  isPhonePublic: boolean;
  latitude: number;
  longitude: number;
  microchipNumber: string | null;
  modDttm: string | null;
  user: {
    displayName: string | null;
    role: 'USER' | 'SHELTER';
    userSeq: number;
  } | null;
};

export type MissingReportPage = {
  content: MissingReportSummary[];
  hasNext: boolean;
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type MissingReportSearchParams = {
  districtCode?: string;
  gender?: PetGender;
  isNeutered?: boolean;
  keyword?: string;
  occurredFrom?: string;
  occurredTo?: string;
  page?: number;
  size?: number;
  sort?: string;
  speciesSeq?: number;
  status?: MissingReportStatus;
};

export type CreateMissingReportRequest = {
  age: string | null;
  address: string;
  combinationSeq: number;
  contactPhone: string;
  description: string | null;
  gender: PetGender;
  imageSeqs: number[];
  isNeutered: boolean | null;
  isPhonePublic: boolean;
  latitude: number;
  longitude: number;
  microchipNumber: string | null;
  occurredDttm: string;
  weight: string | null;
};

export type UpdateMissingReportRequest = Partial<CreateMissingReportRequest>;

export async function searchMissingReports(
  params: MissingReportSearchParams = {},
): Promise<MissingReportPage> {
  const response = await apiClient.get<ApiResponse<MissingReportPage>>('/missing-reports', {
    params: { page: 0, size: 100, sort: 'regDttm,desc', ...params },
  });
  return response.data.data;
}

export async function getMyMissingReports(): Promise<MissingReportSummary[]> {
  const response = await apiClient.get<ApiResponse<MissingReportSummary[]>>('/missing-reports/me');
  return response.data.data;
}

export async function getMissingReport(reportSeq: number): Promise<MissingReportDetail> {
  const response = await apiClient.get<ApiResponse<MissingReportDetail>>(
    `/missing-reports/${reportSeq}`,
  );
  return response.data.data;
}

export async function createMissingReport(
  request: CreateMissingReportRequest,
): Promise<{ missingReportSeq: number; status: MissingReportStatus }> {
  const response = await apiClient.post<
    ApiResponse<{ missingReportSeq: number; status: MissingReportStatus }>
  >('/missing-reports', request);
  return response.data.data;
}

export async function updateMissingReport(
  reportSeq: number,
  request: UpdateMissingReportRequest,
): Promise<MissingReportDetail> {
  const response = await apiClient.patch<ApiResponse<MissingReportDetail>>(
    `/missing-reports/${reportSeq}`,
    request,
  );
  return response.data.data;
}

export async function changeMissingReportStatus(
  reportSeq: number,
  status: MissingReportStatus,
): Promise<void> {
  await apiClient.patch(`/missing-reports/${reportSeq}/status`, { status });
}

export async function deleteMissingReport(reportSeq: number): Promise<void> {
  await apiClient.delete(`/missing-reports/${reportSeq}`);
}
