import type { ApiResponse } from '../api';
import { apiClient } from '../api/client';
import type { PetClassification, PetGender } from '../pets';

export type BookmarkTargetType = 'MISSING' | 'FOUND' | 'SHELTER_ANIMAL';

export type BookmarkPostSummary = {
  address: string;
  age: string | null;
  classification: PetClassification | null;
  gender: PetGender;
  occurredDttm: string | null;
  representativeImageUrl: string | null;
  status: string;
};

export type BookmarkItem = {
  bookmarkSeq: number;
  postSummary: BookmarkPostSummary;
  regDttm: string;
  targetSeq: number;
  targetType: BookmarkTargetType;
};

export type BookmarkPage = {
  content: BookmarkItem[];
  hasNext: boolean;
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export const bookmarkKey = (targetType: BookmarkTargetType, targetSeq: number | string) =>
  `${targetType}:${targetSeq}`;

export async function getBookmarks(
  targetType?: BookmarkTargetType,
  page = 0,
  size = 100,
): Promise<BookmarkPage> {
  const response = await apiClient.get<ApiResponse<BookmarkPage>>('/users/me/bookmarks', {
    params: { page, size, sort: 'regDttm,desc', ...(targetType ? { targetType } : {}) },
  });
  return response.data.data;
}

export async function getAllBookmarks(targetType?: BookmarkTargetType): Promise<BookmarkItem[]> {
  const first = await getBookmarks(targetType);
  const rest = await Promise.all(
    Array.from({ length: Math.max(0, first.totalPages - 1) }, (_, index) =>
      getBookmarks(targetType, index + 1),
    ),
  );
  return [first, ...rest].flatMap(page => page.content);
}

export async function addBookmark(targetType: BookmarkTargetType, targetSeq: number) {
  const response = await apiClient.post<
    ApiResponse<{ bookmarkSeq: number; targetSeq: number; targetType: BookmarkTargetType }>
  >('/users/me/bookmarks', { targetSeq, targetType });
  return response.data.data;
}

export async function removeBookmark(targetType: BookmarkTargetType, targetSeq: number) {
  await apiClient.delete(`/users/me/bookmarks/${targetType}/${targetSeq}`);
}
