import type { ApiResponse } from '../api';
import { apiClient } from '../api/client';
import type { PetClassification, PetGender } from '../pets';

export type FoundReportStatus = 'ACTIVE' | 'CLOSED';

export type FoundReportSummary = {
  age: string | null;
  address: string;
  classification: PetClassification | null;
  districtCode: string | null;
  foundReportSeq: number;
  gender: PetGender;
  isNeutered: boolean | null;
  occurredDttm: string;
  regDttm: string | null;
  representativeImageUrl: string | null;
  shelterSeq: number | null;
  status: FoundReportStatus;
  weight: string | null;
};

export type FoundReportImage = {
  displayOrder: number;
  imageSeq: number;
  imageUrl: string | null;
};

export type FoundReportDetail = FoundReportSummary & {
  closedDttm: string | null;
  closedReason: string | null;
  commentCount: number;
  description: string | null;
  images: FoundReportImage[];
  isBookmarked: boolean;
  isPhonePublic: boolean;
  landmarkDescription: string | null;
  latitude: number;
  longitude: number;
  microchipNumber: string | null;
  reporter: {
    displayName: string | null;
    profileImage: string | { imageUrl: string | null } | null;
    role: 'USER' | 'SHELTER';
    userSeq: number;
  } | null;
  reporterPhone: string | null;
  shelter: { shelterName: string; shelterSeq: number } | null;
};

export type FoundReportPage = {
  content: FoundReportSummary[];
  hasNext: boolean;
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type FoundReportSearchParams = {
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
  status?: FoundReportStatus;
};

export type CreateFoundReportRequest = {
  address: string;
  age: string | null;
  combinationSeq: number;
  description: string | null;
  gender: PetGender;
  imageSeqs: number[];
  isNeutered: boolean | null;
  isPhonePublic: boolean;
  landmarkDescription: string | null;
  latitude: number;
  longitude: number;
  microchipNumber: string | null;
  occurredDttm: string;
  reporterPhone: string;
  weight: string | null;
};

export type UpdateFoundReportRequest = Partial<CreateFoundReportRequest>;

export async function searchFoundReports(
  params: FoundReportSearchParams = {},
): Promise<FoundReportPage> {
  const response = await apiClient.get<ApiResponse<FoundReportPage>>('/found-reports', {
    params: { page: 0, size: 100, sort: 'regDttm,desc', ...params },
  });
  return response.data.data;
}

export async function getMyFoundReports(): Promise<FoundReportSummary[]> {
  const response = await apiClient.get<ApiResponse<FoundReportSummary[]>>('/found-reports/me');
  return response.data.data;
}

export async function getFoundReport(reportSeq: number): Promise<FoundReportDetail> {
  const response = await apiClient.get<ApiResponse<FoundReportDetail>>(
    `/found-reports/${reportSeq}`,
  );
  return response.data.data;
}

export async function createFoundReport(
  request: CreateFoundReportRequest,
): Promise<{ foundReportSeq: number; status: FoundReportStatus }> {
  const response = await apiClient.post<
    ApiResponse<{ foundReportSeq: number; status: FoundReportStatus }>
  >('/found-reports', request);
  return response.data.data;
}

export async function updateFoundReport(
  reportSeq: number,
  request: UpdateFoundReportRequest,
): Promise<FoundReportDetail> {
  const response = await apiClient.patch<ApiResponse<FoundReportDetail>>(
    `/found-reports/${reportSeq}`,
    request,
  );
  return response.data.data;
}

export async function changeFoundReportStatus(
  reportSeq: number,
  status: FoundReportStatus,
  closedReason?: string,
): Promise<void> {
  await apiClient.patch(`/found-reports/${reportSeq}/status`, {
    closedReason: closedReason?.trim() || null,
    status,
  });
}

export async function deleteFoundReport(reportSeq: number): Promise<void> {
  await apiClient.delete(`/found-reports/${reportSeq}`);
}
