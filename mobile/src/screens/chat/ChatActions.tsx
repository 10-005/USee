import { useEffect } from 'react';
import { BackHandler, Modal, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import LeaveIcon from '../../assets/icons/common/leave.svg';
import ReportIcon from '../../assets/icons/common/report.svg';
import { colors, typography } from '../../theme';

const coral = '#FA705B';
export type ChatAction = 'menu' | 'report' | 'leave';

type Props = {
  action: Exclude<ChatAction, 'report'>;
  onChoose?: (action: 'report' | 'leave') => void;
  onClose: () => void;
  onLeave: () => void;
};

export function ChatActions({ action, onChoose, onClose, onLeave }: Props) {
  const insets = useSafeAreaInsets();
  useEffect(() => {
    if (action !== 'menu') {
      return;
    }
    const back = BackHandler.addEventListener('hardwareBackPress', () => {
      onClose();
      return true;
    });
    return () => back.remove();
  }, [action, onClose]);

  if (action === 'menu') {
    return (
      <>
        <Pressable accessibilityLabel="메뉴 닫기" style={styles.backdrop} onPress={onClose} />
        <View style={styles.menu}>
          <Pressable
            accessibilityRole="button"
            onPress={() => onChoose?.('report')}
            style={styles.option}
          >
            <ReportIcon width={20} height={20} />
            <Text style={styles.dangerText}>신고하기</Text>
          </Pressable>
          <Pressable
            accessibilityRole="button"
            onPress={() => onChoose?.('leave')}
            style={styles.option}
          >
            <LeaveIcon width={20} height={20} />
            <Text style={styles.dangerText}>채팅방 나가기</Text>
          </Pressable>
        </View>
      </>
    );
  }
  return (
    <Modal transparent visible statusBarTranslucent animationType="fade" onRequestClose={onClose}>
      <View
        style={[styles.overlay, { paddingTop: insets.top + 16, paddingBottom: insets.bottom + 16 }]}
      >
        <View accessibilityViewIsModal style={styles.card}>
          <ScrollView
            bounces={false}
            contentContainerStyle={styles.content}
            decelerationRate="fast"
          >
            <Text style={styles.title}>채팅방을 나가시겠습니까?</Text>
            <Text style={styles.body}>삭제된 대화 내역은 복원할 수 없습니다.</Text>
            <View style={styles.actions}>
              <Pressable accessibilityRole="button" onPress={onClose} style={styles.button}>
                <Text style={styles.buttonText}>취소</Text>
              </Pressable>
              <Pressable
                accessibilityRole="button"
                onPress={onLeave}
                style={[styles.button, styles.confirm]}
              >
                <Text style={styles.buttonText}>채팅방 나가기</Text>
              </Pressable>
            </View>
          </ScrollView>
        </View>
      </View>
    </Modal>
  );
}
const styles = StyleSheet.create({
  backdrop: { ...StyleSheet.absoluteFill, zIndex: 8 },
  menu: {
    position: 'absolute',
    top: 80,
    right: 12,
    zIndex: 9,
    width: 160,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: '#D9DBE3',
    backgroundColor: 'white',
    elevation: 7,
    overflow: 'hidden',
  },
  option: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    minHeight: 48,
    paddingHorizontal: 16,
  },
  dangerText: { ...typography.bodyRegular16, color: coral },
  overlay: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 24,
    backgroundColor: 'rgba(0,0,0,0.30)',
  },
  card: {
    width: '100%',
    maxHeight: '100%',
    backgroundColor: 'white',
    borderRadius: 18,
  },
  content: { padding: 24 },
  title: {
    ...typography.titleSemiBold18,
    color: '#222222',
  },
  body: { ...typography.bodyRegular14, marginTop: 16, color: colors.gray500 },
  actions: { flexDirection: 'row', gap: 14, marginTop: 16 },
  button: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 40,
    borderRadius: 10,
    backgroundColor: colors.gray200,
  },
  confirm: { backgroundColor: coral },
  buttonText: { ...typography.bodySemiBold16, color: 'white' },
});
