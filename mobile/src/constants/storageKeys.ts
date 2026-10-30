export const STORAGE_KEYS = {
  APP_STATE: '@usee/app-state',
} as const;

export type StorageKey = (typeof STORAGE_KEYS)[keyof typeof STORAGE_KEYS];
