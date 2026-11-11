import { useEffect, useState } from 'react';
import { AppState, Pressable, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { subscribePushMessages } from '../services/notifications/remoteMessage';
import type { ParsedPushMessage } from '../services/notifications/pushMessage';
import { colors, radius, typography } from '../theme';

const POPUP_DURATION_MS = 2500;

export function PushPopup() {
  const insets = useSafeAreaInsets();
  const [message, setMessage] = useState<ParsedPushMessage | null>(null);

  useEffect(
    () =>
      subscribePushMessages(incoming => {
        if (AppState.currentState !== 'active') {
          return;
        }
        setMessage(incoming);
      }),
    [],
  );

  useEffect(() => {
    if (!message) {
      return;
    }
    const timer = setTimeout(() => setMessage(null), POPUP_DURATION_MS);
    return () => clearTimeout(timer);
  }, [message]);

  if (!message) {
    return null;
  }

  return (
    <View pointerEvents="box-none" style={[styles.overlay, { top: insets.top + 8 }]}>
      <Pressable
        accessibilityLiveRegion="polite"
        accessibilityRole="alert"
        onPress={() => setMessage(null)}
        style={styles.card}
      >
        <Text numberOfLines={1} style={styles.title}>
          {message.title}
        </Text>
        <Text numberOfLines={2} style={styles.body}>
          {message.body}
        </Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  overlay: { left: 16, position: 'absolute', right: 16, zIndex: 20 },
  card: {
    backgroundColor: colors.white,
    borderRadius: radius.md,
    elevation: 6,
    paddingHorizontal: 16,
    paddingVertical: 14,
    shadowColor: colors.black,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.16,
    shadowRadius: 12,
  },
  title: { ...typography.bodySemiBold16, color: colors.gray800 },
  body: { ...typography.bodyRegular14, color: colors.gray600, marginTop: 4 },
});
