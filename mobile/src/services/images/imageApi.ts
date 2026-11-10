import { ApiClientError, type ApiResponse } from '../api';
import { apiClient } from '../api/client';

export type ImageUploadSource = {
  byteSize: number;
  contentType: 'image/jpeg' | 'image/png';
  fileName: string;
  uri: string;
};

export type IssuedImageUpload = {
  expiresInSeconds: number;
  imageKey: string;
  uploadUrl: string;
};

export type ConfirmedImage = {
  byteSize: number;
  imageSeq: number;
  imageUrl: string;
  mimeType: string;
};

export async function uploadImageToPendingStorage(
  source: ImageUploadSource,
): Promise<IssuedImageUpload> {
  const issued = await issueImageUpload(source);
  const fileResponse = await fetch(source.uri);
  const body = await fileResponse.blob();
  const uploadResponse = await fetch(issued.uploadUrl, {
    body,
    headers: {
      'Content-Length': String(source.byteSize),
      'Content-Type': source.contentType,
      'If-None-Match': '*',
      'x-amz-tagging': 'upload=pending',
    },
    method: 'PUT',
  });
  if (!uploadResponse.ok) {
    throw new ApiClientError({
      code: 'IMAGE_UPLOAD_FAILED',
      message: '사진 업로드에 실패했어요. 다시 시도해 주세요.',
      status: uploadResponse.status,
    });
  }
  return issued;
}

export async function confirmImage(imageKey: string): Promise<ConfirmedImage> {
  const response = await apiClient.post<ApiResponse<ConfirmedImage>>('/images', { imageKey });
  return response.data.data;
}

async function issueImageUpload(source: ImageUploadSource): Promise<IssuedImageUpload> {
  const response = await apiClient.post<ApiResponse<IssuedImageUpload>>('/images/upload-urls', {
    byteSize: source.byteSize,
    contentType: source.contentType,
    fileName: source.fileName,
  });
  return response.data.data;
}
