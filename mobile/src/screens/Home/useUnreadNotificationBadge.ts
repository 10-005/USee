import { useFocusEffect } from '@react-navigation/native';
import { useCallback, useEffect, useState } from 'react';
import { AppState } from 'react-native';

import { hasUnreadListNotification } from '../../services/notifications/notificationApi';
import { appearsInNotificationList } from '../../services/notifications/pushDestination';
import { subscribePushMessages } from '../../services/notifications/remoteMessage';

export function useUnreadNotificationBadge() {
  const [hasUnread, setHasUnread] = useState(false);
  const refresh = useCallback(() => {
    hasUnreadListNotification()
      .then(setHasUnread)
      .catch(() => undefined);
  }, []);

  useFocusEffect(
    useCallback(() => {
      refresh();
    }, [refresh]),
  );

  useEffect(() => {
    const subscription = AppState.addEventListener('change', state => {
      if (state === 'active') {
        refresh();
      }
    });
    return () => subscription.remove();
  }, [refresh]);

  useEffect(
    () =>
      subscribePushMessages(message => {
        if (appearsInNotificationList(message)) {
          setHasUnread(true);
        }
      }),
    [],
  );

  return hasUnread;
}
