import type { ApiResponse } from '../api';
import { apiClient } from '../api/client';

export type CommentTargetType = 'MISSING' | 'FOUND';
export type CommentType = 'MISSING_COMMENT' | 'FOUND_COMMENT';

export type CommentAuthor = {
  displayName: string;
  profileImage: { imageSeq: number; imageUrl: string } | null;
  role: 'USER' | 'SHELTER';
  userSeq: number;
};

export type CommentDetail = {
  author: CommentAuthor;
  body: string | null;
  commentSeq: number;
  commentType: CommentType;
  image: { imageSeq: number; imageUrl: string } | null;
  isDeleted: boolean;
  modDttm: string | null;
  parentCommentSeq: number | null;
  regDttm: string;
  replyCount: number;
  targetSeq: number;
  targetType: CommentTargetType;
};

export type CommentThread = CommentDetail & { replies: CommentDetail[] };

export type CreateCommentRequest = {
  body?: string | null;
  imageSeq?: number | null;
  parentCommentSeq?: number | null;
  targetSeq: number;
  targetType: CommentTargetType;
};

export type UpdateCommentRequest = {
  body?: string | null;
  imageSeq?: number | null;
};

export type CreatedComment = {
  commentSeq: number;
  commentType: CommentType;
  parentCommentSeq: number | null;
};

export function getCommentType(targetType: CommentTargetType): CommentType {
  return targetType === 'MISSING' ? 'MISSING_COMMENT' : 'FOUND_COMMENT';
}

export async function getComments(
  targetType: CommentTargetType,
  targetSeq: number,
): Promise<CommentDetail[]> {
  const response = await apiClient.get<ApiResponse<CommentDetail[]>>('/comments', {
    params: { targetSeq, targetType },
  });
  return response.data.data;
}

export async function getCommentReplies(
  commentType: CommentType,
  commentSeq: number,
): Promise<CommentDetail[]> {
  const response = await apiClient.get<ApiResponse<CommentDetail[]>>(
    `/comments/${commentType}/${commentSeq}/replies`,
  );
  return response.data.data;
}

export async function getCommentThreads(
  targetType: CommentTargetType,
  targetSeq: number,
): Promise<CommentThread[]> {
  const roots = await getComments(targetType, targetSeq);
  return Promise.all(
    roots.map(async root => ({
      ...root,
      replies:
        root.replyCount > 0 ? await getCommentReplies(root.commentType, root.commentSeq) : [],
    })),
  );
}

export async function createComment(request: CreateCommentRequest): Promise<CreatedComment> {
  const response = await apiClient.post<ApiResponse<CreatedComment>>('/comments', request);
  return response.data.data;
}

export async function updateComment(
  commentType: CommentType,
  commentSeq: number,
  request: UpdateCommentRequest,
): Promise<CommentDetail> {
  const response = await apiClient.patch<ApiResponse<CommentDetail>>(
    `/comments/${commentType}/${commentSeq}`,
    request,
  );
  return response.data.data;
}

export async function deleteComment(
  commentType: CommentType,
  commentSeq: number,
): Promise<void> {
  await apiClient.delete(`/comments/${commentType}/${commentSeq}`);
}
