/** 공통 API 응답 봉투. T는 엔드포인트별 data 타입입니다. */
export interface ApiResponse<T> {
  status: number;
  code: string;
  message: string;
  data: T;
  errors: FieldError[];
  traceId: string;
}
export interface FieldError {
  field: string;
  reason: string;
}
export type ApiSuccess<T> = ApiResponse<T>;
export type ApiFailure = ApiResponse<null>;
/** page는 0부터 시작합니다. 엔드포인트별 기본 size는 다를 수 있습니다. */
export interface PageData<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}
