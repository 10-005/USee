import { createNavigationContainerRef } from '@react-navigation/native';

import type { RootStackParamList } from './types';
import { takePendingNotificationOpen } from '../../services/notifications/notificationOpen';
import { resolveNotificationDestination } from '../../services/notifications/pushDestination';
import { markNotificationAsRead } from '../../services/notifications/notificationApi';

export const notificationNavigationRef = createNavigationContainerRef<RootStackParamList>();

export function openPendingNotification(isAuthenticated: boolean): void {
  const navigation = notificationNavigationRef;
  if (
    !isAuthenticated ||
    !navigation.isReady() ||
    !navigation.getRootState()?.routeNames.includes('Home')
  ) {
    return;
  }
  const message = takePendingNotificationOpen();
  if (!message) {
    return;
  }
  if (message.kind === 'server' && message.notificationType !== 'CHAT') {
    markNotificationAsRead(Number(message.notificationSeq)).catch(() => undefined);
  }
  const destination = resolveNotificationDestination(message);
  if (!destination) {
    return;
  }
  const route =
    destination.name === 'Simulation'
      ? { name: 'MockAdoptionOverview' }
      : (() => {
          const { name, ...params } = destination;
          return { name, params };
        })();
  navigation.resetRoot({ index: 1, routes: [{ name: 'Home' }, route] });
}
