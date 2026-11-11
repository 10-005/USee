export const STORAGE_KEYS = {
  INITIAL_NOTIFICATION_PERMISSION: '@usee/initial-notification-permission',
  FCM_TOKEN: '@usee/fcm-token',
  CHAT_ROOMS: '@usee/chat-rooms-v1',
  ADOPTION_APPLICATION_DRAFT: '@usee/adoption-application-draft',
  ADOPTION_APPLICATION_RECORD: '@usee/adoption-application-record',
  APP_STATE: '@usee/app-state',
  MOCK_ADOPTION_SESSION: '@usee/mock-adoption-session',
  MOCK_ADOPTION_TOILET: '@usee/mock-adoption-toilet',
} as const;

export type StorageKey = (typeof STORAGE_KEYS)[keyof typeof STORAGE_KEYS];
