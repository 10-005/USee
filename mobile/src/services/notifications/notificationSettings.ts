import { PermissionsAndroid, Platform } from 'react-native';

import { STORAGE_KEYS } from '../../constants/storageKeys';
import {
  useNotificationSettingsStore,
  type NotificationSettingKey,
} from '../../stores/notificationSettingsStore';
import { getAccessToken } from '../auth/authSession';
import { getStoredValue, setStoredValue } from '../storage/jsonStorage';
import {
  getMyNotificationSettings,
  updateMyNotificationSettings,
  type NotificationSettings,
} from './notificationSettingsApi';

export const notificationSettingFields = {
  adoption: 'adoptionEnabled',
  aiMatching: 'aiMatchEnabled',
  chat: 'chatEnabled',
  comment: 'commentEnabled',
  mockAdoption: 'simulationEnabled',
} as const satisfies Record<NotificationSettingKey, keyof NotificationSettings>;

type InitialPermission = { allowed: boolean; synced: boolean };
let initialization: Promise<void> | null = null;
let synchronization: Promise<void> | null = null;

function allNotificationSettings(allowed: boolean) {
  return {
    simulationEnabled: allowed,
    chatEnabled: allowed,
    commentEnabled: allowed,
    aiMatchEnabled: allowed,
    adoptionEnabled: allowed,
  };
}

export function initializeNotificationPermission(): Promise<void> {
  if (Platform.OS !== 'android') {
    return Promise.resolve();
  }
  if (initialization) {
    return initialization;
  }
  initialization = (async () => {
    let initial = await getStoredValue<InitialPermission>(
      STORAGE_KEYS.INITIAL_NOTIFICATION_PERMISSION,
    );
    if (!initial) {
      const allowed =
        Number(Platform.Version) < 33 ||
        (await PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS)) ===
          PermissionsAndroid.RESULTS.GRANTED;
      initial = { allowed, synced: false };
      await setStoredValue(STORAGE_KEYS.INITIAL_NOTIFICATION_PERMISSION, initial);
    }
    await syncInitialNotificationSettings();
  })().finally(() => {
    initialization = null;
  });
  return initialization;
}

export function syncInitialNotificationSettings(): Promise<void> {
  if (synchronization) {
    return synchronization;
  }
  synchronization = (async () => {
    const initial = await getStoredValue<InitialPermission>(
      STORAGE_KEYS.INITIAL_NOTIFICATION_PERMISSION,
    );
    if (!initial || initial.synced || !getAccessToken()) {
      return;
    }
    const settings = await updateMyNotificationSettings(allNotificationSettings(initial.allowed));
    useNotificationSettingsStore.getState().applyServerSettings(settings);
    await setStoredValue(STORAGE_KEYS.INITIAL_NOTIFICATION_PERMISSION, {
      ...initial,
      synced: true,
    });
  })().finally(() => {
    synchronization = null;
  });
  return synchronization;
}

export async function loadNotificationSettings(): Promise<void> {
  await syncInitialNotificationSettings();
  const settings = await getMyNotificationSettings();
  useNotificationSettingsStore.getState().applyServerSettings(settings);
}

export async function saveNotificationSettings(
  changes: Partial<NotificationSettings>,
): Promise<void> {
  const settings = await updateMyNotificationSettings(changes);
  useNotificationSettingsStore.getState().applyServerSettings(settings);
}
