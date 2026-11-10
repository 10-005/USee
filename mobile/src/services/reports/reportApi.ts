import type { ApiResponse } from '../api';
import { apiClient } from '../api/client';

export type ReportTargetType =
  | 'USER'
  | 'MISSING'
  | 'FOUND'
  | 'MISSING_COMMENT'
  | 'FOUND_COMMENT';

export type ReportReasonCode =
  | 'FALSE_INFO'
  | 'HARASSMENT'
  | 'OBSCENE'
  | 'OFFENSIVE'
  | 'OTHER'
  | 'SPAM_AD';

export const REPORT_REASON_CODE_BY_LABEL: Record<string, ReportReasonCode> = {
  '기타': 'OTHER',
  '스팸 / 광고': 'SPAM_AD',
  '욕설 / 불쾌한 내용': 'OFFENSIVE',
  '욕설 / 비방 / 괴롭힘': 'HARASSMENT',
  '음란 / 부적절한 내용': 'OBSCENE',
  '허위 정보 / 잘못된 동물 정보': 'FALSE_INFO',
};

export async function createReport(request: {
  reasonCode: ReportReasonCode;
  targetSeq: number;
  targetType: ReportTargetType;
}) {
  const response = await apiClient.post<
    ApiResponse<{ reportSeq: number; status: 'PENDING' }>
  >('/reports', request);
  return response.data.data;
}
