export const STORAGE_KEYS = {
  ADOPTION_APPLICATION_DRAFT: '@usee/adoption-application-draft',
  APP_STATE: '@usee/app-state',
} as const;

export type StorageKey = (typeof STORAGE_KEYS)[keyof typeof STORAGE_KEYS];
