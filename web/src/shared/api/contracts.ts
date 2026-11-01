/** 웹·모바일·백엔드 합의 전 제안 타입. 실제 서버 규격 확정 후 갱신하세요. */
export interface ApiSuccess<T> {
  data: T;
}

export interface ApiFailure {
  error: {
    code: string;
    message: string;
    fieldErrors?: Record<string, string[]>;
  };
}

/** 페이지 번호는 0부터 시작하는 제안입니다. */
export interface PageData<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
