import { Modal, Pressable, StyleSheet, Text, View } from 'react-native';
import { colors, typography } from '../../../theme';

type Props = {
  title: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  onConfirm: () => void;
  onClose: () => void;
};

// Same centered card and action styles as the app's status-change confirmation.
export function AppDialog({
  title,
  message,
  confirmLabel = '확인',
  cancelLabel,
  onConfirm,
  onClose,
}: Props) {
  return (
    <Modal transparent visible animationType="fade" onRequestClose={onClose}>
      <View style={styles.backdrop}>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="대화상자 닫기"
          onPress={onClose}
          style={StyleSheet.absoluteFill}
        />
        <View accessibilityViewIsModal style={styles.card}>
          <Text style={styles.title}>{title}</Text>
          <Text style={styles.message}>{message}</Text>
          <View style={styles.actions}>
            {cancelLabel ? (
              <Pressable
                accessibilityRole="button"
                accessibilityLabel={cancelLabel}
                onPress={onClose}
                style={({ pressed }) => [styles.button, styles.cancel, pressed && styles.pressed]}
              >
                <Text style={styles.buttonText}>{cancelLabel}</Text>
              </Pressable>
            ) : null}
            <Pressable
              accessibilityRole="button"
              accessibilityLabel={confirmLabel}
              onPress={onConfirm}
              style={({ pressed }) => [styles.button, styles.confirm, pressed && styles.pressed]}
            >
              <Text style={styles.buttonText}>{confirmLabel}</Text>
            </Pressable>
          </View>
        </View>
      </View>
    </Modal>
  );
}
const styles = StyleSheet.create({
  backdrop: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.3)',
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 24,
  },
  card: { backgroundColor: colors.white, borderRadius: 18, padding: 24, width: '100%' },
  title: { ...typography.titleSemiBold18, color: '#222222' },
  message: { ...typography.bodyRegular14, color: colors.gray500, marginTop: 16 },
  actions: { flexDirection: 'row', gap: 14, marginTop: 16 },
  button: { alignItems: 'center', borderRadius: 10, flex: 1, height: 40, justifyContent: 'center' },
  cancel: { backgroundColor: colors.gray200 },
  confirm: { backgroundColor: colors.green400 },
  buttonText: { ...typography.bodySemiBold16, color: colors.white },
  pressed: { opacity: 0.65 },
});
