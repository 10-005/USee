export const STORAGE_KEYS = {
  ADOPTION_APPLICATION_DRAFT: '@usee/adoption-application-draft',
  ADOPTION_APPLICATION_RECORD: '@usee/adoption-application-record',
  APP_STATE: '@usee/app-state',
  MOCK_ADOPTION_SESSION: '@usee/mock-adoption-session',
} as const;

export type StorageKey = (typeof STORAGE_KEYS)[keyof typeof STORAGE_KEYS];
